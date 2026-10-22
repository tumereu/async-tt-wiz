"""I01f parent evidence audit only; no build, service, device or scenario launch."""
import hashlib
import json
from pathlib import Path
import runpy
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PREFIX = 'docs/agent/implementation-runs/20261022T051745Z-e3d640da'
STATE = 'docs/agent/implementation-state.json'
START = 'dfae6f876fb30ab14f9d7b5174a7fded9bb33e30'
FIRST = 'docs/agent/implementation-runs/20261022T044527Z-56557333'
SECOND = 'docs/agent/implementation-runs/20261022T044934Z-c307edee'
CHILDREN = {'I01f1': (FIRST, 'a6dbeed7f2fa322490a9af44822a780927ec13ed'),
            'I01f2': (SECOND, START)}


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def load(path):
    return json.loads((ROOT / path).read_text())


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def emit(label, value):
    print(label, json.dumps(value, sort_keys=True))


def output(path):
    raw = (ROOT / path).read_bytes()
    try:
        value = json.loads(raw)
    except ValueError:
        return raw.decode()
    if isinstance(value, dict) and value.get('format') == 'lossless-utf8-output-v1':
        restored = value['output'].encode()
        assert sha(restored) == value['originalSha256'] and len(restored) == value['originalBytes']
        return restored.decode()
    if isinstance(value, dict) and 'outputSha256' in value:
        assert sha(value['output'].encode()) == value['outputSha256']
        return value['output']
    return raw.decode()


def audit():
    before = json.loads(git('show', START + ':' + STATE))
    driver = runpy.run_path(str(ROOT / 'scripts/implementation-loop.py'))
    assert driver['selection'](before) == {'mode': 'implement', 'target': 'I01f', 'findingIds': []}
    tasks = {task['id']: task for task in before['tasks']}
    assert before['mode'] == 'active' and before['authorization'] == 'authorized'
    assert not before['findings'] and before['activeRun'] is None
    assert all(tasks[key]['status'] == 'implemented' for key in tasks['I01f']['children'] + tasks['I01f']['dependsOn'])
    for task, (prefix, commit) in CHILDREN.items():
        report = load(prefix + '.json')
        assert tasks[task]['implementationRecord'] == prefix + '.json' and report['target'] == task
        assert git('show', commit + ':' + prefix + '.json') == (ROOT / (prefix + '.json')).read_bytes()
        checks = []
        for check in report['checks']:
            path = check['evidence']
            raw = (ROOT / path).read_bytes()
            assert git('show', commit + ':' + path) == raw
            output(path)  # Validate any lossless output envelope, including diagnostic output.
            assert check['kind'] != 'required' or check['exitCode'] == 0
            checks.append({'path': path, 'kind': check['kind'], 'exitCode': check['exitCode'], 'sha256': sha(raw)})
        emit('REUSED CHILD (historical executions, not reruns)', {
            'task': task, 'record': prefix + '.json', 'savedCommit': commit,
            'codeStartCommit': report['startCommit'], 'checks': checks})

    # The later child exercises the actual Appium/storage seam with the complete
    # controller. Only its reported host-runner additions supersede I01f1 inputs.
    delta = git('diff', '--name-only', CHILDREN['I01f1'][1], START).decode().splitlines()
    assert set(delta) <= set(load(SECOND + '.json')['changedPaths'])
    emit('I01f2 CHANGES ALREADY COVERED BY ITS AFFECTED CHECKS', delta)
    scopes = ['androidApp', 'client-storage', 'core', 'protocol', 'server', 'e2e', 'gradle',
              'tests', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties',
              'gradlew', 'gradlew.bat', 'scripts/gradle.sh', 'scripts/run-device-tests.py',
              'scripts/e2e-devices.py', 'scripts/e2e-environment.py', 'scripts/install-device-tools.py']
    identities = {}
    for path in git('ls-tree', '-r', '--name-only', START, '--', *scopes).decode().splitlines():
        if path.endswith('.md'):
            continue
        saved, current = git('show', START + ':' + path), (ROOT / path).read_bytes()
        if path == 'gradlew.bat':
            assert b'gradlew.bat text eol=crlf' in (ROOT / '.gitattributes').read_bytes()
            current = current.replace(b'\r\n', b'\n')
        assert saved == current, path
        identities[path] = sha(saved)
    emit('CURRENT COMMITTED SOURCE/BUILD/FIXTURE/TOOL IDENTITIES', identities)
    manifest = load(SECOND + '/input-identities.json')
    differences = []
    for path, digest in manifest.items():
        if '/build/' not in path and sha((ROOT / path).read_bytes()) != digest:
            differences.append(path)
    assert differences == ['e2e/device_runner.py'], differences
    regression = output(SECOND + '/fresh-barrier-recheck.txt')
    assert 'Ran 11 tests' in regression and '\nOK\n' in regression
    assert 'test_failed_compose_reset_prevents_android_clear' in regression
    assert 'test_run_publishes_success_only_after_cleanup_and_invalidates_stale_handoff' in regression
    emit('RECONCILED POST-DEVICE DELTA', {
        'paths': differences, 'evidence': SECOND + '/fresh-barrier-recheck.txt',
        'scope': 'Existing child explicitly records final environment-lock/fail-closed-marker refinements checked by 11 host regressions, not a new device execution.'})

    startup = next((ROOT / SECOND).glob('startup-runner-*'))
    storage = next((ROOT / SECOND).glob('storage-runner-*'))
    for directory, count in [(startup, 1), (storage, 14)]:
        cases = []
        for file in directory.glob('result-*'):
            cases.extend(ET.fromstring(output(str(file.relative_to(ROOT)))).iter('testcase'))
        assert len(cases) == count
        assert not any(list(case.iter(tag)) for case in cases for tag in ['failure', 'error', 'skipped'])
        assert load(str(directory.relative_to(ROOT)) + '/exit.json')['exitCode'] == 0
        if count == 14:
            assert {case.get('classname').rsplit('.', 1)[-1] for case in cases} == {'StorageDriverTest', 'StorageViewModelTest', 'StartupTest'}
        ready = load(str(directory.relative_to(ROOT)) + '/ready.json')
        assert ready['app']['api'] == 29 and ready['app']['apk']['package'] == 'org.asyncttwiz.android'
        assert ready['app']['apk']['activity'] == 'org.asyncttwiz.android.MainActivity'
        assert len(ready['routes']['probes']) == 17
        assert all(probe['status'] == 404 for probe in ready['routes']['probes'] if probe['path'].startswith(('/admin/', '/health/')))
        emit('REUSED ACTUAL XML/COMBINED READINESS', {'directory': str(directory.relative_to(ROOT)),
             'tests': [case.attrib for case in cases], 'apk': ready['app']['apk'],
             'routeProbeCount': len(ready['routes']['probes']), 'serial': ready['app']['serial']})
    session = load(str(startup.relative_to(ROOT)) + '/session.json')
    assert load(str(startup.relative_to(ROOT)) + '/session-closed.json')['sessionId'] == session['sessionId']
    assert session['capabilities']['appium:deviceApiLevel'] == 29
    assert session['forward'] == [session['serial'], 'tcp:18210', 'tcp:6790']
    # Appium uses widget-class tags; adb uiautomator dump uses generic node tags.
    texts = [node.get('text') for node in ET.parse(startup / 'startup.xml').iter()]
    assert 'Async Tabletop Wizards' in texts
    assert (startup / 'startup.png').read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
    emit('REUSED ACTUAL APPIUM SESSION/CAPTURE', {'session': session, 'pngSha256': sha((startup / 'startup.png').read_bytes())})
    fresh = load(SECOND + '/fresh-data-commands.json')
    assert len(fresh) == 4
    assert sum('clear' in item['command'] and item['exitCode'] == 0 and item['output'].strip() == 'Success' for item in fresh) == 2
    assert sum(item['exitCode'] == 1 and 'No such file' in item['output'] for item in fresh) == 2
    clean = load(SECOND + '/device-cleaned.json')
    assert clean['currentProcesses'] == [None, None, None] and clean['listeners'] == []
    assert clean['state']['cleaned'] and clean['state']['nativeWatchdogExitObserved']
    assert all(load(SECOND + '/environment-cleaned.json')[key] for key in ['ownedResourcesRemoved', 'portsRebindable', 'temporarySecretsRemoved'])
    assert 'PASS exact owned Appium/ADB/AVD/watchdog/launchers/routes/system-port and Compose resources stopped' in output(SECOND + '/owned-cleanup.txt')
    emit('REUSED FRESH DATA/EXACT CLEANUP', {'commands': fresh, 'environment': load(SECOND + '/environment-cleaned.json')})
    emit('DISTINCT ARTIFACT STAGES (not asserted byte-identical)', {
        'AppiumPreflightInstalledApkSha256': load(SECOND + '/device-app-installed.json')['apk']['sha256'],
        'postConnectedBuildApkSha256': manifest['androidApp/build/outputs/apk/debug/androidApp-debug.apk'],
        'postConnectedBuildTestApkSha256': manifest['androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk'],
        'qualification': 'Normal source/storage inputs unchanged. Connected Gradle execution built/installed its normal variant; preflight identity is not a read-back of that later install. Global reproducibility/upgrade assertions remain I34/I35.'})
    assert 'preferIPv4Stack=true' in output(str(startup.relative_to(ROOT)) + '/gradle.txt')
    assert tasks['I01f3']['status'] == 'pending' and tasks['I01f3']['batch'] == 'B8'
    assert tasks['I01f3']['dependsOn'] == ['I01f2', 'I31'] and 'I01f3' in tasks['I34']['dependsOn']
    print('PASS I01f children reconcile: actual one-device normal-APK Appium/14 storage tests, existing routes/reset/restart/native cleanup and documented commands. No missing affected integration seam; no device/build/fault rerun. I01 parent, B1 account/review, B8 multi-device/I34, I33/I35 and external gates remain pending; diagnostics are not passes.')


def validate():
    before = json.loads(git('show', START + ':' + STATE))
    state, report = load(STATE), load(PREFIX + '.json')
    driver = runpy.run_path(str(ROOT / 'scripts/implementation-loop.py'))
    choice = {'mode': 'implement', 'target': 'I01f', 'findingIds': []}
    driver['validate_state'](state)
    driver['validate_transition'](before, state, choice, report)
    assert state['runs'] == before['runs'] + [PREFIX + '.json'] and state['activeRun'] is None
    for old, new in zip(before['tasks'], state['tasks']):
        assert old == new if old['id'] != 'I01f' else new == {
            **old, 'status': 'implemented', 'implementationRecord': PREFIX + '.json', 'qualification': new['qualification']}
    for key in before:
        if key not in {'tasks', 'runs'}:
            assert before[key] == state[key], key
    assert driver['selection'](state) == {'mode': 'implement', 'target': 'I01', 'findingIds': []}
    paths = set(git('diff', '--name-only', START).decode().splitlines())
    paths.update(git('ls-files', '--others', '--exclude-standard').decode().splitlines())
    assert paths == set(report['changedPaths'])
    for path in paths:
        assert path == STATE or path.startswith(PREFIX)
        raw = (ROOT / path).read_text()
        assert raw.endswith('\n') and all(line == line.rstrip() for line in raw.splitlines()), path
        if path.endswith('.json'):
            json.loads(raw)
    assert not git('diff', '--cached', '--name-only').strip()
    subprocess.run(['git', 'diff', '--check'], cwd=ROOT, check=True)
    assert 'PASS I01f children reconcile' in output(PREFIX + '-audit.txt')
    print('PASS selected I01f-only transition, next I01 selection, exact owned paths, JSON/whitespace and retained evidence; authorization/model/review/runner/permission/release gates unchanged. No Markdown/UI document edit.')


if __name__ == '__main__':
    {'audit': audit, 'validate': validate}[sys.argv[1]]()
