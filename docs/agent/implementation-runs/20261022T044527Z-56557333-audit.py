"""Run-owned I01f1 evidence reconciliation; never launches services or devices."""
import ast
import hashlib
import json
from pathlib import Path
import runpy
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PREFIX = 'docs/agent/implementation-runs/20261022T044527Z-56557333'
STATE = 'docs/agent/implementation-state.json'
START = '0c5c77d7a7c883b93b2d9f81a78b76e1f5c6e34e'
CHILDREN = {
    'I01f1a': ('20261022T003723Z-932550a0', 'c8e91687d49d210500268ac8087272fc173d53d5'),
    'I01f1b': ('20261022T034912Z-d4685909', 'f0a968f129529ade49a4be3ba789ab8565cac612'),
}


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def load(path):
    return json.loads((ROOT / path).read_text())


def emit(label, value):
    print(label, json.dumps(value, sort_keys=True))


def embedded(output, label):
    return json.JSONDecoder().raw_decode(output.split(label + ' ', 1)[1])[0]


def audit():
    before = json.loads(git('show', START + ':' + STATE))
    driver = runpy.run_path(str(ROOT / 'scripts/implementation-loop.py'))
    assert driver['selection'](before) == {'mode': 'implement', 'target': 'I01f1', 'findingIds': []}
    tasks = {task['id']: task for task in before['tasks']}
    parent = tasks['I01f1']
    assert before['authorization'] == 'authorized' and before['mode'] == 'active'
    assert all(tasks[key]['status'] == 'implemented' for key in parent['children'] + parent['dependsOn'])
    for child, (run, commit) in CHILDREN.items():
        path = 'docs/agent/implementation-runs/' + run + '.json'
        report = load(path)
        assert tasks[child]['implementationRecord'] == path and report['target'] == child
        assert git('show', commit + ':' + path) == (ROOT / path).read_bytes()
        evidence = []
        for check in report['checks']:
            data = (ROOT / check['evidence']).read_bytes()
            assert git('show', commit + ':' + check['evidence']) == data
            retained = json.loads(data)
            assert retained['exitCode'] == check['exitCode']
            assert sha(retained['output'].encode()) == retained['outputSha256']
            assert check['kind'] != 'required' or check['exitCode'] == 0
            evidence.append({'path': check['evidence'], 'kind': check['kind'], 'sha256': sha(data)})
        emit('REUSED CHILD (historical executions, not reruns)', {
            'id': child, 'record': path, 'codeStartCommit': report['startCommit'],
            'savedCommit': commit, 'reportSha256': sha((ROOT / path).read_bytes()), 'checks': evidence})

    # The later child exercised both layers together. Compare its exact inputs,
    # including all module/fixture/tool-lock files, rather than status alone.
    integrated = CHILDREN['I01f1b'][1]
    paths = git('ls-tree', '-r', '--name-only', integrated, '--',
                'androidApp', 'client-storage', 'core', 'protocol', 'server', 'e2e',
                'gradle', 'tests', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties',
                'gradlew', 'gradlew.bat', 'scripts/gradle.sh', 'scripts/e2e-devices.py',
                'scripts/e2e-environment.py', 'scripts/install-device-tools.py',
                'scripts/build-server-image.py', 'scripts/build-synthetic-adapters.py').decode().splitlines()
    changed = {'e2e/devices.py', 'tests/test_e2e_devices.py'}
    identities = {}
    for path in paths:
        if path.endswith('.md'):
            continue
        old, current = git('show', integrated + ':' + path), (ROOT / path).read_bytes()
        if path not in changed:
            if path == 'gradlew.bat':
                assert b'gradlew.bat text eol=crlf' in (ROOT / '.gitattributes').read_bytes()
                assert current.replace(b'\r\n', b'\n') == old, path
            else:
                assert current == old, path
        identities[path] = {'qualifiedSha256': sha(old), 'currentSha256': sha(current)}
    old = ast.parse(git('show', integrated + ':e2e/devices.py'))
    current = ast.parse((ROOT / 'e2e/devices.py').read_text())
    helper = [node for node in current.body if isinstance(node, ast.FunctionDef) and node.name == 'inventory_changes']
    assert len(helper) == 1
    current.body.remove(helper[0])
    assert ast.dump(old) == ast.dump(current), 'Device lifecycle behavior changed'
    # Native launcher and complete npm tool graphs are unchanged even from I01f1a.
    for path in ['e2e/windows-device.ps1', 'scripts/install-device-tools.py'] + sorted(
            str(path.relative_to(ROOT)) for path in (ROOT / 'e2e/device-tools').glob('*/*.json')):
        assert git('show', CHILDREN['I01f1a'][1] + ':' + path) == (ROOT / path).read_bytes(), path
    emit('QUALIFIED/CURRENT INPUT IDENTITIES', identities)
    emit('SCOPED POST-CHILD ORACLE CHANGE', {
        'record': 'docs/agent/delivery-first-application-2026-10-22.md',
        'recordSha256': sha((ROOT / 'docs/agent/delivery-first-application-2026-10-22.md').read_bytes()),
        'effect': 'Only passive inventory_changes added to device controller; every prior lifecycle function AST identical. Paused repair records 36 focused tests. Four checkers retain ambient deltas instead of frozen unrelated inventories; exact owned cleanup/foreign refusal unchanged.',
        'checkerDiff': git('diff', integrated, START, '--', 'scripts/check-e2e-devices.py',
                           'scripts/check-device-app.py', 'scripts/check-device-routes.py',
                           'scripts/check-device-handoff.py').decode()})

    prefix = 'docs/agent/implementation-runs/' + CHILDREN['I01f1b'][0]
    actual = load(prefix + '-actual-repeat.json')['output']
    checkpoints = [line for line in actual.splitlines() if line.startswith('PASS ')]
    assert len(checkpoints) == 9
    emit('REUSED INTEGRATED BEHAVIOR/CLEANUP', checkpoints)
    artifacts = load(prefix + '-artifacts-repeat.json')['output']
    snapshot = embedded(artifacts, 'FINAL COMBINED SNAPSHOT')
    combined = snapshot['combined']
    assert len(combined['routes']['probes']) == 17 and combined['device']['api'] == '29'
    app = snapshot['final']
    assert app['api'] == 29 and app['apk']['package'] == 'org.asyncttwiz.android'
    assert app['apk']['activity'] == 'org.asyncttwiz.android.MainActivity'
    assert app['storage']['readOnly'] and not any(app['storage']['rowCounts'].values())
    for key, suffix in [('screenshot', '-screen.png'), ('hierarchy', '-hierarchy.xml')]:
        data = (ROOT / (prefix + suffix)).read_bytes()
        assert sha(data) == app[key]['sha256']
        assert git('show', integrated + ':' + prefix + suffix) == data
    texts = [node.get('text') for node in ET.fromstring((ROOT / (prefix + '-hierarchy.xml')).read_bytes()).iter('node')]
    assert 'Async Tabletop Wizards' in texts and 'Specialized tabletop simulator for asynchronous wizardry' in texts
    emit('REUSED API29 APK/UI/STORAGE IDENTITY', app)
    emit('REUSED ENVIRONMENT/IMAGE IDENTITY', embedded(artifacts, 'ACTUAL ENVIRONMENT INPUT/IMAGE IDENTITIES'))
    emit('REUSED PINNED TOOL IDENTITY', embedded(artifacts, 'QUALIFIED UNCHANGED TOOL INPUTS/PACKAGES'))
    emit('REUSED NATIVE CLEANUP', embedded(artifacts, 'EXACT NATIVE CLEANUP'))
    emit('REUSED COMPOSE CLEANUP', embedded(artifacts, 'EXACT COMPOSE CLEANUP'))
    assert tasks['I01f2']['status'] == tasks['I01f3']['status'] == 'pending'
    assert tasks['I01f3']['batch'] == 'B8' and 'I01f3' in tasks['I34']['dependsOn']
    assert all(gate['status'] == 'unverified' for gate in before['externalReleaseGates'])
    print('PASS I01f1 integrated child evidence/current inputs reconciled. No missing integration seam; no build/device/fault rerun needed. I01f2 actual Appium/14 instrumentation tests, B8 matrix/I34, I33/I35, independent B1 review and all release gates remain pending. Historical diagnostics remain diagnostics.')


def validate():
    report, state = load(PREFIX + '.json'), load(STATE)
    before = json.loads(git('show', START + ':' + STATE))
    assert git('rev-parse', 'HEAD').decode().strip() == START
    driver = runpy.run_path(str(ROOT / 'scripts/implementation-loop.py'))
    choice = {'mode': 'implement', 'target': 'I01f1', 'findingIds': []}
    driver['validate_state'](state)
    driver['validate_transition'](before, state, choice, report)
    assert state['runs'] == before['runs'] + [PREFIX + '.json'] and state['activeRun'] is None
    for old, new in zip(before['tasks'], state['tasks']):
        if old['id'] == 'I01f1':
            assert new == {**old, 'status': 'implemented', 'implementationRecord': PREFIX + '.json',
                           'qualification': new['qualification']}
        else:
            assert old == new
    for key in before:
        if key not in {'tasks', 'runs'}:
            assert before[key] == state[key], key
    assert driver['selection'](state) == {'mode': 'implement', 'target': 'I01f2', 'findingIds': []}
    paths = set(git('diff', '--name-only', START).decode().splitlines())
    paths.update(git('ls-files', '--others', '--exclude-standard').decode().splitlines())
    assert paths | {PREFIX + '-validation.json'} == set(report['changedPaths'])
    assert not git('diff', '--cached', '--name-only').strip()
    assert not git('diff', '--check').strip()
    for path in report['changedPaths']:
        assert path == STATE or path.startswith(PREFIX)
        if not (ROOT / path).exists():
            assert path == PREFIX + '-validation.json'
            continue
        text = (ROOT / path).read_text()
        assert text.endswith('\n') and all(line == line.rstrip() for line in text.splitlines()), path
        if path.endswith('.json'):
            json.loads(text)
    retained = load(PREFIX + '-audit-pass.json')
    assert retained['exitCode'] == 0 and sha(retained['output'].encode()) == retained['outputSha256']
    print('PASS driver state/transition/next I01f2 selection, selected-task-only closure, immutable authorization/gates/review, retained audit hash, exact owned changed paths, JSON and whitespace. No Markdown/UI documents edited.')


if __name__ == '__main__':
    {'audit': audit, 'validate': validate}[sys.argv[1]]()
