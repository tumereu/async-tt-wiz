-- V10 is independently allocated to setup cancellation. Version gaps are intentional.
-- The existing play root and materialized board remain in game_play; ordinary edits append here.
CREATE TABLE gameplay_group (
    id uuid PRIMARY KEY,
    game_id uuid NOT NULL,
    play_id uuid NOT NULL,
    author_id uuid NOT NULL REFERENCES account(id),
    first_node_id uuid NOT NULL,
    last_node_id uuid NOT NULL,
    payload jsonb NOT NULL,
    FOREIGN KEY (game_id, play_id) REFERENCES game_play(game_id, id),
    UNIQUE (play_id, id)
);
CREATE TABLE gameplay_mutation (
    id uuid PRIMARY KEY,
    game_id uuid NOT NULL,
    play_id uuid NOT NULL,
    parent_id uuid NOT NULL,
    branch_id uuid NOT NULL,
    author_id uuid NOT NULL REFERENCES account(id),
    request_id uuid NOT NULL UNIQUE REFERENCES game_inbox(request_id),
    group_id uuid NOT NULL,
    effective_ordinal bigint NOT NULL CHECK (effective_ordinal > 0),
    node_version bigint NOT NULL DEFAULT 1 CHECK (node_version > 0),
    payload jsonb NOT NULL,
    FOREIGN KEY (game_id, play_id) REFERENCES game_play(game_id, id),
    FOREIGN KEY (play_id, group_id) REFERENCES gameplay_group(play_id, id),
    UNIQUE (play_id, id),
    UNIQUE (play_id, effective_ordinal)
);
CREATE INDEX gameplay_mutation_parent ON gameplay_mutation(play_id, parent_id);
CREATE INDEX gameplay_mutation_author ON gameplay_mutation(play_id, author_id, effective_ordinal);
