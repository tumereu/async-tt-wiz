-- Destructive owner decision (2026-11-10): request rows still holding the old model's fields (engine-responsiveness R4d)
-- are deleted, so the server reads stored plays, revisions and results strictly (GameWire.stored). A prod check found them
-- only in two cancelled games that never started (game_revision and game_inbox rows; their outbox rows already published);
-- no game_play row holds them. Rows are matched by what they hold, not by game, so every database is cleaned the same way.
-- A row goes when one of its JSON columns holds a retired field at its top level or in its "play", exactly the fields the
-- read used to leave out. An outbox row also goes with the request it answers (its foreign key). Nothing else is touched:
-- the games, their other rows and every other table stay. OldFormRowsCleanupTest runs this again on the latest schema.
CREATE FUNCTION pg_temp.holds_retired(stored text) RETURNS boolean LANGUAGE plpgsql AS $$
DECLARE
    retired constant text[] := ARRAY['currentNodeId', 'engineVersion', 'readFormat', 'currentGroup', 'annotations',
        'currentBranchId', 'mutation', 'group', 'treeDelta', 'targetNodeId'];
    doc jsonb;
BEGIN
    IF stored IS NULL THEN RETURN false; END IF;
    BEGIN
        doc := stored::jsonb;
    EXCEPTION WHEN others THEN
        RETURN false;
    END;
    IF jsonb_typeof(doc) <> 'object' THEN RETURN false; END IF;
    RETURN doc ?| retired OR (jsonb_typeof(doc -> 'play') = 'object' AND (doc -> 'play') ?| retired);
END
$$;

CREATE TEMP TABLE old_form_request ON COMMIT DROP AS
SELECT request_id FROM game_inbox WHERE pg_temp.holds_retired(command) OR pg_temp.holds_retired(result);

DELETE FROM game_outbox WHERE request_id IN (SELECT request_id FROM old_form_request)
    OR pg_temp.holds_retired(revision) OR pg_temp.holds_retired(result);
DELETE FROM game_inbox WHERE request_id IN (SELECT request_id FROM old_form_request);
DELETE FROM game_revision WHERE pg_temp.holds_retired(payload);

DROP FUNCTION pg_temp.holds_retired(text);
