#!/usr/bin/env python3
"""Runner qualification: real disposable Git remotes; no product/model work."""
import fcntl
import copy
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
        self.transform = None
        self.finding_limit = None

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
        finding_ids = []
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
                    finding_ids = ["R1"]
                    state["batches"][0]["status"] = "changes-requested"
                else:
                    state["batches"][0].update(status="reviewed", reviewedCommit=context["startCommit"])
            elif mode in {"fix", "verify"}:
                finding_ids = choice["findingIds"][:self.finding_limit]
                for finding in state["findings"]:
                    if finding["id"] not in finding_ids:
                        continue
                    finding["status"] = "fixed-awaiting-check" if mode == "fix" else "verified"
                    if self.fault == "partial-fix" and mode == "fix":
                        finding["status"] = "open"
                        finding["attemptNumber"] = finding.get("attemptNumber", 0) + 1
                    if self.fault == "failed-verification" and mode == "verify":
                        finding["status"] = "open"
                batch = next(b for b in state["batches"] if b["id"] == choice["target"])
                issues = [f for f in state["findings"] if f["batch"] == batch["id"]]
                batch["status"] = ("fixes-awaiting-check" if any(f["status"] == "fixed-awaiting-check" for f in issues)
                                   else "changes-requested" if any(f["status"] == "open" for f in issues)
                                   else "reviewed")
                if batch["status"] == "reviewed":
                    batch["reviewedCommit"] = context["startCommit"]
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
            "mode": mode, "target": choice["target"], "findingIds": finding_ids,
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
        if self.transform:
            self.transform(context, state, report)
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

    def test_children_cannot_change_batch_have_two_parents_or_be_skipped(self):
        for fault in ("batch", "parents", "unfinished"):
            with self.subTest(fault=fault):
                state = initial_state()
                state["tasks"][0].update(status="in-progress", children=["F1a"])
                state["tasks"].append({"id": "F1a", "batch": "B1", "status": "pending"})
                if fault == "batch":
                    state["batches"].append({"id": "B2", "status": "pending"})
                    state["tasks"][-1]["batch"] = "B2"
                elif fault == "parents":
                    state["tasks"][1]["children"] = ["F1a"]
                else:
                    state["tasks"][0]["status"] = "implemented"
                with self.assertRaises(loop.Halt):
                    loop.validate_state(state)


class TransitionTests(unittest.TestCase):
    def fixture(self):
        before = initial_state()
        for task in before["tasks"]:
            task["status"] = "implemented"
        before["batches"][0].update(status="reviewed", reviewedCommit="previous-review")
        before["tasks"].append({"id": "F3", "batch": "B2", "status": "pending"})
        before["batches"].append({"id": "B2", "status": "pending", "reviewedCommit": None})
        after = copy.deepcopy(before)
        after["tasks"][-1]["status"] = "implemented"
        after["batches"][-1]["status"] = "ready-for-review"
        choice = loop.selection(before)
        report = {"startCommit": "current-code", "findingIds": []}
        return before, after, choice, report

    def test_scoped_reopening_does_not_review_another_capability(self):
        before, after, choice, report = self.fixture()
        after["batches"][0]["status"] = "ready-for-review"
        report["reopenedBatches"] = [{"id": "B1", "reason": "Delivered integration materially changes B1 behavior"}]
        loop.validate_state(after)
        loop.validate_transition(before, after, choice, report)
        self.assertEqual(loop.selection(after), {"mode": "review", "target": "B1", "findingIds": []})
        del report["reopenedBatches"]
        with self.assertRaisesRegex(loop.Halt, "Unrelated batch advancement"):
            loop.validate_transition(before, after, choice, report)

    def test_reopened_finding_needs_explicit_evidence_reason_and_new_fix(self):
        before, after, choice, report = self.fixture()
        before["findings"] = [{"id": "R1", "batch": "B1", "status": "verified", "disposition": "actionable"}]
        after["findings"] = [{**before["findings"][0], "status": "open"}]
        after["batches"][0]["status"] = "changes-requested"
        report["reopenedBatches"] = [{"id": "B1", "reason": "Regression reproduced by the integration check"}]
        report["reopenedFindings"] = [{"id": "R1", "reason": "New reproduction invalidates the previous fix"}]
        loop.validate_state(after)
        loop.validate_transition(before, after, choice, report)
        self.assertEqual(loop.selection(after)["mode"], "fix")
        del report["reopenedFindings"]
        with self.assertRaisesRegex(loop.Halt, "Unreported finding change"):
            loop.validate_transition(before, after, choice, report)

    def test_early_critical_fix_returns_to_implementation_without_early_review(self):
        before = initial_state()
        before["tasks"][0]["status"] = "implemented"
        before["batches"][0]["status"] = "fixes-awaiting-check"
        before["findings"] = [{"id": "R1", "batch": "B1", "status": "fixed-awaiting-check", "disposition": "actionable"}]
        after = copy.deepcopy(before)
        after["findings"][0]["status"] = "verified"
        after["batches"][0]["status"] = "pending"
        loop.validate_state(after)
        loop.validate_transition(before, after, loop.selection(before), {"findingIds": ["R1"], "startCommit": "fix-code"})
        self.assertEqual(loop.selection(after)["target"], "F2")

    def test_new_critical_finding_during_implementation_is_reported_not_suppressed(self):
        before, after, choice, report = self.fixture()
        after["findings"] = [{"id": "R2", "batch": "B2", "status": "open", "disposition": "actionable"}]
        after["batches"][-1]["status"] = "changes-requested"
        report["newFindingIds"] = ["R2"]
        loop.validate_state(after)
        loop.validate_transition(before, after, choice, report)
        del report["newFindingIds"]
        with self.assertRaisesRegex(loop.Halt, "New findings must be explicitly reported"):
            loop.validate_transition(before, after, choice, report)

    def test_fresh_review_and_verification_anchor_the_code_actually_inspected(self):
        before = initial_state()
        for task in before["tasks"]:
            task["status"] = "implemented"
        before["batches"][0]["status"] = "ready-for-review"
        after = copy.deepcopy(before)
        after["batches"][0].update(status="reviewed", reviewedCommit="arbitrary-ancestor")
        with self.assertRaisesRegex(loop.Halt, "Review must anchor"):
            loop.validate_transition(before, after, loop.selection(before), {"startCommit": "inspected-code"})


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

    def seed_findings(self, count=1):
        state = self.fixture.state()
        for task in state["tasks"]:
            task["status"] = "implemented"
        state["batches"][0]["status"] = "changes-requested"
        state["findings"] = [{"id": f"R{i+1}", "batch": "B1", "status": "open",
                              "disposition": "actionable", "diagnosis": "initial diagnosis"}
                             for i in range(count)]
        self.fixture.write(loop.STATE, state)
        self.fixture.commit()

    def test_bounded_subsets_do_not_count_untouched_findings(self):
        self.seed_findings(4)
        runner, client = self.runner()
        client.finding_limit = 1
        self.assertEqual(runner.run(10, 30)["status"], "complete")
        self.assertEqual(client.calls, ["fix", "verify"] * 4 + ["complete"])
        self.assertEqual(loop.load(runner.journal / "fix-attempts.json"), {})

    def test_failed_verification_cycles_stop_and_retain_attempts(self):
        self.seed_findings()
        runner, client = self.runner("failed-verification")
        with self.assertRaisesRegex(loop.Halt, "Repeated unresolved fix"):
            runner.run(10, 30)
        self.assertEqual(client.calls, ["fix", "verify", "fix", "verify"])
        self.assertEqual(loop.load(runner.journal / "fix-attempts.json")["R1"]["count"], 2)
        self.assertTrue((runner.journal / "active.json").exists())

    def test_evidenced_new_diagnosis_allows_a_new_attempt(self):
        self.seed_findings()
        runner, client = self.runner("failed-verification")
        def new_diagnosis(context, state, report):
            if len(client.calls) == 4:
                state["findings"][0].update(diagnosis="different evidenced root cause",
                                            diagnosisEvidence=report["checks"][0]["evidence"])
        client.transform = new_diagnosis
        self.assertEqual(runner.run(4, 30)["status"], "paused")
        self.assertEqual(loop.load(runner.journal / "fix-attempts.json")["R1"]["count"], 0)
        client.fault = None
        client.transform = None
        self.assertEqual(runner.run(3, 30)["status"], "complete")

    def test_cosmetic_or_unsupported_diagnosis_changes_do_not_reset_attempts(self):
        for supported in (False, True):
            with self.subTest(whitespace_only=supported):
                self.seed_findings()
                runner, client = self.runner("failed-verification")
                def change(context, state, report):
                    if len(client.calls) == 4:
                        state["findings"][0]["diagnosis"] = (
                            " initial  diagnosis\n" if supported else "unsupported new assertion")
                client.transform = change
                with self.assertRaisesRegex(loop.Halt, "Repeated unresolved fix|Diagnosis change needs"):
                    runner.run(4, 30)
                # This subtest owns only its disposable journal; use a new fixture next time.
                if not supported:
                    self.fixture.close()
                    self.fixture = GitFixture()

    def test_implement_cannot_review_or_complete_another_task(self):
        for fault in ("review", "other-task"):
            with self.subTest(fault=fault):
                runner, client = self.runner()
                def escape(context, state, report):
                    if fault == "review":
                        for task in state["tasks"]:
                            task["status"] = "implemented"
                        state["batches"][0].update(status="reviewed", reviewedCommit=context["startCommit"])
                    else:
                        state["tasks"][1]["status"] = "implemented"
                        state["batches"][0]["status"] = "ready-for-review"
                client.transform = escape
                with self.assertRaises(loop.Halt):
                    runner.run(1, 30)
                self.assertTrue((runner.journal / "active.json").exists())
                if fault == "review":
                    self.fixture.close()
                    self.fixture = GitFixture()

    def test_fix_cannot_verify_itself(self):
        self.seed_findings()
        runner, client = self.runner()
        def self_verify(context, state, report):
            state["findings"][0]["status"] = "verified"
            state["batches"][0].update(status="reviewed", reviewedCommit=context["startCommit"])
        client.transform = self_verify
        with self.assertRaisesRegex(loop.Halt, "Fix cannot verify/close"):
            runner.run(1, 30)
        self.assertEqual(client.calls, ["fix"])

    def test_report_subset_cannot_change_an_unreported_finding(self):
        self.seed_findings(2)
        runner, client = self.runner()
        client.transform = lambda context, state, report: report.update(findingIds=["R1"])
        with self.assertRaisesRegex(loop.Halt, "Unreported finding change"):
            runner.run(1, 30)

    def test_planning_only_split_gets_fresh_child_units_and_one_group_review(self):
        runner, client = self.runner(issue=False)
        def split(context, state, report):
            state["tasks"][0].update(status="in-progress", children=["F1a", "F1b"])
            state["tasks"] += [{"id": name, "batch": "B1", "status": "pending"}
                                for name in ("F1a", "F1b")]
        client.transform = split
        runner.run(1, 30)
        client.transform = None
        self.assertEqual(runner.run(10, 30)["status"], "complete")
        reports = [loop.load(self.fixture.root / path) for path in self.fixture.state()["runs"]]
        self.assertEqual([r["target"] for r in reports], ["F1", "F1a", "F1b", "F1", "F2", "B1", "completion"])

    def test_explicit_independent_lane_around_selected_blocker_is_allowed(self):
        runner, client = self.runner()
        def independent(context, state, report):
            state["tasks"][0]["status"] = "blocked"
            state["tasks"][1].update(dependsOn=[], independent=True)
            report["taskAdjustments"] = [{"id": "F2", "reason": "Independent approved work around F1 blocker"}]
        client.transform = independent
        self.assertEqual(runner.run(1, 30)["status"], "paused")
        self.assertEqual(loop.selection(self.fixture.state())["target"], "F2")

    def test_fix_cannot_demote_a_functional_defect_to_polish(self):
        self.seed_findings()
        runner, client = self.runner()
        def demote(context, state, report):
            state["findings"][0].update(status="open", disposition="nonblocking-polish")
            state["batches"][0].update(status="reviewed", reviewedCommit=context["startCommit"])
        client.transform = demote
        with self.assertRaisesRegex(loop.Halt, "Fix cannot verify/close"):
            runner.run(1, 30)

    def test_second_fix_can_succeed_before_the_retry_guard_stops(self):
        self.seed_findings()
        runner, client = self.runner("failed-verification")
        runner.run(2, 30)
        client.fault = None
        self.assertEqual(runner.run(3, 30)["status"], "complete")
        self.assertEqual(client.calls, ["fix", "verify", "fix", "verify", "complete"])

    def test_verifier_can_withdraw_a_reproduced_false_finding_with_rationale(self):
        self.seed_findings()
        runner, client = self.runner()
        runner.run(1, 30)
        def withdraw(context, state, report):
            if context["selection"]["mode"] == "verify":
                state["findings"][0].update(status="withdrawn", disposition="not-a-defect",
                                            rationale="The retained diagnostic establishes the approved outcome")
        client.transform = withdraw
        self.assertEqual(runner.run(2, 30)["status"], "complete")
        self.assertEqual(loop.load(runner.journal / "fix-attempts.json"), {})

    def test_ignored_evidence_is_rejected(self):
        runner, client = self.runner()
        def ignore(context, state, report):
            (self.fixture.root / ".git/info/exclude").write_text("*-checks.log\n")
            report["changedPaths"].remove(report["checks"][0]["evidence"])
        client.transform = ignore
        with self.assertRaisesRegex(loop.Halt, "Check evidence is not committed byte-for-byte"):
            runner.run(1, 30)

    def test_evidence_survives_clean_clone_and_byte_changes_are_detected(self):
        runner, client = self.runner(issue=False)
        runner.run(10, 30)
        clone = self.fixture.base / "clean-clone"
        loop.run_command(["git", "clone", "--branch", "main", str(self.fixture.remote), str(clone)], self.fixture.root)
        state = loop.load(clone / loop.STATE)
        for path in state["runs"]:
            report = loop.load(clone / path)
            for check in report["checks"]:
                self.assertEqual((clone / check["evidence"]).read_bytes(),
                                 (self.fixture.root / check["evidence"]).read_bytes())
        evidence = report["checks"][0]["evidence"]
        (self.fixture.root / evidence).write_bytes(b"changed output\x00\n")
        with self.assertRaisesRegex(loop.Halt, "Check evidence is not committed byte-for-byte"):
            runner.committed_file(evidence, self.fixture.git("rev-parse", "HEAD"))

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

    def test_wrong_worktree_cannot_clear_original_dirty_run(self):
        other = self.fixture.base / "other"
        self.fixture.git("worktree", "add", "-b", "other", str(other))
        loop.run_command(["git", "config", "branch.other.remote", "origin"], other)
        loop.run_command(["git", "config", "branch.other.merge", "refs/heads/main"], other)
        runner, client = self.runner("dirty-after")
        with self.assertRaises(loop.Halt):
            runner.run(1, 30)
        before = self.fixture.git("status", "--short")
        with self.assertRaisesRegex(loop.Halt, "Recovery ownership mismatch"):
            loop.Runner(other, client=client).recover()
        self.assertEqual(self.fixture.git("status", "--short"), before)
        self.assertTrue((runner.journal / "active.json").exists())

    def test_wrong_state_branch_and_legacy_record_cannot_clear_fence(self):
        runner, client = self.runner("missing-report")
        with self.assertRaises(loop.Halt):
            runner.run(1, 30)
        with self.assertRaisesRegex(loop.Halt, "Recovery ownership mismatch"):
            loop.Runner(self.fixture.root, "different-state.json", client=client).recover()
        self.fixture.git("switch", "-c", "different-branch")
        with self.assertRaisesRegex(loop.Halt, "Recovery ownership mismatch"):
            runner.recover()
        self.fixture.git("switch", "main")
        active = runner.journal / "active.json"
        record = loop.load(active)
        record.pop("worktree")
        loop.atomic_json(active, record)
        with self.assertRaisesRegex(loop.Halt, "Recovery ownership mismatch"):
            runner.recover()
        self.assertTrue(active.exists())

    def test_recovery_accepts_equivalent_state_path_spelling(self):
        runner, client = self.runner("missing-report")
        with self.assertRaises(loop.Halt):
            runner.run(1, 30)
        equivalent = "./docs/agent/../agent/implementation-state.json"
        self.assertEqual(loop.Runner(self.fixture.root, equivalent, client=client).recover()["status"], "recovered")

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
