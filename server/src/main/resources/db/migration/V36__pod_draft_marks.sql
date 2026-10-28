-- A member's keep/burn/Librarian marks for their current head pack: private local intent, kept across app restarts.
-- The row is deleted when that pack is picked and whenever the draft attempt ends.
CREATE TABLE pod_draft_marks (
    pod_id uuid NOT NULL REFERENCES pod(id) ON DELETE CASCADE,
    draft_attempt_id uuid NOT NULL,
    account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    pack_id text NOT NULL,
    keeps jsonb NOT NULL,
    burns jsonb NOT NULL,
    librarians jsonb NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (pod_id, draft_attempt_id, account_id)
);
