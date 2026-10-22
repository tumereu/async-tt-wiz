"""Read-only one-unit/report/retained-evidence/docs validation. No controller or product suite."""
import base64
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess
import xml.etree.ElementTree as ET

root = Path.cwd()
prefix = 'docs/agent/implementation-runs/20261022T122241Z-22388f73'
report_path = prefix + '.json'
report = json.loads(Path(report_path).read_text())
state_path = 'docs/agent/implementation-state.json'
def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()
before = json.loads(git('show', report['startCommit'] + ':' + state_path))
after = json.loads(Path(state_path).read_text())
api = runpy.run_path('scripts/implementation-loop.py')
choice = dict(mode='implement', target='I06b1', findingIds=[])
assert git('rev-parse', 'HEAD') == report['startCommit']
assert git('branch', '--show-current') == 'master' and not git('diff', '--cached', '--name-only')
assert before['authorization'] == 'authorized' and 'D-304' in Path(before['authorizationRecord']).read_text()
assert before['runner']['model'] == 'openai/gpt-6.1-sol#high' and before['findings'] == []
assert api['selection'](before) == choice
api['validate_state'](after)
api['validate_transition'](before, after, choice, report)
old = {t['id']: t for t in before['tasks']}
new = {t['id']: t for t in after['tasks']}
assert new['I06b1']['status'] == 'implemented' and new['I06b1']['implementationRecord'] == report_path
assert all(new[key] == task for key, task in old.items() if key != 'I06b1')
assert all(before[key] == after[key] for key in before if key not in ('tasks', 'runs'))
assert after['activeRun'] is None and after['runs'] == before['runs'] + [report_path]
assert api['selection'](after) == dict(mode='implement', target='I06b2', findingIds=[])
assert 'enabled = false' in Path('androidApp/src/main/kotlin/org/asyncttwiz/android/DeckImportScreen.kt').read_text()
print('PASS D-304/high model/baseline/priority, only I06b1 implemented; next native I06b2, ancestors/reviews/gates unchanged')

for check in report['checks']:
    if check['evidence'] == prefix + '-handoff.json':
        continue
    path = Path(check['evidence'])
    value = json.loads(path.read_text())
    assert value['exitCode'] == check['exitCode']
    assert value['outputSha256'] == hashlib.sha256(value['output'].encode()).hexdigest()
    assert check['kind'] == 'diagnostic' or check['exitCode'] == 0
    for support in check.get('supportPaths', []):
        assert Path(support).is_file() and support in report['changedPaths']
junit = json.loads(Path(prefix + '-junit.json').read_text())
for path, value in junit.items():
    data = base64.b64decode(value['base64'])
    assert hashlib.sha256(data).hexdigest() == value['sha256'] and len(data) == value['bytes']
    cases = list(ET.fromstring(data).iter('testcase'))
    assert len(cases) == value['tests']
    assert not any(list(c.iter(tag)) for c in cases for tag in ('failure', 'error', 'skipped'))
assert sum(value['tests'] for path, value in junit.items()) == 12
initial = json.loads(Path(prefix + '-tests2.json').read_text())['output']
assert initial.count('AccountHttpTest >') == 8 and initial.count('FoundationDatabaseTest >') == 12
assert initial.count(' PASSED') == 13 and '14 tests completed, 1 failed' in initial
failed = ET.parse(prefix + '-tests2-decks.xml').getroot()
assert int(failed.attrib['failures']) == 1
print('PASS exact original outputs including red diagnostics; current XML 7 protocol/5 service green; ten earlier green account/foundation sibling results reused, not rerun or global red command relabeled')

for path, value in json.loads(Path(prefix + '-identities.json').read_text()).items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == value['sha256'], path
assert Path('docs/agent/coverage/ledger.json').read_bytes() == subprocess.check_output([
    'git', 'show', report['startCommit'] + ':docs/agent/coverage/ledger.json'])
for path in (Path('server/README.md'), Path('server/DECKS.md')):
    text = path.read_text()
    assert text.count('```') % 2 == 0
    for target in re.findall(r'\]\(([^)]+)\)', text):
        if '://' not in target:
            local = target.split('#')[0]
            assert (path.parent / local if local else path).is_file(), (path, target)
print('PASS exact current source/build/lock/fixture/generated/runtime/normal-APK identities, unchanged partial ledger, documentation links/fences')

actual = set(git('diff', '--name-only').splitlines()) | set(git('ls-files', '--others', '--exclude-standard').splitlines())
assert actual == set(report['changedPaths']), (actual - set(report['changedPaths']), set(report['changedPaths']) - actual)
runner = object.__new__(api['Runner'])
runner.root = root
runner.state_path = Path(state_path)
runner.state_relative = state_path
runner.role_paths(before, choice, report, actual, report_path)
subprocess.run(['git', 'diff', '--check'], check=True)
for path in actual:
    data = Path(path).read_bytes()
    assert b'\r' not in data and data.endswith(b'\n'), path
    assert all(line.rstrip() == line for line in data.splitlines()), path
print('PASS exact owned paths, existing driver transition/role checks and staged-safe whitespace; no other task/review advanced')
print('NOT CLAIMED: native Save/reopen, full image/Compose/device reset, complete P/T/F scenarios, independent B2 review, full suite or external release readiness')
