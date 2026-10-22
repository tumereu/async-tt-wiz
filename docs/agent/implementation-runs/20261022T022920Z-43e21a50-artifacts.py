"""Retain exact synthetic startup captures and executed checks, not product acceptance."""
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PREFIX = ROOT / 'docs/agent/implementation-runs/20261022T022920Z-43e21a50'
DEVICE = Path('/tmp/opencode/atw-device-43e21a5011114af58b9f3861313df8c1')
ENV = Path('/tmp/opencode/atw-e2e-43e21a5011114af58b9f3861313df8c1')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--write', action='store_true')
    args = parser.parse_args()
    observed = sorted((json.loads(path.read_text()) for path in DEVICE.glob('app-*/observed.json')), key=lambda value: value['checkedAt'])
    assert len(observed) >= 12
    final = json.loads((DEVICE / 'checker-final.json').read_text())
    assert final['first']['foreground']['pid'] != final['final']['foreground']['pid']
    for record in observed:
        assert record['apk'] == final['first']['apk']
        assert record['api'] == 29 and record['storage']['readOnly']
        assert not any(record['storage']['rowCounts'].values())
        for field in ('inode', 'logicalSha256', 'userVersion', 'tables', 'rowCounts', 'bytes'):
            assert record['storage'][field] == final['first']['storage'][field]
    last = final['final']
    assert last['apk']['sha256'] == hashlib.sha256((ROOT / 'androidApp/build/outputs/apk/debug/androidApp-debug.apk').read_bytes()).hexdigest()
    for kind, suffix in (('screenshot', '-screen.png'), ('hierarchy', '-hierarchy.xml')):
        source = Path(last[kind]['path'])
        data = source.read_bytes()
        target = Path(str(PREFIX) + suffix)
        assert hashlib.sha256(data).hexdigest() == last[kind]['sha256']
        if args.write:
            assert not target.exists()
            target.write_bytes(data)
        assert target.read_bytes() == data
        print('RETAINED SYNTHETIC ARTIFACT', str(target.relative_to(ROOT)), hashlib.sha256(data).hexdigest(), len(data))
    tree = ET.fromstring(Path(str(PREFIX) + '-hierarchy.xml').read_bytes())
    texts = [node.get('text', '') for node in tree.iter('node') if node.get('package') == 'org.asyncttwiz.android']
    assert 'Async Tabletop Wizards' in texts and 'Specialized tabletop simulator for asynchronous wizardry' in texts
    assert not any(text.startswith(('Opening local storage', 'Local storage could not', 'Local data needs', 'Retry')) for text in texts)
    print('ACTUAL HIERARCHY', ET.tostring(tree, encoding='unicode'))
    print('FINAL COMBINED SNAPSHOT', json.dumps(final, indent=2))
    print('ACTUAL TOOL VERSIONS', (DEVICE / 'versions.json').read_text())
    env = json.loads((ENV / 'instance.json').read_text())
    print('ACTUAL ENVIRONMENT INPUT/IMAGE IDENTITIES', json.dumps({key: env[key] for key in
          ('directory', 'owner', 'project', 'ports', 'backendOrigin', 'sourceOrigin', 'images')}))
    print('EXACT NATIVE CLEANUP', (DEVICE / 'cleaned.json').read_text())
    print('EXACT COMPOSE CLEANUP', (ENV / 'cleaned.json').read_text())
    for name in ('ready.json', 'app-ready.json', 'routes-ready.json', 'combined-ready.json', 'combined.json', 'routes.json'):
        assert not (DEVICE / name).exists()
    tests = 0
    for module, task in [('core', 'jvmTest'), ('protocol', 'jvmTest'), ('client-storage', 'testDebugUnitTest'), ('e2e', 'test')]:
        paths = sorted((ROOT / module / 'build/test-results' / task).glob('TEST-*.xml'))
        assert paths
        for path in paths:
            data = path.read_text()
            suite = ET.fromstring(data)
            assert int(suite.attrib['tests']) > 0
            assert all(int(suite.attrib[key]) == 0 for key in ('skipped', 'failures', 'errors'))
            tests += int(suite.attrib['tests'])
            print('ACTUAL JUNIT XML', path.relative_to(ROOT), data)
    assert tests == 23
    commands = [json.loads(line) for line in (DEVICE / 'commands.jsonl').read_text().splitlines()]
    assert not any('instrument' in c['command'] or 'clear' in c['command'] or 'uninstall' in c['command'] for c in commands)
    assert 'POST /session' not in (DEVICE / 'appium.log').read_text()
    print('PASS exact synthetic capture hashes, all', len(observed), 'actual startup observations retain identical logical storage/inode; 23 passing JVM tests and exact owned cleanup retained; current clean APK matches installed identity. No device instrumentation/Appium session or product networking claim.')


if __name__ == '__main__':
    main()
