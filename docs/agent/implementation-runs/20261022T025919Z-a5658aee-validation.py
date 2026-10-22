"""Check the exact selected-parent/state/evidence/documentation boundary before save."""
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess

ROOT = Path(__file__).resolve().parents[3]
PREFIX = 'docs/agent/implementation-runs/20261022T025919Z-a5658aee'
REPORT = PREFIX + '.json'
STATE = 'docs/agent/implementation-state.json'


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True)


report = json.loads((ROOT / REPORT).read_text())
state = json.loads((ROOT / STATE).read_text())
baseline = json.loads(git('show', report['startCommit'] + ':' + STATE))
assert git('rev-parse', 'HEAD').strip() == report['startCommit']
assert report['mode'] == 'implement' and report['target'] == 'I01f1b2' and report['outcome'] == 'progress'
assert report['findingIds'] == report['newFindingIds'] == report['reviewedUnitIds'] == []
assert state['runs'] == baseline['runs'] + [REPORT] and state['activeRun'] is None
old, new = ({task['id']: task for task in value['tasks']} for value in (baseline, state))
assert old.keys() == new.keys()
for key in old:
    if key == report['target']:
        assert old[key]['status'] == 'in-progress' and new[key]['status'] == 'implemented'
        assert new[key]['implementationRecord'] == REPORT and new[key]['qualification']
        for field in ('id', 'batch', 'outcome', 'checks', 'dependsOn', 'children', 'planningRecord'):
            assert old[key][field] == new[key][field]
    else:
        assert old[key] == new[key], key
for key in state:
    if key not in ('tasks', 'runs'):
        assert state[key] == baseline[key], key
assert all(new[dep]['status'] == 'implemented' for dep in new[report['target']]['dependsOn'] + new[report['target']]['children'])
assert state['batches'][0]['status'] == 'pending' and not state['findings']
actual = set(git('diff', '--name-only', report['startCommit']).splitlines())
actual.update(git('ls-files', '--others', '--exclude-standard').splitlines())
assert actual | {PREFIX + '-validation.json'} == set(report['changedPaths']), actual ^ set(report['changedPaths'])
assert len(report['changedPaths']) == len(set(report['changedPaths']))
assert not git('diff', '--cached', '--name-only').strip(), 'Unexpected staged work'
assert not git('diff', '--check')
digests = {}
supports = set()
for check in report['checks']:
    assert check['evidence'].startswith(PREFIX + '-') and check['evidence'] in report['changedPaths']
    assert check['kind'] != 'required' or check['exitCode'] == 0
    for support in check.get('supportPaths', []):
        assert support.startswith(PREFIX + '-') and support in report['changedPaths'] and (ROOT / support).is_file()
        supports.add(support)
    if check['evidence'] == PREFIX + '-validation.json':
        continue
    path = ROOT / check['evidence']
    evidence = json.loads(path.read_text())
    assert evidence['exitCode'] == check['exitCode']
    assert shlex.split(check['command']) == evidence['command'], check['evidence']
    assert hashlib.sha256(evidence['output'].encode()).hexdigest() == evidence['outputSha256']
    digests[check['evidence']] = hashlib.sha256(path.read_bytes()).hexdigest()
assert supports == {path for path in actual if path.endswith('.py')}
for path in report['changedPaths']:
    if path.endswith('.md'):
        for link in re.findall(r'\]\(([^)]+)\)', (ROOT / path).read_text()):
            if '://' not in link and not link.startswith('#'):
                assert ((ROOT / path).parent / link.split('#')[0]).exists(), (path, link)
    assert not path.startswith('docs/design/02-experience/ui/'), 'UI publication obligation applies'
assert all(path.startswith(PREFIX) or path in {STATE, 'e2e/README.md', 'e2e/devices-README.md'} for path in actual)
assert 'Ran 134 tests' in json.loads((ROOT / (PREFIX + '-python.json')).read_text())['output']
assert 'Unrelated Windows listener changed' in json.loads((ROOT / (PREFIX + '-actual.json')).read_text())['output']
assert json.loads((ROOT / (PREFIX + '-actual-repeat.json')).read_text())['exitCode'] == 0
assert 'PASS exact automatic unroute/native guest' in json.loads((ROOT / (PREFIX + '-actual-repeat.json')).read_text())['output']
assert json.loads((ROOT / (PREFIX + '-storage.json')).read_text())['exitCode'] == 1
assert json.loads((ROOT / (PREFIX + '-storage-repeat.json')).read_text())['exitCode'] == 0
for path in actual:
    if path.endswith(('.py', '.md', '.json')):
        text = (ROOT / path).read_text()
        assert text.endswith('\n') and not any(line.rstrip() != line for line in text.splitlines()), path
print(json.dumps({'retainedEvidenceSha256': digests, 'changedPathCount': len(report['changedPaths'])}, indent=2))
print('PASS selected parent only; both children complete, all other tasks/reviews/findings/gates/model/grants/authorization unchanged; exact run-owned paths and lossless check output, required results pass, two diagnostics retained, documentation links/whitespace valid, activeRun null. No product/build/controller/test-source/UI packet changes. Save/push still required.')
