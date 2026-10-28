-- A pod challenge sent before the opponent submitted their deck: hidden from the opponent until they submit.
ALTER TABLE game ADD COLUMN pod_invitation_held boolean NOT NULL DEFAULT false;
