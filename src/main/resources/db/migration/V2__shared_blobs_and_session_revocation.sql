-- Blobs are now reference-counted: several file entries (different folders or
-- names) may point at the same stored content, so the per-owner uniqueness on
-- stored_file_name goes away and becomes a plain lookup index.
ALTER TABLE file_metadata DROP CONSTRAINT IF EXISTS uq_file_metadata_owner_stored_filename;
CREATE INDEX IF NOT EXISTS idx_file_metadata_owner_stored_file ON file_metadata (owner_id, stored_file_name);

-- Supports trash retention purges.
CREATE INDEX IF NOT EXISTS idx_folder_trashed_at ON folder (trashed_at);

-- Sessions issued before a password change are rejected.
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP(6);
