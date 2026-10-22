#!/usr/bin/env python3
"""Harness-only synthetic source fault/version change. Never mutates account/deck/game data."""
from pathlib import Path
import json
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from e2e import environment as e

directory, fault, edition = sys.argv[1:]
assert fault in ('ok', 'corrupt-gzip') and edition in ('1', '2')
value = e.load(Path(directory))
with e.locked(Path(directory)):
    state = e.admin(value, '/admin/control', {'instance': value['project'], 'sourceFault': fault,
                    'versions': {'prism': int(edition), 'quilt': 1}})
    print(json.dumps({'sourceFault': state['sourceFault'], 'versions': state['versions']}))
