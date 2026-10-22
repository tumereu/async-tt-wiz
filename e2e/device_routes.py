"""Owned public-only ADB reverse transport. No app client or trust changes."""
import base64
import hashlib
import json
from pathlib import Path
import re
import time

from e2e import devices as d, environment as e


def file(value):
    return Path(value['directory']) / 'routes.json'


def invalidate(value):
    d.invalidate(value)
    for name in ('routes-ready.json', 'app-ready.json', 'combined-ready.json'):
        (Path(value['directory']) / name).unlink(missing_ok=True)


def load(value):
    path = file(value)
    if not path.exists():
        return None
    d.require(not path.is_symlink(), 'Unsafe route ownership record')
    result = json.loads(path.read_text())
    return validate_record(value, result)


def validate_record(value, result):
    d.require(result['owner'] == value['owner'] and
              result['serial'] == 'emulator-' + str(value['config']['consolePort']), 'Foreign route owner/serial')
    ports = result['environment']['ports']
    d.require(set(ports) == {'backend', 'source'} and len(set(ports.values())) == 2 and
              all(type(p) is int and 1024 <= p <= 65534 for p in ports.values()), 'Invalid public route ports')
    d.require(set(ports.values()).isdisjoint({value['config'][k] for k in
              ('adbPort', 'consolePort', 'appiumPort', 'systemPort')} | {value['config']['consolePort'] + 1}),
              'Route/service port collision')
    d.require(len(result['mappings']) <= 2 and len({m['role'] for m in result['mappings']}) == len(result['mappings']),
              'Duplicate route intent')
    for mapping in result['mappings']:
        port = ports[mapping['role']]
        keys = {'role', 'local', 'remote', 'state'} | ({'transport'} if mapping['state'] == 'created' else set())
        d.require(set(mapping) == keys and mapping['local'] == mapping['remote'] == f'tcp:{port}' and
                  mapping['state'] in ('creating', 'created') and
                  (mapping['state'] != 'created' or re.fullmatch(r'[A-Za-z0-9_.:-]+', mapping['transport'])),
                  'Changed route intent')
    return result


def mappings(value, serial, *, complete=False):
    """No forward mappings are ours; reverse transport IDs come from adbd."""
    forward = d.adb(value, 'forward', '--list').stdout.strip()
    raw = d.adb(value, '-s', serial, 'reverse', '--list').stdout.strip()
    d.require(not forward, 'Foreign/unrecorded ADB mapping refused')
    record = load(value)
    allowed = {} if record is None else {
        (m['local'], m['remote']): m for m in record['mappings'] if m['state'] == 'created'}
    actual = []
    for line in raw.splitlines():
        fields = line.split()
        d.require(len(fields) == 3 and tuple(fields[1:]) in allowed,
                  'Foreign/unrecorded ADB mapping refused (including ambiguous pending intent)')
        # Record adbd's opaque transport ID after creation, then require it unchanged.
        d.require(fields[0] == allowed[tuple(fields[1:])]['transport'], 'Changed reverse transport identity')
        actual.append(fields)
    d.require(len({tuple(m[1:]) for m in actual}) == len(actual), 'Duplicate reverse mapping')
    if complete:
        d.require(record is not None and len(record['mappings']) == len(actual) == 2 and
                  all(m['state'] == 'created' for m in record['mappings']), 'Owned public routes missing/incomplete')
    return {'forward': [], 'reverse': actual}


def target(directory):
    directory = e.directory_path(directory)
    d.require(not (directory / 'cleaned.json').exists(), 'Cleaned Compose target refused')
    value = e.load(directory)
    ports = value['ports']
    d.require(set(ports) == {'backend', 'source'} and len(set(ports.values())) == 2 and
              all(type(p) is int and 1024 <= p <= 65534 for p in ports.values()), 'Invalid Compose public ports')
    for role in ports:
        d.require(value[role + 'Origin'] == f'http://127.0.0.1:{ports[role]}',
                  'Only exact same-port public loopback origins are allowed')
    return value


def identity(value):
    return {'directory': value['directory'], 'owner': value['owner'], 'ports': value['ports'],
            'instanceSha256': e.sha(Path(value['directory']) / 'instance.json')}


def attached(value):
    record = load(value)
    d.require(record is not None, 'No attached routes; use route-environment explicitly')
    env = target(record['environment']['directory'])
    d.require(identity(env) == record['environment'], 'Changed attached Compose identity/ports refused')
    return record, env


def listeners(value, serial):
    output = d.adb(value, '-s', serial, 'shell', 'ss -ltn').stdout
    result = []
    for line in output.splitlines():
        fields = line.split()
        if fields and fields[0] == 'LISTEN':
            address, port = fields[3].rsplit(':', 1)
            result.append({'address': address, 'port': int(port)})
    return result


def device_http(value, serial, port, path):
    """Bounded shell-only HTTP diagnostic; never an application acceptance call.

    Keep stdin alive briefly: toybox nc half-closes on EOF, whereas the existing
    public relay intentionally closes both directions on EOF. Readiness is the
    parsed response, NOT this delay. HTTP/1.1 is required by the synthetic adapter.
    """
    d.require(type(port) is int and 1024 <= port <= 65534 and
              re.fullmatch(r'/[A-Za-z0-9/_.-]*', path), 'Unsafe diagnostic request')
    command = (f"(printf 'GET {path} HTTP/1.1\\r\\nHost: 127.0.0.1:{port}\\r\\n"
               f"Connection: close\\r\\n\\r\\n'; sleep 2) | "
               f"toybox nc -w 3 -W 3 127.0.0.1 {port} | base64")
    output = d.adb(value, '-s', serial, 'shell', command, timeout=12).stdout
    raw = base64.b64decode(''.join(output.split()), validate=True)
    d.require(len(raw) <= 2 * 1024 * 1024 and b'\r\n\r\n' in raw, 'Device HTTP response missing/truncated')
    head, body = raw.split(b'\r\n\r\n', 1)
    lines = head.decode('ascii').split('\r\n')
    d.require(re.fullmatch(r'HTTP/1\.[01] [0-9]{3} .*', lines[0]), 'Invalid device HTTP status')
    headers = dict(line.lower().split(':', 1) for line in lines[1:])
    d.require('transfer-encoding' not in headers and int(headers['content-length']) == len(body),
              'Device HTTP framing/truncated body')
    return int(lines[0].split()[1]), body, headers


def probe(value, record, env):
    serial = record['serial']
    observed = []

    def check(role, path, expected, mime=None):
        port = env['ports'][role]
        status, body, headers = device_http(value, serial, port, path)
        d.require((status, body) == expected, 'Device public endpoint status/bytes mismatch: ' + role + path)
        if mime:
            d.require(headers['content-type'].strip() == mime, 'Device source MIME mismatch')
        observed.append({'role': role, 'path': path, 'port': port, 'status': status,
                         'bytes': len(body), 'sha256': hashlib.sha256(body).hexdigest()})
        return body

    check('backend', '/', (404, b'Not found\n'))
    inventory = e.admin(env, '/admin/inventory')
    d.require(inventory['origin'] == env['sourceOrigin'], 'Changed source art origin')
    for dataset in ('prism', 'quilt'):
        for name, mime in (('Meta.json', 'application/json'), ('AllPrintings.json.gz', 'application/gzip')):
            path = '/' + dataset + '/' + name
            expected = e.request('127.0.0.1', env['ports']['source'], path)
            d.require(expected[0] == 200, 'Source not ready')
            check('source', path, expected, mime)
        card = next(k for k in sorted(inventory['files']) if k.startswith(dataset + '/v1/scryfall/cards/') and
                    dataset + '/v1/art/' + Path(k).stem + '.png' in inventory['files'])
        expected = e.request('127.0.0.1', env['ports']['source'], '/' + card)
        body = check('source', '/' + card, expected, 'application/json')
        d.require(hashlib.sha256(body).hexdigest() == inventory['files'][card], 'Source printing hash changed')
        obj = json.loads(body)
        uris = [uri for face in obj.get('card_faces', [obj]) for uri in face['image_uris'].values()]
        d.require(uris and all(uri.startswith(env['sourceOrigin'] + '/') for uri in uris), 'Source art origin rewritten')
        path = uris[0].removeprefix(env['sourceOrigin'])
        expected = e.request('127.0.0.1', env['ports']['source'], path)
        body = check('source', path, expected, 'image/png')
        d.require(expected[0] == 200 and hashlib.sha256(body).hexdigest() == inventory['files'][path[1:]],
                  'Source art hash/status changed')
    for role in ('backend', 'source'):
        for path in ('/health/ready', '/admin/state', '/admin/reset', '/admin/messages'):
            expected = e.request('127.0.0.1', env['ports'][role], path)
            d.require(expected[0] == 404, 'Private interface exposed on public route')
            check(role, path, expected)
    return observed


def validate(value, timeout):
    invalidate(value)
    record, env = attached(value)
    mappings(value, record['serial'], complete=True)
    guest = listeners(value, record['serial'])
    for port in env['ports'].values():
        d.require([row for row in guest if row['port'] == port] == [{'address': '127.0.0.1', 'port': port}],
                  'Missing/non-loopback device route listener')
    with e.locked(Path(env['directory'])):
        # Real current health/schema/writer, private isolation and owned host relay.
        host = e.wait(env, min(timeout, 120))
        observed = probe(value, record, env)
        mappings(value, record['serial'], complete=True)
        d.require(identity(target(env['directory'])) == record['environment'], 'Compose identity changed during probe')
    result = {'owner': value['owner'], 'serial': record['serial'], 'environment': record['environment'],
              'backend': host['backend'], 'source': host['source'], 'checkedAt': time.time(),
              'environmentRelay': json.loads((Path(env['directory']) / 'relay.json').read_text()),
              'deviceListeners': [row for row in guest if row['port'] in env['ports'].values()],
              'transport': 'Same-port ADB reverse to dedicated Windows ADB loopback; measured WSL loopback forwarding; no added relay',
              'probes': observed, 'scope': 'Transport infrastructure only; no APK/client/UI/instrumentation acceptance'}
    d.write_json(Path(value['directory']) / 'routes-ready.json', result)
    return result


def attach(value, directory, timeout):
    invalidate(value)
    d.require(not file(value).exists(), 'Routes already attached or partial; explicitly unroute before reattaching')
    d.infrastructure(value, timeout)
    invalidate(value)  # Never keep the services-only marker after attachment begins.
    env = target(directory)
    serial = 'emulator-' + str(value['config']['consolePort'])
    d.check_mappings(value, serial)
    d.require(not any(row['port'] in env['ports'].values() for row in listeners(value, serial)),
              'Busy device public port refused')
    with e.locked(Path(env['directory'])):
        e.wait(env, min(timeout, 120))
        record = {'owner': value['owner'], 'serial': serial, 'environment': identity(env), 'mappings': []}
        # Validate all ports/identities before writing recoverable attachment intent.
        validate_record(value, record)
        d.write_json(file(value), record)
        for role, port in env['ports'].items():
            mapping = {'role': role, 'local': f'tcp:{port}', 'remote': f'tcp:{port}', 'state': 'creating'}
            record['mappings'].append(mapping)
            d.write_json(file(value), record)  # Intent BEFORE any ADB mutation.
            result = d.adb(value, '-s', serial, 'reverse', '--no-rebind', mapping['local'], mapping['remote'], check=False)
            if result.returncode != 0:
                # A rejected no-rebind never owns the competing mapping. Keep only
                # successful earlier entries, so explicit cleanup cannot remove it.
                record['mappings'].pop()
                d.write_json(file(value), record)
                raise d.Refusal('No-rebind route creation failed; retained partial ownership, inspect before unroute')
            actual = [line.split() for line in d.adb(value, '-s', serial, 'reverse', '--list').stdout.splitlines()]
            matches = [row for row in actual if len(row) == 3 and row[1:] == [mapping['local'], mapping['remote']]]
            d.require(len(matches) == 1, 'Created route not observable; ambiguous intent retained for diagnosis')
            mapping.update(state='created', transport=matches[0][0])
            d.write_json(file(value), record)


def route(value, directory, timeout):
    attach(value, directory, timeout)
    return d.wait(value, timeout)


def unroute(value):
    invalidate(value)
    record = load(value)
    from e2e import device_handoff
    if record is None:
        d.require(device_handoff.file(value).exists(), 'No routes attached')
        d.check_windows(value, d.windows(value, 'snapshot'))
        d.check_mappings(value, 'emulator-' + str(value['config']['consolePort']))
        device_handoff.detach(value)
        return {'removed': True, 'scope': 'Failed preparation detached; call wait for fresh services-only readiness'}
    d.check_windows(value, d.windows(value, 'snapshot'))
    # No environment availability requirement for recovery/removal. Refuse foreign
    # mappings BEFORE touching even our own routes; never --remove-all.
    inventory = mappings(value, record['serial'])['reverse']
    for mapping in list(record['mappings']):
        present = [row for row in inventory if row[1:] == [mapping['local'], mapping['remote']]]
        if present:
            d.adb(value, '-s', record['serial'], 'reverse', '--remove', mapping['local'])
        record['mappings'].remove(mapping)
        d.write_json(file(value), record)
    mappings(value, record['serial'])
    d.write_json(Path(value['directory']) / 'routes-removed.json', record)
    file(value).unlink()
    device_handoff.detach(value)
    return {'removed': True, 'scope': 'Explicitly detached; call wait for fresh services-only readiness'}
