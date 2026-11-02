-- I-D43: problem and crash reports carry the latest game's Automatum log and reproduction bundle.
ALTER TABLE problem_report ADD COLUMN automatum text NOT NULL DEFAULT '';
ALTER TABLE debug_crash_report ADD COLUMN automatum text NOT NULL DEFAULT '';
