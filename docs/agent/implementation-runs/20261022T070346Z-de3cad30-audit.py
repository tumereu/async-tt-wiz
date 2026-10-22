"""I02 parent evidence/input reconciliation only; no builds, devices or services."""
import hashlib
import json
from pathlib import Path
import runpy
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PREFIX = 'docs/agent/implementation-runs/20261022T070346Z-de3cad30'
STATE = 'docs/agent/implementation-state.json'
START = '96f12440cd47d6124aa8e7e810445287b324631e'
CHILDREN = {'I02a': 'db9e1f9da76211a3eb9d92658ba4ad2ad040965e', 'I02b': START}


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def load(path):
    return json.loads((ROOT / path).read_text())


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def output(path):
    raw = (ROOT / path).read_bytes()
    try:
        data = json.loads(raw)
    except ValueError:
        return raw.decode()
    if isinstance(data, dict) and 'outputSha256' in data:
        assert sha(data['output'].encode()) == data['outputSha256'], path
        return data['output']
    if isinstance(data, dict) and data.get('format') == 'lossless-utf8-output-v1':
        assert sha(data['output'].encode()) == data['originalSha256'], path
        assert len(data['output'].encode()) == data['originalBytes'], path
        return data['output']
    return raw.decode()


def emit(label, data):
    print(label, json.dumps(data, sort_keys=True))


def audit():
    before = json.loads(git('show', START + ':' + STATE))
    driver = runpy.run_path(str(ROOT / 'scripts/implementation-loop.py'))
    assert driver['selection'](before) == {'mode': 'implement', 'target': 'I02', 'findingIds': []}
    assert before['authorization'] == 'authorized' and before['mode'] == 'active'
    assert not before['findings'] and before['activeRun'] is None
    tasks = {task['id']: task for task in before['tasks']}
    assert tasks['I01']['status'] == 'implemented' and tasks['I02']['children'] == list(CHILDREN)
    records = {}
    for identifier, commit in CHILDREN.items():
        task = tasks[identifier]
        assert task['status'] == 'implemented'
        path = task['implementationRecord']
        report = load(path)
        records[identifier] = path[:-5]
        assert report['target'] == identifier and git('show', commit + ':' + path) == (ROOT / path).read_bytes()
        checks = []
        for check in report['checks']:
            evidence = check['evidence']
            raw = (ROOT / evidence).read_bytes()
            assert git('show', commit + ':' + evidence) == raw, evidence
            assert check['kind'] != 'required' or check['exitCode'] == 0
            output(evidence)
            checks.append({key: check[key] for key in ('kind', 'exitCode', 'evidence')} | {'sha256': sha(raw)})
        emit('REUSED CHILD (historical checks, not new executions)', {
            'task': identifier, 'savedCommit': commit, 'record': path, 'checks': checks})
    # I02b changed client/tooling, not the qualified server/shared/storage inputs.
    scopes = ['server', 'core', 'protocol', 'client-storage', 'scripts/build-server-image.py',
              'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties']
    assert not git('diff', '--name-only', CHILDREN['I02a'], START, '--', *scopes).strip()
    identities = {path: sha(git('show', START + ':' + path)) for path in
                  git('ls-tree', '-r', '--name-only', START, '--', *scopes).decode().splitlines()}
    assert all(sha((ROOT / path).read_bytes()) == digest for path, digest in identities.items())
    emit('UNCHANGED SERVER/SHARED/STORAGE/BUILD INPUTS', identities)
    client = records['I02b']
    identities = load(client + '/input-identities.json')
    for path, identity in identities.items():
        assert sha((ROOT / path).read_bytes()) == identity['sha256'], path
    emit('CURRENT EXACT CLIENT/PROTOCOL/LOCK/APK/IMAGE IDENTITIES (not rebuilt)', identities)
    # Reconcile actual combined execution, not just task status or a summary claim.
    server = records['I02a']
    combined = {}
    for suffix in ('-tests-initial-xml.json', '-tests-affected-xml.json'):
        for xml in load(server + suffix).values():
            for case in ET.fromstring(xml).iter('testcase'):
                combined[(case.attrib['classname'], case.attrib['name'])] = case
    handoff = output(server + '-handoff.txt')
    final = json.loads(next(line.split(' XML JSON ', 1)[1] for line in handoff.splitlines()
                            if line.startswith('ACTUAL final schema-process XML JSON ')))
    assert sha(final['xml'].encode()) == final['sha256']
    for case in ET.fromstring(final['xml']).iter('testcase'):
        combined[(case.attrib['classname'], case.attrib['name'])] = case
    assert len(combined) == 27
    assert not any(list(case.iter(tag)) for case in combined.values() for tag in ('failure', 'error', 'skipped'))
    emit('REUSED SERVER GREEN ASSERTIONS (original red commands remain diagnostic)', sorted(combined))
    directory = client + '/runner-896baf0a99614aae85a9747f0d8ddeb1'
    cases = list(ET.fromstring(output(directory + '/result-0.xml')).iter('testcase'))
    assert len(cases) == 1 and not any(list(c.iter(tag)) for c in cases for tag in ('failure', 'error', 'skipped'))
    assert load(directory + '/exit.json')['exitCode'] == 0
    ready = load(directory + '/ready.json')
    assert ready['device']['api'] == '29' and ready['device']['gms'] is False
    assert ready['app']['apk']['sha256'] == identities['androidApp/build/outputs/apk/debug/androidApp-debug.apk']['sha256']
    assert load(directory + '/session.json')['sessionId'] == load(directory + '/session-closed.json')['sessionId']
    emit('REUSED REAL NORMAL-APK/COMPOSE ACCOUNT JOURNEY', {
        'directory': directory, 'test': cases[0].attrib, 'apk': ready['app']['apk'],
        'observations': load(directory + '/accounts-observation.json')})
    for path in ('device-cleaned.json', 'environment-cleaned.json', 'old-environment-cleaned.json'):
        emit('RETAINED OWNED CLEANUP', load(client + '/' + path))
    ledger = runpy.run_path(str(ROOT / 'scripts/coverage-ledger.py'))
    emit('SOURCE-PINNED COVERAGE (assignments, not executed passes)', ledger['validate'](load('docs/agent/coverage/ledger.json')))
    print('PASS I02 scoped account outcome: unchanged server plus exact integrated client/protocol/APK/image inputs and actual entry/restart/revocation evidence. No missing integration seam; no build/device/full-suite rerun. I03 shell/Logout, I04 reset/second device, later social/game privacy, B1 independent review, I34/I35/I36 qualification and all external release gates remain open.')


def validate():
    print('Checking selected I02 handoff and owned outputs.', flush=True)
    before = json.loads(git('show', START + ':' + STATE))
    state, report = load(STATE), load(PREFIX + '.json')
    driver = runpy.run_path(str(ROOT / 'scripts/implementation-loop.py'))
    driver['validate_state'](state)
    driver['validate_transition'](before, state, {'mode': 'implement', 'target': 'I02', 'findingIds': []}, report)
    assert state['activeRun'] is None and state['runs'] == before['runs'] + [PREFIX + '.json']
    for old, new in zip(before['tasks'], state['tasks']):
        assert old == new if old['id'] != 'I02' else new == {
            **old, 'status': 'implemented', 'implementationRecord': PREFIX + '.json', 'qualification': new['qualification']}
    assert all(before[key] == state[key] for key in before if key not in ('tasks', 'runs'))
    assert driver['selection'](state) == {'mode': 'implement', 'target': 'I03', 'findingIds': []}
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
    assert 'PASS I02 scoped account outcome' in output(PREFIX + '-audit.txt')
    print('PASS selected I02-only transition, next I03, exact owned paths, JSON and whitespace; authorization/model/runner/reviews/release gates unchanged. No Markdown/UI edit.')


if __name__ == '__main__':
    {'audit': audit, 'validate': validate}[sys.argv[1]]()
