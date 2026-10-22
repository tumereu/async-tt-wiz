"""Retain actual check artifacts, synthetic captures and input identities without private APK/DB/log dumps."""
import hashlib
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PREFIX = '20261022T054937Z-41060cc6'
OUTPUT = ROOT / 'docs/agent/implementation-runs' / PREFIX
OUTPUT.mkdir(exist_ok=True)
DEVICE = Path('/tmp/opencode/atw-device-41060cc611114af58b9f3861313df8c1')

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def retain_text(source, destination):
    text = source.read_bytes().decode('utf-8')
    destination.write_text(json.dumps({'output': text, 'outputSha256': hashlib.sha256(text.encode()).hexdigest()}, indent=2) + '\n')

for directory in sorted(DEVICE.glob('runner-*')):
    if not (directory / 'gradle.txt').exists():
        continue
    target = OUTPUT / directory.name
    target.mkdir(exist_ok=True)
    for file in directory.iterdir():
        if file.suffix == '.xml' and b'\r' in file.read_bytes():
            retain_text(file, target / file.name) # Lossless CRLF XML envelope, not normalized bytes.
        elif file.suffix in ('.png', '.xml', '.json'):
            shutil.copyfile(file, target / file.name)
        elif file.name == 'gradle.txt':
            retain_text(file, target / file.name)
    print('RETAINED actual runner', directory.name, flush=True)

for label, directory in [('device', DEVICE),
    ('old-environment', Path('/tmp/opencode/atw-e2e-41060cc611114af58b9f3861313df8c1')),
    ('environment', Path('/tmp/opencode/atw-e2e-41060cc622224af58b9f3861313df8c1'))]:
    for name in ('cleaned.json', 'reset-completed.json', 'versions.json'):
        file = directory / name
        if file.exists():
            shutil.copyfile(file, OUTPUT / (label + '-' + name))
    assert (directory / 'cleaned.json').exists()
    print('CONFIRMED exact owned cleanup', label, flush=True)

tests = ROOT / 'androidApp/build/test-results/testDebugUnitTest'
(OUTPUT / 'final-host-xml.json').write_text(json.dumps({str(p.relative_to(ROOT)): p.read_text()
    for p in sorted(tests.glob('*.xml'))}, indent=2) + '\n')

paths = list((ROOT / 'androidApp/src').rglob('*')) + list((ROOT / 'e2e/src').rglob('*.kt'))
paths += [ROOT / p for p in ('androidApp/build.gradle.kts', 'androidApp/gradle.lockfile',
    'gradle/libs.versions.toml', 'e2e/build.gradle.kts', 'e2e/gradle.lockfile', 'e2e/environment.py',
    'e2e/device_runner.py', 'protocol/src/commonMain/kotlin/org/asyncttwiz/protocol/Accounts.kt',
    'server/build/application-image.json', 'server/database-images.properties',
    'androidApp/build/outputs/apk/debug/androidApp-debug.apk',
    'androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk')]
identities = {str(p.relative_to(ROOT)): {'sha256': sha(p), 'bytes': p.stat().st_size}
              for p in sorted(paths) if p.is_file()}
(OUTPUT / 'input-identities.json').write_text(json.dumps(identities, indent=2) + '\n')
passing = OUTPUT / 'runner-896baf0a99614aae85a9747f0d8ddeb1'
ready = json.loads((passing / 'ready.json').read_text())
assert ready['app']['apk']['sha256'] == identities['androidApp/build/outputs/apk/debug/androidApp-debug.apk']['sha256']
testcases = [case for file in passing.glob('result-*.xml') for case in ET.parse(file).iter('testcase')]
assert len(testcases) == 1 and not any(list(case.iter(tag)) for case in testcases for tag in ('failure', 'error', 'skipped'))
assert (passing / 'passed.json').is_file() and (passing / 'session-closed.json').is_file()
print('PASS current normal APK matches actual passing native account journey; exact source/config/lock/image identities retained')
print('Initial five-test Android output remains red: four green startup/Keystore tests plus one red input oracle. Exact-input targeted recheck and saved-state test pass separately.')
print('The initial host unit task ran successfully before lint failed; later network assertion was checked alone. Initial host XML was replaced by the targeted run; original console/task output remains retained.')
print('No repeated full suite/clean build/fault matrix, independent group review, later account/game/reset outcome or release gate is claimed.')

# Lossless text envelopes preserve Gradle trailing whitespace, CRs and ANSI bytes
# as escaped strings while remaining safe for the required staged-whitespace check.
for path in sorted((ROOT / 'docs/agent/implementation-runs').glob(PREFIX + '-*.txt')):
    if path.name.startswith(PREFIX + '-retention'):
        continue # Never rewrite a file currently receiving this process's stdout.
    text = path.read_bytes().decode('utf-8')
    try:
        existing = json.loads(text)
    except json.JSONDecodeError:
        existing = None
    if isinstance(existing, dict) and 'outputSha256' in existing and 'output' in existing:
        continue
    path.write_text(json.dumps({'output': text, 'outputSha256': hashlib.sha256(text.encode()).hexdigest()}, indent=2) + '\n')
