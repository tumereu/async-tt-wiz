-- Player-sent reports, also accepted before login (e.g. from the update gate). Only the newest few hundred are kept.
CREATE TABLE problem_report (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code text NOT NULL UNIQUE CHECK (code ~ '^[A-Z0-9]{6}$'),
    account_id uuid REFERENCES account(id) ON DELETE SET NULL,
    received_at timestamptz NOT NULL,
    reported_at timestamptz NOT NULL,
    app_version text NOT NULL,
    version_code integer NOT NULL,
    build_type text NOT NULL,
    device text NOT NULL,
    android text NOT NULL,
    description text NOT NULL,
    problem text NOT NULL,
    log text NOT NULL,
    logcat text NOT NULL
);
