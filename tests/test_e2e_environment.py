"""Controller/topology/relay regressions; real Compose lifecycle is a separate check."""
import concurrent.futures
import contextlib
import hashlib
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch
import uuid

from e2e import environment as e


class EnvironmentUnitTests(unittest.TestCase):
    def test_remote_daemon_is_refused_without_any_mutation(self):
        with patch.dict(os.environ, {"DOCKER_HOST": "tcp://remote.invalid:2375", "DOCKER_CONTEXT": ""}), \
                patch.object(e, "docker") as docker:
            with self.assertRaises(e.Refusal):
                e.local_daemon()
            docker.assert_not_called()
        with patch.dict(os.environ, {"DOCKER_CONTEXT": "selected"}), \
                patch.object(e, "docker", return_value="ssh://remote.invalid"):
            with self.assertRaises(e.Refusal):
                e.local_daemon()

    def test_instance_target_is_narrow_and_symlinks_refused(self):
        for path in ("/tmp/opencode/other", "/tmp/opencode/atw-e2e-abc", "/etc/atw-e2e-" + "a" * 32,
                     "relative", "/tmp/opencode/sub/atw-e2e-" + "a" * 32):
            with self.assertRaises(e.Refusal):
                e.directory_path(path)
        with tempfile.TemporaryDirectory(dir="/tmp/opencode") as target:
            link = Path("/tmp/opencode/atw-e2e-" + uuid.uuid4().hex)
            link.symlink_to(target)
            try:
                with self.assertRaises(e.Refusal):
                    e.directory_path(link)
            finally:
                link.unlink()

    def test_topology_has_only_internal_routes_and_explicit_migration(self):
        topology = json.loads((e.ROOT / "e2e/compose.json").read_text())
        self.assertTrue(topology["networks"]["scenario"]["internal"])
        self.assertEqual(set(topology["services"]), set(e.SERVICES))
        for service, config in topology["services"].items():
            self.assertNotIn("ports", config)
            self.assertNotIn("build", config)
            self.assertEqual(config["restart"], "no")
            self.assertTrue(config["read_only"])
            if service != "adapter-init":
                self.assertEqual(config["networks"], ["scenario"])
        self.assertEqual(topology["services"]["backend"]["command"], ["serve"])
        self.assertEqual(topology["services"]["migrate"]["command"], ["migrate"])
        self.assertEqual(topology["services"]["adapter-init"]["network_mode"], "none")
        text = json.dumps(topology)
        self.assertNotIn("docker.sock", text)
        self.assertNotIn("privileged", text)

    def test_private_manifest_snapshot_and_secret_changes_refused(self):
        with tempfile.TemporaryDirectory(dir="/tmp/opencode") as name:
            path = Path(name)
            value = {"project": path.name, "directory": name, "owner": "a" * 64, "formatVersion": 1}
            for file in ("db-password", "harness-token", "compose.json"):
                (path / file).write_text("synthetic\n")
            value["secretHashes"] = {n: e.sha(path / n) for n in ("db-password", "harness-token")}
            value["composeSha256"] = e.sha(path / "compose.json")
            e.write_json(path / "instance.json", value)
            self.assertEqual(e.load(path), value)
            (path / "compose.json").write_text("changed")
            with self.assertRaises(e.Refusal):
                e.load(path)
            (path / "compose.json").write_text("synthetic\n")
            (path / "db-password").write_text("changed")
            with self.assertRaises(e.Refusal):
                e.load(path)

    def test_controller_lock_is_exclusive(self):
        with tempfile.TemporaryDirectory(dir="/tmp/opencode") as name:
            with e.locked(Path(name)):
                with self.assertRaises(e.Refusal):
                    with e.locked(Path(name)):
                        self.fail("second writer admitted")

    def test_deadline_never_converts_failure_to_success(self):
        with self.assertRaises(e.Refusal):
            e.deadline(lambda: e.require(False, "diagnostic"), 0)

    def test_owned_refuses_foreign_before_any_destructive_command(self):
        value = {"project": "test", "owner": "mine"}
        with patch.object(e, "inspect", return_value={"Config": {"Labels": {e.LABEL: "foreign"}}}), \
                patch.object(e, "docker") as docker:
            with self.assertRaises(e.Refusal):
                e.owned(value)
            docker.assert_not_called()

    def test_inspection_accepts_docker_lowercase_missing_volume_only(self):
        result = subprocess.CompletedProcess([], 1, stdout="[]", stderr="Error response from daemon: no such volume")
        with patch.object(e.subprocess, "run", return_value=result):
            self.assertIsNone(e.inspect("volume", "owned-missing"))
        result.stderr = "permission denied"
        with patch.object(e.subprocess, "run", return_value=result):
            with self.assertRaises(e.Refusal):
                e.inspect("volume", "owned-missing")

    def test_unowned_or_changed_relay_identity_is_not_signaled(self):
        with tempfile.TemporaryDirectory(dir="/tmp/opencode") as name:
            path = Path(name)
            recorded = e.process_identity(os.getpid())
            e.write_json(path / "relay.json", recorded)
            with patch.object(e.os, "kill") as kill:
                with self.assertRaises(e.Refusal):
                    e.stop_relay({"directory": name})
                kill.assert_not_called()

    def test_capture_redacts_both_file_secrets(self):
        with tempfile.TemporaryDirectory(dir="/tmp/opencode") as name:
            for file in ("db-password", "harness-token"):
                (Path(name) / file).write_text(file + "-sentinel\n")
            self.assertEqual(e.redact({"directory": name}, "db-password-sentinel harness-token-sentinel"),
                             "[REDACTED] [REDACTED]")

    def test_failed_wait_invalidates_prior_handoff(self):
        with tempfile.TemporaryDirectory(dir="/tmp/opencode") as name:
            (Path(name) / "ready.json").write_text("old success")
            with patch.object(e, "owned", side_effect=e.Refusal("unavailable")):
                with self.assertRaises(e.Refusal):
                    e.wait({"directory": name}, 0)
            self.assertFalse((Path(name) / "ready.json").exists())

    def test_incomplete_reset_cannot_be_blessed_by_wait_or_restart(self):
        with tempfile.TemporaryDirectory(dir="/tmp/opencode") as name:
            path = Path(name)
            (path / "reset-pending.json").write_text("unfinished")
            for operation in (lambda: e.wait({"directory": name}, 0),
                              lambda: e.restart({"directory": name}, "all", 0)):
                (path / "ready.json").write_text("old success")
                with patch.object(e, "owned") as owned, patch.object(e, "compose") as compose:
                    with self.assertRaises(e.Refusal):
                        operation()
                    owned.assert_not_called()
                    compose.assert_not_called()
                self.assertFalse((path / "ready.json").exists())

    def test_reset_failure_never_leaves_handoff_and_records_retry_obligation(self):
        with tempfile.TemporaryDirectory(dir="/tmp/opencode") as name:
            path = Path(name)
            value = {"directory": name, "project": "test"}
            (path / "ready.json").write_text("old success")
            with patch.object(e, "owned"), patch.object(e, "capture") as capture, \
                    patch.object(e, "stop_relay") as stop, \
                    patch.object(e, "remove_runtime", side_effect=e.Refusal("stop failed")), \
                    patch.object(e, "fresh_services") as fresh:
                with self.assertRaises(e.Refusal):
                    e.reset(value, 0)
                fresh.assert_not_called()
                self.assertEqual(capture.call_count, 2)
                stop.assert_called_once_with(value)
            self.assertTrue((path / "reset-pending.json").exists())
            self.assertFalse((path / "ready.json").exists())

    def test_old_producer_must_be_dead_before_any_store_removal(self):
        value = {"project": "test"}
        active = {"backend": {"Id": "old", "State": {"Running": True, "Pid": 123}}}
        with patch.object(e, "owned", return_value=active), patch.object(e, "stop_relay"), \
                patch.object(e, "compose") as compose, patch.object(e, "docker") as docker:
            with self.assertRaises(e.Refusal):
                e.remove_runtime(value)
            self.assertEqual(compose.call_args_list[0].args[1:], ("--profile", "setup", "stop", "--timeout", "30"))
            self.assertEqual(compose.call_count, 1)  # no down or volume deletion
            docker.assert_not_called()

    def test_reset_capture_failure_keeps_pending_barrier_without_deleting_stores(self):
        with tempfile.TemporaryDirectory(dir="/tmp/opencode") as name:
            with patch.object(e, "owned"), patch.object(e, "capture", side_effect=e.Refusal("capture failed")), \
                    patch.object(e, "stop_relay"), patch.object(e, "remove_runtime") as remove:
                with self.assertRaises(e.Refusal):
                    e.reset({"directory": name, "project": "test"}, 0)
                remove.assert_not_called()
            self.assertTrue((Path(name) / "reset-pending.json").exists())
            self.assertFalse((Path(name) / "ready.json").exists())

    def test_foreign_reset_target_is_refused_before_capture_or_shutdown(self):
        with tempfile.TemporaryDirectory(dir="/tmp/opencode") as name:
            (Path(name) / "ready.json").write_text("old success")
            with patch.object(e, "owned", side_effect=e.Refusal("foreign")), \
                    patch.object(e, "capture") as capture, patch.object(e, "remove_runtime") as remove:
                with self.assertRaises(e.Refusal):
                    e.reset({"directory": name}, 0)
                capture.assert_not_called()
                remove.assert_not_called()
            self.assertFalse((Path(name) / "ready.json").exists())

    def test_socket_reservation_rejects_collision(self):
        with e.reserve() as first:
            with self.assertRaises(OSError):
                e.reserve(first.getsockname()[1])

    def test_real_relay_only_forwards_supplied_ports_and_stops(self):
        with e.reserve() as upstream, e.reserve() as backend, e.reserve() as source:
            # Relay's real target port is deliberately fixed at 8080. Test its
            # forwarding primitive independently with an OS-allocated echo port.
            from e2e.relay import forward
            local, client = socket.socketpair()

            def echo():
                connection, _ = upstream.accept()
                with connection:
                    self.assertEqual(connection.recv(10), b"hello")
                    connection.sendall(b"world")

            with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
                echo_future = pool.submit(echo)
                relay_future = pool.submit(forward, local, upstream.getsockname())
                with client:
                    client.settimeout(3)
                    client.sendall(b"hello")
                    self.assertEqual(client.recv(10), b"world")
                echo_future.result(timeout=3)
                relay_future.result(timeout=3)
            with tempfile.TemporaryDirectory(dir="/tmp/opencode") as name:
                log = Path(name) / "relay.log"
                with log.open("wb") as output:
                    process = subprocess.Popen([sys.executable, str(e.ROOT / "e2e/relay.py"), "--directory", name,
                                                "--backend-fd", str(backend.fileno()), "--source-fd", str(source.fileno()),
                                                "--backend-ip", "127.0.0.1", "--source-ip", "127.0.0.1"],
                                               pass_fds=(backend.fileno(), source.fileno()), stdout=output, stderr=subprocess.STDOUT)
                    e.write_json(Path(name) / "relay.json", e.process_identity(process.pid))
                    e.stop_relay({"directory": name})
                    process.wait(timeout=5)
                self.assertEqual(log.read_bytes(), b"")


if __name__ == "__main__":
    unittest.main()
