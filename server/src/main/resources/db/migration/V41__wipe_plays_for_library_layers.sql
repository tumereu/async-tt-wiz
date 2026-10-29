-- Destructive owner decision (rules v29, library layers): plays recorded with the old three-part
-- library model are not migrated. Every game that ever started a play is deleted with everything
-- that belongs to it (plays, history, revisions, requests, chat, notifications, lobby copies).
-- Its stored revisions embed old play snapshots, so it cannot simply return to the lobby.
-- Games that never started a play, accounts, saved decks, cubes, pods and token favorites stay.
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
DELETE FROM gameplay_endpoint_edge WHERE play_id IN (SELECT id FROM game_play WHERE game_id IN (SELECT game_id FROM wiped_game));
DELETE FROM gameplay_endpoint_record WHERE play_id IN (SELECT id FROM game_play WHERE game_id IN (SELECT game_id FROM wiped_game));
DELETE FROM game_play_mutation WHERE play_id IN (SELECT id FROM game_play WHERE game_id IN (SELECT game_id FROM wiped_game));
DELETE FROM gameplay_tree_control WHERE play_id IN (SELECT id FROM game_play WHERE game_id IN (SELECT game_id FROM wiped_game));
DELETE FROM gameplay_mutation WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM gameplay_group WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM game_play WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM game_outbox WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM game_revision WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM game_stream WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM game_inbox WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM player_preparation WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM game_definition WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM invitation WHERE game_id IN (SELECT game_id FROM wiped_game);
DELETE FROM game WHERE id IN (SELECT game_id FROM wiped_game);
