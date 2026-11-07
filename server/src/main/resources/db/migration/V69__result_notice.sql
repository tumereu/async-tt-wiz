-- TRN-065 (owner 2026-11-07): the last match's result while a player still has to tap OK on it (a ResultNotice as JSON;
-- null when nobody has to). Additive: every existing game starts with none.
ALTER TABLE game ADD COLUMN result_notice text;
