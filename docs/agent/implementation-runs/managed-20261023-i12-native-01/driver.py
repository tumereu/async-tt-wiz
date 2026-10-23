"""One leased native lifecycle. Reuses qualified provisioning, pairing, reset, routing and session adapters."""
from pathlib import Path
import base64
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT))
from e2e import devices as d, environment as e, device_handoff as h, device_app as a
from e2e import device_session as s, device_routes as r, device_artifacts, reset_runner, device_runner

PREFIX = Path(__file__).parent
BUILD = ROOT / 'e2e/build/managed-20261023-i12-native-01'
BUILD.mkdir(parents=True, exist_ok=True)
os.environ['ATW_DEVICE_ARTIFACT_ROOT'] = str(BUILD / 'artifacts')
FLAGS = ['--no-daemon', '--max-workers=1', '-Dorg.gradle.jvmargs=-Xmx1280m -Dfile.encoding=UTF-8', '--console=plain']
def record(name, value):
    (PREFIX / name).write_text(json.dumps(value, indent=2) + '\n')
def capture(name, command, env=None):
    result = subprocess.run(['python3', 'scripts/capture-check.py', str(PREFIX / name), '--', *command], cwd=ROOT, env=env)
    return result.returncode
def retain(path, name):
    data = path.read_bytes()
    if path.suffix == '.png': shutil.copyfile(path, PREFIX / name)
    else:
        record(name + '.json', dict(base64=base64.b64encode(data).decode(), bytes=len(data), sha256=hashlib.sha256(data).hexdigest()))
def checked_junit(paths, name, started):
    paths = [p for p in paths if p.stat().st_mtime >= started]
    assert paths, 'Missing current JUnit'
    subprocess.run(['python3', 'scripts/implementation-evidence.py', 'retain-junit', str(PREFIX / name), *map(str, paths)], check=True)
    cases = [c for p in paths for c in ET.parse(p).iter('testcase')]
    assert len(cases) == 1 and not any(list(c.iter(t)) for c in cases for t in ('failure', 'error', 'skipped'))

token = uuid.uuid4().hex
envdir = Path('/tmp/opencode/atw-e2e-' + token)
devdirs = [Path('/tmp/opencode/atw-device-' + uuid.uuid4().hex) for _ in range(2)]
record('active-environment.json', dict(environment=str(envdir), devices=list(map(str, devdirs)), diskArtifacts=str(BUILD)))
values = []
failure = None
cleanup_errors = []
try:
    # Immutable image input checks and environment provisioning are the existing adapter's job.
    e.provision(envdir, 18880, 18882, 120)
    environment = e.load(envdir)
    record('images.json', dict(images=environment['images'], inputs=environment['inputs'],
        server=json.loads((ROOT / 'server/build/application-image.json').read_text()),
        adapters=json.loads((ROOT / 'e2e/build/adapters/image.json').read_text())))
    assert capture('configured-apks.json', ['./scripts/gradle.sh', *FLAGS,
        '-PatwApiOrigin=' + environment['backendOrigin'], '-PatwCatalogTestOrigin=' + environment['sourceOrigin'],
        ':androidApp:assembleDebug', ':androidApp:assembleDebugAndroidTest', ':androidApp:lintDebug', 'verifyModuleBoundaries']) == 0
    configs = []
    for i in range(2):
        config = dict(node='/home/tuomas/.local/share/fnm/node-versions/v24.21.0/installation/bin/node',
            appiumMain='/tmp/opencode/atw-device-tools-932550a0/appium/node_modules/appium/build/lib/main.js',
            appiumHome='/tmp/opencode/atw-device-tools-932550a0/driver', androidHome='/home/tuomas/Android/Sdk',
            linuxAdb='/home/tuomas/Android/Sdk/platform-tools/adb', windowsSdk='C:\\Android',
            powershell='/mnt/c/WINDOWS/System32/WindowsPowerShell/v1.0/powershell.exe',
            adbPort=15138+i, consolePort=5564+i*2, appiumPort=14853+i, systemPort=18310+i)
        configs.append(config)
    with d.lock():
        for i, (directory, config) in enumerate(zip(devdirs, configs)):
            try: d.provision(directory, config, 240)
            except d.Refusal as error:
                # Provisioned second guest can be auto-discovered before explicit pair exists.
                record(f'provision-{i}-diagnostic.json', dict(error=str(error)))
                assert i == 1 and str(error) == 'Foreign device on dedicated ADB refused', str(error)
            values.append(d.load(directory))
        reset_runner.pair(*values)
        for v in values: d.wait(v, 180)
        apk = ROOT / 'androidApp/build/outputs/apk/debug/androidApp-debug.apk'
        for v in values: h.prepare(v, str(envdir), str(apk), 180)
        # One independent scenario reset, not a reset at any retained recovery checkpoint.
        device_runner.fresh(values[0], 180)
        v = values[1]; serial = 'emulator-' + str(v['config']['consolePort'])
        d.adb(v, '-s', serial, 'shell', 'am', 'force-stop', a.PACKAGE)
        assert d.adb(v, '-s', serial, 'shell', 'pm', 'clear', a.PACKAGE).stdout.strip() == 'Success'
        a.operation(v, 'launch-app', 180)
        runs = []
        for i, v in enumerate(values):
            name = 'runner-' + uuid.uuid4().hex
            metadata = Path(v['directory']) / name; metadata.mkdir(mode=0o700)
            run = device_artifacts.directory(v) / name; run.mkdir(mode=0o700); runs.append(run)
            d.write_json(Path(v['directory']) / 'runner-current.json', dict(owner=v['owner'], run=name))
            ready = json.loads((Path(v['directory']) / 'ready.json').read_text())
            properties = dict(owner=v['owner'], serial='emulator-' + str(v['config']['consolePort']),
                appium=ready['appium'], adbPort=v['config']['adbPort'], systemPort=v['config']['systemPort'],
                linuxAdb=v['config']['linuxAdb'], metadataRun=str(metadata), control=str(PREFIX / 'control.py'))
            (run / 'runner.properties').write_text(''.join(f'{k}={value}\n' for k,value in properties.items()))
            record(f'device-{i}.json', dict(instance=v, ready=ready, run=str(run), installed=json.loads((Path(v['directory']) / 'app-installed.json').read_text())))
        (runs[0] / 'peer-run.txt').write_text(str(runs[1]))
        started = time.time()
        code = capture('native-journey.json', ['./scripts/gradle.sh', *FLAGS, ':e2e:deviceTest', '--tests', '*NativeLobbyCompletionTest'],
            dict(os.environ, ATW_DEVICE_RUN=str(runs[0])))
        xml = list((ROOT / 'e2e/build/test-results/deviceTest').glob('*.xml'))
        if any(p.stat().st_mtime >= started for p in xml):
            subprocess.run(['python3', 'scripts/implementation-evidence.py', 'retain-junit', str(PREFIX / 'native-junit.json'),
                *map(str, [p for p in xml if p.stat().st_mtime >= started])], check=True)
        assert code == 0, 'Native journey failed; actual captures retained'
        checked_junit(xml, 'native-junit-passed.json', started)
        for v in values: s.close(v); d.check_mappings(v, 'emulator-' + str(v['config']['consolePort']))
        v = values[0]; config = v['config']; serial = 'emulator-' + str(config['consolePort'])
        started = time.time()
        code = capture('native-accessibility.json', ['./scripts/gradle.sh', *FLAGS,
            '-PatwApiOrigin=' + environment['backendOrigin'], '-PatwCatalogTestOrigin=' + environment['sourceOrigin'],
            ':androidApp:connectedDebugAndroidTest',
            '-Pandroid.testInstrumentationRunnerArguments.class=org.asyncttwiz.android.SetupCancellationTest'],
            dict(os.environ, ANDROID_SERIAL=serial, ANDROID_ADB_SERVER_PORT=str(config['adbPort']),
                ANDROID_ADB_SERVER_ADDRESS='127.0.0.1', ADB_SERVER_SOCKET=f'tcp:127.0.0.1:{config["adbPort"]}'))
        paths = list((ROOT / 'androidApp/build/outputs/androidTest-results/connected').rglob('*.xml'))
        if any(p.stat().st_mtime >= started for p in paths):
            subprocess.run(['python3', 'scripts/implementation-evidence.py', 'retain-junit', str(PREFIX / 'accessibility-junit.json'),
                *map(str, [p for p in paths if p.stat().st_mtime >= started])], check=True)
        assert code == 0, 'Actual accessibility/hold test failed'
        checked_junit(paths, 'accessibility-junit-passed.json', started)
        assert capture('final-observation.json', ['python3', str(PREFIX / 'control.py'), 'observe']) == 0
except BaseException as error:
    failure = error
    record('lifecycle-failure-' + token + '.json', dict(type=type(error).__name__, error=str(error)))
finally:
    # A diagnostic/test failure does not abandon a live device/backend. Never delete a resource fence.
    if (envdir / 'instance.json').exists():
        environment = e.load(envdir)
        try:
            container = e.compose(environment, 'ps', '-a', '-q', 'backend').strip()
            if container and json.loads(e.docker('inspect', container))[0]['State']['Paused']: e.docker('unpause', container)
        except BaseException as error: cleanup_errors.append(str(error))
    for i, directory in reversed(list(enumerate(devdirs))):
        if (directory / 'instance.json').exists():
            try:
                with d.lock(): d.cleanup(d.load(directory))
                record(f'cleanup-device-{i}-{token}.json', json.loads((directory / 'cleaned.json').read_text()))
            except BaseException as error: cleanup_errors.append(str(error))
    if (envdir / 'instance.json').exists():
        try:
            with e.locked(envdir): e.cleanup(e.load(envdir))
            record('cleanup-environment-' + token + '.json', json.loads((envdir / 'cleaned.json').read_text()))
        except BaseException as error: cleanup_errors.append(str(error))
    for i, run in enumerate(locals().get('runs', [])):
        for path in run.iterdir():
            if path.suffix in ('.json', '.xml', '.png'): retain(path, f'device-{i}-{token}-{path.name}')
    record('cleanup-' + token + '.json', dict(success=not cleanup_errors, errors=cleanup_errors,
        environment=str(envdir), devices=list(map(str, devdirs))))
if cleanup_errors: raise RuntimeError('Owned cleanup failed: ' + str(cleanup_errors))
if failure: raise failure
print('PASS two normal APK lobby completion, actual accessibility and owned cleanup')
