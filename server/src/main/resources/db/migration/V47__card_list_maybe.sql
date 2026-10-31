-- Decks and cubes gain a Maybe section: cards being considered, never dealt, drafted or played.
-- Existing deck rows keep their Main and Side sections; existing cube rows become Main. No row is changed or removed.
ALTER TABLE deck_entry DROP CONSTRAINT deck_entry_section_check;
ALTER TABLE deck_entry ADD CONSTRAINT deck_entry_section_check CHECK (section IN ('MAINBOARD', 'SIDEBOARD', 'MAYBEBOARD'));

ALTER TABLE cube_entry ADD COLUMN section text NOT NULL DEFAULT 'MAINBOARD' CHECK (section IN ('MAINBOARD', 'MAYBEBOARD'));
ALTER TABLE cube_entry ALTER COLUMN section DROP DEFAULT;
-- A card may be in both Main and Maybe.
ALTER TABLE cube_entry DROP CONSTRAINT cube_entry_cube_id_definition_digest_key;
ALTER TABLE cube_entry ADD CONSTRAINT cube_entry_cube_id_section_definition_digest_key UNIQUE (cube_id, section, definition_digest);
