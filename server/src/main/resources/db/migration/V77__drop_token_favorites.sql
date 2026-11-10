-- Token favourites are gone (owner, 2026-11-10: card data from the server's catalog, step 1): no app reads or writes
-- them since the token list left the table, so their table goes with the service.
DROP TABLE account_token_favorites;
