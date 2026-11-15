-- Destructive owner decision (owner 2026-11-15, the first turn during the opponent's mulligans, HIS-042): every random result
-- of a play now comes from the stream of the player whose result it is (H(secret, line, player, k)), and the line state its
-- events and checkpoints keep counts per player, so games played before can't go on: their random answers wouldn't check.
-- Every game that ever started a play is deleted with everything that belongs to it, as V74 and V85 did (plays, engine
-- histories, revisions, requests, chat, notifications, lobby copies; game_change cascades).
-- What stays: accounts, contacts, saved decks, cubes, pods and their drafts, presets, notes, favorites, matchups, problem and
-- crash reports, match records (never deleted), decks shown and games that never started a play. Each wiped game leaves a
-- game_removed row, as the cleanup does (GameRemoval). StartedGamesWipeTest runs this again against the latest schema.
CREATE TEMP TABLE wiped_game ON COMMIT DROP AS
SELECT DISTINCT game_id FROM game_play;

-- None of the game/play FKs cascade. Break the immediate current-play cycle first; the
-- invitation cycle is DEFERRABLE INITIALLY DEFERRED and closes when its game is deleted.
UPDATE game SET current_play = NULL WHERE id IN (SELECT game_id FROM wiped_game);
DELETE FROM activity_push WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM activity_summary WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM account_activity WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM activity_presence WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM game_chat_read_cursor WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM game_chat WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM engine_async_round WHERE play_id IN (SELECT id FROM game_play WHERE game_id IN (SELECT game_id FROM wiped_game));
DELETE FROM engine_seat WHERE play_id IN (SELECT id FROM game_play WHERE game_id IN (SELECT game_id FROM wiped_game));
DELETE FROM engine_checkpoint WHERE play_id IN (SELECT id FROM game_play WHERE game_id IN (SELECT game_id FROM wiped_game));
DELETE FROM engine_event WHERE play_id IN (SELECT id FROM game_play WHERE game_id IN (SELECT game_id FROM wiped_game));
DELETE FROM engine_play WHERE play_id IN (SELECT id FROM game_play WHERE game_id IN (SELECT game_id FROM wiped_game));
DELETE FROM game_play WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM game_outbox WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM game_revision WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM game_stream WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM game_inbox WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM player_preparation WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM game_definition WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM invitation WHERE game_id IN (SELECT game_id FROM wiped_game);
INSERT INTO game_removed (game_id, inviter_id, recipient_id, change_xid)
SELECT id, inviter_id, recipient_id, pg_current_xact_id()::text::bigint FROM game WHERE id IN (SELECT game_id FROM wiped_game)
ON CONFLICT (game_id) DO NOTHING;
DELETE FROM game WHERE id IN (SELECT game_id FROM wiped_game);
