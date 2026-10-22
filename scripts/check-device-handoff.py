#!/usr/bin/env python3
"""Actual combined scaffold/Compose qualification, NOT product networking/UI E2E.

Sequential only: own one fresh API29 non-GMS guest and one fresh real environment.
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
from e2e import devices as d, device_app as a, device_routes as r, device_handoff as h, environment as e


def inventory():
    return {kind: sorted(e.docker(*args).splitlines()) for kind, args in
            (('container', ('ps', '-aq', '--no-trunc')), ('network', ('network', 'ls', '-q', '--no-trunc')),
             ('volume', ('volume', 'ls', '-q')))}


def retained_storage(before, after):
    for key in ('inode', 'logicalSha256', 'userVersion', 'tables', 'rowCounts', 'bytes'):
        assert before['storage'][key] == after['storage'][key], 'Retained storage changed: ' + key
    left = (Path(before['evidenceDirectory']) / 'replica.db').read_bytes()
    right = (Path(after['evidenceDirectory']) / 'replica.db').read_bytes()
    differences = [i for i, (x, y) in enumerate(zip(left, right)) if x != y]
    assert all(i in range(24, 28) or i in range(92, 96) for i in differences), differences
    print('RETAINED SQLITE', json.dumps({'before': before['storage'], 'after': after['storage'],
                                        'changedHeaderByteOffsets': differences}), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', required=True)
    parser.add_argument('--environment', required=True)
    parser.add_argument('--config', required=True)
    parser.add_argument('--apk', required=True)
    args = parser.parse_args()
    directory, envdir = d.directory_path(args.directory), e.directory_path(args.environment)
    assert not directory.exists() and not envdir.exists(), 'Fresh exact targets required'
    apk = str(Path(args.apk).resolve())
    baseline_docker = inventory()

    def cli(kind, *arguments, expected=0):
        target = directory if kind == 'devices' else envdir
        command = [sys.executable, f'scripts/e2e-{kind}.py', '--directory', str(target), *arguments]
        result = subprocess.run(command, cwd=d.ROOT, capture_output=True, text=True, timeout=600)
        print(json.dumps({'command': command, 'exitCode': result.returncode,
                          'stdout': result.stdout, 'stderr': result.stderr}), flush=True)
        assert result.returncode == expected, (command, result.returncode, result.stderr)
        return json.loads(result.stdout) if expected == 0 else result

    def no_handoff():
        for name in ('ready.json', 'routes-ready.json', 'app-ready.json', 'combined-ready.json'):
            assert not (directory / name).exists(), 'Stale success: ' + name

    def combined(ready):
        assert ready == json.loads((directory / 'ready.json').read_text())
        assert ready == json.loads((directory / 'combined-ready.json').read_text())
        assert len(ready['routes']['probes']) == 17
        assert ready['routes']['environment'] == r.identity(env)
        assert ready['routes']['backend'] == env['backendOrigin'] and ready['routes']['source'] == env['sourceOrigin']
        assert ready['app']['apk']['sha256'] == a.sha(Path(apk))
        assert ready['app']['apk']['package'] == a.PACKAGE and ready['app']['apk']['activity'] == a.ACTIVITY
        assert ready['app']['api'] == 29 and ready['app']['apk']['signerSha256']
        assert ready['app']['storage']['userVersion'] == 2 and not any(ready['app']['storage']['rowCounts'].values())
        assert ready['device']['api'] == '29' and not ready['device']['gms']
        return ready['app']

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

        # Actual failed setup retains intent and cannot be blessed by a later wait.
        # Fail source readiness through harness administration, NOT product hooks.
        e.admin(env, '/admin/control', {'instance': env['project'], 'sourceFault': 'unavailable'})
        cli('devices', '--timeout', '1', 'prepare-app', '--environment', str(envdir), '--apk', apk, expected=1)
        assert h.file(value).exists() and not r.file(value).exists()
        cli('devices', '--timeout', '1', 'wait', expected=1)
        no_handoff()
        capture()
        e.admin(env, '/admin/control', {'instance': env['project'], 'sourceFault': 'ok'})
        first = combined(cli('devices', 'prepare-app', '--environment', str(envdir), '--apk', apk))
        print('PASS failed real source setup/wait keeps combined intent, no services-only fallback; explicit same-input preparation recovers and installs/launches the normal APK', flush=True)
        route_bytes = r.file(value).read_bytes()
        repeated = combined(cli('devices', 'wait'))
        assert repeated['evidenceDirectory'] != first['evidenceDirectory']
        assert repeated['foreground']['pid'] == first['foreground']['pid']
        retained_storage(first, repeated)

        # A stopped production app must fail despite healthy current transport and
        # old branded captures. Explicit relaunch retains all Android data.
        d.adb(value, '-s', serial, 'shell', 'am', 'force-stop', a.PACKAGE)
        cli('devices', '--timeout', '1', 'wait', expected=1)
        no_handoff()
        capture()
        cli('devices', 'launch-app')
        relaunched = combined(cli('devices', 'wait'))
        assert relaunched['foreground']['pid'] != first['foreground']['pid']
        retained_storage(first, relaunched)
        print('PASS repeated combined wait freshly rechecks installed APK/UI/storage; stopped app invalidates all success, retained production relaunch recovers without clear/uninstall', flush=True)

        # Remove only the explicitly recorded exact reverse mapping; shell HTTP
        # also fails. Explicit detach/reattach repairs it; never auto-rebind.
        mapping = r.load(value)['mappings'][0]
        d.adb(value, '-s', serial, 'reverse', '--remove', mapping['local'])
        try:
            r.device_http(value, serial, env['ports'][mapping['role']], '/')
        except (d.Refusal, ValueError, OSError) as error:
            print('EXPECTED BROKEN DEVICE HTTP', type(error).__name__, str(error), flush=True)
        else:
            raise AssertionError('Removed route still supplies HTTP')
        cli('devices', '--timeout', '1', 'wait', expected=1)
        no_handoff()
        capture()
        cli('devices', 'unroute')
        assert not h.file(value).exists() and not r.file(value).exists()
        standalone = cli('devices', 'wait')
        assert 'app' not in standalone and 'routes' not in standalone and 'services only' in standalone['scope']
        recovered = combined(cli('devices', 'prepare-app', '--environment', str(envdir), '--apk', apk))
        retained_storage(first, recovered)
        print('PASS actual broken mapping fails combined wait/HTTP; explicit unroute removes combined intent/success, standalone services remain usable and explicit preparation restores full handoff', flush=True)

        # The exact existing Compose relay is checked, never replaced/adopted.
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
        combined(cli('devices', 'wait'))
        assert json.loads((envdir / 'relay.json').read_text()) == relay
        print('PASS actual suspended public relay fails combined wait; exact resume and fresh combined validation recover, no fallback', flush=True)

        # Retained in-scenario restart: no app clear or Compose reset. Only
        # lower-layer test-owned host state is populated, never account/game rows.
        e.database(env, "CREATE TABLE infrastructure_probe(value text NOT NULL); INSERT INTO infrastructure_probe VALUES ('combined-retained')")
        e.admin(env, '/admin/control', {'instance': env['project'], 'versions': {'prism': 2, 'quilt': 1}})
        ids = {key: info['Id'] for key, info in e.owned(env).items()}
        epoch = int(e.database(env, 'SELECT epoch FROM service_writer'))
        e.compose(env, 'stop', '--timeout', '30', 'backend')
        cli('devices', '--timeout', '1', 'wait', expected=1)
        no_handoff()
        capture()
        cli('environment', 'restart', '--service', 'backend')
        restarted = combined(cli('devices', 'wait'))
        retained_storage(first, restarted)
        assert {key: info['Id'] for key, info in e.owned(env).items()} == ids
        assert e.database(env, 'SELECT value FROM infrastructure_probe') == 'combined-retained'
        assert int(e.database(env, 'SELECT epoch FROM service_writer')) > epoch
        assert e.admin(env, '/admin/state')['versions'] == {'prism': 2, 'quilt': 1}
        print('PASS actual unavailable real backend fails combined wait; retained same-container restart advances writer epoch and preserves host probe/source edition plus unchanged Android storage', flush=True)

        # Independent HOST reset explicitly changes stores/container identities but
        # preserves environment identity/ports/art origins. Android is NOT reset.
        cli('environment', 'reset')
        reset = json.loads((envdir / 'reset-completed.json').read_text())
        assert set(reset['removedContainers'].values()) == set(ids.values())
        assert {key: info['Id'] for key, info in e.owned(env).items()} != ids
        assert e.database(env, "SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tablename='infrastructure_probe'") == '0'
        assert e.admin(env, '/admin/state')['versions'] == {'prism': 1, 'quilt': 1}
        assert e.admin(env, '/admin/state')['messageCount'] == 0
        reset_app = combined(cli('devices', 'wait'))
        retained_storage(first, reset_app)
        assert reset_app['foreground']['pid'] == restarted['foreground']['pid']
        print('PASS explicit successful Compose independent-scenario reset removes old host state; fresh combined validation rechecks recreated services/current relay and unchanged device storage without silent device reset', flush=True)

        # Existing strict foreign-map safeguard remains before any own removal.
        local = 'tcp:' + str(value['config']['systemPort'] + 10)
        d.write_json(directory / 'checker-foreign-route.json', {'serial': serial, 'direction': 'reverse', 'local': local, 'remote': 'tcp:19999'})
        d.adb(value, '-s', serial, 'reverse', '--no-rebind', local, 'tcp:19999')
        try:
            before = d.adb(value, '-s', serial, 'reverse', '--list').stdout
            cli('devices', 'wait', expected=1)
            cli('devices', 'unroute', expected=1)
            cli('devices', 'cleanup', expected=1)
            assert d.adb(value, '-s', serial, 'reverse', '--list').stdout == before
            assert h.file(value).exists() and d.appium_identity(value)
            no_handoff()
        finally:
            d.adb(value, '-s', serial, 'reverse', '--remove', local)
        final = combined(cli('devices', 'wait'))
        retained_storage(first, final)
        print('PASS combined foreign-route wait/unroute/cleanup refusal preserves every mapping/service; exact checker-only removal recovers', flush=True)

        resources = e.owned(env)
        for service in ('database', 'backend', 'adapters'):
            assert not resources[service]['HostConfig']['PortBindings']
            e.ip(env, resources[service])
        assert len(r.mappings(value, serial, complete=True)['reverse']) == 2
        runpy.run_path(str(d.ROOT / 'scripts/check-e2e-environment.py'))['check_egress'](env)
        print('PASS exactly two public loopback routes, eight private-route 404 probes; no health/admin/mailbox/DB/SMTP publication and actual namespace external IP/DNS egress blocked', flush=True)
        capture()
        d.write_json(directory / 'checker-final.json', {'first': first, 'final': final, 'combined': json.loads((directory / 'combined-ready.json').read_text())})
        relay = json.loads((envdir / 'relay.json').read_text())
    finally:
        if directory.exists() and not (directory / 'cleaned.json').exists():
            cli('devices', 'capture')
            cli('devices', 'cleanup')
        if envdir.exists() and not (envdir / 'cleaned.json').exists():
            result = cli('environment', 'capture')
            print('REDACTED FINAL ENVIRONMENT CAPTURE', Path(result['capture']).read_text(), flush=True)
            cli('environment', 'cleanup')
    cleaned = json.loads((directory / 'cleaned.json').read_text())
    assert cleaned['state']['nativeWatchdogExitObserved'] and not any(cleaned['currentProcesses']) and not cleaned['listeners']
    no_handoff()
    assert not r.file(value).exists() and not h.file(value).exists()
    for port in [value['config'][key] for key in ('adbPort', 'consolePort', 'appiumPort', 'systemPort')] + [value['config']['consolePort'] + 1]:
        assert not d.linux_listening(port)
    assert not e.process_identity(relay['pid'])
    for port in env['ports'].values():
        assert not d.linux_listening(port)
    for line in (directory / 'launchers.jsonl').read_text().splitlines():
        identity = json.loads(line)
        assert d.process_identity(identity['pid']) != identity
    after = d.windows(value, 'inventory')
    print('WINDOWS BASELINE', json.dumps(baseline_windows), flush=True)
    print('WINDOWS FINAL', json.dumps(after), flush=True)
    print('AMBIENT WINDOWS INVENTORY DELTA (not causal evidence)',
          json.dumps(d.inventory_changes(baseline_windows, after)), flush=True)
    final_docker = inventory()
    print('DOCKER BASELINE', json.dumps(baseline_docker), flush=True)
    print('DOCKER FINAL', json.dumps(final_docker), flush=True)
    print('AMBIENT DOCKER INVENTORY DELTA (not causal evidence)',
          json.dumps(d.inventory_changes(baseline_docker, final_docker)), flush=True)
    commands = (directory / 'commands.jsonl').read_text()
    print('ACTUAL DEVICE/TOOL COMMAND OUTPUT', commands, flush=True)
    assert not any('clear' in c['command'] or 'uninstall' in c['command'] or 'instrument' in c['command'] for c in map(json.loads, commands.splitlines()))
    assert 'POST /session' not in (directory / 'appium.log').read_text()
    print('PASS exact automatic unroute/native guest/ADB/Appium/watchdog/launcher/Compose/relay/port cleanup; ambient inventory retained for diagnosis; no clear/uninstall/instrumentation/Appium session', flush=True)
    print('LIMITATIONS: Combined scaffold startup and shell transport only. No product HTTP client/trust changes, Kotlin UI/touch/instrumentation, account/downloader/gameplay/Canvas, parent review, physical/upgrade/architecture or release qualification.', flush=True)


if __name__ == '__main__':
    main()
