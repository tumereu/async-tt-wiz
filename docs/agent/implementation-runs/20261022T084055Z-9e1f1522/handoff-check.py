"""Small current-unit evidence/transition/docs audit, not another product suite or independent review."""
import base64
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess
import xml.etree.ElementTree as ET


def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()


prefix = Path('docs/agent/implementation-runs/20261022T084055Z-9e1f1522')
report_path = str(prefix) + '.json'
report = json.loads(Path(report_path).read_text())
before = json.loads(git('show', report['startCommit'] + ':docs/agent/implementation-state.json'))
after = json.loads(Path('docs/agent/implementation-state.json').read_text())
driver = runpy.run_path('scripts/implementation-loop.py')
choice = {'mode': 'implement', 'target': 'I04b', 'findingIds': []}
assert git('rev-parse', 'HEAD') == report['startCommit']
assert git('branch', '--show-current') == 'master'
assert not git('diff', '--cached', '--name-only')
assert before['authorization'] == 'authorized' and 'D-304' in Path(before['authorizationRecord']).read_text()
assert driver['selection'](before) == choice
driver['validate_state'](after)
driver['validate_transition'](before, after, choice, report)
assert after['activeRun'] is None and after['runs'] == before['runs'] + [report_path]
for key in before:
    if key not in {'tasks', 'runs'}:
        assert before[key] == after[key], key
for old, new in zip(before['tasks'], after['tasks'], strict=True):
    if old['id'] != 'I04b':
        assert old == new, old['id']
    else:
        assert new['status'] == 'implemented' and new['implementationRecord'] == report_path
assert driver['selection'](after) == {'mode': 'implement', 'target': 'I04', 'findingIds': []}
print('PASS exact baseline/master, D-304 priority and I04b-only implementation; next I04 child audit, not self-review. Other tasks/reviews/findings/model/grants/external gates unchanged.')

actual = set(git('diff', '--name-only').splitlines()) | set(git('ls-files', '--others', '--exclude-standard').splitlines())
assert actual == set(report['changedPaths']), actual ^ set(report['changedPaths'])
runner = object.__new__(driver['Runner'])
runner.root = Path.cwd()
runner.state_path = Path('docs/agent/implementation-state.json')
runner.state_relative = str(runner.state_path)
runner.role_paths(before, choice, report, actual, report_path)
subprocess.run(['git', 'diff', '--check'], check=True)
for path in actual:
    data = Path(path).read_bytes()
    if path.endswith('.png'):
        assert data.startswith(b'\x89PNG\r\n\x1a\n')
        continue
    assert b'\r' not in data and data.endswith(b'\n'), path
    assert all(line.rstrip() == line for line in data.splitlines()), path
    if path.endswith('.md'):
        text = data.decode()
        assert text.count('```') % 2 == 0, path
        for target in re.findall(r'\]\(([^)]+)\)', text):
            if '://' not in target and not target.startswith('#'):
                assert (Path(path).parent / target.split('#')[0]).exists(), (path, target)
print('PASS exact owned paths/role scope, Markdown local links/fences and text whitespace; UI packet untouched (no publication batch).')

for check in report['checks']:
    if check['evidence'].endswith('/handoff-check.json'):
        continue  # The capture process writes this output only after the audit exits.
    output = json.loads(Path(check['evidence']).read_text())
    assert output['exitCode'] == check['exitCode']
    data = output['output'].encode() if 'output' in output else base64.b64decode(output['outputBase64'])
    assert hashlib.sha256(data).hexdigest() == output['outputSha256'], check['evidence']
    if check['kind'] == 'required':
        assert check['exitCode'] == 0
print('PASS retained command bytes/hashes and truthful diagnostic/required statuses.')

count = 0
for filename, data in json.loads((prefix / 'host-junit.json').read_text()).items():
    suite = ET.fromstring(base64.b64decode(data))
    tests = suite.findall('testcase')
    assert tests and not any(list(test.iter(tag)) for test in tests for tag in ('failure', 'error', 'skipped')), filename
    count += len(tests)
print('PASS retained actual affected host/server JUnit:', count, 'non-skipped green assertions; no whole-suite claim.')
passed_native = 0
for file in prefix.glob('first-runner-*.json'):
    records = json.loads(file.read_text())
    for name, entry in records.items():
        if 'base64' in entry:
            data = base64.b64decode(entry['base64'])
            assert hashlib.sha256(data).hexdigest() == entry['sha256']
        elif 'path' in entry:
            assert hashlib.sha256(Path(entry['path']).read_bytes()).hexdigest() == entry['sha256']
        if name == 'passed.json':
            assert json.loads(data)['devices'] == 2
            assert 'tail' in json.loads(data)['scope'].lower()
            passed_native += 1
assert passed_native == 1
baseline = json.loads((prefix / 'first-runner-8f3cb5377c9547448659c5ee503aee32.json').read_text())
suite = ET.fromstring(base64.b64decode(baseline['result-0.xml']['base64']))
failure = suite.find('testcase/failure')
assert failure is not None and 'Device.logout' in failure.text and 'Forgot password?' in failure.text
assert all(name in baseline for name in ('a04-used-link-landscape.xml', 'a04-used-link-portrait.xml',
                                        'new-password-resumed.xml', 'native-intent-input-saved-state.json'))
checkpoint = json.loads(base64.b64decode(baseline['native-intent-input-saved-state.json']['base64']))
assert 'OK (1 test)' in checkpoint['output']
print('PASS one targeted retained two-device Logout/account-switch tail plus exact prior core reset/revocation/input/reuse/resume assertions. Original full-run red results stay diagnostic, not a manufactured whole green journey.')
for role in ('first', 'second'):
    assert (prefix / ('cleaned-' + role + '.json')).exists()
print('PASS exact owned two-device cleanup output retained; Compose cleanup check retained separately. Parent I04, independent B1 review and I34/I35/I36/B8/external gates remain open.')
