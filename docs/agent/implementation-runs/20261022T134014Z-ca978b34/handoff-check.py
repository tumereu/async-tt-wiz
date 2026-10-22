"""Small run-owned evidence/state/docs audit; no product suite or native journey rerun."""
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
choice = dict(mode='implement', target='I07a', findingIds=[])
assert git('rev-parse', 'HEAD') == report['startCommit'] and git('branch', '--show-current') == 'master'
assert not git('diff', '--cached', '--name-only')
assert before['authorization'] == 'authorized' and before['runner']['model'] == 'openai/gpt-6.1-sol#high'
assert api['selection'](before) == choice
api['validate_state'](after)
api['validate_transition'](before, after, choice, report)
old = {t['id']: t for t in before['tasks']}; new = {t['id']: t for t in after['tasks']}
assert new['I07a']['status'] == 'implemented' and new['I07a']['implementationRecord'] == record
assert all(new[key] == value for key, value in old.items() if key != 'I07a')
assert all(before[key] == after[key] for key in before if key not in ('tasks', 'runs'))
assert after['runs'] == before['runs'] + [record] and after['activeRun'] is None
assert api['selection'](after) == dict(mode='implement', target='I07b', findingIds=[])
print('PASS exact starting commit/branch/authorized high-model priority; only I07a advances; I07b next; parents/B2/review/release gates unchanged')

for check in report['checks']:
    path = Path(check['evidence'])
    if path.name == 'handoff-check.json': continue
    value = json.loads(path.read_text())
    assert value['exitCode'] == check['exitCode'], path
    assert check['kind'] == 'diagnostic' or check['exitCode'] == 0
    for support in check.get('supportPaths', []):
        assert Path(support).is_file() and support in report['changedPaths']

initial = {path: ET.fromstring(text) for path, text in json.loads((prefix / 'host-junit-initial.json').read_text()).items()}
passed = {}; failed = []
for path, tree in initial.items():
    for case in tree.iter('testcase'):
        key = (path, case.attrib['name'])
        if any(list(case.iter(tag)) for tag in ('failure', 'error', 'skipped')): failed.append(key)
        else: passed[key] = True
assert len(passed) == 38 and len(failed) == 1 and failed[0][1].startswith('owned list keyset pages')
rechecks = {}
for path, value in json.loads((prefix / 'host-junit-rechecks.json').read_text()).items():
    data = base64.b64decode(value['base64'])
    assert len(data) == value['bytes'] and hashlib.sha256(data).hexdigest() == value['sha256']
    tree = ET.fromstring(data)
    for case in tree.iter('testcase'):
        assert not any(list(case.iter(tag)) for tag in ('failure', 'error', 'skipped'))
        rechecks[(path, case.attrib['name'])] = True
assert failed[0] in rechecks and len(rechecks) == 11
print('PASS retained 38 green initial host/shared/PostgreSQL assertions, original one-case 503 diagnostic, exact passing pagination recheck and ten affected final controller/save host assertions; not a new whole-suite run')

native = prefix / 'runner-9ade44d7898042039fee9e8ef0bfba72-result-0.xml'
cases = list(ET.parse(native).iter('testcase'))
assert len(cases) == 1 and not any(list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped'))
segments = {
    'runner-fb49bddbc4ed4919bf440b5c32afdc36': ['detail-main-portrait', 'detail-sideboard-portrait', 'rename-landscape', 'cover-selection-portrait'],
    'runner-ad96446ce6b442ebac9908ca5b965d35': ['personal-draft-remote-rename'],
    'runner-17b87f777b414d7e942c9d3b22d7adc1': ['same-account-cover'],
    'runner-002ee3fe138d45ef9a3b85a234e215ae': ['delete-confirmation-portrait'],
    'runner-6b3a6667abfe4187adcc4478d9ad4745': ['long-collection-fixed-import', 'delete-confirmation-landscape'],
    'runner-9ade44d7898042039fee9e8ef0bfba72': ['deleted-collection-after-restart'],
}
for run, names in segments.items():
    for name in names:
        for suffix in ('.xml', '.png'): assert (prefix / (run + '-' + name + suffix)).is_file(), (run, name, suffix)
accepted = json.loads((prefix / 'accepted-state.json').read_text())
assert accepted['remainingSavedDeckNames'] == [f'Collection extra {i}' for i in range(1, 7)]
assert accepted['retainedDefinitions'] == 3 and accepted['remainingEntries'] == 18
for path, value in json.loads((prefix / 'artifact-identities.json').read_text()).items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == value['sha256'], path
print('PASS exact current inputs, composed diagnostic native core/cover/cancel/long-list/accepted-deletion evidence plus green persisted-deletion tail and read-only original-outcome corroboration; no wholly green original native run claimed')

for kind, suffix in (('device', '1'), ('device', '2'), ('e2e', '1')):
    assert Path('/tmp/opencode/atw-' + kind + '-ca978b3400000000000000000000000' + suffix + '/cleaned.json').is_file()
for path in (Path('server/DECKS.md'), prefix / 'README.md'):
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
    if Path(path).is_file() and Path(path).suffix in ('.kt', '.py', '.json', '.md', '.sql', '.java'):
        data = Path(path).read_bytes()
        assert b'\r' not in data and data.endswith(b'\n'), path
        assert all(line.rstrip() == line for line in data.splitlines()), path
print('PASS completed exact owned cleanup, docs links/fences/whitespace, unchanged coverage ledger, report output/support paths and selected role/changed-path boundaries')
print('NOT CLAIMED: I07b/I07/I08, selected-copy journeys, independent B2 review, final I34-I36/full-suite/UI/compatibility and external release gates')
