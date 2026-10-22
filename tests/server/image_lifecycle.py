#!/usr/bin/env python3
"""Real local OCI image qualification; owns only UUID-labelled Docker resources.

Build first with scripts/build-server-image.py. No Compose, deployment, developer
DB, user-action endpoints, test application or external Python package is used.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tarfile
import tempfile
import time
import unittest
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]
LABEL = "org.asyncttwiz.image-test"
MANIFEST = None


def docker(*args, check=True, timeout=90):
    result = subprocess.run(["docker", *args], stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            text=True, timeout=timeout)
    if check and result.returncode:
        # Commands contain only generated synthetic inputs; never inherit host secrets.
        raise AssertionError(f"docker {args[0]} exit={result.returncode}: {result.stderr}")
    return result


def inspect(kind, resource):
    return json.loads(docker(kind, "inspect", resource).stdout)[0]


def await_true(description, predicate, seconds=45):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if predicate():
            print("PASS", description, flush=True)
            return
        time.sleep(0.1)
    raise AssertionError("Timed out: " + description)


class ImageLifecycle(unittest.TestCase):
    def setUp(self):
        self.owner = uuid.uuid4().hex
        self.label = LABEL + "=" + self.owner
        self.containers = []
        self.db = None
        self.network = "atw-image-network-" + self.owner
        self.volume = "atw-image-volume-" + self.owner
        self.temp = tempfile.TemporaryDirectory(prefix="atw-image-", dir="/tmp/opencode")
        self.password = "synthetic-only-" + uuid.uuid4().hex
        self.secret = Path(self.temp.name, "client-password")
        self.secret.write_text(self.password + "\r\n")
        self.secret.chmod(0o444)
        self.pg_secret = Path(self.temp.name, "postgres-password")
        self.pg_secret.write_text(self.password)
        self.pg_secret.chmod(0o444)
        self.mail_key = Path(self.temp.name, "reset-mail-key")
        self.mail_key.write_text("CwsLCwsLCwsLCwsLCwsLCwsLCwsLCwsLCwsLCwsLCws=")
        self.mail_key.chmod(0o444)
        self.inputs = {"DB_URL": "jdbc:postgresql://database:5432/foundation", "DB_USER": "image_fixture",
                       "DB_PASSWORD_FILE": "/run/secrets/database-password",
                       "PUBLIC_BASE_URL": "https://synthetic.invalid/base",
                       "SERVER_ADDRESS": "0.0.0.0", "SERVER_PORT": "8080",
                       "HEALTH_ADDRESS": "0.0.0.0", "HEALTH_PORT": "8081",
                       "SPRING_APPLICATION_JSON": '{"logging":{"level":{"root":"TRACE"}},"server":{"port":1}}',
                       "SPRING_CONFIG_LOCATION": "file:/not-an-application-configuration",
                       "SMTP_HOST": "127.0.0.1", "SMTP_PORT": "9", "SMTP_TLS": "local-plaintext",
                       "SMTP_FROM": "reset@synthetic.invalid", "RESET_BASE_URL": "https://reset.synthetic.invalid",
                       "RESET_MAIL_KEY_FILE": "/run/secrets/reset-mail-key"}
        self.addCleanup(self.cleanup)
        docker("network", "create", "--internal", "--label", self.label, self.network)
        docker("volume", "create", "--label", self.label, self.volume)
        postgres = next(line.split("=", 1)[1] for line in
                        (ROOT / "server/database-images.properties").read_text().splitlines()
                        if line.startswith("postgres="))
        self.postgres = postgres
        self.db = self.create(postgres, "--network-alias", "database", "--env", "POSTGRES_USER=image_fixture",
                              "--env", "POSTGRES_DB=foundation", "--env", "POSTGRES_PASSWORD_FILE=/run/secrets/password",
                              "--mount", f"type=bind,src={self.pg_secret},dst=/run/secrets/password,readonly",
                              "--mount", f"type=volume,src={self.volume},dst=/var/lib/postgresql/data")
        docker("start", self.db)
        self.await_database()
        print("OWNED PostgreSQL", postgres, "container", self.db, "network", self.network,
              "volume", self.volume, flush=True)

    def create(self, image, *options, command=()):
        name = "atw-image-" + uuid.uuid4().hex
        self.containers.append(name)  # Failure after create still has targeted cleanup.
        docker("create", "--name", name, "--label", self.label, "--network", self.network,
               *options, image, *command)
        return name

    def app(self, command=("serve",), inputs=None, secret=None, start=True):
        env = self.inputs if inputs is None else inputs
        options = ["--read-only", "--cap-drop=ALL", "--security-opt=no-new-privileges",
                   "--tmpfs", "/tmp:rw,nosuid,nodev,size=64m,mode=1777",
                   "--mount", f"type=bind,src={secret or self.secret},dst=/run/secrets/database-password,readonly",
                   "--mount", f"type=bind,src={self.mail_key},dst=/run/secrets/reset-mail-key,readonly"]
        for key, value in env.items():
            options.extend(["--env", key + "=" + value])
        container = self.create(MANIFEST["imageId"], *options, command=command)
        if start:
            docker("start", container)
        return container

    def logs(self, container):
        result = docker("logs", container)
        output = result.stdout + result.stderr
        values = [self.password, self.inputs["DB_URL"], self.inputs["DB_USER"],
                  self.inputs["DB_PASSWORD_FILE"], self.temp.name, "wrong-synthetic-credential"]
        safe = output
        for value in values:
            safe = safe.replace(value, "[REDACTED]")
        print("RETAINED container output", container, repr(safe), flush=True)
        for value in values:
            self.assertNotIn(value, output, "Application log disclosed a configuration/secret input")
        self.assertNotIn("Exception", output, "Application emitted raw exception/stack trace")
        return output

    def stopped(self, container, expected, seconds=30):
        await_true("container exited " + container,
                   lambda: not inspect("container", container)["State"]["Running"], seconds)
        state = inspect("container", container)["State"]
        self.assertEqual(expected, state["ExitCode"], self.logs(container))
        self.assertFalse(state["OOMKilled"])

    def term(self, container):
        started = time.monotonic()
        docker("kill", "--signal=TERM", container)
        self.stopped(container, 143)
        elapsed = time.monotonic() - started
        self.assertLess(elapsed, 30, "Image SIGTERM exceeded contract")
        # The container's actual PID 1 is the JVM, not a shell swallowing signals.
        print(f"PASS actual image SIGTERM exited 143 in {elapsed:.3f}s", flush=True)

    def request(self, container, path, port=8081, method="GET"):
        info = inspect("container", container)
        self.assertTrue(info["State"]["Running"], "Image service exited unexpectedly")
        # Docker29 internal bridges deliberately suppress published host ports.
        # This local Linux/WSL Docker host can reach its private bridge directly;
        # keep the network internal and publish no ports, rather than add egress.
        self.assertFalse(any(info["NetworkSettings"]["Ports"].values()))
        address = info["NetworkSettings"]["Networks"][self.network]["IPAddress"]
        request = urllib.request.Request(f"http://{address}:{port}{path}", method=method)
        # Never inherit a machine HTTP proxy for private-network traffic.
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        try:
            response = opener.open(request, timeout=10)
        except urllib.error.HTTPError as failure:
            response = failure
        with response:
            return response.status, response.read().decode(), response.headers.get("Cache-Control")

    def status(self, container, path, expected):
        def matches():
            try:
                result = self.request(container, path)
                body = "OK\n" if expected == 200 else "Unavailable\n"
                return result == (expected, body, "no-store")
            except (urllib.error.URLError, ConnectionError, TimeoutError):
                return False
        await_true(f"image HTTP {path}={expected} detail-free no-store {container}", matches)

    def sql(self, query):
        return docker("exec", self.db, "psql", "--no-psqlrc", "-v", "ON_ERROR_STOP=1",
                      "-U", "image_fixture", "-d", "foundation", "-At", "-c", query).stdout.strip()

    def number(self, query):
        return int(self.sql(query))

    def epoch(self):
        return self.number("SELECT epoch FROM service_writer")

    def await_database(self):
        # TCP is unavailable during the entrypoint's temporary Unix-only bootstrap.
        await_true("owned PostgreSQL ready", lambda: docker("exec", self.db, "pg_isready", "-h", "127.0.0.1", "-U", "image_fixture",
                   "-d", "foundation", check=False).returncode == 0)

    def migrate(self, applied=4, success=True):
        migration = self.app(("migrate",))
        self.stopped(migration, 0 if success else 1)
        if success:
            self.assertIn("Migration successful; applied=" + str(applied), self.logs(migration))
        else:
            self.assertIn("Migration failed; no destructive fallback attempted", self.logs(migration))

    def no_sessions(self):
        await_true("no ownership DB sessions/advisory locks", lambda: self.number(
            "SELECT count(*) FROM pg_stat_activity WHERE application_name='async-tt-wiz-ownership'") == 0
            and self.number("SELECT count(*) FROM pg_locks WHERE locktype='advisory'") == 0)

    def cleanup(self):
        # Collect evidence first even after failure; cleanup errors never masquerade as passes.
        errors = []
        for container in self.containers:
            found = docker("container", "inspect", container, check=False)
            if found.returncode:
                continue
            info = json.loads(found.stdout)[0]
            if info["Config"]["Labels"].get(LABEL) != self.owner:
                errors.append("Refused cleanup of non-owned " + container)
                continue
            try:
                if container == self.db:
                    print("RETAINED owned DB state", json.dumps(info["State"]), flush=True)
                    db_output = docker("logs", container)
                    print("RETAINED owned DB output", (db_output.stdout + db_output.stderr)
                          .replace(self.password, "[REDACTED]").replace("image_fixture", "[SYNTHETIC USER]"), flush=True)
                    if info["State"].get("Paused"):
                        docker("unpause", container)
                else:
                    self.logs(container)
                docker("rm", "--force", "--volumes", container)
                print("CLEANUP removed owned container", container, flush=True)
            except Exception as error:
                errors.append(str(error))
                # Always attempt removal after collecting a redaction/test failure.
                docker("rm", "--force", "--volumes", container, check=False)
        for kind, resource in (("network", self.network), ("volume", self.volume)):
            found = docker(kind, "inspect", resource, check=False)
            if found.returncode == 0:
                info = json.loads(found.stdout)[0]
                if info["Labels"].get(LABEL) != self.owner:
                    errors.append("Refused cleanup of non-owned " + resource)
                else:
                    result = docker(kind, "rm", resource, check=False)
                    if result.returncode:
                        errors.append("Owned cleanup failed: " + result.stderr)
            # Container check below and explicit network/volume checks cover each resource kind.
            if docker(kind, "inspect", resource, check=False).returncode == 0:
                errors.append("Owned resource still exists " + resource)
        for query in (("ps", "--all", "--quiet"), ("network", "ls", "--quiet"), ("volume", "ls", "--quiet")):
            if docker(*query, "--filter", "label=" + self.label).stdout.strip():
                errors.append("Labelled resources remain: " + str(query))
        self.temp.cleanup()
        self.assertEqual([], errors)
        print("PASS exact owned containers/network/volume/temporary secrets removed; no shared reset", flush=True)

    def test_image_inventory_and_invalid_configuration(self):
        image = inspect("image", MANIFEST["imageId"])
        base = inspect("image", MANIFEST["base"])
        self.assertEqual("linux/amd64", image["Os"] + "/" + image["Architecture"])
        self.assertEqual("10001:10001", image["Config"]["User"])
        self.assertEqual("SIGTERM", image["Config"]["StopSignal"])
        self.assertFalse(image["Config"].get("Cmd"))
        self.assertEqual(["/opt/java/openjdk/bin/java", "-cp", "/opt/async-tt-wiz/lib/*",
                          "org.asyncttwiz.server.ServiceCommand"], image["Config"]["Entrypoint"])
        self.assertFalse(image["Config"].get("Volumes"))
        self.assertFalse(any(item.startswith(("DB_", "PUBLIC_BASE_URL=", "SMTP_", "MAIL_", "FCM_"))
                             for item in image["Config"]["Env"]))
        self.assertEqual(base["RootFS"]["Layers"], image["RootFS"]["Layers"][:len(base["RootFS"]["Layers"])])
        print("IMAGE IDENTITY", json.dumps({"build": MANIFEST, "descriptor": image.get("Descriptor"),
              "baseId": base["Id"], "config": image["Config"], "rootfs": image["RootFS"]}, sort_keys=True), flush=True)
        probe = self.create(MANIFEST["imageId"], "--read-only", "--entrypoint", "/bin/sh", command=("-ec",
            'id; /opt/java/openjdk/bin/java -version; cat /opt/java/openjdk/release; '
            'test ! -e /opt/java/openjdk/bin/javac; '
            'for tool in gradle mvn kotlinc; do ! command -v "$tool"; done; '
            'ls /opt/java/openjdk/legal/java.base; '
            'test ! -e /root/.gradle; test ! -e /workspace; test ! -e /run/secrets/database-password'))
        docker("start", probe)
        self.stopped(probe, 0)
        self.assertIn('openjdk version "21.', self.logs(probe))
        archive = Path(self.temp.name, "image-filesystem.tar")
        docker("export", "--output", str(archive), probe)
        with tarfile.open(archive) as files:
            entries = {item.name: item for item in files if item.isfile() and item.name.startswith("opt/async-tt-wiz/")}
            expected = {"opt/async-tt-wiz/lib/" + name: digest for name, digest in MANIFEST["libraries"].items()}
            self.assertEqual(set(expected), set(entries), "Source/config/build tools leaked into application layer")
            for name, digest in expected.items():
                item = entries[name]
                self.assertEqual(0, item.uid)
                self.assertEqual(0, item.mode & 0o222, "Runtime libraries must be immutable to the service")
                self.assertEqual(digest, hashlib.sha256(files.extractfile(item).read()).hexdigest())
        print("PASS exported actual image contains exactly the locked runtime JAR hashes, no source/build/secret payload", flush=True)
        archive.unlink()
        cli = self.app(())
        self.stopped(cli, 2)
        for command, inputs, secret in [
            (("serve",), {}, self.secret), (("migrate",), {}, self.secret),
            (("serve",), dict(self.inputs, SERVER_PORT="-1"), self.secret),
            (("serve",), dict(self.inputs, DB_PASSWORD_FILE="/run/secrets/missing"), self.secret),
        ]:
            failed = self.app(command, inputs=inputs, secret=secret)
            self.stopped(failed, 1)
        unreadable = Path(self.temp.name, "unreadable")
        unreadable.write_text(self.password)
        unreadable.chmod(0)
        self.stopped(self.app(secret=unreadable), 1)  # Real image UID cannot read it.
        invalid = Path(self.temp.name, "invalid-utf8")
        invalid.write_bytes(b"\xc3\x28")
        invalid.chmod(0o444)
        self.stopped(self.app(secret=invalid), 1)
        wrong = Path(self.temp.name, "wrong-password")
        wrong.write_text("wrong-synthetic-credential")
        wrong.chmod(0o444)
        self.migrate()
        unauthorized = self.app(secret=wrong)
        self.status(unauthorized, "/health/live", 200)
        self.status(unauthorized, "/health/ready", 503)
        self.term(unauthorized)
        self.no_sessions()

    def test_fresh_explicit_migration_health_shutdown_and_retained_restart(self):
        service = self.app()
        self.status(service, "/health/live", 200)
        self.status(service, "/health/ready", 503)
        self.assertEqual(0, self.number("SELECT count(*) FROM information_schema.tables WHERE table_schema='public'"))
        self.migrate()
        self.status(service, "/health/ready", 200)
        epoch = self.epoch()
        self.assertGreater(epoch, 0)
        pid1 = docker("exec", service, "/bin/sh", "-c", "tr '\\0' ' ' < /proc/1/cmdline").stdout
        self.assertTrue(pid1.startswith("/opt/java/openjdk/bin/java -cp "), pid1)
        self.assertTrue(pid1.endswith("org.asyncttwiz.server.ServiceCommand serve "), pid1)
        for path in ("/health/live", "/health/ready", "/actuator/env", "/error", "/unknown?password=do-not-echo"):
            self.assertEqual((404, "Not found\n", "no-store"), self.request(service, path, port=8080))
        self.assertEqual((404, "Not found\n", "no-store"), self.request(service, "/actuator/env"))
        self.assertEqual((404, "Not found\n", "no-store"), self.request(service, "/health/live", method="POST"))
        self.assertEqual((200, "", "no-store"), self.request(service, "/health/live", method="HEAD"))
        self.term(service)
        self.no_sessions()
        self.assertEqual(epoch, self.epoch())
        self.migrate(applied=0)
        docker("stop", "--timeout", "10", self.db)
        docker("start", self.db)
        self.await_database()
        self.assertEqual(epoch, self.epoch())
        # Restart the same stopped container, not a new distribution or database.
        docker("start", service)
        self.status(service, "/health/ready", 200)
        self.assertEqual(epoch + 1, self.epoch())
        self.term(service)
        self.no_sessions()
        print("PASS same-image/container and retained PostgreSQL restart preserves foundation data/epoch", flush=True)

    def test_invalid_schema_is_unchanged_and_never_ready_or_auto_migrated(self):
        self.sql("CREATE TABLE test_owned_unversioned (retained text); INSERT INTO test_owned_unversioned VALUES ('keep')")
        service = self.app()
        self.status(service, "/health/live", 200)
        self.status(service, "/health/ready", 503)
        self.migrate(success=False)
        self.assertEqual("keep", self.sql("SELECT retained FROM test_owned_unversioned"))
        self.assertEqual(0, self.number("SELECT count(*) FROM information_schema.tables WHERE table_name='flyway_schema_history'"))
        self.term(service)
        self.sql("DROP TABLE test_owned_unversioned")  # Only this test's disposable database.
        self.migrate()
        checksum = self.number("SELECT checksum FROM flyway_schema_history WHERE version='1'")
        for corruption in ("checksum", "future", "missing-singleton"):
            if corruption == "checksum":
                self.sql("UPDATE flyway_schema_history SET checksum=123 WHERE version='1'")
            elif corruption == "future":
                self.sql("INSERT INTO flyway_schema_history VALUES (3,'999','future','SQL','V999__future.sql',42,current_user,now(),0,true)")
            else:
                self.sql("DELETE FROM service_writer")
            service = self.app()
            self.status(service, "/health/live", 200)
            self.status(service, "/health/ready", 503)
            # Explicit migrate validates migration history, not missing business rows.
            if corruption != "missing-singleton":
                self.migrate(success=False)
            time.sleep(1.2)  # Observe multiple recovery attempts without altering schema.
            self.status(service, "/health/ready", 503)
            self.assertEqual(0, self.number("SELECT count(*) FROM service_writer WHERE epoch<>0"))
            if corruption == "checksum":
                self.assertEqual(123, self.number("SELECT checksum FROM flyway_schema_history WHERE version='1'"))
            elif corruption == "future":
                self.assertEqual(3, self.number("SELECT count(*) FROM flyway_schema_history"))
            else:
                self.assertEqual(0, self.number("SELECT count(*) FROM service_writer"))
            self.term(service)
            if corruption == "checksum":
                self.sql(f"UPDATE flyway_schema_history SET checksum={checksum} WHERE version='1'")
            elif corruption == "future":
                self.sql("DELETE FROM flyway_schema_history WHERE version='999'")
            print("PASS image refuses unchanged", corruption, "without repair/migration/readiness", flush=True)
        self.no_sessions()

    def test_competing_writer_takeover_outage_revalidation_and_blackhole_sigterm(self):
        self.migrate()
        first = self.app()
        self.status(first, "/health/ready", 200)
        second = self.app()
        self.status(second, "/health/live", 200)
        self.status(second, "/health/ready", 503)
        initial = self.epoch()
        docker("pause", first)
        try:
            self.sql("SELECT pg_terminate_backend(pid) FROM pg_locks WHERE locktype='advisory' AND granted")
            self.status(second, "/health/ready", 200)
            self.assertEqual(initial + 1, self.epoch())
        finally:
            docker("unpause", first)
        self.status(first, "/health/ready", 503)
        self.status(second, "/health/ready", 200)
        docker("stop", "--timeout", "10", self.db)
        for service in (first, second):
            self.status(service, "/health/ready", 503)
            started = time.monotonic()
            self.status(service, "/health/live", 200)
            self.assertLess(time.monotonic() - started, 1.5)
        docker("start", self.db)
        self.await_database()
        await_true("exactly one recovered image writer", lambda:
                   sum(self.request(service, "/health/ready")[0] == 200 for service in (first, second)) == 1)
        self.assertGreater(self.epoch(), initial + 1)
        owner = first if self.request(first, "/health/ready")[0] == 200 else second
        competitor = second if owner == first else first
        self.term(competitor)
        self.status(owner, "/health/ready", 200)
        original = self.epoch()
        checksum = self.number("SELECT checksum FROM flyway_schema_history WHERE version='1'")
        self.sql("UPDATE flyway_schema_history SET checksum=123 WHERE version='1'")
        self.sql("SELECT pg_terminate_backend(pid) FROM pg_locks WHERE locktype='advisory' AND granted")
        self.status(owner, "/health/ready", 503)
        self.status(owner, "/health/live", 200)
        time.sleep(1.2)
        self.assertEqual(original, self.epoch())
        self.sql(f"UPDATE flyway_schema_history SET checksum={checksum} WHERE version='1'")
        self.status(owner, "/health/ready", 200)
        self.assertEqual(original + 1, self.epoch())
        docker("pause", self.db)
        try:
            self.status(owner, "/health/live", 200)
            self.status(owner, "/health/ready", 503)
            self.term(owner)  # Actual PID1 SIGTERM with PostgreSQL still blackholed.
        finally:
            docker("unpause", self.db)
        self.no_sessions()
        retained = self.epoch()
        docker("start", owner)
        self.status(owner, "/health/ready", 200)
        self.assertEqual(retained + 1, self.epoch())
        self.term(owner)
        self.no_sessions()
        print("PASS image single-writer takeover, DB outage/revalidation/reload and blackhole shutdown/restart", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image-file", type=Path, default=ROOT / "server/build/application-image.json")
    args = parser.parse_args()
    MANIFEST = json.loads(args.image_file.read_text())
    if MANIFEST["platform"] != "linux/amd64":
        parser.error("This qualification is amd64 only; full architecture qualification remains I35")
    baseline = {kind: set(docker(*query).stdout.split()) for kind, query in (
        ("containers", ("ps", "--all", "--quiet", "--no-trunc")),
        ("networks", ("network", "ls", "--quiet", "--no-trunc")),
        ("volumes", ("volume", "ls", "--quiet")))}
    print("UNRELATED RESOURCE BASELINE", json.dumps({k: sorted(v) for k, v in baseline.items()}), flush=True)
    result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(ImageLifecycle))
    for kind, query in (("containers", ("ps", "--all", "--quiet", "--no-trunc")),
                        ("networks", ("network", "ls", "--quiet", "--no-trunc")),
                        ("volumes", ("volume", "ls", "--quiet"))):
        remaining = set(docker(*query).stdout.split())
        assert baseline[kind] <= remaining, "Unrelated resource disappeared during image tests: " + kind
    print("PASS unrelated baseline resources left intact; no Compose or global cleanup", flush=True)
    raise SystemExit(0 if result.wasSuccessful() else 1)
