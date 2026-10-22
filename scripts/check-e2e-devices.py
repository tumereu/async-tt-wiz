#!/usr/bin/env python3
"""Actual device-service checkpoint: readiness, owned Appium loss, exact cleanup.

NOT the complete I01f1a failure matrix, APK/UI/Compose or instrumentation test.
Uses a fresh instance; all failed commands/diagnostics precede scoped cleanup.
"""
import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from e2e import devices as d


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', required=True)
    parser.add_argument('--directory', required=True)
    args = parser.parse_args()
    directory = d.directory_path(args.directory)
    config = d.validate_config(json.loads(Path(args.config).read_text()))
    require_new = not directory.exists()
    assert require_new, 'Fresh exclusive directory required'

    def cli(*arguments):
        command = [sys.executable, str(d.ROOT / 'scripts/e2e-devices.py'), '--directory', str(directory), *arguments]
        result = subprocess.run(command, cwd=d.ROOT, capture_output=True, text=True, timeout=420)
        print(json.dumps({'command': command, 'exitCode': result.returncode,
                          'stdout': result.stdout, 'stderr': result.stderr}), flush=True)
        return result

    try:
        assert cli('provision', '--config', args.config).returncode == 0
        value = d.load(directory)
        baseline = json.loads((directory / 'baseline-windows.json').read_text())
        assert (directory / 'ready.json').exists()
        assert cli('wait').returncode == 0
        assert cli('capture').returncode == 0
        identity = d.appium_identity(value)
        assert identity is not None and d.appium_listener(value)
        print('Controlled loss of exact owned Appium identity:', json.dumps(identity), flush=True)
        assert d.process_identity(identity['pid']) == identity
        os.kill(identity['pid'], signal.SIGTERM)
        until = time.monotonic() + 10
        while d.process_identity(identity['pid']) and time.monotonic() < until:
            time.sleep(.1)
        assert d.process_identity(identity['pid']) is None
        failed = cli('--timeout', '2', 'wait')
        assert failed.returncode != 0 and 'Owned Appium process is not alive' in failed.stderr
        assert not (directory / 'ready.json').exists()
        print('PASS: real service loss fails setup and invalidates ready handoff', flush=True)
        assert cli('cleanup').returncode == 0
        cleaned = json.loads((directory / 'cleaned.json').read_text())
        assert cleaned['state']['nativeWatchdogExitObserved']
        assert not any(cleaned['currentProcesses'])
        assert cleaned['listeners'] == []
        for key in ('adbPort', 'consolePort', 'appiumPort', 'systemPort'):
            assert not d.linux_listening(config[key])
        assert not d.linux_listening(config['consolePort'] + 1)
        after = d.windows(value, 'inventory')
        assert after['androidProcesses'] == baseline['androidProcesses'], 'Windows Android inventory changed'
        print('PASS: exact native guest/ADB/watchdog cleanup; unrelated Android inventory unchanged', flush=True)
        print('Windows baseline:', json.dumps(baseline), flush=True)
        print('Windows final:', json.dumps(after), flush=True)
        for line in (directory / 'launchers.jsonl').read_text().splitlines():
            original = json.loads(line)
            assert d.process_identity(original['pid']) != original
        print('PASS: no recorded WSL launcher identity survives cleanup', flush=True)
        print('LIMITATIONS: no same-port repeated lifecycle or complete Windows failure matrix; Windows mirrored-loopback broker residue is visible, not removed. No APK, Appium session, Compose routing or instrumentation executed.', flush=True)
    finally:
        if directory.exists() and not (directory / 'cleaned.json').exists():
            cli('capture')
            result = cli('cleanup')
            if result.returncode:
                print('ERROR: owned cleanup remains unresolved; inspect retained output', flush=True)


if __name__ == '__main__':
    main()
