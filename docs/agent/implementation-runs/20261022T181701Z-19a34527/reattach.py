"""Reattach the changed verified normal APK through the existing exact ownership guards."""
from pathlib import Path
import json
import subprocess
import sys

sys.path.insert(0, str(Path.cwd()))
from e2e import devices as d, device_routes as r, device_handoff as h

subprocess.run(['python3', str(Path(__file__).parent / 'offload.py')], check=True)
with d.lock():
    for n in (1, 2):
        value = d.load(Path('/tmp/opencode/atw-device-19a3452700000000000000000000000' + str(n)))
        r.unroute(value)
        result = h.prepare(value, '/tmp/opencode/atw-e2e-19a34527000000000000000000000001',
            str(Path('androidApp/build/outputs/apk/debug/androidApp-debug.apk').resolve()), 180)
        print(json.dumps(result, sort_keys=True))
