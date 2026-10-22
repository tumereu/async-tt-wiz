"""Small I03 evidence/handoff/documentation validation, not a new product audit."""
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PREFIX = 'docs/agent/implementation-runs/20261022T070751Z-df665f98'
START = '1151d680ad86a784013a6751da23364748088006'


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def load(path):
    return json.loads((ROOT / path).read_text())


def output(path):
    raw = (ROOT / path).read_bytes()
    try:
        envelope = json.loads(raw)
    except ValueError:
        return raw.decode()
    if isinstance(envelope, dict) and envelope.get('format') == 'lossless-utf8-output-v1':
        original = envelope['output'].encode()
        assert hashlib.sha256(original).hexdigest() == envelope['originalSha256']
        assert len(original) == envelope['originalBytes']
        return envelope['output']
    return raw.decode()


before = json.loads(git('show', START + ':docs/agent/implementation-state.json'))
state = load('docs/agent/implementation-state.json')
report = load(PREFIX + '.json')
driver = runpy.run_path(str(ROOT / 'scripts/implementation-loop.py'))
assert driver['selection'](before) == {'mode': 'implement', 'target': 'I03', 'findingIds': []}
driver['validate_state'](state)
driver['validate_transition'](before, state, {'mode': 'implement', 'target': 'I03', 'findingIds': []}, report)
assert state['activeRun'] is None and state['runs'] == before['runs'] + [PREFIX + '.json']
for old, new in zip(before['tasks'], state['tasks']):
    assert old == new if old['id'] != 'I03' else new['status'] == 'implemented'
assert all(before[key] == state[key] for key in before if key not in ('tasks', 'runs'))
assert driver['selection'](state) == {'mode': 'implement', 'target': 'I04', 'findingIds': []}
print('PASS authorized exact I03-only transition and next I04; B1/reviews/findings/model/runner/release gates unchanged')

paths = set(git('diff', '--name-only', START).decode().splitlines())
paths.update(git('ls-files', '--others', '--exclude-standard').decode().splitlines())
assert paths == set(report['changedPaths'])
assert not git('diff', '--cached', '--name-only').strip()
for path in paths:
    if path == PREFIX + '-validation.txt':
        continue # This command is still writing its own buffered output; checked after execution/save.
    raw = (ROOT / path).read_bytes()
    if path.endswith('.png'):
        assert raw.startswith(b'\x89PNG\r\n\x1a\n')
        continue
    text = raw.decode()
    assert text.endswith('\n') and all(line == line.rstrip() for line in text.splitlines()), path
    if path.endswith('.json'):
        json.loads(text)
    if path.startswith(PREFIX):
        output(path)
for check in report['checks']:
    assert check['kind'] != 'required' or check['exitCode'] == 0
    assert check['evidence'] in paths and (ROOT / check['evidence']).is_file()
    for helper in check.get('supportPaths', []):
        assert helper in paths and helper.startswith(PREFIX)
print('PASS exact owned-path set, byte-preserving envelopes, retained diagnostics and required output inventory')

for relative, count in [('profile/result-0.xml', 2), ('shell-passing-before-final-fullscreen-fix/result-0.xml', 1),
                        ('androidApp-TEST-org.asyncttwiz.android.AuthControllerTest.xml.json', 9),
                        ('client-storage-TEST-org.asyncttwiz.storage.ClientStorageTest.xml.json', 17),
                        ('final-presentation-TEST-emulator-5564 - 10-_androidApp-.xml.json', 1)]:
    tree = ET.fromstring(output(PREFIX + '/' + relative))
    cases = list(tree.iter('testcase'))
    assert len(cases) == count, (relative, len(cases))
    assert not any(list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped'))
    print('PASS retained non-skipped tests', relative, count)
for relative, identity in load(PREFIX + '/final-input-identities.json').items():
    raw = (ROOT / relative).read_bytes()
    assert len(raw) == identity['bytes'] and hashlib.sha256(raw).hexdigest() == identity['sha256'], relative
presentation = json.loads(output(PREFIX + '-presentation-api29-reset.txt'))
assert presentation['exitCode'] == 0 and len(presentation['presentationLog']) == 5
for relative in ('-affected-build.txt', '-final-logout-checks.txt'):
    assert 'BUILD SUCCESSFUL' in output(PREFIX + relative)
print('PASS exact final source/build/lock/APK identities; whole-journey screenshots retain their earlier candidate attribution')

coverage = runpy.run_path(str(ROOT / 'scripts/coverage-ledger.py'))['validate'](load('docs/agent/coverage/ledger.json'))
print('PASS unchanged source-pinned coverage assignments (not scenario passes)', coverage)
for relative in ('androidApp/README.md', 'client-storage/README.md'):
    text = (ROOT / relative).read_text()
    assert len(re.findall(r'^```', text, re.M)) % 2 == 0
    for target in re.findall(r'\]\(([^ )]+)\)', text):
        if '://' in target or target.startswith('#'):
            continue
        assert (ROOT / relative).parent.joinpath(target.split('#')[0]).exists(), (relative, target)
    print('PASS changed Markdown local links and balanced code fences', relative)
assert json.loads(output(PREFIX + '/device-cleaned.json'))['state']['cleaned']
assert not json.loads(output(PREFIX + '/device-cleaned.json'))['listeners']
subprocess.run(['git', 'diff', '--check'], cwd=ROOT, check=True)
print('PASS I03 handoff, affected evidence and documentation checks; no full-suite/clean-build/matrix rerun or later-scope pass')
