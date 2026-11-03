-- I-D44: a game's plays start with Automatum on or off, chosen when the game is created (on by default).
-- Existing games keep today's start (off); every new game sets it explicitly.
ALTER TABLE game ADD COLUMN automatum boolean NOT NULL DEFAULT false;
