ALTER TABLE debug_crash_report ADD COLUMN kind text NOT NULL DEFAULT 'crash'
    CHECK (kind IN ('crash', 'latency'));

ALTER TABLE debug_crash_daily_count
    ADD COLUMN kind text NOT NULL DEFAULT 'crash' CHECK (kind IN ('crash', 'latency')),
    DROP CONSTRAINT debug_crash_daily_count_pkey,
    DROP CONSTRAINT debug_crash_daily_count_reports_check,
    ADD PRIMARY KEY (account_id, day, kind),
    ADD CHECK (reports BETWEEN 1 AND CASE WHEN kind = 'latency' THEN 60 ELSE 20 END);
