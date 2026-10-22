"""Read-only I09b parent evidence/integration audit; no product checks rerun."""
from pathlib import Path
import hashlib
import json
import re
import runpy
import subprocess
import sys
import xml.etree.ElementTree as ET

PREFIX = Path('docs/agent/implementation-runs/20261022T193210Z-618299c4')
STATE = 'docs/agent/implementation-state.json'
START = '7fcfe73058e6090b7920f6339f84439495e2019f'
CHILDREN = [
    ('I09b1', '20261022T175129Z-7080c0f4', '8158e42e98404eda9357389129c210d5f12102c7'),
    ('I09b2', '20261022T181701Z-19a34527', START),
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
report_path = str(PREFIX) + '.json'
report = load(report_path)
api = runpy.run_path('scripts/implementation-loop.py')
choice = dict(mode='implement', target='I09b', findingIds=[])
assert before['authorization'] == 'authorized' and before['mode'] == 'active'
assert 'D-304' in Path(before['authorizationRecord']).read_text()
assert before['runner']['model'] == 'openai/gpt-6.1-sol#high'
assert api['selection'](before) == choice
api['validate_state'](after)
api['validate_transition'](before, after, choice, report)
old = {t['id']: t for t in before['tasks']}
new = {t['id']: t for t in after['tasks']}
assert new['I09b']['status'] == 'implemented'
assert new['I09b']['implementationRecord'] == report_path
assert new['I09b']['children'] == ['I09b1', 'I09b2']
assert all(new[k] == v for k, v in old.items() if k != 'I09b')
assert after['runs'] == before['runs'] + [report_path] and after['activeRun'] is None
assert all(after[k] == v for k, v in before.items() if k not in ('tasks', 'runs'))
assert api['selection'](after) == dict(mode='implement', target='I09', findingIds=[])
print('PASS D-304, exact baseline/model/priority, only I09b closure; I09 next; B3 review and gates unchanged')

for task, run, commit in CHILDREN:
    path = f'docs/agent/implementation-runs/{run}.json'
    child = load(path)
    assert old[task]['status'] == 'implemented' and old[task]['implementationRecord'] == path
    assert child['target'] == task and child['outcome'] == 'progress'
    subprocess.run(['git', 'merge-base', '--is-ancestor', commit, START], check=True)
    # Verify every saved child path, including diagnostics/captures, against its exact commit.
    # State alone subsequently advances; all runtime/test inputs remain byte-identical.
    for name in child['changedPaths']:
        if name == STATE:
            continue
        assert Path(name).read_bytes() == git('show', commit + ':' + name), name
    print('CHILD', task, commit, 'reportSha256=' + sha(Path(path).read_bytes()),
          'unchangedSavedPaths=' + str(len(child['changedPaths']) - 1))
    for check in child['checks']:
        data = Path(check['evidence']).read_bytes()
        assert data == git('show', commit + ':' + check['evidence'])
        assert check['kind'] == 'diagnostic' or check['exitCode'] == 0
        if check['evidence'].endswith('.json'):
            record = json.loads(data)
            assert record['exitCode'] == check['exitCode']
            if 'capturedLog' in record:
                assert sha(record['capturedLog'].encode()) == record['sha256']
        print('REUSED_CHECK', check['kind'], check['exitCode'], sha(data), check['evidence'])

server = Path('docs/agent/implementation-runs/' + CHILDREN[0][1])
logs = {name: load(server / (name + '.json'))['capturedLog'] for name in
        ('initial-tests', 'game-tests', 'socket-and-mail-tests', 'pipelined-socket-final')}
assert len(re.findall(r'^LobbyHttpTest > .* PASSED$', logs['initial-tests'], re.M)) == 4
assert len(re.findall(r'^GameDurabilityTest > .* PASSED$', logs['game-tests'], re.M)) == 5
assert 'actual socket resume replaces old connection' in logs['game-tests']
assert 'recover private original outcomes() PASSED' in logs['pipelined-socket-final']
assert 'corrected restart() PASSED' in logs['socket-and-mail-tests']
shared = [ET.fromstring(text) for text in {text for path, text in load(server / 'retained-junit.json').items() if 'protocol/' in path}]
assert sum(int(root.attrib['tests']) for root in shared) == 3
assert all(all(root.attrib[k] == '0' for k in ('failures', 'errors', 'skipped')) for root in shared)
print('PASS retained server/shared green subsets: 15 distinct cases; red commands remain diagnostic, not new executions')

client = Path('docs/agent/implementation-runs/' + CHILDREN[1][1])
identities = load(client / 'input-identities.json')
for path, identity in identities.items():
    data = Path(path).read_bytes()
    assert sha(data) == identity['sha256'] and len(data) == identity['bytes'], path
    print('CURRENT_INPUT', identity['sha256'], path)
roots = [ET.fromstring(text) for text in load(client / 'host-junit.json').values()]
assert sum(len(list(root.iter('testcase'))) for root in roots) == 23
assert all(not any(list(root.iter(tag)) for tag in ('failure', 'error', 'skipped')) for root in roots)
journey = load(client / 'native-journey-visible-status-corrected.json')
assert journey['exitCode'] == 0 and 'PASS one two-normal-APK' in journey['output']
observations = [load(client / ('observe-' + name + '.json')) for name in
                ('pending', 'declined', 'reinvited', 'accepted', 'restarted')]
for key in ('gameId', 'name', 'createdAt', 'round'):
    assert len({item[key] for item in observations}) == 1
assert [item['state'] for item in observations] == ['PENDING', 'DECLINED', 'PENDING', 'ACCEPTED', 'ACCEPTED']
assert [item['requests'] for item in observations] == [0, 1, 2, 3, 3]
assert observations[0]['invitationId'] == observations[1]['invitationId']
assert observations[2]['invitationId'] != observations[0]['invitationId']
assert observations[2]['invitationId'] == observations[3]['invitationId'] == observations[4]['invitationId']
outcomes = load(client / 'ordered-native-outcomes.json')['outcomes']
assert len(outcomes) == len({item['requestId'] for item in outcomes}) == 3
assert [item['ordinal'] for item in outcomes] == [1, 2, 3]
assert [item['command']['action']['type'] for item in outcomes] == ['declineInvitation', 'reinvite', 'acceptInvitation']
assert all(item['result']['outcome'] == 'accepted' for item in outcomes)
assert all(item['command']['scope']['gameId'] == observations[0]['gameId'] for item in outcomes)
for name in ('cleanup-first', 'cleanup-second', 'cleanup-environment'):
    assert load(client / (name + '.json'))['exitCode'] == 0
assert 'I34 correction/recheck' in (client / 'README.md').read_text()
print('PASS 119 exact final source/build/lock/APK/fixture identities, 23 host cases, native integrated journey and stable original outcomes')
print('REUSE two-normal-APK both-orientation/reconnect/restart evidence and owned cleanup; no device/build/fault matrix rerun')
print('DEFERRED I34 recovered HTTP error correction/recheck retained, not a passing assertion')

actual = set(git('diff', '--name-only').decode().splitlines()) | set(git('ls-files', '--others', '--exclude-standard').decode().splitlines())
assert actual == set(report['changedPaths']), (actual, report['changedPaths'])
runner = object.__new__(api['Runner'])
runner.root = Path.cwd(); runner.state_path = Path(STATE); runner.state_relative = STATE
runner.role_paths(before, choice, report, actual, report_path)
sys.stdout.flush()
for path in actual:
    data = Path(path).read_bytes()
    assert b'\r' not in data and data.endswith(b'\n')
    assert all(line.rstrip() == line for line in data.splitlines())
    if path.endswith('.json'):
        json.loads(data)
subprocess.run(['git', 'diff', '--check'], check=True)
print('PASS current-run state/transition/path guards, JSON/whitespace/exact changed paths; no runtime edits or missing integration seam')
print('REMAINING I09 parent, I10-I12 preparation/Ready/start/cancel, I13/I14/I26 gameplay/rebase/tree, I28/I31 rematch, independent B3 and I33-I36/release gates')
