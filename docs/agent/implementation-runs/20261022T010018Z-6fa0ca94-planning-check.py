"""Read-only I01f1b planning checks; no driver loop, service or device launch."""
import copy
import hashlib
import json
from pathlib import Path
import runpy
import subprocess


REPORT = 'docs/agent/implementation-runs/20261022T010018Z-6fa0ca94.json'
STATE = 'docs/agent/implementation-state.json'
CHILDREN = ['I01f1b1', 'I01f1b2']


def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()


def main():
    report = json.loads(Path(REPORT).read_text())
    before = json.loads(git('show', report['startCommit'] + ':' + STATE))
    after = json.loads(Path(STATE).read_text())
    api = runpy.run_path('scripts/implementation-loop.py')
    choice = {'mode': 'implement', 'target': 'I01f1b', 'findingIds': []}
    assert git('rev-parse', 'HEAD') == report['startCommit']
    assert git('branch', '--show-current') == 'master'
    assert not git('diff', '--cached', '--name-only')
    assert before['mode'] == 'active' and before['authorization'] == 'authorized'
    assert 'D-304' in Path(before['authorizationRecord']).read_text()
    assert before['runner']['model'] == 'openai/gpt-6.1-sol#high'
    assert before['findings'] == []
    assert api['selection'](before) == choice
    print('PASS exact starting commit/master, empty index, D-304 authorization and supplied I01f1b priority')

    api['validate_state'](after)
    api['validate_transition'](before, after, choice, report)
    assert after['activeRun'] is None
    assert after['runs'] == before['runs'] + [REPORT]
    for key in before:
        if key not in {'tasks', 'runs'}:
            assert after[key] == before[key], key
    old = {task['id']: task for task in before['tasks']}
    new = {task['id']: task for task in after['tasks']}
    assert set(new) - set(old) == set(CHILDREN)
    assert all(task == new[identifier] for identifier, task in old.items() if identifier != choice['target'])
    assert new['I01f1b'] == {**old['I01f1b'], 'status': 'in-progress', 'children': CHILDREN,
                             'planningRecord': REPORT}
    for identifier in CHILDREN:
        task = new[identifier]
        assert task['batch'] == 'B1' and task['status'] == 'pending'
        assert task['outcome'].strip() and len(task['checks']) == 3
        assert all(isinstance(check, str) and check.strip() for check in task['checks'])
        assert task['dependsOn'] and not set(task['dependsOn']) & {'I01', 'I01f', 'I01f1', 'I01f1b'}
    assert new['I01f1b1']['dependsOn'] == old['I01f1b']['dependsOn'] == ['I01f1a']
    assert new['I01f1b2']['dependsOn'] == ['I01f1b1', 'I01e']
    assert old['I01f1a']['status'] == old['I01e']['status'] == 'implemented'
    assert new['I01f2']['dependsOn'] == ['I01f1']
    print('PASS driver state/transition validation, pending same-B1 children and explicit acyclic dependencies')
    print('PASS original I01f1b outcome/all three checks and I01f1/I01f/I01 parent qualifications unchanged')
    print('PASS unrelated tasks/findings, batch review coverage/anchors, model/permissions and release gates unchanged')

    # Read-only scheduling experiment; simulated completion is never saved.
    simulated = copy.deepcopy(after)
    tasks = {task['id']: task for task in simulated['tasks']}
    order = CHILDREN + ['I01f1b', 'I01f1', 'I01f2', 'I01f3', 'I01f', 'I01', 'I02', 'I03', 'I04']
    for identifier in order:
        assert api['selection'](simulated) == {**choice, 'target': identifier}, identifier
        tasks[identifier]['status'] = 'implemented'
    assert api['selection'](simulated) == {'mode': 'review', 'target': 'B1', 'findingIds': []}
    print('PASS simulated order: ' + ' -> '.join(order) + ' -> B1 group review')
    print('PASS no child reviews or skipped parent, Kotlin/instrumentation, multi-device or account obligations')

    controller = Path('e2e/devices.py').read_text()
    mappings = controller.split('def check_mappings(value, serial):', 1)[1].split('\ndef check_device', 1)[0]
    assert "require(not forward and not reverse, 'Foreign/unrecorded ADB mapping refused')" in mappings
    assert "for operation in ('wait', 'capture', 'cleanup')" in controller
    launcher = Path('androidApp/src/main/kotlin/org/asyncttwiz/android/MainActivity.kt').read_text()
    assert 'Text("Async Tabletop Wizards"' in launcher and 'StorageState.Opening' in launcher
    assert 'ViewModelProvider(this)[StorageViewModel::class.java]' in launcher
    build = Path('androidApp/build.gradle.kts').read_text()
    assert 'applicationId = "org.asyncttwiz.android"' in build and 'minSdk = 29' in build
    assert 'targetSdk = 36' in build
    print('PASS source evidence: no APK operation yet; empty-mapping refusal is used by readiness/cleanup')
    print('PASS normal production launcher is branded storage-backed scaffold, API29/target36; no runtime pass inferred')

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
    print('PASS primary sources, runtime/build/ownership and coverage inputs unchanged; product outcomes unexecuted')

    assert report['runId'] == '20261022T010018Z-6fa0ca94'
    assert report['sessionID'] == 'ses_e9ee078e4ffe7ZRPBI7L7zRWa0'
    assert report['mode'] == choice['mode'] and report['target'] == choice['target']
    assert report['outcome'] == 'progress'
    assert report['findingIds'] == report['newFindingIds'] == report['reviewedUnitIds'] == []
    paths = report['changedPaths']
    prefix = REPORT[:-5]
    assert len(paths) == len(set(paths)) == 6
    assert set(paths) == {STATE, REPORT, prefix + '-planning-check.py', prefix + '-planning-check.json',
                          prefix + '-coverage-check.json', prefix + '-fixture-check.json'}
    for check in report['checks']:
        assert check['kind'] == 'required' and check['exitCode'] == 0
        assert check['evidence'] in paths
        for support in check.get('supportPaths', []):
            assert support in paths and support.startswith(prefix + '-')
        if check['evidence'] != prefix + '-planning-check.json':
            captured = json.loads(Path(check['evidence']).read_text())
            assert captured['exitCode'] == 0
            assert captured['outputSha256'] == hashlib.sha256(captured['output'].encode()).hexdigest()
    actual = set(git('diff', '--name-only').splitlines())
    actual.update(git('ls-files', '--others', '--exclude-standard').splitlines())
    # capture-check creates this invocation's output after this helper exits.
    assert actual in (set(paths) - {prefix + '-planning-check.json'}, set(paths)), actual
    subprocess.run(['git', 'diff', '--check'], check=True)
    print('PASS exact planning-only paths/support registration, retained check hashes and whitespace')
    print('PASS no runtime/build/UI/runner edits, acceptance/review/release pass or service/device starts')


if __name__ == '__main__':
    main()
