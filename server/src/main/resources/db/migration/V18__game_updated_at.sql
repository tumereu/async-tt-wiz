ALTER TABLE game ADD COLUMN updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP;
-- Older games have no retained wall-clock update time.
UPDATE game SET updated_at = created_at;
