"""Retain this failed lifecycle's already-redacted adapter output and exact installed/build identities."""
from pathlib import Path
import base64
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[4]
PREFIX = Path(__file__).parent
active = json.loads((PREFIX / 'active-environment.json').read_text())
files = [p for p in Path(active['environment']).glob('capture-*.json')]
files += [Path(active['environment']) / name for name in ('reset-pending.json', 'cleaned.json')]
for index, directory in enumerate(active['devices']):
    files += [Path(directory) / name for name in ('instance.json', 'versions.json', 'app-installed.json', 'cleaned.json')]
retained = {}
for path in files:
    if path.is_file():
        data = path.read_bytes()
        retained[str(path)] = dict(base64=base64.b64encode(data).decode(), bytes=len(data), sha256=hashlib.sha256(data).hexdigest())
(PREFIX / 'adapter-records.json').write_text(json.dumps(retained, indent=2) + '\n')
paths = ['e2e/src/test/kotlin/org/asyncttwiz/e2e/NativeLobbyCompletionTest.kt',
    'androidApp/build/outputs/apk/debug/androidApp-debug.apk',
    'androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk',
    'e2e/environment.py', 'tests/test_environment_game_reset.py', 'docs/agent/implementation-state.json']
identities = {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in paths}
state = 'docs/agent/implementation-state.json'
assert (ROOT / state).read_bytes() == subprocess.check_output(['git', 'show', 'b6756135461120951cdf25a76732f54783bac0bf:' + state], cwd=ROOT)
(PREFIX / 'identities.json').write_text(json.dumps(dict(sha256=identities, stateByteIdentical=True,
    nativeJourneyAssertionsExecuted=False, normalApksInstalled=2), indent=2) + '\n')
print('Retained exact redacted adapter captures, migration applied=10 against reset oracle 1..9, installed APK/device identities and unchanged authoritative state')
