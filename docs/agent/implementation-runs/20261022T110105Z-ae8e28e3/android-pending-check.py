"""Only the missing pending-load disposal assertion; does not repeat the green hosted journey."""
from pathlib import Path
import sys
sys.path.insert(0, str(Path.cwd()))
from e2e import devices as d, device_routes as r, device_session as s

value = d.load(Path('/tmp/opencode/atw-device-ae8e28e311014b3e99d382647521e8ff'))
serial = 'emulator-' + str(value['config']['consolePort'])
with d.lock():
    s.close(value)
    r.attached(value)
    d.check_device(value)
    d.adb(value, '-s', serial, 'shell', 'am', 'force-stop', 'org.asyncttwiz.android')
    normal = d.ROOT / 'androidApp/build/outputs/apk/debug/androidApp-debug.apk'
    result = d.adb(value, '-s', serial, 'install', '-r', str(normal), timeout=120)
    assert 'Success' in result.stdout
    apk = d.ROOT / 'androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk'
    result = d.adb(value, '-s', serial, 'install', '-r', str(apk), timeout=120)
    assert 'Success' in result.stdout
    output = d.adb(value, '-s', serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
        'org.asyncttwiz.android.ArtworkProductionTest#pendingFetchRebindAndDisposeNeverPublishesOldImage',
        'org.asyncttwiz.android.test/androidx.test.runner.AndroidJUnitRunner', timeout=120)
    print(output.stdout)
    assert 'OK (1 test)' in output.stdout
