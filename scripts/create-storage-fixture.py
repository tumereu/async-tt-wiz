#!/usr/bin/env python3
"""Explicitly regenerate the retained SQLDelight v1 SQLite fixture (never during builds)."""
from pathlib import Path
import sqlite3
import sys

assert sys.argv[1:] == ["--write"], "Use --write only when intentionally qualifying the retained fixture"
path = Path("client-storage/src/main/sqldelight/databases/1.db")
assert not path.exists(), "Refuse to overwrite retained migration provenance"
path.parent.mkdir(parents=True, exist_ok=True)
with sqlite3.connect(path) as db:
    db.executescript("""
        CREATE TABLE accepted_cursor (
          scope_id TEXT NOT NULL PRIMARY KEY,
          revision INTEGER NOT NULL CHECK (revision >= 0)
        );
        CREATE TABLE accepted_record (
          scope_id TEXT NOT NULL REFERENCES accepted_cursor(scope_id),
          record_kind TEXT NOT NULL,
          record_id TEXT NOT NULL,
          payload_json TEXT NOT NULL,
          PRIMARY KEY (scope_id, record_kind, record_id)
        );
        CREATE TABLE personal_state (
          scope_id TEXT NOT NULL,
          state_key TEXT NOT NULL,
          payload_json TEXT NOT NULL,
          PRIMARY KEY (scope_id, state_key)
        );
        PRAGMA user_version = 1;
    """)
    db.execute("INSERT INTO accepted_cursor VALUES (?, ?)", ("replica-a", 9007199254740993))
    db.execute("INSERT INTO accepted_cursor VALUES (?, ?)", ("replica-b", 17))
    db.execute("INSERT INTO accepted_record VALUES (?, ?, ?, ?)",
               ("replica-a", "fixture", "one", '{"text":"retained 🧙 α","number":"9007199254740993"}'))
    db.execute("INSERT INTO accepted_record VALUES (?, ?, ?, ?)",
               ("replica-b", "fixture", "one", '{"text":"independent"}'))
    db.execute("INSERT INTO personal_state VALUES (?, ?, ?)",
               ("replica-a", "draft", '{"text":"unfinished Δ"}'))
    db.commit()
print(f"Created retained v1 fixture with SQLite {sqlite3.sqlite_version}: {path}")
