"""Read-only I09 closure audit. Reuses saved child runs, never reruns product checks."""
import hashlib
import json
from pathlib import Path
import runpy
import subprocess
import sys
import xml.etree.ElementTree as ET

START = 'e88167996080579a17bc6edc20db05897ccdb2c1'
PREFIX = Path('docs/agent/implementation-runs/20261022T193734Z-9b2c031f')
STATE = 'docs/agent/implementation-state.json'
RECORDS = [
    ('I09a', '20261022T163108Z-f7fc716d', '1787c7eefe62e5d0823e1f78084afb73caeb65f4'),
    ('I09b', '20261022T193210Z-618299c4', START),
    ('I09b1', '20261022T175129Z-7080c0f4', '8158e42e98404eda9357389129c210d5f12102c7'),
    ('I09b2', '20261022T181701Z-19a34527', '7fcfe73058e6090b7920f6339f84439495e2019f'),
]


def git(*args):
    return subprocess.check_output(['git', *args])


def load(path):
    return json.loads(Path(path).read_text())


def sha(data):
    return hashlib.sha256(data).hexdigest()


def green_xml(path):
    roots = [ET.fromstring(text) for text in set(load(path).values())]
    assert all(not any(list(root.iter(tag)) for tag in ('failure', 'error', 'skipped')) for root in roots)
    return sum(len(list(root.iter('testcase'))) for root in roots)


assert git('rev-parse', 'HEAD').decode().strip() == START
assert git('branch', '--show-current').decode().strip() == 'master'
assert not git('diff', '--cached', '--name-only').strip()
before = json.loads(git('show', START + ':' + STATE))
after = load(STATE)
report_path = str(PREFIX) + '.json'
report = load(report_path)
api = runpy.run_path('scripts/implementation-loop.py')
choice = dict(mode='implement', target='I09', findingIds=[])
assert before['mode'] == 'active' and before['authorization'] == 'authorized'
assert 'D-304' in Path(before['authorizationRecord']).read_text()
assert before['runner']['model'] == 'openai/gpt-6.1-sol#high'
assert api['selection'](before) == choice
api['validate_state'](after)
api['validate_transition'](before, after, choice, report)
old = {t['id']: t for t in before['tasks']}
new = {t['id']: t for t in after['tasks']}
assert new['I09']['status'] == 'implemented'
assert new['I09']['implementationRecord'] == report_path
assert new['I09']['children'] == ['I09a', 'I09b']
assert all(new[key] == value for key, value in old.items() if key != 'I09')
assert after['runs'] == before['runs'] + [report_path] and after['activeRun'] is None
assert all(after[key] == value for key, value in before.items() if key not in ('tasks', 'runs'))
assert api['selection'](after) == dict(mode='implement', target='I10', findingIds=[])
print('PASS exact baseline, D-304/model/priority, only I09 closes; I10 next; B3 review and release gates unchanged')

for task, run, commit in RECORDS:
    path = f'docs/agent/implementation-runs/{run}.json'
    child = load(path)
    assert old[task]['status'] == 'implemented' and old[task]['implementationRecord'] == path
    assert child['target'] == task and child['outcome'] == 'progress'
    subprocess.run(['git', 'merge-base', '--is-ancestor', commit, START], check=True)
    # Evidence/captures remain byte-for-byte saved; runtime changes in later children
    # are reconciled below, not misrepresented as unchanged I09a inputs.
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
            if 'capturedLog' in record:
                assert sha(record['capturedLog'].encode()) == record['sha256']
        print('REUSED_CHECK', check['kind'], check['exitCode'], sha(data), check['evidence'])

a = Path('docs/agent/implementation-runs/' + RECORDS[0][1])
b = Path('docs/agent/implementation-runs/' + RECORDS[3][1])
assert green_xml(a / 'lobby-junit.json') == 4
assert green_xml(a / 'client-junit.json') == 17
assert green_xml(b / 'host-junit.json') == 23
assert load(a / 'native-journey-qualified.json')['exitCode'] == 0
assert load(b / 'native-journey-visible-status-corrected.json')['exitCode'] == 0
for base in (a, b):
    for name in ('cleanup-first', 'cleanup-second', 'cleanup-environment'):
        assert load(base / (name + '.json'))['exitCode'] == 0
identities = load(b / 'input-identities.json')
for name, identity in identities.items():
    data = Path(name).read_bytes()
    assert sha(data) == identity['sha256'] and len(data) == identity['bytes'], name
print('PASS', len(identities), 'current final source/build/lock/APK identities; reused green child host/native checks and exact owned cleanup')

for name, identity in load(a / 'input-identities.json').items():
    if name in identities and identity != identities[name]:
        print('SUPERSEDED_I09a_INPUT', name, identity['sha256'], '->', identities[name]['sha256'])
print('INTEGRATION I09b1 current-schema LobbyHttpTest reruns all four account/create/privacy/migration tests; changes only current-attempt pointer and list/head joins')
print('INTEGRATION I09b2 reruns all five original LobbyControllerTest cases plus auth/queue integration on final wiring and incremental APK/lint/locks')
print('INTEGRATION I09b2 native journey UI-creates accounts/contact/named game, then declines/reinvites/accepts with interrupted transport, both orientations and restart')
observations = [load(b / ('observe-' + name + '.json')) for name in ('pending', 'declined', 'reinvited', 'accepted', 'restarted')]
for key in ('gameId', 'name', 'createdAt', 'round'):
    assert len({item[key] for item in observations}) == 1
assert [item['state'] for item in observations] == ['PENDING', 'DECLINED', 'PENDING', 'ACCEPTED', 'ACCEPTED']
assert [item['requests'] for item in observations] == [0, 1, 2, 3, 3]
assert observations[0]['invitationId'] == observations[1]['invitationId']
assert observations[2]['invitationId'] != observations[0]['invitationId']
assert observations[2]['invitationId'] == observations[3]['invitationId'] == observations[4]['invitationId']
print('PASS combined create/decision identity and original-outcome integration already exercised; no uncovered seam requires a repeated product/build/device check')
assert 'I34 correction/recheck' in (b / 'README.md').read_text()
print('RETAIN I34 recovered HTTP-error correction/recheck and migration-fixture reconciliation, not passes or optional polish')
print('RETAIN I10-I12 preparation/copies/Ready/start/cancel, P-072/T-669 and T-457/458/460; I13/I14/I26 gameplay/rebase/tree; I28/I31 rematch/T-681')
print('RETAIN independent B3 after I09-I12; I33-I36 final measurements/full suite/compatibility/complete UI comparison and external gates')

actual = set(git('diff', '--name-only').decode().splitlines()) | set(git('ls-files', '--others', '--exclude-standard').decode().splitlines())
assert actual == set(report['changedPaths']), (actual, report['changedPaths'])
runner = object.__new__(api['Runner'])
runner.root = Path.cwd()
runner.state_path = Path(STATE)
runner.state_relative = STATE
runner.role_paths(before, choice, report, actual, report_path)
sys.stdout.flush()
for path in actual:
    data = Path(path).read_bytes()
    assert b'\r' not in data and data.endswith(b'\n')
    assert all(line.rstrip() == line for line in data.splitlines())
    if path.endswith('.json'):
        json.loads(data)
subprocess.run(['git', 'diff', '--check'], check=True)
print('PASS state/transition/role/exact changed-path guards, JSON and whitespace; no runtime changes, review or release claim')
