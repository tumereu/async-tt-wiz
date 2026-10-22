"""Check-only independent B2 review evidence and role-scoped handoff validation."""
import base64
import hashlib
import json
from pathlib import Path
import runpy
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PREFIX = 'docs/agent/implementation-runs/20261022T161016Z-466155fc'
START = '3ba4099a9d19c169ba59e275cee117be453d16d3'
STATE = 'docs/agent/implementation-state.json'
TEST = 'androidApp/src/test/kotlin/org/asyncttwiz/android/B2DeckIntegrationTest.kt'


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def load(path):
    return json.loads((ROOT / path).read_text())


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def emit(label, value):
    print(label, json.dumps(value, sort_keys=True))


def xml_cases(raw):
    return [{'name': case.get('name'), 'passed': not any(case.find(tag) is not None
            for tag in ('failure', 'error', 'skipped'))} for case in ET.fromstring(raw).iter('testcase')]


def review():
    before = json.loads(git('show', START + ':' + STATE))
    api = runpy.run_path(str(ROOT / 'scripts/implementation-loop.py'))
    assert api['selection'](before) == {'mode': 'review', 'target': 'B2', 'findingIds': []}
    assert before['authorization'] == 'authorized' and not before['findings']
    for task in before['tasks']:
        if task['batch'] != 'B2':
            continue
        assert task['status'] == 'implemented'
        path = task['implementationRecord']
        commit = git('log', '-1', '--format=%H', START, '--', path).decode().strip()
        assert (ROOT / path).read_bytes() == git('show', commit + ':' + path)
        checks = []
        for check in load(path)['checks']:
            evidence = check['evidence']
            raw = (ROOT / evidence).read_bytes()
            assert raw == git('show', commit + ':' + evidence)
            assert check['kind'] != 'required' or check['exitCode'] == 0
            for support in check.get('supportPaths', []):
                assert (ROOT / support).read_bytes() == git('show', commit + ':' + support)
            checks.append({key: check[key] for key in ('kind', 'exitCode', 'evidence')} | {'sha256': sha(raw)})
        emit('REUSED IMPLEMENTATION EVIDENCE (not re-executed)', {
            'task': task['id'], 'record': path, 'savedCommit': commit, 'checks': checks})

    inputs = 'docs/agent/implementation-runs/20261022T154754Z-ad37196f/input-identities.json'
    tracked = set(git('ls-files').decode().splitlines())
    checked = {}
    for path, identity in load(inputs).items():
        if path in tracked or path == 'androidApp/build/outputs/apk/debug/androidApp-debug.apk':
            raw = (ROOT / path).read_bytes()
            assert len(raw) == identity['bytes'] and sha(raw) == identity['sha256'], path
            checked[path] = identity['sha256']
    emit('UNCHANGED LATEST NATIVE INPUT IDENTITIES', {'manifest': inputs, 'checked': checked})

    i08 = 'docs/agent/implementation-runs/20261022T154754Z-ad37196f/'
    i07b = 'docs/agent/implementation-runs/20261022T151433Z-3ad15f39/'
    i07a = 'docs/agent/implementation-runs/20261022T134014Z-ca978b34/'
    for path in [i08 + 'runner-f150b4a134294290ab4051d1443d8c3a-result-0.xml',
                 i07b + 'runner-cd463eab7d24454c9d5d5a11c248505e-result-0.xml',
                 i07b + 'runner-adf52ab16065449486258bd34dc2052b-result-0.xml']:
        raw = (ROOT / path).read_bytes()
        cases = xml_cases(raw)
        assert cases and all(case['passed'] for case in cases)
        emit('REUSED GREEN NATIVE JOURNEY OR TARGETED TAIL', {'path': path, 'sha256': sha(raw), 'cases': cases})
    captures = [
        i08 + 'runner-f150b4a134294290ab4051d1443d8c3a-merge-failure-landscape.png',
        i08 + 'runner-f150b4a134294290ab4051d1443d8c3a-merged-accepted-portrait.png',
        i07b + 'runner-cd463eab7d24454c9d5d5a11c248505e-full-art-back-landscape.png',
        i07b + 'runner-20bbe88c68344ad29ff60a914c8f55dc-preview-missing-art.png',
        i07a + 'runner-fb49bddbc4ed4919bf440b5c32afdc36-cover-selection-portrait.png',
        i07a + 'runner-6b3a6667abfe4187adcc4478d9ad4745-delete-confirmation-landscape.png',
    ]
    emit('HISTORICAL CAPTURES VISUALLY INSPECTED (not new/final-mock captures)', {
        path: sha((ROOT / path).read_bytes()) for path in captures})

    passing = load(PREFIX + '-passing-junit.json')
    raw = base64.b64decode(passing['base64'], validate=True)
    assert sha(raw) == passing['sha256']
    cases = xml_cases(raw)
    assert len(cases) == 2 and all(case['passed'] for case in cases)
    emit('FRESH PASSING COUPLED-CONTROLLER TESTS', cases)
    diagnostic = ROOT / 'androidApp/build/test-results/testDebugUnitTest/TEST-org.asyncttwiz.android.B2DeckIntegrationTest.xml'
    raw = diagnostic.read_bytes()
    cases = xml_cases(raw)
    assert len(cases) == 1 and not cases[0]['passed']
    assert 'A deck HTTP 401 must expose normal sign-in' in raw.decode()
    assert 'authAccountPresent=true; oldTokenStillAvailable=true' in raw.decode()
    (ROOT / (PREFIX + '-diagnostic-junit.json')).write_text(json.dumps({
        'path': str(diagnostic.relative_to(ROOT)), 'sha256': sha(raw),
        'base64': base64.b64encode(raw).decode(), 'cases': cases}, indent=2) + '\n')
    emit('B2-R01 REPRODUCED DIAGNOSTIC (expected red; no fix claimed)', cases)
    assert load(PREFIX + '-controller-seams.json')['exitCode'] == 0
    assert load(PREFIX + '-reauth-diagnostic.json')['exitCode'] == 1
    scopes = ['androidApp/src/main', 'client-storage/src/main', 'protocol/src/commonMain', 'server/src/main']
    assert not git('diff', START, '--', *scopes).strip()
    emit('REVIEWED RUNTIME SOURCE IDENTITIES', {path: sha((ROOT / path).read_bytes())
        for path in git('ls-files', '--', *scopes).decode().splitlines()})
    print('PASS evidence reconciliation and two new passing integration assertions; B2-R01 remains actionable. '
          'Historical diagnostic segments are not whole green journeys. No new native/full-suite/final UI/release pass.')


def validate():
    print('Checking B2 review handoff, exact owned paths and documented diagnostic failure.', flush=True)
    before = json.loads(git('show', START + ':' + STATE))
    after, report = load(STATE), load(PREFIX + '.json')
    api = runpy.run_path(str(ROOT / 'scripts/implementation-loop.py'))
    api['validate_state'](after)
    api['validate_transition'](before, after, {'mode': 'review', 'target': 'B2', 'findingIds': []}, report)
    assert git('rev-parse', 'HEAD').decode().strip() == START
    assert before['tasks'] == after['tasks'] and after['activeRun'] is None
    assert after['runs'] == before['runs'] + [PREFIX + '.json']
    assert len(after['findings']) == 1 and after['findings'][0]['id'] == 'B2-R01'
    for old, new in zip(before['batches'], after['batches'], strict=True):
        assert new == ({**old, 'status': 'changes-requested',
            'review': {**old['review'], 'completed': {'capability': START}}} if old['id'] == 'B2' else old)
    assert all(before[key] == after[key] for key in before if key not in ('runs', 'findings', 'batches'))
    assert api['selection'](after) == {'mode': 'fix', 'target': 'B2', 'findingIds': ['B2-R01']}
    actual = set(git('diff', '--name-only', START).decode().splitlines()) | set(
        git('ls-files', '--others', '--exclude-standard').decode().splitlines())
    assert actual == set(report['changedPaths']), actual ^ set(report['changedPaths'])
    allowed = {STATE, PREFIX + '.json', TEST} | {check['evidence'] for check in report['checks']}
    allowed |= {path for check in report['checks'] for path in check.get('supportPaths', [])}
    assert actual <= allowed
    for path in actual:
        text = (ROOT / path).read_text()
        assert text.endswith('\n') and all(line == line.rstrip() for line in text.splitlines()), path
        if path.endswith('.json'):
            json.loads(text)
    assert all(check['kind'] != 'required' or check['exitCode'] == 0 for check in report['checks'])
    subprocess.run(['git', 'diff', '--check'], cwd=ROOT, check=True)
    print('PASS B2 review-only transition, one open evidenced finding, exact test/report/check paths, '
          'unchanged authorization/model/permissions/tasks/release gates. Fresh B2-R01 fix is next. No UI-document edit.')


if __name__ == '__main__':
    {'review': review, 'validate': validate}[sys.argv[1]]()
