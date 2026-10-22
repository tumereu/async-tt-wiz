#!/usr/bin/env python3
"""Actual normal-APK startup on one owned API29 guest. NOT a full E2E scenario.

No Compose/route, Appium driver session, instrumentation or app-state injection.
Run sequentially with socket tests stopped so unrelated inventory is stable.
"""
import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from e2e import devices as d
from e2e import device_app as a


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', required=True)
    parser.add_argument('--config', required=True)
    parser.add_argument('--apk', required=True)
    parser.add_argument('--test-apk', required=True, help='Existing instrumentation APK, used ONLY to check package refusal')
    args = parser.parse_args()
    directory = d.directory_path(args.directory)
    assert not directory.exists(), 'Fresh device directory required'
    config = d.validate_config(json.loads(Path(args.config).read_text()))

    def cli(*arguments):
        command = [sys.executable, str(d.ROOT / 'scripts/e2e-devices.py'), '--directory', str(directory), *arguments]
        result = subprocess.run(command, cwd=d.ROOT, capture_output=True, text=True, timeout=480)
        print(json.dumps({'command': command, 'exitCode': result.returncode, 'stdout': result.stdout, 'stderr': result.stderr}), flush=True)
        return result

    def success(*arguments):
        result = cli(*arguments)
        assert result.returncode == 0, result.stderr
        return json.loads(result.stdout)

    def retained_storage(before, after):
        for key in ('inode', 'logicalSha256', 'userVersion', 'tables', 'rowCounts', 'bytes'):
            assert before['storage'][key] == after['storage'][key], 'Retained storage changed: ' + key
        # Actual reopen changes SQLite's change-counter/version-valid-for header
        # words, not schema/data. Keep raw hashes, but do not equate a header change
        # with destructive recreation. The file inode and full logical dump agree.
        left = (Path(before['evidenceDirectory']) / 'replica.db').read_bytes()
        right = (Path(after['evidenceDirectory']) / 'replica.db').read_bytes()
        differences = [i for i, (x, y) in enumerate(zip(left, right)) if x != y]
        assert all(i in range(24, 28) or i in range(92, 96) for i in differences), differences
        print('Retained SQLite raw hashes/header differences:', json.dumps({'before': before['storage'], 'after': after['storage'],
                                                                         'changedByteOffsets': differences}), flush=True)

    try:
        ready = success('provision', '--config', args.config)
        value = d.load(directory)
        serial = ready['device']['serial']
        baseline = json.loads((directory / 'baseline-windows.json').read_text())
        assert ready['device']['api'] == '29' and not ready['device']['gms']
        # New synthetic guest only. Do not adopt or replace a personal install.
        absent = d.adb(value, '-s', serial, 'shell', 'pm', 'path', a.PACKAGE, check=False)
        assert absent.returncode in (0, 1) and not absent.stdout.strip(), 'Fresh synthetic guest has an existing/unreadable app'
        installed = success('install-app', '--apk', str(Path(args.apk).resolve()))
        assert installed['apk']['package'] == a.PACKAGE and installed['apk']['activity'] == a.ACTIVITY
        assert installed['apk']['minSdk'] == 29 and installed['apk']['signerSha256']
        first = success('launch-app')
        assert first['storage']['userVersion'] == 2 and not any(first['storage']['rowCounts'].values())
        captured = success('capture-app')
        assert captured['foreground']['pid'] == first['foreground']['pid']
        retained_storage(first, captured)
        print('PASS: ordinary verified normal APK installed; production launcher, branded native UI, API29 non-GMS and empty real no-backup SQLite startup observed', flush=True)

        # A wrong-package APK cannot be installed or leave stale startup evidence.
        result = cli('install-app', '--apk', str(Path(args.test_apk).resolve()))
        assert result.returncode != 0 and 'Only the normal application package is allowed' in result.stderr
        assert not (directory / 'app-ready.json').exists() and not (directory / 'app-installed.json').exists()
        # Invalid bytes fail ordinary aapt validation, also before ADB installation.
        invalid = directory / 'invalid.apk'
        invalid.write_bytes(b'Controlled invalid APK, never a test application')
        result = cli('install-app', '--apk', str(invalid))
        assert result.returncode != 0 and 'Device/tool command failed' in result.stderr
        assert not (directory / 'app-ready.json').exists() and not (directory / 'app-installed.json').exists()
        success('install-app', '--apk', str(Path(args.apk).resolve()))
        retained = success('launch-app')
        retained_storage(first, retained)
        print('PASS: real wrong-package instrumentation artifact and invalid APK refused, no stale install/startup handoff; explicit normal reinstall retains data', flush=True)

        # App is genuinely absent from foreground; capture must not bless its old
        # screenshot. This does not modify DB/schema or manufacture product failure.
        d.adb(value, '-s', serial, 'shell', 'am', 'force-stop', a.PACKAGE)
        result = cli('--timeout', '1', 'capture-app')
        assert result.returncode != 0 and 'startup observation timed out' in result.stderr
        assert not (directory / 'app-ready.json').exists()
        relaunched = success('launch-app')
        assert relaunched['foreground']['pid'] != retained['foreground']['pid']
        retained_storage(first, relaunched)
        assert relaunched['apk'] == first['apk']
        print('PASS: stopped-app capture fails bounded observation and removes stale handoff; retained force-stop/relaunch opens unchanged real storage without clear/reset', flush=True)
        success('capture')
        print('Actual per-device startup evidence:', json.dumps(relaunched), flush=True)
    finally:
        if directory.exists() and not (directory / 'cleaned.json').exists():
            capture = cli('capture')
            result = cli('cleanup')
            if (directory / 'commands.jsonl').exists():
                print('Actual command log:', (directory / 'commands.jsonl').read_text(), flush=True)
            assert capture.returncode == 0 and result.returncode == 0, 'Owned diagnostic/cleanup failure remains unresolved'
    cleaned = json.loads((directory / 'cleaned.json').read_text())
    assert cleaned['state']['nativeWatchdogExitObserved']
    assert not any(cleaned['currentProcesses']) and not cleaned['listeners']
    assert not (directory / 'app-ready.json').exists() and not (directory / 'ready.json').exists()
    for key in ('adbPort', 'consolePort', 'appiumPort', 'systemPort'):
        assert not d.linux_listening(config[key])
    assert not d.linux_listening(config['consolePort'] + 1)
    after = d.windows(value, 'inventory')
    print('AMBIENT WINDOWS INVENTORY DELTA (not causal evidence)',
          json.dumps(d.inventory_changes(baseline, after)), flush=True)
    for line in (directory / 'launchers.jsonl').read_text().splitlines():
        identity = json.loads(line)
        assert d.process_identity(identity['pid']) != identity
    print('Windows baseline:', json.dumps(baseline), flush=True)
    print('Windows final:', json.dumps(after), flush=True)
    print('Actual native cleanup:', json.dumps(cleaned), flush=True)
    # This synthetic guest has no accounts or user content. Retain actual textual
    # tool/ADB results, but binary APK/DB/pixels are hashes/private artifacts.
    print('PASS: exact native guest/ADB/Appium/watchdog/launcher/port cleanup; no mappings created, ambient inventory retained for diagnosis', flush=True)
    print('LIMITATIONS: Startup-only scaffold observation, not full-stack acceptance. No Appium session/Kotlin scenario/instrumentation, Compose routing/backend connection, account/downloader/gameplay/Canvas or release qualification.', flush=True)


if __name__ == '__main__':
    main()
