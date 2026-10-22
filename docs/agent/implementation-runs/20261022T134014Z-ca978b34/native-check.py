"""Run the bounded two-instance normal-APK management journey and retain actual evidence."""
from pathlib import Path
import json
import os
import shutil
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path.cwd()))
from e2e import devices as d, device_runner as runner, device_session as s, device_routes as r, device_app as a

with d.lock():
    values = [d.load(Path('/tmp/opencode/atw-device-ca978b3400000000000000000000000' + str(i))) for i in (1, 2)]
    for value in values:
        s.close(value)
    tail = any(flag in sys.argv for flag in ('--tail', '--finish', '--deleted'))
    ready = [d.wait(values[0], 180) if tail else runner.fresh(values[0], 180)]
    value = values[1]
    serial = 'emulator-' + str(value['config']['consolePort'])
    d.check_windows(value, d.windows(value, 'snapshot')); d.check_mappings(value, serial)
    if not tail:
        d.adb(value, '-s', serial, 'shell', 'am', 'force-stop', a.PACKAGE)
        result = d.adb(value, '-s', serial, 'shell', 'pm', 'clear', a.PACKAGE)
        d.require(result.stdout.strip() == 'Success', 'Second fresh app reset failed')
        a.operation(value, 'launch-app', 180)
    else:
        d.wait(value, 180)
    ready.append(json.loads((Path(value['directory']) / 'ready.json').read_text()))
    runs = [Path(v['directory']) / ('runner-' + uuid.uuid4().hex) for v in values]
    for index, (value, run) in enumerate(zip(values, runs)):
        run.mkdir(mode=0o700)
        d.write_json(Path(value['directory']) / 'runner-current.json', {'owner': value['owner'], 'run': run.name})
        config = value['config']
        properties = dict(owner=value['owner'], serial=ready[index]['device']['serial'], appium=ready[index]['appium'],
            backend=ready[index]['routes']['backend'], source=ready[index]['routes']['source'], systemPort=config['systemPort'],
            adbPort=config['adbPort'], linuxAdb=config['linuxAdb'], secondDirectory=runs[1])
        (run / 'runner.properties').write_text(''.join(f'{k}={v}\n' for k, v in properties.items()))
    command = [str(d.ROOT / 'scripts/gradle.sh'), '--no-daemon', '--console=plain', ':e2e:deviceTest', '--tests', '*NativeDeckManagementTest']
    started = time.time()
    try:
        result = subprocess.run(command, env=dict(os.environ, ATW_DEVICE_RUN=str(runs[0]), ATW_MANAGEMENT_TAIL=str(tail).lower(),
            ATW_MANAGEMENT_FINISH=str('--finish' in sys.argv).lower(), ATW_MANAGEMENT_DELETED=str('--deleted' in sys.argv).lower()), stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, timeout=1800)
        (runs[0] / 'gradle.txt').write_text(result.stdout); print(result.stdout)
        files = [p for p in Path('e2e/build/test-results/deviceTest').glob('*.xml') if p.stat().st_mtime >= started]
        for index, file in enumerate(files): shutil.copyfile(file, runs[0] / f'result-{index}.xml')
        assert result.returncode == 0, str(runs)
        cases = [case for file in files for case in ET.parse(file).iter('testcase')]
        assert len(cases) == 1 and not any(list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped'))
        d.write_json(runs[0] / 'passed.json', {'tests': 1, 'scope': 'Two normal APKs, actual I07a deck metadata/delete/synchronization journey'})
        print('PASS normal APK management journey', runs)
    finally:
        for value, run in zip(values, runs):
            try:
                s.close(value); d.check_mappings(value, 'emulator-' + str(value['config']['consolePort']))
                r.invalidate(value)
            finally:
                for p in run.iterdir():
                    if p.suffix in ('.xml', '.png', '.json'): shutil.copyfile(p, Path(__file__).parent / (run.name + '-' + p.name))
        print('Retained native output', runs)
