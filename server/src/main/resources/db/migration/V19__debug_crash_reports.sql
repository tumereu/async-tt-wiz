CREATE TABLE debug_crash_report (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    received_at timestamptz NOT NULL,
    reported_at timestamptz NOT NULL,
    app_version text NOT NULL,
    device text NOT NULL,
    body text NOT NULL CHECK (octet_length(body) <= 65536)
);
CREATE INDEX debug_crash_report_received ON debug_crash_report(received_at DESC, id DESC);

-- Separate from retained bodies so global eviction cannot reset an account's daily quota.
CREATE TABLE debug_crash_daily_count (
    account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    day date NOT NULL,
    reports integer NOT NULL CHECK (reports BETWEEN 1 AND 20),
    PRIMARY KEY (account_id, day)
);
