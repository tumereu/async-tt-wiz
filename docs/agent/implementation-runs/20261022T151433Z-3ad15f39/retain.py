"""Retain exact input/artifact identities and check existing actual outputs, without rerunning journeys."""
from pathlib import Path
import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET

prefix = Path(__file__).parent
roots = ['androidApp/src/main/kotlin', 'androidApp/src/test/kotlin', 'androidApp/src/androidTest/kotlin',
    'tests/fixtures/catalogs', 'gradle']
paths = {path for root in roots for path in Path(root).rglob('*') if path.is_file()}
paths |= {Path(p) for p in ['androidApp/build.gradle.kts', 'androidApp/gradle.lockfile',
    'e2e/src/test/kotlin/org/asyncttwiz/e2e/NativeCardInspectionTest.kt',
    'androidApp/build/outputs/apk/debug/androidApp-debug.apk',
    'androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk']}
identities = {str(path): dict(bytes=path.stat().st_size, sha256=hashlib.sha256(path.read_bytes()).hexdigest())
    for path in sorted(paths)}
(prefix / 'input-identities.json').write_text(json.dumps(identities, indent=2) + '\n')
instance = json.loads(Path('/tmp/opencode/atw-e2e-3ad15f39000000000000000000000001/instance.json').read_text())
(prefix / 'environment-inputs.json').write_text(json.dumps({key: instance[key] for key in ('images', 'inputs')}, indent=2) + '\n')
cases = [case for value in json.loads((prefix / 'host-junit.json').read_text()).values()
    for case in ET.fromstring(value).iter('testcase')]
assert len(cases) == 17 and not any(list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped'))
for run in ('runner-adf52ab16065449486258bd34dc2052b', 'runner-cd463eab7d24454c9d5d5a11c248505e'):
    cases = list(ET.parse(prefix / (run + '-result-0.xml')).iter('testcase'))
    assert len(cases) == 1 and not any(list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped'))
assert 'OK (2 tests)' in json.loads((prefix / 'artwork-instrumentation.json').read_text())['output']
print('PASS retained 17 focused host cases, green retained restart and final full-art native checks, actual two-test Android artwork size/delayed-lifecycle output and exact final inputs/APKs/image identities')
print('Initial full native command remains diagnostic; its successful preview/saved segments are reused, not relabeled as a green whole journey or as final-renderer evidence')
