"""Passive wait for this run's already-cleaned loopback proxy entries to drain."""
import json
from pathlib import Path
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))
from e2e import devices as d

device = d.load(Path('/tmp/opencode/atw-device-043555f344444af58b9f3861313df8c1'))
ports = set()
for part in ('0000', '1111', '2222', '3333'):
    path = Path('/tmp/opencode/atw-e2e-043555f3' + part + '4af58b9f3861313df8c1')
    assert (path / 'cleaned.json').exists()
    ports.update(json.loads((path / 'instance.json').read_text())['ports'].values())
assert not any(d.linux_listening(port) for port in ports)
until = time.monotonic() + 180
while True:
    current = d.windows(device, 'inventory')
    remaining = [row for row in current['listeners'] if row['port'] in ports]
    print(json.dumps({'alreadyCleanedOwnedPorts': sorted(ports), 'remainingWindowsProxyEntries': remaining}), flush=True)
    if not remaining:
        break
    assert time.monotonic() < until, 'Passive proxy-drain preflight unavailable; no process/network mutation permitted'
    time.sleep(5)
print('PASS passive preflight: no just-cleaned synthetic endpoint is misclassified as unrelated in the next strict native-services baseline. No listener/process stopped or networking changed.')
