-- Draft decks: saved decks kept automatically from pod deck submissions, one per pod membership.
-- No pod FK: a draft deck outlives its pod, so the pod's name is kept for display.
ALTER TABLE saved_deck ADD COLUMN draft_pod_id uuid;
ALTER TABLE saved_deck ADD COLUMN draft_pod_name text;
ALTER TABLE saved_deck ADD CONSTRAINT saved_deck_draft_origin CHECK ((draft_pod_id IS NULL) = (draft_pod_name IS NULL));
CREATE UNIQUE INDEX saved_deck_draft ON saved_deck(account_id, draft_pod_id) WHERE draft_pod_id IS NOT NULL;
CREATE INDEX saved_deck_draft_age ON saved_deck(account_id, created_at) WHERE draft_pod_id IS NOT NULL;

-- Backfill from already submitted pod decks: the newest 20 per account, by last pod activity.
-- Main = submitted Main cards and basics; Side = the rest of the drafted pool and the Side basics.
CREATE TEMPORARY TABLE draft_backfill ON COMMIT DROP AS
SELECT md5('draft-deck:' || ranked.account_id || ':' || ranked.pod_id)::uuid AS deck_id,
       ranked.account_id, ranked.pod_id, ranked.pod_name, ranked.deck_name, ranked.created_at, ranked.deck, ranked.state
FROM (
    SELECT m.account_id, m.pod_id, p.name AS pod_name, coalesce(m.deck_name, p.name || ' draft') AS deck_name,
           p.updated_at AS created_at, m.submitted_deck AS deck, p.draft_state AS state,
           row_number() OVER (PARTITION BY m.account_id ORDER BY p.updated_at DESC, m.pod_id) AS rank
    FROM pod_member m JOIN pod p ON p.id = m.pod_id
    WHERE m.submitted_deck IS NOT NULL AND p.draft_state IS NOT NULL
) ranked
WHERE ranked.rank <= 20;

-- The basics a pod game already used (the app's fixed default printings), when any game recorded them.
CREATE TEMPORARY TABLE draft_backfill_basic ON COMMIT DROP AS
SELECT DISTINCT ON (used.land) used.land, used.digest
FROM (
    SELECT k.key AS land, k.value AS digest, count(*) AS uses
    FROM player_preparation pp, jsonb_each_text(coalesce(pp.deck::jsonb -> 'basicDefinitions', '{}'::jsonb)) k
    WHERE pp.deck IS NOT NULL AND jsonb_typeof(pp.deck::jsonb -> 'basicDefinitions') = 'object'
    GROUP BY k.key, k.value
) used JOIN definition d ON d.digest = used.digest
ORDER BY used.land, used.uses DESC, used.digest;

CREATE TEMPORARY TABLE draft_backfill_card ON COMMIT DROP AS
WITH copies AS (
    SELECT b.deck_id, c ->> 'id' AS copy_id, c ->> 'definition' AS digest
    FROM draft_backfill b, jsonb_array_elements(b.state -> 'copies') c
), main AS (
    SELECT b.deck_id, x.e ->> 'copyId' AS copy_id, x.o AS ord
    FROM draft_backfill b, jsonb_array_elements(b.deck -> 'copies') WITH ORDINALITY AS x(e, o)
    WHERE coalesce(x.e ->> 'category', 'MAIN') = 'MAIN'
), pool AS (
    SELECT b.deck_id, x.copy_id, x.o AS ord
    FROM draft_backfill b, jsonb_array_elements_text(b.state -> 'pools' -> (b.account_id::text)) WITH ORDINALITY AS x(copy_id, o)
)
SELECT m.deck_id, 'MAINBOARD'::text AS section, c.digest, m.ord, 1 AS quantity
FROM main m JOIN copies c ON c.deck_id = m.deck_id AND c.copy_id = m.copy_id
UNION ALL
SELECT p.deck_id, 'SIDEBOARD', c.digest, p.ord, 1
FROM pool p JOIN copies c ON c.deck_id = p.deck_id AND c.copy_id = p.copy_id
WHERE NOT EXISTS (SELECT 1 FROM main m WHERE m.deck_id = p.deck_id AND m.copy_id = p.copy_id);

INSERT INTO draft_backfill_card (deck_id, section, digest, ord, quantity)
SELECT b.deck_id, s.section, basic.digest, 1000000 + l.o, coalesce((b.deck -> s.field ->> lower(l.land))::int, 0)
FROM draft_backfill b
CROSS JOIN (VALUES ('MAINBOARD', 'basics'), ('SIDEBOARD', 'sideBasics')) AS s(section, field)
CROSS JOIN unnest(ARRAY['PLAINS', 'ISLAND', 'SWAMP', 'MOUNTAIN', 'FOREST']) WITH ORDINALITY AS l(land, o)
JOIN draft_backfill_basic basic ON basic.land = l.land;

DELETE FROM draft_backfill_card c WHERE c.quantity <= 0 OR NOT EXISTS (SELECT 1 FROM definition d WHERE d.digest = c.digest);

INSERT INTO saved_deck (id, account_id, name, created_at, draft_pod_id, draft_pod_name)
SELECT b.deck_id, b.account_id, b.deck_name, b.created_at, b.pod_id, b.pod_name
FROM draft_backfill b
WHERE EXISTS (SELECT 1 FROM draft_backfill_card c WHERE c.deck_id = b.deck_id);

INSERT INTO account_definition (account_id, digest)
SELECT DISTINCT b.account_id, c.digest FROM draft_backfill_card c JOIN draft_backfill b ON b.deck_id = c.deck_id
ON CONFLICT DO NOTHING;

INSERT INTO deck_entry (deck_id, account_id, position, section, definition_digest, quantity)
SELECT g.deck_id, g.account_id, (row_number() OVER (PARTITION BY g.deck_id ORDER BY g.section, g.ord) - 1)::int,
       g.section, g.digest, g.quantity
FROM (
    SELECT c.deck_id, b.account_id, c.section, c.digest, sum(c.quantity)::int AS quantity, min(c.ord) AS ord
    FROM draft_backfill_card c JOIN draft_backfill b ON b.deck_id = c.deck_id
    GROUP BY c.deck_id, b.account_id, c.section, c.digest
) g;
