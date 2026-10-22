-- Business identity is global across account HTTP and future game ingress. Never store secrets here.
CREATE TABLE request_identity (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES account(id),
    kind text NOT NULL,
    scope text NOT NULL,
    intent_digest text NOT NULL CHECK (intent_digest ~ '^[0-9a-f]{64}$')
);
CREATE TABLE account_request (
    request_id uuid PRIMARY KEY REFERENCES request_identity(id),
    status integer NOT NULL CHECK (status BETWEEN 200 AND 499),
    response text NOT NULL
);
CREATE TABLE definition (
    digest text PRIMARY KEY CHECK (digest ~ '^sha256:[0-9a-f]{64}$'),
    schema_version integer NOT NULL CHECK (schema_version = 1),
    payload text NOT NULL
);
CREATE TABLE account_definition (
    account_id uuid NOT NULL REFERENCES account(id),
    digest text NOT NULL REFERENCES definition(digest),
    PRIMARY KEY (account_id, digest)
);
CREATE TABLE saved_deck (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES account(id),
    name text NOT NULL CHECK (length(btrim(name)) > 0),
    revision bigint NOT NULL DEFAULT 1 CHECK (revision > 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (id, account_id)
);
CREATE INDEX saved_deck_owner_list ON saved_deck(account_id, id);
CREATE TABLE deck_entry (
    deck_id uuid NOT NULL,
    account_id uuid NOT NULL,
    position integer NOT NULL CHECK (position >= 0),
    section text NOT NULL CHECK (section IN ('MAINBOARD', 'SIDEBOARD')),
    definition_digest text NOT NULL,
    quantity integer NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (deck_id, position),
    UNIQUE (deck_id, section, definition_digest),
    FOREIGN KEY (deck_id, account_id) REFERENCES saved_deck(id, account_id) ON DELETE CASCADE,
    FOREIGN KEY (account_id, definition_digest) REFERENCES account_definition(account_id, digest)
);
