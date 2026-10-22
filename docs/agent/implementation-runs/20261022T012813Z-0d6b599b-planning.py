"""Read-only routing split checks; never starts a controller, device or service."""
import copy
import hashlib
import json
from pathlib import Path
import runpy
import subprocess


REPORT = 'docs/agent/implementation-runs/20261022T012813Z-0d6b599b.json'
STATE = 'docs/agent/implementation-state.json'
CHILDREN = ['I01f1b2a', 'I01f1b2b']


def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()


def main():
    report = json.loads(Path(REPORT).read_text())
    before = json.loads(git('show', report['startCommit'] + ':' + STATE))
    after = json.loads(Path(STATE).read_text())
    api = runpy.run_path('scripts/implementation-loop.py')
    choice = {'mode': 'implement', 'target': 'I01f1b2', 'findingIds': []}
    assert git('rev-parse', 'HEAD') == report['startCommit']
    assert git('branch', '--show-current') == 'master'
    assert not git('diff', '--cached', '--name-only')
    assert before['mode'] == 'active' and before['authorization'] == 'authorized'
    assert 'D-304' in Path(before['authorizationRecord']).read_text()
    assert before['runner']['model'] == 'openai/gpt-6.1-sol#high'
    assert before['findings'] == []
    assert api['selection'](before) == choice
    print('PASS supplied commit/master, empty index, D-304 authorization and earliest eligible I01f1b2')

    api['validate_state'](after)
    api['validate_transition'](before, after, choice, report)
    assert after['activeRun'] is None
    assert after['runs'] == before['runs'] + [REPORT]
    assert len(after['runs']) == len(set(after['runs']))
    for key in before:
        if key not in {'tasks', 'runs'}:
            assert after[key] == before[key], key
    old = {task['id']: task for task in before['tasks']}
    new = {task['id']: task for task in after['tasks']}
    assert set(new) - set(old) == set(CHILDREN)
    assert all(task == new[identifier] for identifier, task in old.items() if identifier != choice['target'])
    assert new[choice['target']] == {**old[choice['target']], 'status': 'in-progress',
                                     'children': CHILDREN, 'planningRecord': REPORT}
    for identifier in CHILDREN:
        task = new[identifier]
        assert task['batch'] == 'B1' and task['status'] == 'pending'
        assert task['outcome'].strip() and len(task['checks']) == 3
        assert all(isinstance(check, str) and check.strip() for check in task['checks'])
        assert task['dependsOn'] and not set(task['dependsOn']) & {'I01', 'I01f', 'I01f1', 'I01f1b', 'I01f1b2'}
    assert new['I01f1b2a']['dependsOn'] == old['I01f1b2']['dependsOn'] == ['I01f1b1', 'I01e']
    assert new['I01f1b2b']['dependsOn'] == ['I01f1b2a']
    assert old['I01f1b1']['status'] == old['I01e']['status'] == 'implemented'
    assert new['I01f2']['dependsOn'] == ['I01f1']
    print('PASS driver schema/transition, exactly two pending same-B1 children, explicit acyclic dependencies')
    print('PASS selected parent outcome/all checks and every ancestor/unrelated task unchanged')
    print('PASS review coverage/anchors, findings, model/permissions, authorization and release gates unchanged')

    # Pure scheduling simulation; these fake completions are never written.
    simulated = copy.deepcopy(after)
    tasks = {task['id']: task for task in simulated['tasks']}
    order = CHILDREN + ['I01f1b2', 'I01f1b', 'I01f1', 'I01f2', 'I01f3', 'I01f', 'I01', 'I02', 'I03', 'I04']
    for identifier in order:
        assert api['selection'](simulated) == {**choice, 'target': identifier}, identifier
        tasks[identifier]['status'] = 'implemented'
    assert api['selection'](simulated) == {'mode': 'review', 'target': 'B1', 'findingIds': []}
    print('PASS simulated selection order: ' + ' -> '.join(order) + ' -> B1 group review')
    print('PASS no child reviews, skipped parent qualification or early product/review completion')

    controller = Path('e2e/devices.py').read_text()
    mappings = controller.split('def check_mappings(value, serial):', 1)[1].split('\ndef check_device', 1)[0]
    assert "require(not forward and not reverse, 'Foreign/unrecorded ADB mapping refused')" in mappings
    wait = controller.split('def wait(value, timeout):', 1)[1].split('\ndef provision', 1)[0]
    assert "'scope': 'Device services only; no APK/UI/Compose/instrumentation acceptance'" in wait
    assert 'check_mappings(value, serial)' in controller.split('def cleanup(value):', 1)[1]
    windows = Path('e2e/windows-device.ps1').read_text()
    assert 'Reused or foreign Windows PID refused' in windows and 'ADB must bind only IPv4 loopback' in windows
    environment = Path('e2e/environment.py').read_text()
    assert '"backendOrigin": "http://127.0.0.1:"' in environment
    assert '"sourceOrigin": "http://127.0.0.1:"' in environment
    assert '"Public backend/health separation failed"' in environment
    assert '"Public source administration exposed"' in environment
    assert '"Incomplete reset; explicitly reset before requesting a handoff"' in environment
    print('PASS source evidence: strict empty-mapping readiness/cleanup, services-only wait, exact Windows ownership')
    print('PASS source evidence: two real Compose loopback origins, private-route separation and failed-reset barrier')

    ledger = json.loads(Path('docs/agent/coverage/ledger.json').read_text())
    items = {item['id']: item for item in ledger['items']}
    for identifier in report['planning']['sourceIds']['directScenarioPortions']:
        assert 'I01' in items[identifier]['implementationDependencies']
        assert items[identifier]['completion'] == 'unexecuted'
    for identifier in report['planning']['sourceIds']['laterBehaviorConstraints']:
        assert items[identifier]['completion'] == 'unexecuted'
    assert items['T-632']['implementationDependencies'] == ['I35']
    for path in report['sourcePaths']:
        content = Path(path).read_bytes()
        assert content == subprocess.check_output(['git', 'show', report['startCommit'] + ':' + path]), path
        print('UNCHANGED ' + path + ' sha256=' + hashlib.sha256(content).hexdigest())
    print('PASS source/coverage/runtime/build inputs unchanged; all product scenarios remain unexecuted')

    assert report['runId'] == '20261022T012813Z-0d6b599b'
    assert report['sessionID'] == 'ses_e9ec6e9bbffe9HSAc7RdZcFLAj'
    assert report['mode'] == choice['mode'] and report['target'] == choice['target']
    assert report['outcome'] == 'progress'
    assert report['findingIds'] == report['newFindingIds'] == report['reviewedUnitIds'] == []
    paths = report['changedPaths']
    prefix = REPORT[:-5]
    assert len(paths) == len(set(paths)) == 6
    assert set(paths) == {STATE, REPORT, prefix + '-planning.py', prefix + '-planning.json',
                          prefix + '-coverage.json', prefix + '-fixtures.json'}
    for check in report['checks']:
        assert check['kind'] == 'required' and check['exitCode'] == 0
        assert check['evidence'] in paths
        for support in check.get('supportPaths', []):
            assert support in paths and support.startswith(prefix + '-')
        if check['evidence'] != prefix + '-planning.json':
            captured = json.loads(Path(check['evidence']).read_text())
            assert captured['exitCode'] == 0
            assert captured['outputSha256'] == hashlib.sha256(captured['output'].encode()).hexdigest()
    actual = set(git('diff', '--name-only').splitlines())
    actual.update(git('ls-files', '--others', '--exclude-standard').splitlines())
    # capture-check writes this invocation's evidence only after this script exits.
    assert actual in (set(paths) - {prefix + '-planning.json'}, set(paths)), actual
    subprocess.run(['git', 'diff', '--check'], check=True)
    print('PASS exact owned paths/support registration, retained checks/output hashes and whitespace')
    print('PASS planning only; no controller/runtime/build/UI/runner edits or device/service/route starts')


if __name__ == '__main__':
    main()
