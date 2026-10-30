-- Account default for each new game's "Show deck name & art" toggle, applied by the server when a deck is selected.
ALTER TABLE account ADD COLUMN deck_visibility text NOT NULL DEFAULT 'AFTER_FIRST_GAME'
    CHECK (deck_visibility IN ('VISIBLE', 'HIDDEN', 'AFTER_FIRST_GAME'));

-- Which deck (saved deck id, or pod id for a pod deck) each player has finished a play with against which opponent.
CREATE TABLE finished_deck_matchup (
    account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    opponent_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    deck_source_id uuid NOT NULL,
    PRIMARY KEY (account_id, opponent_id, deck_source_id)
);

-- Existing ended plays: the game's retained lobby deck is the best record of the deck that was used.
INSERT INTO finished_deck_matchup (account_id, opponent_id, deck_source_id)
SELECT DISTINCT pp.account_id,
       CASE WHEN pp.account_id = g.inviter_id THEN g.recipient_id ELSE g.inviter_id END,
       (pp.deck::jsonb ->> 'sourceId')::uuid
FROM game_play p
JOIN game g ON g.id = p.game_id
JOIN player_preparation pp ON pp.game_id = g.id
WHERE p.ended_request_id IS NOT NULL AND pp.deck IS NOT NULL
  AND (pp.deck::jsonb ->> 'sourceId') ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
ON CONFLICT DO NOTHING;
