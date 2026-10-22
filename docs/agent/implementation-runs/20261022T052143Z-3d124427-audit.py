"""Selected I01 evidence reconciliation; never launches builds or services."""
import fnmatch
import hashlib
import json
from pathlib import Path
import runpy
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PREFIX = 'docs/agent/implementation-runs/20261022T052143Z-3d124427'
STATE = 'docs/agent/implementation-state.json'
START = '1520527fefcd6f2eb00c7928da17dd7237d7c511'
DEVICE = 'docs/agent/implementation-runs/20261022T044934Z-c307edee'
COMMITS = {
    'I01a': '57226f9fc103c1a9d9ec3544c03110656d57daed',
    'I01b': 'a3215eade0762e291005a21b2b9fb3f3b75f4b88',
    'I01c': '6fd2397a8b4300cea9cf9ec55d797c87bf44aeb6',
    'I01d': '1e1ea3822c78dee31ad6f1e931dcbfeaa81ebcc2',
    'I01e': 'aae6e02cc6393eb53e1d8037cdd58fcf72b6ca6f',
    'I01f': START,
}


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def load(path):
    return json.loads((ROOT / path).read_text())


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def emit(label, value):
    print(label, json.dumps(value, sort_keys=True))


def output(path):
    raw = (ROOT / path).read_bytes()
    try:
        data = json.loads(raw)
    except ValueError:
        return raw.decode()
    if isinstance(data, dict) and data.get('format') == 'lossless-utf8-output-v1':
        restored = data['output'].encode()
        assert sha(restored) == data['originalSha256'] and len(restored) == data['originalBytes'], path
        return restored.decode()
    if isinstance(data, dict) and 'outputSha256' in data:
        assert sha(data['output'].encode()) == data['outputSha256'], path
        return data['output']
    return raw.decode()


def audit():
    before = json.loads(git('show', START + ':' + STATE))
    driver = runpy.run_path(str(ROOT / 'scripts/implementation-loop.py'))
    assert driver['selection'](before) == {'mode': 'implement', 'target': 'I01', 'findingIds': []}
    assert before['authorization'] == 'authorized' and before['mode'] == 'active'
    assert not before['findings'] and before['activeRun'] is None
    tasks = {task['id']: task for task in before['tasks']}
    assert set(tasks['I01']['children']) == set(COMMITS)
    for identifier, commit in COMMITS.items():
        task = tasks[identifier]
        assert task['status'] == 'implemented'
        path = task['implementationRecord']
        report = load(path)
        assert report['target'] == identifier
        assert git('show', commit + ':' + path) == (ROOT / path).read_bytes()
        checks = []
        for check in report['checks']:
            raw = (ROOT / check['evidence']).read_bytes()
            assert git('show', commit + ':' + check['evidence']) == raw
            assert check['kind'] != 'required' or check['exitCode'] == 0
            output(check['evidence'])
            checks.append({key: check[key] for key in ['kind', 'exitCode', 'evidence']} | {'sha256': sha(raw)})
        emit('REUSED CHILD (historical checks, not new executions)', {
            'task': identifier, 'savedCommit': commit, 'record': path,
            'startCommit': report['startCommit'], 'checks': checks})

    # Later integrated checks supersede only the relevant earlier input sets.
    scopes = {
        'I01b': ['tests/fixtures/catalogs'],
        'I01c': ['server/src/main', 'core', 'protocol', 'server/Dockerfile'],
        'I01d': ['client-storage', 'androidApp'],
        'I01e': ['server', 'e2e/adapters', 'e2e/compose.json', 'e2e/environment.py',
                 'e2e/relay.py', 'scripts/e2e-environment.py', 'scripts/build-server-image.py',
                 'scripts/build-synthetic-adapters.py'],
    }
    for identifier, paths in scopes.items():
        changed = git('diff', '--name-only', COMMITS[identifier], START, '--', *paths).decode().splitlines()
        assert not [path for path in changed if not path.endswith('.md')], changed
        emit('UNCHANGED QUALIFIED SCOPE', {'task': identifier, 'commit': COMMITS[identifier], 'paths': paths, 'documentationOnlyDelta': changed})
    all_scopes = ['androidApp', 'client-storage', 'core', 'protocol', 'server', 'e2e',
                  'gradle', 'tests', 'scripts', 'build.gradle.kts', 'settings.gradle.kts',
                  'gradle.properties', 'gradlew', 'gradlew.bat', 'buildscript-gradle.lockfile']
    identities = {}
    test_paths = []
    for path in git('ls-tree', '-r', '--name-only', START, '--', *all_scopes).decode().splitlines():
        if path.endswith('.md'):
            continue
        saved, current = git('show', START + ':' + path), (ROOT / path).read_bytes()
        if path == 'gradlew.bat':
            assert b'gradlew.bat text eol=crlf' in (ROOT / '.gitattributes').read_bytes()
            current = current.replace(b'\r\n', b'\n')
        assert saved == current, path
        identities[path] = sha(saved)
        if '/src/' in path and path.endswith('Test.kt'):
            assert any(fnmatch.fnmatchcase(path, pattern) for pattern in before['runner']['reviewTestPaths']), path
            test_paths.append(path)
    emit('CURRENT EXACT SOURCE/BUILD/LOCK/FIXTURE/TOOL SHA256', identities)
    emit('ACTUAL TEST SOURCES MATCH UNCHANGED REVIEW PATHS', test_paths)
    ledger = runpy.run_path(str(ROOT / 'scripts/coverage-ledger.py'))
    counts = ledger['validate'](load('docs/agent/coverage/ledger.json'))
    assert counts == {'P': 72, 'T': 715, 'F': 88}
    emit('SOURCE-PINNED COVERAGE (assignments, not executed passes)', counts)

    # Inspect the existing integrated evidence rather than rerunning it.
    for label, count in [('startup', 1), ('storage', 14)]:
        directory = next((ROOT / DEVICE).glob(label + '-runner-*'))
        cases = [case for file in directory.glob('result-*')
                 for case in ET.fromstring(output(str(file.relative_to(ROOT)))).iter('testcase')]
        assert len(cases) == count
        assert not any(list(case.iter(tag)) for case in cases for tag in ['failure', 'error', 'skipped'])
        assert load(str(directory.relative_to(ROOT)) + '/exit.json')['exitCode'] == 0
        ready = load(str(directory.relative_to(ROOT)) + '/ready.json')
        assert ready['app']['api'] == 29 and ready['app']['apk']['package'] == 'org.asyncttwiz.android'
        assert len(ready['routes']['probes']) == 17
        emit('REUSED NORMAL-APK/REAL-ENVIRONMENT INTEGRATION', {
            'directory': str(directory.relative_to(ROOT)), 'tests': [case.attrib for case in cases],
            'apk': ready['app']['apk'], 'routeProbes': len(ready['routes']['probes'])})
    image_output = output('docs/agent/implementation-runs/20261021T233041Z-1b76a4c6-final.json')
    image_inputs = json.loads(next(line.split(' ', 1)[1] for line in image_output.splitlines() if line.startswith('FINAL-QUALIFIED-INPUTS ')))
    for path, digest in image_inputs['hashes'].items():
        if '/build/' not in path:
            assert sha((ROOT / path).read_bytes()) == digest
    emit('REUSED COMPOSE IMAGE/ARTIFACT IDENTITIES (not rebuilt)', image_inputs)
    assert tasks['I01f3']['status'] == 'pending' and tasks['I01f3']['batch'] == 'B8'
    assert tasks['I01f3']['dependsOn'] == ['I01f2', 'I31'] and 'I01f3' in tasks['I34']['dependsOn']
    assert all(gate['status'] == 'unverified' for gate in before['externalReleaseGates'])
    print('PASS I01 scoped foundation reconciles all six children, current identities, 875-item coverage and actual one-device integration. No missing affected integration assertion; no build/device/full-suite rerun. I02-I04/B1 review, I01f3/I34 broad qualification, I33 physical measurements, I35 compatibility/upgrade/arm64 and I36/B8 final UI/release gates remain pending. Historical diagnostics stay diagnostic.')


def validate():
    print('Checking selected I01 handoff and owned JSON/check outputs.', flush=True)
    before = json.loads(git('show', START + ':' + STATE))
    state, report = load(STATE), load(PREFIX + '.json')
    driver = runpy.run_path(str(ROOT / 'scripts/implementation-loop.py'))
    driver['validate_state'](state)
    driver['validate_transition'](before, state, {'mode': 'implement', 'target': 'I01', 'findingIds': []}, report)
    assert state['activeRun'] is None and state['runs'] == before['runs'] + [PREFIX + '.json']
    for old, new in zip(before['tasks'], state['tasks']):
        assert old == new if old['id'] != 'I01' else new == {
            **old, 'status': 'implemented', 'implementationRecord': PREFIX + '.json', 'qualification': new['qualification']}
    for key in before:
        if key not in {'tasks', 'runs'}:
            assert before[key] == state[key], key
    assert driver['selection'](state) == {'mode': 'implement', 'target': 'I02', 'findingIds': []}
    paths = set(git('diff', '--name-only', START).decode().splitlines())
    paths.update(git('ls-files', '--others', '--exclude-standard').decode().splitlines())
    assert paths == set(report['changedPaths']), paths
    for path in paths:
        assert path == STATE or path.startswith(PREFIX)
        raw = (ROOT / path).read_text()
        assert raw.endswith('\n') and all(line == line.rstrip() for line in raw.splitlines()), path
        if path.endswith('.json'):
            json.loads(raw)
    assert not git('diff', '--cached', '--name-only').strip()
    subprocess.run(['git', 'diff', '--check'], cwd=ROOT, check=True)
    assert 'PASS I01 scoped foundation' in output(PREFIX + '-audit.txt')
    print('PASS selected I01-only transition, next I02 selection, exact owned paths, JSON and whitespace. Authorization/model/runner/permissions/reviews/release gates unchanged. No Markdown/UI edit.')


if __name__ == '__main__':
    {'audit': audit, 'validate': validate}[sys.argv[1]]()
