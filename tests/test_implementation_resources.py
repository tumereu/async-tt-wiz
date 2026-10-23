"""Real cross-process/worktree lease contention, without real device/service use."""
import hashlib
import fcntl
import importlib.util
import json
from pathlib import Path
import signal
import subprocess
import sys
import unittest

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/implementation-resources.py"
spec = importlib.util.spec_from_file_location("fixtures", ROOT / "scripts/test-implementation-loop.py")
fixtures = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixtures)


class ResourceTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.GitFixture()
        self.root = self.fixture.root
        self.processes = []
        self.directory = self.root / ".git/implementation-loop/resources"

    def tearDown(self):
        for process in self.processes:
            if not process.stdin.closed:
                process.stdin.close()
            if process.poll() is None:
                process.wait(timeout=15)
            process.stdout.close()
            process.stderr.close()
        self.fixture.close()

    def args(self, owner="first", resources=("devices",), mode="run"):
        args = [sys.executable, str(SCRIPT), mode, "--owner", owner]
        for resource in resources:
            args += ["--resource", resource]
        return args

    def hold(self, resources=("devices",), owner="first", root=None):
        process = subprocess.Popen(self.args(owner, resources) + ["--", sys.executable, "-c",
                                   "import sys;print('LEASED',flush=True);sys.stdin.read()"],
                                   cwd=root or self.root, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                   stderr=subprocess.PIPE, text=True)
        self.processes.append(process)
        self.assertEqual(process.stdout.readline().strip(), "LEASED")
        return process

    def run_command(self, resources=("devices",), owner="second", code=0, root=None):
        return subprocess.run(self.args(owner, resources) + ["--", sys.executable, "-c", f"raise SystemExit({code})"],
                              cwd=root or self.root, text=True, capture_output=True, timeout=15)

    def proof(self, owner="first", code=0):
        path = f"docs/agent/implementation-runs/{owner}-cleanup.json"
        self.fixture.write(path, {"command": ["fixture-owned-cleanup"], "exitCode": code, "output": "cleaned\n",
                                  "outputSha256": hashlib.sha256(b"cleaned\n").hexdigest()})
        return path

    def recover(self, owner="first", proof=None):
        return subprocess.run(self.args(owner, mode="recover") + ["--cleanup-evidence", proof or self.proof(owner)],
                              cwd=self.root, text=True, capture_output=True, timeout=15)

    def test_same_resource_excludes_sibling_worktree_and_success_releases(self):
        sibling = self.fixture.base / "sibling"
        self.fixture.git("worktree", "add", "-b", "worker", str(sibling))
        process = self.hold()
        self.assertEqual(self.run_command(root=sibling).returncode, 75)
        self.assertEqual(json.loads((self.directory / "devices.json").read_text())["owner"], "first")
        process.stdin.close(); self.assertEqual(process.wait(timeout=10), 0)
        self.assertFalse((self.directory / "devices.json").exists())
        self.assertEqual(self.run_command(root=sibling).returncode, 0)

    def test_independent_resources_can_overlap(self):
        self.hold()
        self.assertEqual(self.run_command(resources=("isolated-backend",)).returncode, 0)

    def test_integration_writer_excludes_legacy_controller(self):
        directory = self.root / ".git/implementation-loop"
        directory.mkdir(parents=True)
        with (directory / "writer.lock").open("a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            self.assertEqual(self.run_command(resources=("integration-writer",)).returncode, 75)
        process = self.hold(resources=("integration-writer",))
        with (directory / "writer.lock").open("a") as lock:
            with self.assertRaises(BlockingIOError):
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        process.stdin.close(); process.wait(timeout=10)

    def test_resource_bundle_order_cannot_deadlock_or_take_partial_ownership(self):
        self.hold(resources=("routes", "devices"))
        self.assertEqual(self.run_command(resources=("devices", "routes")).returncode, 75)
        for resource in ("devices", "routes"):
            self.assertEqual(json.loads((self.directory / (resource + ".json")).read_text())["owner"], "first")

    def test_failed_managed_writer_fences_legacy_run_and_recover_until_owned_cleanup(self):
        self.assertEqual(self.run_command(resources=("integration-writer",), owner="first", code=3).returncode, 3)
        runner = fixtures.loop.Runner(self.root, client=fixtures.FakeOpenCode(self.fixture))
        record = {"runId": "legacy", "worktree": str(self.root), "branch": "main", "state": fixtures.loop.STATE,
                  "report": fixtures.loop.REPORTS + "/legacy.json", "selection": {"mode": "implement", "target": "F1"}}
        fixtures.loop.atomic_json(runner.journal / "active.json", record)
        for operation in (lambda: runner.run(1, 60), runner.recover):
            with self.assertRaisesRegex(fixtures.loop.Halt, "Retained managed integration-writer"):
                operation()
        self.assertTrue((runner.journal / "active.json").exists())
        for owner, code in (("wrong", 0), ("first", 1), ("first", 0)):
            result = subprocess.run(self.args(owner, ("integration-writer",), "recover") +
                                    ["--cleanup-evidence", self.proof(owner, code)], cwd=self.root,
                                    text=True, capture_output=True, timeout=15)
            self.assertEqual(result.returncode, 0 if owner == "first" and code == 0 else 75)
        self.assertTrue((self.directory / "integration-writer.recovered.json").exists())
        self.fixture.commit()  # Legacy recovery still requires cleanup evidence saved/pushed.
        self.assertEqual(runner.recover()["status"], "recovered")
        (runner.journal / "STOP").touch()
        self.assertEqual(runner.run(1, 60)["status"], "paused")
        self.assertEqual(runner.client.calls, [])

    def test_failed_command_retains_fence_until_its_owner_proves_cleanup(self):
        self.assertEqual(self.run_command(owner="first", code=3).returncode, 3)
        self.assertEqual(self.run_command().returncode, 75)
        self.assertEqual(self.recover(owner="wrong").returncode, 75)
        self.assertEqual(self.recover(proof=self.proof(code=1)).returncode, 75)
        self.assertEqual(self.recover().returncode, 0)
        self.assertTrue((self.directory / "devices.recovered.json").exists())
        self.assertEqual(self.run_command().returncode, 0)

    def test_running_owner_cannot_be_recovered(self):
        self.hold()
        self.assertEqual(self.recover().returncode, 75)

    def test_interrupt_stops_only_owned_command_and_retains_fence(self):
        process = self.hold()
        process.send_signal(signal.SIGTERM)
        self.assertEqual(process.wait(timeout=15), 130)
        self.assertEqual(self.run_command().returncode, 75)
        self.assertEqual(self.recover().returncode, 0)

    def test_killed_wrapper_does_not_release_still_running_child_locks(self):
        process = self.hold()
        process.kill()
        self.assertEqual(process.wait(timeout=10), -signal.SIGKILL)
        self.assertEqual(self.recover().returncode, 75)
        process.stdin.close()
        process.stdout.read()  # EOF only after the owned child closes its inherited descriptors.
        self.assertEqual(self.recover().returncode, 0)

    def test_invalid_resource_names_and_wrong_namespace_are_refused(self):
        result = self.run_command(resources=("../unsafe",))
        self.assertEqual(result.returncode, 2)
        self.assertEqual(self.run_command(owner="first", code=3).returncode, 3)
        self.assertEqual(self.recover(proof=self.proof("other")).returncode, 75)


if __name__ == "__main__":
    unittest.main(verbosity=2)
