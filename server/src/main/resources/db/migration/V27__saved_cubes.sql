CREATE TABLE saved_cube (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES account(id),
    name text NOT NULL CHECK (length(btrim(name)) > 0),
    revision bigint NOT NULL DEFAULT 1 CHECK (revision > 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cube_cobra_id text,
    UNIQUE (id, account_id)
);
CREATE INDEX saved_cube_owner_list ON saved_cube(account_id, id);
CREATE TABLE cube_entry (
    cube_id uuid NOT NULL,
    account_id uuid NOT NULL,
    position integer NOT NULL CHECK (position >= 0),
    definition_digest text NOT NULL,
    quantity integer NOT NULL CHECK (quantity BETWEEN 1 AND 3000),
    PRIMARY KEY (cube_id, position),
    UNIQUE (cube_id, definition_digest),
    FOREIGN KEY (cube_id, account_id) REFERENCES saved_cube(id, account_id) ON DELETE CASCADE,
    FOREIGN KEY (account_id, definition_digest) REFERENCES account_definition(account_id, digest)
);
