"""Retain this unit's actual test output/UI captures, never mailbox bodies, raw ADB/Appium logs or credentials."""
import base64
import hashlib
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET

root = Path.cwd()
output = root / 'docs/agent/implementation-runs/20261022T084055Z-9e1f1522'
records = {}
for module, path in [('android-host', 'androidApp/build/test-results/testDebugUnitTest'),
                     ('server', 'server/build/test-results/test')]:
    for file in sorted((root / path).glob('*.xml')):
        if module == 'server' and 'PasswordResetHttpTest' not in file.name:
            continue
        records[module + '/' + file.name] = base64.b64encode(file.read_bytes()).decode()
(output / 'host-junit.json').write_text(json.dumps(records, indent=2) + '\n')

for role, suffix in [('first', '1111'), ('second', '2222')]:
    device = Path('/tmp/opencode/atw-device-9e1f1522' + suffix + '4af58b9f3861313df8c1')
    config = Path('/tmp/opencode/i04b-' + role + '-config.json')
    shutil.copyfile(config, output / ('device-config-' + role + '.json'))
    for run in sorted(device.glob('runner-*')):
        if not run.is_dir():
            continue
        retained = {}
        for file in sorted(run.iterdir()):
            if file.suffix not in ('.json', '.xml', '.png') and file.name != 'gradle.txt':
                continue
            # Only the runner's explicit output/capture files; no environment secret or mailbox source file.
            if file.suffix == '.png':
                target = output / (role + '-' + run.name + '-' + file.name)
                shutil.copyfile(file, target)
                retained[file.name] = {'path': str(target.relative_to(root)), 'sha256': hashlib.sha256(file.read_bytes()).hexdigest()}
            else:
                data = file.read_bytes()
                assert b'token=' not in data or not __import__('re').search(rb'token=[A-Za-z0-9_-]{43}', data), 'Do not retain raw reset links'
                retained[file.name] = {'sha256': hashlib.sha256(data).hexdigest(), 'base64': base64.b64encode(data).decode()}
        (output / (role + '-' + run.name + '.json')).write_text(json.dumps(retained, indent=2) + '\n')
        for name, entry in retained.items():
            if name.startswith('result-') and name.endswith('.xml'):
                suite = ET.fromstring(base64.b64decode(entry['base64']))
                print('ACTUAL JUNIT', role, run.name, suite.attrib)
    cleaned = json.loads((device / 'cleaned.json').read_text())
    (output / ('cleaned-' + role + '.json')).write_text(json.dumps(cleaned, indent=2) + '\n')

identities = {}
for path in ['androidApp/build/outputs/apk/debug/androidApp-debug.apk',
             'androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk',
             'server/build/application-image.json']:
    file = root / path
    if file.exists():
        identities[path] = hashlib.sha256(file.read_bytes()).hexdigest()
manifest = root / 'server/build/application-image.json'
if manifest.exists():
    shutil.copyfile(manifest, output / 'image-manifest.json')
sources = [p for module in ('androidApp', 'e2e', 'protocol', 'server') for p in Path(module, 'src').rglob('*.kt')]
identities['sources'] = {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(sources)}
(output / 'artifact-identities.json').write_text(json.dumps(identities, indent=2) + '\n')
print('PASS retained actual host/device/JUnit/UI/cleanup output; no mailbox, raw command log or token-bearing artifact copied')
