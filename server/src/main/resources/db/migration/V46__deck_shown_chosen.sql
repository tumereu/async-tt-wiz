-- The player switched this game's "Show deck name & art" themselves since selecting the deck: the account default
-- ("Show after first game" revealing the deck once it finished a game against this opponent) leaves it alone.
ALTER TABLE player_preparation ADD COLUMN deck_shown_chosen boolean NOT NULL DEFAULT false;

-- Lobby decks that already finished a game against the same opponent were left hidden: show them now.
UPDATE player_preparation pp SET deck_shown = true
FROM game g, account a
WHERE g.id = pp.game_id AND a.id = pp.account_id AND g.lifecycle = 'LOBBY' AND a.deck_visibility = 'AFTER_FIRST_GAME'
  AND NOT pp.deck_shown AND pp.deck IS NOT NULL
  AND EXISTS (SELECT 1 FROM finished_deck_matchup m
      WHERE m.account_id = pp.account_id
        AND m.opponent_id = CASE WHEN pp.account_id = g.inviter_id THEN g.recipient_id ELSE g.inviter_id END
        AND m.deck_source_id::text = lower(pp.deck::jsonb ->> 'sourceId'));
