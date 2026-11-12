-- A tile can come in more than one look (the storage tile as a silo, a ring or
-- bars). NULL means the tile's default look.
ALTER TABLE dashboard_layout_tiles ADD COLUMN tile_variant VARCHAR(20);
