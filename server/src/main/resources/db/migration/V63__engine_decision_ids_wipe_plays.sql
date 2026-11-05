-- Destructive owner decision (stable decision identity, 2026-11-05: wipes are fine until Automatum first goes to prod):
-- the engine now names each decision by what it asks, of whom, and how many inputs the game has had before it, and each
-- input's outcome counts itself (GameState.inputCount). Games played before have states and events without that count
-- and open decisions under the old ids, which can't be read as the new ones. Every game that ever started a play is
-- deleted with everything that belongs to it, as V59 did (plays, engine histories, revisions, requests, chat,
-- notifications, lobby copies; game_change cascades). Games that never started a play (invitations, lobbies, pod
-- challenges not yet started), accounts, contacts, saved decks, cubes, pods and their drafts, presets, notes, favorites,
-- matchups and problem/crash reports stay. EngineHistoryStoreTest runs it again against the latest schema.
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
DELETE FROM game WHERE id IN (SELECT game_id FROM wiped_game);
