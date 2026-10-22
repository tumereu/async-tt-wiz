"""Recover this run's interrupted test finally using exact live session/forward provenance."""
from pathlib import Path
import json
import shutil
import sys
sys.path.insert(0, str(Path.cwd()))
from e2e import devices as d, device_session as s, environment as e

value = d.load(Path('/tmp/opencode/atw-device-3bc7cb2f202610220000000000000001'))
with d.lock():
    directory, session = s.record(value)
    assert directory.name == 'runner-870d82c841c54b1498cee80e14465867'
    live = s.capabilities(value, session)
    assert live is not None
    actual = [line.split() for line in d.adb(value, 'forward', '--list').stdout.strip().splitlines()]
    expected = [session['serial'], 'tcp:18210', 'tcp:6790']
    assert actual == [expected]
    session['forward'] = expected
    d.write_json(directory / 'session.json', session)
    s.close(value)
    for path in directory.iterdir():
        if path.suffix in ('.xml', '.png', '.json'): shutil.copyfile(path, Path(__file__).parent / (directory.name + '-' + path.name))
env = e.load(Path('/tmp/opencode/atw-e2e-3bc7cb2f202610220000000000000001'))
with e.locked(Path(env['directory'])):
    print(json.dumps(e.restart(env, 'backend', 60)))
print('PASS exact interrupted session/forward recovered and retained backend restarted')
