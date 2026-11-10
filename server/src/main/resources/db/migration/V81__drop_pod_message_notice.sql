-- Pod chat sends no notification (owner, 2026-11-10): the "New message — <pod>" notices go, and their kind with them.
-- Touches no game, pod or chat message.
DELETE FROM activity_push WHERE kind = 'POD_MESSAGE';
DELETE FROM activity_summary WHERE kind = 'POD_MESSAGE';
DELETE FROM account_activity WHERE kind = 'POD_MESSAGE';
ALTER TABLE account_activity DROP CONSTRAINT account_activity_kind_check;
ALTER TABLE activity_summary DROP CONSTRAINT activity_summary_kind_check;
ALTER TABLE account_activity ADD CHECK (kind IN ('INVITATION','GAME','POD_INVITATION','POD_YOUR_PICK','POD_DRAFT_COMPLETE','POD_DECKS_READY','POD_DRAFT_CANCELLED','POD_DESTROYED','POD_AUTO_START_OFF','GAME_REMOVAL','POD_REMOVAL'));
ALTER TABLE activity_summary ADD CHECK (kind IN ('INVITATION','GAME','POD_INVITATION','POD_YOUR_PICK','POD_DRAFT_COMPLETE','POD_DECKS_READY','POD_DRAFT_CANCELLED','POD_DESTROYED','POD_AUTO_START_OFF','GAME_REMOVAL','POD_REMOVAL'));
