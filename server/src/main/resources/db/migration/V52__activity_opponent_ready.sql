-- LOB-114: the latest activity left its author Ready while the recipient is still in the opening "Mulliganing" status.
ALTER TABLE activity_summary ADD COLUMN opponent_ready boolean NOT NULL DEFAULT false;
