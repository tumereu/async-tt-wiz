-- Lobby toggle stored with the starter choice: after each play the choice switches to the player who
-- did not start it. On by default, also for existing games.
ALTER TABLE game ADD COLUMN alternate_starter boolean NOT NULL DEFAULT true;
