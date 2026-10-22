"""Test-only, explicitly owned Windows WHPX / WSL Appium lifecycle.

No action API, default ADB reuse, networking changes or implicit cleanup.
Failed setup keeps evidence/resources and removes the ready handoff.
"""
import argparse
import base64
import contextlib
import fcntl
import hashlib
import http.client
import json
import os
from pathlib import Path
import re
import secrets
import signal
import socket
import subprocess
import sys
import time

from e2e.environment import Refusal, require, write_json, process_identity

ROOT = Path(__file__).resolve().parents[1]
BASE = Path('/tmp/opencode')
CONFIG_KEYS = {'node', 'appiumMain', 'appiumHome', 'androidHome', 'linuxAdb',
               'windowsSdk', 'powershell', 'adbPort', 'consolePort', 'appiumPort', 'systemPort'}


def validate_config(value):
    require(set(value) == CONFIG_KEYS, 'Explicit complete device configuration required')
    for key in ('node', 'appiumMain', 'linuxAdb', 'powershell'):
        path = Path(value[key])
        require(path.is_absolute() and path.is_file(), 'Explicit installed executable/file required: ' + key)
    for key in ('appiumHome', 'androidHome'):
        require(Path(value[key]).is_absolute() and Path(value[key]).is_dir(), 'Explicit tool home required: ' + key)
    require(Path(value['linuxAdb']).resolve() == (Path(value['androidHome']) / 'platform-tools/adb').resolve(),
            'Appium and controller must use the same Linux ADB')
    require(re.fullmatch(r'[A-Za-z]:\\[A-Za-z0-9_\\.-]+', value['windowsSdk']), 'Simple absolute Windows SDK path required')
    ports = [value[k] for k in ('adbPort', 'consolePort', 'appiumPort', 'systemPort')]
    require(all(type(p) is int and 1024 <= p <= 65534 for p in ports), 'Invalid device ports')
    require(value['consolePort'] % 2 == 0 and 5554 <= value['consolePort'] <= 5682,
            'Emulator console must be even in 5554..5682')
    require(len(set(ports + [value['consolePort'] + 1])) == 5, 'Duplicate device ports refused')
    require(value['adbPort'] != 5037, 'Default/unowned ADB port refused')
    # Explicit isolated driver installation, not ~/.appium or PATH fallback.
    require(Path(value['appiumHome']).resolve() != Path.home() / '.appium', 'Global Appium home refused')
    appium = json.loads((Path(value['appiumMain']).parents[2] / 'package.json').read_text())
    driver = json.loads((Path(value['appiumHome']) / 'node_modules/appium-uiautomator2-driver/package.json').read_text())
    require(appium['name'] == 'appium' and appium['version'] == '3.8.0' and
            driver['name'] == 'appium-uiautomator2-driver' and driver['version'] == '8.7.0',
            'Qualified Appium3.8.0/UiAutomator2-8.7.0 required')
    return value


def directory_path(path):
    path = Path(path)
    require(path.is_absolute() and path.parent == BASE and re.fullmatch(r'atw-device-[a-f0-9]{32}', path.name),
            'Instance must be /tmp/opencode/atw-device-<32 lowercase hex digits>')
    require(not path.is_symlink() and path.resolve() == path, 'Symlink instance refused')
    return path


@contextlib.contextmanager
def lock():
    path = BASE / 'atw-device-controller.lock'
    require(not path.is_symlink(), 'Unsafe device lock')
    with path.open('a') as handle:
        try:
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise Refusal('Another device controller is active') from None
        yield


def load(directory):
    require(directory.is_dir() and directory.stat().st_uid == os.getuid() and
            directory.stat().st_mode & 0o077 == 0, 'Private owned device directory required')
    file = directory / 'instance.json'
    require(not file.is_symlink(), 'Symlink instance input refused')
    value = json.loads(file.read_text())
    require(value['directory'] == str(directory) and re.fullmatch('[a-f0-9]{64}', value['owner']),
            'Invalid device identity')
    validate_config(value['config'])
    return value


def invalidate(value):
    for name in ('ready.json', 'combined-ready.json'):
        (Path(value['directory']) / name).unlink(missing_ok=True)


def inventory_changes(before, after):
    """Report ambient changes, not proof that this controller caused them.

    Exact owned-process/port cleanup and foreign-resource refusal remain hard
    checks. An unrelated listener/process/container can legitimately stop or
    start during qualification; retain the delta without declaring interference.
    """
    changes = {}
    for kind in sorted(before.keys() | after.keys()):
        removed = [item for item in before.get(kind, []) if item not in after.get(kind, [])]
        added = [item for item in after.get(kind, []) if item not in before.get(kind, [])]
        if removed or added:
            changes[kind] = {'removed': removed, 'added': added}
    return changes


def windows(value, operation, timeout=90):
    """Detach redirected Windows children from WSL pipes, with a bounded parent."""
    directory = Path(value['directory'])
    payload = {'name': directory.name, 'owner': value['owner'], 'config': value['config'], 'operation': operation}
    encoded = base64.b64encode(json.dumps(payload).encode()).decode()
    source = "$inputValue = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('" + encoded + "')) | ConvertFrom-Json\n"
    # Keep the Windows command line below CreateProcess's 32767-character limit.
    # Read only this checked-in helper through its exact WSL UNC path.
    helper = subprocess.run(['wslpath', '-w', str(ROOT / 'e2e/windows-device.ps1')],
                            check=True, capture_output=True, text=True, timeout=5).stdout.strip()
    source += "try { $reply = & ([scriptblock]::Create([IO.File]::ReadAllText('" + helper.replace("'", "''") + "'))); $reply } catch { @{ controllerError = $_.Exception.Message } | ConvertTo-Json -Compress }\n"
    command = [value['config']['powershell'], '-NoProfile', '-NonInteractive', '-EncodedCommand',
               base64.b64encode(source.encode('utf-16le')).decode()]
    out = directory / ('windows-' + operation + '.out')
    err = directory / ('windows-' + operation + '.err')
    with out.open('wb') as stdout, err.open('wb') as stderr:
        process = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr,
                                   start_new_session=True)
        # WSL interop can keep its Linux launcher alive until every Windows
        # descendant exits, even after PowerShell has returned. A complete JSON
        # reply (or explicit error envelope), not launcher exit, bounds this op.
        identity = process_identity(process.pid)
        if identity:
            with (directory / 'launchers.jsonl').open('a') as record:
                record.write(json.dumps(identity) + '\n')
        until = time.monotonic() + timeout
        while time.monotonic() < until:
            try:
                reply = json.loads(out.read_text(encoding='utf-8-sig').strip())
            except (json.JSONDecodeError, UnicodeDecodeError):
                if process.poll() is not None:
                    raise Refusal('Windows command exited without a complete reply; inspect retained logs') from None
                time.sleep(.1)
                continue
            require('controllerError' not in reply, 'Windows ' + operation + ' failed: ' + reply.get('controllerError', ''))
            return reply
    raise Refusal('Windows command timed out; inspect retained ownership/logs before cleanup')


def reserve(port):
    sock = socket.socket()
    try:
        # Deliberately no REUSEADDR/REUSEPORT: never share another owner's port.
        sock.bind(('127.0.0.1', port))
        sock.listen(1)
        return sock
    except BaseException:
        sock.close()
        raise


def linux_listening(port):
    # Do not bind a Windows-owned port from WSL as a probe: this host's mirrored
    # loopback broker retains a Windows listener even after that probe closes.
    for name in ('tcp', 'tcp6'):
        for line in Path('/proc/net/' + name).read_text().splitlines()[1:]:
            fields = line.split()
            if fields[3] == '0A' and int(fields[1].rsplit(':', 1)[1], 16) == port:
                return True
    return False


def appium_listener(value):
    """A responding unrelated HTTP server is not readiness for our Node PID."""
    identity = appium_identity(value)
    require(identity, 'Owned Appium process is not alive')
    owned = set()
    for fd in Path('/proc/' + str(identity['pid']) + '/fd').iterdir():
        try:
            owned.add(os.readlink(fd))
        except FileNotFoundError:
            pass
    found = False
    for name in ('tcp', 'tcp6'):
        for line in Path('/proc/net/' + name).read_text().splitlines()[1:]:
            fields = line.split()
            if fields[3] == '0A' and int(fields[1].rsplit(':', 1)[1], 16) == value['config']['appiumPort']:
                require(fields[1].split(':')[0] == '0100007F' and 'socket:[' + fields[9] + ']' in owned,
                        'Foreign or non-loopback Appium listener refused')
                found = True
    return found


def run(value, arguments, timeout=12, check=True, env=None):
    try:
        result = subprocess.run(arguments, env=env, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, timeout=timeout, text=True, errors='replace')
    except subprocess.TimeoutExpired as error:
        output = error.stdout or b''
        if isinstance(output, bytes):
            output = output.decode('utf-8', errors='replace')
        with (Path(value['directory']) / 'commands.jsonl').open('a') as record:
            record.write(json.dumps({'command': arguments, 'timedOutAfterSeconds': timeout, 'output': output}) + '\n')
        raise Refusal('Bounded device/tool command timed out; inspect retained command output') from None
    with (Path(value['directory']) / 'commands.jsonl').open('a') as output:
        output.write(json.dumps({'command': arguments, 'exitCode': result.returncode, 'output': result.stdout}) + '\n')
    if check:
        require(result.returncode == 0, 'Device/tool command failed; inspect retained command output')
    return result


def adb(value, *arguments, check=True, timeout=12):
    config = value['config']
    # Never let a failed connection start/adopt a replacement server.
    with socket.create_connection(('127.0.0.1', config['adbPort']), timeout=2):
        pass
    return run(value, [config['linuxAdb'], '-H', '127.0.0.1', '-P', str(config['adbPort']), *arguments], check=check, timeout=timeout)


def appium_identity(value):
    file = Path(value['directory']) / 'appium.json'
    if not file.exists():
        return None
    expected = json.loads(file.read_text())
    actual = process_identity(expected['pid'])
    require(actual is None or actual == expected, 'Reused/foreign Appium PID refused')
    args = bytes.fromhex(expected['cmdline']).decode().split('\0')
    require(value['config']['appiumMain'] in args and value['directory'] in args, 'Foreign Appium command refused')
    return actual


def check_windows(value, snapshot):
    state = snapshot['state']
    require(state['name'] == Path(value['directory']).name and state['owner'] == value['owner'] and
            state['avd'] == state['name'], 'Foreign Windows/AVD identity refused')
    expected = state['processes']
    actual = [p for p in snapshot['currentProcesses'] if p]
    require(all(p in expected for p in actual), 'Reused Windows identity refused')
    require(len(actual) >= 3, 'ADB/emulator processes are not alive')
    listeners = snapshot['listeners']
    adb_listeners = [p for p in listeners if p['port'] == value['config']['adbPort']]
    require(len(adb_listeners) == 1 and adb_listeners[0]['address'] == '127.0.0.1', 'Non-loopback/missing dedicated ADB')
    require(adb_listeners[0]['pid'] in {p['pid'] for p in actual}, 'Foreign dedicated ADB listener refused')
    return state


def check_mappings(value, serial):
    from e2e import device_routes
    if device_routes.file(value).exists():
        return device_routes.mappings(value, serial)
    # No attachment owns mappings. Never clear/adopt a pre-existing route.
    from e2e import device_session
    forward = device_session.forwards(value, serial)
    reverse = adb(value, '-s', serial, 'reverse', '--list').stdout.strip()
    require(not reverse, 'Foreign/unrecorded ADB mapping refused')
    return {'forward': forward, 'reverse': []}


def check_device(value):
    serial = 'emulator-' + str(value['config']['consolePort'])
    devices = adb(value, 'devices', '-l').stdout.splitlines()[1:]
    fields = [line.split() for line in devices if line.strip()]
    require(all(f[0] == serial for f in fields), 'Foreign device on dedicated ADB refused')
    if not fields or fields[0][1] != 'device':
        write_json(Path(value['directory']) / 'readiness-progress.json', {'stage': 'device-connection'})
        return None
    def shell(*args):
        result = adb(value, '-s', serial, 'shell', *args, check=False)
        return result.stdout.strip() if result.returncode == 0 else ''
    avd = shell('getprop', 'ro.kernel.qemu.avd_name')
    if not avd:
        write_json(Path(value['directory']) / 'readiness-progress.json', {'stage': 'avd-identity'})
        return None
    require(avd == Path(value['directory']).name, 'Mismatched AVD identity refused')
    if shell('getprop', 'sys.boot_completed') != '1':
        write_json(Path(value['directory']) / 'readiness-progress.json', {'stage': 'android-boot'})
        return None
    api = shell('getprop', 'ro.build.version.sdk')
    require(api == '29', 'Unexpected Android API')
    packages = shell('pm', 'list', 'packages')
    if 'package:android' not in packages.splitlines():
        write_json(Path(value['directory']) / 'readiness-progress.json', {'stage': 'package-manager'})
        return None
    require('package:com.google.android.gms' not in packages.splitlines(), 'Unexpected GMS image')
    mappings = check_mappings(value, serial)
    write_json(Path(value['directory']) / 'readiness-progress.json', {'stage': 'appium'})
    return {'serial': serial, 'avd': avd, 'api': api, 'bootCompleted': True, 'packageManager': True, 'gms': False,
            'fingerprint': shell('getprop', 'ro.build.fingerprint'), 'mappings': mappings}


def appium_status(value):
    if not appium_listener(value):
        raise ConnectionRefusedError('Owned Appium has not bound yet')
    connection = http.client.HTTPConnection('127.0.0.1', value['config']['appiumPort'], timeout=2)
    try:
        connection.request('GET', '/status')
        response = connection.getresponse()
        body = response.read(65537)
        require(len(body) <= 65536, 'Oversized Appium status')
        require(response.status == 200, 'Appium status failed')
        status = json.loads(body)
        require(status['value']['ready'] is True, 'Appium is not ready')
        return status
    finally:
        connection.close()


def infrastructure(value, timeout):
    """Validate services/attached transport without publishing a partial handoff."""
    invalidate(value)
    from e2e import device_routes
    if device_routes.file(value).exists():
        device_routes.invalidate(value)
    check_windows(value, windows(value, 'snapshot'))
    until = time.monotonic() + timeout
    last = 'Not ready'
    while time.monotonic() < until:
        try:
            device = check_device(value)
            status = appium_status(value)
            if device:
                result = {'owner': value['owner'], 'checkedAt': time.time(), 'device': device,
                          'adbHost': '127.0.0.1', 'adbPort': value['config']['adbPort'],
                          'appium': 'http://127.0.0.1:' + str(value['config']['appiumPort']),
                          'systemPort': value['config']['systemPort'], 'appiumStatus': status,
                          'scope': 'Device services only; no APK/UI/Compose/instrumentation acceptance'}
                # Recheck Windows ownership after actual Android readiness, before handoff.
                check_windows(value, windows(value, 'snapshot'))
                from e2e import device_routes
                if device_routes.file(value).exists():
                    result['routes'] = device_routes.validate(value, timeout)
                    result['scope'] = 'Device services plus measured public transport only; no combined APK/client/UI acceptance'
                result['checkedAt'] = time.time()
                return result
        except (ConnectionError, TimeoutError, OSError, http.client.HTTPException) as error:
            last = type(error).__name__
        time.sleep(.25)
    raise Refusal('Bounded device/Appium readiness timeout: ' + last)


def wait(value, timeout):
    try:
        result = infrastructure(value, timeout)
        from e2e import device_handoff
        if device_handoff.file(value).exists():
            result = device_handoff.validate(value, result, timeout)
        write_json(Path(value['directory']) / 'ready.json', result)
        return result
    except BaseException:
        from e2e import device_routes
        device_routes.invalidate(value)
        raise


def provision(directory, config, timeout):
    validate_config(config)
    directory.mkdir(mode=0o700)
    value = {'formatVersion': 1, 'directory': str(directory), 'owner': secrets.token_hex(32), 'config': config,
             'helperSha256': hashlib.sha256((ROOT / 'e2e/windows-device.ps1').read_bytes()).hexdigest()}
    write_json(directory / 'instance.json', value)
    # Mirrored WSL sockets appear in Windows inventory: inspect before reserving.
    # Windows holds its actual reservations atomically with detached launch.
    baseline = windows(value, 'inventory')
    write_json(directory / 'baseline-windows.json', baseline)
    ports = [config[k] for k in ('adbPort', 'consolePort', 'appiumPort', 'systemPort')]
    for listener in baseline['listeners']:
        require(listener['port'] not in ports + [config['consolePort'] + 1], 'Busy Windows port refused')
    require(not any(linux_listening(p) for p in ports + [config['consolePort'] + 1]), 'Busy Linux port refused')
    # Global controller lock + recorded live-instance checks reserve logical driver
    # ports; Windows reserves Windows sockets at launch. Appium binds its own port.
    for file in BASE.glob('atw-device-*/instance.json'):
        if file.parent == directory or (file.parent / 'cleaned.json').exists():
            continue
        other = json.loads(file.read_text())['config']
        other_ports = {other[k] for k in ('adbPort', 'consolePort', 'appiumPort', 'systemPort')}
        other_ports.add(other['consolePort'] + 1)
        require(not set(ports + [config['consolePort'] + 1]) & other_ports,
                'Ports reserved by an unfinished device instance')
    snapshot = windows(value, 'start')
    write_json(directory / 'started-windows.json', snapshot)
    check_windows(value, snapshot)
    env = {k: v for k, v in os.environ.items() if not k.startswith(('APPIUM_', 'ADB_', 'ANDROID_'))}
    env.update(APPIUM_HOME=config['appiumHome'], ANDROID_HOME=config['androidHome'], ANDROID_SDK_ROOT=config['androidHome'])
    versions = {}
    for name, args in {'node': [config['node'], '--version'], 'linuxAdb': [config['linuxAdb'], 'version'],
                       'appium': [config['node'], config['appiumMain'], '--version'],
                       'drivers': [config['node'], config['appiumMain'], 'driver', 'list', '--installed', '--json']}.items():
        versions[name] = run(value, args, timeout=30, env=env).stdout
    write_json(directory / 'versions.json', versions)
    command = [config['node'], config['appiumMain'], '--address', '127.0.0.1', '--port', str(config['appiumPort']),
               '--base-path', '/', '--use-drivers', 'uiautomator2', '--log-no-colors', '--log-level', 'info',
               '--log', str(directory / 'appium.log'), '--tmp', str(directory)]
    with (directory / 'appium-launch.log').open('wb') as output:
        process = subprocess.Popen(command, env=env, stdin=subprocess.DEVNULL, stdout=output, stderr=output,
                                   start_new_session=True)
    identity = process_identity(process.pid)
    require(identity is not None, 'Appium detached launch failed')
    write_json(directory / 'appium.json', identity)
    return wait(value, timeout)


def capture(value):
    snapshot = windows(value, 'snapshot')
    output = {'windows': snapshot, 'appiumProcess': appium_identity(value)}
    for name in ('versions.json', 'ready.json', 'readiness-progress.json', 'appium.log', 'appium-launch.log',
                 'app-installed.json', 'app-ready.json', 'app-progress.json',
                 'routes.json', 'routes-ready.json', 'routes-removed.json',
                 'combined.json', 'combined-ready.json', 'combined-detached.json'):
        path = Path(value['directory']) / name
        if path.exists():
            output[name] = path.read_text(errors='replace')[-16000:]
    write_json(Path(value['directory']) / 'capture.json', output)
    return output


def drain_launchers(value):
    # A native Windows exit can precede WSL's interop-proxy exit briefly. These
    # are only our recorded detached launchers, not arbitrary Windows processes.
    # Wait without signalling anything; PID reuse means the original is gone.
    file = Path(value['directory']) / 'launchers.jsonl'
    if not file.exists():
        return
    require(not file.is_symlink(), 'Unsafe launcher record')
    recorded = [json.loads(line) for line in file.read_text().splitlines()]
    until = time.monotonic() + 15
    while any(process_identity(p['pid']) == p for p in recorded):
        require(time.monotonic() < until, 'Owned WSL launchers did not exit after native shutdown; retain for recovery')
        time.sleep(.1)


def cleanup(value):
    invalidate(value)
    (Path(value['directory']) / 'app-ready.json').unlink(missing_ok=True)
    capture(value)  # Diagnostics before any shutdown; never broad process cleanup.
    if (Path(value['directory']) / 'runner-current.json').exists():
        from e2e import device_session
        device_session.close(value)
    from e2e import device_routes
    if device_routes.file(value).exists():
        device_routes.unroute(value)
    snapshot = windows(value, 'snapshot')
    if not snapshot.get('absent') and any(p and p['executable'].lower().endswith('\\adb.exe')
                                          for p in snapshot['currentProcesses']):
        # Even failed boot/setup can have foreign routes. A connected device is
        # sufficient to inspect reverse mappings; package-manager readiness is not.
        serial = 'emulator-' + str(value['config']['consolePort'])
        listed = adb(value, 'devices', '-l').stdout.splitlines()[1:]
        fields = [line.split() for line in listed if line.strip()]
        require(all(f[0] == serial for f in fields), 'Foreign device at cleanup refused')
        require(not adb(value, 'forward', '--list').stdout.strip(), 'Foreign/unrecorded ADB mapping refused')
        if fields and fields[0][1] == 'device':
            check_mappings(value, serial)
    actual = appium_identity(value)
    if actual:
        os.kill(actual['pid'], signal.SIGTERM)
        until = time.monotonic() + 10
        while process_identity(actual['pid']) and time.monotonic() < until:
            time.sleep(.1)
        require(process_identity(actual['pid']) is None, 'Appium did not stop; retain for recovery')
    result = windows(value, 'stop')
    # A preflight refusal has not acquired the busy foreign sockets. Only require
    # disappearance of ports whose services this instance actually launched.
    ports = [] if snapshot.get('absent') else [value['config'][k] for k in ('adbPort', 'consolePort')]
    if not snapshot.get('absent'):
        ports.append(value['config']['consolePort'] + 1)
    if (Path(value['directory']) / 'appium.json').exists():
        ports.append(value['config']['appiumPort'])
    require(not any(linux_listening(p) for p in ports),
            'Owned Linux ports remain listening')
    drain_launchers(value)
    write_json(Path(value['directory']) / 'cleaned.json', result)
    from e2e import device_handoff
    device_handoff.detach(value)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', required=True)
    parser.add_argument('--timeout', type=int, default=180)
    sub = parser.add_subparsers(dest='operation', required=True)
    sub.add_parser('provision').add_argument('--config', required=True)
    for operation in ('wait', 'capture', 'cleanup'):
        sub.add_parser(operation)
    sub.add_parser('install-app').add_argument('--apk', required=True)
    for operation in ('launch-app', 'capture-app'):
        sub.add_parser(operation)
    sub.add_parser('route-environment').add_argument('--environment', required=True)
    combined = sub.add_parser('prepare-app')
    combined.add_argument('--environment', required=True)
    combined.add_argument('--apk', required=True)
    for operation in ('validate-routes', 'unroute'):
        sub.add_parser(operation)
    args = parser.parse_args()
    directory = directory_path(args.directory)
    require(1 <= args.timeout <= 300, 'Readiness timeout must be 1..300 seconds')
    with lock():
        if args.operation == 'provision':
            require(not directory.exists(), 'Existing device instance refused; use its explicit lifecycle commands')
        try:
            if args.operation == 'provision':
                result = provision(directory, json.loads(Path(args.config).read_text()), args.timeout)
            else:
                value = load(directory)
                require(not (directory / 'cleaned.json').exists(), 'Cleaned instance cannot be reused')
                if args.operation == 'prepare-app':
                    from e2e import device_handoff
                    result = device_handoff.prepare(value, args.environment, args.apk, args.timeout)
                elif args.operation in ('install-app', 'launch-app', 'capture-app'):
                    from e2e import device_app
                    result = device_app.operation(value, args.operation, args.timeout, getattr(args, 'apk', None))
                elif args.operation in ('route-environment', 'validate-routes', 'unroute'):
                    from e2e import device_routes
                    if args.operation == 'route-environment':
                        result = device_routes.route(value, args.environment, args.timeout)
                    elif args.operation == 'validate-routes':
                        # Includes current device/services ownership, not only sockets.
                        require(device_routes.file(value).exists(), 'No attached routes')
                        result = wait(value, args.timeout)['routes']
                    else:
                        result = device_routes.unroute(value)
                else:
                    result = wait(value, args.timeout) if args.operation == 'wait' else globals()[args.operation](value)
            print(json.dumps(result, indent=2))
        except BaseException:
            if directory.is_dir():
                (directory / 'ready.json').unlink(missing_ok=True)
                (directory / 'app-ready.json').unlink(missing_ok=True)
                (directory / 'routes-ready.json').unlink(missing_ok=True)
                (directory / 'combined-ready.json').unlink(missing_ok=True)
            raise


if __name__ == '__main__':
    try:
        main()
    except (Refusal, OSError, ValueError, subprocess.SubprocessError) as error:
        print('Device controller failed: ' + str(error), file=sys.stderr)
        raise SystemExit(1)
