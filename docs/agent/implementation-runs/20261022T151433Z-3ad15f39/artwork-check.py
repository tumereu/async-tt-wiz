"""Focused actual Android production-loader size/lifecycle assertions, no scenario reset."""
from pathlib import Path
import hashlib
import json
import sys

sys.path.insert(0, str(Path.cwd()))
from e2e import devices as d, device_session as s, device_routes as r

with d.lock():
    value = d.load(Path('/tmp/opencode/atw-device-3ad15f39000000000000000000000001'))
    s.idle(value)
    ready = d.wait(value, 180)
    apk = Path('androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk')
    installed = d.adb(value, '-s', ready['device']['serial'], 'install', '-r', str(apk.resolve()), timeout=120)
    assert 'Success' in installed.stdout
    print('Normal companion APK SHA256', hashlib.sha256(apk.read_bytes()).hexdigest())
    try:
        result = d.adb(value, '-s', ready['device']['serial'], 'shell', 'am', 'instrument', '-w', '-r',
            '-e', 'class', 'org.asyncttwiz.android.ArtworkProductionTest',
            'org.asyncttwiz.android.test/androidx.test.runner.AndroidJUnitRunner', timeout=300)
        print(result.stdout)
        assert 'OK (2 tests)' in result.stdout and 'FAILURES' not in result.stdout
        print('PASS exact front/back scene-size bounds and actual hosted Coil/BitmapFactory/cache/rebind/dispose plus delayed HTTP lifecycle')
    finally:
        r.invalidate(value)
