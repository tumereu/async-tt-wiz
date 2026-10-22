"""Bounded read-only state/report/evidence/docs validation, not another product test run."""
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
report_path = str(prefix.with_suffix('.json'))
report = json.loads(Path(report_path).read_text())
state_path = 'docs/agent/implementation-state.json'
after = json.loads(Path(state_path).read_text())
def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()
before = json.loads(git('show', report['startCommit'] + ':' + state_path))
api = runpy.run_path('scripts/implementation-loop.py')
choice = dict(mode='implement', target='I06a', findingIds=[])
assert git('rev-parse', 'HEAD') == report['startCommit']
assert git('branch', '--show-current') == 'master' and not git('diff', '--cached', '--name-only')
assert before['authorization'] == 'authorized' and before['runner']['model'] == 'openai/gpt-6.1-sol#high'
assert api['selection'](before) == choice
api['validate_state'](after)
api['validate_transition'](before, after, choice, report)
old = {t['id']: t for t in before['tasks']}
new = {t['id']: t for t in after['tasks']}
assert new['I06a']['status'] == 'implemented' and new['I06a']['implementationRecord'] == report_path
assert all(new[key] == task for key, task in old.items() if key != 'I06a')
assert all(before[key] == after[key] for key in before if key not in ('tasks', 'runs'))
assert after['activeRun'] is None and after['runs'] == before['runs'] + [report_path]
assert api['selection'](after) == dict(mode='implement', target='I06b', findingIds=[])
print('PASS D-304/high-model/commit/priority and only I06a implemented; next I06b, reviews/gates unchanged')

for check in report['checks']:
    path = Path(check['evidence'])
    if path.name == 'handoff-check.json':
        continue
    if path.suffix == '.json':
        value = json.loads(path.read_text())
        assert value['exitCode'] == check['exitCode']
    assert check['kind'] == 'diagnostic' or check['exitCode'] == 0
    for support in check.get('supportPaths', []):
        assert Path(support).is_file() and support in report['changedPaths']
host = json.loads((prefix / 'host-junit.json').read_text())
counts = {}
for name, value in host.items():
    data = base64.b64decode(value['base64'])
    assert len(data) == value['bytes'] and hashlib.sha256(data).hexdigest() == value['sha256']
    cases = list(ET.fromstring(data).iter('testcase'))
    assert cases and not any(list(c.iter(t)) for c in cases for t in ('failure', 'error', 'skipped'))
    counts[name] = len(cases)
assert sum(v for k, v in counts.items() if k.startswith('client-storage/')) == 5
assert sum(v for k, v in counts.items() if k.startswith('androidApp/')) == 3
native = prefix / 'runner-acaf719979794f55b47ce44ca7839b9f-result-0.xml'
cases = list(ET.parse(native).iter('testcase'))
assert len(cases) == 1 and not any(list(c.iter(t)) for c in cases for t in ('failure', 'error', 'skipped'))
for orientation in ('portrait', 'landscape'):
    for state in ('d03-error', 'd04-preview', 'd04b-controls'):
        assert (prefix / f'runner-acaf719979794f55b47ce44ca7839b9f-{state}-{orientation}.png').is_file()
print('PASS retained actual command outcomes, 5 parser/3 controller current XML and final normal-APK journey/captures')
for path, value in json.loads((prefix / 'artifact-identities.json').read_text()).items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == value['sha256'], path
for directory in ('/tmp/opencode/atw-device-f8a9eff111114b3e99d382647521e8ff',
                  '/tmp/opencode/atw-e2e-f8a9eff111114b3e99d382647521e8ff'):
    assert (Path(directory) / 'cleaned.json').is_file()
print('PASS current source/fixture/lock/APK/image identities and exact completed owned-resource cleanup')

for path in (Path('client-storage/IMPORT.md'), prefix / 'README.md'):
    text = path.read_text()
    assert text.count('```') % 2 == 0
    for target in re.findall(r'\]\(([^)]+)\)', text):
        if '://' not in target:
            assert (path.parent / target.split('#')[0]).is_file(), (path, target)
ledger = Path('docs/agent/coverage/ledger.json')
assert ledger.read_bytes() == subprocess.check_output(['git', 'show', report['startCommit'] + ':' + str(ledger)])
actual = set(git('diff', '--name-only').splitlines()) | set(git('ls-files', '--others', '--exclude-standard').splitlines())
assert actual == set(report['changedPaths']), (actual - set(report['changedPaths']), set(report['changedPaths']) - actual)
runner = object.__new__(api['Runner'])
runner.root = root
runner.state_path = Path(state_path)
runner.state_relative = state_path
runner.role_paths(before, choice, report, actual, report_path)
subprocess.run(['git', 'diff', '--check'], check=True)
for path in actual:
    if Path(path).suffix in ('.kt', '.py', '.json', '.md'):
        data = Path(path).read_bytes()
        assert b'\r' not in data and data.endswith(b'\n'), path
        assert all(line.rstrip() == line for line in data.splitlines()), path
print('PASS docs links/fences/whitespace, unchanged partial ledger, exact owned changed paths and role boundary')
print('NOT CLAIMED: saved-deck/server acceptance, complete P/T/F scenarios, independent B2 review, full suite or release gates')
