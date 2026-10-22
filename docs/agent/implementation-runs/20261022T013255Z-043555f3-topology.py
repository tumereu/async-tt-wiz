"""Controlled direct-route experiment; exact temporary intent/removal, no app."""
import base64
import json
from pathlib import Path
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))
from e2e import devices as d, environment as e

device = d.load(Path('/tmp/opencode/atw-device-043555f300004af58b9f3861313df8c1'))
env = e.load(Path('/tmp/opencode/atw-e2e-043555f300004af58b9f3861313df8c1'))
serial = 'emulator-5564'
d.check_mappings(device, serial)
intent = {'environment': env['directory'], 'serial': serial, 'ports': env['ports'], 'mode': 'direct reverse experiment'}
d.write_json(Path(device['directory']) / 'topology-probe-intent.json', intent)
source = ""
for port in env['ports'].values():
    source += "$c=New-Object Net.Sockets.TcpClient; try { $t=$c.ConnectAsync('127.0.0.1'," + str(port) + "); if (!$t.Wait(3000)) { throw 'connect timeout' }; $s=$c.GetStream(); $s.ReadTimeout=3000; $b=[Text.Encoding]::ASCII.GetBytes(\"GET / HTTP/1.1`r`nHost: 127.0.0.1`r`nConnection: close`r`n`r`n\"); $s.Write($b,0,$b.Length); $b=New-Object byte[] 2048; $n=$s.Read($b,0,$b.Length); [Text.Encoding]::ASCII.GetString($b,0,$n) } catch { $_.Exception.Message } finally { $c.Close() }; "
result = subprocess.run([device['config']['powershell'], '-NoProfile', '-NonInteractive', '-EncodedCommand', base64.b64encode(source.encode('utf-16le')).decode()], capture_output=True, text=True, timeout=20)
print('Windows loopback HTTP measurement', result.returncode, result.stdout, result.stderr, flush=True)
try:
    for role, port in env['ports'].items():
        print('CREATE', d.adb(device, '-s', serial, 'reverse', '--no-rebind', f'tcp:{port}', f'tcp:{port}').stdout, flush=True)
        path = '/' if role == 'backend' else '/prism/Meta.json'
        command = f"(printf 'GET {path} HTTP/1.1\\r\\nHost: 127.0.0.1\\r\\nConnection: close\\r\\n\\r\\n'; sleep 2) | toybox nc -w 4 -q 4 -W 4 127.0.0.1 {port} | base64"
        result = d.adb(device, '-s', serial, 'shell', command)
        print('DEVICE', role, result.returncode, result.stdout, flush=True)
finally:
    actual = d.adb(device, '-s', serial, 'reverse', '--list').stdout
    print('MAPPINGS', actual, flush=True)
    for port in env['ports'].values():
        if any(line.split()[1:] == [f'tcp:{port}', f'tcp:{port}'] for line in actual.splitlines()):
            d.adb(device, '-s', serial, 'reverse', '--remove', f'tcp:{port}')
    d.check_mappings(device, serial)
