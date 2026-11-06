-- HIS-097, HIS-106 (owner playtest 2026-11-06): when another player's real answer branches the main line and a seat's
-- local head was on the discarded line, the local head falls back to the branch point and taken_back_from keeps where it
-- stood, so a device opening the game afterwards shows the branch (the take-back of what that player had been shown,
-- with its note) instead of a plain catch-up. Any later move of the local head clears it. Existing games keep working:
-- the column starts empty.
ALTER TABLE engine_seat ADD COLUMN taken_back_from uuid;
