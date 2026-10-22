CREATE TABLE password_reset_token (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES account(id),
    token_hash bytea NOT NULL UNIQUE CHECK (octet_length(token_hash) = 32),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    CHECK (expires_at > created_at)
);
CREATE INDEX password_reset_account ON password_reset_token(account_id);

CREATE TABLE reset_mail (
    token_id uuid PRIMARY KEY REFERENCES password_reset_token(id),
    encrypted_payload bytea,
    status text NOT NULL CHECK (status IN ('pending', 'delivered', 'failed', 'expired', 'cancelled')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz NOT NULL,
    CHECK ((status = 'pending') = (encrypted_payload IS NOT NULL))
);
CREATE INDEX reset_mail_due ON reset_mail(next_attempt_at) WHERE status = 'pending';
