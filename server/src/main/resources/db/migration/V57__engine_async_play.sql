-- I-D56 step A (async play on engine state, still behind the I-D54 seam: nothing writes these for today's plays).
-- engine_play.lines: the play's line counter (I-D56 §9): line 0 starts at the game's start, each branch or rewind takes
-- the next number, never reused. The game's random secret lives in its state (the root checkpoint's table state).
-- rewind_*: the open undo request (§10), if any: who asks, the target event, their message.
ALTER TABLE engine_play ADD COLUMN lines integer NOT NULL DEFAULT 0;
ALTER TABLE engine_play ADD COLUMN rewind_by uuid;
ALTER TABLE engine_play ADD COLUMN rewind_target uuid;
ALTER TABLE engine_play ADD COLUMN rewind_message text;

-- engine_event.created_at: when the server accepted the event (a player's move time comes from the event at their next
-- stop, §7).
ALTER TABLE engine_event ADD COLUMN created_at timestamptz NOT NULL DEFAULT now();

-- engine_seat: per play and player, the player's local head (the newest main-line event shown to them, §2), their
-- private play settings (response mode, Always assume resolves, Autotap: never shared, §13) and their next stop
-- (where their playback stops next, worked out by the server, §7) with its time.
CREATE TABLE engine_seat (
    play_id uuid NOT NULL REFERENCES engine_play(play_id),
    account_id uuid NOT NULL,
    local_head uuid NOT NULL,
    settings text NOT NULL DEFAULT '{}',
    next_stop uuid,
    next_stop_at timestamptz,
    PRIMARY KEY (play_id, account_id)
);

-- engine_async_round: the async-round measure (§15): each wait for a player's real input that began while they weren't
-- live, with its kind (stop, choice, blocks, keep, priority) and the event it began at.
CREATE TABLE engine_async_round (
    play_id uuid NOT NULL REFERENCES engine_play(play_id),
    ordinal bigint NOT NULL,
    account_id uuid NOT NULL,
    event_id uuid NOT NULL,
    kind text NOT NULL,
    at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (play_id, ordinal)
);
