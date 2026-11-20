# HomeSilo

HomeSilo is a file storage service you run on your own computer: the everyday
parts of Google Drive, for you and the people you live with. Everyone gets their
own space with a storage limit. You can upload from the browser, sort things into
folders, preview photos, videos and PDFs, and get anything back from the trash for
a month. Your files stay on your hardware, and nobody outside your network can
reach them unless you set that up.

This repository holds the server and the install files, so start here. The web
interface lives in [homesilo-client](https://github.com/OAndrija/homesilo-client).
Both are published as Docker images for amd64 and arm64, so an old PC, a mini PC
or a Raspberry Pi 4 or 5 will run them.

## Install

You need a machine with Docker and the Compose plugin. In an empty folder:

```sh
curl -fsSLO https://raw.githubusercontent.com/OAndrija/homesilo-server/main/deploy/docker-compose.yml
curl -fsSL -o .env https://raw.githubusercontent.com/OAndrija/homesilo-server/main/deploy/.env.example
sed -i "s|^DB_PASSWORD=$|DB_PASSWORD=$(openssl rand -hex 24)|" .env
docker compose up -d
docker compose logs server | grep -A3 "setup code"
```

Open port 8080 on that machine in a browser, enter the setup code from the log
and create your admin account. From there you invite people with one-time links.
Sign-up is closed until you open it.

The [install guide](docs/install.md) goes through the same steps more slowly.
After that:

- [Reaching it from outside your home](docs/remote-access.md): Tailscale,
  Cloudflare Tunnel, or Caddy with your own domain.
- [Backups](docs/backups.md): what HomeSilo does by itself, and how to keep a
  copy somewhere else. Read this before you put anything important in.
- [Upgrading](docs/upgrading.md).

## What it does

- Folders, drag-and-drop uploads, moving, renaming, starring and search.
- Large uploads go up in 32 MB pieces. If the connection drops, the upload picks
  up where it stopped instead of starting over.
- Previews for images, video, audio, PDFs and text. The grid shows thumbnails.
- Downloads of a whole selection as one zip, folders included.
- A trash that empties itself after 30 days (or never, if you prefer).
- Storage limits per person. Identical files are stored once per person, however
  many folders they're in.
- An admin area for accounts, invite links, server settings and an activity log.
- Nightly database backups with a restore guide.

## Configuration

Most people only ever set `DB_PASSWORD`. The rest has sensible defaults and is
described in [`deploy/.env.example`](deploy/.env.example). Sign-up, storage for
new accounts and how long the trash and the activity log are kept can also be
changed on the **Server** page, which then takes priority over these values.

| Variable | Default | What it does |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/homesilo`, `homesilo`, none | The PostgreSQL connection |
| `JWT_SECRET` | generated | Key for signing sessions, 32 characters or more. Leave it unset and one is made on first start and kept in `<storage>/.secrets/` |
| `JWT_EXPIRATION` | `7d` | How long a sign-in lasts (`12h`, `7d`, or milliseconds) |
| `COOKIE_SECURE` | automatic | Whether the session cookie needs HTTPS. Unset, it follows the request (directly or via `X-Forwarded-Proto`) |
| `SETUP_CODE` | random, in the log | The code that creates the first admin. Set your own (12 or more letters or digits) for an unattended install |
| `REGISTRATION_ENABLED` | `false` | Let anyone create an account. When off, people join with an invite |
| `DEFAULT_QUOTA` | `100GB` | Storage for each new account |
| `MAX_UPLOAD_SIZE` | `5GB` | The largest single file. Set the same value on the client container |
| `TRASH_RETENTION_DAYS` | `30` | Days before the trash empties itself; `0` keeps it |
| `ACTIVITY_RETENTION_DAYS` | `90` | Days the activity log is kept; `0` keeps it forever |
| `BACKUP_ENABLED` | `true` | Nightly database dumps |
| `BACKUP_CRON` | `0 0 2 * * *` | When they run (seconds first, Spring style) |
| `BACKUP_KEEP` | `7` | How many dumps to keep |
| `BACKUP_PATH` | `./data/backups` | Where they go |
| `STORAGE_BASE_PATH` | `./data/storage` | Where files are stored |
| `THUMBNAIL_PATH` | `./data/thumbnails` | Thumbnail cache. Safe to delete; they're made again |
| `CORS_ALLOWED_ORIGINS` | none | Only if the UI is served from a different origin than the API |

The client container has a few of its own (`API_UPSTREAM`, `MAX_UPLOAD_SIZE`,
`TRUSTED_PROXIES`, `REAL_IP_HEADER`); see its README.

## Building from source

The `docker-compose.yml` in this folder builds both images from source. Check out
the two repositories next to each other:

```
homesilo/
├── homesilo-server/
└── homesilo-client/
```

```sh
cd homesilo-server
cp .env.example .env     # set DB_PASSWORD
docker compose up -d --build
```

To work on the server itself you need JDK 21 and Docker:

```sh
./mvnw spring-boot:test-run   # the server, with a throwaway PostgreSQL in Docker
./mvnw verify                 # all tests
```

`spring-boot:run` works too if you have your own PostgreSQL; point `DB_URL`,
`DB_USERNAME` and `DB_PASSWORD` at it. For the UI, run `npm start` in
`homesilo-client`. Its dev server forwards `/api` to `localhost:8080`, so the
browser sees one origin, the same as in production.

`verify` runs unit tests, integration tests against PostgreSQL 17 (Testcontainers
starts and removes it), and the Gherkin scenarios in `src/test/resources/features`,
which describe accounts, files, the trash and storage limits in plain language.
The Cucumber report ends up in `target/cucumber-report.html`.

The `e2e` folder has the browser tests. CI runs them against the full Docker
stack in two ways: upgrading a database made by an old version, and a fresh
install from `deploy/` done the way the install guide describes.

Releases are covered in [RELEASING.md](RELEASING.md) and the
[changelog](CHANGELOG.md).

## How it works

The code is grouped by feature. Each package has its own entities, repository,
service, controller and DTOs: `auth`, `user`, `invite`, `file`, `folder`,
`upload`, `trash`, `storage`, `thumbnail`, `dashboard`, `activity`, `settings`,
`backup`, `admin`, plus `security` for the filter chain and `common` for
configuration, error handling and scheduled jobs.

**Files on disk.** An upload is streamed to a temporary file and hashed on the
way, then renamed to `<storage>/<ownerId>/<sha256>`. Nothing is held in memory,
and a crash can't leave half a file under a real name. Database rows point at
blobs by hash. Once the last row pointing at a blob is deleted, the blob is
removed after the transaction commits, and a nightly sweep catches anything that
slipped through.

**Chunked uploads.** Files over 32 MB go through an upload session: each chunk
is a separate request, written straight into place in a part file, and the
session remembers which chunks it has. The client asks for that list to resume.
Sessions left untouched for a day are cleared out overnight.

**Sessions.** A JWT in an `HttpOnly; SameSite=Lax` cookie that page scripts
can't read. Requests that change something need the `X-XSRF-TOKEN` header, and
requests from other sites are refused by `Sec-Fetch-Site` or `Origin`. Every
token has an id, so signing out, changing a password or "sign out everywhere"
ends sessions on the spot. Passwords are hashed with Argon2id; old bcrypt hashes
are upgraded the next time their owner signs in. Sign-in attempts are limited
per address and per account.

**Previews.** Media and PDFs are served inline. Anything that could run script
(HTML, SVG, XML, JavaScript) is served as plain text, and every preview has a
sandboxing Content-Security-Policy, so an uploaded file can't run code on
HomeSilo's origin.

**Errors.** Every error is `application/problem+json` with a `detail` a person
can read, and per-field `errors` when validation fails.

## API

Everything is under `/api/v1`. Apart from `auth/*` and `/actuator/health`,
every endpoint needs a session.

| Area | Endpoints |
|---|---|
| `auth` | `GET config`, `POST setup`, `POST register`, `POST login`, `POST logout`, `POST invites/preview` |
| `users/me` | `GET`, `PUT password`, `POST sessions/sign-out-others`, `GET`/`PATCH preferences`, `GET activity` |
| `files` | list, `recent`, `photos`, `starred`, `trash`, `search` (also under `starred/` and `trash/`), `POST upload`, `GET {id}/metadata`, `download`, `preview`, `thumbnail`, `GET download-zip?fileIds=&folderIds=`, `PATCH {id}/rename`, `move`, `star`, `trash`, `restore`, `DELETE {id}` |
| `uploads` | `POST` (start), `GET {id}`, `PUT {id}/chunks/{n}`, `POST {id}/complete`, `DELETE {id}` |
| `folders` | `GET root`, `trash`, `starred`, `search`, `{id}`, `{id}/summary`, `{id}/contents`, `POST`, `PATCH {id}/rename`, `move`, `star`, `trash`, `restore`, `DELETE {id}` |
| `trash` | `DELETE` (empty it) |
| `dashboard` | `GET stats`, `GET`/`PUT`/`DELETE layout` |
| `admin` | `GET overview`, `users` (`GET`, `POST`, `PATCH`, `DELETE`, `PUT {id}/password`), `invites` (`GET`, `POST`, `DELETE`), `GET`/`PATCH settings`, `GET`/`POST backups`, `GET activity` |

## Licence

HomeSilo is free software under the [GNU AGPL v3](LICENSE). You can use it,
change it and share it. If you run a changed version for other people over a
network, you have to offer them your source as well; the Settings page links to
the source for that reason, so keep a link like it in a fork.
