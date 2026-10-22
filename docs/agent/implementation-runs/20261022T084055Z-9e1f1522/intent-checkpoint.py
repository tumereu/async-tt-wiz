"""Targeted repair recheck in the retained failed scenario; no reset/new account or password mutation."""
from pathlib import Path
import sys
sys.path.insert(0, str(Path.cwd()))
from e2e import devices as d, device_session as s, reset_runner

value = d.load(Path('/tmp/opencode/atw-device-9e1f152211114af58b9f3861313df8c1'))
serial = 'emulator-5564'
d.check_windows(value, d.windows(value, 'snapshot'))
s.idle(value)
link = reset_runner.observe(value)
apk = d.ROOT / 'androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk'
d.adb(value, '-s', serial, 'install', '-r', str(apk), timeout=120)
result = d.adb(value, '-s', serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'resetLink', "'" + link + "'",
               '-e', 'class', 'org.asyncttwiz.android.ResetIntentTest',
               'org.asyncttwiz.android.test/androidx.test.runner.AndroidJUnitRunner', timeout=180)
assert link not in result.stdout and link.split('token=')[1] not in result.stdout
print(result.stdout)
assert 'OK (1 test)' in result.stdout
print('PASS retained real-email-link Android exact input, duplicate warm callback and saved-state checkpoint; no mutation or new scenario')
