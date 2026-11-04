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
  return page.locator('tr', { has: page.locator('td.col-name', { hasText: name }) });
}

/** Opens a row's ⋯ menu and picks an item from it. */
async function rowAction(page: Page, name: string, action: string) {
  await row(page, name).getByRole('button', { name: `Actions for ${name}` }).click();
  await page.getByRole('menuitem', { name: action, exact: true }).click();
}

/** Settings and Sign out live in the account menu at the bottom of the sidebar. */
async function accountMenu(page: Page, item: 'Settings' | 'Sign out') {
  await page.click('button.account-trigger');
  await page.getByRole('menuitem', { name: item }).click();
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
  await expect(page.getByText('Administrator')).toBeVisible();
});

test('files and folders from the old version are all there and downloadable', async ({ page }) => {
  await signIn(page, ALICE.username, ALICE.password);
  await page.click('a[href="/my-files"]');

  await expect(row(page, 'Photos')).toBeVisible();
  await expect(row(page, 'readme.txt')).toBeVisible();

  // Native browser download through nginx, streamed by the API.
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    rowAction(page, 'readme.txt', 'Download'),
  ]);
  expect(download.suggestedFilename()).toBe('readme.txt');
  expect(readFileSync((await download.path())!, 'utf8')).toBe('hello from the old version');

  await row(page, 'Photos').dblclick();
  await expect(row(page, 'beach.png')).toBeVisible();
  await expect(row(page, '2024')).toBeVisible();
  await row(page, '2024').dblclick();
  await expect(row(page, 'notes.md')).toBeVisible();
  await expect(page.locator('nav.breadcrumb')).toContainText('Photos');

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

  await rowAction(page, 'after-upgrade.txt', 'Rename');
  const input = page.locator('#folderNameInput');
  await expect(input).toBeFocused();
  await page.keyboard.type('renamed');
  await page.click('button.btn-confirm');
  await expect(row(page, 'renamed.txt')).toBeVisible();

  await page.click('a[href="/my-files"]');
  const [zip] = await Promise.all([
    page.waitForEvent('download'),
    rowAction(page, 'Photos', 'Download as zip'),
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

  await rowAction(page, 'old-draft.txt', 'Restore');
  await expect(row(page, 'old-draft.txt')).toHaveCount(0);

  await page.click('a[href="/my-files"]');
  await rowAction(page, 'old-draft.txt', 'Move to trash');
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
  await expect(page.getByText('No files yet')).toBeVisible();

  const alicesFile = await page.request.get(`/api/v1/files/${seed.files.readme}/metadata`);
  expect(alicesFile.status()).toBe(404);

  await accountMenu(page, 'Settings');
  await expect(page.getByText('User', { exact: true })).toBeVisible();
});
