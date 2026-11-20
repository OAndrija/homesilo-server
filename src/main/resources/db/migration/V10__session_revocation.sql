-- Signing out ends that session on the server, not just in the browser: the
-- session's id is kept here until it would have expired anyway, and requests
-- carrying it are refused. Rows past expires_at are removed nightly.
CREATE TABLE revoked_sessions (
    token_id   UUID         NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_revoked_sessions PRIMARY KEY (token_id)
);

CREATE INDEX idx_revoked_sessions_expires_at ON revoked_sessions (expires_at);

-- "Sign out of other devices": sessions started before this are refused.
ALTER TABLE users ADD COLUMN sessions_revoked_at TIMESTAMP(6);
