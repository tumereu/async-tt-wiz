-- LOB-141, DRF-092 (owner 2026-11-08): limited Build's Trash is now Maybe, and a pod deck's Maybe goes to its games
-- outside the game with Side. Nothing is lost: builds keep every copy with TRASH renamed MAYBE; a submitted deck (which
-- left Trash out) takes its build's copies again, Maybe included (a submitted build is locked, so they are the same
-- copies in the same order); stored request responses say MAYBE too. Games and their prepared decks are untouched.
UPDATE pod_member SET build = jsonb_set(build, '{copies}', (
    SELECT jsonb_agg(CASE WHEN c ->> 'category' = 'TRASH' THEN jsonb_set(c, '{category}', '"MAYBE"') ELSE c END ORDER BY n)
    FROM jsonb_array_elements(build -> 'copies') WITH ORDINALITY AS t(c, n)))
WHERE build -> 'copies' @> '[{"category": "TRASH"}]';

UPDATE pod_member SET submitted_deck = jsonb_set(submitted_deck, '{copies}', build -> 'copies')
WHERE submitted_deck IS NOT NULL AND build -> 'copies' @> '[{"category": "MAYBE"}]';

UPDATE account_request SET response = replace(response, '"category":"TRASH"', '"category":"MAYBE"')
WHERE response LIKE '%"category":"TRASH"%';
