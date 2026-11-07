-- HIS-108 (owner playtest 2026-11-07): when the other player's undo takes back events a seat had been shown, the undo
-- deletes them, so a device opening the game afterwards (from a notification, an app started anew) could only show the
-- game as it is now. The seat keeps what it had been shown of them (undone_events: a JSON array of encoded events, from
-- the undo's point on) and who undid (undone_by), so that device first shows where the player left it, then the undo.
-- Any later move of the local head clears them. Existing games keep working: the columns start empty.
ALTER TABLE engine_seat ADD COLUMN undone_events text;
ALTER TABLE engine_seat ADD COLUMN undone_by uuid;
