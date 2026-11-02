-- Rules v36 (Arcanum integration I-D14, SYS-061): an automated batch holds up to 1000 members. Only the limit widens;
-- no stored row changes.
ALTER TABLE gameplay_mutation DROP CONSTRAINT gameplay_mutation_member_index_check;
ALTER TABLE gameplay_mutation ADD CONSTRAINT gameplay_mutation_member_index_check CHECK (member_index BETWEEN 0 AND 999);
