"""Inspect retained checker baseline against exact owned environment ports."""
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))
from e2e import devices as d

directory = Path('/tmp/opencode/atw-device-043555f322224af58b9f3861313df8c1')
envdir = Path('/tmp/opencode/atw-e2e-043555f322224af58b9f3861313df8c1')
value = d.load(directory)
baseline = json.loads((directory / 'baseline-windows.json').read_text())
ports = json.loads((envdir / 'instance.json').read_text())['ports']
after = d.windows(value, 'inventory')
missing = [row for row in baseline['listeners'] if row not in after['listeners']]
print(json.dumps({'ownedEnvironmentPorts': ports, 'missingSinceBaseline': missing,
                  'baseline': baseline, 'current': after}, indent=2))
assert missing and all(row['port'] in ports.values() for row in missing)
assert after['androidProcesses'] == baseline['androidProcesses']
print('PASS diagnosis: only the checker-owned Compose loopback ports vanished. Device baseline was captured AFTER Compose provision, so it incorrectly classified these two owned endpoints as unrelated. Preserve strict comparison for every other baseline listener.')
