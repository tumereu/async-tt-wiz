-- Same request identity, game FIFO and atomic revision/outcome transaction as all game requests.
CREATE TABLE game_chat (
    id uuid PRIMARY KEY REFERENCES game_inbox(request_id),
    game_id uuid NOT NULL REFERENCES game(id),
    play_id uuid NOT NULL,
    author_id uuid NOT NULL REFERENCES account(id),
    text text NOT NULL,
    revision bigint NOT NULL CHECK (revision > 0),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (game_id, revision),
    FOREIGN KEY (game_id, play_id) REFERENCES game_play(game_id, id)
);
