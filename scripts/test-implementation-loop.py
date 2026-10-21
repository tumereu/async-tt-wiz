#!/usr/bin/env python3
"""Runner qualification: real disposable Git remotes; no product/model work."""
import fcntl
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True  # Qualification must not dirty the checkout with import caches.
spec = importlib.util.spec_from_file_location("implementation_loop", ROOT / "scripts/implementation-loop.py")
loop = importlib.util.module_from_spec(spec)
spec.loader.exec_module(loop)


def initial_state():
    return {
        "formatVersion": 1, "mode": "active", "authorization": "authorized",
        "authorizationRecord": "Owner-authorized disposable runner qualification only",
        "runner": {"model": loop.MODEL, "permissions": "docs/agent/implementation-permissions.json"},
        "activeRun": None, "runs": [], "findings": [], "externalReleaseGates": [],
        "batches": [{"id": "B1", "status": "pending", "reviewedCommit": None}],
        "tasks": [{"id": "F1", "batch": "B1", "status": "pending"},
                  {"id": "F2", "batch": "B1", "status": "pending"}],
    }


class GitFixture:
    def __init__(self):
        self.temp = tempfile.TemporaryDirectory(prefix="atw-loop-test-", dir="/tmp/opencode")
        self.base = Path(self.temp.name)
        self.root = self.base / "repo"
        self.root.mkdir()
        self.remote = self.base / "remote.git"
        self.git("init", "--bare", str(self.remote))
        self.git("init", "-b", "main")
        self.git("config", "user.name", "Runner Fixture")
        self.git("config", "user.email", "runner-fixture@example.invalid")
        self.git("remote", "add", "origin", str(self.remote))
        self.write(loop.STATE, initial_state())
        self.write("probe.txt", "disposable baseline\n")
        self.write("unrelated.txt", "preserve unrelated work\n")
        self.write("AGENTS.md", "Disposable runner fixture only. No product authorization.\n")
        for name in ("implementation-agent.md", "implementation-permissions.json"):
            destination = self.root / "docs/agent" / name
            shutil.copy2(ROOT / "docs/agent" / name, destination)
        script = self.root / "scripts/save.sh"
        script.parent.mkdir()
        shutil.copy2(ROOT / "scripts/save.sh", script)
        script.chmod(0o755)
        self.git("add", ".")
        self.git("commit", "-m", "Fixture baseline")
        self.git("push", "-u", "origin", "main")

    def write(self, relative, value):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, indent=2) + "\n" if not isinstance(value, str) else value)

    def git(self, *args):
        return loop.run_command(["git", *args], self.root)

    def commit(self):
        self.git("add", ".")
        self.git("commit", "-m", "Fixture setup")
        self.git("push")

    def state(self):
        return loop.load(self.root / loop.STATE)

    def close(self):
        self.temp.cleanup()


class FakeOpenCode:
    def __init__(self, fixture, fault=None, issue=True):
        self.fixture, self.fault, self.issue = fixture, fault, issue
        self.calls, self.sessions = [], []
        self.runner = None

    def create(self, title, permissions):
        identifier = "ses_fixture_" + str(len(self.sessions))
        self.sessions.append(identifier)
        return identifier

    def is_active(self, session):
        return False

    def execute(self, session, prompt, log, timeout):
        context = json.loads(prompt.split("\n\nTHIS RUN:\n", 1)[1])
        choice = context["selection"]
        mode = choice["mode"]
        self.calls.append(mode)
        log.write_text("Controlled fake CLI; this is not a live model test\n")
        if self.fault == "timeout":
            raise loop.Halt("Agent exceeded the configured wall-time budget")
        if self.fault == "missing-report":
            return {"model": loop.MODEL_REF}
        state = self.fixture.state()
        if self.fault != "no-progress":
            if mode == "implement":
                task = next(t for t in state["tasks"] if t["id"] == choice["target"])
                task["status"] = "blocked" if self.fault == "blocked" else "implemented"
                if all(t["status"] == "implemented" for t in state["tasks"]):
                    state["batches"][0]["status"] = "ready-for-review"
            elif mode == "review":
                if self.issue:
                    state["findings"].append({"id": "R1", "batch": "B1", "status": "open",
                                               "disposition": "actionable"})
                    state["batches"][0]["status"] = "changes-requested"
                else:
                    state["batches"][0].update(status="reviewed", reviewedCommit=context["startCommit"])
            elif mode in {"fix", "verify"}:
                state["findings"][0]["status"] = "fixed-awaiting-check" if mode == "fix" else "verified"
                state["batches"][0]["status"] = "fixes-awaiting-check" if mode == "fix" else "reviewed"
                if self.fault == "partial-fix" and mode == "fix":
                    state["findings"][0]["status"] = "open"
                    state["findings"][0]["attemptNumber"] = state["findings"][0].get("attemptNumber", 0) + 1
                    state["batches"][0]["status"] = "changes-requested"
                if mode == "verify":
                    state["batches"][0]["reviewedCommit"] = context["startCommit"]
            elif mode == "complete":
                state["mode"] = "complete"
        if self.fault == "premature-complete":
            state["mode"] = "complete"
        if self.fault == "change-authorization":
            state["authorizationRecord"] = "Agent changed authorization"
        state["runs"].append(context["report"])
        evidence = context["report"].replace(".json", "-checks.log")
        paths = [loop.STATE, context["report"], evidence, "probe.txt"]
        self.fixture.write("probe.txt", f"{mode} {choice['target']} {len(state['runs'])}\n")
        self.fixture.write(evidence, "Fixture required check output\n")
        report = {
            "runId": context["runId"], "sessionID": session, "startCommit": context["startCommit"],
            "mode": mode, "target": choice["target"], "findingIds": choice["findingIds"],
            "outcome": "complete" if mode == "complete" or self.fault == "premature-complete"
            else "blocked" if self.fault == "blocked" else "progress",
            "summary": "Controlled fixture outcome", "selectionReason": "Required priority",
            "checks": [{"kind": "required", "command": "fixture check", "exitCode": 1
                        if self.fault == "failed-check" else 0, "evidence": evidence}],
            "changedPaths": paths,
        }
        if self.fault == "wrong-selection":
            report["mode"] = "review"
        if self.fault == "missing-evidence":
            report["checks"][0]["evidence"] = "absent.log"
        if self.fault == "diagnostic":
            report["checks"].append({"kind": "diagnostic", "command": "reproduce issue",
                                     "exitCode": 1, "evidence": evidence})
        self.fixture.write(loop.STATE, state)
        self.fixture.write(context["report"], report)
        if self.fault == "extra-path":
            self.fixture.write("unrelated.txt", "unexpected changed work\n")
            self.fixture.git("add", "unrelated.txt")
        if self.fault == "dirty-after":
            return {"model": loop.MODEL_REF}
        if self.fault == "unpushed":
            self.fixture.git("add", "--", *paths)
            self.fixture.git("commit", "-m", "Controlled unpushed work")
        else:
            loop.run_command(["./scripts/save.sh", *paths], self.fixture.root)
        if self.fault == "stop":
            (self.runner.journal / "STOP").touch()
        return {"model": {**loop.MODEL_REF, "variant": "low"} if self.fault == "wrong-model"
                else loop.MODEL_REF, "outcome": "succeeded"}


class SelectionTests(unittest.TestCase):
    def test_group_review_not_after_each_task(self):
        state = initial_state()
        self.assertEqual(loop.selection(state)["target"], "F1")
        state["tasks"][0]["status"] = "implemented"
        self.assertEqual(loop.selection(state), {"mode": "implement", "target": "F2", "findingIds": []})
        state["tasks"][1]["status"] = "implemented"
        self.assertEqual(loop.selection(state)["mode"], "review")

    def test_fix_then_targeted_verification(self):
        state = initial_state()
        state["findings"] = [{"id": "R1", "batch": "B1", "status": "open", "disposition": "actionable"}]
        self.assertEqual(loop.selection(state)["mode"], "fix")
        state["findings"][0]["status"] = "fixed-awaiting-check"
        self.assertEqual(loop.selection(state)["mode"], "verify")

    def test_report_commit_does_not_reopen_review(self):
        state = initial_state()
        for task in state["tasks"]:
            task["status"] = "implemented"
        state["batches"][0].update(status="reviewed", reviewedCommit="older-code-commit")
        self.assertEqual(loop.selection(state)["mode"], "complete")

    def test_release_gate_not_fake_completion(self):
        state = initial_state()
        for task in state["tasks"]:
            task["status"] = "implemented"
        state["batches"][0].update(status="reviewed", reviewedCommit="code")
        state["externalReleaseGates"] = [{"id": "hardware", "status": "unverified"}]
        self.assertEqual(loop.selection(state)["mode"], "blocked")

    def test_independent_work_around_blocker(self):
        state = initial_state()
        state["tasks"][0]["status"] = "blocked"
        self.assertEqual(loop.selection(state)["mode"], "blocked")
        state["tasks"][1]["dependsOn"] = []
        self.assertEqual(loop.selection(state)["target"], "F2")

    def test_child_split_remains_in_group(self):
        state = initial_state()
        state["tasks"][0].update(status="in-progress", children=["F1a"])
        state["tasks"].append({"id": "F1a", "batch": "B1", "status": "pending"})
        self.assertEqual(loop.selection(state)["target"], "F1a")
        state["tasks"][-1]["status"] = "implemented"
        self.assertEqual(loop.selection(state)["target"], "F1")

    def test_bad_state_fails_before_launch(self):
        state = initial_state()
        state["tasks"][0]["children"] = ["F1"]
        with self.assertRaises(loop.Halt):
            loop.selection(state)


class RunnerTests(unittest.TestCase):
    def setUp(self):
        self.fixture = GitFixture()

    def tearDown(self):
        self.fixture.close()

    def runner(self, fault=None, issue=True):
        client = FakeOpenCode(self.fixture, fault, issue)
        runner = loop.Runner(self.fixture.root, client=client)
        client.runner = runner
        return runner, client

    def test_full_serial_cycle_and_actual_pushes(self):
        runner, client = self.runner()
        result = runner.run(10, 30)
        self.assertEqual(result["status"], "complete")
        self.assertEqual(client.calls, ["implement", "implement", "review", "fix", "verify", "complete"])
        self.assertEqual(len(set(client.sessions)), 6)
        self.assertFalse((runner.journal / "active.json").exists())
        runner.safe_git()

    def test_no_findings_needs_no_extra_fix_review(self):
        runner, client = self.runner(issue=False)
        self.assertEqual(runner.run(10, 30)["status"], "complete")
        self.assertEqual(client.calls, ["implement", "implement", "review", "complete"])

    def test_run_count_is_pause_not_completion(self):
        runner, client = self.runner()
        self.assertEqual(runner.run(1, 30)["status"], "paused")
        self.assertEqual(self.fixture.state()["mode"], "active")

    def test_completed_state_does_not_spend_another_session(self):
        runner, client = self.runner(issue=False)
        runner.run(10, 30)
        count = len(client.calls)
        self.assertEqual(runner.run(10, 30)["status"], "complete")
        self.assertEqual(len(client.calls), count)

    def test_repeated_unresolved_fix_stops_instead_of_spinning(self):
        runner, client = self.runner()
        runner.run(3, 30)  # Two implementations and one grouped review.
        client.fault = "partial-fix"
        with self.assertRaisesRegex(loop.Halt, "Repeated unresolved fix"):
            runner.run(10, 30)
        self.assertEqual(client.calls[-2:], ["fix", "fix"])
        self.assertTrue((runner.journal / "active.json").exists())

    def test_stop_finishes_unit_without_launching_next(self):
        runner, client = self.runner("stop")
        self.assertEqual(runner.run(10, 30)["status"], "paused")
        self.assertEqual(client.calls, ["implement"])
        runner.safe_git()

    def test_unauthorized_product_state_never_launches(self):
        state = self.fixture.state()
        state.update(mode="draft", authorization="not-authorized", authorizationRecord=None)
        self.fixture.write(loop.STATE, state)
        self.fixture.commit()
        runner, client = self.runner()
        with self.assertRaisesRegex(loop.Halt, "not authorized"):
            runner.run(10, 30)
        self.assertEqual(client.sessions, [])

    def test_wrong_configured_model_never_launches(self):
        state = self.fixture.state()
        state["runner"]["model"] = "openai/gpt-6.1-sol#low"
        self.fixture.write(loop.STATE, state)
        self.fixture.commit()
        runner, client = self.runner()
        with self.assertRaisesRegex(loop.Halt, "explicitly pin"):
            runner.run(1, 30)
        self.assertFalse(client.sessions)

    def test_dirty_foreign_work_is_untouched(self):
        self.fixture.write("unrelated.txt", "owner's unfinished work\n")
        runner, client = self.runner()
        with self.assertRaisesRegex(loop.Halt, "Dirty working"):
            runner.run(10, 30)
        self.assertEqual((self.fixture.root / "unrelated.txt").read_text(), "owner's unfinished work\n")
        self.assertFalse(client.sessions)

    def test_staged_foreign_work_is_untouched(self):
        self.fixture.write("unrelated.txt", "owner's staged work\n")
        self.fixture.git("add", "unrelated.txt")
        runner, client = self.runner()
        with self.assertRaises(loop.Halt):
            runner.run(1, 30)
        self.assertEqual(self.fixture.git("diff", "--cached", "--name-only"), "unrelated.txt")
        self.assertFalse(client.sessions)

    def test_writer_lock_excludes_second_loop(self):
        runner, client = self.runner()
        with (runner.journal / "writer.lock").open("a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            with self.assertRaisesRegex(loop.Halt, "writer holds"):
                runner.run(1, 30)
        self.assertFalse(client.sessions)

    def test_failed_boundaries_stop_and_preserve_evidence(self):
        for fault in ["missing-report", "no-progress", "failed-check", "wrong-selection", "wrong-model",
                      "missing-evidence", "premature-complete", "change-authorization", "extra-path",
                      "dirty-after", "unpushed", "timeout"]:
            with self.subTest(fault=fault):
                fixture = GitFixture()
                try:
                    client = FakeOpenCode(fixture, fault)
                    runner = loop.Runner(fixture.root, client=client)
                    client.runner = runner
                    with self.assertRaises(loop.Halt):
                        runner.run(5, 30)
                    self.assertEqual(client.calls, ["implement"])
                    self.assertTrue((runner.journal / "active.json").exists())
                    with self.assertRaisesRegex(loop.Halt, "Interrupted/failed"):
                        runner.run(5, 30)
                    self.assertTrue(list(runner.journal.glob("*/result.json")))
                finally:
                    fixture.close()

    def test_diagnostic_failure_is_not_hidden_required_failure(self):
        runner, client = self.runner("diagnostic")
        self.assertEqual(runner.run(1, 30)["status"], "paused")

    def test_blocked_is_distinct_from_complete(self):
        runner, client = self.runner("blocked")
        self.assertEqual(runner.run(10, 30)["status"], "blocked")
        self.assertEqual(client.calls, ["implement"])

    def test_push_failure_requires_same_save_repair_not_new_agent(self):
        hook = self.fixture.remote / "hooks/pre-receive"
        hook.write_text("#!/bin/sh\nexit 1\n")
        hook.chmod(0o755)
        runner, client = self.runner()
        with self.assertRaises(loop.Halt):
            runner.run(5, 30)
        with self.assertRaisesRegex(loop.Halt, "upstream differ"):
            runner.recover()
        self.assertEqual(client.calls, ["implement"])
        hook.unlink()  # Remove only our own fixture's deliberate failure control.
        loop.run_command(["./scripts/save.sh"], self.fixture.root)
        self.assertEqual(runner.recover()["status"], "recovered")
        self.assertFalse((runner.journal / "active.json").exists())

    def test_recovery_refuses_running_session(self):
        runner, client = self.runner("missing-report")
        with self.assertRaises(loop.Halt):
            runner.run(1, 30)
        client.is_active = lambda session: True
        with self.assertRaisesRegex(loop.Halt, "still active"):
            runner.recover()

    def test_recovery_does_not_bypass_red_required_checks(self):
        runner, client = self.runner("failed-check")
        with self.assertRaises(loop.Halt):
            runner.run(1, 30)
        with self.assertRaisesRegex(loop.Halt, "failed required"):
            runner.recover()
        self.assertTrue((runner.journal / "active.json").exists())

    def test_timeout_interrupts_server_owned_session(self):
        executable = self.fixture.base / "fake-opencode"
        executable.write_text("#!/usr/bin/env python3\nimport time\ntime.sleep(60)\n")
        executable.chmod(0o755)
        client = loop.OpenCode(self.fixture.root, str(executable))
        calls = []
        client.api = lambda *args, **kwargs: calls.append(args) or {}
        with self.assertRaisesRegex(loop.Halt, "wall-time"):
            client.execute("ses_owned", "fixture", self.fixture.base / "timeout.log", 0.05)
        self.assertIn(("post", "/api/session/ses_owned/interrupt"), calls)


if __name__ == "__main__":
    unittest.main(verbosity=2)
