-- Each person's dashboard: which tiles they show, in what order and at what size.
-- No row means they never changed it, and the app shows its default layout.
CREATE TABLE dashboard_layouts (
    user_id    UUID         NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_dashboard_layouts PRIMARY KEY (user_id),
    CONSTRAINT fk_dashboard_layouts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE dashboard_layout_tiles (
    user_id   UUID        NOT NULL,
    position  INTEGER     NOT NULL,
    tile_id   VARCHAR(40) NOT NULL,
    tile_size VARCHAR(16) NOT NULL,
    CONSTRAINT pk_dashboard_layout_tiles PRIMARY KEY (user_id, position),
    CONSTRAINT fk_dashboard_layout_tiles_layout FOREIGN KEY (user_id)
        REFERENCES dashboard_layouts (user_id) ON DELETE CASCADE
);
