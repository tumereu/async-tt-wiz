#!/usr/bin/env python3
"""Qualify explicit reset of an already provisioned owned Compose instance.

Invoked by ComposeIsolationTest while a separately owned real Testcontainers DB
and sentinel transaction/connection remain live. No app/account/game injection.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import smtplib
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from e2e import environment as e

spec = importlib.util.spec_from_file_location("lifecycle", e.ROOT / "scripts/check-e2e-environment.py")
lifecycle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lifecycle)
cli = lifecycle.cli


def assert_baseline(directory, old_ids):
    value = e.load(directory)
    current = e.owned(value)
    assert not set(old_ids.values()) & {c["Id"] for c in current.values()}
    for identity in old_ids.values():
        assert e.inspect("container", identity) is None
    assert e.empty_baseline(value)["messageCount"] == 0
    assert e.database(value, "SELECT to_regclass('public.infrastructure_probe') IS NULL") == "t"
    assert (directory / "ready.json").exists() and not (directory / "reset-pending.json").exists()
    completed = json.loads((directory / "reset-completed.json").read_text())
    assert completed["removedContainers"] == old_ids
    assert completed["baseline"]["versions"] == {"prism": 1, "quilt": 1}
    # Prove the normal pinned v1 source path (not just an admin control value).
    served = e.admin(value, "/admin/inventory")
    for dataset in ("prism", "quilt"):
        status, data = e.request("127.0.0.1", value["ports"]["source"], f"/{dataset}/Meta.json")
        assert status == 200
        assert hashlib.sha256(data).hexdigest() == served["files"][f"{dataset}/v1/Meta.json"]
    print("PASS independently empty migrated baseline; new process IDs; pinned v1 bytes; no probe/mail/fault/old namespace", flush=True)


def dirty(value):
    e.database(value, "CREATE TABLE infrastructure_probe(value text); INSERT INTO infrastructure_probe VALUES ('old-scenario')")
    e.admin(value, "/admin/control", {"instance": value["project"], "versions": {"prism": 2, "quilt": 2}})
    address = e.ip(value, e.owned(value)["adapters"])
    with smtplib.SMTP(address, 2525, timeout=3) as smtp:
        smtp.sendmail("test@example.invalid", "sink@example.invalid", b"Subject: reset probe\r\n\r\nreset-private-body\r\n")
    assert e.admin(value, "/admin/state")["messageCount"] == 1
    e.admin(value, "/admin/control", {"instance": value["project"], "sourceFault": "unavailable", "mailFault": "temporary"})


def change_topology(directory, edit):
    # Test-owned configuration fault, NOT a production CLI fault/seed feature.
    value = e.load(directory)
    file = directory / "compose.json"
    topology = json.loads(file.read_text())
    edit(topology)
    e.write_json(file, topology)
    value["composeSha256"] = e.sha(file)
    e.write_json(directory / "instance.json", value)


def failed_reset(directory, edit, restore, expected_log):
    value = e.load(directory)
    change_topology(directory, edit)
    started = time.monotonic()
    cli(directory, "--timeout", "2", "reset", expected=1)
    assert time.monotonic() - started < 180
    assert not (directory / "ready.json").exists() and not (directory / "relay.json").exists()
    assert (directory / "reset-pending.json").exists()
    cli(directory, "--timeout", "1", "wait", expected=1)
    cli(directory, "--timeout", "1", "restart", expected=1)
    cli(directory, "capture")
    assert any(expected_log in p.read_text() for p in directory.glob("capture-*.json"))
    change_topology(directory, restore)
    old_ids = {s: c["Id"] for s, c in e.owned(value).items()}
    cli(directory, "reset")
    assert_baseline(directory, old_ids)
    print("PASS public CLI failed reset is bounded/nonzero, no handoff/relay; wait/restart cannot bless it; explicit repaired reset passes", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", required=True)
    args = parser.parse_args()
    directory = e.directory_path(args.directory)
    value = e.load(directory)
    print("QUALIFIED-RESET-INPUTS", json.dumps({str(p.relative_to(e.ROOT)): e.sha(p) for p in
          (e.ROOT / "e2e/environment.py", e.ROOT / "scripts/check-e2e-reset.py", e.ROOT / "e2e/compose.json")}, sort_keys=True), flush=True)

    # Existing retained restarts, isolation, privacy and exact fixture-byte checks
    # run before introducing reset: no recovery checkpoint is accidentally wiped.
    lifecycle.retained_checks(directory)
    old_ids = {s: c["Id"] for s, c in e.owned(value).items()}
    cli(directory, "reset")
    assert_baseline(directory, old_ids)
    for iteration in range(2):
        dirty(value)
        # Keep actual SMTP DATA and a database connection active through shutdown.
        e.admin(value, "/admin/control", {"instance": value["project"], "mailFault": "ok"})
        smtp = smtplib.SMTP(e.ip(value, e.owned(value)["adapters"]), 2525, timeout=3)
        smtp.helo("old-scenario")
        smtp.mail("old@example.invalid")
        smtp.rcpt("sink@example.invalid")
        smtp.putcmd("DATA")
        assert smtp.getreply()[0] == 354
        smtp.send(b"Subject: incomplete old delivery\r\n\r\nold-inflight-body\r\n")
        old_db = subprocess.Popen(["docker", "exec", value["project"] + "-database", "psql", "-U", "atw", "-d", "atw",
                                   "-AtX", "-c", "SET application_name='old_reset_probe'; SELECT pg_sleep(300)"],
                                  stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        try:
            e.deadline(lambda: e.require(e.database(value, "SELECT count(*) FROM pg_stat_activity WHERE application_name='old_reset_probe'") == "1", "old DB session not observed"), 10)
            old_ids = {s: c["Id"] for s, c in e.owned(value).items()}
            cli(directory, "reset")
            old_db.communicate(timeout=10)
            assert old_db.returncode != 0
            try:
                smtp.send(b".\r\n")
                reply = smtp.getreply()
                assert reply[0] != 250, "old SMTP delivery accepted after reset"
            except (OSError, smtplib.SMTPException):
                pass
            assert_baseline(directory, old_ids)
            print("PASS repeat reset", iteration + 1, "active old PostgreSQL/SMTP connections terminated; no late delivery", flush=True)
        finally:
            smtp.close()
            if old_db.poll() is None:
                old_db.terminate()
                old_db.communicate(timeout=10)

    # A stopped required service is an environment failure, not a reason to skip
    # the next scenario's reset or retain the dirty stores.
    dirty(value)
    e.docker("stop", "-t", "30", value["project"] + "-backend")
    cli(directory, "--timeout", "1", "wait", expected=1)
    cli(directory, "capture")
    old_ids = {s: c["Id"] for s, c in e.owned(value).items()}
    cli(directory, "reset")
    assert_baseline(directory, old_ids)
    print("PASS dirty failed environment -> explicit empty reset", flush=True)

    # A foreign mount must refuse reset before stopping any existing service.
    helper = value["project"] + "-foreign-mount-probe"
    ids = {s: c["Id"] for s, c in e.owned(value).items()}
    try:
        e.docker("create", "--name", helper, "--network", "none", "--label", "org.asyncttwiz.probe=" + helper,
                 "--mount", "type=volume,src=" + value["project"] + "-database,dst=/probe,readonly",
                 "--entrypoint", "python3", value["images"]["adapters"], "-c", "pass")
        cli(directory, "reset", expected=1)
        assert not (directory / "ready.json").exists()
        assert {s: c["Id"] for s, c in ((s, e.inspect("container", value["project"] + "-" + s)) for s in e.SERVICES)} == ids
        assert all(e.inspect("container", value["project"] + "-" + s)["State"]["Running"] for s in ("database", "backend", "adapters"))
        print("PASS shared foreign target refused before any shutdown/store deletion", flush=True)
    finally:
        info = e.inspect("container", helper)
        if info:
            assert info["Config"]["Labels"]["org.asyncttwiz.probe"] == helper
            e.docker("rm", info["Id"])
    cli(directory, "reset")

    failed_reset(directory,
                 lambda t: t["services"]["migrate"]["environment"].update(DB_USER="missing_reset_test_role"),
                 lambda t: t["services"]["migrate"]["environment"].update(DB_USER="atw"), "Migration failed; no destructive fallback attempted")
    failed_reset(directory,
                 lambda t: t["services"]["backend"]["environment"].update(SERVER_PORT="malformed"),
                 lambda t: t["services"]["backend"]["environment"].update(SERVER_PORT="8080"), "Service failed; check configuration")
    # Restart after a successful reset retains this new scenario's own data.
    e.database(value, "CREATE TABLE infrastructure_probe(value text); INSERT INTO infrastructure_probe VALUES ('new-scenario-retained')")
    cli(directory, "restart", "--service", "all")
    assert e.database(value, "SELECT value FROM infrastructure_probe") == "new-scenario-retained"
    cli(directory, "reset")
    cli(directory, "capture")
    for file in directory.glob("capture-*.json"):
        text = file.read_text()
        assert "reset-private-body" not in text and "old-inflight-body" not in text
        for name in ("db-password", "harness-token"):
            assert (directory / name).read_text().strip() not in text
    print("PASS complete repeated/failure/reset/retained/privacy host qualification; Testcontainers witness remains independently owned", flush=True)


if __name__ == "__main__":
    main()
