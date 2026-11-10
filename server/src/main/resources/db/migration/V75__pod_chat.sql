-- Pod chat (owner, 2026-11-08): one chat per pod for its members, kept as long as the pod (deleted with it). Ordinals are
-- given under the pod's row lock. Touches no game.
CREATE TABLE pod_chat (
    id uuid PRIMARY KEY,
    pod_id uuid NOT NULL REFERENCES pod(id) ON DELETE CASCADE,
    author_id uuid NOT NULL REFERENCES account(id),
    text text NOT NULL,
    ordinal bigint NOT NULL CHECK (ordinal > 0),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (pod_id, ordinal)
);
CREATE INDEX pod_chat_unread ON pod_chat(pod_id, ordinal, author_id);
-- Private member state, as game_chat_read_cursor.
CREATE TABLE pod_chat_read_cursor (
    pod_id uuid NOT NULL REFERENCES pod(id) ON DELETE CASCADE,
    account_id uuid NOT NULL REFERENCES account(id),
    ordinal bigint NOT NULL CHECK (ordinal > 0),
    PRIMARY KEY (pod_id, account_id)
);

-- "New message — <pod>" on the Draft activity channel.
ALTER TABLE account_activity DROP CONSTRAINT account_activity_kind_check;
ALTER TABLE activity_summary DROP CONSTRAINT activity_summary_kind_check;
ALTER TABLE account_activity ADD CHECK (kind IN ('INVITATION','GAME','POD_INVITATION','POD_YOUR_PICK','POD_DRAFT_COMPLETE','POD_DECKS_READY','POD_DRAFT_CANCELLED','POD_DESTROYED','POD_AUTO_START_OFF','GAME_REMOVAL','POD_REMOVAL','POD_MESSAGE'));
ALTER TABLE activity_summary ADD CHECK (kind IN ('INVITATION','GAME','POD_INVITATION','POD_YOUR_PICK','POD_DRAFT_COMPLETE','POD_DECKS_READY','POD_DRAFT_CANCELLED','POD_DESTROYED','POD_AUTO_START_OFF','GAME_REMOVAL','POD_REMOVAL','POD_MESSAGE'));
