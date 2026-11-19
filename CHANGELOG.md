# Changelog

Notable changes to HomeSilo, newest first. The server and the web client are
released together under the same version number, so this file covers both.
Versions follow [semantic versioning](https://semver.org): a change to the
major number means something you need to read before upgrading.

## [Unreleased]

The first public release.

### What's in it

- Files and folders: upload (including files of several GB, sent in chunks
  that resume after a dropped connection), drag and drop, move, rename, star,
  search, and zip download of whole folders.
- Previews of images, video, audio, PDFs and text, with thumbnails for photos,
  PDFs and office documents.
- Trash with automatic clean-up after 30 days.
- A home dashboard you can rearrange, resize and fill with the tiles you want.
- 35 colour themes, plus settings for corners, spacing, accent, font and motion.
  Your look is saved with your account and follows you to other devices.
- Accounts: a setup code creates the first admin, then people join with invite
  links. Admins can add, disable and remove accounts, set storage quotas and
  read an activity log.

### Security

- Passwords are hashed with Argon2id. Sign-in is rate limited per account, per
  address, and per both together.
- Signing out ends the session on the server. "Sign out other devices" is in
  Settings.
- Uploaded files are served so that nothing in them can run in the app's page.
- The Docker images are scanned for known vulnerabilities on every build and release.

[Unreleased]: https://github.com/OAndrija/homesilo-server/commits/main
