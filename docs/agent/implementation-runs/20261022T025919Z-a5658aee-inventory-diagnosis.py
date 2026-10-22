"""Read-only diagnosis of an actual inventory failure; no exclusions or repair claims."""
import json
from pathlib import Path
import sys
import time

ROOT = Path(__file__).resolve().parents[3]
PREFIX = ROOT / 'docs/agent/implementation-runs/20261022T025919Z-a5658aee'
sys.path.insert(0, str(ROOT))
from e2e import devices as d

output = json.loads(Path(str(PREFIX) + '-actual.json').read_text())
assert output['exitCode'] == 1
baseline = json.loads(output['output'].split('WINDOWS BASELINE ')[1].splitlines()[0])
final = json.loads(output['output'].split('WINDOWS FINAL ')[1].splitlines()[0])
missing = [row for row in baseline['listeners'] if row not in final['listeners']]
assert missing and baseline['androidProcesses'] == final['androidProcesses'] == []
device_dir = Path('/tmp/opencode/atw-device-a5658aee11114af58b9f3861313df8c1')
env_dir = Path('/tmp/opencode/atw-e2e-a5658aee11114af58b9f3861313df8c1')
assert (device_dir / 'cleaned.json').exists() and (env_dir / 'cleaned.json').exists()
device = d.load(device_dir)
env = json.loads((env_dir / 'instance.json').read_text())
owned_ports = set(env['ports'].values()) | {device['config'][key] for key in
                                          ('adbPort', 'consolePort', 'appiumPort', 'systemPort')}
assert not any(row['port'] in owned_ports for row in missing)
assert not any(d.linux_listening(row['port']) for row in missing)
print('FAILED STRICT BASELINE', json.dumps(baseline), flush=True)
print('FAILED STRICT FINAL', json.dumps(final), flush=True)
print('MISSING PRE-EXISTING LISTENERS', json.dumps(missing), flush=True)
print('OWNED PORTS (disjoint from missing)', sorted(owned_ports), flush=True)
for attempt in range(3):
    current = d.windows(device, 'inventory')
    assert not current['androidProcesses']
    assert not any(row in current['listeners'] for row in missing)
    print('PASSIVE CURRENT INVENTORY', attempt, json.dumps(current), flush=True)
    if attempt < 2:
        time.sleep(15)
commands = [json.loads(line) for line in (device_dir / 'commands.jsonl').read_text().splitlines()]
assert not any('45467' in str(row['command']) or '38957' in str(row['command']) for row in commands)
print('PASS the two disappeared broker-owned loopback entries are unrelated to the owned ports and were not command targets; no Android process survived. They remain absent during passive observation. Their original Linux socket provenance is unknown; no root-cause fix or Windows broker shutdown claimed. Keep the failed run diagnostic and rerun the exact unchanged inventory assertions on a fresh environment/guest after passive drain.')
