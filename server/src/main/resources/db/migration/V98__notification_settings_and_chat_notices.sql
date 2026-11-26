-- LOB-276 (owner, 2026-11-26): which kinds of notification the account gets (Settings → Notifications), as JSON; null: all.
ALTER TABLE account ADD COLUMN notification_settings text;
-- LOB-277: a new chat message notifies (a game's chat, CHAT; a pod's, POD_CHAT). Touches no game, pod or chat message.
ALTER TABLE account_activity DROP CONSTRAINT account_activity_kind_check;
ALTER TABLE activity_summary DROP CONSTRAINT activity_summary_kind_check;
ALTER TABLE account_activity ADD CHECK (kind IN ('INVITATION','GAME','POD_INVITATION','POD_YOUR_PICK','POD_DRAFT_COMPLETE','POD_DECKS_READY','POD_DRAFT_CANCELLED','POD_DESTROYED','POD_AUTO_START_OFF','GAME_REMOVAL','POD_REMOVAL','POD_MEMBER_REMOVED','CHAT','POD_CHAT'));
ALTER TABLE activity_summary ADD CHECK (kind IN ('INVITATION','GAME','POD_INVITATION','POD_YOUR_PICK','POD_DRAFT_COMPLETE','POD_DECKS_READY','POD_DRAFT_CANCELLED','POD_DESTROYED','POD_AUTO_START_OFF','GAME_REMOVAL','POD_REMOVAL','POD_MEMBER_REMOVED','CHAT','POD_CHAT'));
-- WEB-051 (owner, 2026-11-26): a browser's own kind switches are gone (the account's decide): its stored subscription keeps
-- only the push service's address and keys
UPDATE activity_installation SET web_push = (web_push::jsonb - 'invitations' - 'games' - 'drafts')::text WHERE web_push IS NOT NULL;
