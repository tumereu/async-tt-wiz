-- Switch-review #11 (owner, 2026-11-04): the games list is read as "games changed since X". Every write of a game row
-- records the writing transaction's id (pg_current_xact_id, as a bigint) in game_change; a list read returns the oldest
-- transaction still running when it began (pg_snapshot_xmin) as its token. A game changed by any transaction the earlier
-- read couldn't see has a marker at or after that token, so "changed since the token" never misses one (it may repeat
-- some, which the app takes by revision). Every summary change writes the game row in the same transaction (its
-- revision). A table of its own, so reading a game never depends on it.
CREATE TABLE game_change (
    game_id uuid PRIMARY KEY REFERENCES game (id) ON DELETE CASCADE,
    change_xid bigint NOT NULL
);

INSERT INTO game_change (game_id, change_xid) SELECT id, pg_current_xact_id()::text::bigint FROM game;

CREATE FUNCTION game_change_xid() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO game_change (game_id, change_xid) VALUES (NEW.id, pg_current_xact_id()::text::bigint)
        ON CONFLICT (game_id) DO UPDATE SET change_xid = EXCLUDED.change_xid;
    RETURN NEW;
END
$$;

CREATE TRIGGER game_change_xid AFTER INSERT OR UPDATE ON game FOR EACH ROW EXECUTE FUNCTION game_change_xid();
