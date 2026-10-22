"""Run affected two-normal-APK transfers with exact device ownership and retained captures."""
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
stage = sys.argv[1] if len(sys.argv) == 2 else ''
assert stage in ('', 'portrait-drags', 'portrait-outside')
subprocess.run(['python3', str(prefix / 'offload.py')], check=True)
values = [d.load(Path('/tmp/opencode/atw-device-802a24bd00000000000000000000000' + str(i))) for i in (1, 2)]
environment = e.load(Path('/tmp/opencode/atw-e2e-802a24bd000000000000000000000001'))
with d.lock():
    with e.locked(Path(environment['directory'])):
        if not stage and (prefix / 'native-attempted.json').exists(): e.reset(environment, 120)
        e.wait(environment, 90)
        if not stage: e.empty_baseline(environment)
    (prefix / 'native-attempted.json').write_text(json.dumps(dict(attempted=True)) + '\n')
    runs = []
    for value in values:
        s.close(value); d.check_windows(value, d.windows(value, 'snapshot'))
        serial = 'emulator-' + str(value['config']['consolePort'])
        d.check_mappings(value, serial); r.invalidate(value)
        if not stage:
            d.adb(value, '-s', serial, 'shell', 'am', 'force-stop', a.PACKAGE)
            assert d.adb(value, '-s', serial, 'shell', 'pm', 'clear', a.PACKAGE).stdout.strip() == 'Success'
        a.operation(value, 'capture-app' if stage else 'launch-app', 180)
        ready = json.loads((Path(value['directory']) / 'ready.json').read_text())
        run = Path(value['directory']) / ('runner-' + uuid.uuid4().hex); run.mkdir(mode=0o700); runs.append(run)
        d.write_json(Path(value['directory']) / 'runner-current.json', dict(owner=value['owner'], run=run.name))
        config = value['config']
        properties = dict(owner=value['owner'], serial=serial, appium=ready['appium'], systemPort=config['systemPort'],
            adbPort=config['adbPort'], linuxAdb=config['linuxAdb'], control=str((prefix / 'control.py').resolve()))
        (run / 'runner.properties').write_text(''.join(f'{k}={v}\n' for k, v in properties.items()))
        subprocess.run(['python3', str(prefix / 'offload.py')], check=True)
    (runs[0] / 'peer-run.txt').write_text(str(runs[1]))
    started = time.time()
    try:
        command = [str(d.ROOT / 'scripts/gradle.sh'), '--no-daemon', '--console=plain', ':e2e:deviceTest', '--tests', '*NativePreparationTransferTest']
        result = subprocess.run(command, env=dict(os.environ, ATW_DEVICE_RUN=str(runs[0]), ATW_TRANSFER_STAGE=stage), stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, timeout=2400)
        print(result.stdout)
        files = [p for p in Path('e2e/build/test-results/deviceTest').glob('*.xml') if p.stat().st_mtime >= started]
        for index, file in enumerate(files): shutil.copyfile(file, runs[0] / f'result-{index}.xml')
        assert result.returncode == 0, str(runs[0])
        cases = [case for file in files for case in ET.parse(file).iter('testcase')]
        assert len(cases) == 1 and not any(list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped'))
        print('PASS actual two-normal-APK preparation transfer journey', str(runs[0]))
    finally:
        for value, run in zip(values, runs):
            s.close(value); d.check_mappings(value, 'emulator-' + str(value['config']['consolePort'])); r.invalidate(value)
            for path in run.iterdir():
                if path.suffix in ('.xml', '.png', '.json'):
                    data = path.read_bytes(); target = prefix / (run.name + '-' + path.name)
                    if path.suffix != '.png' and (b'\r' in data or any(line.rstrip() != line for line in data.splitlines())):
                        target = target.with_suffix(target.suffix + '.json')
                        target.write_text(json.dumps(dict(base64=base64.b64encode(data).decode(), bytes=len(data), sha256=hashlib.sha256(data).hexdigest()), indent=2) + '\n')
                    else: target.write_bytes(data)
