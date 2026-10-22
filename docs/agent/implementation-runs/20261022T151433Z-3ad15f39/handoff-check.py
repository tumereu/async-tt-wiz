"""Small report/state/input/docs check, not another product or device qualification run."""
from pathlib import Path
import base64
import hashlib
import json
import re
import runpy
import subprocess

root = Path.cwd()
prefix = Path(__file__).resolve().parent.relative_to(root)
record = str(prefix.with_suffix('.json'))
report = json.loads(Path(record).read_text())
state_path = 'docs/agent/implementation-state.json'
after = json.loads(Path(state_path).read_text())
def git(*args): return subprocess.check_output(['git', *args], text=True).strip()
before = json.loads(git('show', report['startCommit'] + ':' + state_path))
api = runpy.run_path('scripts/implementation-loop.py')
choice = dict(mode='implement', target='I07b', findingIds=[])
assert git('rev-parse', 'HEAD') == report['startCommit'] and git('branch', '--show-current') == 'master'
assert not git('diff', '--cached', '--name-only')
assert before['authorization'] == 'authorized' and before['runner']['model'] == 'openai/gpt-6.1-sol#high'
assert before['findings'] == [] and api['selection'](before) == choice
api['validate_state'](after)
api['validate_transition'](before, after, choice, report)
old = {t['id']: t for t in before['tasks']}; new = {t['id']: t for t in after['tasks']}
assert new['I07b']['status'] == 'implemented' and new['I07b']['implementationRecord'] == record
assert all(new[key] == value for key, value in old.items() if key != 'I07b')
assert all(before[key] == after[key] for key in before if key not in ('tasks', 'runs'))
assert after['runs'] == before['runs'] + [record] and after['activeRun'] is None
assert api['selection'](after) == dict(mode='implement', target='I07', findingIds=[])
print('PASS exact starting commit/branch/authorized high-model priority; only I07b advances; I07 parent next; B2/reviews/gates unchanged')
for check in report['checks']:
    path = Path(check['evidence'])
    if path.name == 'handoff-check.json': continue
    value = json.loads(path.read_text())
    assert value['exitCode'] == check['exitCode'], path
    assert check['kind'] == 'diagnostic' or check['exitCode'] == 0
    for support in check.get('supportPaths', []):
        assert Path(support).is_file() and support in report['changedPaths']
for path, value in json.loads((prefix / 'input-identities.json').read_text()).items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == value['sha256'], path
for path in prefix.glob('*.xml.json'):
    value = json.loads(path.read_text()); data = base64.b64decode(value['base64'])
    assert len(data) == value['bytes'] and hashlib.sha256(data).hexdigest() == value['sha256']
assert Path('/tmp/opencode/atw-device-3ad15f39000000000000000000000001/cleaned.json').is_file()
assert Path('/tmp/opencode/atw-e2e-3ad15f39000000000000000000000001/cleaned.json').is_file()
print('PASS retained actual required/diagnostic output, final source/artifact hashes, lossless XML and completed exact device/environment cleanup')
for path in [prefix / 'README.md']:
    text = path.read_text(); assert text.count('```') % 2 == 0
    for target in re.findall(r'\]\(([^)]+)\)', text):
        if '://' not in target: assert (path.parent / target.split('#')[0]).is_file(), (path, target)
actual = set(git('diff', '--name-only').splitlines()) | set(git('ls-files', '--others', '--exclude-standard').splitlines())
actual.add(str(prefix / 'handoff-check.json'))
assert actual == set(report['changedPaths']), (actual - set(report['changedPaths']), set(report['changedPaths']) - actual)
runner = object.__new__(api['Runner']); runner.root = root; runner.state_path = Path(state_path); runner.state_relative = state_path
runner.role_paths(before, choice, report, actual, record)
subprocess.run(['git', 'diff', '--check'], check=True)
for path in actual:
    if Path(path).is_file() and Path(path).suffix in ('.kt', '.py', '.json', '.md'):
        data = Path(path).read_bytes()
        assert b'\r' not in data and data.endswith(b'\n'), path
        assert all(line.rstrip() == line for line in data.splitlines()), path
print('PASS docs links/fences/whitespace, report/support/changed-path role boundaries; no full suite or repeated green product check')
print('NOT CLAIMED: wholly green original native journey, final whole-R1/UI/compatibility, independent B2 review, I07/I08, preparation/gameplay disclosure or external release gates')
