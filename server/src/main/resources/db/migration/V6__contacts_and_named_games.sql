CREATE TABLE contact (
    account_id uuid NOT NULL REFERENCES account(id),
    contact_id uuid NOT NULL REFERENCES account(id),
    PRIMARY KEY (account_id, contact_id),
    CHECK (account_id <> contact_id)
);
-- One persistent identity across invitation attempts, preparation and later incarnations.
CREATE TABLE game (
    id uuid PRIMARY KEY,
    game_name text NOT NULL CHECK (length(btrim(game_name)) > 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    inviter_id uuid NOT NULL REFERENCES account(id),
    recipient_id uuid NOT NULL REFERENCES account(id),
    preparation_round uuid NOT NULL UNIQUE,
    lifecycle text NOT NULL DEFAULT 'LOBBY' CHECK (lifecycle IN ('LOBBY','ACTIVE','CANCELLED','DELETED')),
    revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
    CHECK (inviter_id <> recipient_id)
);
CREATE INDEX game_inviter_list ON game(inviter_id, id);
CREATE INDEX game_recipient_list ON game(recipient_id, id);
CREATE TABLE invitation (
    id uuid PRIMARY KEY,
    game_id uuid NOT NULL UNIQUE REFERENCES game(id),
    state text NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','ACCEPTED','DECLINED'))
);
