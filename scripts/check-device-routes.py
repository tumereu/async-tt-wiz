#!/usr/bin/env python3
"""Real public transport qualification. No APK, app API or UI acceptance.

Run sequentially with socket tests stopped. Own a fresh device AND Compose target.
"""
import argparse
import json
import os
from pathlib import Path
import runpy
import signal
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from e2e import devices as d, device_routes as r, environment as e


def inventory():
    return {kind: sorted(e.docker(*args).splitlines()) for kind, args in
            (('container', ('ps', '-aq', '--no-trunc')), ('network', ('network', 'ls', '-q', '--no-trunc')),
             ('volume', ('volume', 'ls', '-q')))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', required=True)
    parser.add_argument('--environment', required=True)
    parser.add_argument('--config', required=True)
    args = parser.parse_args()
    directory = d.directory_path(args.directory)
    envdir = e.directory_path(args.environment)
    assert not directory.exists() and not envdir.exists(), 'Fresh exact targets required'
    baseline_docker = inventory()

    def cli(kind, *arguments, expected=0):
        target = directory if kind == 'devices' else envdir
        command = [sys.executable, f'scripts/e2e-{kind}.py', '--directory', str(target), *arguments]
        result = subprocess.run(command, cwd=d.ROOT, capture_output=True, text=True, timeout=480)
        print(json.dumps({'command': command, 'exitCode': result.returncode,
                          'stdout': result.stdout, 'stderr': result.stderr}), flush=True)
        assert result.returncode == expected, (command, result.returncode, result.stderr)
        return json.loads(result.stdout) if expected == 0 else result

    def no_handoff():
        for name in ('ready.json', 'routes-ready.json', 'combined-ready.json'):
            assert not (directory / name).exists(), 'Stale handoff: ' + name

    def capture():
        cli('devices', 'capture')
        result = cli('environment', 'capture')
        print('REDACTED ENVIRONMENT CAPTURE', Path(result['capture']).read_text(), flush=True)

    try:
        cli('environment', 'provision')
        env = e.load(envdir)
        cli('devices', 'provision', '--config', args.config)
        value = d.load(directory)
        baseline_windows = json.loads((directory / 'baseline-windows.json').read_text())
        serial = 'emulator-' + str(value['config']['consolePort'])
        d.check_mappings(value, serial)
        # Check-only foreign mapping intent, before any mutation. Never use remove-all.
        for direction in ('forward', 'reverse'):
            local = 'tcp:' + str(value['config']['systemPort'] + 10)
            d.write_json(directory / 'checker-foreign-route.json', {'direction': direction, 'serial': serial,
                         'local': local, 'remote': 'tcp:19999'})
            d.adb(value, '-s', serial, direction, '--no-rebind', local, 'tcp:19999')
            try:
                before = d.adb(value, '-s', serial, direction, '--list').stdout
                cli('devices', 'route-environment', '--environment', str(envdir), expected=1)
                assert not r.file(value).exists()
                no_handoff()
                cli('devices', 'cleanup', expected=1)
                assert d.adb(value, '-s', serial, direction, '--list').stdout == before
                assert d.appium_identity(value)
            finally:
                d.adb(value, '-s', serial, direction, '--remove', local)
            cli('devices', 'wait')
        print('PASS actual forward/reverse foreign-mapping attachment and cleanup refusal; exact foreign routes/services untouched', flush=True)

        ready = cli('devices', 'route-environment', '--environment', str(envdir))
        assert len(ready['routes']['probes']) == 17
        assert ready['routes']['backend'] == env['backendOrigin'] and ready['routes']['source'] == env['sourceOrigin']
        assert len(ready['device']['mappings']['reverse']) == 2
        first_routes = r.file(value).read_bytes()
        cli('devices', 'validate-routes')
        assert r.file(value).read_bytes() == first_routes
        print('PASS same-port device-originated backend 404, two catalogs/metadata/printing/art bytes/hashes/MIME/origins and eight private-route 404s; repeated current validation', flush=True)

        # A real route disappears; an attempted device HTTP diagnostic fails too,
        # not merely a file-based readiness assertion. Reattach only explicitly.
        mapping = r.load(value)['mappings'][0]
        d.adb(value, '-s', serial, 'reverse', '--remove', mapping['local'])
        try:
            r.device_http(value, serial, env['ports'][mapping['role']], '/')
        except (d.Refusal, ValueError, OSError) as error:
            print('EXPECTED actual broken device HTTP:', type(error).__name__, str(error), flush=True)
        else:
            raise AssertionError('Removed route still supplies HTTP')
        cli('devices', '--timeout', '1', 'wait', expected=1)
        no_handoff()
        capture()
        cli('devices', 'unroute')
        assert not r.file(value).exists()
        standalone = cli('devices', 'wait')
        assert 'routes' not in standalone and 'services only' in standalone['scope']
        cli('devices', 'route-environment', '--environment', str(envdir))
        print('PASS broken device transport fails HTTP/wait without services-only fallback; explicit partial unroute, standalone wait and successful reattachment', flush=True)

        # The actual existing Compose relay is the only host transport process.
        # Suspend just its exact Linux identity to measure unavailable transport,
        # then resume it (no reset, no replacement process or extra public relay).
        relay = json.loads((envdir / 'relay.json').read_text())
        assert e.process_identity(relay['pid']) == relay
        os.kill(relay['pid'], signal.SIGSTOP)
        try:
            cli('devices', '--timeout', '1', 'wait', expected=1)
            no_handoff()
            capture()
        finally:
            assert e.process_identity(relay['pid']) == relay
            os.kill(relay['pid'], signal.SIGCONT)
        cli('devices', 'wait')
        assert json.loads((envdir / 'relay.json').read_text()) == relay
        print('PASS actual suspended host public relay invalidates attached readiness; exact resume recovers without reset or services-only downgrade', flush=True)

        # Required backend availability is not inferred from still-alive routes.
        e.compose(env, 'stop', '--timeout', '30', 'backend')
        cli('devices', '--timeout', '1', 'validate-routes', expected=1)
        no_handoff()
        capture()
        cli('environment', 'restart', '--service', 'backend')
        cli('devices', 'wait')
        print('PASS stopped attached real backend invalidates transport readiness; explicit retained restart and fresh device probes recover', flush=True)

        # A new foreign mapping while attached must not be absorbed by cleanup.
        local = 'tcp:' + str(value['config']['systemPort'] + 10)
        d.adb(value, '-s', serial, 'reverse', '--no-rebind', local, 'tcp:19999')
        try:
            before = d.adb(value, '-s', serial, 'reverse', '--list').stdout
            cli('devices', 'wait', expected=1)
            cli('devices', 'unroute', expected=1)
            cli('devices', 'cleanup', expected=1)
            assert d.adb(value, '-s', serial, 'reverse', '--list').stdout == before
            assert r.file(value).read_bytes() == first_routes
            no_handoff()
        finally:
            d.adb(value, '-s', serial, 'reverse', '--remove', local)
        cli('devices', 'wait')
        print('PASS attached foreign mapping blocks wait/unroute/cleanup before ANY route removal; explicit checker-only removal recovers', flush=True)

        resources = e.owned(env)
        for service in ('database', 'backend', 'adapters'):
            assert not resources[service]['HostConfig']['PortBindings']
            e.ip(env, resources[service])  # Requires exactly the owned internal network.
        assert len(r.mappings(value, serial, complete=True)['reverse']) == 2
        runpy.run_path(str(d.ROOT / 'scripts/check-e2e-environment.py'))['check_egress'](env)
        print('PASS only two public loopback device routes; no health/admin/mailbox/DB/SMTP publication; real container external IP/DNS egress remains blocked', flush=True)
        capture()
        print('Final route record:', r.file(value).read_text(), flush=True)
        print('Final route validation:', (directory / 'routes-ready.json').read_text(), flush=True)
    finally:
        # Capture before exact cleanup, including a partial/failed setup.
        if directory.exists() and not (directory / 'cleaned.json').exists():
            cli('devices', 'capture')
            cli('devices', 'cleanup')  # Exercises automatic exact unroute while attached.
        if envdir.exists() and not (envdir / 'cleaned.json').exists():
            result = cli('environment', 'capture')
            print('REDACTED FINAL ENVIRONMENT CAPTURE', Path(result['capture']).read_text(), flush=True)
            cli('environment', 'cleanup')
    cleaned = json.loads((directory / 'cleaned.json').read_text())
    assert cleaned['state']['nativeWatchdogExitObserved'] and not any(cleaned['currentProcesses']) and not cleaned['listeners']
    assert not r.file(value).exists()
    no_handoff()
    for port in (value['config'][k] for k in ('adbPort', 'consolePort', 'appiumPort', 'systemPort')):
        assert not d.linux_listening(port)
    assert not d.linux_listening(value['config']['consolePort'] + 1)
    assert not e.process_identity(relay['pid'])
    for port in env['ports'].values():
        assert not d.linux_listening(port)
    for line in (directory / 'launchers.jsonl').read_text().splitlines():
        identity = json.loads(line)
        assert d.process_identity(identity['pid']) != identity
    after = d.windows(value, 'inventory')
    print('WINDOWS BASELINE', json.dumps(baseline_windows), flush=True)
    print('WINDOWS FINAL', json.dumps(after), flush=True)
    assert after['androidProcesses'] == baseline_windows['androidProcesses']
    for listener in baseline_windows['listeners']:
        assert listener in after['listeners'], 'Unrelated Windows listener changed'
    assert inventory() == baseline_docker, 'Unrelated Docker inventory changed'
    print('DOCKER BASELINE/FINAL', json.dumps(baseline_docker), flush=True)
    print('ACTUAL DEVICE/TOOL COMMAND OUTPUT', (directory / 'commands.jsonl').read_text(), flush=True)
    print('PASS automatic exact unroute/native guest/ADB/Appium/watchdog/launcher/Compose/relay/port cleanup, unchanged unrelated inventory', flush=True)
    print('LIMITATIONS: Transport only, no APK or combined startup handoff, Kotlin/Appium sessions/input/instrumentation, product networking/downloader/account/gameplay/Canvas, physical hardware or release qualification.', flush=True)


if __name__ == '__main__':
    main()
