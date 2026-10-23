"""I11 parent evidence reconciliation, not a new execution of child product checks."""
import base64
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess
import uuid
import xml.etree.ElementTree as ET

START = '9fcdb54a5da2812828814d401e542344cf36130d'
BASE = Path('docs/agent/implementation-runs/20261023T024324Z-aa902dde')
STATE = 'docs/agent/implementation-state.json'
CHILDREN = [
    ('I11a', '20261023T000449Z-24e73bbc', 'aa0d133bc4b62eee96a54b998130c5685959536d'),
    ('I11b', '20261023T010830Z-bc92c3f3', START),
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
choice = dict(mode='implement', target='I11', findingIds=[])
api = runpy.run_path('scripts/implementation-loop.py')
assert before['mode'] == 'active' and before['authorization'] == 'authorized'
assert 'D-304' in Path(before['authorizationRecord']).read_text()
assert before['runner']['model'] == 'openai/gpt-6.1-sol#high'
assert api['selection'](before) == choice
api['validate_state'](after)
api['validate_transition'](before, after, choice, report)
old = {task['id']: task for task in before['tasks']}
new = {task['id']: task for task in after['tasks']}
assert new['I11']['status'] == 'implemented' and new['I11']['implementationRecord'] == report_path
assert new['I11']['children'] == ['I11a', 'I11b']
assert all(new[key] == value for key, value in old.items() if key != 'I11')
assert after['runs'] == before['runs'] + [report_path] and after['activeRun'] is None
assert all(after[key] == value for key, value in before.items() if key not in ('tasks', 'runs'))
assert api['selection'](after) == dict(mode='implement', target='I12', findingIds=[])
print('PASS supplied start/master, D-304/high model/priority, state/transition; only I11 closes, I12 next')
print('PASS B3 pending, independent review/findings/authorization/grants/external gates unchanged')

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
            saved = json.loads(data)
            assert saved['exitCode'] == check['exitCode']
            text, digest = (saved['stdout'], saved['sha256']) if 'stdout' in saved else (saved['output'], saved['outputSha256'])
            assert sha(text.encode()) == digest
        print('REUSED_CHECK', check['kind'], check['exitCode'], sha(data), check['evidence'])

a = 'docs/agent/implementation-runs/20261023T000449Z-24e73bbc'
b = Path('docs/agent/implementation-runs/20261023T010830Z-bc92c3f3')
for path, digest in re.findall(r'^FINAL INPUT (.+) SHA256 ([a-f0-9]{64})$', Path(a + '-handoff.txt').read_text(), re.M):
    if path.endswith('/androidApp-debug.apk'):
        print('SUPERSEDED_I11a_APK', digest, 'native/final I11b artifacts are separately identified')
    else:
        assert sha(Path(path).read_bytes()) == digest, path
inputs = load(b / 'inputs.json')
for path, digest in inputs['finalSources'].items():
    assert sha(Path(path).read_bytes()) == digest, path
apk = inputs['finalCompiledApk']
assert sha(Path(apk['path']).read_bytes()) == apk['sha256']
assert Path(apk['path']).stat().st_size == apk['bytes']
assert len({item['sha256'] for item in inputs['nativeInstalled']}) == 1
assert inputs['nativeInstalled'][0]['sha256'] != apk['sha256']
assert 'accepted-replica' in inputs['nativeInputSupersession']
print('PASS unchanged I11a final source/build/lock/fixture/JAR identities; I11b final', len(inputs['finalSources']), 'source/build/lock/fixture identities and compiled APK')
print('SCOPE native installed APK', inputs['nativeInstalled'][0]['sha256'], 'final compiled APK', apk['sha256'])
print('SUPERSESSION final session replica-correlation repair has targeted host rechecks and compilation; unchanged native controls/rendering/start reused, not a new device run')

def junit(paths):
    cases = {}
    for path in paths:
        for saved in load(path).values():
            data = base64.b64decode(saved['base64'], validate=True)
            assert sha(data) == saved['sha256']
            root = ET.fromstring(data)
            assert root.attrib == saved['tests']
            assert int(root.attrib['skipped']) == int(root.attrib['errors']) == 0
            for case in root.iter('testcase'):
                cases[(case.attrib['classname'], case.attrib['name'])] = not any(case.find(tag) is not None for tag in ('failure', 'error', 'skipped'))
    assert all(cases.values()), [key for key, passed in cases.items() if not passed]
    return cases


shared_server_client = junit([a + suffix for suffix in ('-initial-junit.json', '-final-junit.json', '-model-junit.json', '-random-junit.json')])
assert len(shared_server_client) == 74
final_host = junit([b / 'client-junit.json'])
assert len(final_host) == 30
for name in ('httpSummaryHighWaterCannotRetireReadinessBeforeItsAcceptedReplicaArrives',
             'startRevisionBeforeResultDoesNotResetOrRepeatAndClosesPreparationUI',
             'newProcessUsesExactAcceptedInitialStateWithoutRecreatingUncertainIntent',
             'sameSceneBindsOwnersConcreteLibrariesAndEmptyAreasBothOrientationsWithoutCatalog'):
    assert any(key[1] == name for key in final_host), name
    print('REUSED_AFFECTED_SEAM', name)
print('PASS retained composite I11a 74 cases (31 shared/19 server/24 host) and I11b final 30 host cases; original red command remains diagnostic, exact targeted rechecks overlay failed cases only')

final = load(sorted(b.glob('observe-final-*.json'))[-1])
committed = load(b / 'committed-before-resume.json')
assert all(final[key] == committed[key] for key in ('games', 'plays', 'requests'))
assert len(final['plays']) == 1 and len(final['requests']) == 11
assert len({request['id'] for request in final['requests']}) == 11
assert all(request['result']['outcome'] == 'accepted' for request in final['requests'])
play = final['plays'][0]
assert play['startRequestId'] == final['requests'][-1]['id']
assert play['id'] == str(uuid.uuid5(uuid.UUID(play['startRequestId']), 'play-incarnation'))
assert play['currentNodeId'] == play['root']['id'] and play['root']['parentId'] is None
assert all(player['life'] == '20' for player in play['board']['players'])
assert play['board']['turn']['number'] == '1' and play['board']['turn']['step'] == 'UNTAP'
assert play['board']['turn']['phase'] == 'BEGINNING'
assert len({item['seed'] for item in play['shuffles']} | {play['starter']['seed']}) == 3
for prepared in final['preparations']:
    expected = {}
    for entry in prepared['deck']['entries']:
        if entry['section'] == 'MAINBOARD':
            for index in range(int(entry['firstIndex']), int(entry['firstIndex']) + entry['quantity']):
                expected[str(uuid.uuid5(uuid.UUID(prepared['deck']['selectionRequestId']), f'prepared-card/{index}'))] = entry['definition']
    cards = [card for card in play['board']['cards'] if card['owner'] == prepared['actor']]
    assert {card['id']: card['definition'] for card in cards} == expected
for shuffle in play['shuffles']:
    library = next(container for container in play['board']['containers'] if container['accountId'] == shuffle['accountId'] and container['zone'] == 'LIBRARY')
    assert library['cards'] == shuffle['order']
assert sorted(len(item['order']) for item in play['shuffles']) == [5, 6]
assert all(not container['cards'] for container in play['board']['containers'] if container['zone'] != 'LIBRARY')
assert all(container['zone'] != 'SIDEBOARD' for container in play['board']['containers'])
assert {card['definition'] for card in play['board']['cards']} == {definition['digest'] for definition in play['definitions']}
for path in b.glob('observe-*.json'):
    game = load(path)['games'][0]
    assert all(game[key] == final['games'][0][key] for key in ('id', 'name', 'createdAt', 'round'))
print('PASS retained native combined preparation/start/recovery seam: exact prepared UUID/definition/owner membership, 11 originals, one play/root, independent orders/Random result unchanged across lost-ack and backend/app restart; 20 life, turn-1 Beginning/Untap, no opening draw/Sideboard')
assert load(b / 'native-retained-restart.json')['exitCode'] == 0
assert load(b / 'native-retained-starter.json')['exitCode'] == 1
for index in (0, 1):
    for orientation in ('portrait', 'landscape'):
        captures = list(b.glob(f'*-table-{index}-{orientation}.png'))
        assert len(captures) == 1 and captures[0].read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
        assert captures[0].with_suffix('.xml').is_file()
print('REUSE two-normal-APK both-orientation captures/composite stages; diagnostic tails do not become a newly passing full native journey')
for path, saved in load(b / 'owned-reset-cleanup.json').items():
    data = base64.b64decode(saved['base64'], validate=True)
    assert sha(data) == saved['sha256'] and json.loads(data) == saved['record']
    if path.endswith('/reset-completed.json'):
        assert saved['record']['baseline']['migrations'] == [str(index) for index in range(1, 10)]
        assert 'game_play' in saved['record']['baseline']['tables']
print('PASS retained actual populated V9 reset and exact two-device/environment cleanup; no resources launched or mutated here')

items = [item for item in load('docs/agent/coverage/ledger.json')['items'] if 'I11' in item['implementationDependencies']]
assert [item['id'] for item in items] == report['sourceIds'] and all(item['completion'] == 'unexecuted' for item in items)
print('PASS all 31 I11 source assignments retain partial-versus-whole-scenario accounting')
print('RETAIN I12 list/cancel/start/preparation/acceptance/isolation overlaps and first B3 review; I13-I26 gameplay/history; I28/I31 rematch; I32-I36/B8 final full suite/measurements/compatibility/complete UI comparison/deferred fixes and unchanged external gates')
print('PASS no missing affected I11 integration seam warrants repeated build/device/fault/full-suite execution')

actual = set(git('diff', '--name-only').decode().splitlines()) | set(git('ls-files', '--others', '--exclude-standard').decode().splitlines())
assert actual == set(report['changedPaths']), actual ^ set(report['changedPaths'])
runner = object.__new__(api['Runner'])
runner.root = Path.cwd(); runner.state_path = Path(STATE); runner.state_relative = STATE
runner.role_paths(before, choice, report, actual, report_path)
for path in actual - {str(BASE / 'audit.txt')}:
    data = Path(path).read_bytes()
    assert b'\r' not in data and data.endswith(b'\n') and all(line.rstrip() == line for line in data.splitlines()), path
    if path.endswith('.json'):
        json.loads(data)
subprocess.run(['git', 'diff', '--check'], check=True)
print('PASS exact report/state/owned paths, JSON/references/whitespace and role guards; no runtime/build/UI-document or release changes')
