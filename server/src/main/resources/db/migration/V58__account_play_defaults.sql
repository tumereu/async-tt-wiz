-- I-D56 step D (LOB-113): the account's defaults for new games on engine state (response mode, Always assume resolves,
-- Autotap mana) as the JSON of a PlaySettings; null: the defaults. The server gives them to each player's seat when an
-- engine play starts (engine_seat.settings); each game's own are changed from its quick actions.
ALTER TABLE account ADD COLUMN play_defaults text;

-- I-D56 §8 (LOB-116): the game's latest activity for the recipient is the other player's answer that branched it (their
-- real answer differed from what the recipient assumed): its notification says so. One per branch, collapsed per game
-- like every game activity.
ALTER TABLE activity_summary ADD COLUMN branched boolean NOT NULL DEFAULT false;
