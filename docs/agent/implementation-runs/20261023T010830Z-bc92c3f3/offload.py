"""Preserve only this run's redundant APK readbacks off bounded /tmp/opencode tmpfs."""
from pathlib import Path
import hashlib
import json
import shutil
import sys

prefix = Path(__file__).parent
archive = Path('e2e/build/bc92c3f3-apk-readbacks')
archive.mkdir(exist_ok=True)
records = []
for directory in Path('/tmp/opencode').glob('atw-device-bc92c3f300000000000000000000000[12]'):
    active = None
    if '--completed-only' in sys.argv and (directory / 'app-progress.json').exists():
        active = Path(json.loads((directory / 'app-progress.json').read_text())['evidenceDirectory'])
    for path in directory.glob('app-*/installed.apk'):
        if path.parent == active:
            continue
        assert path.is_file() and not path.is_symlink()
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        target = archive / (directory.name + '-' + path.parent.name + '.apk')
        assert not target.exists()
        shutil.move(path, target)
        assert hashlib.sha256(target.read_bytes()).hexdigest() == digest
        records.append(dict(original=str(path), archive=str(target), sha256=digest, bytes=target.stat().st_size))
path = prefix / 'offloaded-apks.json'
old = json.loads(path.read_text()) if path.exists() else []
path.write_text(json.dumps(old + records, indent=2) + '\n')
print(json.dumps(dict(preservedReadbacks=len(records), remaining=shutil.disk_usage('/tmp/opencode').free)))
