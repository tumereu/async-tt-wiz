"""Private host artifacts, not device state. Never migrate or prune old evidence.

ATW_DEVICE_ARTIFACT_ROOT may select a directory inside this worktree's e2e/build
or /tmp/opencode only; it does not grant access to another external location.
The default keeps repeated APK readbacks off the host's bounded /tmp tmpfs.
"""
import json
import os
from pathlib import Path
import re

from e2e import devices as d


def directory(value):
    build = d.ROOT / 'e2e/build'
    root = Path(os.environ.get('ATW_DEVICE_ARTIFACT_ROOT', str(build / 'device-artifacts')))
    d.require(root.is_absolute() and root.resolve() == root and
              any(root.is_relative_to(base) and root != base for base in (build, d.BASE)),
              'Artifact root must be a non-symlink directory within e2e/build or /tmp/opencode')
    d.require(re.fullmatch(r'[a-f0-9]{64}', value['owner']), 'Explicit artifact owner required')
    root.mkdir(mode=0o700, parents=True, exist_ok=True)
    d.require(root.is_dir() and root.stat().st_uid == os.getuid(), 'Owned artifact root required')
    path = root / ('device-' + value['owner'])
    identity = {'owner': value['owner'], 'deviceDirectory': value['directory']}
    if not path.exists():
        path.mkdir(mode=0o700)
        with (path / 'owner.json').open('x') as record:
            json.dump(identity, record)
    d.require(not path.is_symlink() and path.is_dir() and path.stat().st_uid == os.getuid() and
              path.stat().st_mode & 0o077 == 0, 'Private owned artifact directory required')
    record = path / 'owner.json'
    d.require(record.is_file() and not record.is_symlink() and json.loads(record.read_text()) == identity,
              'Foreign artifact ownership record refused')
    return path
