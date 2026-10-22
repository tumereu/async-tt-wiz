"""I04 parent evidence reconciliation; no product build, service or device execution."""
import base64
import hashlib
import json
from pathlib import Path
import runpy
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PREFIX = 'docs/agent/implementation-runs/20261022T095434Z-3f5c6c3a'
STATE = 'docs/agent/implementation-state.json'
START = 'e9b51a762c680a99f38a98aeebda1973abfc233f'
CHILDREN = {'I04a': 'f668bbd60232da10c7212a40e083999cf60ff2fd', 'I04b': START}


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def load(path):
    return json.loads((ROOT / path).read_text())


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def emit(label, value):
    print(label, json.dumps(value, sort_keys=True))


def command_output(path):
    data = load(path)
    raw = data['output'].encode() if 'output' in data else base64.b64decode(data['outputBase64'], validate=True)
    assert sha(raw) == data['outputSha256'], path
    return data, raw.decode()


def cases(path):
    result = []
    for filename, encoded in load(path).items():
        suite = ET.fromstring(base64.b64decode(encoded, validate=True))
        result.extend((test.attrib, not any(test.find(tag) is not None for tag in ('failure', 'error', 'skipped')))
                      for test in suite.iter('testcase'))
    return result


def bundle(path):
    entries = load(path)
    decoded = {}
    for name, entry in entries.items():
        raw = base64.b64decode(entry['base64'], validate=True) if 'base64' in entry else (ROOT / entry['path']).read_bytes()
        assert sha(raw) == entry['sha256'], (path, name)
        decoded[name] = raw
    return decoded


def audit():
    before = json.loads(git('show', START + ':' + STATE))
    api = runpy.run_path(str(ROOT / 'scripts/implementation-loop.py'))
    assert api['selection'](before) == {'mode': 'implement', 'target': 'I04', 'findingIds': []}
    assert before['authorization'] == 'authorized' and before['mode'] == 'active'
    assert not before['findings'] and before['activeRun'] is None
    tasks = {task['id']: task for task in before['tasks']}
    assert tasks['I04']['children'] == list(CHILDREN)
    prefixes = {}
    for identifier, commit in CHILDREN.items():
        task = tasks[identifier]
        assert task['status'] == 'implemented'
        path = task['implementationRecord']
        report = load(path)
        assert report['target'] == identifier
        prefixes[identifier] = path[:-5]
        # All retained evidence/support/source bytes belong to the saved child, not just its status.
        hashes = {}
        for changed in report['changedPaths']:
            if changed == STATE or (identifier == 'I04a' and changed in load(tasks['I04b']['implementationRecord'])['changedPaths']):
                continue
            raw = (ROOT / changed).read_bytes()
            assert git('show', commit + ':' + changed) == raw, changed
            hashes[changed] = sha(raw)
        checks = []
        for check in report['checks']:
            path = check['evidence']
            assert git('show', commit + ':' + path) == (ROOT / path).read_bytes()
            data, _ = command_output(path)
            assert data['exitCode'] == check['exitCode']
            assert check['kind'] != 'required' or check['exitCode'] == 0
            checks.append({key: check[key] for key in ('evidence', 'kind', 'exitCode')})
        emit('REUSED CHILD (historical checks, not new executions)', {
            'id': identifier, 'savedCommit': commit, 'record': task['implementationRecord'], 'checks': checks, 'savedBytesSha256': hashes})
    a, b = prefixes['I04a'], prefixes['I04b']
    # I04b adds only the read-only preflight to qualified server/protocol production sources.
    scopes = ['server/src/main', 'server/src/schemaTools', 'server/build.gradle.kts', 'server/gradle.lockfile',
              'protocol', 'core', 'client-storage', 'gradle', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties',
              'e2e/compose.json', 'e2e/environment.py', 'scripts/build-server-image.py']
    delta = git('diff', '--name-only', CHILDREN['I04a'], START, '--', *scopes).decode().splitlines()
    assert set(delta) == {'server/src/main/kotlin/org/asyncttwiz/server/accounts/AccountHttp.kt',
                          'protocol/src/commonMain/kotlin/org/asyncttwiz/protocol/Accounts.kt'}, delta
    emit('I04b SERVER/PROTOCOL DELTA (covered by affected HTTP and actual integrated image)', delta)
    identities = load(b + '/artifact-identities.json')
    for path, expected in identities['sources'].items():
        assert sha((ROOT / path).read_bytes()) == expected, path
    for path, expected in identities.items():
        if path != 'sources':
            assert sha((ROOT / path).read_bytes()) == expected, path
    emit('EXACT CURRENT SOURCE/FINAL APK/IMAGE IDENTITIES (not rebuilt)', identities)
    image = load(b + '/image-manifest.json')
    for filename, expected in image['libraries'].items():
        assert sha((ROOT / 'server/build/install/server/lib' / filename).read_bytes()) == expected
    emit('REUSED INTEGRATED LOCAL IMAGE', {'imageId': image['imageId'], 'distributionSha256': image['distributionSha256']})
    green = set()
    for filename in ('initial-junit.json', 'reset-junit.json', 'affected-recheck-junit.json'):
        results = cases(a + '/' + filename)
        if filename != 'initial-junit.json':
            assert all(passed for _, passed in results)
        green.update((attrs['classname'], attrs['name']) for attrs, passed in results if passed)
        emit('HISTORICAL SERVER JUNIT', {'path': a + '/' + filename, 'tests': len(results), 'nongreen': sum(not p for _, p in results)})
    emit('REUSED DISTINCT GREEN SERVER ASSERTIONS (red originals remain red)', sorted(green))
    for filename in ('host-junit.json', 'pending-logout-junit.json'):
        results = cases(b + '/' + filename)
        assert results and all(passed for _, passed in results)
        emit('REUSED AFFECTED FINAL HOST/HTTP ASSERTIONS', {'path': b + '/' + filename, 'tests': len(results)})
    core = bundle(b + '/first-runner-8f3cb5377c9547448659c5ee503aee32.json')
    tail = bundle(b + '/first-runner-c2f40f4956cd451fa38a18eacac23286.json')
    failure = ET.fromstring(core['result-0.xml']).find('testcase/failure')
    assert failure is not None and 'Device.logout' in failure.text and 'Forgot password?' in failure.text
    assert 'OK (1 test)' in json.loads(core['native-intent-input-saved-state.json'])['output']
    assert 'OK (1 test)' in json.loads(core['authorization-false-108325073279127.json'])['output']
    for name in ('a03-landscape.xml', 'a04-portrait.xml', 'a04-landscape.xml', 'a04-used-link-portrait.xml',
                 'a04-used-link-landscape.xml', 'new-password-resumed.xml'):
        assert name in core
    tail_cases = list(ET.fromstring(tail['result-0.xml']).iter('testcase'))
    assert len(tail_cases) == 1 and not any(tail_cases[0].find(tag) is not None for tag in ('failure', 'error', 'skipped'))
    assert json.loads(tail['passed.json'])['devices'] == 2
    for entries in (core, tail):
        assert json.loads(entries['session.json'])['sessionId'] == json.loads(entries['session-closed.json'])['sessionId']
        assert json.loads(entries['inputs.json'])['ready']['app']['apk']['sha256'] == load(b + '.json')['artifactQualification']['journeyApkSha256']
    emit('REUSED RETAINED TWO-DEVICE TAIL', json.loads(tail['retained-tail-observation.json']))
    emit('EXPLICIT NATIVE LIMITATIONS', {
        'originalCoreExit': 'diagnostic failure after completed reset/revocation/reuse/resume assertions; not a wholly green run',
        'finalApkDelta': load(b + '.json')['artifactQualification'],
        'deferred': load(b + '.json')['noncriticalFollowups']})
    for name in ('cleanup-first.json', 'cleanup-second.json', 'cleanup-environment.json'):
        data, _ = command_output(b + '/' + name)
        assert data['exitCode'] == 0
    ledger = runpy.run_path(str(ROOT / 'scripts/coverage-ledger.py'))
    emit('COVERAGE ASSIGNMENTS (not full scenario passes)', ledger['validate'](load('docs/agent/coverage/ledger.json')))
    print('PASS I04 scoped outcome reconciled: exact child inputs/evidence and real server-mail-native integration; no uncovered ordinary integration seam. No new build/device/full-suite run or independent review. B1 review due; I34/I35/I36 deferred assertions and all external gates retained.')


def validate():
    print('Checking I04 evidence-only handoff and exact owned outputs.', flush=True)
    before = json.loads(git('show', START + ':' + STATE))
    after, report = load(STATE), load(PREFIX + '.json')
    api = runpy.run_path(str(ROOT / 'scripts/implementation-loop.py'))
    choice = {'mode': 'implement', 'target': 'I04', 'findingIds': []}
    assert git('rev-parse', 'HEAD').decode().strip() == START
    assert git('branch', '--show-current').decode().strip() == 'master'
    api['validate_state'](after)
    api['validate_transition'](before, after, choice, report)
    assert after['activeRun'] is None and after['runs'] == before['runs'] + [PREFIX + '.json']
    for old, new in zip(before['tasks'], after['tasks'], strict=True):
        assert old == new if old['id'] != 'I04' else new == {
            **old, 'status': 'implemented', 'implementationRecord': PREFIX + '.json', 'qualification': new['qualification']}
    for old, new in zip(before['batches'], after['batches'], strict=True):
        assert new == ({**old, 'status': 'ready-for-review'} if old['id'] == 'B1' else old)
    assert all(before[key] == after[key] for key in before if key not in ('tasks', 'runs', 'batches'))
    assert api['selection'](after) == {'mode': 'review', 'target': 'B1', 'findingIds': []}
    actual = set(git('diff', '--name-only', START).decode().splitlines()) | set(git('ls-files', '--others', '--exclude-standard').decode().splitlines())
    assert actual == set(report['changedPaths']), actual ^ set(report['changedPaths'])
    for path in actual:
        assert path == STATE or path.startswith(PREFIX)
        raw = (ROOT / path).read_text()
        assert raw.endswith('\n') and all(line == line.rstrip() for line in raw.splitlines()), path
        if path.endswith('.json'):
            json.loads(raw)
    assert not git('diff', '--cached', '--name-only').strip()
    subprocess.run(['git', 'diff', '--check'], cwd=ROOT, check=True)
    assert 'PASS I04 scoped outcome' in (ROOT / (PREFIX + '-audit.txt')).read_text()
    print('PASS I04-only implementation closure, B1 ready for first independent review, exact owned JSON/text paths and whitespace. Model/authorization/runner/other tasks/review coverage/findings/external gates unchanged; no Markdown/UI edit.')


if __name__ == '__main__':
    {'audit': audit, 'validate': validate}[sys.argv[1]]()
