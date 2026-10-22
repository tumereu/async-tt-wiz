"""Record only these two owned live emulator peers for the existing ADB discovery guard."""
from pathlib import Path
import sys
sys.path.insert(0, str(Path.cwd()))
from e2e import devices as d

with d.lock():
    values = [d.load(Path('/tmp/opencode/atw-device-802a24bd00000000000000000000000' + str(i))) for i in (1, 2)]
    for value, other in (values, values[::-1]):
        d.check_windows(other, d.windows(other, 'snapshot'))
        d.write_json(Path(value['directory']) / 'observed-peers.json', dict(owner=value['owner'], peers={
            'emulator-' + str(other['config']['consolePort']): dict(directory=other['directory'], owner=other['owner'])}))
    print(d.wait(values[1], 180))
