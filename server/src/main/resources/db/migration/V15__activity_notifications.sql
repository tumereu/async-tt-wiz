-- Delivery bookkeeping is separate from game history and personal review progress.
CREATE TABLE activity_cursor (
    account_id uuid PRIMARY KEY REFERENCES account(id),
    high_water bigint NOT NULL DEFAULT 0 CHECK (high_water >= 0),
    retention_floor bigint NOT NULL DEFAULT 0 CHECK (retention_floor >= 0 AND retention_floor <= high_water)
);
CREATE TABLE account_activity (
    account_id uuid NOT NULL REFERENCES account(id),
    activity_id bigint NOT NULL,
    kind text NOT NULL CHECK (kind IN ('INVITATION','GAME')),
    game_id uuid NOT NULL REFERENCES game(id),
    identity uuid NOT NULL,
    author_id uuid REFERENCES account(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, activity_id)
);
CREATE INDEX account_activity_retention ON account_activity(created_at);
CREATE TABLE activity_summary (
    account_id uuid NOT NULL REFERENCES account(id),
    game_id uuid NOT NULL REFERENCES game(id),
    kind text NOT NULL CHECK (kind IN ('INVITATION','GAME')),
    activity_id bigint NOT NULL,
    identity uuid NOT NULL,
    author_id uuid REFERENCES account(id),
    PRIMARY KEY (account_id, game_id, kind)
);
CREATE TABLE activity_installation (
    installation_id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES account(id),
    session_id uuid NOT NULL REFERENCES account_session(id),
    binding uuid NOT NULL,
    generation uuid NOT NULL UNIQUE,
    push_token text,
    invalid_token_hash text,
    registered_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE activity_push (
    generation uuid NOT NULL REFERENCES activity_installation(generation) ON DELETE CASCADE ON UPDATE CASCADE,
    game_id uuid NOT NULL REFERENCES game(id),
    kind text NOT NULL,
    activity_id bigint NOT NULL,
    identity uuid NOT NULL,
    first_at timestamptz NOT NULL,
    due_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    PRIMARY KEY (generation, game_id, kind)
);
