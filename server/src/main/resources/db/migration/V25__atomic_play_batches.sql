-- Member identities are deterministic gameplay inputs, not separately admitted inbox requests.
ALTER TABLE gameplay_mutation DROP CONSTRAINT gameplay_mutation_request_id_fkey;
ALTER TABLE gameplay_mutation ADD COLUMN command_request_id uuid REFERENCES game_inbox(request_id);
UPDATE gameplay_mutation SET command_request_id = request_id;
ALTER TABLE gameplay_mutation ALTER COLUMN command_request_id SET NOT NULL;
ALTER TABLE gameplay_mutation ADD COLUMN member_index integer NOT NULL DEFAULT 0 CHECK (member_index BETWEEN 0 AND 99);
ALTER TABLE gameplay_mutation DROP CONSTRAINT gameplay_mutation_play_id_effective_ordinal_key;
ALTER TABLE gameplay_mutation ADD UNIQUE (play_id, effective_ordinal, member_index);
