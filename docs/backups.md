# Backups

A HomeSilo backup has two parts: the **database** (accounts, folders, file
names, settings) and the **files** themselves. You need both. A database
without the files is a list of names, and the files without the database are
thousands of blobs named after their checksums.

## What HomeSilo does on its own

Every night at 02:00 the server dumps the database into the `homesilo_backups`
volume and keeps the last seven. **Manage users → Settings** shows when the
last one ran and whether it worked, and has a button to make one now. If a
nightly backup fails, it also shows up in the activity log.

That protects you from mistakes and from a damaged database. It doesn't protect
you from the disk dying, because the dumps are on the same disk. For that you
need a copy somewhere else.

You can change the schedule and how many are kept in `.env`:

```sh
BACKUP_CRON=0 0 2 * * *    # second minute hour day month weekday
BACKUP_KEEP=7
```

## Copying everything off the machine

The usual advice is three copies, on two different devices, one of them
somewhere else. For HomeSilo that means: the live data, a copy on a USB disk or
another computer, and a copy in the cloud or at a friend's place.

[restic](https://restic.net) is a good fit. It only sends what changed since
last time, encrypts everything before it leaves your machine, and can write to
a local disk, another computer over SFTP, or cloud storage like Backblaze B2.

### Once: create the backup repository

This example uses a USB disk mounted at `/mnt/backup`. Pick a long password and
store it somewhere other than this machine; without it the backup can't be read.

```sh
sudo mkdir -p /mnt/backup/homesilo /etc/homesilo
echo 'a long password you have written down elsewhere' | sudo tee /etc/homesilo/restic-password >/dev/null
sudo chmod 600 /etc/homesilo/restic-password

docker run --rm \
  -v /mnt/backup/homesilo:/repo \
  -v /etc/homesilo/restic-password:/password:ro \
  -e RESTIC_REPOSITORY=/repo -e RESTIC_PASSWORD_FILE=/password \
  restic/restic:0.18.0 init
```

### Every night: back up the files and the latest dumps

Save this as `/usr/local/bin/homesilo-backup`:

```sh
#!/bin/sh
set -e
docker run --rm \
  -v homesilo_files:/homesilo/files:ro \
  -v homesilo_backups:/homesilo/database:ro \
  -v /mnt/backup/homesilo:/repo \
  -v /etc/homesilo/restic-password:/password:ro \
  -e RESTIC_REPOSITORY=/repo -e RESTIC_PASSWORD_FILE=/password \
  restic/restic:0.18.0 backup /homesilo --host homesilo

# Keep a week of daily copies, a month of weekly ones and a year of monthly ones.
docker run --rm \
  -v /mnt/backup/homesilo:/repo \
  -v /etc/homesilo/restic-password:/password:ro \
  -e RESTIC_REPOSITORY=/repo -e RESTIC_PASSWORD_FILE=/password \
  restic/restic:0.18.0 forget --keep-daily 7 --keep-weekly 4 --keep-monthly 12 --prune
```

Make it executable (`sudo chmod +x /usr/local/bin/homesilo-backup`) and run it
every night shortly after HomeSilo's own dump, with `sudo crontab -e`:

```
30 2 * * * /usr/local/bin/homesilo-backup >> /var/log/homesilo-backup.log 2>&1
```

Running it soon after 02:00 matters a little: the database dump and the file
copy then agree on which files exist. HomeSilo's nightly clean-up runs at 03:30,
after both.

For an off-site copy, run the same script a second time with a different
`RESTIC_REPOSITORY`, for example `b2:your-bucket:homesilo` with
`B2_ACCOUNT_ID` and `B2_ACCOUNT_KEY` set. The
[restic docs](https://restic.readthedocs.io/en/stable/030_preparing_a_new_repo.html)
list every place it can write to.

## Restoring

Read this once now, so it isn't new on the day you need it. Better still, try
it on a spare machine.

1. Install HomeSilo as in [the install guide](install.md), with the same
   `.env` as before, but don't open the setup page. Then stop the app, leaving
   only the database running:

   ```sh
   docker compose up -d
   docker compose stop server client
   ```

2. Put the files back:

   ```sh
   docker run --rm \
     -v homesilo_files:/homesilo/files \
     -v homesilo_backups:/homesilo/database \
     -v /mnt/backup/homesilo:/repo \
     -v /etc/homesilo/restic-password:/password:ro \
     -e RESTIC_REPOSITORY=/repo -e RESTIC_PASSWORD_FILE=/password \
     restic/restic:0.18.0 restore latest --target /
   ```

3. Put the database back from the newest dump:

   ```sh
   docker compose cp server:/data/backups/. ./restore/
   ls ./restore                       # pick the newest homesilo-*.dump
   docker compose exec -T db dropdb -U homesilo homesilo
   docker compose exec -T db createdb -U homesilo homesilo
   docker compose exec -T db pg_restore -U homesilo -d homesilo --no-owner < ./restore/homesilo-2026-11-20-020000.dump
   ```

4. Start HomeSilo:

   ```sh
   docker compose start server client
   ```

Sign in as before. Thumbnails aren't in the backup; they're made again as you
browse. Sessions carry on too, because the signing key lives with the files.

If you only lost the database (the files volume is fine), skip step 2. If you
only lost some files, restic can restore single paths with `--include`, but
mixing an older file copy with a newer database can leave some files missing,
so restoring both from the same night is the safe choice.
