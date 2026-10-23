-- Existing preparation rows are the last accepted lobby copies, not gameplay state.
-- Retain ended plays and their original outcomes; returning only changes the current pointer.
ALTER TABLE game_play ADD COLUMN ended_request_id uuid UNIQUE REFERENCES game_inbox(request_id);
CREATE TABLE game_play_mutation (
    id uuid PRIMARY KEY,
    play_id uuid NOT NULL REFERENCES game_play(id),
    request_id uuid NOT NULL UNIQUE REFERENCES game_inbox(request_id),
    payload text NOT NULL
);
