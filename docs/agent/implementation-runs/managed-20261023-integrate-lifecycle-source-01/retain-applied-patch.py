"""Losslessly retain the applied patch and use an exact zero-context diff for text-safe Git storage."""
import base64
import hashlib
import json
from pathlib import Path
import subprocess

root = Path(__file__).parent
patch = root / 'lifecycle-source.patch'
original = patch.read_bytes()
record = json.loads((root / 'patch-allowlist.json').read_text())
manifest = json.loads(record['output'])
assert hashlib.sha256(original).hexdigest() == manifest['patchSha256']
(root / 'applied-patch-bytes.json').write_text(json.dumps({
    'meaning': 'Exact bytes at lifecycle-source.patch when the authorized three-way apply ran; lossless encoding avoids diff context whitespace in committed text.',
    'sha256': hashlib.sha256(original).hexdigest(), 'base64': base64.b64encode(original).decode()
}, indent=2) + '\n')
command = ['git', 'diff', '--binary', '--full-index', '--unified=0', manifest['diffBase'], manifest['sourcePin'], '--', *manifest['patchPaths']]
raw = subprocess.check_output(command)
actual = [line.split(' b/', 1)[1] for line in raw.decode().splitlines() if line.startswith('diff --git ')]
assert actual == manifest['patchPaths']
patch.write_bytes(raw)
print(json.dumps({'command': command, 'storedPatchSha256': hashlib.sha256(raw).hexdigest(),
                  'appliedPatchSha256': manifest['patchSha256'], 'sameImmutableDiffAndPaths': True,
                  'reason': 'Save rejected space-only unchanged context lines, not product whitespace. Original applied bytes retained losslessly; zero-context diff is not reapplied or claimed separately tested.'}, indent=2))
