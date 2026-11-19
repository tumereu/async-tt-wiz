-- LOB-135, N15 (owner 2026-11-19): the game setting "Undo past the other player's passes needs a request", set by its
-- host, outside undo. Additive: every game, existing ones too, starts with it off (today's rule, HIS-202).
ALTER TABLE game ADD COLUMN undo_past_passes boolean NOT NULL DEFAULT false;
