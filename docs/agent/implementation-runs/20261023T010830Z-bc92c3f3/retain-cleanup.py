"""Retain exact owned reset/cleanup records and the first handoff-oracle diagnostic."""
from pathlib import Path
import base64
import hashlib
import json

prefix = Path(__file__).parent
paths = [Path('/tmp/opencode/atw-e2e-bc92c3f3000000000000000000000001') / name
    for name in ('reset-completed.json', 'cleaned.json')]
paths += [Path('/tmp/opencode/atw-device-bc92c3f300000000000000000000000' + str(i)) / 'cleaned.json' for i in (1, 2)]
records = {}
for path in paths:
    data = path.read_bytes()
    records[str(path)] = dict(base64=base64.b64encode(data).decode(), sha256=hashlib.sha256(data).hexdigest(), record=json.loads(data))
(prefix / 'owned-reset-cleanup.json').write_text(json.dumps(records, indent=2) + '\n')
path = prefix / 'handoff-diagnostic.json'
assert not path.exists()
data = (prefix / 'handoff.txt').read_bytes()
assert b'AssertionError' in data
path.write_text(json.dumps(dict(command=['python3', 'docs/agent/implementation-runs/20261023T010830Z-bc92c3f3/handoff.py'],
    exitCode=1, outputSha256=hashlib.sha256(data).hexdigest(), output=data.decode()), indent=2) + '\n')
print('PASS retained exact reset/cleanup records and first handoff diagnostic; reset CLI stdout is readiness, while reset-completed.json owns the checked store inventory')
