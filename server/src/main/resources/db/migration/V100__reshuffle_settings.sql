-- HIS-042 (owner 2026-11-26, playtest F): the game settings "Reshuffle libraries on undo" and "Reshuffle libraries on branch",
-- set by the game's creator in the lobby. Additive: every game, existing ones too, starts with both on (today's rule).
ALTER TABLE game ADD COLUMN reshuffle_on_undo boolean NOT NULL DEFAULT true,
    ADD COLUMN reshuffle_on_branch boolean NOT NULL DEFAULT true;
-- Whether the play's line counter's line is a branch's (HIS-194: a re-made line taken back) rather than an undo's: when it is
-- due at the head, its reshuffle follows the switch of its kind. Existing plays' due lines are all as their switches (on) make them.
ALTER TABLE engine_play ADD COLUMN line_branch boolean NOT NULL DEFAULT false;
