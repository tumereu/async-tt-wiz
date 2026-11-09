-- Assume default (owner 2026-11-09, LOB-113): "No mana" is the default of "Assume resolves" in new games, for every account:
-- each account's saved play defaults move to it whatever they said; an account with none saved reads it from the new
-- default. Each game's own settings (engine_seat) stay what they are: games in play keep theirs.
UPDATE account SET play_defaults = (play_defaults::jsonb || jsonb_build_object('assume', jsonb_build_object('type', 'no-mana')))::text
    WHERE play_defaults IS NOT NULL;
