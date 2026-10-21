-- Foundation only. Advisory-lock ownership and epoch fencing are wired in I01c2.
CREATE TABLE service_writer (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    epoch bigint NOT NULL CHECK (epoch >= 0)
);

INSERT INTO service_writer (singleton, epoch) VALUES (true, 0);
