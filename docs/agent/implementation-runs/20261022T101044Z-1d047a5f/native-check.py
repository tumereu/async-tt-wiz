"""One foreground normal-APK catalog journey on an already explicitly owned fresh device/environment."""
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

value = d.load(Path('/tmp/opencode/atw-device-1d047a5f11114af58b9f3861313df8c1'))
with d.lock():
    s.close(value)
    retained = '--retained' in sys.argv
    if retained:
        d.check_device(value)
        r.attached(value)
        d.adb(value, '-s', 'emulator-' + str(value['config']['consolePort']), 'shell', 'am', 'start',
              '-n', 'org.asyncttwiz.android/.MainActivity')
        ready = d.wait(value, 180)
    else:
        ready = runner.fresh(value, 180)
    parent = Path(value['directory'])
    run = parent / ('runner-' + uuid.uuid4().hex)
    run.mkdir(mode=0o700)
    d.write_json(parent / 'runner-current.json', {'owner': value['owner'], 'run': run.name})
    config = value['config']
    properties = dict(owner=value['owner'], serial=ready['device']['serial'], appium=ready['appium'],
        backend=ready['routes']['backend'], source=ready['routes']['source'], systemPort=config['systemPort'],
        adbPort=config['adbPort'], linuxAdb=config['linuxAdb'], environment='/tmp/opencode/atw-e2e-1d047a5f11114af58b9f3861313df8c1',
        repository=str(d.ROOT), retained=str(retained).lower())
    if '--tail' in sys.argv:
        properties['checkpoint'] = 'tail'
    (run / 'runner.properties').write_text(''.join(f'{k}={v}\n' for k, v in properties.items()))
    command = [str(d.ROOT / 'scripts/gradle.sh'), '--no-daemon', '--console=plain', ':e2e:deviceTest', '--tests', '*NativeCatalogTest']
    started = time.time()
    try:
        with (run / 'gradle.txt').open('w') as output:
            result = subprocess.run(command, env=dict(os.environ, ATW_DEVICE_RUN=str(run)), stdout=output, stderr=subprocess.STDOUT, timeout=1200)
        files = [p for p in Path('e2e/build/test-results/deviceTest').glob('*.xml') if p.stat().st_mtime >= started]
        for index, file in enumerate(files):
            shutil.copyfile(file, run / f'result-{index}.xml')
        assert result.returncode == 0, str(run)
        cases = [case for file in files for case in ET.parse(file).iter('testcase')]
        assert len(cases) == 1 and not any(list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped'))
        d.write_json(run / 'passed.json', {'tests': 1, 'scope': 'Normal APK I05a catalog consent/failure/retry/update/restart'})
        print('PASS normal APK catalog journey', run)
    finally:
        s.close(value)
        d.check_mappings(value, properties['serial'])
        r.invalidate(value)
        print('Retained native output', run)
