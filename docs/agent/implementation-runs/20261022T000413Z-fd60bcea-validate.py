"""Check the actual checkpoint's state, source syntax, links and owned cleanup."""
import base64
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from e2e import devices as d

run = '20261022T000413Z-fd60bcea'
report_path = ROOT / ('docs/agent/implementation-runs/' + run + '.json')
report = json.loads(report_path.read_text())
state = json.loads((ROOT / 'docs/agent/implementation-state.json').read_text())
before = json.loads(subprocess.check_output(['git', 'show', report['startCommit'] + ':docs/agent/implementation-state.json'], cwd=ROOT))
assert report['mode'] == 'implement' and report['target'] == 'I01f1a' and report['outcome'] == 'progress'
assert state['activeRun'] is None and state['authorization'] == 'authorized' and state['mode'] == 'active'
assert state['runs'] == before['runs'] + [str(report_path.relative_to(ROOT))]
for key in before:
    if key not in ('tasks', 'runs'):
        assert state[key] == before[key], key
for old, new in zip(before['tasks'], state['tasks'], strict=True):
    if old['id'] == 'I01f1a':
        assert new['status'] == 'in-progress'
        assert new['troubleshooting']['record'] == str(report_path.relative_to(ROOT))
        assert {k: v for k, v in new.items() if k not in ('status', 'troubleshooting')} == {k: v for k, v in old.items() if k != 'status'}
    else:
        assert old == new, old['id']
for check in report['checks']:
    evidence = ROOT / check['evidence']
    assert evidence.is_file() and evidence.name.startswith(run)
    actual = json.loads(evidence.read_text())
    assert actual['exitCode'] == check['exitCode']
    if check['kind'] == 'required':
        assert actual['exitCode'] == 0
for path in report['changedPaths']:
    assert (ROOT / path).is_file(), path
for path in ('e2e/devices.py', 'scripts/e2e-devices.py', 'scripts/check-e2e-devices.py', 'tests/test_e2e_devices.py'):
    compile((ROOT / path).read_text(), path, 'exec')
    print('PASS Python syntax:', path)
for path in (ROOT / 'e2e/README.md', ROOT / 'e2e/devices-README.md'):
    for link in re.findall(r'\]\(([^)]+)\)', path.read_text()):
        if not link.startswith(('https://', 'http://', '#')):
            assert (path.parent / link.split('#')[0]).is_file(), (str(path), link)
    print('PASS local Markdown links:', path.relative_to(ROOT))
helper = subprocess.check_output(['wslpath', '-w', str(ROOT / 'e2e/windows-device.ps1')], text=True).strip()
source = "$tokens=$null; $parseErrors=$null; $null=[Management.Automation.Language.Parser]::ParseFile('" + helper.replace("'", "''") + "',[ref]$tokens,[ref]$parseErrors); if ($parseErrors.Count) { $parseErrors | Out-String; exit 1 }; 'PASS PowerShell AST syntax'"
result = subprocess.run(['powershell.exe', '-NoProfile', '-NonInteractive', '-EncodedCommand',
                         base64.b64encode(source.encode('utf-16le')).decode()], capture_output=True, text=True, timeout=30)
print(result.stdout)
assert result.returncode == 0, result.stderr
latest = d.load(Path('/tmp/opencode/atw-device-79566d3f599144f88d9bed69467fc53d'))
after = d.windows(latest, 'inventory')
assert after['androidProcesses'] == [], after['androidProcesses']
for identity in ('3da4bb54f5eb4f069a76b0a2c1db514c', '4e4aa4f6959340149f8610206f1f1661',
                 '0262904813bd40eab878e4f9f0ea5c8a', '588f2d11cb934eb4a97e4d2ae670fd96',
                 'f66dc58d08154120ac0bd317c1a7879f', '859b213df1784ff1bf32b05fa8a6f38e',
                 'cebbcf988acc4a7ea1c3392560636829', '49e933a75b0f49c5ae0a27714f1436d2',
                 '3df6efc0059f410ab2e397ea1958cb17', '79566d3f599144f88d9bed69467fc53d'):
    directory = Path('/tmp/opencode/atw-device-' + identity)
    assert (directory / 'cleaned.json').exists() and not (directory / 'ready.json').exists()
    value = d.load(directory)
    assert d.appium_identity(value) is None
    for key in ('adbPort', 'consolePort', 'appiumPort', 'systemPort'):
        assert not d.linux_listening(value['config'][key])
assert d.linux_listening(5037), 'Original default ADB must remain'
# Initial `docker ps` inventories running containers only. Preserve the stopped
# unrelated container already recorded by I01e, rather than deleting it to make
# an erroneous all-containers-empty assertion pass.
prior = json.loads((ROOT / 'docs/agent/implementation-runs/20261021T233041Z-1b76a4c6-baseline.json').read_text())['output']
baseline = json.JSONDecoder().raw_decode(prior[prior.index('{'):])[0]
inventory = {}
for kind, arguments in {'container': ['ps', '-aq', '--no-trunc'],
                        'network': ['network', 'ls', '-q', '--no-trunc'],
                        'volume': ['volume', 'ls', '-q']}.items():
    inventory[kind] = sorted(subprocess.check_output(['docker', *arguments], text=True, timeout=15).splitlines())
assert inventory == {k: sorted(v) for k, v in baseline.items()}, (inventory, baseline)
print('PASS exact retained unrelated Docker inventory:', json.dumps(inventory))
print('PASS all ten run-owned device instances cleaned, all owned guests/Appium/ADB gone, default ADB and unrelated stopped container retained')
print('Final Windows listener inventory (including untouched OS broker residue):', json.dumps(after))
status = subprocess.check_output(['git', 'status', '--porcelain', '-uall'], cwd=ROOT, text=True)
changed = {line[3:] for line in status.splitlines()}
assert changed == set(report['changedPaths']), (changed ^ set(report['changedPaths']))
print('PASS exact owned changed-path set and selected-only troubleshooting transition; no review/gate/authorization change')
