# Releasing

A release is a version tag pushed to both repositories, the server and the
[web client](https://github.com/OAndrija/homesilo-client). They always share
a version number, so `homesilo-server:1.4.0` and `homesilo-client:1.4.0` are
meant to run together.

Pushing a tag starts the Release workflow in that repository. It runs the
tests, builds the image for amd64 and arm64, scans it, publishes it to GHCR as
`1.4.0`, `1.4`, `1` and `latest`, and creates a GitHub release with the notes
from `CHANGELOG.md`. If the tests or the scan fail, nothing is published.

## Steps

Do this in each repository, server first.

1. Make sure `main` is green and everything for the release is merged.
2. Set the version:
   - server: `scripts/set-version.sh 1.4.0`
   - client: `npm version 1.4.0 --no-git-tag-version`
3. In `CHANGELOG.md`, rename `## [Unreleased]` to `## [1.4.0] - 2026-12-01`
   (today's date) and start a new, empty `## [Unreleased]` above it. Fix the
   links at the bottom.
4. Commit (`release: 1.4.0`), then tag and push:

   ```sh
   git tag -a v1.4.0 -m "HomeSilo 1.4.0"
   git push origin main v1.4.0
   ```

5. Watch the Release workflow. When both repositories are done, try the new
   images with the install compose file before announcing anything.

The workflow refuses a tag that doesn't match the version in `pom.xml` or
`package.json`, and a version with no section in the changelog.

## Which number to bump

- **Patch** (1.4.0 → 1.4.1): fixes only. Safe to upgrade without reading anything.
- **Minor** (1.4 → 1.5): new features. Upgrading needs no action, but say what's new.
- **Major** (1.x → 2.0): something an admin must do or know, like a changed
  setting or a migration that can't be undone. Write the steps at the top of
  the changelog section.

Database migrations run by themselves when the server starts, so a minor
release can include them. Never edit a migration that has been released; add a
new one.

## The first time

GHCR packages start out private. After the first release, open each package on
GitHub (Profile → Packages → homesilo-server → Package settings) and set its
visibility to public, or people won't be able to pull it.
