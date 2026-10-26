CREATE TABLE pod (
    id uuid PRIMARY KEY,
    name text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 120),
    creator_id uuid NOT NULL REFERENCES account(id),
    phase text NOT NULL DEFAULT 'SETUP' CHECK (phase IN ('SETUP', 'DRAFTING', 'DRAFTED')),
    settings jsonb NOT NULL,
    source_name text NOT NULL,
    source_snapshot jsonb NOT NULL,
    draft_attempt_id uuid,
    draft_state jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((phase = 'SETUP' AND draft_attempt_id IS NULL AND draft_state IS NULL)
        OR (phase <> 'SETUP' AND draft_attempt_id IS NOT NULL AND draft_state IS NOT NULL))
);
CREATE TABLE pod_member (
    pod_id uuid NOT NULL REFERENCES pod(id) ON DELETE CASCADE,
    account_id uuid NOT NULL REFERENCES account(id),
    build jsonb NOT NULL DEFAULT '{"copies":[],"basics":{}}',
    submitted_deck jsonb,
    PRIMARY KEY (pod_id, account_id)
);
CREATE INDEX pod_member_account ON pod_member(account_id, pod_id);
CREATE TABLE pod_invitation (
    pod_id uuid NOT NULL REFERENCES pod(id) ON DELETE CASCADE,
    account_id uuid NOT NULL REFERENCES account(id),
    state text NOT NULL CHECK (state IN ('PENDING', 'DECLINED', 'ACCEPTED', 'LAPSED')),
    invited_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (pod_id, account_id)
);
CREATE INDEX pod_invitation_account ON pod_invitation(account_id, state);

-- The existing content-free resource hint now also addresses pods. Destroy/cancel
-- notices must survive deletion of their resource, so there is no resource FK.
ALTER TABLE account_activity DROP CONSTRAINT account_activity_game_id_fkey;
ALTER TABLE activity_summary DROP CONSTRAINT activity_summary_game_id_fkey;
ALTER TABLE activity_push DROP CONSTRAINT activity_push_game_id_fkey;
ALTER TABLE account_activity DROP CONSTRAINT account_activity_kind_check;
ALTER TABLE activity_summary DROP CONSTRAINT activity_summary_kind_check;
ALTER TABLE account_activity ADD CHECK (kind IN ('INVITATION','GAME','POD_INVITATION','POD_YOUR_PICK','POD_DRAFT_COMPLETE','POD_DRAFT_CANCELLED','POD_DESTROYED'));
ALTER TABLE activity_summary ADD CHECK (kind IN ('INVITATION','GAME','POD_INVITATION','POD_YOUR_PICK','POD_DRAFT_COMPLETE','POD_DRAFT_CANCELLED','POD_DESTROYED'));
