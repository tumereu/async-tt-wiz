"""Check the failed checker's actual idle database copies; never mutate app data."""
import hashlib
import json
from pathlib import Path
import sqlite3

root = Path('/tmp/opencode/atw-device-d986151411114af58b9f3861313df8c1')
records = sorted((json.loads(p.read_text()) for p in root.glob('app-*/observed.json')), key=lambda r: r['checkedAt'])
assert len(records) == 3
original = None
for record in records:
    path = Path(record['evidenceDirectory']) / 'replica.db'
    data = path.read_bytes()
    assert hashlib.sha256(data).hexdigest() == record['storage']['sha256']
    with sqlite3.connect(path.as_uri() + '?mode=ro&immutable=1', uri=True) as db:
        logical = hashlib.sha256('\n'.join(db.iterdump()).encode()).hexdigest()
        assert db.execute('PRAGMA integrity_check').fetchall() == [('ok',)]
        assert db.execute('PRAGMA user_version').fetchone() == (2,)
    print(path, 'rawSha256', record['storage']['sha256'], 'logicalSha256', logical)
    if original is None:
        original, expected_logical = data, logical
    assert len(data) == len(original) and logical == expected_logical
    differences = [(i, x, y) for i, (x, y) in enumerate(zip(original, data)) if x != y]
    assert all(i in (27, 95) and x == 1 and y == 2 for i, x, y in differences)
    print('actual byte differences (offset, before, after)', differences)
print('PASS: unchanged complete schema/data; only SQLite file-change counter and version-valid-for header words advanced on ordinary reopen. Raw-hash retention assertion was incorrect; no product repair, data wipe or seeded state.')
