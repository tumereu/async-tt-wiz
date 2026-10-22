"""One affected two-normal-APK journey; reuse existing exact device/environment ownership."""
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
values = [d.load(Path('/tmp/opencode/atw-device-f7fc716d00000000000000000000000' + str(i))) for i in (1, 2)]
environment = e.load(Path('/tmp/opencode/atw-e2e-f7fc716d000000000000000000000001'))
runs = []
with d.lock():
    for value in values:
        s.close(value); d.check_windows(value, d.windows(value, 'snapshot'))
        d.check_mappings(value, 'emulator-' + str(value['config']['consolePort'])); r.invalidate(value)
    with e.locked(Path(environment['directory'])):
        e.reset(environment, 120)
    for value in values:
        serial = 'emulator-' + str(value['config']['consolePort'])
        d.adb(value, '-s', serial, 'shell', 'am', 'force-stop', a.PACKAGE)
        assert d.adb(value, '-s', serial, 'shell', 'pm', 'clear', a.PACKAGE).stdout.strip() == 'Success'
        a.operation(value, 'launch-app', 180)
        ready = json.loads((Path(value['directory']) / 'ready.json').read_text())
        run = Path(value['directory']) / ('runner-' + uuid.uuid4().hex); run.mkdir(mode=0o700); runs.append(run)
        d.write_json(Path(value['directory']) / 'runner-current.json', {'owner': value['owner'], 'run': run.name})
        config = value['config']
        properties = dict(owner=value['owner'], serial=serial, appium=ready['appium'], systemPort=config['systemPort'],
            adbPort=config['adbPort'], linuxAdb=config['linuxAdb'])
        (run / 'runner.properties').write_text(''.join(f'{k}={v}\n' for k, v in properties.items()))
    (runs[0] / 'peer.properties').write_bytes((runs[1] / 'runner.properties').read_bytes())
    (runs[0] / 'peer-run.txt').write_text(str(runs[1]))
    command = [str(d.ROOT / 'scripts/gradle.sh'), '--no-daemon', '--console=plain', ':e2e:deviceTest', '--tests', '*NativeLobbyCreationTest']
    started = time.time()
    try:
        result = subprocess.run(command, env=dict(os.environ, ATW_DEVICE_RUN=str(runs[0])), stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, timeout=1800)
        print(result.stdout)
        files = [p for p in Path('e2e/build/test-results/deviceTest').glob('*.xml') if p.stat().st_mtime >= started]
        for index, file in enumerate(files): shutil.copyfile(file, runs[0] / f'result-{index}.xml')
        assert result.returncode == 0, str(runs[0])
        cases = [case for file in files for case in ET.parse(file).iter('testcase')]
        assert len(cases) == 1 and not any(list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped'))
        observed = e.database(environment, "SELECT json_build_object('gameId',g.id,'name',g.game_name,'createdAt',g.created_at,'round',g.preparation_round,'invitationId',i.id,'state',i.state,'inviter',a.username,'recipient',b.username) FROM game g JOIN invitation i ON i.game_id=g.id JOIN account a ON a.id=g.inviter_id JOIN account b ON b.id=g.recipient_id")
        game = json.loads(observed)
        assert game['name'] == 'Native Azure Spark' and game['state'] == 'PENDING'
        assert e.database(environment, 'SELECT count(*) FROM game') == '1'
        assert e.database(environment, 'SELECT count(*) FROM invitation') == '1'
        assert e.database(environment, 'SELECT count(*) FROM contact') == '1'
        (prefix / 'observed-native-game.json').write_text(json.dumps(game, indent=2) + '\n')
        print('PASS two actual normal-APK contacts/named-create/same-account synchronization', [str(run) for run in runs])
    finally:
        for value in values:
            s.close(value); d.check_mappings(value, 'emulator-' + str(value['config']['consolePort'])); r.invalidate(value)
        for run in runs:
            for path in run.iterdir():
                if path.suffix in ('.xml', '.png', '.json'):
                    data = path.read_bytes(); target = prefix / (run.name + '-' + path.name)
                    if path.suffix != '.png' and (b'\r' in data or any(line.rstrip() != line for line in data.splitlines())):
                        target = target.with_suffix(target.suffix + '.json')
                        target.write_text(json.dumps(dict(base64=base64.b64encode(data).decode(), bytes=len(data), sha256=hashlib.sha256(data).hexdigest()), indent=2) + '\n')
                    else: target.write_bytes(data)
