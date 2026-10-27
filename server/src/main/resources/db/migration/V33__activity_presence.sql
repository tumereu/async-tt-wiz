CREATE TABLE activity_presence (
    game_id uuid NOT NULL REFERENCES game(id) ON DELETE CASCADE,
    account_id uuid NOT NULL REFERENCES account(id),
    session_id uuid NOT NULL REFERENCES account_session(id) ON DELETE CASCADE,
    presence_id uuid NOT NULL,
    heartbeat_at timestamptz NOT NULL,
    input_at timestamptz NOT NULL,
    left_game boolean NOT NULL DEFAULT false,
    PRIMARY KEY (game_id, account_id, presence_id)
);
CREATE INDEX activity_presence_expiry ON activity_presence(heartbeat_at);
ALTER TABLE activity_summary ADD COLUMN last_event_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE activity_summary ADD COLUMN your_move boolean NOT NULL DEFAULT false;
ALTER TABLE activity_summary ADD COLUMN new_message boolean NOT NULL DEFAULT false;
ALTER TABLE activity_summary ADD COLUMN seen_through bigint NOT NULL DEFAULT 0;
ALTER TABLE account_activity DROP CONSTRAINT account_activity_kind_check;
ALTER TABLE activity_summary DROP CONSTRAINT activity_summary_kind_check;
ALTER TABLE account_activity ADD CHECK (kind IN ('INVITATION','GAME','POD_INVITATION','POD_YOUR_PICK','POD_DRAFT_COMPLETE','POD_DECKS_READY','POD_DRAFT_CANCELLED','POD_DESTROYED'));
ALTER TABLE activity_summary ADD CHECK (kind IN ('INVITATION','GAME','POD_INVITATION','POD_YOUR_PICK','POD_DRAFT_COMPLETE','POD_DECKS_READY','POD_DRAFT_CANCELLED','POD_DESTROYED'));
