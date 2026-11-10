-- Card data from the server's catalog (owner, 2026-11-10, step 4): saved decks and cubes hold their cards by printing id,
-- the phone and the server building each card from the card catalog, instead of by the digest of card data stored with
-- them. Each row takes the printing id and name of the card data it named. Rows that then hold the same card in the same
-- section become one, the first of them keeping all their copies. A card data without a printing id keeps its name and no
-- printing; the server's startup job (SavedPrintings) finds such cards, and printings its catalog lacks, again by name.
-- The card data rows stay: games and pods keep their own copies by digest.
ALTER TABLE deck_entry ADD COLUMN printing_id text, ADD COLUMN name text;
UPDATE deck_entry e SET printing_id = d.payload::jsonb ->> 'printingId', name = d.payload::jsonb ->> 'name'
    FROM definition d WHERE d.digest = e.definition_digest;
UPDATE deck_entry e SET quantity = s.total FROM (
    SELECT deck_id, section, coalesce(printing_id, 'name:' || name) AS card, min(position) AS keeper, sum(quantity) AS total
    FROM deck_entry GROUP BY deck_id, section, coalesce(printing_id, 'name:' || name) HAVING count(*) > 1) s
    WHERE e.deck_id = s.deck_id AND e.position = s.keeper;
DELETE FROM deck_entry e USING deck_entry k
    WHERE k.deck_id = e.deck_id AND k.section = e.section AND k.position < e.position
    AND coalesce(k.printing_id, 'name:' || k.name) = coalesce(e.printing_id, 'name:' || e.name);
ALTER TABLE deck_entry DROP COLUMN definition_digest;
ALTER TABLE deck_entry ALTER COLUMN name SET NOT NULL;
CREATE UNIQUE INDEX deck_entry_card ON deck_entry (deck_id, section, coalesce(printing_id, 'name:' || name));

ALTER TABLE cube_entry ADD COLUMN printing_id text, ADD COLUMN name text;
UPDATE cube_entry e SET printing_id = d.payload::jsonb ->> 'printingId', name = d.payload::jsonb ->> 'name'
    FROM definition d WHERE d.digest = e.definition_digest;
UPDATE cube_entry e SET quantity = s.total FROM (
    SELECT cube_id, section, coalesce(printing_id, 'name:' || name) AS card, min(position) AS keeper, sum(quantity) AS total
    FROM cube_entry GROUP BY cube_id, section, coalesce(printing_id, 'name:' || name) HAVING count(*) > 1) s
    WHERE e.cube_id = s.cube_id AND e.position = s.keeper;
DELETE FROM cube_entry e USING cube_entry k
    WHERE k.cube_id = e.cube_id AND k.section = e.section AND k.position < e.position
    AND coalesce(k.printing_id, 'name:' || k.name) = coalesce(e.printing_id, 'name:' || e.name);
ALTER TABLE cube_entry DROP COLUMN definition_digest;
ALTER TABLE cube_entry ALTER COLUMN name SET NOT NULL;
CREATE UNIQUE INDEX cube_entry_card ON cube_entry (cube_id, section, coalesce(printing_id, 'name:' || name));

-- A deck's cover is one of its cards by printing id too.
ALTER TABLE saved_deck ADD COLUMN cover_printing text;
UPDATE saved_deck s SET cover_printing = d.payload::jsonb ->> 'printingId' FROM definition d WHERE d.digest = s.cover_digest;
ALTER TABLE saved_deck DROP COLUMN cover_digest;
