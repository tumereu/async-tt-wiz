"""Normal scaffold install/observation on the controller's synthetic guest only.

No Appium session, instrumentation, action injection, network route or data reset.
Private captures are snapshots, not enduring health or full-product acceptance.
"""
import hashlib
import json
from pathlib import Path
import re
import secrets
import sqlite3
import struct
import subprocess
import time
import xml.etree.ElementTree as ET

from e2e import devices as d

PACKAGE = 'org.asyncttwiz.android'
ACTIVITY = PACKAGE + '.MainActivity'
COMPONENT = PACKAGE + '/' + ACTIVITY
TITLE = 'Async Tabletop Wizards'
SUBTITLE = 'Specialized tabletop simulator for asynchronous wizardry'
DB = 'no_backup/client-storage/replica.db'
TABLES = ('accepted_cursor', 'accepted_record', 'personal_state', 'public_cache')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify_apk(value, path):
    """Use the same explicit SDK as the service controller, with API29 verification."""
    path = Path(path)
    d.require(path.is_absolute() and path.is_file() and not path.is_symlink(), 'Explicit regular APK required')
    tools = Path(value['config']['androidHome']) / 'build-tools/36.0.0'
    for name in ('aapt2', 'apksigner'):
        d.require((tools / name).is_file(), 'Qualified SDK36 build tool missing: ' + name)
    badging = d.run(value, [str(tools / 'aapt2'), 'dump', 'badging', str(path)], timeout=30).stdout
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", badging, re.M)
    launcher = re.search(r"^launchable-activity: name='([^']+)'", badging, re.M)
    d.require(package is not None and package[1] == PACKAGE, 'Only the normal application package is allowed')
    d.require(launcher is not None and launcher[1] == ACTIVITY, 'Only the production MainActivity launcher is allowed')
    d.require("minSdkVersion:'29'" in badging and "targetSdkVersion:'36'" in badging, 'Normal API29/SDK36 APK required')
    manifest = d.run(value, [str(tools / 'aapt2'), 'dump', 'xmltree', str(path), '--file', 'AndroidManifest.xml'], timeout=30).stdout
    d.require('testOnly' not in manifest and 'E: instrumentation' not in manifest and
              'application-debuggable' in badging, 'Normal debug APK required, not an instrumentation/test-only APK')
    signature = d.run(value, [str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs',
                              '--min-sdk-version', '29', str(path)], timeout=30).stdout
    signers = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})$', signature, re.M)
    d.require(bool(signers), 'Verified API29 signer identity missing')
    return {'sha256': sha(path), 'bytes': path.stat().st_size, 'package': package[1],
            'versionCode': int(package[2]), 'versionName': package[3], 'activity': launcher[1],
            'minSdk': 29, 'targetSdk': 36, 'signerSha256': [s.lower() for s in signers]}


def binary_adb(value, serial, path, *arguments, timeout=30):
    """Do not decode binary ADB streams or put APK/DB/pixel bytes in command logs."""
    config = value['config']
    with d.socket.create_connection(('127.0.0.1', config['adbPort']), timeout=2):
        pass
    command = [config['linuxAdb'], '-H', '127.0.0.1', '-P', str(config['adbPort']), '-s', serial, *arguments]
    timed_out = False
    with path.open('xb') as output:
        try:
            result = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=output, stderr=subprocess.PIPE, timeout=timeout)
            code, error = result.returncode, result.stderr.decode(errors='replace')
        except subprocess.TimeoutExpired as failure:
            timed_out = True
            code, error = None, (failure.stderr or b'').decode(errors='replace')
    record = {'command': command, 'exitCode': code, 'timedOut': timed_out,
              'stderr': error, 'artifact': str(path), 'bytes': path.stat().st_size, 'sha256': sha(path)}
    with (Path(value['directory']) / 'commands.jsonl').open('a') as log:
        log.write(json.dumps(record) + '\n')
    d.require(not timed_out and code == 0 and path.stat().st_size > 0, 'Bounded binary ADB capture failed; inspect retained evidence')
    return record


def installed(value, serial, expected, directory):
    result = d.adb(value, '-s', serial, 'shell', 'pm', 'path', PACKAGE).stdout.strip().splitlines()
    d.require(len(result) == 1 and re.fullmatch(r'package:/data/app/[A-Za-z0-9_+=/.-]+/base.apk', result[0]),
              'One installed normal base APK required')
    path = directory / 'installed.apk'
    binary_adb(value, serial, path, 'exec-out', 'cat', result[0][8:], timeout=60)
    actual = verify_apk(value, path)
    d.require(actual == expected, 'Installed normal APK identity differs from the verified input')
    resolved = d.adb(value, '-s', serial, 'shell', 'cmd', 'package', 'resolve-activity', '--brief',
                     '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LAUNCHER', PACKAGE).stdout
    d.require(bool(resolved.strip()) and resolved.strip().splitlines()[-1] in (COMPONENT, PACKAGE + '/.MainActivity'),
              'Installed production launcher differs')
    return actual


def app_record(value):
    record = json.loads((Path(value['directory']) / 'app-installed.json').read_text())
    d.require(record['owner'] == value['owner'] and record['apk']['package'] == PACKAGE and
              record['apk']['activity'] == ACTIVITY, 'Foreign app installation record refused')
    path = Path(value['directory']) / 'normal.apk'
    d.require(path.is_file() and not path.is_symlink() and sha(path) == record['apk']['sha256'],
              'Retained verified APK changed or is missing')
    return record


def attempt(value, stage):
    directory = Path(value['directory']) / ('app-' + secrets.token_hex(8))
    directory.mkdir(mode=0o700)
    d.write_json(Path(value['directory']) / 'app-progress.json', {'stage': stage, 'evidenceDirectory': str(directory)})
    return directory


def install(value, serial, apk):
    # Revoke prior installation/startup success before verification or mutation.
    root = Path(value['directory'])
    (root / 'app-installed.json').unlink(missing_ok=True)
    directory = attempt(value, 'verify-install')
    source = Path(apk)
    expected = verify_apk(value, source)
    snapshot = directory / 'input.apk'
    with snapshot.open('xb') as output:
        output.write(source.read_bytes())
    d.require(verify_apk(value, snapshot) == expected, 'APK changed during verification/copy')
    result = d.adb(value, '-s', serial, 'install', '-r', str(snapshot), timeout=120)
    d.require('Success' in result.stdout.splitlines(), 'Normal APK installation did not report success')
    actual = installed(value, serial, expected, directory)
    # Atomic retained artifact publication; never fall back to an older input.
    snapshot.replace(root / 'normal.apk')
    record = {'owner': value['owner'], 'serial': serial, 'apk': actual, 'checkedAt': time.time(),
              'evidenceDirectory': str(directory), 'scope': 'Verified normal APK install only; no startup observation'}
    d.write_json(root / 'app-installed.json', record)
    return record


def visible(hierarchy):
    d.require(hierarchy.tag == 'hierarchy', 'Invalid native UI hierarchy')
    texts = [node.get('text', '') for node in hierarchy.iter('node') if node.get('package') == PACKAGE]
    return TITLE in texts and SUBTITLE in texts and not any(
        text.startswith(('Opening local storage', 'Local storage could not', 'Local data needs', 'Retry')) for text in texts)


def foreground(value, serial):
    output = d.adb(value, '-s', serial, 'shell', 'dumpsys', 'activity', 'activities').stdout
    match = re.search(r'mResumedActivity:.*?\s(' + re.escape(PACKAGE) + r'/(?:\.MainActivity|' + re.escape(ACTIVITY) + r'))\s', output)
    pid = d.adb(value, '-s', serial, 'shell', 'pidof', PACKAGE, check=False).stdout.strip()
    return bool(match and re.fullmatch(r'\d+', pid)), {'resumed': match[1] if match else None, 'pid': pid}


def hierarchy(value, serial, directory, index):
    # Unique guest artifact; no shared /sdcard/window.xml or remove-all cleanup.
    remote = '/data/local/tmp/atw-' + secrets.token_hex(16) + '.xml'
    local = directory / ('hierarchy-' + str(index) + '.xml')
    try:
        d.adb(value, '-s', serial, 'shell', 'uiautomator', 'dump', remote, timeout=30)
        binary_adb(value, serial, local, 'exec-out', 'cat', remote)
        try:
            return local, ET.fromstring(local.read_bytes())
        except ET.ParseError:
            raise d.Refusal('Malformed native hierarchy; evidence retained') from None
    finally:
        d.adb(value, '-s', serial, 'shell', 'rm', '-f', remote, check=False)


def storage(value, serial, directory):
    """Read-only corroboration, not a storage instrumentation substitute.

    The idle scaffold has no ongoing feature writes. Refuse live WAL/journal data
    rather than claiming a coherent copy of a changing future product database.
    """
    listing = d.adb(value, '-s', serial, 'shell', 'run-as', PACKAGE, 'ls', '-l', 'no_backup/client-storage').stdout
    for line in listing.splitlines():
        fields = line.split()
        if fields and fields[-1] in ('replica.db-wal', 'replica.db-journal'):
            d.require(len(fields) >= 5 and fields[4] == '0', 'Active SQLite journal: cannot claim an idle raw database snapshot')
    path = directory / 'replica.db'
    inode = d.adb(value, '-s', serial, 'shell', 'run-as', PACKAGE, 'stat', '-c', '%i', DB).stdout.strip()
    d.require(re.fullmatch(r'\d+', inode) is not None, 'Production database file identity missing')
    binary_adb(value, serial, path, 'exec-out', 'run-as', PACKAGE, 'cat', DB)
    try:
        with sqlite3.connect(path.as_uri() + '?mode=ro&immutable=1', uri=True) as connection:
            d.require(connection.execute('PRAGMA integrity_check').fetchall() == [('ok',)], 'Captured startup database integrity failed')
            version = connection.execute('PRAGMA user_version').fetchone()[0]
            tables = [row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")]
            d.require(version == 2 and tables == sorted(TABLES), 'Production storage startup did not establish the foundation schema')
            counts = {name: connection.execute('SELECT count(*) FROM ' + name).fetchone()[0] for name in TABLES}
            logical_sha = hashlib.sha256('\n'.join(connection.iterdump()).encode()).hexdigest()
    except sqlite3.Error:
        raise d.Refusal('Read-only startup SQLite diagnostic failed; no app data modified') from None
    return {'path': DB, 'sha256': sha(path), 'bytes': path.stat().st_size, 'userVersion': version,
            'inode': inode, 'logicalSha256': logical_sha, 'tables': tables, 'rowCounts': counts,
            'readOnly': True, 'scope': 'Idle scaffold startup corroboration only'}


def observe(value, serial, directory, timeout):
    until = time.monotonic() + timeout
    index = 0
    while time.monotonic() < until:
        index += 1
        path, tree = hierarchy(value, serial, directory, index)
        is_foreground, activity = foreground(value, serial)
        d.write_json(directory / 'observation.json', {'attempt': index, 'foreground': activity, 'visibleReady': visible(tree)})
        if is_foreground and visible(tree):
            image = directory / 'screen.png'
            binary_adb(value, serial, image, 'exec-out', 'screencap', '-p')
            data = image.read_bytes()
            d.require(data[:8] == b'\x89PNG\r\n\x1a\n' and data[12:16] == b'IHDR' and len(data) > 100,
                      'Invalid screenshot; no startup handoff')
            width, height = struct.unpack('>II', data[16:24])
            d.require(width > 0 and height > 0, 'Empty screenshot geometry')
            # No acceptance based solely on old XML if the app left foreground.
            d.require(foreground(value, serial)[0], 'Production app left foreground during capture')
            result = {'owner': value['owner'], 'serial': serial, 'checkedAt': time.time(),
                      'foreground': activity, 'apk': app_record(value)['apk'], 'api': 29,
                      'evidenceDirectory': str(directory), 'hierarchy': {'path': str(path), 'sha256': sha(path)},
                      'screenshot': {'path': str(image), 'sha256': sha(image), 'width': width, 'height': height},
                      'storage': storage(value, serial, directory),
                      'scope': 'Visible normal scaffold startup only; no backend, account, Appium scenario or instrumentation acceptance'}
            d.write_json(directory / 'observed.json', result)
            d.write_json(Path(value['directory']) / 'app-ready.json', result)
            return result
        time.sleep(.25)
    raise d.Refusal('Bounded normal-app visible/storage-ready startup observation timed out; evidence retained')


def operation(value, name, timeout, apk=None):
    root = Path(value['directory'])
    (root / 'app-ready.json').unlink(missing_ok=True)
    try:
        ready = d.wait(value, timeout)  # Actual owned guest/service checks, no default ADB.
        serial = ready['device']['serial']
        if name == 'install-app':
            return install(value, serial, apk)
        record = app_record(value)
        d.require(record['serial'] == serial, 'Installed app belongs to a different guest')
        directory = attempt(value, name)
        installed(value, serial, record['apk'], directory)
        if name == 'launch-app':
            # Retained in-scenario restart: no clear, uninstall, seed or DB write.
            d.adb(value, '-s', serial, 'shell', 'am', 'force-stop', PACKAGE)
            output = d.adb(value, '-s', serial, 'shell', 'am', 'start', '-W', '-n', COMPONENT, timeout=30).stdout
            d.require('Status: ok' in output and 'Error:' not in output, 'Production MainActivity launch failed')
        return observe(value, serial, directory, timeout)
    except BaseException:
        (root / 'app-ready.json').unlink(missing_ok=True)
        raise
