"""Retain actual JVM XML, input identities and all run-owned cleanup records."""
import base64
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PREFIX = '20261022T013255Z-043555f3'
reports = ROOT / 'docs/agent/implementation-runs'
result = {'junit': [], 'ownedDevices': {}, 'ownedEnvironments': {}, 'sourceHashes': {}}
for module, task in (('core', 'jvmTest'), ('protocol', 'jvmTest'), ('e2e', 'test')):
    files = sorted((ROOT / module / 'build/test-results' / task).glob('TEST-*.xml'))
    assert files, module
    for file in files:
        raw = file.read_bytes()
        suite = ET.fromstring(raw)
        assert int(suite.attrib['tests']) > 0
        assert all(int(suite.attrib[k]) == 0 for k in ('failures', 'errors', 'skipped'))
        result['junit'].append({'path': str(file.relative_to(ROOT)), 'attributes': suite.attrib,
                                'sha256': hashlib.sha256(raw).hexdigest(), 'xmlBase64': base64.b64encode(raw).decode()})
outputs = [json.loads(file.read_text())['output'] for file in reports.glob(PREFIX + '-*.json')
           if file.name not in (PREFIX + '-artifacts.json', PREFIX + '-validation.json')]
for kind, key in (('device', 'ownedDevices'), ('e2e', 'ownedEnvironments')):
    paths = set(re.findall(r'/tmp/opencode/atw-' + kind + r'-[a-f0-9]{32}', '\n'.join(outputs)))
    for path in sorted(paths):
        directory = Path(path)
        if not directory.exists():
            continue  # Refused provision before mkdir does not own a resource.
        assert (directory / 'cleaned.json').is_file(), path
        assert not (directory / 'ready.json').exists(), path
        assert not (directory / 'routes.json').exists(), path
        assert not (directory / 'routes-ready.json').exists(), path
        result[key][path] = json.loads((directory / 'cleaned.json').read_text())
for path in ('e2e/devices.py', 'e2e/device_routes.py', 'e2e/windows-device.ps1',
             'scripts/check-device-routes.py', 'tests/test_device_routes.py'):
    result['sourceHashes'][path] = hashlib.sha256((ROOT / path).read_bytes()).hexdigest()
print(json.dumps(result, indent=2))
print('PASS exact actual passing unskipped JVM XML retained losslessly, final controller source hashes and every observed run-owned device/environment cleaned; no source/app/UI acceptance inferred.')
