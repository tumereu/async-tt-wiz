"""Targeted normal-launcher fullscreen/Back recheck on the exact owned transport."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from e2e import devices as d, device_routes as r, device_session as s

directory = Path('/tmp/opencode/atw-device-df665f9811114af58b9f3861313df8c1')
with d.lock():
    value = d.load(directory)
    s.idle(value)
    d.check_windows(value, d.windows(value, 'snapshot'))
    d.check_mappings(value, 'emulator-5564')
    env = {k: v for k, v in os.environ.items() if not k.startswith(('ADB_', 'ANDROID_SERIAL', 'ANDROID_ADB_'))}
    env.update(ANDROID_SERIAL='emulator-5564', ANDROID_ADB_SERVER_PORT='15038',
               ANDROID_ADB_SERVER_ADDRESS='127.0.0.1', ADB_SERVER_SOCKET='tcp:127.0.0.1:15038')
    command = [str(ROOT / 'scripts/gradle.sh'), '--no-daemon', '--console=plain',
               '-PatwApiOrigin=http://127.0.0.1:46277', ':androidApp:connectedDebugAndroidTest',
               '-Pandroid.testInstrumentationRunnerArguments.class=org.asyncttwiz.android.ShellPresentationTest']
    started = time.time()
    result = subprocess.run(command, cwd=ROOT, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=1200)
    files = [p for p in (ROOT / 'androidApp/build/outputs/androidTest-results/connected').rglob('*.xml') if p.stat().st_mtime >= started]
    logcats = [p for p in (ROOT / 'androidApp/build/outputs/androidTest-results/connected').rglob('logcat-*') if p.stat().st_mtime >= started]
    print(json.dumps({'command': command, 'exitCode': result.returncode, 'output': result.stdout.decode(),
                      'outputSha256': hashlib.sha256(result.stdout).hexdigest(),
                      'presentationLog': [line for p in logcats for line in p.read_text().splitlines() if 'System.out' in line],
                      'xml': {str(p.relative_to(ROOT)): p.read_text() for p in files}}, indent=2))
    r.invalidate(value)
    assert result.returncode == 0
    tests = [case for path in files for case in ET.parse(path).iter('testcase')]
    assert len(tests) == 1 and not any(list(case.iter(tag)) for case in tests for tag in ('failure', 'error', 'skipped'))
