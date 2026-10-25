ALTER TABLE debug_crash_report DROP CONSTRAINT debug_crash_report_kind_check;
ALTER TABLE debug_crash_report ADD CHECK (kind IN ('crash', 'latency', 'jank'));

ALTER TABLE debug_crash_daily_count
    DROP CONSTRAINT debug_crash_daily_count_kind_check,
    DROP CONSTRAINT debug_crash_daily_count_check,
    ADD CHECK (kind IN ('crash', 'latency', 'jank')),
    ADD CHECK (reports BETWEEN 1 AND CASE kind WHEN 'latency' THEN 60 WHEN 'jank' THEN 3 ELSE 20 END);
