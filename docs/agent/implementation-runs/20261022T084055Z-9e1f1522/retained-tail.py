"""Foreground affected tail recheck of recheck3's retained scenario, not a new/reset scenario or implementation loop."""
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
from e2e import devices as d, device_session as s, device_routes as r

devices = [d.load(Path('/tmp/opencode/atw-device-9e1f1522' + suffix + '4af58b9f3861313df8c1')) for suffix in ('1111', '2222')]
runs = []
with d.lock():
    for value in devices:
        s.close(value)
        d.check_windows(value, d.windows(value, 'snapshot'))
        d.check_device(value)
        r.attached(value)
        parent = Path(value['directory'])
        previous = parent / json.loads((parent / 'runner-current.json').read_text())['run']
        run = parent / ('runner-' + uuid.uuid4().hex)
        run.mkdir(mode=0o700)
        shutil.copyfile(previous / 'runner.properties', run / 'runner.properties')
        shutil.copyfile(previous / 'inputs.json', run / 'inputs.json')
        d.write_json(parent / 'runner-current.json', {'owner': value['owner'], 'run': run.name})
        runs.append(run)
    shutil.copyfile(runs[1] / 'runner.properties', runs[0] / 'peer.properties')
    (runs[0] / 'peer-run.txt').write_text(str(runs[1]))
    started = time.time()
    command = [str(d.ROOT / 'scripts/gradle.sh'), '--no-daemon', '--console=plain', ':e2e:deviceTest', '--tests',
               '*NativePasswordResetTest.completesRetainedLogoutAndSecondAccountCheckpoint']
    try:
        with (runs[0] / 'gradle.txt').open('w') as output:
            result = subprocess.run(command, env=dict(os.environ, ATW_DEVICE_RUN=str(runs[0])), stdout=output,
                                    stderr=subprocess.STDOUT, timeout=600)
        files = [p for p in Path('e2e/build/test-results/deviceTest').glob('*.xml') if p.stat().st_mtime >= started]
        for index, file in enumerate(files):
            shutil.copyfile(file, runs[0] / f'result-{index}.xml')
        assert result.returncode == 0, str(runs[0])
        cases = [case for file in files for case in ET.parse(file).iter('testcase')]
        assert len(cases) == 1 and not any(list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped'))
        d.write_json(runs[0] / 'passed.json', {'devices': 2, 'tests': 1, 'scope': 'Retained affected Logout/restart/account-switch tail only',
                     'baseline': 'runner-8f3cb5377c9547448659c5ee503aee32 reset core + runner-2f81ff7657d444b086408484fe70c040 accepted second-account registration (original red results retained)'})
        print('PASS affected retained two-device Logout/restart/different-account tail only; no fresh reset/confirmation/core journey rerun')
    finally:
        for value in devices:
            s.close(value)
            d.check_mappings(value, 'emulator-' + str(value['config']['consolePort']))
            r.invalidate(value)
    print('EVIDENCE', runs[0], runs[1])
