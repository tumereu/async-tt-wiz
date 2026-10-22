#!/usr/bin/env python3
"""Affected I02a normal-image account journey, reusing exact owned lifecycle resources.

No full image failure-matrix rerun, Compose, deployment or test-only product routes.
"""
import json
from pathlib import Path
import unittest
import urllib.error
import urllib.request

import image_lifecycle as image


class AccountsImage(image.ImageLifecycle):
    def account_request(self, container, method, path, body=None, token=None):
        address = image.inspect("container", container)["NetworkSettings"]["Networks"][self.network]["IPAddress"]
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        request = urllib.request.Request("http://" + address + ":8080" + path, method=method,
                                         data=None if body is None else json.dumps(body).encode(), headers=headers)
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        try:
            response = opener.open(request, timeout=20)
        except urllib.error.HTTPError as failure:
            response = failure
        with response:
            self.assertEqual("no-store", response.headers.get("Cache-Control"))
            self.assertIsNone(response.headers.get("Set-Cookie"))
            raw = response.read()
            return response.status, json.loads(raw) if raw else None

    def test_accounts(self):
        self.migrate()
        service = self.app()
        self.status(service, "/health/ready", 200)
        password = " \tImage päss e\u0301😀\n"
        email = "Private.Image@EXAMPLE.invalid"
        status, account = self.account_request(service, "POST", "/accounts",
                                             {"username": "Image wizard", "email": email, "password": password})
        self.assertEqual(201, status)
        self.assertEqual({"accountId", "username"}, set(account))
        status, failure = self.account_request(service, "POST", "/sessions",
                                               {"email": email.lower(), "password": password.strip()})
        self.assertEqual((401, {"code": "invalid_credentials"}), (status, failure))
        status, session = self.account_request(service, "POST", "/sessions", {"email": email.lower(), "password": password})
        self.assertEqual(201, status)
        self.assertEqual(account, session["account"])
        token = session["token"]
        self.assertEqual((200, account), self.account_request(service, "GET", "/account", token=token))
        self.assertEqual(401, self.account_request(service, "GET", "/account")[0])
        self.assertEqual("32", self.sql("SELECT octet_length(token_hash) FROM account_session"))
        self.assertEqual("1", self.sql("SELECT count(*) FROM account WHERE password_phc LIKE '$argon2id$v=19$m=65536,t=3,p=1$%'"))
        self.term(service)
        self.no_sessions()
        image.docker("start", service)
        self.status(service, "/health/ready", 200)
        self.assertEqual((200, account), self.account_request(service, "GET", "/account", token=token))
        self.assertEqual(204, self.account_request(service, "DELETE", "/session", token=token)[0])
        self.assertEqual(204, self.account_request(service, "DELETE", "/session", token=token)[0])
        self.assertEqual(401, self.account_request(service, "GET", "/account", token=token)[0])
        output = self.logs(service)
        for secret in (password, email, email.lower(), token):
            self.assertNotIn(secret, output)
        self.term(service)
        self.no_sessions()
        print("PASS actual local image register/login/exact Unicode-whitespace credentials/safe identity/hashed-only storage/retained restart/idempotent revoke/security refusal and no secret logs", flush=True)


if __name__ == "__main__":
    image.MANIFEST = json.loads(Path(image.ROOT, "server/build/application-image.json").read_text())
    assert image.MANIFEST["platform"] == "linux/amd64"
    print("IMAGE INPUT", json.dumps(image.MANIFEST, sort_keys=True), flush=True)
    # Do not inherit/rerun the unchanged full ImageLifecycle failure matrix.
    result = unittest.TextTestRunner(verbosity=2).run(unittest.TestSuite([AccountsImage("test_accounts")]))
    raise SystemExit(0 if result.wasSuccessful() else 1)
