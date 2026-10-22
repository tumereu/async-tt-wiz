"""Opt-in normal-APK Kotlin/Appium and real connected storage test runner."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

from e2e import devices as d, device_app as a, device_routes as r, device_session as s, environment as e

CLASSES = ['StorageDriverTest', 'StorageViewModelTest', 'StartupTest']


def fresh(value, timeout):
    """Independent scenario only. Never called by retained restart operations."""
    r.invalidate(value)
    s.idle(value)
    _, env = r.attached(value)
    with e.locked(Path(env['directory'])):
        e.reset(env, min(timeout, 120))
    serial = 'emulator-' + str(value['config']['consolePort'])
    d.check_windows(value, d.windows(value, 'snapshot'))
    d.check_mappings(value, serial)
    d.adb(value, '-s', serial, 'shell', 'am', 'force-stop', a.PACKAGE)
    result = d.adb(value, '-s', serial, 'shell', 'pm', 'clear', a.PACKAGE, timeout=30)
    d.require(result.stdout.strip() == 'Success', 'Fresh normal-app data reset failed')
    absent = d.adb(value, '-s', serial, 'shell', 'run-as', a.PACKAGE, 'ls',
                   'no_backup/client-storage/replica.db', check=False)
    d.require(absent.returncode != 0 and 'No such file' in absent.stdout,
              'Fresh app data did not remove the previous database')
    a.operation(value, 'launch-app', timeout)
    ready = json.loads((Path(value['directory']) / 'ready.json').read_text())
    d.require('routes' in ready and 'app' in ready, 'Combined normal-app/environment readiness required')
    return ready


def run(value, mode, timeout):
    directory = Path(value['directory']) / ('runner-' + uuid.uuid4().hex)
    directory.mkdir(mode=0o700)
    # Recover a previous recorded session before replacing its pointer.
    s.close(value)
    ready = fresh(value, timeout)
    d.write_json(directory / 'ready.json', ready)
    d.write_json(Path(value['directory']) / 'runner-current.json', {'owner': value['owner'], 'run': directory.name})
    config = value['config']
    properties = dict(owner=value['owner'], serial=ready['device']['serial'], appium=ready['appium'],
                      systemPort=config['systemPort'], adbPort=config['adbPort'], linuxAdb=config['linuxAdb'])
    (directory / 'runner.properties').write_text(''.join(f'{k}={v}\n' for k, v in properties.items()))
    env = {k: v for k, v in os.environ.items() if not k.startswith(('ADB_', 'ANDROID_SERIAL', 'ANDROID_ADB_'))}
    env.update(ATW_DEVICE_RUN=str(directory), ANDROID_SERIAL=properties['serial'],
               ANDROID_ADB_SERVER_PORT=str(config['adbPort']), ANDROID_ADB_SERVER_ADDRESS='127.0.0.1',
               ADB_SERVER_SOCKET=f'tcp:127.0.0.1:{config["adbPort"]}')
    task = ':e2e:deviceTest' if mode == 'startup' else ':androidApp:connectedDebugAndroidTest'
    command = [str(d.ROOT / 'scripts/gradle.sh'), '--no-daemon', '--console=plain', task]
    if mode == 'storage':
        command += ['-Pandroid.testInstrumentationRunnerArguments.class=' +
                    ','.join('org.asyncttwiz.android.' + name for name in CLASSES)]
    started = time.time()
    d.write_json(directory / 'command.json', {'command': command, 'mode': mode, 'startedAt': started,
                 'serial': properties['serial'], 'adbPort': config['adbPort'], 'systemPort': config['systemPort']})
    try:
        with (directory / 'gradle.txt').open('w') as output:
            result = subprocess.run(command, cwd=d.ROOT, env=env, stdout=output, stderr=subprocess.STDOUT, timeout=1200)
        d.write_json(directory / 'exit.json', {'exitCode': result.returncode})
        root = d.ROOT / ('e2e/build/test-results/deviceTest' if mode == 'startup' else
                         'androidApp/build/outputs/androidTest-results/connected')
        files = [p for p in root.rglob('*.xml') if p.stat().st_mtime >= started]
        for index, file in enumerate(files):
            shutil.copyfile(file, directory / f'result-{index}.xml')
        d.require(result.returncode == 0, 'Device Gradle check failed; retained output: ' + str(directory))
        d.require(files, 'No current-run JUnit results')
        tests = [case for file in files for case in ET.parse(file).iter('testcase')]
        d.require(len(tests) == (1 if mode == 'startup' else 14) and
                  not any(list(case.iter(tag)) for case in tests for tag in ('failure', 'error', 'skipped')),
                  'Required actual tests missing, red or skipped')
        if mode == 'storage':
            d.require({case.attrib['classname'].rsplit('.', 1)[-1] for case in tests} == set(CLASSES),
                      'Wrong instrumentation test classes')
    finally:
        # Captures survive failures. Appium owns removal of its recorded forward;
        # never issue remove-all or remove an unknown mapping to pass cleanup.
        try:
            s.close(value)
            d.check_mappings(value, properties['serial'])
        finally:
            r.invalidate(value)
    d.write_json(directory / 'passed.json', {'mode': mode, 'tests': len(tests), 'serial': properties['serial'],
                 'scope': 'Native startup' if mode == 'startup' else 'Actual Android storage/owner/launcher instrumentation'})
    return {'directory': str(directory), 'tests': len(tests), 'mode': mode}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', required=True)
    parser.add_argument('--timeout', type=int, default=180)
    parser.add_argument('mode', choices=('startup', 'storage', 'recover-session'))
    args = parser.parse_args()
    try:
        d.require(1 <= args.timeout <= 300, 'Invalid timeout')
        with d.lock():
            value = d.load(d.directory_path(args.directory))
            d.require(not (Path(value['directory']) / 'cleaned.json').exists(), 'Cleaned device refused')
            if args.mode == 'recover-session':
                s.close(value)
                result = {'recovered': True}
            else:
                result = run(value, args.mode, args.timeout)
        print(json.dumps(result, sort_keys=True))
        return 0
    except (d.Refusal, OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        print('Device runner refused: ' + str(error), file=__import__('sys').stderr)
        return 1
