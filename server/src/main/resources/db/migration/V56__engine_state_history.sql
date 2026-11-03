-- I-D54 phase 2 (engine state as the game state): a play's history as engine events, stored beside today's model until
-- the server switches to it (phase 3). Nothing writes these tables yet.
-- engine_play: one row per play, with the engine card definitions of its decks stored once (by digest) and the root
-- event id (the state before the first event is the root's checkpoint).
CREATE TABLE engine_play (
    play_id uuid PRIMARY KEY REFERENCES game_play(id),
    root_id uuid NOT NULL,
    definitions text NOT NULL
);

-- engine_event: the history tree. body is the event (engine part, table part) in the engine's own format with card
-- definitions by reference; depth counts from the root (0); ordinal is the order the server accepted events in.
CREATE TABLE engine_event (
    play_id uuid NOT NULL REFERENCES engine_play(play_id),
    id uuid NOT NULL,
    parent_id uuid NOT NULL,
    depth integer NOT NULL CHECK (depth > 0),
    ordinal bigint NOT NULL,
    author_id uuid NOT NULL,
    body text NOT NULL,
    PRIMARY KEY (play_id, id),
    UNIQUE (play_id, ordinal)
);
CREATE INDEX engine_event_children ON engine_event (play_id, parent_id);

-- engine_checkpoint: the whole state (engine state and table state) at the root and at every event whose depth is a
-- multiple of the checkpoint interval, so a state is one checkpoint plus a bounded number of events (never the root
-- replayed: games reach 10k+ events).
CREATE TABLE engine_checkpoint (
    play_id uuid NOT NULL REFERENCES engine_play(play_id),
    event_id uuid NOT NULL,
    state text NOT NULL,
    PRIMARY KEY (play_id, event_id)
);
