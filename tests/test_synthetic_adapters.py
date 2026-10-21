"""Real socket/process qualification of the test-only adapters (not product E2E)."""
import base64
import concurrent.futures
import gzip
import hashlib
import http.client
import json
import os
from pathlib import Path
import shutil
import smtplib
import socket
import subprocess
import sys
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = Path(os.environ.get("ADAPTER_SCRIPT", ROOT / "e2e/adapters/adapter.py"))
FIXTURES = Path(os.environ.get("ADAPTER_FIXTURES", ROOT / "tests/fixtures/catalogs"))
ADMIN = Path(os.environ.get("ADAPTER_ADMIN", ROOT / "e2e/adapters/admin.py"))


def port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


class Running:
    def __init__(self, directory, instance="fixture-a", origin="http://device-source.invalid:18080"):
        self.directory, self.instance, self.origin = directory, instance, origin
        self.http, self.admin, self.smtp = port(), port(), port()
        self.token = "a7" * 32
        self.token_file = directory / "harness-token"
        self.token_file.write_text(self.token + "\n")
        self.logs = directory / "process.log"
        self.state = directory / "state"
        self.process = None

    def command(self, **overrides):
        args = {"fixtures": FIXTURES, "origin": self.origin, "state-dir": self.state,
                "instance": self.instance, "token-file": self.token_file,
                "http-port": self.http, "admin-port": self.admin, "smtp-port": self.smtp, **overrides}
        return [sys.executable, str(SCRIPT), *[str(v) for k, value in args.items() for v in ("--" + k, value)]]

    def start(self):
        self.log = self.logs.open("ab")
        self.process = subprocess.Popen(self.command(), stdout=self.log, stderr=subprocess.STDOUT)
        until = time.monotonic() + 10
        while time.monotonic() < until:
            if self.process.poll() is not None:
                raise AssertionError("Adapter exited: " + self.logs.read_text())
            try:
                if self.request("/health/ready", admin=True)[0] == 200:
                    return self
            except OSError:
                pass
            time.sleep(.02)
        raise AssertionError("Adapter readiness deadline")

    def stop(self):
        if self.process:
            self.process.terminate()
            self.process.wait(timeout=5)
            self.log.close()
            if self.process.returncode != 0:
                raise AssertionError("nonzero adapter shutdown")
            self.process = None
            for number in (self.http, self.admin, self.smtp):
                with socket.socket() as sock:
                    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                    sock.bind(("127.0.0.1", number))

    def request(self, path, admin=False, method="GET", body=None, token=True, headers=None):
        conn = http.client.HTTPConnection("127.0.0.1", self.admin if admin else self.http, timeout=3)
        try:
            hdrs = {"Authorization": "Bearer " + self.token} if token else {}
            hdrs.update(headers or {})
            conn.request(method, path, body=body, headers=hdrs)
            response = conn.getresponse()
            return response.status, dict(response.getheaders()), response.read()
        finally:
            conn.close()

    def admin_json(self, path, value=None):
        status, _, body = self.request(path, True, "GET" if value is None else "POST",
                                       None if value is None else json.dumps(value))
        assert status == 200, (path, status)
        return json.loads(body)

    def control(self, **value):
        return self.admin_json("/admin/control", {"instance": self.instance, **value})

    def reset(self):
        return self.admin_json("/admin/reset", {"instance": self.instance})

    def send(self, body=b"Subject: synthetic\r\n\r\nLocal synthetic delivery.\r\n"):
        with smtplib.SMTP("127.0.0.1", self.smtp, timeout=3) as client:
            return client.sendmail("sender@example.invalid", ["one@example.invalid", "two@external.invalid"], body)


class SyntheticAdapterTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="adapters-", dir="/tmp/opencode")
        self.addCleanup(self.tmp.cleanup)
        self.app = Running(Path(self.tmp.name))
        self.addCleanup(self.app.stop)
        self.app.start()

    def test_every_provider_file_actual_bytes_hash_mime_both_datasets_editions(self):
        inventory = self.app.admin_json("/admin/inventory")
        fixture = json.loads((FIXTURES / "manifest.json").read_text())
        self.assertEqual(inventory["fixtureManifestSha256"], hashlib.sha256((FIXTURES / "manifest.json").read_bytes()).hexdigest())
        self.assertEqual(inventory["origin"], self.app.origin)
        count = 0
        for path, expected in inventory["files"].items():
            status, headers, raw = self.app.request("/" + path)
            self.assertEqual(status, 200, path)
            self.assertEqual(hashlib.sha256(raw).hexdigest(), expected, path)
            if "/scryfall/cards/" in path:
                original = json.loads((FIXTURES / path).read_bytes())
                obj = json.loads(raw)
                for face, old in zip(obj.get("card_faces", [obj]), original.get("card_faces", [original])):
                    for size, uri in face["image_uris"].items():
                        self.assertEqual(uri, self.app.origin + old["image_uris"][size].removeprefix(fixture["origin"]))
                alias = "/" + path.removesuffix(".json")
                self.assertEqual(self.app.request(alias)[2], raw)
            else:
                self.assertEqual(raw, (FIXTURES / path).read_bytes(), path)
            expected_mime = ("image/png" if path.endswith(".png") else "application/gzip" if path.endswith(".gz")
                             else "text/plain; charset=utf-8" if path.endswith(".sha256") else "application/json")
            self.assertEqual(headers["Content-Type"], expected_mime, path)
            self.assertEqual(headers["ETag"], '"' + expected + '"')
            count += 1
        self.assertEqual(count, len(fixture["files"]) - 4)  # harness import cases never served
        for entry in fixture["datasets"]:
            base = "/" + entry["base"] + "/"
            raw = self.app.request(base + "AllPrintings.json.gz")[2]
            self.assertEqual(gzip.decompress(raw), self.app.request(base + "AllPrintings.json")[2])
            self.assertEqual(hashlib.sha256(raw).hexdigest().encode() + b"\n", self.app.request(base + "AllPrintings.json.gz.sha256")[2])
            for art in entry["artCases"].values():
                status, _, image = self.app.request("/" + art["path"])
                self.assertEqual(status, 404 if art["state"] == "missing" else 200)
                if art["state"] == "broken":
                    self.assertFalse(image.startswith(b"\x89PNG"))
        print(f"PASS actual HTTP: {count} provider files, all hashes/MIME; both datasets/editions/faces, missing/broken art")

    def test_normal_routes_cannot_read_harness_host_or_admin_and_no_host_rebinding(self):
        for path in ("/", "/manifest.json", "/admin/messages", "/admin/state", "/admin/reset", "/health/ready",
                     "/prism/manifest.json", "/prism/v1/import-cases.json", "/prism/v1/../../harness-token",
                     "/prism/v1/%2e%2e/harness-token", "/prism/Meta.json?token=secret", "/etc/passwd",
                     "http://localhost/admin/messages"):
            self.assertEqual(self.app.request(path)[0], 404, path)
            self.assertNotEqual(self.app.request(path, True, token=False)[0], 200)
        self.assertEqual(self.app.request("/admin/reset", method="POST", body='{}')[0], 405)
        self.assertEqual(self.app.request("/admin/messages", True, headers={"Authorization": "Bearer wrong"})[0], 403)
        self.assertEqual(self.app.request("/admin/messages?token=" + self.app.token, True, token=False)[0], 403)
        _, _, raw = self.app.request(next("/" + p for p in self.app.admin_json("/admin/inventory")["files"] if "/scryfall/" in p),
                                     headers={"Host": "attacker.invalid", "Authorization": "Bearer sentinel-credential"})
        self.assertNotIn(b"attacker.invalid", raw)
        self.assertNotIn(b"sentinel-credential", raw)
        self.assertEqual(self.app.request("/admin/control", True, "POST", b"not-json")[0], 400)
        self.assertEqual(self.app.request("/admin/reset", True, "POST", '{"instance":"wrong"}')[0], 409)
        self.assertEqual(self.app.request("/admin/control", True, "POST", json.dumps({"instance": self.app.instance, "versions": {"prism": True, "quilt": 1}}))[0], 400)
        self.assertEqual(self.app.admin_json("/admin/state")["generation"], 0)

    def test_source_controls_change_ordinary_routes_not_other_dataset_and_retained_restart(self):
        first = self.app.request("/prism/Meta.json")[2]
        self.app.control(versions={"prism": 2, "quilt": 1})
        self.assertNotEqual(self.app.request("/prism/Meta.json")[2], first)
        self.assertEqual(self.app.request("/quilt/Meta.json")[2], first)
        self.assertEqual(self.app.request("/prism/v1/Meta.json")[2], first)
        self.app.stop()
        self.app.start()
        self.assertEqual(self.app.admin_json("/admin/state")["versions"], {"prism": 2, "quilt": 1})
        self.app.control(sourceFault="unavailable")
        self.assertEqual(self.app.request("/prism/Meta.json")[0], 503)
        self.assertEqual(self.app.request("/health/ready", True)[0], 503)
        self.assertEqual(self.app.request("/health/live", True)[0], 200)
        self.app.send()  # independent SMTP remains usable during source failure
        self.app.control(sourceFault="corrupt-gzip")
        with self.assertRaises((gzip.BadGzipFile, EOFError)):
            gzip.decompress(self.app.request("/prism/AllPrintings.json.gz")[2])
        self.app.control(sourceFault="truncate-gzip")
        with self.assertRaises(http.client.IncompleteRead):
            self.app.request("/prism/AllPrintings.json.gz")
        self.app.reset()
        self.assertEqual(self.app.request("/health/ready", True)[0], 200)
        self.assertEqual(self.app.request("/prism/Meta.json")[2], first)
        self.assertEqual(self.app.admin_json("/admin/messages"), [])

    def test_smtp_real_envelopes_unicode_dot_unstuff_multiple_messages_retained_restart_safe_logs(self):
        message = "Subject: synthetic reset\r\n\r\nhttps://example.invalid/reset?token=body-secret\r\nUnicode: ää✨\r\n.dot line\r\n".encode()
        self.assertEqual(self.app.send(message), {})
        with smtplib.SMTP("127.0.0.1", self.app.smtp, timeout=3) as client:
            self.assertEqual(client.ehlo()[0], 250)
            self.assertNotIn("auth", client.esmtp_features)
            self.assertNotIn("starttls", client.esmtp_features)
            self.assertEqual(client.verify("one@example.invalid")[0], 502)
            self.assertEqual(client.docmd("AUTH", "PLAIN credential-sentinel")[0], 502)
            client.sendmail("", "null-sender@example.invalid", b"Subject: second\r\n\r\nSecond\r\n")
            client.sendmail("sender@example.invalid", "third@example.invalid", b"Subject: third\r\n\r\nThird\r\n")
        messages = self.app.admin_json("/admin/messages")
        self.assertEqual(len(messages), 3)
        self.assertEqual(base64.b64decode(messages[0]["bodyBase64"]), message)
        self.assertEqual(messages[0]["recipients"], ["one@example.invalid", "two@external.invalid"])
        self.assertEqual(messages[1]["sender"], "")
        self.app.stop()
        self.app.start()
        self.assertEqual(self.app.admin_json("/admin/messages"), messages)
        logs = self.app.logs.read_text()
        for secret in ("body-secret", "credential-sentinel", self.app.token, "one@example.invalid", "Subject:"):
            self.assertNotIn(secret, logs)

    def test_smtp_failures_reset_twice_and_in_flight_old_delivery_cannot_repopulate(self):
        for fault, code in (("temporary", 451), ("permanent", 550)):
            self.app.control(mailFault=fault)
            self.assertEqual(self.app.request("/health/ready", True)[0], 503)
            with self.assertRaises(smtplib.SMTPDataError) as caught:
                self.app.send()
            self.assertEqual(caught.exception.smtp_code, code)
            self.assertEqual(self.app.admin_json("/admin/messages"), [])
        self.app.reset()
        self.app.send()
        client = smtplib.SMTP("127.0.0.1", self.app.smtp, timeout=3)
        self.addCleanup(client.close)
        client.ehlo()
        client.mail("sender@example.invalid")
        client.rcpt("one@example.invalid")
        self.assertEqual(client.docmd("DATA")[0], 354)
        client.send(b"Subject: old in flight\r\n\r\nold token")
        self.assertEqual(self.app.admin_json("/admin/state")["smtpConnections"], 1)
        reset = self.app.reset()
        self.assertEqual(reset["messageCount"], 0)
        try:
            client.send(b"\r\n.\r\n")
            self.assertNotEqual(client.getreply()[0], 250)
        except (OSError, smtplib.SMTPServerDisconnected):
            pass
        # Drain the actual old connection, not a sleep-based assertion.
        until = time.monotonic() + 3
        while self.app.admin_json("/admin/state")["smtpConnections"] and time.monotonic() < until:
            time.sleep(.01)
        self.assertEqual(self.app.admin_json("/admin/state")["smtpConnections"], 0)
        self.assertEqual(self.app.admin_json("/admin/messages"), [])
        self.assertEqual(self.app.reset()["messageCount"], 0)
        self.app.send()
        self.assertEqual(len(self.app.admin_json("/admin/messages")), 1)
        print("PASS SMTP in-flight DATA reset closes/fences old connection; two empty resets and new-generation delivery")

    def test_parallel_instances_have_distinct_stores_and_reset_isolation(self):
        other_dir = Path(self.tmp.name) / "other"
        other_dir.mkdir()
        other = Running(other_dir, "fixture-b", "http://different-device.invalid:18081")
        self.addCleanup(other.stop)
        other.start()
        self.assertEqual(other.admin_json("/admin/messages"), [])
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
            list(pool.map(lambda i: (self.app if i % 2 else other).send(), range(12)))
        self.assertEqual(len(self.app.admin_json("/admin/messages")), 6)
        self.assertEqual(len(other.admin_json("/admin/messages")), 6)
        self.app.control(versions={"prism": 2, "quilt": 2})
        self.app.reset()
        self.assertEqual(other.admin_json("/admin/state")["versions"], {"prism": 1, "quilt": 1})
        self.assertEqual(len(other.admin_json("/admin/messages")), 6)
        self.assertEqual(self.app.admin_json("/admin/messages"), [])
        self.assertNotEqual(self.app.admin_json("/admin/inventory")["origin"], other.admin_json("/admin/inventory")["origin"])

    def test_wrong_owner_concurrent_store_and_invalid_config_fail_bounded_without_secret_logs(self):
        bad_token = Path(self.tmp.name) / "bad-token"
        bad_token.write_text("not-a-valid-harness-secret")
        for index, args in enumerate(({}, {"origin": "http://user:sentinel-secret@localhost"}, {"instance": "../wrong"},
                     {"token-file": Path(self.tmp.name) / "missing-secret"}, {"admin-port": self.app.http},
                     {"token-file": bad_token})):
            if index:
                args["state-dir"] = Path(self.tmp.name) / f"invalid-{index}"
            result = subprocess.run(self.app.command(**args), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=5)
            self.assertNotEqual(result.returncode, 0)
            self.assertNotIn(b"sentinel-secret", result.stdout)
            self.assertNotIn(self.app.token.encode(), result.stdout)
        self.app.send()
        self.app.stop()
        wrong = subprocess.run(self.app.command(instance="wrong-owner"), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=5)
        self.assertNotEqual(wrong.returncode, 0)
        self.app.start()
        self.assertEqual(len(self.app.admin_json("/admin/messages")), 1)

    def test_tampered_source_and_unwritable_store_fail_setup_without_repair_or_wipe(self):
        copy = Path(self.tmp.name) / "tampered"
        shutil.copytree(FIXTURES, copy)
        target = copy / "prism/v1/AllPrintings.json.gz"
        target.write_bytes(target.read_bytes() + b"tampered")
        result = subprocess.run(self.app.command(fixtures=copy, **{"state-dir": Path(self.tmp.name) / "tampered-state"}),
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=5)
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue(target.read_bytes().endswith(b"tampered"))
        # Not a mock database failure: a real filesystem target cannot be opened
        # as the SQLite file. Refusal must preserve its marker, never auto-delete.
        blocked = Path(self.tmp.name) / "blocked-state"
        (blocked / "state.sqlite").mkdir(parents=True)
        marker = blocked / "state.sqlite" / "retain-marker"
        marker.write_bytes(b"retained previous state")
        result = subprocess.run(self.app.command(**{"state-dir": blocked}),
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=5)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(marker.read_bytes(), b"retained previous state")
        self.assertEqual(self.app.request("/health/ready", True)[0], 200)

    def test_sigkill_restart_recovers_acknowledged_deliveries_and_selected_edition(self):
        self.app.send()
        deliveries = self.app.admin_json("/admin/messages")
        self.app.control(versions={"prism": 1, "quilt": 2})
        self.app.process.kill()
        self.app.process.wait(timeout=5)
        self.app.log.close()
        self.app.process = None
        self.app.start()
        self.assertEqual(self.app.admin_json("/admin/messages"), deliveries)
        self.assertEqual(self.app.admin_json("/admin/state")["versions"], {"prism": 1, "quilt": 2})

    def test_idle_and_bad_protocol_connections_are_bounded_and_do_not_leak(self):
        with socket.create_connection(("127.0.0.1", self.app.http), timeout=3) as sock:
            sock.sendall(b"GET /prism/Meta.json HTTP/1.1\r\nContent-Length: -1\r\n\r\n")
            self.assertEqual(sock.recv(1024), b"")
        with socket.create_connection(("127.0.0.1", self.app.smtp), timeout=3) as sock:
            self.assertTrue(sock.recv(1024).startswith(b"220"))
            sock.sendall(b"DATA\r\n")
            self.assertTrue(sock.recv(1024).startswith(b"502"))
            sock.settimeout(12)
            self.assertEqual(sock.recv(1024), b"")
        self.assertEqual(self.app.request("/health/ready", True)[0], 200)
        self.assertEqual(self.app.admin_json("/admin/messages"), [])

    def test_host_admin_cli_wait_reset_controls_and_failure_exits(self):
        def cli(*args):
            return subprocess.run([sys.executable, str(ADMIN), "--url", f"http://127.0.0.1:{self.app.admin}",
                                   "--token-file", str(self.app.token_file), *args],
                                  stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=5)
        self.assertEqual(cli("wait").stdout, b"ready\n")
        self.assertEqual(cli("--instance", self.app.instance, "control", "--source-fault", "unavailable").returncode, 0)
        start = time.monotonic()
        failed = cli("--timeout", ".3", "wait")
        self.assertNotEqual(failed.returncode, 0)
        self.assertLess(time.monotonic() - start, 2)
        self.assertNotIn(self.app.token.encode(), failed.stdout)
        self.assertNotEqual(cli("--instance", "wrong-owner", "reset").returncode, 0)
        self.assertEqual(cli("--instance", self.app.instance, "reset").returncode, 0)
        self.assertEqual(cli("wait").returncode, 0)
        self.assertEqual(json.loads(cli("state").stdout)["messageCount"], 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
