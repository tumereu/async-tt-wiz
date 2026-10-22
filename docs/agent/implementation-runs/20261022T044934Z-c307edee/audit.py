"""This unit's small handoff/documentation/evidence check, not product recertification."""
from pathlib import Path
import hashlib
import json
import re
import runpy
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[4]
base = Path(__file__).resolve().parent
report_path = base.with_suffix('.json')
report = json.loads(report_path.read_text())
state = json.loads((root / 'docs/agent/implementation-state.json').read_text())
before = json.loads(subprocess.check_output(['git', 'show', report['startCommit'] + ':docs/agent/implementation-state.json'], cwd=root))
driver = runpy.run_path(str(root / 'scripts/implementation-loop.py'))
choice = {'mode': 'implement', 'target': 'I01f2', 'findingIds': []}
assert driver['selection'](before) == choice
driver['validate_state'](state)
driver['validate_transition'](before, state, choice, report)
assert state['activeRun'] is None and state['runs'][-1] == str(report_path.relative_to(root))
assert before['externalReleaseGates'] == state['externalReleaseGates']
print('PASS authorized selection/one-task transition/no review or release-gate advancement; next:', driver['selection'](state))

for directory, count in [('startup-runner-bcfab0f045374b6fa795766155a75820', 1),
                         ('storage-runner-3eb0bbd561c14c8abce03759397925de', 14)]:
    tests = []
    for file in (base / directory).glob('result-*'):
        raw = file.read_bytes()
        if file.suffix == '.json':
            envelope = json.loads(raw)
            raw = envelope['output'].encode('utf-8')
            assert hashlib.sha256(raw).hexdigest() == envelope['originalSha256']
            assert len(raw) == envelope['originalBytes']
        tests.extend(ET.fromstring(raw).iter('testcase'))
    assert len(tests) == count
    assert not any(list(node.iter(tag)) for node in tests for tag in ['error', 'failure', 'skipped'])
    assert json.loads((base / directory / 'exit.json').read_text())['exitCode'] == 0
    print('PASS retained actual non-skipped JUnit cases', directory, count)
assert (base / 'startup-runner-bcfab0f045374b6fa795766155a75820/startup.png').read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
assert json.loads((base / 'environment-cleaned.json').read_text())['ownedResourcesRemoved'] is True
fresh = json.loads((base / 'fresh-data-commands.json').read_text())
assert sum('clear' in c['command'] and c['output'].strip() == 'Success' for c in fresh) == 2
print('PASS actual startup PNG, both exact fresh-data clears and owned environment cleanup retained')

recorded = json.loads((base / 'input-identities.json').read_text())
changed = [path for path, digest in recorded.items() if hashlib.sha256((root / path).read_bytes()).hexdigest() != digest]
# Device tests/product/build/APK bytes are unchanged. This post-run boundary-only
# helper refinement has its fresh host regressions, not a falsely claimed new device run.
assert set(changed) <= {'e2e/device_runner.py'}, changed
print('PASS unchanged tested Kotlin/app/storage/APK inputs; later helper-only refinements:', changed)

for path in ['client-storage/README.md', 'e2e/README.md', 'e2e/devices-README.md']:
    file = root / path
    text = file.read_text()
    assert len(re.findall(r'^```', text, re.M)) % 2 == 0, path
    for target in re.findall(r'\[[^\]]*\]\(([^)]+)\)', text):
        if '://' not in target and not target.startswith('#'):
            assert (file.parent / target.split('#')[0]).exists(), (path, target)
    print('PASS documentation local links/fences', path)

for check in report['checks']:
    assert (root / check['evidence']).is_file(), check
    assert check['kind'] == 'diagnostic' or check['exitCode'] == 0
actual = set(subprocess.check_output(['git', 'diff', 'HEAD', '--name-only', '--diff-filter=ACMRT'], cwd=root, text=True).splitlines())
actual.update(subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard'], cwd=root, text=True).splitlines())
assert len(report['changedPaths']) == len(set(report['changedPaths']))
assert actual == set(report['changedPaths']), (actual - set(report['changedPaths']), set(report['changedPaths']) - actual)
for file in base.rglob('*.txt'):
    raw = file.read_bytes()
    if raw.startswith(b'{\n  "format": "lossless-utf8-output-v1"'):
        envelope = json.loads(raw)
        if envelope.get('format') == 'lossless-utf8-output-v1':
            restored = envelope['output'].encode('utf-8')
            assert hashlib.sha256(restored).hexdigest() == envelope['originalSha256']
            assert len(restored) == envelope['originalBytes']
print('PASS lossless UTF-8 output/XML envelopes preserve original byte lengths and SHA-256 without disabling whitespace checks')
subprocess.run(['git', 'diff', 'HEAD', '--check'], cwd=root, check=True)
print('PASS exact owned changed-path set, retained required/diagnostic evidence and whitespace')
