"""One real normal-APK inspector journey; retained tail never resets accepted work."""
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

with d.lock():
    value = d.load(Path('/tmp/opencode/atw-device-3ad15f39000000000000000000000001'))
    s.close(value)
    tail = any(flag in sys.argv for flag in ('--tail', '--finish', '--art'))
    ready = d.wait(value, 180) if tail else runner.fresh(value, 180)
    run = Path(value['directory']) / ('runner-' + uuid.uuid4().hex)
    run.mkdir(mode=0o700)
    d.write_json(Path(value['directory']) / 'runner-current.json', {'owner': value['owner'], 'run': run.name})
    config = value['config']
    properties = dict(owner=value['owner'], serial=ready['device']['serial'], appium=ready['appium'],
        backend=ready['routes']['backend'], source=ready['routes']['source'], systemPort=config['systemPort'],
        adbPort=config['adbPort'], linuxAdb=config['linuxAdb'])
    (run / 'runner.properties').write_text(''.join(f'{k}={v}\n' for k, v in properties.items()))
    command = [str(d.ROOT / 'scripts/gradle.sh'), '--no-daemon', '--console=plain', ':e2e:deviceTest', '--tests', '*NativeCardInspectionTest']
    started = time.time()
    try:
        result = subprocess.run(command, env=dict(os.environ, ATW_DEVICE_RUN=str(run), ATW_INSPECTION_TAIL=str(tail).lower(),
            ATW_INSPECTION_FINISH=str('--finish' in sys.argv).lower(), ATW_INSPECTION_ART=str('--art' in sys.argv).lower()),
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=1800)
        print(result.stdout)
        files = [p for p in Path('e2e/build/test-results/deviceTest').glob('*.xml') if p.stat().st_mtime >= started]
        for index, file in enumerate(files): shutil.copyfile(file, run / f'result-{index}.xml')
        assert result.returncode == 0, str(run)
        cases = [case for file in files for case in ET.parse(file).iter('testcase')]
        assert len(cases) == 1 and not any(list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped'))
        print('PASS normal APK inspector', run)
    finally:
        try:
            s.close(value); d.check_mappings(value, properties['serial']); r.invalidate(value)
        finally:
            for p in run.iterdir():
                if p.suffix in ('.xml', '.png', '.json'):
                    data = p.read_bytes()
                    if p.suffix != '.png' and (b'\r' in data or any(line.rstrip() != line for line in data.splitlines())):
                        import base64
                        import hashlib
                        (Path(__file__).parent / (run.name + '-' + p.name + '.json')).write_text(json.dumps(dict(
                            base64=base64.b64encode(data).decode(), bytes=len(data), sha256=hashlib.sha256(data).hexdigest()), indent=2) + '\n')
                    else: shutil.copyfile(p, Path(__file__).parent / (run.name + '-' + p.name))
