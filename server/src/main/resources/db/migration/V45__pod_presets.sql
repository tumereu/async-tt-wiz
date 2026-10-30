-- Saved New pod forms (draft settings and source, without pod name or invitees), per account.
-- cube_id is a plain reference: deleting the cube keeps the preset, which then reports the cube as missing.
CREATE TABLE pod_preset (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    name text NOT NULL CHECK (length(btrim(name)) > 0),
    name_key text NOT NULL,
    settings text NOT NULL,
    cube_id uuid,
    set_code text,
    set_name text,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (cube_id IS NULL OR set_code IS NULL),
    CHECK ((set_code IS NULL) = (set_name IS NULL)),
    UNIQUE (account_id, name_key)
);
