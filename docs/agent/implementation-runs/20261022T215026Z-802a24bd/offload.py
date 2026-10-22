"""Preserve this run's redundant APK readbacks off the bounded shared tmpfs."""
from pathlib import Path
import hashlib
import json
import shutil

prefix = Path(__file__).parent
archive = Path('e2e/build/802a24bd-apk-readbacks')
archive.mkdir(exist_ok=True)
records = []
before = shutil.disk_usage('/tmp/opencode')._asdict()
for directory in Path('/tmp/opencode').glob('atw-device-802a24bd00000000000000000000000[12]'):
    for path in directory.glob('app-*/installed.apk'):
        assert path.is_file() and not path.is_symlink()
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        destination = archive / (directory.name + '-' + path.parent.name + '.apk')
        assert not destination.exists()
        shutil.move(path, destination)
        assert hashlib.sha256(destination.read_bytes()).hexdigest() == digest
        records.append(dict(original=str(path), archive=str(destination), sha256=digest, bytes=destination.stat().st_size))
path = prefix / 'offloaded-apks.json'
old = json.loads(path.read_text()) if path.exists() else []
path.write_text(json.dumps(old + records, indent=2) + '\n')
print(json.dumps(dict(preservedReadbacks=len(records), before=before, after=shutil.disk_usage('/tmp/opencode')._asdict())))
