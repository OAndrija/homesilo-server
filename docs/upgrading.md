# Upgrading

New versions are announced on the
[releases page](https://github.com/OAndrija/homesilo-server/releases). Each one
lists what changed in the [changelog](../CHANGELOG.md).

## The usual way

```sh
cd homesilo
docker compose pull
docker compose up -d
```

That's all. Database changes run by themselves when the server starts, and
your files aren't touched. HomeSilo is unavailable for the half a minute or so
it takes to restart.

If you use an overlay (Cloudflare, Caddy), include it in both commands, or set
`COMPOSE_FILE` in `.env` as described in [remote access](remote-access.md).

## Which updates you get

`HOMESILO_VERSION` in `.env` decides:

- `1` (the default) follows every 1.x release: fixes and new features, but
  never a release that needs you to do something first.
- `1.4` gets fixes to 1.4 only.
- `1.4.2` stays on exactly that version until you change it.

A new major version (2.0) always comes with instructions at the top of its
changelog entry. Read them before changing `HOMESILO_VERSION` to `2`.

## Before a big upgrade

Make a backup first (see [backups](backups.md)). Database changes go one way:
an older version can't run on a database a newer one has updated, so the way
back is restoring the backup.

## If something goes wrong

`docker compose logs server` usually says why. If the server won't start after
an upgrade, set `HOMESILO_VERSION` back to the version you had, restore the
backup you made before upgrading, and
[open an issue](https://github.com/OAndrija/homesilo-server/issues) with the log.

## Coming from a build before 1.0

Before 1.0 there were no published images, and the database schema was managed
by Hibernate instead of migrations. A database from that time upgrades by itself
too: on the first start, Flyway takes the existing tables as its starting point,
leaves them as they are, and applies everything since. Files keep working
because they're stored the same way (`<ownerId>/<sha256>`). CI checks this on
every change, starting from a database made by the old server.

Two things to do by hand:

- `JWT_SECRET` is no longer required. You can keep yours; if you remove it, a
  new one is generated and everyone signs in once more.
- `COOKIE_SECURE` now works itself out. Remove it unless you need to force it.
