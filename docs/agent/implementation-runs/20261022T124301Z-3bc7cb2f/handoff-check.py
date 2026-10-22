"""Small read-only state/report/evidence/docs handoff validation, not a product-suite rerun."""
from pathlib import Path
import base64
import hashlib
import json
import re
import runpy
import subprocess
import xml.etree.ElementTree as ET

root = Path.cwd()
prefix = Path(__file__).resolve().parent.relative_to(root)
record = str(prefix.with_suffix('.json'))
report = json.loads(Path(record).read_text())
state_path = 'docs/agent/implementation-state.json'
after = json.loads(Path(state_path).read_text())
def git(*args): return subprocess.check_output(['git', *args], text=True).strip()
before = json.loads(git('show', report['startCommit'] + ':' + state_path))
api = runpy.run_path('scripts/implementation-loop.py')
choice = dict(mode='implement', target='I06b2', findingIds=[])
assert git('rev-parse', 'HEAD') == report['startCommit'] and git('branch', '--show-current') == 'master'
assert not git('diff', '--cached', '--name-only')
assert before['authorization'] == 'authorized' and before['runner']['model'] == 'openai/gpt-6.1-sol#high'
assert api['selection'](before) == choice
api['validate_state'](after)
api['validate_transition'](before, after, choice, report)
old = {t['id']: t for t in before['tasks']}; new = {t['id']: t for t in after['tasks']}
assert new['I06b2']['status'] == 'implemented' and new['I06b2']['implementationRecord'] == record
assert all(new[key] == value for key, value in old.items() if key != 'I06b2')
assert all(before[key] == after[key] for key in before if key not in ('tasks', 'runs'))
assert after['runs'] == before['runs'] + [record] and after['activeRun'] is None
assert api['selection'](after) == dict(mode='implement', target='I06b', findingIds=[])
print('PASS authorized exact high-model/start commit/priority; only I06b2 advances; next I06b; B2/reviews/gates unchanged')
for check in report['checks']:
    path = Path(check['evidence'])
    if path.name == 'handoff-check.json': continue
    value = json.loads(path.read_text())
    assert value['exitCode'] == check['exitCode'], path
    assert check['kind'] == 'diagnostic' or check['exitCode'] == 0
    for support in check.get('supportPaths', []):
        assert Path(support).is_file() and support in report['changedPaths']
counts = []
for value in json.loads((prefix / 'host-junit.json').read_text()).values():
    data = base64.b64decode(value['base64'])
    assert len(data) == value['bytes'] and hashlib.sha256(data).hexdigest() == value['sha256']
    cases = list(ET.fromstring(data).iter('testcase'))
    assert not any(list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped'))
    counts.append(len(cases))
assert sorted(counts) == [3, 5, 12]
native = prefix / 'runner-597854085e4f4790b19bd6ad052b8b88-result-0.xml'
cases = list(ET.parse(native).iter('testcase'))
assert len(cases) == 1 and not any(list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped'))
for name in ('saved-detail-portrait', 'saved-detail-landscape', 'save-failure-portrait', 'save-failure-landscape',
             'reopened-server-definitions', 'public-catalog-evicted', 'second-account-private', 'original-account-retained', 'save-observation'):
    assert any(prefix.glob('runner-597854085e4f4790b19bd6ad052b8b88-' + name + '.*')), name
print('PASS truthful command exits, lossless 20 affected host tests and complete final native journey/captures')
for path, value in json.loads((prefix / 'artifact-identities.json').read_text()).items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == value['sha256'], path
for kind in ('device', 'e2e'):
    assert Path('/tmp/opencode/atw-' + kind + '-3bc7cb2f202610220000000000000001/cleaned.json').is_file()
print('PASS exact current source/fixture/lock/APK/image identities and completed owned cleanup')
for path in (Path('client-storage/IMPORT.md'), Path('server/DECKS.md'), prefix / 'README.md'):
    text = path.read_text(); assert text.count('```') % 2 == 0
    for target in re.findall(r'\]\(([^)]+)\)', text):
        if '://' not in target: assert (path.parent / target.split('#')[0]).is_file(), (path, target)
ledger = 'docs/agent/coverage/ledger.json'
assert Path(ledger).read_bytes() == subprocess.check_output(['git', 'show', report['startCommit'] + ':' + ledger])
actual = set(git('diff', '--name-only').splitlines()) | set(git('ls-files', '--others', '--exclude-standard').splitlines())
actual.add(str(prefix / 'handoff-check.json'))
assert actual == set(report['changedPaths']), (actual - set(report['changedPaths']), set(report['changedPaths']) - actual)
runner = object.__new__(api['Runner']); runner.root = root; runner.state_path = Path(state_path); runner.state_relative = state_path
runner.role_paths(before, choice, report, actual, record)
subprocess.run(['git', 'diff', '--check'], check=True)
for path in actual:
    if not Path(path).is_file(): continue
    if Path(path).suffix in ('.kt', '.py', '.json', '.md'):
        data = Path(path).read_bytes()
        assert b'\r' not in data and data.endswith(b'\n'), path
        assert all(line.rstrip() == line for line in data.splitlines()), path
print('PASS documentation links/fences/whitespace, unchanged ledger and exact owned changed paths/role boundary')
print('NOT CLAIMED: I06b/I06 closure, I07/I08, independent B2 review, whole P/T/F scenarios, full suite/final UI/release gates')
