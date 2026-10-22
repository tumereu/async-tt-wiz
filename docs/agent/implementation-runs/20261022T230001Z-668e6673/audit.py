"""Read-only I10b parent closure; reuse child executions without rerunning them."""
import hashlib
import json
from pathlib import Path
import runpy
import subprocess
import xml.etree.ElementTree as ET

START = '0b4585483143efc62601caab9d666f62501d162b'
BASE = Path('docs/agent/implementation-runs/20261022T230001Z-668e6673')
STATE = 'docs/agent/implementation-state.json'
CHILDREN = [
    ('I10b1', '20261022T212809Z-652a7416', 'e2d9bfaf4aee00b8dfca2fd66d93b794409edb6c'),
    ('I10b2', '20261022T215026Z-802a24bd', START),
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
choice = dict(mode='implement', target='I10b', findingIds=[])
api = runpy.run_path('scripts/implementation-loop.py')
assert before['mode'] == 'active' and before['authorization'] == 'authorized'
assert 'D-304' in Path(before['authorizationRecord']).read_text()
assert before['runner']['model'] == 'openai/gpt-6.1-sol#high'
assert api['selection'](before) == choice
api['validate_state'](after)
api['validate_transition'](before, after, choice, report)
old = {task['id']: task for task in before['tasks']}
new = {task['id']: task for task in after['tasks']}
assert new['I10b']['status'] == 'implemented' and new['I10b']['implementationRecord'] == report_path
assert new['I10b']['children'] == ['I10b1', 'I10b2']
assert all(new[key] == value for key, value in old.items() if key != 'I10b')
assert after['runs'] == before['runs'] + [report_path] and after['activeRun'] is None
assert all(after[key] == value for key, value in before.items() if key not in ('tasks', 'runs'))
assert api['selection'](after) == dict(mode='implement', target='I10', findingIds=[])
print('PASS exact baseline/master, authorization/model/priority, state/transition; only I10b closes, I10 next')
print('PASS I10 parent/B3 independent review/findings/grants/external gates unchanged')

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

a, b = [Path('docs/agent/implementation-runs/' + item[1]) for item in CHILDREN]
cases = {}
failures = set()
for phase in ('initial', 'rollback-recheck', 'controller-recheck'):
    for item in load(a / (phase + '-junit.json')):
        assert sha(item['xml'].encode()) == item['sha256']
        root = ET.fromstring(item['xml'])
        assert not list(root.iter('skipped')) and not list(root.iter('error'))
        for case in root.iter('testcase'):
            key = case.attrib['classname'], case.attrib['name']
            failed = case.find('failure') is not None
            if failed:
                assert phase == 'initial' and 'NOT_OWNER' in case.find('failure').attrib['message']
                failures.add(key)
            cases[key] = phase, not failed
assert len(cases) == 41 and len(failures) == 1 and all(passed for phase, passed in cases.values())
assert all(cases[key] == ('rollback-recheck', True) for key in failures)
print('PASS reuse 41 distinct final I10b1 cases: 14 shared, 3 real PostgreSQL/socket, 24 client; initial red remains diagnostic')
native_cases = set()
for text in load(b / 'host-junit.json').values():
    native_cases.update(green(text))
assert len(native_cases) == 20
for name in ('native-retained-observer-cwd', 'native-interaction-navigation', 'native-rapid-interaction', 'final-e2e-compile'):
    assert load(b / (name + '.json'))['exitCode'] == 0
for prefix in ('runner-0e805d07d04247858e0189ae7aa21d92',):
    assert len(green((b / (prefix + '-result-0.xml')).read_text())) == 1
for name in ('cleanup-first', 'cleanup-peer'):
    cleaned = json.loads(load(b / (name + '.json'))['output'])
    assert cleaned['state']['cleaned'] and cleaned['listeners'] == [] and all(x is None for x in cleaned['currentProcesses'])
assert json.loads(load(b / 'cleanup-environment.json')['output'])['cleaned']
print('PASS reuse 20 affected host cases, composite both-orientation two-normal-APK journey, native accessibility/animation/rapid-touch checks and exact cleanup')
print('SCOPE setup/portrait tap and drag assertions ran in retained diagnostics; passing tail continues retained state, not a new full journey')

native = load(str(b) + '.json')
for name, identity in load(b / 'input-identities.json').items():
    data = Path(name).read_bytes()
    if name.endswith('/NativePreparationTransferTest.kt'):
        executed = data.replace(b'                run {\n', b'                if (stage != "done") {\n')
        assert sha(executed) == identity['sha256'] and len(executed) == identity['bytes']
        print('PASS final test-only unused done-shortcut deletion reconstructs exact executed-stage input; final compile retained')
    else:
        assert sha(data) == identity['sha256'] and len(data) == identity['bytes'], name
print('PASS 130 final source/build/lock/fixture/APK identities matched, with explicit executed E2E source provenance')
for name, identity in load(a / 'input-identities.json').items():
    if sha(Path(name).read_bytes()) != identity['sha256']:
        assert name in native['changedPaths'] or name == 'androidApp/build/outputs/apk/debug/androidApp-debug.apk', name
        print('SUPERSEDED_I10b1_INPUT', name, identity['sha256'], '->', sha(Path(name).read_bytes()))
print('INTEGRATION I10b2 changes native presentation/controller only; same GameSession, shared rules/wire, server processor and V8 persistence remain unchanged')

outcomes = load(b / 'ordered-native-outcomes.json')
final = load(b / 'final-preparation-and-source.json')
assert len(outcomes) == len({item['requestId'] for item in outcomes}) == final['requests'] == 48
assert [item['ordinal'] for item in outcomes] == list(range(1, 49))
accepted = [item for item in outcomes if item['result']['outcome'] == 'accepted']
rejected = [item for item in outcomes if item['result']['outcome'] == 'rejected']
assert len(accepted) == final['revisions'] == 47 and len(rejected) == final['rejected'] == 1
assert final['resultOutbox'] == 48
assert [int(item['result']['commitRevision']) for item in accepted] == list(range(1, 48))
assert outcomes[0]['command']['action']['type'] == 'selectDeck'
assert all(item['command']['action']['type'] == 'transferPreparedCard' for item in outcomes[1:])
assert rejected[0]['result']['reason'] == 'sourceSectionChanged'
prepared = final['prepared']
assert prepared['selectionRequestId'] == outcomes[0]['requestId']
assert prepared['id'] == outcomes[0]['result']['createdIds'][0]
assert all(item['command']['action']['deckId'] == prepared['id'] for item in outcomes[1:])
entries = [{key: entry[key] for key in ('section', 'definition', 'quantity')} for entry in prepared['entries']]
assert entries == final['savedEntries']
assert [sum(entry['quantity'] for entry in entries if entry['section'] == section) for section in ('MAINBOARD', 'SIDEBOARD')] == [12, 3]
assert load(b / 'observe-partial.json')['rejectedTransfers'] == 1
assert load(b / 'observe-final.json')['prepared'] == prepared
print('PASS retained exact copy/source/round original outcomes: 48 IDs, one source conflict without substitution, 47 revisions, 48 results, final 12/3 source isolation')
print('INTEGRATION normal APK crosses gesture capture -> controller -> multi-in-flight shared prediction -> ordered server -> accepted reconciliation; fixed slot/row transitions never gate requests')

items = [item for item in load('docs/agent/coverage/ledger.json')['items'] if 'I10' in item['implementationDependencies']]
assert [item['id'] for item in items] == report['sourceIds'] and all(item['completion'] == 'unexecuted' for item in items)
assert Path('docs/agent/coverage/ledger.json').read_bytes() == git('show', START + ':docs/agent/coverage/ledger.json')
print('PASS 30 I10 source assignments unchanged; partial assertions are not whole-scenario passes')
print('RETAIN I11/I12 Ready/start/randomness/table/cancel/play-sideboard/two-player overlaps; I14 gameplay; I28/I31 rematch; B3 independent review')
print('RETAIN I32/I34 reduced-motion/large-text/full suite/deferred/migration fixtures/recovered HTTP error; I33/I35/I36/B8 measurements/compatibility/UI comparison and external gates')
print('PASS no missing affected integration seam; no new product test/build/device/fault/full-suite run needed')

actual = set(git('diff', '--name-only').decode().splitlines()) | set(git('ls-files', '--others', '--exclude-standard').decode().splitlines())
assert actual == set(report['changedPaths']), (actual, report['changedPaths'])
runner = object.__new__(api['Runner'])
runner.root = Path.cwd()
runner.state_path = Path(STATE)
runner.state_relative = STATE
runner.role_paths(before, choice, report, actual, report_path)
for name in actual - {str(BASE / 'audit.txt')}:
    data = Path(name).read_bytes()
    assert b'\r' not in data and data.endswith(b'\n') and all(line.rstrip() == line for line in data.splitlines()), name
    if name.endswith('.json'):
        json.loads(data)
subprocess.run(['git', 'diff', '--check'], check=True)
print('PASS exact owned paths, JSON/references/whitespace and role guards; no runtime/build/UI-document/review/release changes')
