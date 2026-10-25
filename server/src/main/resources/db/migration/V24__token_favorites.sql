CREATE TABLE account_token_favorites (
    account_id uuid PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE,
    keys_json text NOT NULL
);
