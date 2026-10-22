"""Passive read-only preflight; never stop unrelated Windows proxy processes."""
import json
from pathlib import Path
import sys
import time

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from e2e import devices as d

previous = Path('/tmp/opencode/atw-device-43e21a5011114af58b9f3861313df8c1')
environments = [Path('/tmp/opencode/atw-e2e-43e21a5011114af58b9f3861313df8c1')]
# Also wait for the current unit's first, fully cleaned diagnostic attempt when present.
current = Path('/tmp/opencode/atw-e2e-a5658aee11114af58b9f3861313df8c1')
if current.exists():
    environments.append(current)
assert (previous / 'cleaned.json').exists()
device = d.load(previous)
ports = set()
for environment in environments:
    assert (environment / 'cleaned.json').exists()
    ports.update(json.loads((environment / 'instance.json').read_text())['ports'].values())
assert not any(d.linux_listening(port) for port in ports)
until = time.monotonic() + 180
while True:
    current = d.windows(device, 'inventory')
    remaining = [row for row in current['listeners'] if row['port'] in ports]
    print(json.dumps({'previousCleanedSyntheticPorts': sorted(ports),
                      'remainingWindowsProxyEntries': remaining}), flush=True)
    if not remaining:
        print('READ-ONLY WINDOWS INVENTORY', json.dumps(current), flush=True)
        break
    assert time.monotonic() < until, 'Proxy entries have not drained; no mutation permitted'
    time.sleep(5)
print('PASS previous known-cleaned synthetic proxies drained; strict actual-suite baseline remains unchanged. No process/listener/network mutation.')
