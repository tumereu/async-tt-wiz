-- LOB-135 (owner 2026-11-07): the game's assume reveal settings, set by its host, outside undo. Additive: every game,
-- existing ones too, starts with the defaults (the other player's library allowed, hands not).
ALTER TABLE game ADD COLUMN assume_library_reveals boolean NOT NULL DEFAULT true;
ALTER TABLE game ADD COLUMN assume_hand_reveals boolean NOT NULL DEFAULT false;
