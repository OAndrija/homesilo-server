-- Who did what, and when. No foreign keys on purpose: entries outlive the accounts,
-- files and folders they mention, so names are copied in at the time.
CREATE TABLE activity_log (
    id             UUID          NOT NULL,
    occurred_at    TIMESTAMP(6)  NOT NULL,
    actor_id       UUID,
    actor_username VARCHAR(255),
    action         VARCHAR(64)   NOT NULL,
    target_type    VARCHAR(16),
    target_id      UUID,
    target_name    VARCHAR(1024),
    old_value      VARCHAR(1024),
    new_value      VARCHAR(1024),
    ip_address     VARCHAR(64),
    CONSTRAINT pk_activity_log PRIMARY KEY (id)
);

CREATE INDEX idx_activity_log_occurred_at ON activity_log (occurred_at DESC);
CREATE INDEX idx_activity_log_actor ON activity_log (actor_id, occurred_at DESC);
CREATE INDEX idx_activity_log_target ON activity_log (target_id, occurred_at DESC);
