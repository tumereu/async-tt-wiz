-- The existing mutation table is the sole retained topology. Removed nodes stay detached for
-- original outcomes/Redo, not as an alternative branch. game_play.payload is a rebuildable endpoint.
ALTER TABLE game_play ADD COLUMN initial_payload text;
UPDATE game_play p SET initial_payload = COALESCE(
    (SELECT (r.payload::jsonb -> 'play')::text FROM game_revision r
     WHERE r.game_id = p.game_id AND r.payload::jsonb #>> '{play,id}' = p.id::text
       AND r.payload::jsonb #>> '{play,currentNodeId}' = p.root_id::text ORDER BY r.revision LIMIT 1),
    CASE WHEN p.payload::jsonb ->> 'currentNodeId' = p.root_id::text THEN p.payload END);
ALTER TABLE game_play ALTER COLUMN initial_payload SET NOT NULL;
ALTER TABLE gameplay_mutation ADD COLUMN retained boolean NOT NULL DEFAULT true;
CREATE TABLE gameplay_tree_control (
    play_id uuid NOT NULL REFERENCES game_play(id),
    kind text NOT NULL,
    id text NOT NULL,
    payload jsonb NOT NULL,
    PRIMARY KEY (play_id, kind, id)
);
-- Existing engines have one original continuation. Seed its explicitly recorded head; no subtree
-- inference is used once branched runtime controls exist.
INSERT INTO gameplay_tree_control(play_id, kind, id, payload)
SELECT id, 'current', 'current', payload::jsonb -> 'currentNodeId' FROM game_play;
INSERT INTO gameplay_tree_control(play_id, kind, id, payload)
SELECT id, 'selected-branch', 'current', payload::jsonb #> '{root,branchId}' FROM game_play;
INSERT INTO gameplay_tree_control(play_id, kind, id, payload)
SELECT id, 'branch-head', payload::jsonb #>> '{root,branchId}', payload::jsonb -> 'currentNodeId' FROM game_play;
INSERT INTO gameplay_tree_control(play_id, kind, id, payload)
SELECT id, 'branch', payload::jsonb #>> '{root,branchId}',
    jsonb_build_object('id', payload::jsonb #>> '{root,branchId}') FROM game_play;
