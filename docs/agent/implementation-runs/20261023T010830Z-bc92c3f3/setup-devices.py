"""Provision exactly this run's two synthetic guests, recording their explicit pairing."""
from pathlib import Path
import json
import subprocess
import time

root = Path.cwd()
directories = [Path('/tmp/opencode/atw-device-bc92c3f300000000000000000000000' + str(i)) for i in (1, 2)]
for index, directory in enumerate(directories):
    assert not directory.exists(), 'Fresh setup only; retain and explicitly recover failed resources'
    config = '/tmp/opencode/i04b-' + ('first' if index == 0 else 'second') + '-config.json'
    command = ['python3', 'scripts/e2e-devices.py', '--directory', str(directory), 'provision', '--config', config]
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    if index == 1:
        # Both identities are now explicitly ours. Pair them before ADB's cross-console discovery.
        until = time.monotonic() + 30
        while not (directory / 'instance.json').exists() and process.poll() is None and time.monotonic() < until:
            time.sleep(.1)
        values = [json.loads((path / 'instance.json').read_text()) for path in directories]
        for value, peer in (values, values[::-1]):
            (Path(value['directory']) / 'observed-peers.json').write_text(json.dumps(dict(owner=value['owner'], peers={
                'emulator-' + str(peer['config']['consolePort']): dict(directory=peer['directory'], owner=peer['owner'])}), indent=2) + '\n')
    output, _ = process.communicate(timeout=600)
    print(json.dumps(dict(command=command, exitCode=process.returncode, output=output)), flush=True)
    assert process.returncode == 0
print('PASS two fresh explicitly paired owned API29 non-GMS guests')
