-- Card data from the server's catalog (owner, 2026-11-10, step 2): devices no longer upload card data, so the grants of
-- uploaded card data to accounts go. Deck and cube rows keep their digests and now refer to the stored card data itself.
-- Nothing else changes: every digest a row names is already in definition (the grants referred to it).
DROP TABLE account_definition CASCADE;
ALTER TABLE deck_entry ADD FOREIGN KEY (definition_digest) REFERENCES definition(digest);
ALTER TABLE cube_entry ADD FOREIGN KEY (definition_digest) REFERENCES definition(digest);
