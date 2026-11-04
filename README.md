# HomeSilo — server

Self-hosted personal cloud storage: a REST API in Spring Boot 4 / Java 21 backed by
PostgreSQL and a content-addressed blob store on local disk. The web UI lives in
[homesilo-client](https://github.com/OAndrija/homesilo-client).

## Features

- Files and nested folders: upload, download, preview, rename, move, star, search
- Trash with cascading trash/restore, empty-trash, and automatic purge after N days
- Zip downloads of any selection, folders included, streamed from disk
- Per-user storage quotas (trash counts, as on Google Drive) and a usage dashboard
- Content-addressed storage: identical bytes are stored once per user, however many
  folders they appear in; blobs are garbage-collected after the last reference goes
- Multi-user with roles; the first account becomes the administrator
- HttpOnly-cookie sessions, CSRF protection, login throttling, session revocation on
  password change

## Quick start (Docker)

Check out both repositories side by side:

```
homesilo/
├── homesilo-server/   ← this repo
└── homesilo-client/
```

```bash
cd homesilo-server
cp .env.example .env          # set DB_PASSWORD and JWT_SECRET (openssl rand -base64 48)
docker compose up -d --build
```

Open http://localhost:8080 and register. The first account is the admin; set
`REGISTRATION_ENABLED=false` afterwards if you don't want anyone else to sign up.

Data lives in two Docker volumes: `db-data` (PostgreSQL) and `storage` (file blobs).
Back up both together.

## Local development

Requirements: JDK 21 and a PostgreSQL database.

```bash
export DB_URL=jdbc:postgresql://localhost:5432/homesilo DB_USERNAME=homesilo DB_PASSWORD=...
export JWT_SECRET=$(openssl rand -base64 48)
./mvnw spring-boot:run
```

Run the client with `npm start` in `homesilo-client`; its dev server proxies `/api`
to `localhost:8080`, so the browser sees a single origin.

```bash
./mvnw verify    # unit + integration tests (H2 in PostgreSQL mode, real Flyway migrations)
```

## Configuration

All settings are environment variables (mapped in `application.properties`).

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/homesilo`, `homesilo`, — | PostgreSQL connection |
| `JWT_SECRET` | — (required) | Session signing key, at least 32 characters |
| `JWT_EXPIRATION` | `7d` | Session lifetime (`12h`, `7d`, or milliseconds) |
| `COOKIE_SECURE` | `false` | Set `true` when served over HTTPS |
| `REGISTRATION_ENABLED` | `true` | Allow self-registration after the first (admin) account |
| `STORAGE_BASE_PATH` | `./data/storage` | Where blobs are stored |
| `DEFAULT_QUOTA` | `100GB` | Quota for new accounts |
| `MAX_UPLOAD_SIZE` | `5GB` | Largest single upload |
| `TRASH_RETENTION_DAYS` | `30` | Purge trash older than this; `0` keeps it forever |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Only needed if the UI is on another origin |

## Architecture

Packages are organised by feature, each owning its entity, repository, service,
controller and DTOs:

| Package | Responsibility |
|---|---|
| `auth` | Register / login / logout, JWT issuing, session cookie, login throttling |
| `security` | Filter chain, cookie/bearer authentication, CSRF, JSON 401/403 |
| `user` | Accounts, password change, admin user management |
| `file` | File entries, upload/download/preview, zip archives |
| `folder` | Folder tree (adjacency list), breadcrumbs, cascading trash/restore |
| `trash` | Permanent deletion, empty trash, retention purge |
| `storage` | Blob store, after-commit blob garbage collection, orphan sweep |
| `dashboard` | Usage statistics |
| `common` | Typed configuration, error handling (RFC 9457), paging, scheduled jobs |

**Storage.** An upload streams to a temp file while being hashed (SHA-256), then is
atomically renamed to `<base>/<ownerId>/<sha256>`, so nothing is ever buffered in
memory and a crash never leaves a half-written blob. File rows reference blobs by
hash. When rows are deleted, a listener removes now-unreferenced blobs *after* the
transaction commits; a nightly sweep catches anything missed.

**Sessions.** The JWT lives in an `HttpOnly; SameSite=Lax` cookie the page can't
read. State-changing requests need the `X-XSRF-TOKEN` header (Angular sends it
automatically). Each request reloads the user by id, so disabling an account or
changing a password takes effect immediately. Scripts can instead send
`Authorization: Bearer <token>`.

**Previews.** Media and PDFs are served inline; anything script-capable (HTML,
SVG, XML, JS) is served as `text/plain`, and every preview carries a sandboxing
`Content-Security-Policy`, so an uploaded file can never run code in HomeSilo's origin.

**Errors.** Every error is `application/problem+json` with a human-readable `detail`
(and per-field `errors` for validation failures).

## API overview

All endpoints are under `/api/v1`; everything except `auth/*` and `/actuator/health`
requires a session.

| | |
|---|---|
| `auth` | `GET config`, `POST register`, `POST login`, `POST logout` |
| `users` | `GET me`, `PUT me/password`; admin: `GET /`, `GET {id}`, `PATCH {id}` |
| `files` | `POST upload`, `GET` (list), `recent`, `starred`, `trash`, `search`, `starred/search`, `trash/search`, `GET {id}/metadata`, `{id}/download`, `{id}/preview`, `GET download-zip?fileIds=&folderIds=`, `PATCH {id}/rename`, `move`, `star`, `trash`, `restore`, `DELETE {id}` |
| `folders` | `GET root`, `trash`, `search`, `{id}`, `{id}/contents`, `POST /`, `PATCH {id}/rename`, `move`, `trash`, `restore`, `DELETE {id}` |
| `trash` | `DELETE /` (empty trash) |
| `dashboard` | `GET stats` |

## Upgrading an existing database

The schema used to be managed by `ddl-auto=update`; it is now owned by Flyway
(`src/main/resources/db/migration`) and Hibernate only validates it. On first start,
an existing database is baselined at version 0 and the idempotent `V1` leaves your
tables untouched; `V2` then drops the old one-blob-per-file unique constraint and adds
`users.password_changed_at`. Existing files keep working because their storage layout
(`<ownerId>/<sha256>`) is unchanged. Take a backup first all the same.
