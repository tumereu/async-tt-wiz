"""Check-only handoff/cleanup/lock/evidence validator for this implementation unit."""
import base64
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from e2e import devices as d

RUN = '20261022T003723Z-932550a0'
PREFIX = ROOT / 'docs/agent/implementation-runs' / RUN
report = json.loads(PREFIX.with_suffix('.json').read_text())
state = json.loads((ROOT / 'docs/agent/implementation-state.json').read_text())
old = json.loads(subprocess.check_output(['git', 'show', report['startCommit'] + ':docs/agent/implementation-state.json'], text=True))
assert report['target'] == 'I01f1a' and report['mode'] == 'implement' and report['outcome'] == 'progress'
assert state['activeRun'] is None and state['runs'] == old['runs'] + [str(PREFIX.with_suffix('.json').relative_to(ROOT))]
assert len(state['tasks']) == len(old['tasks'])
for before, after in zip(old['tasks'], state['tasks']):
    if before['id'] != 'I01f1a':
        assert before == after
    else:
        for key in ('id', 'batch', 'dependsOn', 'outcome', 'checks'):
            assert before[key] == after[key]
        assert after['status'] == 'implemented'
        assert after['priorTroubleshootingRecord'] == before['troubleshooting']['record']
for key in set(old) - {'tasks', 'runs'}:
    assert old[key] == state[key], key

observed_paths = set(subprocess.check_output(['git', 'diff', '--name-only', report['startCommit']], text=True).splitlines())
observed_paths.update(subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard'], text=True).splitlines())
validation_path = str(PREFIX.relative_to(ROOT)) + '-validation-pass.json'
assert set(report['changedPaths']) == observed_paths | {validation_path}
assert not subprocess.check_output(['git', 'diff', '--cached', '--name-only'], text=True).strip()
subprocess.run(['git', 'diff', '--check'], check=True)

instances = set()
for check in report['checks']:
    if check['evidence'] == validation_path:
        continue  # This validator's output is written after it exits.
    evidence = json.loads((ROOT / check['evidence']).read_text())
    output = evidence.get('output', '')
    raw = base64.b64decode(evidence['outputBase64']) if 'outputBase64' in evidence else output.encode()
    assert hashlib.sha256(raw).hexdigest() == evidence['outputSha256']
    assert evidence['exitCode'] == check['exitCode']
    if check['kind'] == 'required':
        assert check['exitCode'] == 0
    if 'scripts/check-e2e-devices.py' in evidence['command']:
        for line in output.splitlines():
            if not line.startswith('{'):
                continue
            try:
                entry = json.loads(line)
            except ValueError:
                continue
            command = entry.get('command', entry.get('interruptedCallerCommand', []))
            if '--directory' in command:
                instances.add(Path(command[command.index('--directory') + 1]))
    print('Retained check:', check['evidence'], evidence['exitCode'], evidence['outputSha256'])
for directory in sorted(instances):
    value = d.load(directory)
    assert (directory / 'cleaned.json').exists(), str(directory)
    assert not (directory / 'ready.json').exists()
    cleaned = json.loads((directory / 'cleaned.json').read_text())
    assert cleaned.get('absent') or (not any(cleaned['currentProcesses']) and cleaned['listeners'] == [])
    assert d.appium_identity(value) is None
    launchers = directory / 'launchers.jsonl'
    if launchers.exists():
        for line in launchers.read_text().splitlines():
            original = json.loads(line)
            assert d.process_identity(original['pid']) != original
    print('Exact owned instance cleaned:', directory.name)
assert len(instances) >= 15, instances

tools = Path('/tmp/opencode/atw-device-tools-932550a0')
qualified = json.loads((tools / 'qualified.json').read_text())
for component, expected in (('appium', {'appium': '3.8.0'}), ('driver', {'appium-uiautomator2-driver': '8.7.0'})):
    package_dir = ROOT / 'e2e/device-tools' / component
    manifest = json.loads((package_dir / 'package.json').read_text())
    lock = json.loads((package_dir / 'package-lock.json').read_text())
    assert manifest.get('dependencies', manifest.get('devDependencies')) == expected
    assert lock['packages'][''].get('dependencies', lock['packages'][''].get('devDependencies')) == expected
    for name in ('package.json', 'package-lock.json'):
        assert qualified['inputs'][component + '/' + name] == hashlib.sha256((package_dir / name).read_bytes()).hexdigest()
        assert (package_dir / name).read_bytes() == (tools / component / name).read_bytes()
    for path, package in lock['packages'].items():
        if path:
            if package.get('inBundle'):
                # npm's bundled children are bytes inside the enclosing pinned
                # tarball, not independently fetched registry artifacts.
                enclosing = path
                while enclosing not in lock['packages'] or not lock['packages'][enclosing].get('integrity'):
                    enclosing = enclosing.rsplit('/node_modules/', 1)[0]
                    assert enclosing != path and enclosing
                assert lock['packages'][enclosing]['resolved'].startswith('https://registry.npmjs.org/')
                installed_manifest = tools / component / path / 'package.json'
                if installed_manifest.exists():
                    installed = json.loads(installed_manifest.read_text())
                    assert installed['version'] == package['version']
                    print('Bundled installed manifest:', path, installed['version'], installed.get('license'),
                          hashlib.sha256(installed_manifest.read_bytes()).hexdigest(), 'archive:', enclosing)
                else:
                    assert package.get('optional'), path
                    print('Optional bundled package not installed on this host:', path)
            else:
                assert package.get('integrity') and package['resolved'].startswith('https://registry.npmjs.org/')
    print('Locked graph:', component, len(lock['packages']) - 1, 'packages')
before = (tools / 'qualified.json').read_bytes()
command = [sys.executable, str(ROOT / 'scripts/install-device-tools.py'), '--directory', str(tools),
           '--node', qualified['node'], '--npm', qualified['npm']]
result = subprocess.run(command, capture_output=True, text=True, timeout=10)
print('Expected existing-installation refusal:', json.dumps({'command': command, 'exitCode': result.returncode,
                                                          'stdout': result.stdout, 'stderr': result.stderr}))
assert result.returncode != 0 and 'Never replace an existing tool installation' in result.stderr
assert (tools / 'qualified.json').read_bytes() == before

for path in ('e2e/README.md', 'e2e/devices-README.md'):
    file = ROOT / path
    for target in re.findall(r'\]\(([^)]+)\)', file.read_text()):
        if '://' not in target and not target.startswith('#'):
            assert (file.parent / target.split('#')[0]).exists(), (path, target)
    print('Local documentation links pass:', path)
for file in sorted((ROOT / 'e2e/build/test-results/test').glob('TEST-*.xml')):
    suite = ET.parse(file).getroot()
    assert suite.attrib['tests'] != '0' and all(suite.attrib[k] == '0' for k in ('failures', 'errors', 'skipped'))
    print('Actual host JUnit XML:', file.relative_to(ROOT), file.read_text())
assert list((ROOT / 'e2e/build/test-results/test').glob('TEST-*.xml'))
print('PASS: selected-child-only transition, full retained output hashes, lock/install refusal, exact owned cleanup, JUnit, docs and whitespace; no parent/review/gate advancement')
