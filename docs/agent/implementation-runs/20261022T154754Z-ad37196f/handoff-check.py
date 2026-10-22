"""Scoped report/state/docs/input handoff validation, not a repeated product suite."""
from pathlib import Path
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
choice = dict(mode='implement', target='I08', findingIds=[])
assert git('rev-parse', 'HEAD') == report['startCommit'] and git('branch', '--show-current') == 'master'
assert not git('diff', '--cached', '--name-only')
assert before['authorization'] == 'authorized' and before['runner']['model'] == 'openai/gpt-6.1-sol#high'
assert before['findings'] == [] and api['selection'](before) == choice
api['validate_state'](after)
api['validate_transition'](before, after, choice, report)
old = {t['id']: t for t in before['tasks']}; new = {t['id']: t for t in after['tasks']}
assert new['I08']['status'] == 'implemented' and new['I08']['implementationRecord'] == record
assert all(new[key] == value for key, value in old.items() if key != 'I08')
assert all(before[key] == after[key] for key in before if key not in ('tasks', 'batches', 'runs'))
assert after['runs'] == before['runs'] + [record] and after['activeRun'] is None
assert api['selection'](after) == dict(mode='review', target='B2', findingIds=[])
assert next(b for b in after['batches'] if b['id'] == 'B2')['review']['completed'] == {}
print('PASS exact clean starting commit/branch/authorized high-model priority; only I08 implemented; B2 ready for first independent review; external gates/authorization unchanged')
for check in report['checks']:
    path = Path(check['evidence'])
    if path.name == 'handoff-check.json': continue
    value = json.loads(path.read_text())
    assert value['exitCode'] == check['exitCode'] == 0, path
    for support in check.get('supportPaths', []):
        assert Path(support).is_file() and support in report['changedPaths']
for path, value in json.loads((prefix / 'input-identities.json').read_text()).items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == value['sha256'], path
assert Path('/tmp/opencode/atw-device-ad37196f000000000000000000000001/cleaned.json').is_file()
assert Path('/tmp/opencode/atw-e2e-ad37196f000000000000000000000001/cleaned.json').is_file()
print('PASS retained required actual outputs, final source/APK/image/fixture/lock hashes and completed exact device/environment cleanup')
for path in [prefix / 'README.md', Path('server/DECKS.md')]:
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
print('PASS docs links/fences/whitespace, report/support/changed-path role boundaries; no full-suite or repeated green product check')
