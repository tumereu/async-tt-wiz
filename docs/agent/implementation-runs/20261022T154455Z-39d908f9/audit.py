"""Read-only I07 parent reconciliation; old results are not new test executions."""
import base64
import hashlib
import json
from pathlib import Path
import re
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


def xml_bytes(value):
    if isinstance(value, str):
        return value.encode()
    data = base64.b64decode(value['base64'])
    assert len(data) == value['bytes'] and sha(data) == value['sha256']
    return data


def cases(data, require_green=True):
    found = list(ET.fromstring(data).iter('testcase'))
    assert found
    passed, failed = [], []
    for case in found:
        target = failed if any(list(case.iter(t)) for t in ('failure', 'error', 'skipped')) else passed
        target.append(case.attrib['name'])
    assert not require_green or not failed, failed
    return passed, failed


report = load(RECORD)
before = json.loads(git('show', report['startCommit'] + ':' + STATE))
after = load(STATE)
api = runpy.run_path('scripts/implementation-loop.py')
choice = dict(mode='implement', target='I07', findingIds=[])
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
assert new['I07']['status'] == 'implemented' and new['I07']['implementationRecord'] == RECORD
assert all(new[k] == v for k, v in old.items() if k != 'I07')
assert all(before[k] == after[k] for k in before if k not in ('tasks', 'runs'))
assert after['runs'] == before['runs'] + [RECORD] and after['activeRun'] is None
assert api['selection'](after) == dict(mode='implement', target='I08', findingIds=[])

records, commits = [], {}
for task_id in old['I07']['children']:
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
        value = load(p)
        assert value['exitCode'] == check['exitCode'], p
        assert check['kind'] == 'diagnostic' or check['exitCode'] == 0
        if 'outputSha256' in value:
            assert sha(value['output'].encode()) == value['outputSha256'], p
        assert all(p in child['changedPaths'] for p in check.get('supportPaths', []))
        checks.append(dict(kind=check['kind'], exitCode=check['exitCode'], evidence=p,
                           sha256=sha(Path(p).read_bytes())))
    records.append(dict(id=task_id, record=path, commit=commit,
                        reportSha256=sha(Path(path).read_bytes()), checks=checks))

management = Path(old['I07a']['implementationRecord']).with_suffix('')
inspection = Path(old['I07b']['implementationRecord']).with_suffix('')
latest = load(inspection / 'input-identities.json')
for p, identity in latest.items():
    data = Path(p).read_bytes()
    assert len(data) == identity['bytes'] and sha(data) == identity['sha256'], p
superseded, unchanged = [], []
for p, identity in load(management / 'artifact-identities.json').items():
    data = Path(p).read_bytes()
    if sha(data) != identity['sha256']:
        assert p in latest and sha(data) == latest[p]['sha256'], p
        superseded.append(p)
    else:
        unchanged.append(p)
assert git('diff', '--name-only', commits['I07a'], report['startCommit'],
           'server', 'protocol', 'core', 'client-storage', 'gradle', 'tests', 'scripts') == ''
assert git('diff', '--name-only', commits['I07b'], report['startCommit'],
           'androidApp', 'server', 'protocol', 'core', 'client-storage', 'e2e', 'gradle', 'tests', 'scripts') == ''
environment = load(inspection / 'environment-inputs.json')
for p, identity in environment['inputs'].items():
    assert sha(Path(p).read_bytes()) == identity, p
assert load('server/build/application-image.json')['imageId'] == environment['images']['server']

reused = {}
failed = []
for p, value in load(management / 'host-junit-initial.json').items():
    passed, red = cases(xml_bytes(value), require_green=False)
    reused[p] = passed
    failed.extend((p, name) for name in red)
assert sum(map(len, reused.values())) == 38 and len(failed) == 1
rechecks = {}
for p, value in load(management / 'host-junit-rechecks.json').items():
    rechecks[p] = cases(xml_bytes(value))[0]
assert failed[0][1] in rechecks[failed[0][0]]
assert sum(map(len, rechecks.values())) == 11
final_host = {p: cases(xml_bytes(v))[0] for p, v in load(inspection / 'host-junit.json').items()}
assert sum(map(len, final_host.values())) == 17
native_records = []
for directory, runner in (
    (management, 'runner-9ade44d7898042039fee9e8ef0bfba72'),
    (inspection, 'runner-adf52ab16065449486258bd34dc2052b'),
    (inspection, 'runner-cd463eab7d24454c9d5d5a11c248505e'),
):
    p = directory / (runner + '-result-0.xml')
    assert len(cases(p.read_bytes())[0]) == 1
    assert load(directory / (runner + '-session-closed.json'))['closed'] is True
    native_records.append(dict(path=str(p), sha256=sha(p.read_bytes())))
instrumentation = load(inspection / 'artwork-instrumentation.json')
assert instrumentation['exitCode'] == 0 and 'OK (2 tests)' in instrumentation['output']
assert 'PASS exact front/back scene-size bounds' in instrumentation['output']
accepted = load(management / 'accepted-state.json')
assert accepted['remainingSavedDeckNames'] == [f'Collection extra {i}' for i in range(1, 7)]
assert accepted['retainedDefinitions'] == 3 and accepted['remainingEntries'] == 18
for directory in (management, inspection):
    for p in directory.glob('cleanup-*.json'):
        assert load(p)['exitCode'] == 0, p
    for p in directory.glob('*.xml.json'):
        xml_bytes(load(p))

# Current native inspection includes Save/read/section/Back/restart on the same real
# unchanged management service. Its host tests also cover personal inspection during
# refresh, no writes and account cleanup. No uncovered changed seam needs a rerun.
actual = set(git('diff', '--name-only').splitlines()) | set(git('ls-files', '--others', '--exclude-standard').splitlines())
assert actual == set(report['changedPaths']), (actual, report['changedPaths'])
runner = object.__new__(api['Runner'])
runner.root = ROOT
runner.state_path = Path(STATE)
runner.state_relative = STATE
runner.role_paths(before, choice, report, actual, RECORD)
subprocess.run(['git', 'diff', '--check'], check=True)
assert Path('docs/agent/coverage/ledger.json').read_bytes() == subprocess.check_output(
    ['git', 'show', report['startCommit'] + ':docs/agent/coverage/ledger.json'])
for p in actual - {str(PREFIX / 'audit.json')}:
    data = Path(p).read_bytes()
    assert data.endswith(b'\n') and b'\r' not in data
    assert all(line.rstrip() == line for line in data.splitlines()), p
    if p.endswith('.json'):
        json.loads(data)
for p in (management / 'README.md', inspection / 'README.md'):
    text = p.read_text()
    assert text.count('```') % 2 == 0
    for target in re.findall(r'\]\(([^)]+)\)', text):
        if '://' not in target:
            assert (p.parent / target.split('#')[0]).is_file(), (p, target)

print(json.dumps(dict(
    result='PASS', startCommit=report['startCommit'], selected=choice,
    completed='I07 only', nextSelection=api['selection'](after), childRecords=records,
    inputAudit=dict(latestManifest=str(inspection / 'input-identities.json'),
                    latestManifestSha256=sha((inspection / 'input-identities.json').read_bytes()),
                    currentIdentitiesMatched=len(latest), unchangedManagementIdentities=len(unchanged),
                    supersededManagementInputs=superseded, environment=environment,
                    serverSharedStorageFixturesAndLocksUnchanged=True),
    reusedResults=dict(initialGreenCases=reused, initialDiagnosticCases=failed,
                       greenAffectedRechecks=rechecks, finalInspectionHostCases=final_host,
                       greenTargetedNativeRecords=native_records, artworkInstrumentationTests=2,
                       acceptedStateSha256=sha((management / 'accepted-state.json').read_bytes())),
    integration='Final inspection child already exercises normal Save/saved-deck definitions/sections/Back/restart against unchanged real management service; 17 affected host assertions cover shared mapping and management integration. Full-art repair has final native bounds/face/rotation/large-text and production-loader checks. Reuse original outputs without claiming wholly green original journeys or new executions; no missing materially changed seam.',
    handoff='Only selected I07 advances. State/transition/role/owned paths/JSON/docs links/fences/whitespace pass. Ledger, reviews, findings and release gates unchanged; no build/device/full-suite rerun.',
    remaining=report['remainingObligations']), indent=2))
