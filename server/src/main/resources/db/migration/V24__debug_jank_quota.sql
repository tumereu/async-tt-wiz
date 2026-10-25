ALTER TABLE debug_crash_daily_count
    DROP CONSTRAINT debug_crash_daily_count_check,
    ADD CHECK (reports BETWEEN 1 AND CASE kind WHEN 'latency' THEN 60 ELSE 20 END);
