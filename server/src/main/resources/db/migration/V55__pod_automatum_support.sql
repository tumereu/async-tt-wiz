-- I-D51/I-D52 (pivot review #2): which cards of a pod's source Automatum plays by hand (JSON CardListSupport), stored
-- with the pod when its source is chosen: a saved cube's stored support, or the creator's device's result for a set.
-- Null when not known (pods created before this, or sources not worked out yet).
ALTER TABLE pod ADD COLUMN automatum_support text;
