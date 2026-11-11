-- Automatic fault reports the app sends by itself ('fault') beside the ones players send ('player'); each kind keeps its
-- own newest per account, so faults never push out a player's report.
ALTER TABLE problem_report ADD COLUMN kind text NOT NULL DEFAULT 'player' CHECK (kind IN ('player', 'fault'));
