-- Control mode and assumptions (owner 2026-11-06): playback's Control toggle is now a shortcut to the response mode, so
-- the separate playbackFullControl setting goes from each game's settings and each account's defaults. "Assume resolves"
-- gains Normal (the new default), kept as all its flags off: a stored Never (all off) reads as Normal from now on, as the
-- owner decided; Never is stored as its own flag. Games and their events are untouched.
UPDATE engine_seat SET settings = (settings::jsonb - 'playbackFullControl')::text
    WHERE settings::jsonb ? 'playbackFullControl';
UPDATE account SET play_defaults = (play_defaults::jsonb - 'playbackFullControl')::text
    WHERE play_defaults IS NOT NULL AND play_defaults::jsonb ? 'playbackFullControl';
