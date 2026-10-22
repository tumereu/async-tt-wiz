#!/usr/bin/env python3
"""Actual owned device-service lifecycle, refusal and recovery qualification.

NOT APK/UI/Compose or instrumentation acceptance.
Uses a fresh instance; all failed commands/diagnostics precede scoped cleanup.
"""
import argparse
import base64
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time
import uuid

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

    def cli(*arguments, target=None):
        command = [sys.executable, str(d.ROOT / 'scripts/e2e-devices.py'), '--directory', str(target or directory), *arguments]
        result = subprocess.run(command, cwd=d.ROOT, capture_output=True, text=True, timeout=420)
        print(json.dumps({'command': command, 'exitCode': result.returncode,
                          'stdout': result.stdout, 'stderr': result.stderr}), flush=True)
        return result

    def fresh():
        return d.BASE / ('atw-device-' + uuid.uuid4().hex)

    def refused(result, message):
        assert result.returncode != 0 and message in result.stderr, result

    def state_script(body):
        # Check-only mutation of this run's exact synthetic ownership file. No
        # production/controller fault endpoints or broad process operations.
        source = "$ErrorActionPreference='Stop'; $file=Join-Path $env:LOCALAPPDATA 'async-tt-wiz-device-controller\\" + directory.name + "\\state.json'; " + body
        result = subprocess.run([config['powershell'], '-NoProfile', '-NonInteractive', '-EncodedCommand',
                                 base64.b64encode(source.encode('utf-16le')).decode()],
                                capture_output=True, text=True, timeout=20)
        print(json.dumps({'checkOnlyWindowsStateScript': body, 'exitCode': result.returncode,
                          'stdout': result.stdout, 'stderr': result.stderr}), flush=True)
        assert result.returncode == 0
        return result.stdout.strip()

    try:
        assert cli('provision', '--config', args.config).returncode == 0
        value = d.load(directory)
        baseline = json.loads((directory / 'baseline-windows.json').read_text())
        assert (directory / 'ready.json').exists()
        assert cli('wait').returncode == 0
        assert cli('capture').returncode == 0
        ready_before = (directory / 'ready.json').read_bytes()
        refused(cli('provision', '--config', args.config), 'Existing device instance refused')
        assert (directory / 'ready.json').read_bytes() == ready_before
        with d.lock():
            refused(cli('wait'), 'Another device controller is active')
        assert (directory / 'ready.json').read_bytes() == ready_before
        # Another target must neither adopt nor stop these genuinely live ports.
        collision = fresh()
        refused(cli('provision', '--config', args.config, target=collision), 'Busy Windows port refused')
        assert not (collision / 'ready.json').exists()
        assert cli('cleanup', target=collision).returncode == 0
        assert cli('wait').returncode == 0
        print('PASS: existing-instance, live foreign Windows/ADB/Appium ports and active lock refused without adoption', flush=True)

        # Both mapping directions are real ADB state, not mocked responses. Only
        # this checker creates/removes these exact temporary routes on its guest.
        serial = 'emulator-' + str(config['consolePort'])
        for direction in ('forward', 'reverse'):
            port = config['systemPort'] + 10
            route = 'tcp:' + str(port)
            prefix = ('-s', serial, direction)
            d.adb(value, *prefix, '--no-rebind', route, 'tcp:19999')
            try:
                before = d.adb(value, *prefix, '--list').stdout
                assert route in before
                refused(cli('wait'), 'Foreign/unrecorded ADB mapping refused')
                assert not (directory / 'ready.json').exists()
                refused(cli('cleanup'), 'Foreign/unrecorded ADB mapping refused')
                assert d.adb(value, *prefix, '--list').stdout == before
                assert d.appium_identity(value) is not None
            finally:
                d.adb(value, *prefix, '--remove', route)
            assert cli('wait').returncode == 0
        print('PASS: actual forward/reverse mapping refusal invalidates readiness and cleanup leaves foreign routes/services intact', flush=True)

        # Simulate stale/reused PID metadata, preserving the exact original bytes.
        saved = state_script('[Convert]::ToBase64String([IO.File]::ReadAllBytes($file))')
        try:
            state_script("$s=Get-Content -Raw $file | ConvertFrom-Json; $s.processes[0].created='stale-creation-identity'; $s | ConvertTo-Json -Depth 12 | Set-Content -Encoding UTF8 $file")
            refused(cli('wait'), 'Reused or foreign Windows PID refused')
            refused(cli('cleanup'), 'Reused or foreign Windows PID refused')
            assert d.appium_identity(value) is not None
        finally:
            state_script("[IO.File]::WriteAllBytes($file,[Convert]::FromBase64String('" + saved + "'))")
        assert cli('wait').returncode == 0
        print('PASS: reused Windows PID identity refused before any shutdown; exact original ownership restored', flush=True)
        identity = d.appium_identity(value)
        assert identity is not None
        os.kill(identity['pid'], signal.SIGSTOP)
        try:
            failed = cli('--timeout', '1', 'wait')
            refused(failed, 'Bounded device/Appium readiness timeout')
            assert not (directory / 'ready.json').exists()
        finally:
            assert d.process_identity(identity['pid']) == identity
            os.kill(identity['pid'], signal.SIGCONT)
        assert cli('wait').returncode == 0
        print('PASS: actual nonresponsive owned Appium times out, invalidates handoff and resumes without data reset', flush=True)
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
        # Logical reservations outlive a crashed caller even when its resources
        # have already stopped. No launch is allowed until explicit cleanup.
        unfinished = fresh()
        unfinished.mkdir(mode=0o700)
        reservation = dict(value, directory=str(unfinished), owner=uuid.uuid4().hex * 2)
        d.write_json(unfinished / 'instance.json', reservation)
        candidate = fresh()
        refused(cli('provision', '--config', args.config, target=candidate), 'Ports reserved by an unfinished device instance')
        assert cli('cleanup', target=candidate).returncode == 0
        assert cli('cleanup', target=unfinished).returncode == 0
        print('PASS: unfinished-instance logical reservations fail closed and explicit no-resource cleanup recovers', flush=True)
        # Interrupt the Linux caller while its separately detached Windows start
        # continues. Pause after the helper launch is observable, before Python
        # can advance to starting Appium; never kill the guest/Windows launcher.
        interrupted = fresh()
        command = [sys.executable, str(d.ROOT / 'scripts/e2e-devices.py'), '--directory', str(interrupted),
                   'provision', '--config', args.config]
        out = directory / 'interrupted-caller.log'
        with out.open('w') as stream:
            caller = subprocess.Popen(command, stdout=stream, stderr=stream, start_new_session=True)
            stopped = False
            try:
                deadline = time.monotonic() + 40
                while time.monotonic() < deadline and not (interrupted / 'windows-start.out').exists():
                    assert caller.poll() is None
                    time.sleep(.01)
                assert (interrupted / 'windows-start.out').exists(), 'Windows launch was not reached'
                os.kill(caller.pid, signal.SIGSTOP)
                stopped = True
                deadline = time.monotonic() + 90
                while time.monotonic() < deadline:
                    try:
                        launched = json.loads((interrupted / 'windows-start.out').read_text(encoding='utf-8-sig'))
                        break
                    except (ValueError, OSError):
                        time.sleep(.1)
                else:
                    raise AssertionError('Detached Windows helper did not finish')
                assert len([p for p in launched['currentProcesses'] if p]) >= 3, launched
                os.kill(caller.pid, signal.SIGTERM)
                os.kill(caller.pid, signal.SIGCONT)
                stopped = False
                assert caller.wait(timeout=10) == -signal.SIGTERM
                assert not (interrupted / 'appium.json').exists()
                assert not (interrupted / 'ready.json').exists()
            finally:
                if caller.poll() is None:
                    caller.terminate()
                    if stopped:
                        os.kill(caller.pid, signal.SIGCONT)
                    caller.wait(timeout=10)
                assert cli('capture', target=interrupted).returncode == 0
                assert cli('cleanup', target=interrupted).returncode == 0
        print(json.dumps({'interruptedCallerCommand': command, 'output': out.read_text(), 'exitCode': caller.returncode}), flush=True)
        print('PASS: interrupted detached-launch caller recovers via retained exact Windows identities and native cleanup', flush=True)

        # Fresh successful cold boot on the exact same ports after all failures.
        repeated = fresh()
        try:
            assert cli('provision', '--config', args.config, target=repeated).returncode == 0
            assert cli('wait', target=repeated).returncode == 0
            assert cli('capture', target=repeated).returncode == 0
        finally:
            assert cli('cleanup', target=repeated).returncode == 0
        final = d.windows(value, 'inventory')
        assert final['androidProcesses'] == baseline['androidProcesses']
        for listener in baseline['listeners']:
            assert listener in final['listeners'], 'Pre-existing unrelated Windows listener changed: ' + str(listener)
        print('Windows final after interruption and repeated cold boot:', json.dumps(final), flush=True)
        print('PASS: fresh same-port cold-boot lifecycle after failures; unrelated Android inventory unchanged', flush=True)
        print('LIMITATIONS: Windows mirrored-loopback broker residue remains visible and untouched. No APK, Appium session, Compose routing or instrumentation executed.', flush=True)
    finally:
        if directory.exists() and not (directory / 'cleaned.json').exists():
            cli('capture')
            result = cli('cleanup')
            if result.returncode:
                print('ERROR: owned cleanup remains unresolved; inspect retained output', flush=True)


if __name__ == '__main__':
    main()
