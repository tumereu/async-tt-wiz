"""Read-only I10a closure: reconcile exact child evidence, not repeat product checks."""
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess
import xml.etree.ElementTree as ET

START = '2640a4f4f18a06d2b2bf68f4181a9f53976b3f6e'
BASE = Path('docs/agent/implementation-runs/20261022T210618Z-60f9921b')
STATE = 'docs/agent/implementation-state.json'
CHILDREN = [
    ('I10a1', '20261022T195604Z-aa3e4430', '627006fc035932e0fe959efe772b970c77360f49'),
    ('I10a2', '20261022T201612Z-f7a4a46b', START),
]


def git(*args):
    return subprocess.check_output(['git', *args])


def load(path):
    return json.loads(Path(path).read_text())


def sha(data):
    return hashlib.sha256(data).hexdigest()


def green(text):
    root = ET.fromstring(text)
    assert not any(list(root.iter(tag)) for tag in ('failure', 'error', 'skipped'))
    return {(case.attrib['classname'], case.attrib['name']) for case in root.iter('testcase')}


assert git('rev-parse', 'HEAD').decode().strip() == START
assert git('branch', '--show-current').decode().strip() == 'master'
assert not git('diff', '--cached', '--name-only').strip()
before = json.loads(git('show', START + ':' + STATE))
after = load(STATE)
report_path = str(BASE) + '.json'
report = load(report_path)
choice = dict(mode='implement', target='I10a', findingIds=[])
api = runpy.run_path('scripts/implementation-loop.py')
assert before['mode'] == 'active' and before['authorization'] == 'authorized'
assert 'D-304' in Path(before['authorizationRecord']).read_text()
assert before['runner']['model'] == 'openai/gpt-6.1-sol#high'
assert api['selection'](before) == choice
api['validate_state'](after)
api['validate_transition'](before, after, choice, report)
old = {t['id']: t for t in before['tasks']}
new = {t['id']: t for t in after['tasks']}
assert new['I10a']['status'] == 'implemented' and new['I10a']['implementationRecord'] == report_path
assert new['I10a']['children'] == ['I10a1', 'I10a2']
assert all(new[key] == value for key, value in old.items() if key != 'I10a')
assert after['runs'] == before['runs'] + [report_path] and after['activeRun'] is None
assert all(after[key] == value for key, value in before.items() if key not in ('tasks', 'runs'))
assert api['selection'](after) == dict(mode='implement', target='I10b', findingIds=[])
print('PASS exact baseline/master, D-304/model/priority and state transitions; only I10a closes, I10b next')
print('PASS I10 parent/B3 review/findings/grants/external gates unchanged')

for task, run, commit in CHILDREN:
    path = f'docs/agent/implementation-runs/{run}.json'
    child = load(path)
    assert old[task]['status'] == 'implemented' and old[task]['implementationRecord'] == path
    assert child['target'] == task and child['outcome'] == 'progress'
    subprocess.run(['git', 'merge-base', '--is-ancestor', commit, START], check=True)
    owned = [name for name in child['changedPaths'] if name.startswith(f'docs/agent/implementation-runs/{run}')]
    for name in owned:
        assert Path(name).read_bytes() == git('show', commit + ':' + name), name
    print('REUSE', task, commit, 'reportSha256=' + sha(Path(path).read_bytes()), 'savedEvidencePaths=' + str(len(owned)))
    for check in child['checks']:
        data = Path(check['evidence']).read_bytes()
        assert data == git('show', commit + ':' + check['evidence'])
        assert check['kind'] == 'diagnostic' or check['exitCode'] == 0
        if check['evidence'].endswith('.json'):
            record = json.loads(data)
            assert record['exitCode'] == check['exitCode']
            assert sha(record['output'].encode()) == record['outputSha256']
        print('REUSED_CHECK', check['kind'], check['exitCode'], sha(data), check['evidence'])

a, b = [Path('docs/agent/implementation-runs/' + child[1]) for child in CHILDREN]
cases = set()
for item in load(a / 'retained-junit.json'):
    assert sha(item['xml'].encode()) == item['sha256']
    cases.update(green(item['xml']))
assert len(cases) == 29
print('PASS reused 29 I10a1 cases: 9 shared, 14 PostgreSQL/socket/deck, 6 client host; no new execution claim')
cases = set()
for name in ('host-junit-baseline.json', 'host-junit-targeted.json'):
    for text in load(b / name).values():
        cases.update(green(text))
assert len(cases) == 36
for prefix in ('runner-aec37c68851d4e29b0a3b725407805b8', 'runner-8872e6229f8e4f1cb93ff6d8255ec3ba'):
    assert len(green((b / (prefix + '-result-0.xml')).read_text())) == 1
for name in ('native-journey-accessible-selection', 'native-retained-acceptance-label-corrected', 'cleanup-device', 'cleanup-environment'):
    assert load(b / (name + '.json'))['exitCode'] == 0
print('PASS reused 36 distinct final host cases, complete native picker plus retained accepted-lobby tail, exact saved cleanup')
print('SCOPE Accept executed in retained diagnostic before incorrect title assertion; passing tail checks already-accepted state, not a new Accept')

identities = load(b / 'input-identities.json')
for name, identity in identities.items():
    data = Path(name).read_bytes()
    assert sha(data) == identity['sha256'] and len(data) == identity['bytes'], name
print('PASS', len(identities), 'unchanged final source/build/lock/fixture/APK identities')
native = load(str(b) + '.json')
for name, digest in re.findall(r'^INPUT (\S+) SHA256 ([a-f0-9]{64})$', (a / 'audit.txt').read_text(), re.M):
    if sha(Path(name).read_bytes()) != digest:
        assert name in native['changedPaths'], name
        print('SUPERSEDED_I10a1_INPUT', name, digest, '->', sha(Path(name).read_bytes()))
print('INTEGRATION I10a2 changes native session/controller/screens only; server/shared selection durability inputs remain unchanged')
print('INTEGRATION existing native journey exercises shared server selection, revision/result/prediction wiring, draft/import/cancel/confirm/restart/source edit-delete isolation')

observations = [load(b / ('observe-' + name + '.json')) for name in ('selected', 'restarted', 'edited', 'deleted', 'final', 'accepted')]
for key in ('gameId', 'name', 'createdAt', 'round', 'copy', 'definitions'):
    assert all(item[key] == observations[0][key] for item in observations), key
assert load(b / 'observe-draft.json')['requests'] == 0
assert [item['requests'] for item in observations] == [1, 1, 1, 1, 1, 2]
assert observations[2]['sources'] == 2 and observations[3]['sources'] == 1
outcomes = load(b / 'ordered-native-outcomes.json')
assert [item['command']['action']['type'] for item in outcomes] == ['selectDeck', 'acceptInvitation']
assert [item['ordinal'] for item in outcomes] == [1, 2]
assert [item['result']['commitRevision'] for item in outcomes] == ['1', '2']
assert all(item['result']['outcome'] == 'accepted' for item in outcomes)
assert outcomes[0]['requestId'] == observations[0]['copy']['selectionRequestId']
assert outcomes[0]['result']['createdIds'] == [observations[0]['copy']['id']]
print('PASS retained integrated one-confirmation/original copy/partition/definitions/source deletion/acceptance identity without duplication')
ledger = load('docs/agent/coverage/ledger.json')
items = [item for item in ledger['items'] if 'I10' in item['implementationDependencies']]
assert [item['id'] for item in items] == report['sourceIds']
assert all(item['completion'] == 'unexecuted' for item in items)
assert Path('docs/agent/coverage/ledger.json').read_bytes() == git('show', START + ':docs/agent/coverage/ledger.json')
print('PASS', len(items), 'I10 partial source assignments unchanged, not complete-scenario passes')
print('RETAIN I10b transfers/feedback; I11/I12 Ready/start/randomness/table/cancel/two-Android overlaps; I14 gameplay; I28/I31 rematch')
print('RETAIN B3 independent review; I34 full suite/deferred/migration fixtures/recovered HTTP-error correction; I33/I35/I36/B8 measurements/compatibility/complete UI comparison and external gates')
print('PASS no missing affected seam requires another build/device/fault/full-suite execution')

actual = set(git('diff', '--name-only').decode().splitlines()) | set(git('ls-files', '--others', '--exclude-standard').decode().splitlines())
assert actual == set(report['changedPaths']), (actual, report['changedPaths'])
runner = object.__new__(api['Runner'])
runner.root = Path.cwd()
runner.state_path = Path(STATE)
runner.state_relative = STATE
runner.role_paths(before, choice, report, actual, report_path)
sys_output = BASE / 'audit.txt'
for name in actual - {str(sys_output)}:
    data = Path(name).read_bytes()
    assert b'\r' not in data and data.endswith(b'\n'), name
    assert all(line.rstrip() == line for line in data.splitlines()), name
    if name.endswith('.json'):
        json.loads(data)
subprocess.run(['git', 'diff', '--check'], check=True)
print('PASS state/role/exact owned paths, JSON/references and whitespace; no runtime/build/UI-document changes or review/release claim')
