-- DRF-308 (owner, 2026-11-22): a member the pod's creator removes in setup gets the notice "Removed from <pod>".
-- Touches no game, pod or notice.
ALTER TABLE account_activity DROP CONSTRAINT account_activity_kind_check;
ALTER TABLE activity_summary DROP CONSTRAINT activity_summary_kind_check;
ALTER TABLE account_activity ADD CHECK (kind IN ('INVITATION','GAME','POD_INVITATION','POD_YOUR_PICK','POD_DRAFT_COMPLETE','POD_DECKS_READY','POD_DRAFT_CANCELLED','POD_DESTROYED','POD_AUTO_START_OFF','GAME_REMOVAL','POD_REMOVAL','POD_MEMBER_REMOVED'));
ALTER TABLE activity_summary ADD CHECK (kind IN ('INVITATION','GAME','POD_INVITATION','POD_YOUR_PICK','POD_DRAFT_COMPLETE','POD_DECKS_READY','POD_DRAFT_CANCELLED','POD_DESTROYED','POD_AUTO_START_OFF','GAME_REMOVAL','POD_REMOVAL','POD_MEMBER_REMOVED'));
