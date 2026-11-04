-- Switch-review #11 (owner, 2026-11-04): the games list is read as "games changed since X". Every write of a game row
-- records the writing transaction's id (pg_current_xact_id, as a bigint); a list read returns the oldest transaction
-- still running when it began (pg_snapshot_xmin) as its token. A game changed by any transaction the earlier read
-- couldn't see has a marker at or after that token, so "changed since the token" never misses one (it may repeat some,
-- which the app takes by revision). Every summary change writes the game row in the same transaction (its revision).
ALTER TABLE game ADD COLUMN change_xid bigint NOT NULL DEFAULT (pg_current_xact_id()::text::bigint);

CREATE FUNCTION game_change_xid() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.change_xid := pg_current_xact_id()::text::bigint;
    RETURN NEW;
END
$$;

CREATE TRIGGER game_change_xid BEFORE INSERT OR UPDATE ON game FOR EACH ROW EXECUTE FUNCTION game_change_xid();
