"""Final state, documentation, evidence and owned-path validation, not a review."""
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess

ROOT = Path(__file__).resolve().parents[3]
PREFIX = 'docs/agent/implementation-runs/20261022T013255Z-043555f3'
REPORT = PREFIX + '.json'
STATE = 'docs/agent/implementation-state.json'


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True)


report = json.loads((ROOT / REPORT).read_text())
state = json.loads((ROOT / STATE).read_text())
baseline = json.loads(git('show', report['startCommit'] + ':' + STATE))
assert git('rev-parse', 'HEAD').strip() == report['startCommit']
assert report['mode'] == 'implement' and report['target'] == 'I01f1b2a' and report['outcome'] == 'progress'
assert report['findingIds'] == report['newFindingIds'] == report['reviewedUnitIds'] == []
assert state['runs'] == baseline['runs'] + [REPORT] and state['activeRun'] is None
old = {t['id']: t for t in baseline['tasks']}
new = {t['id']: t for t in state['tasks']}
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
    if key not in ('runs', 'tasks'):
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
        continue  # Recorded byte-for-byte by the caller after this successful exit.
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
        assert not path.startswith('docs/design/02-experience/ui/'), 'UI publication obligation would apply'
assert json.loads((ROOT / (PREFIX + '-actual-routes-pass.json')).read_text())['exitCode'] == 0
assert json.loads((ROOT / (PREFIX + '-services-regression-final.json')).read_text())['exitCode'] == 0
assert 'Ran 121 tests' in json.loads((ROOT / (PREFIX + '-python-regressions-final.json')).read_text())['output']
print(json.dumps({'retainedEvidenceSha256': digests}, indent=2))
print('PASS selected child only; exact owned path set, unchanged gates/model/grants/parents/reviews, all required outputs genuinely pass, diagnostics retained, local documentation links and whitespace valid; activeRun null. UI publication not applicable. Save/push still required.')
