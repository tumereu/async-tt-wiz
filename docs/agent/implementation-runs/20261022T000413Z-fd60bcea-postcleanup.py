"""Verify actual earlier-run cleanup after observing native watchdog lifetime."""
import json
from pathlib import Path
import sys
import time

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from e2e import devices as d

directory = Path('/tmp/opencode/atw-device-cebbcf988acc4a7ea1c3392560636829')
value = d.load(directory)
until = time.monotonic() + 30
while True:
    after = d.windows(value, 'inventory')
    if not after['androidProcesses'] or time.monotonic() >= until:
        break
    time.sleep(.5)
assert after['androidProcesses'] == []
print('PASS: earlier native watchdog exited without broad/forced shutdown')
for name in ('3da4bb54f5eb4f069a76b0a2c1db514c', '4e4aa4f6959340149f8610206f1f1661',
             '0262904813bd40eab878e4f9f0ea5c8a', '588f2d11cb934eb4a97e4d2ae670fd96',
             'f66dc58d08154120ac0bd317c1a7879f', '859b213df1784ff1bf32b05fa8a6f38e',
             'cebbcf988acc4a7ea1c3392560636829'):
    path = Path('/tmp/opencode/atw-device-' + name)
    assert (path / 'cleaned.json').exists()
    assert not (path / 'ready.json').exists()
    print('PASS cleaned instance:', path.name)
for port in (5038, 5560, 5561, 14723, 18200, 15038, 5564, 5565, 14733, 18210):
    assert not d.linux_listening(port)
assert d.linux_listening(5037), 'Original default ADB listener must remain'
print('Final Windows inventory including untouched broker listeners:', json.dumps(after))
print('PASS: all earlier run-owned service/guest identities gone; original default ADB retained. Broker-owned loopback listeners remain a diagnosed test-adapter follow-up, not a claimed complete port-qualification pass.')
