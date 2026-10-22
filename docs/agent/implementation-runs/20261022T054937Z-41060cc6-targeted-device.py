"""Affected failed input check only; exact owned transport, no reset/reinstall/data wipe."""
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from e2e import devices as d, device_app as a, device_session as s

directory = Path('/tmp/opencode/atw-device-41060cc611114af58b9f3861313df8c1')
test_class = sys.argv[1] if len(sys.argv) > 1 else 'AccountEntryTest'
assert test_class in ('AccountEntryTest', 'AuthSavedStateTest')
with d.lock():
    value = d.load(directory)
    s.idle(value)
    d.check_mappings(value, 'emulator-5564')
    # AGP's connected runner uninstalls its app-under-test even after a red test.
    # Reinstall the same normal artifact using the qualified owner path, not a wipe.
    a.operation(value, 'install-app', 180, str(ROOT / 'androidApp/build/outputs/apk/debug/androidApp-debug.apk'))
    a.operation(value, 'launch-app', 180)
    ready = json.loads((directory / 'ready.json').read_text())
    env = dict(os.environ, ANDROID_SERIAL='emulator-5564', ANDROID_ADB_SERVER_PORT='15038',
               ANDROID_ADB_SERVER_ADDRESS='127.0.0.1', ADB_SERVER_SOCKET='tcp:127.0.0.1:15038')
    command = [str(ROOT / 'scripts/gradle.sh'), '--no-daemon', '--console=plain',
               '-PatwApiOrigin=http://127.0.0.1:18080', ':androidApp:connectedDebugAndroidTest',
               '-Pandroid.testInstrumentationRunnerArguments.class=org.asyncttwiz.android.' + test_class]
    print(json.dumps({'command': command, 'ownedSerial': ready['device']['serial']}), flush=True)
    result = subprocess.run(command, cwd=ROOT, env=env)
    # Retain raw JUnit bytes before another focused invocation can replace AGP output.
    files = sorted((ROOT / 'androidApp/build/outputs/androidTest-results/connected').rglob('*.xml'))
    (Path(__file__).with_name('20261022T054937Z-41060cc6-' + test_class + '-xml.json')).write_text(
        json.dumps({str(p.relative_to(ROOT)): p.read_text() for p in files}, indent=2) + '\n')
    raise SystemExit(result.returncode)
