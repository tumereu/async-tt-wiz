-- Private account state, independent of gameplay revisions and Undo.
CREATE TABLE game_chat_read_cursor (
    game_id uuid NOT NULL REFERENCES game(id),
    account_id uuid NOT NULL REFERENCES account(id),
    revision bigint NOT NULL CHECK (revision > 0),
    PRIMARY KEY (game_id, account_id),
    FOREIGN KEY (game_id, revision) REFERENCES game_chat(game_id, revision)
);
CREATE INDEX game_chat_unread ON game_chat(game_id, revision, author_id);
