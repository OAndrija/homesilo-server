-- Each person's settings for how the app looks and behaves: theme, spacing, how
-- each list is viewed and sorted. Kept with the account so they follow it to
-- every device, and so two people using one browser don't share them.
-- A setting with no row has its default value.
CREATE TABLE user_preferences (
    user_id    UUID         NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_user_preferences PRIMARY KEY (user_id),
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE user_preference_values (
    user_id    UUID        NOT NULL,
    pref_key   VARCHAR(48) NOT NULL,
    pref_value VARCHAR(64) NOT NULL,
    CONSTRAINT pk_user_preference_values PRIMARY KEY (user_id, pref_key),
    CONSTRAINT fk_user_preference_values_preferences FOREIGN KEY (user_id)
        REFERENCES user_preferences (user_id) ON DELETE CASCADE
);
