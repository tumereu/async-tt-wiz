"""Combined normal-scaffold/startup and public-transport test handoff, not product E2E."""
import json
from pathlib import Path
import time

from e2e import devices as d, device_app as a, device_routes as r, environment as e


def file(value):
    return Path(value['directory']) / 'combined.json'


def load(value):
    path = file(value)
    d.require(not path.is_symlink(), 'Unsafe combined preparation record')
    result = json.loads(path.read_text())
    d.require(result['owner'] == value['owner'] and
              result['serial'] == 'emulator-' + str(value['config']['consolePort']), 'Foreign combined owner/serial')
    e.directory_path(result['environmentDirectory'])
    return result


def detach(value):
    """Called only after checked unroute or exact native cleanup, never as recovery fallback."""
    if file(value).exists():
        record = load(value)
        d.write_json(Path(value['directory']) / 'combined-detached.json', record)
        file(value).unlink()
    d.invalidate(value)


def prepare(value, environment, apk, timeout):
    r.invalidate(value)
    environment = str(e.directory_path(environment))
    record = {'owner': value['owner'], 'serial': 'emulator-' + str(value['config']['consolePort']),
              'environmentDirectory': environment}
    if file(value).exists():
        prior = load(value)
        d.require(prior['environmentDirectory'] == environment,
                  'Different combined target; explicitly unroute before preparing another environment')
        record = prior
    else:
        # Durable intent BEFORE endpoint validation, route mutation or APK install.
        # Failed preparation cannot turn a later wait into services-only success.
        d.write_json(file(value), record)
    try:
        env = r.target(environment)
        expected = a.verify_apk(value, Path(apk))
        d.require('environment' not in record or record['environment'] == r.identity(env), 'Changed combined environment')
        d.require('apk' not in record or record['apk'] == expected, 'Changed combined APK; explicitly unroute first')
        record.update(environment=r.identity(env), apk=expected)
        d.write_json(file(value), record)
        if not r.file(value).exists():
            r.attach(value, environment, timeout)
        else:
            attached, _ = r.attached(value)
            d.require(attached['environment'] == record['environment'], 'Routes belong to another environment')
        a.operation(value, 'install-app', timeout, apk)
        a.operation(value, 'launch-app', timeout)
        return json.loads((Path(value['directory']) / 'ready.json').read_text())
    except BaseException:
        r.invalidate(value)
        raise


def validate(value, infrastructure, timeout):
    """Fresh bytes/launcher/UI/storage observation; never reuse app-ready.json."""
    record = load(value)
    routes = infrastructure.get('routes')
    d.require(routes is not None and 'environment' in record and 'apk' in record,
              'Incomplete combined preparation; explicitly prepare-app or unroute')
    d.require(routes['environment'] == record['environment'] and
              routes['environment']['directory'] == record['environmentDirectory'], 'Changed combined environment/route target')
    serial = infrastructure['device']['serial']
    d.require(serial == record['serial'], 'Changed combined guest')
    installed = a.app_record(value)
    d.require(installed['serial'] == serial and installed['apk'] == record['apk'], 'Changed combined normal APK installation')
    directory = a.attempt(value, 'combined-wait')
    a.installed(value, serial, record['apk'], directory)
    app = a.observe(value, serial, directory, timeout)
    # Recheck cheap identities after UI capture; all are snapshots, not leases.
    r.mappings(value, serial, complete=True)
    d.require(r.identity(r.target(record['environmentDirectory'])) == record['environment'], 'Combined environment changed during capture')
    d.check_windows(value, d.windows(value, 'snapshot'))
    result = dict(infrastructure, app=app, checkedAt=time.time(),
                  scope='Combined normal scaffold startup and public transport; no product HTTP, Kotlin/Appium scenario or instrumentation acceptance')
    d.write_json(Path(value['directory']) / 'combined-ready.json', result)
    return result
