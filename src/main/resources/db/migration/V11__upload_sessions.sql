-- Large files are sent in chunks (a proxy in front, like Cloudflare's, may cap a
-- single request at 100 MB), so an upload can be retried or resumed piece by
-- piece. A session lives until it's completed, cancelled or left idle for a day.
CREATE TABLE upload_sessions (
    id          UUID          NOT NULL,
    owner_id    UUID          NOT NULL,
    folder_id   UUID,
    file_name   VARCHAR(255)  NOT NULL,
    size        BIGINT        NOT NULL,
    chunk_size  INTEGER       NOT NULL,
    chunk_count INTEGER       NOT NULL,
    created_at  TIMESTAMP(6)  NOT NULL,
    updated_at  TIMESTAMP(6)  NOT NULL,
    CONSTRAINT pk_upload_sessions PRIMARY KEY (id),
    CONSTRAINT ck_upload_sessions_size CHECK (size > 0 AND chunk_size > 0 AND chunk_count > 0),
    CONSTRAINT fk_upload_sessions_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_upload_sessions_folder FOREIGN KEY (folder_id) REFERENCES folder (id) ON DELETE CASCADE
);

CREATE INDEX idx_upload_sessions_owner ON upload_sessions (owner_id);
CREATE INDEX idx_upload_sessions_updated_at ON upload_sessions (updated_at);

-- Which chunks have arrived; a chunk sent twice (a retry) is recorded once.
CREATE TABLE upload_session_chunks (
    session_id  UUID    NOT NULL,
    chunk_index INTEGER NOT NULL,
    CONSTRAINT pk_upload_session_chunks PRIMARY KEY (session_id, chunk_index),
    CONSTRAINT fk_upload_session_chunks_session FOREIGN KEY (session_id)
        REFERENCES upload_sessions (id) ON DELETE CASCADE
);
