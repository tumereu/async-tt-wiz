-- Deliberately no pod foreign key: accepted games outlive pod destruction.
ALTER TABLE game ADD COLUMN pod_id uuid;
ALTER TABLE game ADD COLUMN pod_name text;
ALTER TABLE game ADD COLUMN pod_sideboard_limit integer CHECK (pod_sideboard_limit >= 0);
CREATE INDEX game_pod_challenges ON game(pod_id) WHERE pod_id IS NOT NULL;
