"""One real two-device reset journey, using existing exclusive ownership and mailbox observation."""
import argparse
import base64
import email
from email import policy
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET

from e2e import devices as d, device_app as a, device_routes as r, device_session as s, environment as e


def pair(first, second):
    d.require(first['owner'] != second['owner'], 'Distinct peer owners required')
    for value, peer in ((first, second), (second, first)):
        d.require(not (Path(peer['directory']) / 'cleaned.json').exists(), 'Cleaned peer refused')
        d.check_windows(peer, d.windows(peer, 'snapshot'))
        d.write_json(Path(value['directory']) / 'observed-peers.json', {'owner': value['owner'], 'peers': {
            'emulator-' + str(peer['config']['consolePort']): {'owner': peer['owner'], 'directory': peer['directory']}}})


def observe(value):
    _, environment = r.attached(value)
    def check():
        messages = e.admin(environment, '/admin/messages')
        d.require(len(messages) == 1, 'Expected one UI-requested reset email')
        message = email.message_from_bytes(base64.b64decode(messages[0]['bodyBase64']), policy=policy.default)
        match = re.search(r'https://reset.synthetic.invalid/reset-password\?token=[A-Za-z0-9_-]{43}', message.get_content())
        d.require(match is not None, 'Configured link missing from actual email')
        return match[0]
    return e.deadline(check, 20)


def run(first, second):
    d.require(first['owner'] != second['owner'], 'Two distinct owned installations required')
    _, environment = r.attached(first)
    _, peer_environment = r.attached(second)
    d.require(environment['owner'] == peer_environment['owner'], 'Both devices must use the same owned backend')
    for value in (first, second):
        s.close(value)
        d.check_windows(value, d.windows(value, 'snapshot'))
        d.check_mappings(value, 'emulator-' + str(value['config']['consolePort']))
        r.invalidate(value)
    with e.locked(Path(environment['directory'])):
        e.reset(environment, 120)
    runs = []
    for value in (first, second):
        serial = 'emulator-' + str(value['config']['consolePort'])
        d.adb(value, '-s', serial, 'shell', 'am', 'force-stop', a.PACKAGE)
        cleared = d.adb(value, '-s', serial, 'shell', 'pm', 'clear', a.PACKAGE, timeout=30)
        d.require(cleared.stdout.strip() == 'Success', 'Owned app reset failed')
        a.operation(value, 'launch-app', 180)
        ready = json.loads((Path(value['directory']) / 'ready.json').read_text())
        directory = Path(value['directory']) / ('runner-' + uuid.uuid4().hex)
        directory.mkdir(mode=0o700)
        runs.append(directory)
        d.write_json(Path(value['directory']) / 'runner-current.json', {'owner': value['owner'], 'run': directory.name})
        properties = dict(owner=value['owner'], serial=serial, appium=ready['appium'], backend=ready['routes']['backend'],
                          systemPort=value['config']['systemPort'], adbPort=value['config']['adbPort'],
                          linuxAdb=value['config']['linuxAdb'], deviceDirectory=value['directory'], repository=str(d.ROOT))
        (directory / 'runner.properties').write_text(''.join(f'{k}={v}\n' for k, v in properties.items()))
        test_apk = d.ROOT / 'androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk'
        installed = d.adb(value, '-s', serial, 'install', '-r', str(test_apk), timeout=120)
        d.require('Success' in installed.stdout.splitlines(), 'Observer instrumentation install failed')
        d.write_json(directory / 'inputs.json', {'ready': ready, 'instrumentationSha256': hashlib.sha256(test_apk.read_bytes()).hexdigest()})
    (runs[0] / 'peer.properties').write_text((runs[1] / 'runner.properties').read_text())
    (runs[0] / 'peer-run.txt').write_text(str(runs[1]))
    command = [str(d.ROOT / 'scripts/gradle.sh'), '--no-daemon', '--console=plain', ':e2e:deviceTest', '--tests',
               '*NativePasswordResetTest.twoNormalInstallationsRequestOpenConfirmAndRecoverOnlyAfterSuccessfulReset']
    started = time.time()
    try:
        env = dict(os.environ, ATW_DEVICE_RUN=str(runs[0]))
        with (runs[0] / 'gradle.txt').open('w') as output:
            result = subprocess.run(command, cwd=d.ROOT, env=env, stdout=output, stderr=subprocess.STDOUT, timeout=1800)
        files = [p for p in (d.ROOT / 'e2e/build/test-results/deviceTest').glob('*.xml') if p.stat().st_mtime >= started]
        for index, file in enumerate(files):
            shutil.copyfile(file, runs[0] / f'result-{index}.xml')
        d.require(result.returncode == 0, 'Reset journey failed; retained output: ' + str(runs[0]))
        cases = [case for file in files for case in ET.parse(file).iter('testcase')]
        d.require(len(cases) == 1 and not any(list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped')),
                  'One actual passing non-skipped reset journey required')
        d.write_json(runs[0] / 'passed.json', {'tests': 1, 'devices': 2, 'scope': 'Normal APK UI/email/reset/revocation/logout/resume'})
    finally:
        for value in (first, second):
            s.close(value)
            d.check_mappings(value, 'emulator-' + str(value['config']['consolePort']))
            r.invalidate(value)
    return {'run': str(runs[0]), 'peerRun': str(runs[1]), 'tests': 1, 'devices': 2}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('run', 'observe', 'pair'))
    parser.add_argument('--directory', required=True)
    parser.add_argument('--peer')
    args = parser.parse_args()
    first = d.load(d.directory_path(args.directory))
    d.require(not (Path(first['directory']) / 'cleaned.json').exists(), 'Cleaned device refused')
    if args.mode == 'observe':
        # Secret stdout is consumed only in the private test process, never a retained command log.
        print(observe(first))
    else:
        with d.lock():
            second = d.load(d.directory_path(args.peer))
            if args.mode == 'pair':
                pair(first, second)
                print('Paired exact owned emulator identities for read-only ADB discovery; no peer actions/mappings adopted')
            else:
                print(json.dumps(run(first, second), sort_keys=True))


if __name__ == '__main__':
    main()
