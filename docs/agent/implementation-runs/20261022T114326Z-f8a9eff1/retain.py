"""Retain exact final input identities, current JUnit bytes and hosted source inventory."""
from pathlib import Path
import base64
import hashlib
import json
import sys
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path.cwd()))
from e2e import environment as e
out = Path(__file__).parent
def sha(data):
    return hashlib.sha256(data).hexdigest()
def write(name, value):
    (out / name).write_text(json.dumps(value, indent=2) + '\n')
results = {}
for module in ('client-storage', 'androidApp'):
    for path in Path(module + '/build/test-results/testDebugUnitTest').glob('*.xml'):
        data = path.read_bytes()
        cases = list(ET.fromstring(data).iter('testcase'))
        assert cases and not any(list(c.iter(t)) for c in cases for t in ('failure', 'error', 'skipped'))
        results[str(path)] = dict(sha256=sha(data), bytes=len(data), base64=base64.b64encode(data).decode())
        print('PASS retained current affected JUnit', path.name, len(cases), 'cases')
write('host-junit.json', results)
paths = [Path('androidApp/build/outputs/apk/debug/androidApp-debug.apk'),
         Path('androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk')]
paths += list(Path('tests/fixtures/catalogs').rglob('*'))
for root in ('androidApp/src', 'client-storage/src', 'e2e/src/test'):
    paths += list(Path(root).rglob('*.kt'))
paths += [Path(p) for p in ('androidApp/build.gradle.kts', 'client-storage/build.gradle.kts',
    'androidApp/gradle.lockfile', 'client-storage/gradle.lockfile', 'gradle/libs.versions.toml',
    'server/build/application-image.json', 'e2e/build/synthetic-adapter-image.json')]
write('artifact-identities.json', {str(p): dict(bytes=p.stat().st_size, sha256=sha(p.read_bytes()))
      for p in sorted(paths) if p.is_file()})
value = e.load(Path('/tmp/opencode/atw-e2e-f8a9eff111114b3e99d382647521e8ff'))
with e.locked(Path(value['directory'])):
    write('served-inventory.json', e.admin(value, '/admin/inventory'))
print('PASS exact source/test/fixture/lock/APK/image identities and actual hosted provider inventory')
