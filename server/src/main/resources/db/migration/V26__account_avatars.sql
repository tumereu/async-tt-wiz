ALTER TABLE account ADD COLUMN avatar_scryfall_id uuid;
ALTER TABLE account ADD COLUMN avatar_face integer NOT NULL DEFAULT 0 CHECK (avatar_face BETWEEN 0 AND 15);
