"""Run-owned actual failure/cleanup checkpoint, not whole I01f1a qualification."""
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from e2e import devices as d

directory = Path('/tmp/opencode/atw-device-cebbcf988acc4a7ea1c3392560636829')
value = d.load(directory)


def cli(*arguments):
    command = [sys.executable, 'scripts/e2e-devices.py', '--directory', str(directory), *arguments]
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, timeout=120)
    print(json.dumps({'command': command, 'exitCode': result.returncode,
                      'stdout': result.stdout, 'stderr': result.stderr}), flush=True)
    return result


def default_adb():
    command = [value['config']['linuxAdb'], '-H', '127.0.0.1', '-P', '5037', 'devices', '-l']
    result = subprocess.run(command, capture_output=True, text=True, timeout=10)
    assert result.returncode == 0
    return result.stdout


before_default = default_adb()
baseline = json.loads((directory / 'baseline-windows.json').read_text())
assert (directory / 'ready.json').exists()
identity = d.appium_identity(value)
assert identity is not None and d.appium_listener(value)
print('Readiness before controlled failure:', (directory / 'ready.json').read_text(), flush=True)
print('Stopping only owned Appium identity:', json.dumps(identity), flush=True)
assert d.process_identity(identity['pid']) == identity
os.kill(identity['pid'], signal.SIGTERM)
until = time.monotonic() + 10
while d.process_identity(identity['pid']) and time.monotonic() < until:
    time.sleep(.1)
assert d.process_identity(identity['pid']) is None
failed = cli('--timeout', '2', 'wait')
assert failed.returncode != 0
assert 'Owned Appium process is not alive' in failed.stderr
assert not (directory / 'ready.json').exists(), 'Failed readiness retained a usable handoff'
print('PASS controlled owned-Appium loss: setup nonzero, no ready handoff', flush=True)
assert cli('cleanup').returncode == 0
assert (directory / 'cleaned.json').exists()
assert not (directory / 'ready.json').exists()
for key in ('adbPort', 'consolePort', 'appiumPort', 'systemPort'):
    assert not d.linux_listening(value['config'][key])
assert not d.linux_listening(value['config']['consolePort'] + 1)
after = d.windows(value, 'inventory')
assert after['androidProcesses'] == baseline['androidProcesses'] == [], 'Unrelated Windows Android inventory changed'
assert default_adb() == before_default, 'Default ADB changed'
assert not any(l['port'] in (value['config']['adbPort'], value['config']['consolePort'],
                            value['config']['consolePort'] + 1) for l in after['listeners'])
print('PASS exact Windows-native shutdown: owned ADB/AVD processes and Windows ports gone; default ADB unchanged', flush=True)
print('Retained Windows baseline:', json.dumps(baseline), flush=True)
print('Windows final inventory:', json.dumps(after), flush=True)
print('Linux listener status including earlier mirrored-probe ports:', json.dumps({str(p): d.linux_listening(p)
      for p in (5037, 5038, 5560, 5561, 14723, 18200, 15038, 5564, 5565, 14733, 18210)}), flush=True)
for file in directory.glob('launchers.jsonl'):
    for line in file.read_text().splitlines():
        original = json.loads(line)
        current = d.process_identity(original['pid'])
        # PID reuse does not mean our launcher survived; never signal it.
        assert current != original, 'Owned WSL launcher still alive after services stopped'
print('PASS WSL detached launch proxies no longer alive under recorded identities', flush=True)
for name in ('instance.json', 'versions.json'):
    print(name + ':', (directory / name).read_text(), flush=True)
for name in ('tools-install', 'driver-install'):
    path = ROOT / f'docs/agent/implementation-runs/20261022T000413Z-fd60bcea-{name}.json'
    assert json.loads(path.read_text())['exitCode'] == 0
print('LIMITATION: Windows loopback broker listeners left by Linux sockets are not owned services. No global networking/broker cleanup attempted. Same-port fresh lifecycle and complete Windows failure matrix remain unqualified.', flush=True)
