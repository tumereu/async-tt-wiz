"""Validate/generate the unit's report accounting and evidence handoff; no new product run."""
from pathlib import Path
import base64
import hashlib
import json
import re
import runpy
import shlex
import subprocess
import xml.etree.ElementTree as ET

prefix = Path(__file__).resolve().parent.relative_to(Path.cwd())
state_path = 'docs/agent/implementation-state.json'
report_path = str(prefix) + '.json'
start = 'e2d9bfaf4aee00b8dfca2fd66d93b794409edb6c'
def git(*args): return subprocess.check_output(['git', *args], text=True).strip()
before = json.loads(git('show', start + ':' + state_path))
after = json.loads(Path(state_path).read_text())
report = json.loads(Path(report_path).read_text())
choice = dict(mode='implement', target='I10b2', findingIds=[])
checks = []
for path in sorted(prefix.glob('*.json')):
    record = json.loads(path.read_text())
    if not isinstance(record, dict) or not {'command', 'exitCode'} <= record.keys(): continue
    support = []
    for arg in record['command']:
        if arg.startswith(str(prefix)) and arg.endswith('.py'): support.append(arg)
    if any('native-check.py' in arg for arg in record['command']): support += [str(prefix / name) for name in ('control.py', 'offload.py')]
    if any('reattach.py' in arg or 'interaction-check.py' in arg for arg in record['command']): support.append(str(prefix / 'offload.py'))
    check = dict(kind='required' if record['exitCode'] == 0 else 'diagnostic',
        command=f'python3 scripts/capture-check.py {path} -- ' + shlex.join(record['command']), exitCode=record['exitCode'], evidence=str(path))
    if support: check['supportPaths'] = sorted(set(support))
    checks.append(check)
checks.append(dict(kind='required', command=f'python3 {prefix}/finish.py > {prefix}/handoff.txt 2>&1', exitCode=0,
    evidence=str(prefix / 'handoff.txt'), supportPaths=[str(prefix / 'finish.py')]))
report['checks'] = checks
actual = set(git('diff', '--name-only').splitlines()) | set(git('ls-files', '--others', '--exclude-standard').splitlines())
report['changedPaths'] = sorted(actual)
Path(report_path).write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n')
api = runpy.run_path('scripts/implementation-loop.py')
assert git('rev-parse', 'HEAD') == start and git('branch', '--show-current') == 'master'
assert not git('diff', '--cached', '--name-only')
assert before['authorization'] == 'authorized' and before['runner']['model'] == 'openai/gpt-6.1-sol#high'
assert api['selection'](before) == choice
api['validate_state'](after); api['validate_transition'](before, after, choice, report)
assert api['selection'](after) == dict(mode='implement', target='I10b', findingIds=[])
assert after['activeRun'] is None and after['runs'] == before['runs'] + [report_path]
old = {t['id']: t for t in before['tasks']}; new = {t['id']: t for t in after['tasks']}
assert new['I10b2']['status'] == 'implemented'
assert all(new[key] == value for key, value in old.items() if key != 'I10b2')
for key in before:
    if key not in ('tasks', 'runs'): assert before[key] == after[key], key
runner = object.__new__(api['Runner']); runner.root = Path.cwd(); runner.state_path = Path(state_path); runner.state_relative = state_path
runner.role_paths(before, choice, report, actual, report_path)
print('PASS supplied baseline/authorization/model/priority and driver state/transition/path guards; only I10b2 implemented, next I10b')
print('PASS other tasks/batches/reviews/findings/authorization/permissions/external gates unchanged')
identities = json.loads((prefix / 'input-identities.json').read_text())
for path, identity in identities.items():
    raw = Path(path).read_bytes()
    if path.endswith('/NativePreparationTransferTest.kt'):
        executed = raw.replace(b'                run {\n', b'                if (stage != "done") {\n')
        assert hashlib.sha256(executed).hexdigest() == identity['sha256']
        print('PASS final E2E-only deletion of unexercised done shortcut; exact executed-stage identity reconstructs, final compile passes')
    else: assert hashlib.sha256(raw).hexdigest() == identity['sha256'], path
cases = []
for text in json.loads((prefix / 'host-junit.json').read_text()).values():
    root = ET.fromstring(text); assert not any(list(root.iter(tag)) for tag in ('failure', 'error', 'skipped'))
    cases += list(root.iter('testcase'))
assert len(cases) == 20
for name in ('native-retained-observer-cwd.json', 'native-interaction-navigation.json', 'native-rapid-interaction.json'):
    assert json.loads((prefix / name).read_text())['exitCode'] == 0
for i in (1, 2):
    cleaned = json.loads(Path('/tmp/opencode/atw-device-802a24bd00000000000000000000000' + str(i) + '/cleaned.json').read_text())
    assert cleaned['state']['cleaned'] and cleaned['listeners'] == [] and all(x is None for x in cleaned['currentProcesses'])
assert json.loads(Path('/tmp/opencode/atw-e2e-802a24bd000000000000000000000001/cleaned.json').read_text())['ownedResourcesRemoved']
for check in checks:
    assert Path(check['evidence']).is_file()
    assert check['kind'] == 'diagnostic' or check['exitCode'] == 0
    for path in check.get('supportPaths', []): assert path in actual and Path(path).is_file()
for name in actual:
    path = Path(name)
    if path.suffix == '.png': continue
    data = path.read_bytes()
    assert b'\r' not in data and (path.suffix == '.xml' or not data or data.endswith(b'\n')), name
    assert all(line.rstrip() == line for line in data.splitlines()), name
    if path.suffix == '.json':
        value = json.loads(data)
        if isinstance(value, dict) and {'base64', 'sha256', 'bytes'} <= value.keys():
            raw = base64.b64decode(value['base64']); assert len(raw) == value['bytes'] and hashlib.sha256(raw).hexdigest() == value['sha256']
        if isinstance(value, dict) and 'outputSha256' in value:
            raw = value['output'].encode() if 'output' in value else base64.b64decode(value['outputBase64'])
            assert hashlib.sha256(raw).hexdigest() == value['outputSha256'], name
for path in (prefix / 'README.md',):
    text = path.read_text(); assert sum(line.startswith('```') for line in text.splitlines()) % 2 == 0
    for link in re.findall(r'\[[^\]]*\]\(([^)]+)\)', text):
        if '://' not in link and not link.startswith('#'): assert (path.parent / link.split('#')[0]).exists()
subprocess.run(['git', 'diff', '--check'], check=True)
assert set(report['changedPaths']) == actual
print('PASS retained 20 host cases, two-normal-APK composite journey and native accessibility/animation/rapid-pointer outputs')
print('PASS source/APK/image identities, lossless artifacts/JSON/docs/whitespace/exact changed paths and exact owned cleanup')
print('PASS report ready for mandatory scoped save/push; no repeated full suite/native journey/review/release claim')
