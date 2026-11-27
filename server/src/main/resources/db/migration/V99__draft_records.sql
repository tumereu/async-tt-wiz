-- LOB-279 (owner, 2026-11-26): draft_record keeps, per player, their last 20 drafts with card ratings (DRF-315) for Stats:
-- one row per player and draft attempt, written when their own draft is done. No foreign key to pod: a row outlives its
-- pod's deletion, by hand or by the cleanup (LOB-182). record is the stored draft (DraftRecords.Stored: the pod's name,
-- source and settings, the seating and the player's history); the columns are what lists select by.
CREATE TABLE draft_record (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES account(id),
    pod_id uuid NOT NULL,
    draft_attempt_id uuid NOT NULL,
    finished_at timestamptz NOT NULL,
    record text NOT NULL,
    UNIQUE (account_id, draft_attempt_id)
);
CREATE INDEX draft_record_account ON draft_record (account_id, finished_at);
