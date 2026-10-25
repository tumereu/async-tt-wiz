-- Endpoint records and reverse edges are maintained with each accepted command, not rebuilt at
-- startup. Older plays require the explicit checkpoint maintenance command once after migration.
ALTER TABLE game_play ADD COLUMN checkpoint_version integer NOT NULL DEFAULT 0;
ALTER TABLE game_play ADD COLUMN checkpoint_revision bigint NOT NULL DEFAULT 0;
ALTER TABLE game_play ADD COLUMN checkpoint_digest text;
CREATE TABLE gameplay_endpoint_record (
    play_id uuid NOT NULL REFERENCES game_play(id),
    kind text NOT NULL,
    id text NOT NULL,
    payload jsonb NOT NULL,
    PRIMARY KEY (play_id, kind, id)
);
CREATE TABLE gameplay_endpoint_edge (
    play_id uuid NOT NULL REFERENCES game_play(id),
    kind text NOT NULL,
    source_id text NOT NULL,
    target_id text NOT NULL,
    PRIMARY KEY (play_id, kind, source_id, target_id)
);
CREATE INDEX gameplay_mutation_retained_page ON gameplay_mutation(play_id, effective_ordinal) WHERE retained;
