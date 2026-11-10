-- Card data from the server's catalog (owner, 2026-11-10, step 3; LOB-124): the server works out a deck's or cube's
-- Automatum support from its card catalog's marks whenever it reads the list, so the support devices stored with each
-- list (V53) goes. A pod keeps its own (V55): its source's support as it was when the source was chosen.
ALTER TABLE saved_deck DROP COLUMN automatum_support;
ALTER TABLE saved_cube DROP COLUMN automatum_support;
