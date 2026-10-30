-- Per game and player: show this game's deck name and cover to the opponent. Hidden by default, also for existing games.
ALTER TABLE player_preparation ADD COLUMN deck_shown boolean NOT NULL DEFAULT false;
