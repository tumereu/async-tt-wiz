"""Actual Android accessibility/animation assertions on the retained UI-created normal app."""
from pathlib import Path
import json
import subprocess
import sys
sys.path.insert(0, str(Path.cwd()))
from e2e import devices as d, device_session as s, device_routes as r

prefix = Path(__file__).parent
method = sys.argv[1] if len(sys.argv) == 2 else 'nativeAccessibilityFixedFeedbackAndNonActionableRowAnimation'
assert method in ('nativeAccessibilityFixedFeedbackAndNonActionableRowAnimation', 'rapidRepeatAndRemovalCannotRedirectCapturedTouch')
subprocess.run(['python3', str(prefix / 'offload.py')], check=True)
value = d.load(Path('/tmp/opencode/atw-device-802a24bd000000000000000000000001'))
with d.lock():
    s.close(value)
    serial = 'emulator-' + str(value['config']['consolePort'])
    d.check_windows(value, d.windows(value, 'snapshot')); d.check_mappings(value, serial)
    result = d.adb(value, '-s', serial, 'install', '-r', str(Path('androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk').resolve()), timeout=120)
    assert 'Success' in result.stdout
    output = d.adb(value, '-s', serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
        'org.asyncttwiz.android.PreparationInteractionTest#' + method, 'org.asyncttwiz.android.test/androidx.test.runner.AndroidJUnitRunner', timeout=300).stdout
    print(output)
    # Pull only these test-owned normal-app private captures, never credentials or account stores.
    files = d.adb(value, '-s', serial, 'shell', 'run-as', 'org.asyncttwiz.android', 'ls', 'files/preparation-interaction', check=False).stdout.splitlines()
    for name in files:
        if not name.endswith(('.txt', '.png')) or '/' in name: continue
        data = subprocess.run([value['config']['linuxAdb'], '-H', '127.0.0.1', '-P', str(value['config']['adbPort']), '-s', serial,
            'exec-out', 'run-as', 'org.asyncttwiz.android', 'cat', 'files/preparation-interaction/' + name], check=True, stdout=subprocess.PIPE).stdout
        # Semantics dumps contain trailing spaces; losslessly envelope text rather than normalize it.
        if name.endswith('.txt'):
            (prefix / ('interaction-' + name + '.json')).write_text(json.dumps(dict(text=data.decode()), indent=2) + '\n')
        else: (prefix / ('interaction-' + name)).write_bytes(data)
    r.invalidate(value)
    assert 'OK (1 test)' in output and 'FAILURES' not in output
    print('PASS actual Android production native accessibility, cancellation, fixed feedback and removed-row animation assertions')
