-- Schema as it existed when HomeSilo moved from Hibernate `ddl-auto=update` to Flyway.
-- Every statement is idempotent so this also runs cleanly against a database that
-- Hibernate already created (Flyway baselines those at version 0, see application.properties).

CREATE TABLE IF NOT EXISTS users (
    id                  UUID         NOT NULL,
    username            VARCHAR(255) NOT NULL,
    password            VARCHAR(255) NOT NULL,
    email               VARCHAR(255) NOT NULL,
    created_at          TIMESTAMP(6) NOT NULL,
    role                VARCHAR(255) NOT NULL,
    enabled             BOOLEAN      NOT NULL,
    storage_quota_bytes BIGINT       NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'USER'))
);

CREATE TABLE IF NOT EXISTS folder (
    id            UUID         NOT NULL,
    name          VARCHAR(255) NOT NULL,
    owner_id      UUID         NOT NULL,
    parent_id     UUID,
    trashed       BOOLEAN      NOT NULL,
    starred       BOOLEAN      NOT NULL,
    trashed_at    TIMESTAMP(6),
    created_at    TIMESTAMP(6) NOT NULL,
    last_modified TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_folder PRIMARY KEY (id),
    CONSTRAINT fk_folder_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fk_folder_parent FOREIGN KEY (parent_id) REFERENCES folder (id)
);

CREATE TABLE IF NOT EXISTS file_metadata (
    id                 UUID         NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name   VARCHAR(255) NOT NULL,
    content_type       VARCHAR(255) NOT NULL,
    size               BIGINT       NOT NULL,
    trashed            BOOLEAN      NOT NULL,
    starred            BOOLEAN      NOT NULL,
    trashed_at         TIMESTAMP(6),
    owner_id           UUID         NOT NULL,
    folder_id          UUID,
    last_modified      TIMESTAMP(6) NOT NULL,
    uploaded_at        TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_file_metadata PRIMARY KEY (id),
    CONSTRAINT fk_file_metadata_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fk_file_metadata_folder FOREIGN KEY (folder_id) REFERENCES folder (id),
    CONSTRAINT uq_file_metadata_owner_stored_filename UNIQUE (owner_id, stored_file_name)
);

CREATE INDEX IF NOT EXISTS idx_folder_owner_id ON folder (owner_id);
CREATE INDEX IF NOT EXISTS idx_folder_parent_id ON folder (parent_id);
CREATE INDEX IF NOT EXISTS idx_file_metadata_owner_id ON file_metadata (owner_id);
CREATE INDEX IF NOT EXISTS idx_file_metadata_trashed_at ON file_metadata (trashed_at);
CREATE INDEX IF NOT EXISTS idx_file_metadata_folder_id ON file_metadata (folder_id);
