"""Retain parent qualification artifacts without claiming UI E2E or instrumentation."""
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PREFIX = ROOT / 'docs/agent/implementation-runs/20261022T025919Z-a5658aee'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--write', action='store_true')
    args = parser.parse_args()
    finals = []
    for part in ('1111', '2222'):
        suffix = 'a5658aee' + part + '4af58b9f3861313df8c1'
        device = Path('/tmp/opencode/atw-device-' + suffix)
        environment = Path('/tmp/opencode/atw-e2e-' + suffix)
        final = json.loads((device / 'checker-final.json').read_text())
        finals.append(final)
        observed = [json.loads(path.read_text()) for path in device.glob('app-*/observed.json')]
        assert len(observed) >= 12
        for item in observed:
            assert item['api'] == 29 and item['storage']['readOnly']
            assert item['apk'] == final['first']['apk']
            assert not any(item['storage']['rowCounts'].values())
            for key in ('inode', 'logicalSha256', 'userVersion', 'tables', 'rowCounts', 'bytes'):
                assert item['storage'][key] == final['first']['storage'][key]
        assert final['first']['foreground']['pid'] != final['final']['foreground']['pid']
        assert len(final['combined']['routes']['probes']) == 17
        assert final['final']['apk']['sha256'] == digest(ROOT / 'androidApp/build/outputs/apk/debug/androidApp-debug.apk')
        for name in ('ready.json', 'app-ready.json', 'routes-ready.json', 'combined-ready.json', 'combined.json', 'routes.json'):
            assert not (device / name).exists()
        assert not (environment / 'db-password').exists() and not (environment / 'harness-token').exists()
        print('ACTUAL STARTUP OBSERVATIONS', part, len(observed))
        print('FINAL COMBINED SNAPSHOT', part, json.dumps(final, indent=2))
        print('ACTUAL TOOL VERSIONS', part, (device / 'versions.json').read_text())
        env = json.loads((environment / 'instance.json').read_text())
        print('ACTUAL ENVIRONMENT INPUT/IMAGE IDENTITIES', part, json.dumps({key: env[key] for key in
              ('directory', 'owner', 'project', 'ports', 'backendOrigin', 'sourceOrigin', 'images')}))
        print('EXACT NATIVE CLEANUP', part, (device / 'cleaned.json').read_text())
        print('EXACT COMPOSE CLEANUP', part, (environment / 'cleaned.json').read_text())
        commands = (device / 'commands.jsonl').read_text()
        print('ACTUAL DEVICE/TOOL COMMAND OUTPUT', part, commands)
        assert not any('instrument' in row['command'] or 'clear' in row['command'] or 'uninstall' in row['command']
                       for row in map(json.loads, commands.splitlines()))
        assert 'POST /session' not in (device / 'appium.log').read_text()
    assert finals[0]['first']['apk'] == finals[1]['first']['apk']
    last = finals[-1]['final']
    for kind, suffix in (('screenshot', '-screen.png'), ('hierarchy', '-hierarchy.xml')):
        source = Path(last[kind]['path'])
        data = source.read_bytes()
        target = Path(str(PREFIX) + suffix)
        assert digest(source) == last[kind]['sha256']
        if args.write:
            assert not target.exists()
            target.write_bytes(data)
        assert target.read_bytes() == data
        print('RETAINED SYNTHETIC ARTIFACT', str(target.relative_to(ROOT)), digest(target), len(data))
    tree = ET.fromstring(Path(str(PREFIX) + '-hierarchy.xml').read_bytes())
    texts = [node.get('text', '') for node in tree.iter('node') if node.get('package') == 'org.asyncttwiz.android']
    assert 'Async Tabletop Wizards' in texts and 'Specialized tabletop simulator for asynchronous wizardry' in texts
    assert not any(text.startswith(('Opening local storage', 'Local storage could not', 'Local data needs', 'Retry')) for text in texts)
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
    tools = Path('/tmp/opencode/atw-device-tools-932550a0')
    qualified = json.loads((tools / 'qualified.json').read_text())
    for path, expected in qualified['inputs'].items():
        assert digest(ROOT / 'e2e/device-tools' / path) == expected
        assert digest(tools / path) == expected
    packages = {}
    for path in (tools / 'appium/node_modules/appium/package.json', tools / 'driver/node_modules/appium-uiautomator2-driver/package.json'):
        package = json.loads(path.read_text())
        group = 'appium' if package['name'] == 'appium' else 'driver'
        expected = qualified['packages'][group][package['name']]
        assert digest(path) == expected['manifestSha256']
        packages[package['name']] = expected
    print('QUALIFIED UNCHANGED TOOL INPUTS/PACKAGES', json.dumps({'qualificationSha256': digest(tools / 'qualified.json'), 'inputs': qualified['inputs'], 'packages': packages}, indent=2))
    print('PASS both actual attempts retained; first inventory failure remains diagnostic, full unchanged repeat passes. Exact synthetic capture, installed/rebuilt APK identity, real empty retained SQLite, 23 unskipped JVM tests, pinned tools and exact cleanup verified. No product HTTP/Appium session/instrumentation/parent review/release claim.')


if __name__ == '__main__':
    main()
