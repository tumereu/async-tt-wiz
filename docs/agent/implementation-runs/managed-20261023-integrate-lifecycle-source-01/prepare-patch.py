"""Produce the explicitly authorized immutable source-only import, without applying it."""
import hashlib
import json
from pathlib import Path
import subprocess

context = json.loads(Path('/tmp/opencode/managed-20261023-integrate-lifecycle-source-01/context.json').read_text())
root = Path('docs/agent/implementation-runs') / context['runId']
base = 'a4b444e192776f5d165fb9188fc6e526f697620e'
pin = 'e84f93557cd5093858e499af5eb747650d70b99f'
def git(*args):
    return subprocess.check_output(['git', *args])

assert git('rev-parse', 'HEAD').decode().strip() == context['startCommit']
assert git('rev-parse', '--abbrev-ref', 'HEAD').decode().strip() == context['branch']
assert str(Path.cwd()) == context['worktree']
assert not git('diff', '--cached', '--name-only').strip()
names = git('diff', '--name-only', base, pin).decode().splitlines()
prefixes = ['docs/agent/implementation-runs/managed-20261023-' + name for name in
            ('i12a-01', 'i12b-01', 'i27-01', 'i29-01', 'integrate-lifecycle-01', 'verify-lifecycle-01')]
def allowed(path):
    return path in context['ownedPaths'] or any(path == prefix + '.json' or path.startswith(prefix + '/') for prefix in prefixes)

paths = [path for path in names if allowed(path)]
assert not any(path.startswith('scripts/') or path == context['state'] for path in paths)
raw = git('diff', '--binary', '--full-index', base, pin, '--', *paths)
patch = root / 'lifecycle-source.patch'
patch.write_bytes(raw)
actual = [line.split(' b/', 1)[1] for line in raw.decode().splitlines() if line.startswith('diff --git ')]
assert actual == paths and all(allowed(path) for path in actual)
print(json.dumps({'strategy': 'allowlisted binary diff then explicit git apply --3way --index; no ancestry merge',
                  'startCommit': context['startCommit'], 'diffBase': base, 'sourcePin': pin,
                  'modelMetadata': context['serverCreatedMetadata']['model'], 'sessionID': context['sessionID'],
                  'resources': context['resourceBundle'], 'patchSha256': hashlib.sha256(raw).hexdigest(),
                  'patchPaths': actual, 'excludedPaths': [path for path in names if path not in paths]}, indent=2))
