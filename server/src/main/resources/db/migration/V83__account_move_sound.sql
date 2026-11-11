-- TRN-075, LOB-146 (owner, 2026-11-11): the account's "Your move" sound by its id ("off", "plip", ...); null: the default
-- (plip). Every device of the account plays the same.
ALTER TABLE account ADD COLUMN move_sound text;
