"""Read-only bounded report/state/output/identity/docs validation; no additional product audit."""
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
state_path = 'docs/agent/implementation-state.json'
report = json.loads(Path(report_path).read_text())
after = json.loads(Path(state_path).read_text())
def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()
before = json.loads(git('show', report['startCommit'] + ':' + state_path))
api = runpy.run_path('scripts/implementation-loop.py')
choice = {'mode': 'implement', 'target': 'I05b', 'findingIds': []}
assert git('rev-parse', 'HEAD') == report['startCommit']
assert git('branch', '--show-current') == 'master' and not git('diff', '--cached', '--name-only')
assert before['authorization'] == 'authorized' and before['runner']['model'] == 'openai/gpt-6.1-sol#high'
assert api['selection'](before) == choice
api['validate_state'](after)
api['validate_transition'](before, after, choice, report)
assert after['activeRun'] is None and after['runs'] == before['runs'] + [report_path]
old = {t['id']: t for t in before['tasks']}
new = {t['id']: t for t in after['tasks']}
assert new['I05b']['status'] == 'implemented' and new['I05b']['implementationRecord'] == report_path
assert all(new[key] == task for key, task in old.items() if key != 'I05b')
assert all(before[key] == after[key] for key in before if key not in ('tasks', 'runs'))
assert api['selection'](after) == {'mode': 'implement', 'target': 'I05', 'findingIds': []}
print('PASS supplied commit/branch/high-model/D-304/priority, only I05b implemented, next I05; reviews/gates unchanged')

for check in report['checks']:
    path = Path(check['evidence'])
    if path.name == 'handoff-check.json':
        continue
    value = json.loads(path.read_text())
    assert value['exitCode'] == check['exitCode']
    assert check['kind'] == 'diagnostic' or check['exitCode'] == 0
    data = value['output'].encode() if 'output' in value else base64.b64decode(value['outputBase64'])
    assert hashlib.sha256(data).hexdigest() == value['outputSha256']
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
assert sum(v for k, v in counts.items() if k.startswith('androidApp/')) == 22
assert sum(v for k, v in counts.items() if k.startswith('client-storage/')) == 1
for name in ('android-final.json', 'android-pending-recheck.json'):
    assert 'OK (1 test)' in json.loads((prefix / name).read_text())['output']
print('PASS actual retained checks, lossless current decoder-cache/22 Android host XML and two distinct passing native scopes; no full-suite claim')

for path, value in json.loads((prefix / 'artifact-identities.json').read_text()).items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == value['sha256'], path
for directory in ('/tmp/opencode/atw-device-ae8e28e311014b3e99d382647521e8ff',
                  '/tmp/opencode/atw-e2e-ae8e28e311014b3e99d382647521e8ff'):
    assert (Path(directory) / 'cleaned.json').is_file()
for name in ('cleanup-device.json', 'cleanup-environment.json'):
    assert json.loads((prefix / name).read_text())['exitCode'] == 0
print('PASS exact final source/test/APK/lock/fixture identities and completed owned device/Compose cleanup')

for path in (Path('client-storage/ARTWORK.md'), prefix / 'README.md'):
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
    if Path(path).suffix in ('.kt', '.kts', '.py', '.json', '.md', '.lockfile'):
        data = Path(path).read_bytes()
        assert b'\r' not in data and data.endswith(b'\n'), path
        assert all(line.rstrip() == line for line in data.splitlines()), path
print('PASS documentation links/fences/whitespace, unchanged partial coverage ledger and exact owned report/state/source paths')
print('NOT CLAIMED: import/inspector/table/full scenarios, first B2 review, full suite, physical scene performance, final UI or release gates')
