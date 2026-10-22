"""Read-only I06 parent reconciliation; retained results are not new test runs."""
import base64
import hashlib
import json
from pathlib import Path
import runpy
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path.cwd()
PREFIX = Path(__file__).resolve().parent.relative_to(ROOT)
RECORD = str(PREFIX.with_suffix('.json'))
STATE = 'docs/agent/implementation-state.json'


def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()


def load(path):
    return json.loads(Path(path).read_text())


def sha(data):
    return hashlib.sha256(data).hexdigest()


def green(data):
    cases = list(ET.fromstring(data).iter('testcase'))
    assert cases and not any(list(c.iter(tag)) for c in cases for tag in ('failure', 'error', 'skipped'))
    return [c.attrib['name'] for c in cases]


report = load(RECORD)
before = json.loads(git('show', report['startCommit'] + ':' + STATE))
after = load(STATE)
api = runpy.run_path('scripts/implementation-loop.py')
choice = dict(mode='implement', target='I06', findingIds=[])
assert git('rev-parse', 'HEAD') == report['startCommit']
assert git('branch', '--show-current') == 'master' and not git('diff', '--cached', '--name-only')
assert before['mode'] == 'active' and before['authorization'] == 'authorized'
assert 'D-304' in Path(before['authorizationRecord']).read_text()
assert before['runner']['model'] == 'openai/gpt-6.1-sol#high' and before['findings'] == []
assert api['selection'](before) == choice
api['validate_state'](after)
api['validate_transition'](before, after, choice, report)
old = {t['id']: t for t in before['tasks']}
new = {t['id']: t for t in after['tasks']}
assert new['I06']['status'] == 'implemented' and new['I06']['implementationRecord'] == RECORD
assert all(new[k] == v for k, v in old.items() if k != 'I06')
assert all(before[k] == after[k] for k in before if k not in ('tasks', 'runs'))
assert after['runs'] == before['runs'] + [RECORD] and after['activeRun'] is None
assert api['selection'](after) == dict(mode='implement', target='I07', findingIds=[])

records = []
commits = {}
for task_id in ('I06a', 'I06b', 'I06b1', 'I06b2'):
    task = old[task_id]
    assert task['status'] == 'implemented'
    path = task['implementationRecord']
    child = load(path)
    commit = git('log', '--diff-filter=A', '--format=%H', '--', path).splitlines()[-1]
    commits[task_id] = commit
    subprocess.run(['git', 'merge-base', '--is-ancestor', commit, report['startCommit']], check=True)
    assert child['target'] == task_id and child['mode'] == 'implement' and child['outcome'] == 'progress'
    for p in child['changedPaths']:
        if p.startswith('docs/agent/implementation-runs/'):
            assert Path(p).read_bytes() == subprocess.check_output(['git', 'show', commit + ':' + p]), p
    checks = []
    for check in child['checks']:
        p = check['evidence']
        assert check['kind'] == 'diagnostic' or check['exitCode'] == 0
        if p.endswith('.json'):
            value = load(p)
            assert value['exitCode'] == check['exitCode'], p
            if 'outputSha256' in value:
                assert sha(value['output'].encode()) == value['outputSha256'], p
        assert all(p in child['changedPaths'] for p in check.get('supportPaths', []))
        checks.append(dict(kind=check['kind'], exitCode=check['exitCode'], evidence=p,
                           sha256=sha(Path(p).read_bytes())))
    records.append(dict(id=task_id, record=path, commit=commit,
                        reportSha256=sha(Path(path).read_bytes()), checks=checks))

parser = 'docs/agent/implementation-runs/20261022T114326Z-f8a9eff1/'
native = 'docs/agent/implementation-runs/20261022T124301Z-3bc7cb2f/'
parent = 'docs/agent/implementation-runs/20261022T132840Z-1bd01564/audit.txt'
prior = load(parent)
assert prior['result'] == 'PASS' and prior['completed'] == 'I06b only'
assert git('diff', '--name-only', commits['I06b2'], report['startCommit'],
           'androidApp', 'client-storage', 'core', 'protocol', 'server', 'gradle', 'e2e', 'tests', 'scripts') == ''
assert git('diff', '--name-only', commits['I06a'], report['startCommit'],
           'client-storage/src', 'tests/fixtures') == ''
manifest = load(native + 'artifact-identities.json')
for path, identity in manifest.items():
    data = Path(path).read_bytes()
    assert len(data) == identity['bytes'] and sha(data) == identity['sha256'], path
reused_cases = {}
for envelope in (parser + 'host-junit.json', native + 'host-junit.json'):
    for path, value in load(envelope).items():
        data = base64.b64decode(value['base64'])
        assert len(data) == value['bytes'] and sha(data) == value['sha256']
        reused_cases[envelope + ':' + path] = green(data)
for prefix in (parser + 'runner-acaf719979794f55b47ce44ca7839b9f-',
               native + 'runner-597854085e4f4790b19bd6ad052b8b88-'):
    assert len(green(Path(prefix + 'result-0.xml').read_bytes())) == 1
    assert load(prefix + 'session-closed.json')['closed'] is True
for directory in (parser, native):
    for name in ('cleanup-device', 'cleanup-environment'):
        assert load(directory + name + '.json')['exitCode'] == 0

actual = set(git('diff', '--name-only').splitlines()) | set(git('ls-files', '--others', '--exclude-standard').splitlines())
assert actual == set(report['changedPaths']), (actual, report['changedPaths'])
runner = object.__new__(api['Runner'])
runner.root = ROOT
runner.state_path = Path(STATE)
runner.state_relative = STATE
runner.role_paths(before, choice, report, actual, RECORD)
subprocess.run(['git', 'diff', '--check'], check=True)
for p in actual - {str(PREFIX / 'audit.txt')}:
    data = Path(p).read_bytes()
    assert data.endswith(b'\n') and b'\r' not in data
    assert all(line.rstrip() == line for line in data.splitlines()), p
    if p.endswith('.json'):
        json.loads(data)

print(json.dumps(dict(
    result='PASS', startCommit=report['startCommit'], selected=choice,
    completed='I06 only', nextSelection=api['selection'](after), childRecords=records,
    inputAudit=dict(currentManifest=native + 'artifact-identities.json',
                    manifestSha256=sha(Path(native + 'artifact-identities.json').read_bytes()),
                    currentIdentitiesMatched=len(manifest), parserResolverAndFixturesUnchanged=True,
                    runtimeBuildAndTestInputsUnchangedSinceFinalNativeSave=True),
    reusedHostCases=reused_cases,
    reusedSaveAudit=dict(path=parent, sha256=sha(Path(parent).read_bytes()),
                        exactResults=prior['reusedResults']),
    integration='Final I06b2 normal APK already exercises I06a parser/catalog correction/preview with real named save/failure/retry/restart. Parser/resolver/fixtures unchanged; save wiring covered by retained affected host and native checks. No missing changed seam, product rerun or new test-pass claim.',
    handoff='State/transition/role/owned paths/JSON/whitespace pass. Only selected parent advances; all reviews/findings/ledger/release gates unchanged.',
    remaining=report['remainingObligations']), indent=2))
