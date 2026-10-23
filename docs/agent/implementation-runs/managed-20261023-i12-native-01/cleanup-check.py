"""Recheck only the exact completed lifecycle's recorded cleanup; no fence/resource adoption."""
from pathlib import Path
import json
import sys

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT))
from e2e import devices as d, environment as e

active = json.loads((Path(__file__).parent / 'active-environment.json').read_text())
with d.lock():
    for directory in active['devices']:
        value = d.load(Path(directory))
        assert (Path(directory) / 'cleaned.json').is_file()
        snapshot = d.windows(value, 'snapshot')
        assert not any(snapshot.get('currentProcesses', [])) and not snapshot.get('listeners', [])
        assert d.appium_identity(value) is None
        d.drain_launchers(value)
        assert not any(d.linux_listening(value['config'][key]) for key in ('adbPort', 'consolePort', 'appiumPort', 'systemPort'))
directory = Path(active['environment'])
environment = json.loads((directory / 'instance.json').read_text())
assert environment['directory'] == str(directory) and environment['project'] == directory.name
assert all(not (directory / name).exists() for name in environment['secretHashes'])
with e.locked(Path(environment['directory'])):
    assert (Path(environment['directory']) / 'cleaned.json').is_file()
    assert not e.owned(environment)
    for kind, suffixes in (('volume', ('database', 'adapters')), ('network', ('scenario',))):
        assert all(e.inspect(kind, environment['project'] + '-' + suffix) is None for suffix in suffixes)
print(json.dumps(dict(success=True, exactOwnedCleanupChecked=active)))
