"""Affected one-normal-APK picker journey with exact ownership, real backend and retained outputs."""
from pathlib import Path
import base64
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path.cwd()))
from e2e import devices as d, device_app as a, device_session as s, device_routes as r, environment as e

prefix = Path(__file__).parent
retained = sys.argv[1:] == ['--retained-acceptance']
assert retained or sys.argv[1:] == []
already_accepted = False
subprocess.run(['python3', str(prefix / 'offload.py')], check=True)
value = d.load(Path('/tmp/opencode/atw-device-f7a4a46b000000000000000000000001'))
environment = e.load(Path('/tmp/opencode/atw-e2e-f7a4a46b000000000000000000000001'))
with d.lock():
    s.close(value); d.check_windows(value, d.windows(value, 'snapshot'))
    serial = 'emulator-' + str(value['config']['consolePort'])
    d.check_mappings(value, serial); r.invalidate(value)
    with e.locked(Path(environment['directory'])):
        if not retained and (prefix / 'native-attempted.json').exists(): e.reset(environment, 120)
        e.wait(environment, 90)
        if retained:
            assert json.loads((prefix / 'observe-final.json').read_text())['requests'] == 1
            count = e.database(environment, 'SELECT count(*) FROM game_inbox')
            assert count in ('1', '2')
            already_accepted = count == '2'
        else:
            baseline = e.empty_baseline(environment)
            (prefix / 'actual-empty-v8-baseline.json').write_text(json.dumps(baseline, indent=2) + '\n')
    if not retained:
        (prefix / 'native-attempted.json').write_text(json.dumps(dict(attempted=True)) + '\n')
        d.adb(value, '-s', serial, 'shell', 'am', 'force-stop', a.PACKAGE)
        assert d.adb(value, '-s', serial, 'shell', 'pm', 'clear', a.PACKAGE).stdout.strip() == 'Success'
    a.operation(value, 'capture-app' if retained else 'launch-app', 180)
    ready = json.loads((Path(value['directory']) / 'ready.json').read_text())
    run = Path(value['directory']) / ('runner-' + uuid.uuid4().hex); run.mkdir(mode=0o700)
    d.write_json(Path(value['directory']) / 'runner-current.json', {'owner': value['owner'], 'run': run.name})
    config = value['config']
    properties = dict(owner=value['owner'], serial=serial, appium=ready['appium'], systemPort=config['systemPort'],
        adbPort=config['adbPort'], linuxAdb=config['linuxAdb'], control=str((prefix / 'control.py').resolve()))
    (run / 'runner.properties').write_text(''.join(f'{k}={v}\n' for k, v in properties.items()))
    test = '*NativePreparationPickerTest.' + ('retainedInvitationAcceptancePreservesInviterCopyAndRecipientPrivateEmptyPicker' if retained else 'personalPickerOneConfirmationImportReturnAndImmutablePreparedCopy')
    command = [str(d.ROOT / 'scripts/gradle.sh'), '--no-daemon', '--console=plain', ':e2e:deviceTest', '--tests', test]
    started = time.time()
    try:
        result = subprocess.run(command, env=dict(os.environ, ATW_DEVICE_RUN=str(run), ATW_PICKER_ACCEPTED=str(already_accepted).lower()), stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, timeout=1800)
        print(result.stdout)
        files = [p for p in Path('e2e/build/test-results/deviceTest').glob('*.xml') if p.stat().st_mtime >= started]
        for index, file in enumerate(files): shutil.copyfile(file, run / f'result-{index}.xml')
        assert result.returncode == 0, str(run)
        cases = [case for file in files for case in ET.parse(file).iter('testcase')]
        assert len(cases) == 1 and not any(list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped'))
        print('PASS normal-APK ' + ('retained acceptance/private recipient picker' if retained else 'picker/import/draft/cancel/confirm/restart/source-isolation journey'), str(run))
    finally:
        s.close(value); d.check_mappings(value, serial); r.invalidate(value)
        for path in run.iterdir():
            if path.suffix in ('.xml', '.png', '.json'):
                data = path.read_bytes(); target = prefix / (run.name + '-' + path.name)
                if path.suffix != '.png' and (b'\r' in data or any(line.rstrip() != line for line in data.splitlines())):
                    target = target.with_suffix(target.suffix + '.json')
                    target.write_text(json.dumps(dict(base64=base64.b64encode(data).decode(), bytes=len(data), sha256=hashlib.sha256(data).hexdigest()), indent=2) + '\n')
                else: target.write_bytes(data)
