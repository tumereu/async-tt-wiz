ALTER TABLE debug_crash_report ADD COLUMN kind text NOT NULL DEFAULT 'crash'
    CHECK (kind IN ('crash', 'latency'));
