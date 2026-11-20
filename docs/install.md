# Installing HomeSilo

This gets HomeSilo running on one machine at home. It takes about ten minutes,
most of it waiting for Docker to download things.

## What you need

- A 64-bit computer that stays on: an old PC or laptop, a mini PC, a NAS that
  runs Docker, or a Raspberry Pi 4 or 5. Both x86 (amd64) and ARM (arm64) work.
- 2 GB of RAM or more. The server gets 1 GB (`HOMESILO_MEMORY` in `.env`
  changes that), and the database and the system need the rest.
- Disk space for your files. A separate data disk is nicer than the system
  disk, but not required.
- Linux with [Docker Engine and the Compose plugin](https://docs.docker.com/engine/install/).
  Docker Desktop on Windows or macOS works too, for trying it out.

Check Docker is ready with `docker compose version`. You want v2.20 or newer.

## 1. Get the two files

HomeSilo runs from a compose file and a settings file. Make a folder for them:

```sh
mkdir homesilo && cd homesilo
curl -fsSLO https://raw.githubusercontent.com/OAndrija/homesilo-server/main/deploy/docker-compose.yml
curl -fsSL -o .env https://raw.githubusercontent.com/OAndrija/homesilo-server/main/deploy/.env.example
```

## 2. Set a database password

The only required setting. This fills in a random one:

```sh
sed -i "s|^DB_PASSWORD=$|DB_PASSWORD=$(openssl rand -hex 24)|" .env
```

Have a look through `.env` while you're there. Everything else is optional and
explained in the file.

## 3. Start it

```sh
docker compose up -d
```

The first start downloads the images and sets up the database, so give it a
minute. `docker compose ps` shows all three containers as `healthy` when it's
ready.

## 4. Create your admin account

A new server doesn't let just anyone become its admin. It prints a setup code
in its log, and the setup page asks for it:

```sh
docker compose logs server | grep -A3 "setup code"
```

Open `http://<this machine's address>:8080` in a browser (find the address
with `hostname -I`). You'll land on the setup page. Enter the code, pick your
username and password, and you're in.

The code changes every time the server restarts until setup is done, so if it
doesn't work, look at the log again.

## 5. Invite people

Sign-up is closed by default. To add someone, open **Users** in the sidebar,
choose **Invite someone** and send them the link. Each link works once, for the
number of days you choose. You can also create accounts directly with **Add
user**.

If you'd rather let anyone who can reach the site sign up, turn that on in
**Server**. The same page sets how much storage new accounts get and how long
the trash and the activity log are kept.

## Where everything is

Your files, the database, the database backups and the thumbnails are in
Docker volumes called `homesilo_files`, `homesilo_db`, `homesilo_backups` and
`homesilo_thumbnails`. They survive
updates and `docker compose down`, but not `docker compose down -v`, which
deletes them. Don't run that unless you mean it.

## Next

- [Reach it from outside your home](remote-access.md), if you want that.
- [Set up backups](backups.md). Do this before you put anything important in.
- [Upgrading](upgrading.md), for when a new version comes out.
