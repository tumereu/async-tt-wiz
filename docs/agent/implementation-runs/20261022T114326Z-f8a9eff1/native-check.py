"""Foreground normal-APK import journey; exact controller-owned resources only."""
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
from e2e import devices as d, device_runner as runner, device_session as s, device_routes as r

value = d.load(Path('/tmp/opencode/atw-device-f8a9eff111114b3e99d382647521e8ff'))
with d.lock():
    s.close(value)
    ready = runner.fresh(value, 180)
    parent = Path(value['directory'])
    run = parent / ('runner-' + uuid.uuid4().hex)
    run.mkdir(mode=0o700)
    d.write_json(parent / 'runner-current.json', {'owner': value['owner'], 'run': run.name})
    config = value['config']
    properties = dict(owner=value['owner'], serial=ready['device']['serial'], appium=ready['appium'],
        backend=ready['routes']['backend'], source=ready['routes']['source'], systemPort=config['systemPort'],
        adbPort=config['adbPort'], linuxAdb=config['linuxAdb'])
    (run / 'runner.properties').write_text(''.join(f'{k}={v}\n' for k, v in properties.items()))
    command = [str(d.ROOT / 'scripts/gradle.sh'), '--no-daemon', '--console=plain', ':e2e:deviceTest', '--tests', '*NativeImportTest']
    started = time.time()
    try:
        result = subprocess.run(command, env=dict(os.environ, ATW_DEVICE_RUN=str(run)), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=1200)
        (run / 'gradle.txt').write_text(result.stdout)
        print(result.stdout)
        files = [p for p in Path('e2e/build/test-results/deviceTest').glob('*.xml') if p.stat().st_mtime >= started]
        for index, file in enumerate(files):
            shutil.copyfile(file, run / f'result-{index}.xml')
        assert result.returncode == 0, str(run)
        cases = [case for file in files for case in ET.parse(file).iter('testcase')]
        assert len(cases) == 1 and not any(list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped'))
        d.write_json(run / 'passed.json', {'tests': 1, 'scope': 'Normal APK I06a input/error/correction/preview/Edit/Exit/Back/rotation/Logout'})
        print('PASS normal APK import journey', run)
    finally:
        s.close(value)
        d.check_mappings(value, properties['serial'])
        r.invalidate(value)
        for p in run.iterdir():
            if p.suffix in ('.xml', '.png', '.json'):
                shutil.copyfile(p, Path(__file__).parent / (run.name + '-' + p.name))
        print('Retained native output', run)
