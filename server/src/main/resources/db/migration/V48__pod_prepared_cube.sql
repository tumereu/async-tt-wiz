-- Prepare cube: a pod's own Maybe beside its drafted Main snapshot (source_snapshot). Preparing moves cards between
-- the two for this pod only; the saved cube never changes. NULL means no Maybe cards (also every existing pod).
ALTER TABLE pod ADD COLUMN source_maybe jsonb;
