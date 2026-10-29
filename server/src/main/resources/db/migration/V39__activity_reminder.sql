-- A push already sent for an activity stays shown on the next poll even if the actor touched the
-- screen again in between (their late heartbeat must not cancel a notice that was already due).
ALTER TABLE activity_summary ADD COLUMN pushed_through bigint NOT NULL DEFAULT 0;
-- One reminder per stretch of unanswered opponent moves: the opponent's latest move in the
-- stretch (NULL once the recipient moves) and the activity ID of the reminder once sent.
ALTER TABLE activity_summary ADD COLUMN reminder_move_at timestamptz;
ALTER TABLE activity_summary ADD COLUMN reminder_activity_id bigint;
CREATE INDEX activity_summary_reminder_due ON activity_summary(reminder_move_at)
    WHERE reminder_move_at IS NOT NULL AND reminder_activity_id IS NULL;
