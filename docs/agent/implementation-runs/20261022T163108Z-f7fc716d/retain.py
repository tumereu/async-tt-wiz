"""Retain exact executed test records and tested inputs; no additional product test execution."""
from pathlib import Path
import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET

prefix = Path(__file__).parent
(prefix / 'application-image.json').write_bytes(Path('server/build/application-image.json').read_bytes())
instance = json.loads(Path('/tmp/opencode/atw-e2e-f7fc716d000000000000000000000001/instance.json').read_text())
(prefix / 'environment-inputs.json').write_text(json.dumps({k: instance[k] for k in ('images', 'inputs')}, indent=2) + '\n')
server = {str(p): p.read_text() for p in Path('server/build/test-results/test').glob('TEST-*.xml')}
(prefix / 'lobby-junit.json').write_text(json.dumps(server, indent=2) + '\n')
expected = {'protocol-junit': 1, 'client-junit': 17, 'decks-junit': 10, 'lobby-junit': 4}
for name, count in expected.items():
    records = json.loads((prefix / (name + '.json')).read_text())
    tests = [case for value in records.values() for case in ET.fromstring(value).iter('testcase')]
    assert len(tests) == count
    assert not any(list(case.iter(tag)) for case in tests for tag in ('failure', 'error', 'skipped'))
    print('PASS retained', name, count, 'actual non-skipped green tests')
native = prefix / 'runner-1c83d464c1d24de9836fe0941419f090-result-0.xml'
tests = list(ET.parse(native).iter('testcase'))
assert len(tests) == 1 and not any(list(case.iter(tag)) for case in tests for tag in ('failure', 'error', 'skipped'))
for name in ('1c83d464c1d24de9836fe0941419f090', 'cfd0081448e24cd2bb4922c841f2c324'):
    assert json.loads((prefix / ('runner-' + name + '-session-closed.json')).read_text())['closed']
for name in ('native-journey-qualified', 'cleanup-first', 'cleanup-second', 'cleanup-environment'):
    assert json.loads((prefix / (name + '.json')).read_text())['exitCode'] == 0
paths = set()
for root in ('server/src', 'protocol/src', 'androidApp/src'):
    paths.update(p for p in Path(root).rglob('*') if p.is_file())
for file in ('e2e/environment.py', 'e2e/src/test/kotlin/org/asyncttwiz/e2e/NativeLobbyCreationTest.kt',
    'androidApp/build.gradle.kts', 'androidApp/gradle.lockfile', 'server/build.gradle.kts', 'server/gradle.lockfile',
    'protocol/build.gradle.kts', 'protocol/gradle.lockfile', 'gradle/libs.versions.toml',
    'androidApp/build/outputs/apk/debug/androidApp-debug.apk', 'server/build/application-image.json'):
    paths.add(Path(file))
(prefix / 'input-identities.json').write_text(json.dumps({str(p): dict(bytes=p.stat().st_size,
    sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in sorted(paths)}, indent=2) + '\n')
print('PASS one complete two-normal-APK journey and exact final source/lock/APK/image identities; owned sessions/devices/Compose cleaned')
print('NOT CLAIMED: invitation decisions, preparation/start/cancellation, complete B3, broad qualification, final UI comparison or external gates')
