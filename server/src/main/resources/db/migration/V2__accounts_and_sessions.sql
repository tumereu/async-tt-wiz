CREATE TABLE account (
    id uuid PRIMARY KEY,
    username text NOT NULL,
    username_key text COLLATE "C" NOT NULL UNIQUE,
    email_delivery text NOT NULL,
    email_key text COLLATE "C" NOT NULL UNIQUE,
    password_phc text NOT NULL,
    auth_epoch bigint NOT NULL DEFAULT 0 CHECK (auth_epoch >= 0),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE account_session (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES account(id),
    token_hash bytea NOT NULL UNIQUE CHECK (octet_length(token_hash) = 32),
    auth_epoch bigint NOT NULL CHECK (auth_epoch >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz
);
CREATE INDEX account_session_account ON account_session(account_id);
