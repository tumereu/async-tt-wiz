-- LOB-244 (owner 2026-11-19): an account's deck categories, nesting (a category lies in its parent, null: the top level).
-- A saved deck is in at most one category. Additive: every existing deck starts without one.
CREATE TABLE deck_category (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES account(id),
    parent_id uuid REFERENCES deck_category(id),
    name text NOT NULL CHECK (length(btrim(name)) > 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX deck_category_owner ON deck_category(account_id, id);
ALTER TABLE saved_deck ADD COLUMN category_id uuid REFERENCES deck_category(id);
CREATE INDEX saved_deck_category ON saved_deck(category_id) WHERE category_id IS NOT NULL;

-- LOB-247 (owner 2026-11-19): a game's deck settings (lending, only lent decks, same deck, random deal, re-deal on
-- rematch) as their JSON (org.asyncttwiz.protocol.DeckSettings), set by the game's creator; null: the defaults.
-- Additive: every game, existing ones too, starts with the defaults (nothing lent).
ALTER TABLE game ADD COLUMN deck_settings text;
