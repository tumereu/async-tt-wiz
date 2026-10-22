"""Run-owned I01f1b parent qualification evidence; not an application helper."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PREFIX = 'docs/agent/implementation-runs/20261022T034912Z-d4685909'
SUFFIX = 'd468590922224af58b9f3861313df8c1'
DEVICE = Path('/tmp/opencode/atw-device-' + SUFFIX)
ENVIRONMENT = Path('/tmp/opencode/atw-e2e-' + SUFFIX)
sys.path.insert(0, str(ROOT))
from e2e import devices as d


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def preflight():
    previous = Path('/tmp/opencode/atw-device-a5658aee22224af58b9f3861313df8c1')
    assert (previous / 'cleaned.json').exists()
    device = d.load(previous)
    ports = set()
    for part in ('1111', '2222'):
        path = Path('/tmp/opencode/atw-e2e-a5658aee' + part + '4af58b9f3861313df8c1')
        assert (path / 'cleaned.json').exists()
        ports.update(json.loads((path / 'instance.json').read_text())['ports'].values())
    current = Path('/tmp/opencode/atw-e2e-d468590911114af58b9f3861313df8c1')
    if current.exists():
        assert (current / 'cleaned.json').exists()
        ports.update(json.loads((current / 'instance.json').read_text())['ports'].values())
    assert not any(d.linux_listening(port) for port in ports)
    until = time.monotonic() + 180
    while True:
        inventory = d.windows(device, 'inventory')
        remaining = [row for row in inventory['listeners'] if row['port'] in ports]
        print(json.dumps({'previousCleanedSyntheticPorts': sorted(ports), 'remainingProxyEntries': remaining}), flush=True)
        if not remaining:
            print('READ-ONLY WINDOWS INVENTORY', json.dumps(inventory), flush=True)
            break
        assert time.monotonic() < until, 'Known synthetic proxies did not drain'
        time.sleep(5)
    print('PASS passive known-cleaned synthetic proxy drain; no process/network mutation or baseline exclusion.')


def diagnosis():
    result = json.loads((ROOT / (PREFIX + '-actual.json')).read_text())
    assert result['exitCode'] == 1
    baseline = json.loads(result['output'].split('WINDOWS BASELINE ')[1].splitlines()[0])
    final = json.loads(result['output'].split('WINDOWS FINAL ')[1].splitlines()[0])
    missing = [row for row in baseline['listeners'] if row not in final['listeners']]
    assert missing and baseline['androidProcesses'] == final['androidProcesses'] == []
    device_dir = Path('/tmp/opencode/atw-device-d468590911114af58b9f3861313df8c1')
    env_dir = Path('/tmp/opencode/atw-e2e-d468590911114af58b9f3861313df8c1')
    assert (device_dir / 'cleaned.json').exists() and (env_dir / 'cleaned.json').exists()
    device = d.load(device_dir)
    env = json.loads((env_dir / 'instance.json').read_text())
    owned = set(env['ports'].values()) | {device['config'][key] for key in
                                       ('adbPort', 'consolePort', 'appiumPort', 'systemPort')}
    owned.add(device['config']['consolePort'] + 1)
    assert not any(row['port'] in owned for row in missing)
    assert not any(d.linux_listening(row['port']) for row in missing)
    print('FAILED STRICT BASELINE', json.dumps(baseline), flush=True)
    print('FAILED STRICT FINAL', json.dumps(final), flush=True)
    print('MISSING PRE-EXISTING LISTENERS', json.dumps(missing), flush=True)
    print('DISJOINT OWNED PORTS', sorted(owned), flush=True)
    commands = [json.loads(line) for line in (device_dir / 'commands.jsonl').read_text().splitlines()]
    assert not any(str(row['port']) in str(command['command']) for row in missing for command in commands)
    for attempt in range(3):
        current = d.windows(device, 'inventory')
        assert not current['androidProcesses']
        assert not any(row in current['listeners'] for row in missing)
        print('PASSIVE CURRENT INVENTORY', attempt, json.dumps(current), flush=True)
        if attempt < 2:
            time.sleep(15)
    for line in result['output'].splitlines():
        if line.startswith('PASS '):
            print('FIRST ATTEMPT BEHAVIORAL CHECKPOINT', line)
    print('PASS first attempt cleaned, missing listeners disjoint and not targeted; passive absence confirmed. Original socket provenance/root cause unknown. Retain exit1, do not claim a fix; repeat fresh unchanged assertions after drain.')


def artifacts(write):
    final = json.loads((DEVICE / 'checker-final.json').read_text())
    observed = [json.loads(path.read_text()) for path in DEVICE.glob('app-*/observed.json')]
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
        assert not (DEVICE / name).exists()
    assert not (ENVIRONMENT / 'db-password').exists() and not (ENVIRONMENT / 'harness-token').exists()
    print('ACTUAL STARTUP OBSERVATION COUNT', len(observed))
    print('FINAL COMBINED SNAPSHOT', json.dumps(final, indent=2))
    print('ACTUAL TOOL VERSIONS', (DEVICE / 'versions.json').read_text())
    env = json.loads((ENVIRONMENT / 'instance.json').read_text())
    print('ACTUAL ENVIRONMENT INPUT/IMAGE IDENTITIES', json.dumps({key: env[key] for key in
          ('directory', 'owner', 'project', 'ports', 'backendOrigin', 'sourceOrigin', 'images')}, indent=2))
    print('EXACT NATIVE CLEANUP', (DEVICE / 'cleaned.json').read_text())
    print('EXACT COMPOSE CLEANUP', (ENVIRONMENT / 'cleaned.json').read_text())
    commands = (DEVICE / 'commands.jsonl').read_text()
    print('ACTUAL DEVICE/TOOL COMMAND OUTPUT', commands)
    assert not any('instrument' in row['command'] or 'clear' in row['command'] or 'uninstall' in row['command']
                   for row in map(json.loads, commands.splitlines()))
    assert 'POST /session' not in (DEVICE / 'appium.log').read_text()
    for kind, suffix in (('screenshot', '-screen.png'), ('hierarchy', '-hierarchy.xml')):
        source = Path(final['final'][kind]['path'])
        assert digest(source) == final['final'][kind]['sha256']
        target = ROOT / (PREFIX + suffix)
        if write:
            assert not target.exists()
            target.write_bytes(source.read_bytes())
        assert target.read_bytes() == source.read_bytes()
        print('RETAINED SYNTHETIC ARTIFACT', target.relative_to(ROOT), digest(target), target.stat().st_size)
    tree = ET.fromstring((ROOT / (PREFIX + '-hierarchy.xml')).read_bytes())
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
        assert digest(ROOT / 'e2e/device-tools' / path) == expected and digest(tools / path) == expected
    packages = {}
    for path in (tools / 'appium/node_modules/appium/package.json', tools / 'driver/node_modules/appium-uiautomator2-driver/package.json'):
        package = json.loads(path.read_text())
        group = 'appium' if package['name'] == 'appium' else 'driver'
        expected = qualified['packages'][group][package['name']]
        assert digest(path) == expected['manifestSha256']
        packages[package['name']] = expected
    print('QUALIFIED UNCHANGED TOOL INPUTS/PACKAGES', json.dumps({'qualificationSha256': digest(tools / 'qualified.json'), 'inputs': qualified['inputs'], 'packages': packages}, indent=2))
    print('PASS exact actual synthetic capture, installed/rebuilt normal APK, empty retained SQLite, 23 unskipped JVM tests, pinned tools and cleanup. No Appium session/instrumentation/product networking or independent review/release claim.')


def validate():
    def git(*args):
        return subprocess.check_output(['git', *args], cwd=ROOT, text=True)
    report_path = PREFIX + '.json'
    state_path = 'docs/agent/implementation-state.json'
    report = json.loads((ROOT / report_path).read_text())
    state = json.loads((ROOT / state_path).read_text())
    baseline = json.loads(git('show', report['startCommit'] + ':' + state_path))
    assert git('rev-parse', 'HEAD').strip() == report['startCommit']
    assert report['mode'] == 'implement' and report['target'] == 'I01f1b' and report['outcome'] == 'progress'
    assert report['findingIds'] == report['newFindingIds'] == report['reviewedUnitIds'] == []
    assert state['runs'] == baseline['runs'] + [report_path] and state['activeRun'] is None
    old, new = ({task['id']: task for task in value['tasks']} for value in (baseline, state))
    assert old.keys() == new.keys()
    for key in old:
        if key == report['target']:
            assert old[key]['status'] == 'in-progress' and new[key]['status'] == 'implemented'
            assert new[key]['implementationRecord'] == report_path and new[key]['qualification']
            for field in ('id', 'batch', 'outcome', 'checks', 'dependsOn', 'children', 'planningRecord'):
                assert old[key][field] == new[key][field]
        else:
            assert old[key] == new[key], key
    for key in state:
        if key not in ('tasks', 'runs'):
            assert state[key] == baseline[key], key
    assert all(new[dep]['status'] == 'implemented' for dep in new['I01f1b']['dependsOn'] + new['I01f1b']['children'])
    assert state['batches'][0]['status'] == 'pending' and not state['findings']
    actual = set(git('diff', '--name-only', report['startCommit']).splitlines())
    actual.update(git('ls-files', '--others', '--exclude-standard').splitlines())
    assert actual | {PREFIX + '-validation.json'} == set(report['changedPaths']), actual ^ set(report['changedPaths'])
    assert len(report['changedPaths']) == len(set(report['changedPaths']))
    assert not git('diff', '--cached', '--name-only').strip(), 'Unexpected staged work'
    assert not git('diff', '--check')
    hashes, supports = {}, set()
    for check in report['checks']:
        assert check['evidence'].startswith(PREFIX + '-') and check['evidence'] in report['changedPaths']
        assert check['kind'] != 'required' or check['exitCode'] == 0
        for support in check.get('supportPaths', []):
            assert support.startswith(PREFIX + '-') and support in report['changedPaths'] and (ROOT / support).is_file()
            supports.add(support)
        if check['evidence'] == PREFIX + '-validation.json':
            continue
        path = ROOT / check['evidence']
        evidence = json.loads(path.read_text())
        assert evidence['exitCode'] == check['exitCode']
        assert shlex.split(check['command']) == evidence['command'], path
        assert hashlib.sha256(evidence['output'].encode()).hexdigest() == evidence['outputSha256']
        hashes[check['evidence']] = digest(path)
    assert supports == {path for path in actual if path.endswith('.py')}
    for path in actual:
        assert path.startswith(PREFIX) or path in {state_path, 'e2e/README.md', 'e2e/devices-README.md'}
        if path.endswith(('.py', '.md', '.json')):
            text = (ROOT / path).read_text()
            assert text.endswith('\n') and not any(line.rstrip() != line for line in text.splitlines()), path
        if path.endswith('.md'):
            for link in re.findall(r'\]\(([^)]+)\)', (ROOT / path).read_text()):
                if '://' not in link and not link.startswith('#'):
                    assert ((ROOT / path).parent / link.split('#')[0]).exists(), (path, link)
    assert 'Ran 134 tests' in json.loads((ROOT / (PREFIX + '-python.json')).read_text())['output']
    assert 'Unrelated Windows listener changed' in json.loads((ROOT / (PREFIX + '-actual.json')).read_text())['output']
    assert 'PASS exact automatic unroute/native guest' in json.loads((ROOT / (PREFIX + '-actual-repeat.json')).read_text())['output']
    assert json.loads((ROOT / (PREFIX + '-actual-repeat.json')).read_text())['exitCode'] == 0
    assert json.loads((ROOT / (PREFIX + '-artifacts.json')).read_text())['exitCode'] == 1
    assert json.loads((ROOT / (PREFIX + '-artifacts-repeat.json')).read_text())['exitCode'] == 0
    print(json.dumps({'retainedEvidenceSha256': hashes, 'changedPathCount': len(report['changedPaths'])}, indent=2))
    print('PASS selected parent only; children/dependency implemented; all other tasks/batches/reviews/findings/gates/model/grants/authorization unchanged; required results/evidence/documentation links/whitespace/path accounting valid; both diagnostics retained, activeRun null. No product/build/controller/test-source/UI-document change. Save/push still required.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase', choices=('preflight', 'diagnosis', 'artifacts', 'validate'))
    parser.add_argument('--write', action='store_true')
    args = parser.parse_args()
    if args.phase == 'preflight':
        preflight()
    elif args.phase == 'diagnosis':
        diagnosis()
    elif args.phase == 'validate':
        validate()
    else:
        artifacts(args.write)
