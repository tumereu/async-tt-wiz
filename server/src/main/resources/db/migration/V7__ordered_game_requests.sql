-- Retain every attempt; the persistent game points only to its current invitation.
ALTER TABLE invitation DROP CONSTRAINT invitation_game_id_key;
ALTER TABLE game ADD COLUMN current_invitation uuid;
UPDATE game SET current_invitation = invitation.id FROM invitation WHERE invitation.game_id = game.id;
ALTER TABLE game ALTER COLUMN current_invitation SET NOT NULL;
ALTER TABLE invitation ADD UNIQUE (game_id, id);
ALTER TABLE game ADD FOREIGN KEY (id, current_invitation) REFERENCES invitation(game_id, id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE game ADD COLUMN next_ingress bigint NOT NULL DEFAULT 1 CHECK (next_ingress > 0);

CREATE TABLE game_stream (
    game_id uuid NOT NULL REFERENCES game(id),
    account_id uuid NOT NULL REFERENCES account(id),
    installation_id uuid NOT NULL,
    process_id uuid NOT NULL,
    stream_id uuid NOT NULL,
    connection_id uuid NOT NULL,
    generation bigint NOT NULL CHECK (generation > 0),
    PRIMARY KEY (game_id, account_id, installation_id)
);
CREATE TABLE game_inbox (
    request_id uuid PRIMARY KEY REFERENCES request_identity(id),
    game_id uuid NOT NULL REFERENCES game(id),
    account_id uuid NOT NULL REFERENCES account(id),
    ingress_ordinal bigint NOT NULL CHECK (ingress_ordinal > 0),
    stream_id uuid NOT NULL,
    generation bigint NOT NULL,
    command text NOT NULL,
    result text,
    UNIQUE (game_id, ingress_ordinal)
);
CREATE INDEX game_inbox_pending ON game_inbox(game_id, ingress_ordinal) WHERE result IS NULL;
CREATE TABLE game_revision (
    game_id uuid NOT NULL REFERENCES game(id),
    revision bigint NOT NULL CHECK (revision > 0),
    payload text NOT NULL,
    PRIMARY KEY (game_id, revision)
);
CREATE TABLE game_outbox (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    game_id uuid NOT NULL REFERENCES game(id),
    account_id uuid NOT NULL REFERENCES account(id),
    request_id uuid NOT NULL UNIQUE REFERENCES game_inbox(request_id),
    revision text,
    result text NOT NULL,
    published boolean NOT NULL DEFAULT false
);
CREATE INDEX game_outbox_due ON game_outbox(id) WHERE NOT published;
