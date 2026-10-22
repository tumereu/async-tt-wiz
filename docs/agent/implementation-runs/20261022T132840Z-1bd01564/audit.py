"""I06b's read-only child-evidence/input/handoff audit; no product reruns."""
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


def digest(data):
    return hashlib.sha256(data).hexdigest()


def load(path):
    return json.loads(Path(path).read_text())


def green_xml(data):
    cases = list(ET.fromstring(data).iter('testcase'))
    assert cases and not any(list(c.iter(t)) for c in cases for t in ('failure', 'error', 'skipped'))
    return len(cases)


report = load(RECORD)
before = json.loads(git('show', report['startCommit'] + ':' + STATE))
after = load(STATE)
api = runpy.run_path('scripts/implementation-loop.py')
choice = dict(mode='implement', target='I06b', findingIds=[])
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
assert new['I06b']['status'] == 'implemented' and new['I06b']['implementationRecord'] == RECORD
assert all(new[key] == value for key, value in old.items() if key != 'I06b')
assert all(before[key] == after[key] for key in before if key not in ('tasks', 'runs'))
assert after['runs'] == before['runs'] + [RECORD] and after['activeRun'] is None
assert api['selection'](after) == dict(mode='implement', target='I06', findingIds=[])

children = []
for task_id in old['I06b']['children']:
    task = old[task_id]
    assert task['status'] == 'implemented'
    path = task['implementationRecord']
    child = load(path)
    commit = git('log', '--diff-filter=A', '--format=%H', '--', path).splitlines()[-1]
    subprocess.run(['git', 'merge-base', '--is-ancestor', commit, report['startCommit']], check=True)
    assert child['target'] == task_id and child['mode'] == 'implement'
    assert child['outcome'] == 'progress'
    retained = []
    # Verify old records, every passing/red output and all support files against
    # their exact original commit, not an incidental build-directory copy.
    for item in child['changedPaths']:
        assert Path(item).is_file(), item
        if item == STATE:
            continue
        if item.startswith('docs/agent/implementation-runs/'):
            expected = subprocess.check_output(['git', 'show', commit + ':' + item])
            assert Path(item).read_bytes() == expected, item
    for check in child['checks']:
        evidence = check['evidence']
        value = load(evidence)
        assert value['exitCode'] == check['exitCode'], evidence
        assert check['kind'] == 'diagnostic' or check['exitCode'] == 0
        if 'outputSha256' in value:
            assert value['outputSha256'] == digest(value['output'].encode()), evidence
        assert all(p in child['changedPaths'] for p in check.get('supportPaths', []))
        retained.append(dict(kind=check['kind'], exitCode=check['exitCode'],
                             evidence=evidence, sha256=digest(Path(evidence).read_bytes())))
    children.append(dict(id=task_id, record=path, commit=commit,
                         reportSha256=digest(Path(path).read_bytes()), checks=retained))

first = 'docs/agent/implementation-runs/20261022T122241Z-22388f73'
second = 'docs/agent/implementation-runs/20261022T124301Z-3bc7cb2f'
latest = load(second + '/artifact-identities.json')
for path, value in latest.items():
    data = Path(path).read_bytes()
    assert len(data) == value['bytes'] and digest(data) == value['sha256'], path
earlier = load(first + '-identities.json')
replaced = []
for path, value in earlier.items():
    if digest(Path(path).read_bytes()) != value['sha256']:
        if path == 'server/DECKS.md':
            assert Path(path).read_bytes() == subprocess.check_output([
                'git', 'show', children[1]['commit'] + ':' + path])
        else:
            assert path in latest
        replaced.append(path)
assert sorted(replaced) == ['androidApp/build/outputs/apk/debug/androidApp-debug.apk', 'server/DECKS.md']
# I06b2 replaces only the older client APK and adds native wiring documentation.
# All server/shared/generated/runtime/fixture/lock identities from I06b1 survive.
assert git('diff', '--name-only', children[0]['commit'], report['startCommit'],
           'server/src', 'protocol', 'gradle', 'e2e/environment.py', 'tests/fixtures') == ''
host_counts = []
for path in (first + '-junit.json', second + '/host-junit.json'):
    count = 0
    for value in load(path).values():
        data = base64.b64decode(value['base64'])
        assert len(data) == value['bytes'] and digest(data) == value['sha256']
        count += green_xml(data)
    host_counts.append(count)
assert host_counts == [12, 20]
native_prefix = second + '/runner-597854085e4f4790b19bd6ad052b8b88-'
assert green_xml(Path(native_prefix + 'result-0.xml').read_bytes()) == 1
observations = load(native_prefix + 'save-observation.json')
assert len(observations['journeys']) == 10
for name in ('named-preview-landscape', 'save-failure-portrait', 'save-failure-landscape',
             'saved-detail-portrait', 'saved-detail-landscape', 'reopened-server-definitions',
             'second-account-private', 'original-account-retained'):
    assert Path(native_prefix + name + '.png').stat().st_size > 0
    ET.parse(native_prefix + name + '.xml')
assert Path(native_prefix + 'public-catalog-evicted.json').is_file()
assert load(native_prefix + 'session-closed.json')['closed'] is True
for name in ('cleanup-device', 'cleanup-environment'):
    assert load(second + '/' + name + '.json')['exitCode'] == 0

actual = set(git('diff', '--name-only').splitlines()) | set(git('ls-files', '--others', '--exclude-standard').splitlines())
actual.add(str(PREFIX / 'audit.txt'))
assert actual == set(report['changedPaths']), (actual - set(report['changedPaths']), set(report['changedPaths']) - actual)
runner = object.__new__(api['Runner'])
runner.root = ROOT
runner.state_path = Path(STATE)
runner.state_relative = STATE
runner.role_paths(before, choice, report, actual, RECORD)
subprocess.run(['git', 'diff', '--check'], check=True)
for path in actual - {str(PREFIX / 'audit.txt')}:
    data = Path(path).read_bytes()
    assert data.endswith(b'\n') and b'\r' not in data
    assert all(line.rstrip() == line for line in data.splitlines()), path
    if path.endswith('.json'):
        json.loads(data)

print(json.dumps(dict(
    result='PASS', startCommit=report['startCommit'], selected=choice,
    completed='I06b only', nextSelection=api['selection'](after),
    children=children,
    inputAudit=dict(latestManifest=second + '/artifact-identities.json',
                    manifestSha256=digest(Path(second + '/artifact-identities.json').read_bytes()),
                    currentIdentitiesMatched=len(latest), earlierIdentities=len(earlier),
                    earlierReplacedByNativeChild=replaced,
                    unchangedServerSharedGeneratedRuntimeFixtureLocks=True),
    reusedResults=dict(sharedAndPostgresTests=12, mappingTransportImportAccountTests=20,
                       nativeTest=1, nativeXml=native_prefix + 'result-0.xml',
                       journeys=observations['journeys'], retainedCleanupPassed=True),
    integration='I06b2 final normal APK exercised I06b1 unchanged service/image and shared wire types; no uncovered changed integration input. No build/device/full-suite rerun.',
    handoff='State/transition/role/exact paths/JSON/whitespace pass; authorization, findings, ledger, batches, review coverage and external gates unchanged.',
    remaining=report['remainingObligations']), indent=2))
