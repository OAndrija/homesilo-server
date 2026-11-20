-- Settings admins change in the app (Manage users → Settings). Environment
-- variables give the starting values; a value here takes over once an admin
-- has changed it.
CREATE TABLE server_settings (
    setting_key   VARCHAR(64)  NOT NULL,
    setting_value VARCHAR(255) NOT NULL,
    updated_at    TIMESTAMP(6) NOT NULL,
    updated_by    UUID,
    CONSTRAINT pk_server_settings PRIMARY KEY (setting_key),
    CONSTRAINT fk_server_settings_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
);
