-- DRF-239 (owner 2026-11-19): how many picks of the pod's Rotisserie draft each member has seen, so "While you were away"
-- tells only what happened since, on whichever device. Additive: existing members start at 0 (everything so far unseen).
ALTER TABLE pod_member ADD COLUMN picks_seen integer NOT NULL DEFAULT 0;
