#!/usr/bin/env python3
"""Real-image Compose host qualification; no Android/UI or product action injection.

Requires the existing qualified local server/adapter image manifests. Evidence is
redacted infrastructure only. Every resource is exact-owned, never global-pruned.
"""
import hashlib
import json
from pathlib import Path
import smtplib
import socket
import subprocess
import sys
import time
from unittest.mock import patch
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from e2e import environment as e


def inventory():
    return {kind: sorted(e.docker(*args).splitlines()) for kind, args in
            (("container", ("ps", "-aq", "--no-trunc")), ("network", ("network", "ls", "-q", "--no-trunc")),
             ("volume", ("volume", "ls", "-q")))}


def cli(directory, *args, expected=0, timeout=240):
    command = [sys.executable, "scripts/e2e-environment.py", "--directory", str(directory), *args]
    print("COMMAND", json.dumps(command), flush=True)
    result = subprocess.run(command, cwd=e.ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=timeout)
    print(result.stdout.strip(), flush=True)
    assert result.returncode == expected, (result.returncode, expected)
    if args[-1] == "capture" and result.returncode == 0:
        print("REDACTED-CAPTURE", Path(json.loads(result.stdout)["capture"]).read_text(), flush=True)
    return result.stdout


def check_egress(value):
    # Execute the network probe *inside each actual service's network namespace*.
    # A short-lived Python helper shares that namespace but has no Docker socket,
    # host mount, extra network or production credentials. Its exact ID is removed.
    code = """import socket,json,signal
def expired(*_):
    raise TimeoutError()
signal.signal(signal.SIGALRM,expired)
results={}
for address in ['1.1.1.1','8.8.8.8']:
    try:
        s=socket.create_connection((address,443),timeout=1); s.close(); results[address]='CONNECTED'
    except OSError:
        results[address]='blocked'
try:
    signal.alarm(5)
    socket.getaddrinfo('example.com',443); results['externalDNS']='RESOLVED'
except OSError:
    results['externalDNS']='blocked'
finally:
    signal.alarm(0)
print(json.dumps(results,sort_keys=True))
assert all(v=='blocked' for v in results.values())
"""
    for service in ("database", "backend", "adapters"):
        name = value["project"] + "-egress-" + service
        try:
            result = e.docker("run", "--name", name, "--label", e.LABEL + "=" + value["owner"],
                              "--network", "container:" + value["project"] + "-" + service,
                              "--read-only", "--cap-drop=ALL", "--security-opt=no-new-privileges",
                              "--entrypoint", "python3", value["images"]["adapters"], "-c", code)
            print("MEASURED-EGRESS", service, result, flush=True)
        finally:
            info = e.inspect("container", name)
            if info:
                assert info["Config"]["Labels"][e.LABEL] == value["owner"]
                e.docker("rm", "-f", info["Id"])


def check_internal_publication(value):
    name = value["project"] + "-publication-probe"
    with e.reserve() as reservation:
        port = reservation.getsockname()[1]
    try:
        e.docker("run", "-d", "--name", name, "--label", e.LABEL + "=" + value["owner"],
                 "--network", value["project"] + "-scenario", "-p", f"127.0.0.1:{port}:8080",
                 "--read-only", "--cap-drop=ALL", "--entrypoint", "python3", value["images"]["adapters"],
                 "-c", "import http.server; http.server.HTTPServer(('0.0.0.0',8080),http.server.BaseHTTPRequestHandler).serve_forever()")
        info = e.inspect("container", name)
        published = info["NetworkSettings"]["Ports"]
        assert not any(published.values()), published
        # A direct HTTP request into this same private bridge must still work.
        address = info["NetworkSettings"]["Networks"][value["project"] + "-scenario"]["IPAddress"]
        e.deadline(lambda: e.require(e.request(address, 8080, "/")[0] == 501, "probe startup"), 10)
        try:
            host_result = e.request("127.0.0.1", port, "/")
        except (OSError, e.http.client.HTTPException):
            host_result = "unreachable"
        assert host_result == "unreachable", host_result
        print("MEASURED Docker29 internal-network -p loopback mapping discarded; host publication unreachable; direct private bridge HTTP reachable; qualified controller loopback relay required", flush=True)
    finally:
        info = e.inspect("container", name)
        if info:
            assert info["Config"]["Labels"][e.LABEL] == value["owner"]
            e.docker("rm", "-f", info["Id"])
        with e.reserve(port):
            pass


def retained_checks(directory):
    value = e.load(directory)
    initial = e.owned(value)
    identities = {k: c["Id"] for k, c in initial.items()}
    served = e.admin(value, "/admin/inventory")
    assert served["origin"] == value["sourceOrigin"]
    for relative, expected in served["files"].items():
        status, data = e.request("127.0.0.1", value["ports"]["source"], "/" + relative)
        assert status == 200 and hashlib.sha256(data).hexdigest() == expected, relative
        if "/scryfall/cards/" in relative:
            obj = json.loads(data)
            for face in obj.get("card_faces", [obj]):
                assert all(uri.startswith(value["sourceOrigin"] + "/") for uri in face["image_uris"].values())
    print("PASS", len(served["files"]), "provider objects/catalog/gzip/checksum/art hashes through actual source loopback relay; configured art origin retained", flush=True)
    assert e.database(value, "SELECT version FROM flyway_schema_history WHERE success") == "1"
    assert e.database(value, "SELECT string_agg(tablename,',' ORDER BY tablename) FROM pg_tables WHERE schemaname='public'") == "flyway_schema_history,service_writer"
    assert e.admin(value, "/admin/state")["messageCount"] == 0
    e.admin(value, "/admin/control", {"instance": value["project"], "versions": {"prism": 2, "quilt": 1}})
    adapter_ip = e.ip(value, initial["adapters"])
    message = b"Subject: synthetic infrastructure\r\n\r\nprivate-mail-body-sentinel\r\n"
    with smtplib.SMTP(adapter_ip, 2525, timeout=3) as smtp:
        smtp.sendmail("source@example.invalid", "no-relay@external.invalid", message)
    messages = e.admin(value, "/admin/messages")
    assert len(messages) == 1
    message_hash = hashlib.sha256(json.dumps(messages, sort_keys=True).encode()).hexdigest()
    before_epoch = int(e.database(value, "SELECT epoch FROM service_writer"))
    # A test-owned probe belongs to lower-layer infrastructure, never app/game data.
    e.database(value, "CREATE TABLE infrastructure_probe(value text NOT NULL); INSERT INTO infrastructure_probe VALUES ('retained-synthetic')")
    for service in ("backend", "database", "adapters", "all"):
        cli(directory, "restart", "--service", service)
        current = e.owned(value)
        assert {k: c["Id"] for k, c in current.items()} == identities
        epoch = int(e.database(value, "SELECT epoch FROM service_writer"))
        assert epoch >= before_epoch and (service == "adapters" or epoch > before_epoch)
        before_epoch = epoch
        assert e.database(value, "SELECT value FROM infrastructure_probe") == "retained-synthetic"
        assert e.admin(value, "/admin/state")["versions"] == {"prism": 2, "quilt": 1}
        assert hashlib.sha256(json.dumps(e.admin(value, "/admin/messages"), sort_keys=True).encode()).hexdigest() == message_hash
        print("PASS retained restart", service, "same container/storage identity; writer epoch", epoch,
              "probe retained; exact synthetic delivery hash", message_hash, flush=True)

    # Docker can reassign bridge IPs even while container IDs stay the same.
    # Restart out of allocation order, then require wait to refresh an alive relay
    # instead of forwarding the source to yesterday's backend/admin address.
    old_addresses = {s: e.ip(value, e.owned(value)[s]) for s in ("backend", "adapters")}
    e.compose(value, "stop", "backend", "adapters")
    e.compose(value, "start", "backend")
    e.compose(value, "start", "adapters")
    cli(directory, "wait")
    new_addresses = {s: e.ip(value, e.owned(value)[s]) for s in ("backend", "adapters")}
    assert old_addresses != new_addresses
    assert e.admin(value, "/admin/state")["messageCount"] == 1
    print("PASS wait refreshes alive relay after measured same-container bridge-IP reassignment", old_addresses, new_addresses, flush=True)

    # Source outage is independent of the real writer/backend health, and vice versa.
    e.admin(value, "/admin/control", {"instance": value["project"], "sourceFault": "unavailable"})
    cli(directory, "--timeout", "1", "wait", expected=1)
    assert not (directory / "ready.json").exists()
    assert e.request(e.ip(value, e.owned(value)["backend"]), 8081, "/health/ready") == (200, b"OK\n")
    cli(directory, "capture")
    e.admin(value, "/admin/control", {"instance": value["project"], "sourceFault": "ok"})
    cli(directory, "wait")
    e.docker("stop", "-t", "30", value["project"] + "-backend")
    cli(directory, "--timeout", "1", "wait", expected=1)
    assert not (directory / "ready.json").exists()
    assert e.request("127.0.0.1", value["ports"]["source"], "/prism/Meta.json")[0] == 200
    cli(directory, "capture")
    cli(directory, "restart", "--service", "backend")

    # The private admin socket requires a harness secret even from its own network;
    # neither of the two client candidates can reach a health/admin/reset API.
    adapter_ip = e.ip(value, e.owned(value)["adapters"])
    status, body = e.request(adapter_ip, 8081, "/health/ready")
    assert status == 403, ("private adapter authorization", status, body)
    for path in ("/admin/state", "/admin/messages", "/admin/reset", "/health/ready"):
        for port in value["ports"].values():
            assert e.request("127.0.0.1", port, path)[0] == 404
    addresses = {s: e.ip(value, e.owned(value)[s]) for s in ("database", "backend", "adapters")}
    print("MEASURED-TOPOLOGY", json.dumps({"bridge": addresses, "internal": True, "dockerPublishedPorts": [],
                                          "loopbackForwarders": value["ports"], "androidReachability": "I01f pending"}), flush=True)
    check_egress(value)
    check_internal_publication(value)
    cli(directory, "capture")
    for capture in directory.glob("capture-*.json"):
        text = capture.read_text()
        assert message.decode().split("\r\n\r\n")[1].strip() not in text
        assert "no-relay@external.invalid" not in text
        for file in ("db-password", "harness-token"):
            assert (directory / file).read_text().strip() not in text
    print("PASS real private database/health/SMTP/admin, separate public/source endpoints, redacted captures and source/backend fault independence", flush=True)


def migration_failure(directory):
    original = e.compose

    def poison(value, *args, **kwargs):
        if args[-1] == "migrate":
            e.database(value, "CREATE TABLE unversioned_probe(value integer)")
        return original(value, *args, **kwargs)

    # Only this test interposes at the lower-layer environment boundary to make
    # the real explicit migration fail. No runtime fault/seed/action switch.
    with patch.object(e, "compose", side_effect=poison):
        try:
            e.main(["--directory", str(directory), "provision"])
            raise AssertionError("invalid migration was accepted")
        except SystemExit as error:
            assert error.code == 1
    value = e.load(directory)
    assert not (directory / "ready.json").exists()
    assert not e.owned(value).get("backend")
    assert e.database(value, "SELECT count(*) FROM information_schema.tables WHERE table_name='unversioned_probe'") == "1"
    captures = list(directory.glob("capture-*.json"))
    assert captures
    text = captures[0].read_text()
    print("REDACTED-MIGRATION-FAILURE", text, flush=True)
    assert '"ExitCode": 1' in text and "Migration failed; no destructive fallback attempted" in text
    cli(directory, "--timeout", "1", "wait", expected=1)
    print("PASS actual same-image migration failure exits nonzero, preserves invalid schema, starts no backend and issues no handoff; diagnostics captured", flush=True)


def startup_failure(directory):
    original = e.compose

    def malformed(value, *args, **kwargs):
        if args[-1] == "backend" and "up" in args:
            file = directory / "compose.json"
            topology = json.loads(file.read_text())
            topology["services"]["backend"]["environment"]["SERVER_PORT"] = "malformed"
            e.write_json(file, topology)
            value["composeSha256"] = e.sha(file)
            e.write_json(directory / "instance.json", value)
        return original(value, *args, **kwargs)

    with patch.object(e, "compose", side_effect=malformed):
        try:
            e.main(["--directory", str(directory), "--timeout", "2", "provision"])
            raise AssertionError("invalid startup was accepted")
        except SystemExit as error:
            assert error.code == 1
    value = e.load(directory)
    assert not (directory / "ready.json").exists()
    assert e.owned(value)["backend"]["State"]["ExitCode"] == 1
    assert e.database(value, "SELECT epoch FROM service_writer") == "0"
    cli(directory, "capture")
    print("PASS actual malformed backend startup exits nonzero, no writer/no ready handoff, migrated database retained; diagnostics captured", flush=True)


def main():
    before = inventory()
    print("TOOLS", e.docker("version", "--format", "{{json .}}"), e.docker("compose", "version"), flush=True)
    images, inputs = e.image_inputs()
    print("QUALIFIED-INPUTS", json.dumps({"images": images, "hashes": inputs}, sort_keys=True), flush=True)
    directories = [Path("/tmp/opencode/atw-e2e-" + uuid.uuid4().hex) for _ in range(4)]
    started = time.monotonic()
    try:
        directory = directories[0]
        cli(directory, "provision")
        cli(directory, "wait")
        cli(directory, "provision", expected=1)  # no restart/reset on a used target
        retained_checks(directory)
        cli(directory, "cleanup")
        assert (directory / "cleaned.json").exists()
        assert not any((directory / n).exists() for n in ("db-password", "harness-token", "relay.json", "ready.json"))
        cli(directory, "provision", expected=1)  # cleaned instances aren't reusable either
        assert inventory() == before
        migration_failure(directories[1])
        cli(directories[1], "cleanup")
        assert inventory() == before
        startup_failure(directories[3])
        cli(directories[3], "cleanup")
        assert inventory() == before

        # Fresh provision must not adopt/delete an identically named foreign store.
        foreign = directories[2].name + "-database"
        e.docker("volume", "create", "--label", "org.asyncttwiz.probe=" + directories[2].name, foreign)
        try:
            cli(directories[2], "provision", expected=1)
            cli(directories[2], "cleanup", expected=1)
            assert e.inspect("volume", foreign)["Labels"]["org.asyncttwiz.probe"] == directories[2].name
            print("PASS reused/foreign target refused without adoption/removal", flush=True)
        finally:
            info = e.inspect("volume", foreign)
            if info:
                assert info["Labels"]["org.asyncttwiz.probe"] == directories[2].name
                e.docker("volume", "rm", foreign)
        # No resources were created for that failed target. Now safe exact cleanup
        # removes only its temporary secrets and records the empty inventory.
        cli(directories[2], "cleanup")
    finally:
        for directory in directories:
            if (directory / "instance.json").exists() and not (directory / "cleaned.json").exists():
                cli(directory, "capture")
                cli(directory, "cleanup")
        assert inventory() == before, {"before": before, "after": inventory()}
        print("PASS exact owned Compose containers/networks/volumes/listeners/temporary-secret cleanup; unrelated inventory unchanged", json.dumps(before, sort_keys=True), flush=True)
    print("PASS real-image Compose lifecycle qualification; elapsed seconds", round(time.monotonic() - started, 3), flush=True)


if __name__ == "__main__":
    main()
