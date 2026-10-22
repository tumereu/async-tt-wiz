"""Read-only diagnosis of vanished stale mirrored-loopback entries, no cleanup."""
import base64
import json
from pathlib import Path
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))
from e2e import devices as d

directory = Path('/tmp/opencode/atw-device-043555f322224af58b9f3861313df8c1')
value = d.load(directory)
baseline = json.loads((directory / 'baseline-windows.json').read_text())
current = d.windows(value, 'inventory')
missing = [row for row in baseline['listeners'] if row not in current['listeners']]
assert missing and len({row['pid'] for row in missing}) == 1
assert all(row['address'] == '127.0.0.1' and not d.linux_listening(row['port']) for row in missing)
pid = missing[0]['pid']
source = f"Get-Process -Id {pid} | Select-Object Id,ProcessName,Path | ConvertTo-Json -Compress"
result = subprocess.run([value['config']['powershell'], '-NoProfile', '-NonInteractive', '-EncodedCommand',
                         base64.b64encode(source.encode('utf-16le')).decode()], capture_output=True, text=True, timeout=20)
assert result.returncode == 0
process = json.loads(result.stdout)
print('Actual process observation:', result.stdout, flush=True)
assert process['Id'] == pid and process['ProcessName'].lower() == 'dllhost'
assert current['androidProcesses'] == baseline['androidProcesses']
print(json.dumps({'missingStaleBrokerEntries': missing, 'stillAliveUnmodifiedBroker': process,
                  'noCorrespondingLinuxListener': True, 'baseline': baseline, 'current': current}, indent=2))
print('PASS corrected diagnosis: the vanished entries are not the two owned Compose ports (the previous assumption was disproved). All belong to the still-alive Windows DllHost mirrored-loopback broker and have no corresponding Linux listener. Full Python socket regressions ran immediately before this baseline, matching previously observed stale-broker expiry. No broker/network change is authorized or made. Rerun the unchanged strict inventory assertion sequentially after this stale baseline has drained; preserve the failed repeat as diagnostic, not a pass.')
