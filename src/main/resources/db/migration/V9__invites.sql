-- Links an admin sends to let someone create an account while sign-up is closed.
-- Only a hash of each link's secret is kept: the link itself is shown once, to
-- the admin who made it, so a copy of the database can't be used to join.
CREATE TABLE invites (
    id         UUID         NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    note       VARCHAR(80),
    created_by UUID,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at    TIMESTAMP(6),
    used_by    UUID,
    CONSTRAINT pk_invites PRIMARY KEY (id),
    CONSTRAINT uk_invites_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_invites_role CHECK (role IN ('ADMIN', 'USER')),
    CONSTRAINT fk_invites_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_invites_used_by FOREIGN KEY (used_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_invites_created_at ON invites (created_at DESC);
