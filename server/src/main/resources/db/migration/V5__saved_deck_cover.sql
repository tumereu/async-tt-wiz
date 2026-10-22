-- Presentation metadata only. Definitions and selected game copies have independent lifetimes.
ALTER TABLE saved_deck ADD COLUMN cover_digest text REFERENCES definition(digest);
