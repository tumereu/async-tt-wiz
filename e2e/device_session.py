"""Exact Appium session/forward ownership, including interrupted-client recovery.

Records are test-only and local to the already owned device. Never remove an
unrecorded forward, infer ownership from a port alone, or delete every session.
"""
import http.client
import json
from pathlib import Path
import re

from e2e import devices as d


def request(value, method, path):
    d.require(d.appium_listener(value), 'Owned Appium listener required')
    connection = http.client.HTTPConnection('127.0.0.1', value['config']['appiumPort'], timeout=30)
    try:
        connection.request(method, path)
        response = connection.getresponse()
        body = response.read(1024 * 1024 + 1)
        d.require(len(body) <= 1024 * 1024, 'Oversized Appium response')
        result = json.loads(body)['value']
        if response.status == 404 and isinstance(result, dict) and result.get('error') == 'invalid session id':
            return None
        d.require(response.status == 200, 'Appium session request failed')
        return result
    finally:
        connection.close()


def record(value):
    pointer = Path(value['directory']) / 'runner-current.json'
    if not pointer.exists():
        return None
    d.require(not pointer.is_symlink(), 'Unsafe runner pointer')
    current = json.loads(pointer.read_text())
    d.require(current['owner'] == value['owner'] and re.fullmatch(r'runner-[a-f0-9]{32}', current['run']),
              'Foreign runner record')
    directory = pointer.parent / current['run']
    d.require(not directory.is_symlink(), 'Unsafe runner directory')
    file = directory / 'session.json'
    if not file.exists():
        return None
    d.require(not file.is_symlink(), 'Unsafe session record')
    result = json.loads(file.read_text())
    serial = 'emulator-' + str(value['config']['consolePort'])
    d.require(result['owner'] == value['owner'] and result['serial'] == serial and
              result['systemPort'] == value['config']['systemPort'] and
              re.fullmatch(r'[a-f0-9-]{36}', result['sessionId']), 'Foreign session identity')
    if 'forward' in result:
        d.require(result['forward'] == [serial, 'tcp:' + str(value['config']['systemPort']), 'tcp:6790'],
                  'Changed session forward identity')
    return directory, result


def forwards(value, serial):
    raw = d.adb(value, 'forward', '--list').stdout.strip()
    actual = [line.split() for line in raw.splitlines()]
    if not actual:
        return []
    owned = record(value)
    d.require(owned is not None, 'Foreign/unrecorded ADB forward refused')
    directory, session = owned
    d.require(not (directory / 'session-closed.json').exists() and session['serial'] == serial and
              'forward' in session and actual == [session['forward']], 'Foreign/unrecorded ADB forward refused')
    d.require(capabilities(value, session) is not None, 'Recorded Appium session is no longer alive')
    return actual


def capabilities(value, session):
    response = request(value, 'GET', '/session/' + session['sessionId'] + '/appium/capabilities')
    if response is None:
        return None
    live = response['capabilities']
    serial = session['serial']
    d.require(live['udid'] == serial and live['systemPort'] == session['systemPort'] and
              live['adbPort'] == value['config']['adbPort'] and live['remoteAdbHost'] == '127.0.0.1',
              'Live session transport differs from recorded owner')
    return live


def idle(value):
    owned = record(value)
    d.require(owned is None or capabilities(value, owned[1]) is None, 'A recorded Appium session is still active')
    d.require(not d.adb(value, 'forward', '--list').stdout.strip(), 'Foreign/surviving ADB forward refused')


def close(value):
    owned = record(value)
    if owned is None or capabilities(value, owned[1]) is None:
        d.require(not d.adb(value, 'forward', '--list').stdout.strip(), 'Unrecorded surviving forward refused')
        return
    directory, session = owned
    forwards(value, session['serial'])
    request(value, 'DELETE', '/session/' + session['sessionId'])
    d.require(capabilities(value, session) is None, 'Appium session did not close')
    d.require(not d.adb(value, 'forward', '--list').stdout.strip(), 'Appium forward did not close')
    d.write_json(directory / 'session-closed.json', {'sessionId': session['sessionId'], 'recovered': True})
