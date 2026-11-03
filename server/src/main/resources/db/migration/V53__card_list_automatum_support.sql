-- I-D51: which cards of a saved deck or cube Automatum plays by hand, as the owner's device worked it out (JSON
-- CardListSupport: parser version and card names). Null until then and whenever the list's cards change.
ALTER TABLE saved_deck ADD COLUMN automatum_support text;
ALTER TABLE saved_cube ADD COLUMN automatum_support text;
