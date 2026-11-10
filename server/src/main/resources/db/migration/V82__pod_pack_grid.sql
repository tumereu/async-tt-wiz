-- Multiset drafting (DRF-177): a set pod's source is its pack grid read row by row (each pack's set by its code) and the
-- snapshot of every set it names, still a JSON string inside jsonb (the card-map order of fixed recipe sheets matters).
-- A stored pod of one set becomes that set for every pack: {"packs":[<code>],"sets":[<its snapshot>]}. Cube pods
-- (arrays) and games are untouched.
UPDATE pod SET source_snapshot = to_jsonb(
        '{"packs":[' || ((source_snapshot #>> '{}')::jsonb -> 'setCode')::text || '],"sets":[' || (source_snapshot #>> '{}') || ']}')
    WHERE jsonb_typeof(source_snapshot) = 'string';

-- A set preset's pack grid read row by row, as JSON text (a list of set codes); null when every pack is set_code's.
ALTER TABLE pod_preset ADD COLUMN pack_sets text;
ALTER TABLE pod_preset ADD CHECK (pack_sets IS NULL OR set_code IS NOT NULL);
