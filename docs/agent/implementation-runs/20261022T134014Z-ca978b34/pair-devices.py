"""Pair only this run's two owned consoles for ADB auto-discovery; no foreign mutation."""
import json
from pathlib import Path
import sys
sys.path.insert(0, str(Path.cwd()))
from e2e import devices as d

with d.lock():
    values = [d.load(Path('/tmp/opencode/atw-device-ca978b3400000000000000000000000' + str(i))) for i in (1, 2)]
    for value in values:
        d.check_windows(value, d.windows(value, 'snapshot'))
        peers = {'emulator-' + str(other['config']['consolePort']): {'directory': other['directory'], 'owner': other['owner']}
                 for other in values if other != value}
        d.write_json(Path(value['directory']) / 'observed-peers.json', {'owner': value['owner'], 'peers': peers})
    print(json.dumps({'paired': [v['directory'] for v in values], 'scope': 'read-only observation of live owned peers only'}))
