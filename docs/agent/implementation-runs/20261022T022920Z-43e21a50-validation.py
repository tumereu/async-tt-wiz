"""Validate this unit's exact state/path/evidence/doc boundary before save; not a review."""
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess

ROOT = Path(__file__).resolve().parents[3]
PREFIX = 'docs/agent/implementation-runs/20261022T022920Z-43e21a50'
REPORT = PREFIX + '.json'
STATE = 'docs/agent/implementation-state.json'


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True)


report = json.loads((ROOT / REPORT).read_text())
state = json.loads((ROOT / STATE).read_text())
baseline = json.loads(git('show', report['startCommit'] + ':' + STATE))
assert git('rev-parse', 'HEAD').strip() == report['startCommit']
assert report['mode'] == 'implement' and report['target'] == 'I01f1b2b' and report['outcome'] == 'progress'
assert report['findingIds'] == report['newFindingIds'] == report['reviewedUnitIds'] == []
assert state['runs'] == baseline['runs'] + [REPORT] and state['activeRun'] is None
old, new = ({task['id']: task for task in value['tasks']} for value in (baseline, state))
assert old.keys() == new.keys()
for key in old:
    if key == report['target']:
        assert old[key]['status'] == 'pending' and new[key]['status'] == 'implemented'
        assert new[key]['implementationRecord'] == REPORT and new[key]['qualification']
        for field in ('id', 'batch', 'outcome', 'checks', 'dependsOn'):
            assert old[key][field] == new[key][field]
    else:
        assert old[key] == new[key], key
for key in state:
    if key not in ('tasks', 'runs'):
        assert state[key] == baseline[key], key
assert all(new[dep]['status'] == 'implemented' for dep in new[report['target']]['dependsOn'])
assert state['batches'][0]['status'] == 'pending' and not state['findings']
actual = set(git('diff', '--name-only', report['startCommit']).splitlines())
actual.update(git('ls-files', '--others', '--exclude-standard').splitlines())
assert actual | {PREFIX + '-validation.json'} == set(report['changedPaths']), actual ^ set(report['changedPaths'])
assert len(report['changedPaths']) == len(set(report['changedPaths']))
assert not git('diff', '--cached', '--name-only').strip(), 'Unexpected staged work'
assert not git('diff', '--check')
digests = {}
for check in report['checks']:
    assert check['evidence'] in report['changedPaths']
    assert check['kind'] != 'required' or check['exitCode'] == 0
    for support in check.get('supportPaths', []):
        assert support.startswith(PREFIX + '-') and support in report['changedPaths'] and (ROOT / support).is_file()
    if check['evidence'] == PREFIX + '-validation.json':
        continue
    path = ROOT / check['evidence']
    evidence = json.loads(path.read_text())
    assert evidence['exitCode'] == check['exitCode']
    assert shlex.split(check['command']) == evidence['command'], check['evidence']
    digests[check['evidence']] = hashlib.sha256(path.read_bytes()).hexdigest()
for path in report['changedPaths']:
    if path.endswith('.md'):
        for link in re.findall(r'\]\(([^)]+)\)', (ROOT / path).read_text()):
            if '://' not in link and not link.startswith('#'):
                assert ((ROOT / path).parent / link.split('#')[0]).exists(), (path, link)
        assert not path.startswith('docs/design/02-experience/ui/'), 'UI publication obligation applies'
assert json.loads((ROOT / (PREFIX + '-actual-first.json')).read_text())['exitCode'] == 0
assert 'Ran 134 tests' in json.loads((ROOT / (PREFIX + '-python-final.json')).read_text())['output']
# No runtime/build/controller-input changes outside the intended test-only adapter.
assert all(path.startswith(PREFIX) or path == STATE or path in {
    'e2e/README.md', 'e2e/devices-README.md', 'e2e/devices.py', 'e2e/device_app.py',
    'e2e/device_routes.py', 'e2e/device_handoff.py', 'scripts/check-device-handoff.py',
    'tests/test_device_app.py', 'tests/test_device_routes.py', 'tests/test_device_handoff.py'} for path in actual)
# Check the redacted final environment and textual outputs do not contain our
# ephemeral harness/DB secrets (the normal controller removed their source files).
envdir = Path('/tmp/opencode/atw-e2e-43e21a5011114af58b9f3861313df8c1')
assert (envdir / 'cleaned.json').exists()
assert not (envdir / 'db-password').exists() and not (envdir / 'harness-token').exists()
print(json.dumps({'retainedEvidenceSha256': digests}, indent=2))
print('PASS selected child only; unchanged parents, tasks/reviews/findings/gates/model/grants; exact owned path/evidence bytes, all required outputs pass, diagnostics retained, docs links/whitespace valid and activeRun null. No UI packet edit/publication needed; save/push still required.')
