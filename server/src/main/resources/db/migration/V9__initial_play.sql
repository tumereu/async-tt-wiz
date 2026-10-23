-- Lobby choice is explicit; Random is JSON {"accountId":null}, not a sampled result.
ALTER TABLE game ADD COLUMN starter_choice text;
ALTER TABLE game ADD COLUMN current_play uuid;
CREATE TABLE game_play (
    id uuid PRIMARY KEY,
    game_id uuid NOT NULL REFERENCES game(id),
    preparation_round uuid NOT NULL UNIQUE,
    start_request_id uuid NOT NULL UNIQUE REFERENCES game_inbox(request_id),
    root_id uuid NOT NULL UNIQUE,
    payload text NOT NULL,
    UNIQUE (game_id, id)
);
ALTER TABLE game ADD CONSTRAINT game_current_play_fk FOREIGN KEY (id, current_play) REFERENCES game_play(game_id, id);
