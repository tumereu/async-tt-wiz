-- LOB-261 (owner, 2026-11-19): answered game requests are deleted by the cleanup a while after their answer. When each was
-- answered; requests answered before this column existed count as answered now (the cleanup waits the full time for them).
ALTER TABLE game_inbox ADD COLUMN answered_at timestamptz;
UPDATE game_inbox SET answered_at = CURRENT_TIMESTAMP WHERE result IS NOT NULL;
CREATE INDEX game_inbox_answered_at ON game_inbox(answered_at) WHERE answered_at IS NOT NULL;
