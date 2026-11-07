-- Game history, Stats and cleanup (owner, 2026-11-07). Additive: no game, pod or other row is deleted or changed here.
-- The hourly cleanup (GameCleanup) deletes old games and pods by the rules of the feature; this only gives it what it
-- reads.

-- match_record: one row per finished match (a result made final), or per match in play when its game was deleted
-- ("Unfinished", finished = false). Never deleted, also not with its game: no foreign key to game, game_play or pod.
-- record is the stored match (MatchRecords.Stored: players, decks, how it ended); the columns are what lists select by.
CREATE TABLE match_record (
    id uuid PRIMARY KEY,
    play_id uuid NOT NULL UNIQUE,
    game_id uuid NOT NULL,
    pod_id uuid,
    inviter_id uuid NOT NULL REFERENCES account(id),
    recipient_id uuid NOT NULL REFERENCES account(id),
    started_at timestamptz NOT NULL,
    ended_at timestamptz NOT NULL,
    finished boolean NOT NULL,
    record text NOT NULL
);
CREATE INDEX match_record_inviter ON match_record (inviter_id, ended_at);
CREATE INDEX match_record_recipient ON match_record (recipient_id, ended_at);
CREATE INDEX match_record_game ON match_record (game_id);
CREATE INDEX match_record_pod ON match_record (pod_id) WHERE pod_id IS NOT NULL;

-- deck_shown_to: the decks (by stable source: a saved deck's id, or a pod's id for a pod deck) an owner has shown to a
-- viewer in any game. Once shown a deck stays shown in the viewer's records, even if hidden again. Written by the
-- trigger below whenever a game's preparation shows its deck (the Show deck switch, the visibility setting, a reveal
-- after a finished game), whatever code path does it.
CREATE TABLE deck_shown_to (
    viewer_id uuid NOT NULL REFERENCES account(id),
    owner_id uuid NOT NULL REFERENCES account(id),
    source_id text NOT NULL,
    PRIMARY KEY (viewer_id, owner_id, source_id)
);

CREATE FUNCTION deck_shown_to_record() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.deck_shown AND NEW.deck IS NOT NULL THEN
        INSERT INTO deck_shown_to (viewer_id, owner_id, source_id)
        SELECT CASE WHEN g.inviter_id = NEW.account_id THEN g.recipient_id ELSE g.inviter_id END, NEW.account_id,
            NEW.deck::jsonb ->> 'sourceId'
        FROM game g WHERE g.id = NEW.game_id AND NEW.deck::jsonb ->> 'sourceId' IS NOT NULL
        ON CONFLICT DO NOTHING;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER deck_shown_to_record AFTER INSERT OR UPDATE OF deck_shown, deck ON player_preparation
    FOR EACH ROW EXECUTE FUNCTION deck_shown_to_record();

-- The decks shown in games today were shown to their opponents.
INSERT INTO deck_shown_to (viewer_id, owner_id, source_id)
SELECT CASE WHEN g.inviter_id = pp.account_id THEN g.recipient_id ELSE g.inviter_id END, pp.account_id, pp.deck::jsonb ->> 'sourceId'
FROM player_preparation pp JOIN game g ON g.id = pp.game_id
WHERE pp.deck_shown AND pp.deck IS NOT NULL AND pp.deck::jsonb ->> 'sourceId' IS NOT NULL
ON CONFLICT DO NOTHING;

-- game.activity_at: the last change by a player (spec §4.4: a move or other game event, a chat message, a lobby change,
-- an invitation or its answer); unlike updated_at, never moved by the server's own revisions (a reveal in another game,
-- a pod's withdrawn challenge) or by a player's device only reporting what it has seen. last_match_end_at: when the
-- game's last match ended (its result final). removal_warned_at: when the day-11 warning of a game in play was sent
-- (none again until there was activity after it).
ALTER TABLE game ADD COLUMN activity_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE game ADD COLUMN last_match_end_at timestamptz;
ALTER TABLE game ADD COLUMN removal_warned_at timestamptz;

-- Until now the last update time is the best record of the last activity (it is never earlier).
UPDATE game SET activity_at = updated_at;
-- A game back in its lobby after a finished match: the match ended with the last event of its latest ended play; a game
-- updated no later than a minute after that has had no activity since (the end's own revision), so its activity is the
-- end itself.
UPDATE game g SET last_match_end_at = ended.at
FROM (SELECT p.game_id, max(e.created_at) AS at FROM game_play p JOIN engine_event e ON e.play_id = p.id
      WHERE p.ended_request_id IS NOT NULL GROUP BY p.game_id) ended
WHERE ended.game_id = g.id AND g.lifecycle = 'LOBBY';
UPDATE game SET activity_at = last_match_end_at
WHERE last_match_end_at IS NOT NULL AND activity_at <= last_match_end_at + interval '1 minute';
CREATE INDEX game_activity ON game (activity_at);

-- pod.removal_warned_at: when the day-11 warning of a stalled pod was sent. A pod's own activity is its updated_at (every
-- change by a member saves the pod); its games' activity counts too.
ALTER TABLE pod ADD COLUMN removal_warned_at timestamptz;

-- engine_seat.mulligans: the mulligans the player took in the play's opening, kept while the engine still logs them (its
-- first two turns), for the match's record.
ALTER TABLE engine_seat ADD COLUMN mulligans integer NOT NULL DEFAULT 0;

-- game_removed: a game the cleanup deleted, so a games list read "changed since" a token still learns it is gone (V61's
-- marker went with the game). Only ids and the writing transaction's id.
CREATE TABLE game_removed (
    game_id uuid PRIMARY KEY,
    inviter_id uuid NOT NULL,
    recipient_id uuid NOT NULL,
    change_xid bigint NOT NULL
);
CREATE INDEX game_removed_inviter ON game_removed (inviter_id, change_xid);
CREATE INDEX game_removed_recipient ON game_removed (recipient_id, change_xid);

-- The cleanup's warnings: "Removed in 3 days if nobody plays" for a game in play, "… if nobody picks or plays" for a pod.
ALTER TABLE account_activity DROP CONSTRAINT account_activity_kind_check;
ALTER TABLE activity_summary DROP CONSTRAINT activity_summary_kind_check;
ALTER TABLE account_activity ADD CHECK (kind IN ('INVITATION','GAME','POD_INVITATION','POD_YOUR_PICK','POD_DRAFT_COMPLETE','POD_DECKS_READY','POD_DRAFT_CANCELLED','POD_DESTROYED','POD_AUTO_START_OFF','GAME_REMOVAL','POD_REMOVAL'));
ALTER TABLE activity_summary ADD CHECK (kind IN ('INVITATION','GAME','POD_INVITATION','POD_YOUR_PICK','POD_DRAFT_COMPLETE','POD_DECKS_READY','POD_DRAFT_CANCELLED','POD_DESTROYED','POD_AUTO_START_OFF','GAME_REMOVAL','POD_REMOVAL'));
