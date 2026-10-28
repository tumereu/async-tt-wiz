-- A member's private deck notes and tally counters for one draft attempt: kept after the draft and after submitting,
-- deleted when that attempt is cancelled.
CREATE TABLE pod_deck_notes (
    pod_id uuid NOT NULL REFERENCES pod(id) ON DELETE CASCADE,
    draft_attempt_id uuid NOT NULL,
    account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    note text NOT NULL,
    counters jsonb NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (pod_id, draft_attempt_id, account_id)
);
