-- I-D57 (async-play.md §24): whether the game waits for the player (their decision at the main line's head, their
-- opening declaration, a result they haven't seen, an undo request to answer): only then are they told "Your move" and
-- reminded, never while the other player is still playing on assumptions. And the point they would resume at (the
-- turn and step at their own local head, as the JSON of a ResumePoint) that their notifications name.
ALTER TABLE engine_seat ADD COLUMN waited boolean NOT NULL DEFAULT false;
ALTER TABLE engine_seat ADD COLUMN resume_at text;
