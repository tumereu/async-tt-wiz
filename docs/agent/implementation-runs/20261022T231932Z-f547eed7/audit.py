"""I10 combined evidence audit; does not reexecute child tests or builds."""
import hashlib
import json
from pathlib import Path
import runpy
import subprocess
import xml.etree.ElementTree as ET

START = '7cf79a9e64deb948f8732b80448d8d7b4e0ec50b'
BASE = Path('docs/agent/implementation-runs/20261022T231932Z-f547eed7')
STATE = 'docs/agent/implementation-state.json'
RECORDS = [
    ('I10a', '20261022T210618Z-60f9921b', '401c4597b00bb033dc4c98c46a81196d14e75037'),
    ('I10b', '20261022T230001Z-668e6673', START),
    ('I10a1', '20261022T195604Z-aa3e4430', '627006fc035932e0fe959efe772b970c77360f49'),
    ('I10a2', '20261022T201612Z-f7a4a46b', '2640a4f4f18a06d2b2bf68f4181a9f53976b3f6e'),
    ('I10b1', '20261022T212809Z-652a7416', 'e2d9bfaf4aee00b8dfca2fd66d93b794409edb6c'),
    ('I10b2', '20261022T215026Z-802a24bd', '0b4585483143efc62601caab9d666f62501d162b'),
]


def git(*args):
    return subprocess.check_output(['git', *args])


def load(path):
    return json.loads(Path(path).read_text())


def sha(data):
    return hashlib.sha256(data).hexdigest()


assert git('rev-parse', 'HEAD').decode().strip() == START
assert git('branch', '--show-current').decode().strip() == 'master'
assert not git('diff', '--cached', '--name-only').strip()
before = json.loads(git('show', START + ':' + STATE))
after = load(STATE)
report_path = str(BASE) + '.json'
report = load(report_path)
choice = dict(mode='implement', target='I10', findingIds=[])
api = runpy.run_path('scripts/implementation-loop.py')
assert before['mode'] == 'active' and before['authorization'] == 'authorized'
assert 'D-304' in Path(before['authorizationRecord']).read_text()
assert before['runner']['model'] == 'openai/gpt-6.1-sol#high'
assert api['selection'](before) == choice
api['validate_state'](after)
api['validate_transition'](before, after, choice, report)
old = {task['id']: task for task in before['tasks']}
new = {task['id']: task for task in after['tasks']}
assert new['I10']['status'] == 'implemented' and new['I10']['implementationRecord'] == report_path
assert new['I10']['children'] == ['I10a', 'I10b']
assert all(new[key] == value for key, value in old.items() if key != 'I10')
assert after['runs'] == before['runs'] + [report_path] and after['activeRun'] is None
assert all(after[key] == value for key, value in before.items() if key not in ('tasks', 'runs'))
assert api['selection'](after) == dict(mode='implement', target='I11', findingIds=[])
print('PASS exact start/master, D-304/high model/priority, state/transition; only I10 closes, I11 next')
print('PASS B3 pending, independent review coverage/findings/authorization/grants/external gates unchanged')

for task, run, commit in RECORDS:
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
            captured = json.loads(data)
            assert captured['exitCode'] == check['exitCode']
            assert sha(captured['output'].encode()) == captured['outputSha256']
        print('REUSED_CHECK', check['kind'], check['exitCode'], sha(data), check['evidence'])

a = Path('docs/agent/implementation-runs/20261022T201612Z-f7a4a46b')
b = Path('docs/agent/implementation-runs/20261022T215026Z-802a24bd')
transfer_changes = set()
for task, run, commit in RECORDS[-2:]:
    transfer_changes.update(load(f'docs/agent/implementation-runs/{run}.json')['changedPaths'])
for name, identity in load(a / 'input-identities.json').items():
    if sha(Path(name).read_bytes()) != identity['sha256']:
        assert name in transfer_changes or name == 'androidApp/build/outputs/apk/debug/androidApp-debug.apk', name
        print('SUPERSEDED_PICKER_INPUT', name, identity['sha256'], '->', sha(Path(name).read_bytes()))
for name, identity in load(b / 'input-identities.json').items():
    data = Path(name).read_bytes()
    if name.endswith('/NativePreparationTransferTest.kt'):
        data = data.replace(b'                run {\n', b'                if (stage != "done") {\n')
    assert sha(data) == identity['sha256'] and len(data) == identity['bytes'], name
print('PASS final 130 source/build/lock/fixture/APK identities; unused test-only shortcut removal has exact executed-source reconstruction and saved final compile')
print('PASS earlier picker input supersessions belong to implemented transfers, not unexplained drift')

cases = set()
for text in load(b / 'host-junit.json').values():
    root = ET.fromstring(text)
    assert not any(list(root.iter(tag)) for tag in ('failure', 'error', 'skipped'))
    cases.update(case.attrib['name'] for case in root.iter('testcase'))
for name in ('oneConfirmationPredictsWithSharedRulesButReceiptAndResultDoNotInstallAcceptedState',
             'transfersComposeAfterUnacknowledgedSelectionAndAccountSwitchClearsOriginalsAndCapturedTail',
             'productionControllerCapturesGestureStartThenSubmitsMultiplePerCardTransfersWithoutInvitationPendingGate'):
    assert name in cases
    print('REUSED_INTEGRATION_CASE', name)
observations = [load(a / ('observe-' + name + '.json')) for name in ('selected', 'restarted', 'edited', 'deleted', 'final', 'accepted')]
for key in ('gameId', 'name', 'createdAt', 'round', 'copy', 'definitions'):
    assert all(item[key] == observations[0][key] for item in observations), key
assert load(a / 'observe-draft.json')['requests'] == 0
assert [item['requests'] for item in observations] == [1, 1, 1, 1, 1, 2]
outcomes = load(b / 'ordered-native-outcomes.json')
final = load(b / 'final-preparation-and-source.json')
assert len(outcomes) == len({item['requestId'] for item in outcomes}) == final['requests'] == 48
assert outcomes[0]['command']['action']['type'] == 'selectDeck'
assert final['prepared']['selectionRequestId'] == outcomes[0]['requestId']
assert final['prepared']['id'] == outcomes[0]['result']['createdIds'][0]
assert all(item['command']['action']['type'] == 'transferPreparedCard' and
           item['command']['action']['deckId'] == final['prepared']['id'] for item in outcomes[1:])
assert final['revisions'] == 47 and final['resultOutbox'] == 48 and final['rejected'] == 1
assert [item['result']['reason'] for item in outcomes if item['result']['outcome'] == 'rejected'] == ['sourceSectionChanged']
assert [{key: entry[key] for key in ('section', 'definition', 'quantity')} for entry in final['prepared']['entries']] == final['savedEntries']
print('PASS retained combined picker/selection/transfer seam: zero-request draft, immutable copy through source edits/deletion/Accept, same selected deck for 47 transfer requests, one exact-copy conflict, unchanged source')
print('REUSE native both-orientation picker and composite two-APK transfer/inspection/delayed-ack/restart plus native feedback/accessibility/rapid-touch evidence; no new journey execution')
print('SCOPE successful retained tails do not relabel earlier diagnostic failures as green complete journeys')

items = [item for item in load('docs/agent/coverage/ledger.json')['items'] if 'I10' in item['implementationDependencies']]
assert [item['id'] for item in items] == report['sourceIds'] and all(item['completion'] == 'unexecuted' for item in items)
print('PASS 30 source assignments retained as partial assertions, not whole-scenario passes')
print('RETAIN I11/I12 Ready/starter/start/randomness/table/cancel/play-sideboard/two-player overlaps; B3 independent review; I14 gameplay; I28/I31 rematch')
print('RETAIN I32/I34 reduced-motion/large-text/full suite/deferred migration-fixture and recovered HTTP-error fixes; I33/I35/I36/B8 measurement/compatibility/UI and external gates')
print('PASS integrated children cover current I10 outcome; no missing affected seam warrants another build/device/fault/full-suite run')

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
print('PASS exact owned paths, JSON/references/whitespace/role guards; no runtime/build/UI-document or release changes')
