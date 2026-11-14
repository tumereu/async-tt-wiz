-- DRF-031 (owner, 2026-11-14): Glimpse is no longer a built-in preset of the app but an ordinary preset every account
-- has, made with the account (PodPresetService.addDefaults). Existing accounts get the same copy now: a Booster of
-- 9 packs × 15, keep 1, burn 2 (DraftSettings.glimpse(), every field written as the server writes it), no source.
-- An account that already has a preset of that name keeps its own.
INSERT INTO pod_preset (id, account_id, name, name_key, settings)
SELECT md5('glimpse-preset:' || id::text)::uuid, id, 'Glimpse', 'glimpse',
    '{"mode":"BOOSTER","packsPerPlayer":9,"cardsPerPack":15,"keep":1,"burn":2,"discardRemainingAt":0,"packsOpenAtOnce":1,"colorBalance":true,"sideboardLimit":null,"grids":18,"twoPicks":false,"gridsOpenAtOnce":1,"librarians":0,"librarianDefinition":null,"freshBoosters":true,"cardsPerPlayer":45,"piles":3,"tablePacksOpen":null,"tables":1}'
FROM account
ON CONFLICT (account_id, name_key) DO NOTHING;
