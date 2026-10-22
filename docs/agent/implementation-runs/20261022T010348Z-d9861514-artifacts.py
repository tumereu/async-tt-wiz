"""Retain/check this run's synthetic captures and actual build/device results."""
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PREFIX = ROOT / 'docs/agent/implementation-runs/20261022T010348Z-d9861514'
DEVICE = Path('/tmp/opencode/atw-device-d986151422224af58b9f3861313df8c1')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--write', action='store_true')
    args = parser.parse_args()
    observed = sorted((json.loads(p.read_text()) for p in DEVICE.glob('app-*/observed.json')), key=lambda r: r['checkedAt'])
    assert len(observed) == 4
    before, after = observed[0], observed[-1]
    assert before['foreground']['pid'] != after['foreground']['pid']
    assert after['apk']['package'] == 'org.asyncttwiz.android'
    assert after['apk']['activity'] == 'org.asyncttwiz.android.MainActivity'
    assert after['apk']['versionCode'] == 1 and after['apk']['versionName'] == '0.1.0'
    assert after['api'] == 29 and after['apk']['signerSha256']
    assert after['apk']['sha256'] == hashlib.sha256((DEVICE / 'normal.apk').read_bytes()).hexdigest()
    for record in observed:
        assert record['storage']['userVersion'] == 2 and not any(record['storage']['rowCounts'].values())
        for key in ('inode', 'logicalSha256', 'tables', 'rowCounts', 'userVersion', 'bytes'):
            assert record['storage'][key] == before['storage'][key]
    assert not (DEVICE / 'app-ready.json').exists() and not (DEVICE / 'ready.json').exists()
    assert (DEVICE / 'cleaned.json').is_file()
    for field, suffix in [('screenshot', '-screen.png'), ('hierarchy', '-hierarchy.xml')]:
        source = Path(after[field]['path'])
        target = Path(str(PREFIX) + suffix)
        data = source.read_bytes()
        assert hashlib.sha256(data).hexdigest() == after[field]['sha256']
        if args.write:
            assert not target.exists()
            target.write_bytes(data)
        assert target.read_bytes() == data
        print('COMMITTED SYNTHETIC CAPTURE', target.relative_to(ROOT), 'SHA256', hashlib.sha256(data).hexdigest(), 'bytes', len(data))
    xml = Path(str(PREFIX) + '-hierarchy.xml').read_bytes()
    tree = ET.fromstring(xml)
    texts = [node.get('text') for node in tree.iter('node') if node.get('package') == after['apk']['package']]
    assert 'Async Tabletop Wizards' in texts and 'Specialized tabletop simulator for asynchronous wizardry' in texts
    assert not any(text.startswith(('Opening local storage', 'Local storage could not', 'Local data needs', 'Retry')) for text in texts if text)
    print('ACTUAL NATIVE HIERARCHY', xml.decode())
    print('ACTUAL INSTALLED NORMAL APK/STARTUP/READ-ONLY STORAGE', json.dumps(after, sort_keys=True))

    for evidence, expected in [('build', 0), ('unit', 0), ('unit-final', 0), ('startup-first', 1), ('startup-second', 1),
                               ('sqlite-diagnosis', 0), ('startup-pass', 0), ('services-regression', 1),
                               ('services-regression-pass', 0), ('python-regressions', 0), ('storage-check', 0),
                               ('android-check', 0), ('coverage', 0), ('fixtures', 0)]:
        path = Path(str(PREFIX) + '-' + evidence + '.json')
        value = json.loads(path.read_text())
        assert value['exitCode'] == expected
        output = value['output'].encode()
        assert hashlib.sha256(output).hexdigest() == value['outputSha256']
        print('ACTUAL RETAINED CHECK', path.relative_to(ROOT), 'exitCode', expected, 'outputSha256', value['outputSha256'])
    tests = 0
    for module, task in [('core', 'jvmTest'), ('protocol', 'jvmTest'), ('client-storage', 'testDebugUnitTest'), ('e2e', 'test')]:
        paths = sorted((ROOT / module / 'build/test-results' / task).glob('TEST-*.xml'))
        assert paths
        for path in paths:
            data = path.read_text()
            suite = ET.fromstring(data)
            assert int(suite.attrib['tests']) > 0 and all(int(suite.attrib[k]) == 0 for k in ('failures', 'errors', 'skipped'))
            tests += int(suite.attrib['tests'])
            print('ACTUAL JUNIT XML', path.relative_to(ROOT), data)
    assert tests == 23
    commands = [json.loads(line) for line in (DEVICE / 'commands.jsonl').read_text().splitlines()]
    installs = [c['command'] for c in commands if 'install' in c['command']]
    assert len(installs) == 2
    assert all(command[-1].endswith('/input.apk') for command in installs)
    assert not any('instrument' in c['command'] or 'clear' in c['command'] or 'uninstall' in c['command'] for c in commands)
    assert all(c['output'].strip() == '' for c in commands if c['command'][-2:] in (['forward', '--list'], ['reverse', '--list']))
    appium = (DEVICE / 'appium.log').read_text()
    assert 'POST /session' not in appium
    print('PASS: exact synthetic capture hashes, 23 actual passing shared/storage/host JVM tests, real startup/failure/recovery/cleanup evidence; no instrumentation/Appium session/reset/route. Full product/release obligations remain open.')


if __name__ == '__main__':
    main()
