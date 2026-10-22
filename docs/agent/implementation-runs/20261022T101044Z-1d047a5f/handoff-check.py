"""Read-only scoped I05a report/state/evidence/cleanup/documentation validation, no new product run."""
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
choice = {'mode': 'implement', 'target': 'I05a', 'findingIds': []}
assert git('rev-parse', 'HEAD') == report['startCommit']
assert git('branch', '--show-current') == 'master' and not git('diff', '--cached', '--name-only')
assert before['authorization'] == 'authorized' and before['runner']['model'] == 'openai/gpt-6.1-sol#high'
assert api['selection'](before) == choice
api['validate_state'](after)
api['validate_transition'](before, after, choice, report)
assert after['activeRun'] is None and after['runs'] == before['runs'] + [report_path]
old = {task['id']: task for task in before['tasks']}
new = {task['id']: task for task in after['tasks']}
assert new['I05a']['status'] == 'implemented' and new['I05a']['implementationRecord'] == report_path
assert all(new[identifier] == task for identifier, task in old.items() if identifier != 'I05a')
for key in before:
    if key not in ('tasks', 'runs'):
        assert before[key] == after[key], key
assert api['selection'](after) == {'mode': 'implement', 'target': 'I05b', 'findingIds': []}
print('PASS exact supplied high-model/D-304/master priority; only I05a implemented, next I05b; all gates/reviews retained')

for check in report['checks']:
    path = Path(check['evidence'])
    if path.name == 'handoff-check.json':
        continue
    value = json.loads(path.read_text())
    assert value['exitCode'] == check['exitCode']
    assert check['kind'] == 'diagnostic' or check['exitCode'] == 0
    data = value.get('output', '').encode() if 'output' in value else base64.b64decode(value['outputBase64'])
    assert hashlib.sha256(data).hexdigest() == value['outputSha256']
    for support in check.get('supportPaths', []):
        assert Path(support).is_file() and support in report['changedPaths']

host = json.loads((prefix / 'host-junit.json').read_text())
counts = {}
for name, value in host.items():
    data = base64.b64decode(value['base64'])
    assert hashlib.sha256(data).hexdigest() == value['sha256']
    cases = list(ET.fromstring(data).iter('testcase'))
    assert cases and not any(list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped'))
    counts[name] = len(cases)
assert sum(value for key, value in counts.items() if key.startswith('client-storage/')) == 8
assert sum(value for key, value in counts.items() if key.startswith('androidApp/')) == 22
tail = json.loads((prefix / 'runner-084fd122bfae433faca52d69685f41d2.json').read_text())
assert 'passed.json' in tail
for name, envelope in tail.items():
    assert hashlib.sha256(base64.b64decode(envelope['base64'])).hexdigest() == envelope['sha256']
print('PASS actual eight catalog/22 Android host cases and passing retained native tail; diagnostics remain diagnostic')

identities = json.loads((prefix / 'artifact-identities.json').read_text())
for path, value in identities.items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == value['sha256'], path
assert 'OK (1 test)' in json.loads((prefix / 'android-catalog-recheck.json').read_text())['output']
for directory in ('/tmp/opencode/atw-device-1d047a5f11114af58b9f3861313df8c1',
                  '/tmp/opencode/atw-e2e-1d047a5f11114af58b9f3861313df8c1'):
    assert (Path(directory) / 'cleaned.json').is_file()
for name in ('cleanup-device.json', 'cleanup-environment.json'):
    assert json.loads((prefix / name).read_text())['exitCode'] == 0
print('PASS actual APK/source/locks/fixture input identities and exact completed device/Compose cleanup')

for path in (Path('client-storage/CATALOG.md'), prefix / 'README.md'):
    text = path.read_text()
    assert text.count('```') % 2 == 0
    for target in re.findall(r'\]\(([^)]+)\)', text):
        if '://' not in target:
            assert (path.parent / target.split('#')[0]).is_file(), (path, target)
    assert not any(line.rstrip() != line for line in text.splitlines())
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
    if Path(path).suffix in ('.kt', '.kts', '.py', '.json', '.md', '.xml', '.lockfile'):
        data = Path(path).read_bytes()
        assert b'\r' not in data and data.endswith(b'\n'), path
        assert all(line.rstrip() == line for line in data.splitlines()), path
print('PASS documentation links/fences, unchanged partial coverage ledger, exact owned paths, runner role boundary and whitespace')
print('NOT CLAIMED: artwork/import/saved-definition/game/history scenarios, independent B2 review, full suite/final mocks or release readiness')
