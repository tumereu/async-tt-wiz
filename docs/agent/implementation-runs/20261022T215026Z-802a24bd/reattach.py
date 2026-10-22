"""Explicit changed-APK attachment through unchanged combined ownership/route guards."""
from pathlib import Path
import json
import subprocess
import sys
sys.path.insert(0, str(Path.cwd()))
from e2e import devices as d, device_routes as r, device_handoff as h

for i in (1, 2):
    subprocess.run(['python3', str(Path(__file__).parent / 'offload.py')], check=True)
    with d.lock():
        value = d.load(Path('/tmp/opencode/atw-device-802a24bd00000000000000000000000' + str(i)))
        r.unroute(value)
        print(json.dumps(h.prepare(value, '/tmp/opencode/atw-e2e-802a24bd000000000000000000000001',
            str(Path('androidApp/build/outputs/apk/debug/androidApp-debug.apk').resolve()), 180)))
