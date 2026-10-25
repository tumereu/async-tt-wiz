-- Destructive owner decision: never re-execute pre-delta rules to recover a board.
-- Include inactive/ended plays and detached Redo data, not just the selected head.
CREATE TEMP TABLE nonincremental_game ON COMMIT DROP AS
WITH RECURSIVE reachable(play_id, id) AS (
    SELECT id, root_id::text FROM game_play
    UNION
    SELECT n.play_id, n.id::text FROM gameplay_mutation n
    JOIN reachable p ON p.play_id = n.play_id AND n.payload ->> 'parentId' = p.id
    WHERE n.retained
), events AS (
    SELECT p.game_id, n.payload FROM gameplay_mutation n JOIN game_play p ON p.id = n.play_id WHERE n.retained
    UNION ALL
    SELECT p.game_id, entry -> 'mutation' FROM gameplay_tree_control c JOIN game_play p ON p.id = c.play_id
    CROSS JOIN LATERAL jsonb_array_elements(c.payload -> 'entries') entry WHERE c.kind = 'redo'
)
SELECT DISTINCT game_id FROM events WHERE
    COALESCE(payload ->> 'schemaVersion', '1') <> '2'
    OR jsonb_typeof(payload #> '{transition,delta}') IS DISTINCT FROM 'object'
    OR COALESCE(payload #>> '{transition,delta,seal}', '') = ''
    OR jsonb_typeof(payload -> 'bookkeeping') IS DISTINCT FROM 'object'
    OR EXISTS (SELECT 1 FROM jsonb_array_elements(payload #> '{transition,delta,entities}') e WHERE e ->> 'kind' = 'annotations')
UNION
SELECT p.game_id FROM gameplay_mutation n JOIN game_play p ON p.id = n.play_id
WHERE n.retained AND NOT EXISTS (SELECT 1 FROM reachable r WHERE r.play_id = n.play_id AND r.id = n.id::text);

-- None of the game/play FKs cascade. Break the immediate current-play cycle first; the
-- invitation cycle is DEFERRABLE INITIALLY DEFERRED and closes when its game is deleted.
UPDATE game SET current_play = NULL WHERE id IN (SELECT game_id FROM nonincremental_game);
DELETE FROM activity_push WHERE game_id IN (SELECT game_id FROM nonincremental_game);
DELETE FROM activity_summary WHERE game_id IN (SELECT game_id FROM nonincremental_game);
DELETE FROM account_activity WHERE game_id IN (SELECT game_id FROM nonincremental_game);
DELETE FROM game_chat WHERE game_id IN (SELECT game_id FROM nonincremental_game);
DELETE FROM game_play_mutation WHERE play_id IN (SELECT id FROM game_play WHERE game_id IN (SELECT game_id FROM nonincremental_game));
DELETE FROM gameplay_tree_control WHERE play_id IN (SELECT id FROM game_play WHERE game_id IN (SELECT game_id FROM nonincremental_game));
DELETE FROM gameplay_mutation WHERE game_id IN (SELECT game_id FROM nonincremental_game);
DELETE FROM gameplay_group WHERE game_id IN (SELECT game_id FROM nonincremental_game);
DELETE FROM game_play WHERE game_id IN (SELECT game_id FROM nonincremental_game);
DELETE FROM game_outbox WHERE game_id IN (SELECT game_id FROM nonincremental_game);
DELETE FROM game_revision WHERE game_id IN (SELECT game_id FROM nonincremental_game);
DELETE FROM game_stream WHERE game_id IN (SELECT game_id FROM nonincremental_game);
DELETE FROM game_inbox WHERE game_id IN (SELECT game_id FROM nonincremental_game);
DELETE FROM player_preparation WHERE game_id IN (SELECT game_id FROM nonincremental_game);
DELETE FROM game_definition WHERE game_id IN (SELECT game_id FROM nonincremental_game);
DELETE FROM invitation WHERE game_id IN (SELECT game_id FROM nonincremental_game);
DELETE FROM game WHERE id IN (SELECT game_id FROM nonincremental_game);
