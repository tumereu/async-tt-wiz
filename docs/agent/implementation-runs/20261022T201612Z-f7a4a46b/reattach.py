"""Explicitly reattach the changed verified APK, retaining the controller's exact ownership guards."""
from pathlib import Path
import json
import subprocess
import sys

sys.path.insert(0, str(Path.cwd()))
from e2e import devices as d, device_routes as r, device_handoff as h

subprocess.run(['python3', str(Path(__file__).parent / 'offload.py')], check=True)
with d.lock():
    value = d.load(Path('/tmp/opencode/atw-device-f7a4a46b000000000000000000000001'))
    r.unroute(value)
    print(json.dumps(h.prepare(value, '/tmp/opencode/atw-e2e-f7a4a46b000000000000000000000001',
        str(Path('androidApp/build/outputs/apk/debug/androidApp-debug.apk').resolve()), 180), sort_keys=True))
