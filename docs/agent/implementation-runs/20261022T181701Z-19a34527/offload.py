"""Move only this run's redundant APK readbacks off the bounded tmpfs, preserving bytes/hashes."""
from pathlib import Path
import hashlib
import json
import shutil

root = Path.cwd()
prefix = Path(__file__).parent
archive = root / 'e2e/build/19a34527-apk-readbacks'
archive.mkdir(exist_ok=True)
records = []
for n in (1, 2):
    directory = Path('/tmp/opencode/atw-device-19a3452700000000000000000000000' + str(n))
    for path in directory.glob('app-*/installed.apk'):
        assert path.is_file() and not path.is_symlink()
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        destination = archive / (str(n) + '-' + path.parent.name + '.apk')
        assert not destination.exists()
        shutil.move(path, destination)
        assert hashlib.sha256(destination.read_bytes()).hexdigest() == digest
        records.append(dict(original=str(path), archive=str(destination.relative_to(root)), sha256=digest,
            bytes=destination.stat().st_size))
path = prefix / 'offloaded-apks.json'
old = json.loads(path.read_text()) if path.exists() else []
path.write_text(json.dumps(old + records, indent=2) + '\n')
print('Preserved exact owned APK readbacks off tmpfs:', len(records))
