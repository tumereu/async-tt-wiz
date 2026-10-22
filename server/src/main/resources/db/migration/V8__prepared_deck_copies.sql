-- Selected decks are independent game snapshots: deliberately no saved_deck FK.
CREATE TABLE player_preparation (
    game_id uuid NOT NULL REFERENCES game(id),
    account_id uuid NOT NULL REFERENCES account(id),
    ready boolean NOT NULL DEFAULT false,
    deck text,
    PRIMARY KEY (game_id, account_id)
);
-- Immutable definitions are retained with the game, not via a saved source or catalog.
CREATE TABLE game_definition (
    game_id uuid NOT NULL REFERENCES game(id),
    digest text NOT NULL REFERENCES definition(digest),
    PRIMARY KEY (game_id, digest)
);
