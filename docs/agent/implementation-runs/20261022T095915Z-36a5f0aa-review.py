"""Check-only B1 review evidence/handoff; no runtime, device or build mutation."""
import base64
import hashlib
import json
from pathlib import Path
import runpy
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PREFIX = 'docs/agent/implementation-runs/20261022T095915Z-36a5f0aa'
STATE = 'docs/agent/implementation-state.json'
START = '5283daee31ac94d53c2c9c92e2b9c6a0abc1f92a'
TEST = 'androidApp/src/test/kotlin/org/asyncttwiz/android/B1EntryIntegrationTest.kt'


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def load(path):
    return json.loads((ROOT / path).read_text())


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def emit(label, value):
    print(label, json.dumps(value, sort_keys=True))


def xml_result(raw):
    suite = ET.fromstring(raw)
    return [{'class': case.get('classname'), 'name': case.get('name'),
             'passed': not any(case.find(tag) is not None for tag in ('failure', 'error', 'skipped'))}
            for case in suite.iter('testcase')]


def bundle(path):
    result = {}
    for name, item in load(path).items():
        raw = base64.b64decode(item['base64'], validate=True) if 'base64' in item else (ROOT / item['path']).read_bytes()
        assert sha(raw) == item['sha256'], (path, name)
        result[name] = raw
    return result


def review():
    before = json.loads(git('show', START + ':' + STATE))
    api = runpy.run_path(str(ROOT / 'scripts/implementation-loop.py'))
    assert api['selection'](before) == {'mode': 'review', 'target': 'B1', 'findingIds': []}
    tasks = {task['id']: task for task in before['tasks']}
    assert all(task['status'] == 'implemented' for task in before['tasks'] if task['batch'] == 'B1')
    assert not before['findings'] and before['authorization'] == 'authorized'

    for identifier in ('I01', 'I02', 'I03', 'I04'):
        record = tasks[identifier]['implementationRecord']
        saved = git('log', '-1', '--format=%H', START, '--', record).decode().strip()
        report = load(record)
        assert git('show', saved + ':' + record) == (ROOT / record).read_bytes()
        checks = []
        for check in report['checks']:
            path = check['evidence']
            raw = (ROOT / path).read_bytes()
            assert raw == git('show', saved + ':' + path)
            if check['kind'] == 'required':
                assert check['exitCode'] == 0
            checks.append({'kind': check['kind'], 'exitCode': check['exitCode'], 'path': path, 'sha256': sha(raw)})
        emit('HISTORICAL IMPLEMENTATION EVIDENCE (not re-executed)', {
            'task': identifier, 'record': record, 'savedCommit': saved, 'checks': checks})

    # Independently inspect native result/capture bytes rather than interpreting task statuses as passes.
    entry = 'docs/agent/implementation-runs/20261022T054937Z-41060cc6/runner-896baf0a99614aae85a9747f0d8ddeb1/'
    assert all(case['passed'] for case in xml_result((ROOT / (entry + 'result-0.xml')).read_bytes()))
    emit('REUSED NATIVE ACCOUNT JOURNEY', {'path': entry + 'result-0.xml',
        'cases': xml_result((ROOT / (entry + 'result-0.xml')).read_bytes())})
    shell = 'docs/agent/implementation-runs/20261022T070751Z-df665f98/shell-passing-before-final-fullscreen-fix/'
    raw = load(shell + 'result-0.xml')['output'].encode()
    assert sha(raw) == load(shell + 'result-0.xml')['originalSha256']
    assert all(case['passed'] for case in xml_result(raw))
    emit('REUSED NATIVE SHELL JOURNEY (captures predate fullscreen repair)', {
        'path': shell + 'result-0.xml', 'cases': xml_result(raw)})
    reset = 'docs/agent/implementation-runs/20261022T084055Z-9e1f1522/'
    core = bundle(reset + 'first-runner-8f3cb5377c9547448659c5ee503aee32.json')
    tail = bundle(reset + 'first-runner-c2f40f4956cd451fa38a18eacac23286.json')
    assert not all(case['passed'] for case in xml_result(core['result-0.xml']))
    failure = ET.fromstring(core['result-0.xml']).find('testcase/failure')
    assert failure is not None and 'Device.logout' in failure.text
    assert all(case['passed'] for case in xml_result(tail['result-0.xml']))
    assert json.loads(tail['passed.json'])['devices'] == 2
    for name in ('authorization-false-108325073279127.json', 'native-intent-input-saved-state.json'):
        assert 'OK (1 test)' in json.loads(core[name])['output']
    emit('REUSED TWO-DEVICE CORE AND TARGETED TAIL (not a whole green run)', {
        'core': xml_result(core['result-0.xml']), 'tail': xml_result(tail['result-0.xml']),
        'artifactLimitations': load(reset[:-1] + '.json')['artifactQualification'],
        'retainedTailObservation': json.loads(tail['retained-tail-observation.json'])})
    captures = [entry + name for name in ('a01-portrait.png', 'a01-landscape.png', 'a02b-landscape.png')]
    captures += [shell + name for name in ('n02-portrait.png', 'games-landscape.png')]
    captures += [reset + 'first-runner-8f3cb5377c9547448659c5ee503aee32-' + name for name in (
        'a03-landscape.png', 'a04-portrait.png', 'a04-landscape.png', 'a04-used-link-landscape.png')]
    captures += [reset + 'first-runner-c2f40f4956cd451fa38a18eacac23286-clean-second-account.png']
    emit('HISTORICAL PNGS VISUALLY INSPECTED IN THIS REVIEW (not new captures)', {
        path: sha((ROOT / path).read_bytes()) for path in captures})

    scopes = ['androidApp/src/main', 'server/src/main', 'client-storage/src/main', 'protocol/src/commonMain']
    sources = git('ls-files', '--', *scopes).decode().splitlines()
    emit('STARTING RUNTIME SOURCE IDENTITIES', {path: sha((ROOT / path).read_bytes()) for path in sources})
    assert not git('diff', START, '--', *scopes).strip()
    output = load(PREFIX + '-controller-seams.json')
    assert output['exitCode'] == 0
    assert sha(output['output'].encode()) == output['outputSha256']
    assert 'BUILD SUCCESSFUL' in output['output']
    junit = ROOT / 'androidApp/build/test-results/testDebugUnitTest/TEST-org.asyncttwiz.android.B1EntryIntegrationTest.xml'
    cases = xml_result(junit.read_bytes())
    assert len(cases) == 2 and all(case['passed'] for case in cases)
    retained = {'path': str(junit.relative_to(ROOT)), 'sha256': sha(junit.read_bytes()),
                'base64': base64.b64encode(junit.read_bytes()).decode(), 'cases': cases}
    (ROOT / (PREFIX + '-junit.json')).write_text(json.dumps(retained, indent=2) + '\n')
    emit('FRESH CONTROLLER INTEGRATION CHECK', {'testSource': TEST, 'sha256': sha((ROOT / TEST).read_bytes()), 'cases': cases})
    print('PASS B1 evidence and two fresh controller-seam tests. Independent source/behavioral review found no actionable defect. No new native run, full suite, final mock qualification or release clearance claimed.')


def validate():
    print('Checking the B1 review handoff and owned output paths.', flush=True)
    before = json.loads(git('show', START + ':' + STATE))
    after, report = load(STATE), load(PREFIX + '.json')
    api = runpy.run_path(str(ROOT / 'scripts/implementation-loop.py'))
    choice = {'mode': 'review', 'target': 'B1', 'findingIds': []}
    assert git('rev-parse', 'HEAD').decode().strip() == START
    api['validate_state'](after)
    api['validate_transition'](before, after, choice, report)
    assert after['activeRun'] is None and after['runs'] == before['runs'] + [PREFIX + '.json']
    assert after['tasks'] == before['tasks'] and after['findings'] == before['findings']
    for old, new in zip(before['batches'], after['batches'], strict=True):
        assert new == ({**old, 'status': 'reviewed', 'reviewedCommit': START,
            'review': {**old['review'], 'completed': {'capability': START}}} if old['id'] == 'B1' else old)
    assert all(before[key] == after[key] for key in before if key not in ('runs', 'batches'))
    assert api['selection'](after) == {'mode': 'implement', 'target': 'I05', 'findingIds': []}
    actual = set(git('diff', '--name-only', START).decode().splitlines()) | set(git('ls-files', '--others', '--exclude-standard').decode().splitlines())
    assert actual == set(report['changedPaths']), actual ^ set(report['changedPaths'])
    allowed = {STATE, PREFIX + '.json'} | {check['evidence'] for check in report['checks']}
    allowed |= {path for check in report['checks'] for path in check.get('supportPaths', [])}
    assert actual <= allowed | {TEST}
    assert not git('diff', '--cached', '--name-only').strip()
    for path in actual:
        raw = (ROOT / path).read_text()
        assert raw.endswith('\n') and all(line == line.rstrip() for line in raw.splitlines()), path
        if path.endswith('.json'):
            json.loads(raw)
    for check in report['checks']:
        assert (ROOT / check['evidence']).is_file()
        assert check['kind'] != 'required' or check['exitCode'] == 0
    subprocess.run(['git', 'diff', '--check'], check=True, cwd=ROOT)
    print('PASS B1-only independent review transition and exact owned test/report/evidence paths; I05 next. Tasks, authorization, model, runner, permission profile and external gates unchanged. No Markdown/UI-document edits.')


if __name__ == '__main__':
    {'review': review, 'validate': validate}[sys.argv[1]]()
