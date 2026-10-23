"""Attach only these exact owned devices/environment and the normal APK."""
from pathlib import Path
import subprocess
import sys
import time

prefix = Path(__file__).parent
subprocess.run(['python3', str(prefix / 'offload.py')], check=True)
for i in ([int(sys.argv[1])] if len(sys.argv) == 2 else (1, 2)):
    process = subprocess.Popen(['python3', 'scripts/e2e-devices.py', '--directory',
        '/tmp/opencode/atw-device-bc92c3f300000000000000000000000' + str(i), 'prepare-app', '--environment',
        '/tmp/opencode/atw-e2e-bc92c3f3000000000000000000000001', '--apk',
        str(Path('androidApp/build/outputs/apk/debug/androidApp-debug.apk').resolve())])
    while process.poll() is None:
        subprocess.run(['python3', str(prefix / 'offload.py'), '--completed-only'], check=True, stdout=subprocess.DEVNULL)
        time.sleep(1)
    assert process.returncode == 0
    subprocess.run(['python3', str(prefix / 'offload.py')], check=True)
