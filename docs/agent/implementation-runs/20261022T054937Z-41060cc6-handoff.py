"""Small final owned-evidence/state/document audit, not another application test run."""
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
RUN = '20261022T054937Z-41060cc6'
BASE = 'docs/agent/implementation-runs/' + RUN
def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT).decode()

before = json.loads(git('show', 'db9e1f9:docs/agent/implementation-state.json'))
after = json.loads((ROOT / 'docs/agent/implementation-state.json').read_text())
report = json.loads((ROOT / (BASE + '.json')).read_text())
spec = importlib.util.spec_from_file_location('loop', ROOT / 'scripts/implementation-loop.py')
loop = importlib.util.module_from_spec(spec); spec.loader.exec_module(loop)
loop.validate_state(after)
assert loop.selection(before)['target'] == 'I02b'
loop.validate_transition(before, after, {'mode': 'implement', 'target': 'I02b', 'findingIds': []}, report)
assert before['authorization'] == after['authorization'] == 'authorized'
assert before['runner'] == after['runner'] and before['externalReleaseGates'] == after['externalReleaseGates']
assert after['activeRun'] is None
assert next(t for t in after['tasks'] if t['id'] == 'I02b')['status'] == 'implemented'
assert next(t for t in after['tasks'] if t['id'] == 'I02')['status'] == 'in-progress'
print('PASS existing driver state/selection/transition validation: only I02b implemented, B1 review/later obligations preserved')
for check in report['checks']:
    path = ROOT / check['evidence']
    assert path.is_file(), path
    if check['kind'] == 'required':
        assert check['exitCode'] == 0
    if path.name.endswith('-handoff.txt') or path.name.endswith('-retention.txt'):
        continue
    text = path.read_bytes().decode()
    try:
        envelope = json.loads(text)
    except json.JSONDecodeError:
        envelope = None
    if isinstance(envelope, dict) and 'outputSha256' in envelope:
        assert hashlib.sha256(envelope['output'].encode()).hexdigest() == envelope['outputSha256']
print('PASS all reported required checks are exit 0 and retained; diagnostic failures and lossless output hashes remain explicit')
identities = json.loads((ROOT / BASE / 'input-identities.json').read_text())
for relative, value in identities.items():
    assert hashlib.sha256((ROOT / relative).read_bytes()).hexdigest() == value['sha256'], relative
print('PASS exact affected source/build/lock/shared-protocol/image/APK identities still match retained checks')
saved = json.loads((ROOT / (BASE + '-AuthSavedStateTest-xml.json')).read_text())
cases = [c for xml in saved.values() for c in ET.fromstring(xml).iter('testcase')]
assert len(cases) == 1 and not any(list(c.iter(tag)) for c in cases for tag in ('failure', 'error', 'skipped'))
auth = json.loads((ROOT / (BASE + '-auth-results.json')).read_text())
green = {c.attrib['classname'] + '.' + c.attrib['name'] for xml in auth.values() for c in ET.fromstring(xml).iter('testcase')
         if not any(list(c.iter(tag)) for tag in ('failure', 'error', 'skipped'))}
assert len(green) == 5, green
print('PASS five distinct startup/Keystore/exact-input tests plus independent saved-state assertion have actual green device XML, without relabeling the red initial command')
for relative in ('androidApp/README.md',):
    path = ROOT / relative; text = path.read_text()
    assert text.count('```') % 2 == 0
    for link in re.findall(r'\]\(([^)]+)\)', text):
        if '://' not in link:
            assert (path.parent / link.split('#')[0]).resolve().exists(), link
print('PASS changed documentation links and code fences; no UI-document edit/publication is involved')
actual = set(git('diff', 'HEAD', '--name-only').splitlines()) | set(git('ls-files', '--others', '--exclude-standard').splitlines())
assert actual == set(report['changedPaths']), actual ^ set(report['changedPaths'])
assert set(git('diff', '--cached', '--name-only').splitlines()) <= set(report['changedPaths']), 'Unrelated staged work refused'
subprocess.run(['git', 'diff', '--check'], cwd=ROOT, check=True)
subprocess.run(['git', 'diff', '--cached', '--check'], cwd=ROOT, check=True)
for relative in report['changedPaths']:
    file = ROOT / relative
    if file.suffix in ('.kt', '.kts', '.py', '.md', '.json', '.xml', '.txt', '.toml'):
        assert not any(line.rstrip(' \t') != line for line in file.read_text().splitlines()), relative
print('PASS exact owned changedPaths/index/whitespace audit; ready for scoped save/push')
