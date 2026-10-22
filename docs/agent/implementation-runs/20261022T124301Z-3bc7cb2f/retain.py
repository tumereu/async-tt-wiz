"""Retain exact input/artifact identities and lossless affected JUnit output."""
from pathlib import Path
import base64
import hashlib
import json
import sys
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path.cwd()))
from e2e import environment as e
out = Path(__file__).parent
def sha(data): return hashlib.sha256(data).hexdigest()
def write(name, value): (out / name).write_text(json.dumps(value, indent=2) + '\n')
results = {}
for name in ('DeckSaveTest', 'DeckImportControllerTest', 'AuthControllerTest'):
    path = Path('androidApp/build/test-results/testDebugUnitTest/TEST-org.asyncttwiz.android.' + name + '.xml')
    data = path.read_bytes()
    cases = list(ET.fromstring(data).iter('testcase'))
    assert cases and not any(list(c.iter(tag)) for c in cases for tag in ('failure', 'error', 'skipped'))
    results[str(path)] = dict(sha256=sha(data), bytes=len(data), base64=base64.b64encode(data).decode())
    print('PASS retained affected JUnit', name, len(cases), 'cases')
write('host-junit.json', results)
paths = [Path('androidApp/build/outputs/apk/debug/androidApp-debug.apk'),
    Path('androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk'),
    Path('server/build/application-image.json'), Path('e2e/build/adapters/image.json')]
for directory in ('androidApp/src', 'client-storage/src', 'protocol/src', 'server/src', 'e2e/src/test', 'tests/fixtures/catalogs'):
    paths += [p for p in Path(directory).rglob('*') if p.is_file()]
paths += list(Path('.').glob('**/gradle.lockfile'))
paths += [Path(p) for p in ('androidApp/build.gradle.kts', 'protocol/build.gradle.kts', 'server/build.gradle.kts',
    'client-storage/build.gradle.kts', 'gradle/libs.versions.toml')]
write('artifact-identities.json', {str(p): dict(bytes=p.stat().st_size, sha256=sha(p.read_bytes())) for p in sorted(set(paths))})
env = e.load(Path('/tmp/opencode/atw-e2e-3bc7cb2f202610220000000000000001'))
with e.locked(Path(env['directory'])):
    write('served-inventory.json', e.admin(env, '/admin/inventory'))
write('server-image.json', json.loads(Path('server/build/application-image.json').read_text()))
print('PASS exact current source/fixtures/locks/APK/image identities and hosted provider inventory')
