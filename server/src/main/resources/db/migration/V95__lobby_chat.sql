-- LOB-271 (owner, 2026-11-22): a game's chat also before it starts, in its lobby, where it has no play
ALTER TABLE game_chat ALTER COLUMN play_id DROP NOT NULL;
