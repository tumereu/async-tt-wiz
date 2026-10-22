"""Retain this unit's synthetic check output losslessly; never copy credentials or DBs."""
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PREFIX = 'docs/agent/implementation-runs/20261022T070751Z-df665f98'
OUT = ROOT / PREFIX
DEVICE = Path('/tmp/opencode/atw-device-df665f9811114af58b9f3861313df8c1')
ENV = Path('/tmp/opencode/atw-e2e-df665f9811114af58b9f3861313df8c1')


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def retain(source, destination):
    raw = source.read_bytes()
    destination.parent.mkdir(parents=True, exist_ok=True)
    if source.suffix == '.png':
        destination.write_bytes(raw)
    else:
        destination.write_text(json.dumps({'format': 'lossless-utf8-output-v1', 'output': raw.decode(),
                                         'originalBytes': len(raw), 'originalSha256': sha(raw)}, indent=2) + '\n')
    print(json.dumps({'source': str(source), 'retained': str(destination.relative_to(ROOT)), 'sha256': sha(raw)}))


for label, name in [('profile', 'runner-e248eaa9647045a080b435efd20f8256'),
                    ('shell-initial-diagnostic', 'runner-1ba08a81d27847d680866a29440ae60d'),
                    ('shell-passing-before-final-fullscreen-fix', 'runner-ad3ddbc63fd643f6b652f9133870601a')]:
    for path in sorted((DEVICE / name).iterdir()):
        if path.name in ('command.json', 'exit.json', 'passed.json', 'ready.json', 'session.json', 'session-closed.json',
                         'test-apk.json', 'shell-observation.json', 'gradle.txt') or path.suffix in ('.xml', '.png'):
            retain(path, OUT / label / path.name)
for path, name in [(DEVICE / 'cleaned.json', 'device-cleaned.json'), (ENV / 'cleaned.json', 'environment-cleaned.json'),
                   (DEVICE / 'instance.json', 'device-instance.json')]:
    retain(path, OUT / name)
for module, task in [('androidApp', 'testDebugUnitTest'), ('client-storage', 'testDebugUnitTest')]:
    for path in sorted((ROOT / module / 'build/test-results' / task).glob('TEST-*.xml')):
        retain(path, OUT / (module + '-' + path.name + '.json'))
for path in (ROOT / 'androidApp/build/outputs/androidTest-results/connected').rglob('*.xml'):
    retain(path, OUT / ('final-presentation-' + path.name + '.json'))

# Preserve original command bytes rather than stripping whitespace to pass the save hook.
for path in (ROOT / 'docs/agent/implementation-runs').glob('20261022T070751Z-df665f98-*.txt'):
    raw = path.read_bytes()
    text = raw.decode()
    if any(line != line.rstrip() for line in text.splitlines()):
        path.write_text(json.dumps({'format': 'lossless-utf8-output-v1', 'output': text,
                                   'originalBytes': len(raw), 'originalSha256': sha(raw)}, indent=2) + '\n')

inputs = {}
for directory in ('androidApp/src', 'client-storage/src', 'protocol/src'):
    for path in (ROOT / directory).rglob('*'):
        if path.is_file():
            inputs[str(path.relative_to(ROOT))] = {'sha256': sha(path.read_bytes()), 'bytes': path.stat().st_size}
for path in ('androidApp/build.gradle.kts', 'androidApp/gradle.lockfile', 'client-storage/build.gradle.kts',
             'client-storage/gradle.lockfile', 'gradle/libs.versions.toml', 'gradle.properties',
             'e2e/src/test/kotlin/org/asyncttwiz/e2e/NativeShellTest.kt', 'e2e/device_app.py', 'e2e/device_runner.py',
             'androidApp/build/outputs/apk/debug/androidApp-debug.apk',
             'androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk'):
    raw = (ROOT / path).read_bytes()
    inputs[path] = {'sha256': sha(raw), 'bytes': len(raw)}
(OUT / 'final-input-identities.json').write_text(json.dumps(inputs, indent=2, sort_keys=True) + '\n')
for path in (OUT / 'profile/result-0.xml', OUT / 'shell-passing-before-final-fullscreen-fix/result-0.xml'):
    tree = ET.fromstring(json.loads(path.read_text())['output'])
    cases = list(tree.iter('testcase'))
    assert len(cases) == (2 if path.parent.name == 'profile' else 1)
    assert not any(list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped'))
    print('PASS retained actual non-skipped cases', len(cases), path.relative_to(ROOT))
assert json.loads((DEVICE / 'cleaned.json').read_text())['state']['cleaned']
assert not json.loads((DEVICE / 'cleaned.json').read_text())['listeners']
print('PASS exact synthetic outputs and final input/APK identities retained; screenshots are explicitly before final API29 correction, not final mock acceptance')
