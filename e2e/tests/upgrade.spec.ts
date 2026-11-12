import { expect, Page, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * Runs against the full Docker Compose stack (nginx + API + PostgreSQL) after
 * it was started on top of data created by the original, pre-Flyway server.
 */
const seed = JSON.parse(readFileSync(join(__dirname, '..', '..', 'e2e-data', 'seed.json'), 'utf8')) as {
  files: Record<'readme' | 'beach' | 'notes' | 'draft', string>;
  folders: Record<'photos' | 'trip', string>;
};

const ALICE = { username: 'legacy-alice', password: 'legacy-password-1' };
const NEW_PASSWORD = 'a-brand-new-password';

test.describe.configure({ mode: 'serial' });

async function signIn(page: Page, username: string, password: string) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type=submit]');
  await page.waitForURL(/\/home/);
}

function row(page: Page, name: string) {
  return page.locator('tr', { has: page.locator('td.file-name', { hasText: name }) });
}

/** Settings and Sign out live in the account menu at the top right. */
async function accountMenu(page: Page, item: 'Settings' | 'Manage users' | 'Sign out') {
  await page.click('button.account-trigger');
  await page.getByRole('menuitem', { name: item }).click();
  // Signing out ends the session on the server first; wait for that, or the next
  // page load would start while the old session is still valid.
  if (item === 'Sign out') await page.waitForURL(/\/login/);
}

test('security headers are served by nginx', async ({ request }) => {
  const response = await request.get('/');
  expect(response.ok()).toBeTruthy();
  expect(response.headers()['content-security-policy']).toContain("default-src 'self'");
  expect(response.headers()['x-frame-options']).toBe('DENY');
});

test('signed-out visitors are sent to the login page', async ({ page }) => {
  await page.goto('/my-files');
  await expect(page).toHaveURL(/\/login\?returnUrl=%2Fmy-files/);
});

test('a user from the old version signs in with their old password and is promoted to admin', async ({ page }) => {
  await signIn(page, ALICE.username, ALICE.password);
  await expect(page.getByText(`Welcome back, ${ALICE.username}`)).toBeVisible();

  await accountMenu(page, 'Settings');
  await expect(page.getByText('Administrator', { exact: true })).toBeVisible();
});

test('the admin can add a user, who can then sign in', async ({ page }) => {
  await signIn(page, ALICE.username, ALICE.password);
  await accountMenu(page, 'Manage users');
  await expect(page).toHaveURL(/\/admin\/users/);
  const me = page.locator('tbody tr', { hasText: ALICE.username });
  await expect(me.getByText('you', { exact: true })).toBeVisible();
  await expect(me.getByText('Admin', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Add user' }).click();
  await page.fill('#new-username', 'e2e-member');
  await page.fill('#new-email', 'e2e-member@example.com');
  await page.fill('#new-password', 'e2e-member-password');
  await page.getByRole('button', { name: 'Add user' }).last().click();
  await expect(page.locator('tbody tr', { hasText: 'e2e-member' })).toBeVisible();

  // The activity log shows what just happened.
  await page.getByRole('link', { name: 'Activity' }).click();
  await expect(page.getByText('added e2e-member as a member')).toBeVisible();

  await accountMenu(page, 'Sign out');
  await signIn(page, 'e2e-member', 'e2e-member-password');
  await expect(page.getByText('Welcome back, e2e-member')).toBeVisible();
  // Members don't see the admin area.
  await expect(page.getByRole('link', { name: 'Users' })).toHaveCount(0);
});

test('files and folders from the old version are all there and downloadable', async ({ page }) => {
  await signIn(page, ALICE.username, ALICE.password);
  await page.click('a[href="/my-files"]');

  await expect(row(page, 'Photos')).toBeVisible();
  await expect(row(page, 'readme.txt')).toBeVisible();

  // Native browser download through nginx, streamed by the API.
  await row(page, 'readme.txt').hover();
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    row(page, 'readme.txt').getByRole('button', { name: 'Download' }).click(),
  ]);
  expect(download.suggestedFilename()).toBe('readme.txt');
  expect(readFileSync((await download.path())!, 'utf8')).toBe('hello from the old version');

  await row(page, 'Photos').dblclick();
  await expect(row(page, 'beach.png')).toBeVisible();
  await expect(row(page, '2024')).toBeVisible();
  // Files open in the in-app viewer: the image actually loads through nginx and the API.
  await row(page, 'beach.png').dblclick();
  const viewer = page.getByRole('dialog', { name: 'Preview of beach.png' });
  await expect(viewer).toBeVisible();
  await expect
    .poll(() => viewer.locator('img.image').evaluate((img: HTMLImageElement) => img.naturalWidth))
    .toBe(1);
  await page.keyboard.press('Escape');
  await expect(viewer).toBeHidden();

  // The grid shows the image's own thumbnail, made by the server on request.
  await page.getByRole('radio', { name: 'Grid' }).click();
  const tile = page.getByRole('option', { name: 'beach.png' });
  await expect(tile).toBeVisible();
  await expect
    .poll(() => tile.locator('img').evaluate((img: HTMLImageElement) => img.naturalWidth))
    .toBe(1);
  await page.getByRole('radio', { name: 'List' }).click();

  await row(page, '2024').dblclick();
  await expect(row(page, 'notes.md')).toBeVisible();
  await expect(page.locator('nav.breadcrumb')).toContainText('Photos');

  // Text files are shown in the viewer too.
  await row(page, 'notes.md').dblclick();
  await expect(page.getByRole('dialog', { name: 'Preview of notes.md' }).locator('pre')).toContainText(
    '# Trip notes',
  );
  await page.keyboard.press('Escape');

  // Starred state survived the upgrade.
  await page.click('a[href="/starred"]');
  await expect(row(page, 'readme.txt')).toBeVisible();
});

test('previews are served with the right type and a sandboxing CSP', async ({ page }) => {
  await signIn(page, ALICE.username, ALICE.password);
  const image = await page.request.get(`/api/v1/files/${seed.files.beach}/preview`);
  expect(image.headers()['content-type']).toBe('image/png');
  expect(image.headers()['content-security-policy']).toContain('sandbox');
});

test('the server image draws document thumbnails (fonts are installed)', async ({ page }) => {
  await signIn(page, ALICE.username, ALICE.password);
  const metadata = await (await page.request.get(`/api/v1/files/${seed.files.notes}/metadata`)).json();
  expect(metadata.thumbnail).toBe(true);
  const thumbnail = await page.request.get(`/api/v1/files/${seed.files.notes}/thumbnail`);
  expect(thumbnail.status()).toBe(200);
  expect(thumbnail.headers()['content-type']).toBe('image/jpeg');
  expect((await thumbnail.body()).length).toBeGreaterThan(1000);
});

test('state-changing requests without the CSRF header are rejected', async ({ page }) => {
  await signIn(page, ALICE.username, ALICE.password);
  const response = await page.request.post('/api/v1/folders', { data: { name: 'csrf-probe' } });
  expect(response.status()).toBe(403);
});

test('upload, rename, folder zip download', async ({ page }) => {
  await signIn(page, ALICE.username, ALICE.password);
  await page.goto(`/my-files?folderId=${seed.folders.photos}`);
  await expect(row(page, 'beach.png')).toBeVisible();

  await page.setInputFiles('input[type=file]', {
    name: 'after-upgrade.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('uploaded after the upgrade'),
  });
  await expect(row(page, 'after-upgrade.txt')).toBeVisible();

  await row(page, 'after-upgrade.txt').hover();
  await row(page, 'after-upgrade.txt').getByRole('button', { name: 'Rename file' }).click();
  const input = page.locator('#folderNameInput');
  await expect(input).toBeFocused();
  await page.keyboard.type('renamed');
  await page.click('button.btn-confirm');
  await expect(row(page, 'renamed.txt')).toBeVisible();

  await page.click('a[href="/my-files"]');
  await row(page, 'Photos').hover();
  const [zip] = await Promise.all([
    page.waitForEvent('download'),
    row(page, 'Photos').getByRole('button', { name: 'Download folder as zip' }).click(),
  ]);
  expect(zip.suggestedFilename()).toBe('Photos.zip');
  const bytes = readFileSync((await zip.path())!);
  expect(bytes.subarray(0, 2).toString()).toBe('PK');
  expect(bytes.includes(Buffer.from('Photos/2024/notes.md'))).toBeTruthy();
  expect(bytes.includes(Buffer.from('Photos/renamed.txt'))).toBeTruthy();
});

test('trash: restore an old item, trash it again, empty the trash', async ({ page }) => {
  await signIn(page, ALICE.username, ALICE.password);
  await page.click('a[href="/trash"]');
  await expect(row(page, 'old-draft.txt')).toBeVisible();

  await row(page, 'old-draft.txt').hover();
  await row(page, 'old-draft.txt').getByRole('button', { name: 'Restore' }).click();
  await expect(row(page, 'old-draft.txt')).toHaveCount(0);

  await page.click('a[href="/my-files"]');
  await row(page, 'old-draft.txt').hover();
  await row(page, 'old-draft.txt').getByRole('button', { name: 'Move to trash' }).click();
  await expect(row(page, 'old-draft.txt')).toHaveCount(0);

  await page.click('a[href="/trash"]');
  await expect(row(page, 'old-draft.txt')).toBeVisible();
  await page.click('button.empty-trash-btn');
  await page.click('button.btn-danger');
  await expect(page.getByText('Trash is empty')).toBeVisible();
});

test('changing the password ends the old session and the new one works', async ({ page, browser }) => {
  await signIn(page, ALICE.username, ALICE.password);

  // A second, independent session that must be revoked by the change.
  const other = await browser.newContext();
  const otherPage = await other.newPage();
  await signIn(otherPage, ALICE.username, ALICE.password);
  await page.waitForTimeout(1100); // token timestamps have one-second resolution

  await accountMenu(page, 'Settings');
  await page.fill('#currentPassword', ALICE.password);
  await page.fill('#newPassword', NEW_PASSWORD);
  await page.fill('#confirmPassword', NEW_PASSWORD);
  await page.getByRole('button', { name: 'Change password' }).click();
  await expect(page.getByText('Password changed.')).toBeVisible();

  const revoked = await otherPage.request.get('/api/v1/users/me');
  expect(revoked.status()).toBe(401);
  await other.close();

  await accountMenu(page, 'Sign out');
  await page.waitForURL(/\/login/);
  await signIn(page, ALICE.username, NEW_PASSWORD);
});

test('a new user can register and only sees their own files', async ({ page }) => {
  await page.goto('/register');
  await page.fill('#username', 'new-carol');
  await page.fill('#email', 'carol@example.com');
  await page.fill('#password', 'carols-password');
  await page.click('button[type=submit]');
  await page.waitForURL(/\/home/);

  await page.click('a[href="/my-files"]');
  await expect(page.getByText('Your silo is empty')).toBeVisible();

  const alicesFile = await page.request.get(`/api/v1/files/${seed.files.readme}/metadata`);
  expect(alicesFile.status()).toBe(404);

  await accountMenu(page, 'Settings');
  await expect(page.getByText('User', { exact: true })).toBeVisible();
});

test('the dashboard can be rearranged and keeps its tiles after signing in again', async ({ page, browser }) => {
  await signIn(page, 'new-carol', 'carols-password');
  const tiles = () => page.locator('[data-tile]').evaluateAll((els) => els.map((e) => (e as HTMLElement).dataset['tile']));
  await expect.poll(tiles).toEqual([
    'storage', 'total-files', 'added-week', 'starred-count', 'largest-type', 'starred-files', 'folders', 'recent',
  ]);

  await page.getByRole('button', { name: 'Edit dashboard' }).click();
  await page.getByRole('button', { name: 'Remove Largest file type' }).click();
  await page.locator('.add-card', { hasText: 'Recent activity' }).click();
  // Keyboard: Shift+Left makes Storage smaller, Home moves Recent activity to the front.
  await page.locator('[data-tile="storage"]').focus();
  await page.keyboard.press('Shift+ArrowLeft');
  await page.locator('[data-tile="activity"]').focus();
  await page.keyboard.press('Home');
  // A click on a tile shows the sizes it can take.
  await page.locator('[data-tile="starred-count"]').evaluate((e) => e.scrollIntoView({ block: 'center' }));
  await page.locator('[data-tile="starred-count"]').click();
  await page.getByRole('radiogroup', { name: 'Size of Starred' }).getByRole('radio', { name: /^Tall/ }).click();
  await expect(page.locator('[data-tile="starred-count"]')).toHaveAttribute('data-size', 'tall');
  await page.keyboard.press('Escape');
  // Mouse: carry Total files sideways onto its neighbour, once the tiles have slid into place.
  // (A path across other tiles would swap with those too, as it should.)
  await page.locator('[data-tile]').evaluateAll((els) =>
    // A slide cut short by the next change is cancelled, which rejects `finished`.
    Promise.all(els.flatMap((e) => e.getAnimations().map((a) => a.finished.catch(() => null)))));
  // Adding a tile scrolled the page; bring both into view, clear of the sticky header.
  await page.locator('[data-tile="total-files"]').evaluate((e) => e.scrollIntoView({ block: 'center' }));
  const from = (await page.locator('[data-tile="total-files"]').boundingBox())!;
  const to = (await page.locator('[data-tile="added-week"]').boundingBox())!;
  await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2);
  await page.mouse.down();
  await page.mouse.move(to.x + to.width / 2, to.y + to.height / 2, { steps: 20 });
  await expect(page.locator('[data-tile="total-files"]')).toHaveClass(/lifted/);
  await page.mouse.up();
  await page.getByRole('button', { name: 'Done' }).click();
  await expect(page.getByText('Dashboard saved')).toBeVisible();

  const expected = ['activity', 'storage', 'added-week', 'total-files', 'starred-count', 'starred-files', 'folders', 'recent'];
  expect(await tiles()).toEqual(expected);
  await expect(page.locator('[data-tile="activity"]')).toContainText('You created an account');

  // A fresh browser (no local copy) gets the same layout from the server.
  const other = await browser.newPage();
  await signIn(other, 'new-carol', 'carols-password');
  await expect(other.locator('[data-tile]').first()).toHaveAttribute('data-tile', 'activity');
  await expect(other.locator('[data-tile="storage"]')).toHaveAttribute('data-size', 'medium');
  await expect(other.locator('[data-tile="starred-count"]')).toHaveAttribute('data-size', 'tall');
  await other.close();

  // Reset goes back to the default and forgets the saved layout.
  await page.getByRole('button', { name: 'Edit dashboard' }).click();
  await page.getByRole('button', { name: /^Reset/ }).click();
  const dialog = page.getByRole('alertdialog', { name: 'Reset your dashboard?' });
  await dialog.getByRole('button', { name: 'Reset' }).click();
  await expect(dialog).toBeHidden();
  // Undo brings the custom layout back; redo resets it again.
  await page.getByRole('button', { name: 'Undo' }).click();
  await expect.poll(tiles).toEqual(expected);
  await page.keyboard.press('Control+y');
  await page.getByRole('button', { name: 'Done' }).click();
  // Edit mode closes once the server has the change.
  await expect(page.getByRole('heading', { name: 'Dashboard', exact: true })).toBeVisible();
  expect(await tiles()).toEqual([
    'storage', 'total-files', 'added-week', 'starred-count', 'largest-type', 'starred-files', 'folders', 'recent',
  ]);
  await expect
    .poll(async () => (await (await page.request.get('/api/v1/dashboard/layout')).json()).customized)
    .toBe(false);
});
