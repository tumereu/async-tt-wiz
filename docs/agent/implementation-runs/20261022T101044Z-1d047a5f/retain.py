"""Retain lossless run-owned text/JUnit, selected native captures and actual input identities."""
from pathlib import Path
import base64
import hashlib
import json
import shutil
import sys
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path.cwd()))
from e2e import environment as e

out = Path(__file__).parent
device = Path('/tmp/opencode/atw-device-1d047a5f11114af58b9f3861313df8c1')
def digest(data):
    return hashlib.sha256(data).hexdigest()
def envelope(path):
    data = path.read_bytes()
    return {'sha256': digest(data), 'bytes': len(data), 'base64': base64.b64encode(data).decode()}
def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')

for run in sorted(device.glob('runner-*')):
    if not run.is_dir():
        continue
    files = {str(path.relative_to(run)): envelope(path) for path in sorted(run.iterdir())
             if path.suffix in ('.txt', '.xml', '.json') and path.name != 'runner.properties'}
    write(out / (run.name + '.json'), files)
    for path in run.glob('catalog-*.png'):
        shutil.copyfile(path, out / (run.name + '-' + path.name))
    print('Retained native run', run.name, 'passed marker:', (run / 'passed.json').exists())

host = {}
for module in ('client-storage', 'androidApp'):
    for path in Path(module + '/build/test-results/testDebugUnitTest').glob('*.xml'):
        host[str(path)] = envelope(path)
        cases = list(ET.parse(path).iter('testcase'))
        assert cases and not any(list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped'))
        print('Current affected host results:', path.name, len(cases), 'passing')
write(out / 'host-junit.json', host)

identities = {}
paths = [Path('androidApp/build/outputs/apk/debug/androidApp-debug.apk'),
         Path('androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk')]
paths += sorted(Path('tests/fixtures/catalogs').rglob('*'))
paths += sorted(Path('client-storage/src/main/kotlin').rglob('*.kt'))
paths += sorted(Path('androidApp/src/main/kotlin').rglob('*.kt'))
paths += [Path(path) for path in ('client-storage/gradle.lockfile', 'androidApp/gradle.lockfile',
    'client-storage/build.gradle.kts', 'androidApp/build.gradle.kts', 'gradle/libs.versions.toml',
    'server/build/application-image.json', 'e2e/build/synthetic-adapter-image.json') if Path(path).is_file()]
for path in paths:
    if path.is_file():
        data = path.read_bytes()
        identities[str(path)] = {'bytes': len(data), 'sha256': digest(data)}
write(out / 'artifact-identities.json', identities)
value = e.load(Path('/tmp/opencode/atw-e2e-1d047a5f11114af58b9f3861313df8c1'))
with e.locked(Path(value['directory'])):
    write(out / 'served-inventory.json', e.admin(value, '/admin/inventory'))
print('PASS retained actual host/native outputs, screenshot bytes, APK/source/lock/fixture identities and served-source inventory')
