"""Retain this unit's actual JUnit and exact source/artifact identities, not new test execution."""
from pathlib import Path
import hashlib
import json
import xml.etree.ElementTree as ET

prefix = Path(__file__).parent
roots = ['protocol/src', 'server/src', 'androidApp/src', 'tests/fixtures/catalogs', 'gradle']
paths = {path for root in roots for path in Path(root).rglob('*') if path.is_file()}
paths |= {Path(p) for p in ['androidApp/build.gradle.kts', 'androidApp/gradle.lockfile',
    'server/build.gradle.kts', 'server/gradle.lockfile', 'protocol/build.gradle.kts', 'protocol/gradle.lockfile',
    'e2e/src/test/kotlin/org/asyncttwiz/e2e/NativeDeckReimportTest.kt',
    'androidApp/build/outputs/apk/debug/androidApp-debug.apk',
    'server/build/application-image.json']}
identities = {str(path): dict(bytes=path.stat().st_size, sha256=hashlib.sha256(path.read_bytes()).hexdigest())
    for path in sorted(paths)}
(prefix / 'input-identities.json').write_text(json.dumps(identities, indent=2) + '\n')
instance = json.loads(Path('/tmp/opencode/atw-e2e-ad37196f000000000000000000000001/instance.json').read_text())
(prefix / 'environment-inputs.json').write_text(json.dumps({key: instance[key] for key in ('images', 'inputs')}, indent=2) + '\n')
(prefix / 'application-image.json').write_bytes(Path('server/build/application-image.json').read_bytes())
junit = {}
for root in ['protocol/build/test-results/jvmTest', 'server/build/test-results/test', 'androidApp/build/test-results/testDebugUnitTest']:
    for path in Path(root).glob('TEST-*.xml'):
        junit[str(path)] = path.read_text()
(prefix / 'host-junit.json').write_text(json.dumps(junit, indent=2) + '\n')
cases = [case for value in junit.values() for case in ET.fromstring(value).iter('testcase')]
assert cases and not any(list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped'))
required = {'DecksTest', 'DeckHttpTest', 'DeckSaveTest', 'DeckManagementTest', 'DeckImportControllerTest', 'AuthControllerTest'}
assert {case.attrib['classname'].rsplit('.', 1)[-1] for case in cases} == required
for name in sorted(required):
    print(name, sum(case.attrib['classname'].endswith('.' + name) for case in cases), 'passed')
native = list(ET.parse(prefix / 'runner-f150b4a134294290ab4051d1443d8c3a-result-0.xml').iter('testcase'))
assert len(native) == 1 and not any(list(case.iter(tag)) for case in native for tag in ('failure', 'error', 'skipped'))
assert json.loads((prefix / 'runner-f150b4a134294290ab4051d1443d8c3a-session-closed.json').read_text())['closed']
for name in ['affected-host', 'image-build', 'device-provision', 'environment-provision', 'hosted-build', 'prepare-app', 'native-journey']:
    assert json.loads((prefix / (name + '.json')).read_text())['exitCode'] == 0
print('PASS', len(cases), 'affected host cases, one complete native journey and actual command outputs; final tested source/APK/image/fixture/lock identities retained')
print('NOT CLAIMED: selected lobby/game copies, second-device content-edit journey, whole cross-feature scenarios, full suite/compatibility/final mock comparison, independent B2 review or external release gates')
