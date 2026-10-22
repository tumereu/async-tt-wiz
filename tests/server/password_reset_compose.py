#!/usr/bin/env python3
"""I04a affected image/real Compose SMTP and new-store reset integration; no Android claim."""
import base64
import email
from email import policy
import http.client
import json
from pathlib import Path
import re
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from e2e import environment as e


def request(value, method, path, body=None, token=None):
    connection = http.client.HTTPConnection('127.0.0.1', value['ports']['backend'], timeout=20)
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    try:
        connection.request(method, path, None if body is None else json.dumps(body).encode(), headers)
        response = connection.getresponse()
        data = response.read()
        assert response.getheader('Cache-Control') == 'no-store'
        return response.status, json.loads(data) if data else None
    finally:
        connection.close()


def wait_delivery(value):
    def check():
        messages = e.admin(value, '/admin/messages')
        e.require(len(messages) == 1, 'Delivery not complete')
        raw = base64.b64decode(messages[0]['bodyBase64'])
        message = email.message_from_bytes(raw, policy=policy.default)
        text = message.get_content()
        match = re.search(r'https://reset.synthetic.invalid/reset-password\?token=([A-Za-z0-9_-]{43})', text)
        e.require(match is not None, 'Configured reset link absent')
        return match[1]
    return e.deadline(check, 20)


def main():
    directory = e.directory_path('/tmp/opencode/atw-e2e-' + uuid.uuid4().hex)
    value = None
    try:
        e.provision(directory, 0, 0, 90)
        value = e.load(directory)
        assert e.empty_baseline(value)['resetMailCount'] == 0
        address, password = 'reset.compose@example.invalid', ' old e\u0301😀 password \n'
        status, _ = request(value, 'POST', '/accounts', {'username': 'Compose reset wizard', 'email': address, 'password': password})
        assert status == 201
        status, session = request(value, 'POST', '/sessions', {'email': address, 'password': password})
        assert status == 201
        bearer = session['token']
        assert request(value, 'POST', '/password-reset-requests', {'email': address}) == (202, None)
        reset = wait_delivery(value)
        assert request(value, 'GET', '/account', token=bearer)[0] == 200
        assert e.database(value, "SELECT count(*) FROM reset_mail WHERE status='delivered' AND encrypted_payload IS NULL") == '1'
        # Retained backend restart, no store reset and no reissued email/token.
        e.restart(value, 'backend', 90)
        assert request(value, 'GET', '/account', token=bearer)[0] == 200
        assert len(e.admin(value, '/admin/messages')) == 1
        replacement = '\t new e\u0301😀 password \n'
        assert request(value, 'POST', '/password-resets', {'token': reset, 'password': replacement}) == (204, None)
        assert request(value, 'GET', '/account', token=bearer)[0] == 401
        assert request(value, 'POST', '/password-resets', {'token': reset, 'password': password})[0] == 400
        assert request(value, 'POST', '/sessions', {'email': address, 'password': replacement})[0] == 201
        assert e.database(value, 'SELECT auth_epoch FROM account') == '1'
        assert e.database(value, 'SELECT count(*) FROM password_reset_token WHERE consumed_at IS NOT NULL') == '1'
        # Safe logs: inspect without printing tokens, email, passwords or captured delivery bodies.
        logs = e.docker('logs', value['project'] + '-backend')
        for secret in (bearer, reset, address, password, replacement):
            assert secret not in logs, 'Secret in service logs'
        print('PASS local real OCI/Compose V3 readiness, ordinary account/reset APIs, actual synthetic SMTP configured HTTPS link, payload deletion, retained backend restart, successful-only revocation, reuse refusal and exact new-password login; no secret logs', flush=True)
        e.reset(value, 90)
        baseline = e.empty_baseline(value)
        assert baseline['resetTokenCount'] == baseline['resetMailCount'] == 0
        assert e.admin(value, '/admin/messages') == []
        print('PASS scenario reset quiesces actual reset sender and independently empties account/session/reset-token/reset-mail stores and mailbox under V1/V2/V3', flush=True)
        print('INPUT IDENTITIES', json.dumps({'server': value['images']['server'], 'adapters': value['images']['adapters'],
                                            'inputs': value['inputs']}, sort_keys=True), flush=True)
    finally:
        if directory.exists():
            if value is None:
                value = e.load(directory)
            capture = e.capture(value)
            # Do not print generic capture: mailbox bodies are intentionally observation-only secrets.
            safe = json.loads(capture.read_text())
            print('SAFE FINAL CAPTURE', json.dumps({k: safe[k] for k in ('images', 'implementedWorkers', 'absentWorkers')}, sort_keys=True), flush=True)
            e.cleanup(value)
            assert not e.owned(value)
            print('CLEANUP exact owned Compose processes/relay/containers/network/volumes removed; no external resources or devices used', flush=True)


if __name__ == '__main__':
    main()
