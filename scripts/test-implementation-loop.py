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
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True  # Qualification must not dirty the checkout with import caches.
spec = importlib.util.spec_from_file_location("implementation_loop", ROOT / "scripts/implementation-loop.py")
loop = importlib.util.module_from_spec(spec)
spec.loader.exec_module(loop)


def initial_state():
    return {
        "formatVersion": 1, "mode": "active", "authorization": "authorized",
        "authorizationRecord": "Owner-authorized disposable runner qualification only",
        "runner": {"model": loop.MODEL, "permissions": "docs/agent/implementation-permissions.json",
                   "reviewTestPaths": ["tests/**"]},
        "activeRun": None, "runs": [], "findings": [], "externalReleaseGates": [],
        "batches": [{"id": "B1", "status": "pending", "reviewedCommit": None}],
        "tasks": [{"id": "F1", "batch": "B1", "status": "pending"},
                  {"id": "F2", "batch": "B1", "status": "pending"}],
    }


def review_record(commit=None):
    return {"units": [{"id": "capability", "scope": "Full capability and integration"}],
            "completed": {"capability": commit} if commit else {}, "blocked": {}}


def reviewed(batch, commit):
    batch.update(status="reviewed", reviewedCommit=commit, review=review_record(commit))


def review_leaves(units):
    return [i for u in units for i in (review_leaves(u["children"]) if u.get("children") else [u["id"]])]


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
        shutil.copy2(ROOT / "scripts/implementation-loop.py", self.root / "scripts/implementation-loop.py")
        shutil.copy2(ROOT / "scripts/test-implementation-loop.py", self.root / "scripts/test-implementation-loop.py")
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
        self.review_limit = None

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
        reviewed_ids = []
        if self.fault != "no-progress":
            if mode == "implement":
                task = next(t for t in state["tasks"] if t["id"] == choice["target"])
                task["status"] = "blocked" if self.fault == "blocked" else "implemented"
                if all(t["status"] == "implemented" for t in state["tasks"]):
                    state["batches"][0]["status"] = "ready-for-review"
            elif mode == "review":
                batch = next(b for b in state["batches"] if b["id"] == choice["target"])
                review = batch.setdefault("review", review_record())
                pending = [i for i in review_leaves(review["units"]) if i not in review["completed"] and
                           i not in review.get("blocked", {})]
                reviewed_ids = pending[:self.review_limit]
                review["completed"].update({i: context["startCommit"] for i in reviewed_ids})
                if self.issue and not any(f["id"] == "R1" for f in state["findings"]):
                    state["findings"].append({"id": "R1", "batch": "B1", "status": "open",
                                                "disposition": "actionable"})
                    finding_ids = ["R1"]
                    batch["status"] = "changes-requested"
                else:
                    batch["status"] = "ready-for-review" if set(review_leaves(review["units"])) - review["completed"].keys() else "reviewed"
                    if batch["status"] == "reviewed":
                        batch["reviewedCommit"] = context["startCommit"]
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
                                   else "reviewed" if set(review_leaves(batch.get("review", review_record())["units"])) <=
                                   batch.get("review", review_record())["completed"].keys()
                                   else "ready-for-review" if all(t["status"] == "implemented" for t in state["tasks"]
                                                                if t["batch"] == batch["id"]) else "pending")
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
        paths = [loop.STATE, context["report"], evidence]
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
            "reviewedUnitIds": reviewed_ids,
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
        if mode in {"implement", "fix"} and not (mode == "implement" and next(
                t for t in state["tasks"] if t["id"] == choice["target"])["status"] == "in-progress"):
            paths.append("probe.txt")
            self.fixture.write("probe.txt", f"{mode} {choice['target']} {len(state['runs'])}\n")
        if mode == "implement":
            task = next(t for t in state["tasks"] if t["id"] == choice["target"])
            if task["status"] == "blocked":
                task.update(blocker="Controlled missing fixture resource", blockerEvidence=evidence)
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
    def delivery_first_state(self):
        state = initial_state()
        state['batches'].append({'id': 'B8', 'status': 'pending', 'reviewedCommit': None})
        state['tasks'] = [
            {'id': 'I01', 'batch': 'B1', 'status': 'implemented', 'children': ['I01f']},
            {'id': 'I01f', 'batch': 'B1', 'status': 'implemented'},
            {'id': 'I02', 'batch': 'B1', 'status': 'pending'},
            {'id': 'I01f3', 'batch': 'B8', 'status': 'pending', 'dependsOn': ['I01f']},
            {'id': 'I34', 'batch': 'B8', 'status': 'pending', 'dependsOn': ['I01f3']},
        ]
        return state

    def test_delivery_first_deferred_matrix_does_not_block_account_or_group_review(self):
        state = self.delivery_first_state()
        self.assertEqual(loop.selection(state)['target'], 'I02')
        state['tasks'][2]['status'] = 'implemented'
        self.assertEqual(loop.selection(state), {'mode': 'review', 'target': 'B1', 'findingIds': []})
        self.assertEqual(state['tasks'][3]['status'], 'pending')

    def test_delivery_first_final_suite_still_requires_deferred_matrix(self):
        state = self.delivery_first_state()
        state['tasks'][2]['status'] = 'implemented'
        reviewed(state['batches'][0], 'reviewed-feature-code')
        self.assertEqual(loop.selection(state)['target'], 'I01f3')
        state['tasks'][3]['status'] = 'implemented'
        self.assertEqual(loop.selection(state)['target'], 'I34')

    def test_delivery_first_blocked_matrix_cannot_be_skipped_for_final_completion(self):
        state = self.delivery_first_state()
        state['tasks'][2]['status'] = 'implemented'
        reviewed(state['batches'][0], 'reviewed-feature-code')
        state['tasks'][3]['status'] = 'blocked'
        self.assertEqual(loop.selection(state)['mode'], 'blocked')

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
        reviewed(state["batches"][0], "older-code-commit")
        self.assertEqual(loop.selection(state)["mode"], "complete")

    def test_release_gate_not_fake_completion(self):
        state = initial_state()
        for task in state["tasks"]:
            task["status"] = "implemented"
        reviewed(state["batches"][0], "code")
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

    def test_independent_child_is_not_hidden_by_blocked_sibling(self):
        state = initial_state()
        state["tasks"][0].update(status="in-progress", children=["F1a", "F1b"])
        state["tasks"] += [{"id": "F1a", "batch": "B1", "status": "blocked"},
                           {"id": "F1b", "batch": "B1", "status": "pending", "independent": True, "dependsOn": []}]
        self.assertEqual(loop.selection(state)["target"], "F1b")
        del state["tasks"][-1]["dependsOn"]
        state["tasks"][-1].pop("independent")
        self.assertEqual(loop.selection(state)["mode"], "blocked")

    def test_independent_child_still_inherits_ancestor_dependencies(self):
        state = initial_state()
        state["tasks"][0].update(status="in-progress", children=["F1a"], dependsOn=["F2"])
        state["tasks"][1]["status"] = "blocked"
        state["tasks"].append({"id": "F1a", "batch": "B1", "status": "pending", "independent": True, "dependsOn": []})
        self.assertEqual(loop.selection(state)["mode"], "blocked")

    def test_independent_completed_batch_gets_due_review(self):
        state = initial_state()
        state["tasks"][0]["status"] = "blocked"
        state["tasks"].append({"id": "F3", "batch": "B2", "status": "implemented", "independent": True, "dependsOn": []})
        state["batches"].append({"id": "B2", "status": "ready-for-review", "reviewedCommit": None})
        self.assertEqual(loop.selection(state), {"mode": "review", "target": "B2", "findingIds": []})

    def test_missing_or_invalid_gate_inventory_refused(self):
        for fault in ("missing", "duplicate", "invalid", "unsupported-pass"):
            state = initial_state()
            state["externalReleaseGates"] = [{"id": "hardware", "status": "unverified"}]
            if fault == "missing":
                del state["externalReleaseGates"]
            elif fault == "duplicate":
                state["externalReleaseGates"] *= 2
            else:
                state["externalReleaseGates"][0]["status"] = "passed" if fault == "unsupported-pass" else "ignore"
            with self.subTest(fault=fault), self.assertRaises(loop.Halt):
                loop.selection(state)

    def test_review_session_edit_permissions_are_narrowed_not_expanded(self):
        base = [{"action": "edit", "resource": "*", "effect": "allow"}]
        rules = loop.session_permissions(base, initial_state(), {"mode": "verify"}, loop.STATE,
                                         loop.REPORTS + "/r.json", "r")
        def effect(path):
            return next(rule["effect"] for rule in reversed(rules) if rule["action"] == "edit" and
                        loop.fnmatch.fnmatchcase(path, rule["resource"]))
        self.assertEqual(effect("arithmetic.py"), "deny")
        self.assertEqual(effect("build.gradle.kts"), "deny")
        self.assertEqual(effect("tests/regression.py"), "allow")
        self.assertEqual(effect(loop.STATE), "allow")
        self.assertEqual(effect(loop.REPORTS + "/r-checks.log"), "allow")
        self.assertEqual(effect(loop.REPORTS + "/previous-checks.log"), "deny")
        self.assertEqual(loop.session_permissions(base, initial_state(), {"mode": "fix"}, loop.STATE,
                                                 loop.REPORTS + "/r.json", "r"), base)

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
        reviewed(before["batches"][0], "previous-review")
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
        after["batches"][0]["review"]["completed"] = {}
        report["reviewInvalidations"] = [{"id": "B1", "unitIds": ["capability"], "reason": "Changed integration"}]
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
        after["batches"][0]["review"] = review_record("inspected-code")
        with self.assertRaisesRegex(loop.Halt, "Review must anchor"):
            loop.validate_transition(before, after, loop.selection(before), {
                "startCommit": "inspected-code", "reviewedUnitIds": ["capability"]})


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
        state["batches"][0]["review"] = review_record(self.fixture.git("rev-parse", "HEAD"))
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
            for task in state["tasks"][-2:]:
                task.update(outcome="Bounded fixture outcome", checks=["fixture check"], dependsOn=[])
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

    def cli_client(self, exit_code=1, *, outcome="succeeded", active=False, model=None):
        executable = self.fixture.base / "fake-opencode"
        executable.write_text("#!/usr/bin/env python3\nimport sys\n"
                              "print('{\"type\":\"error\",\"error\":{\"type\":\"provider.transport\","
                              "\"message\":\"WebSocket closed with code 1012\"}}')\n"
                              f"sys.exit({exit_code})\n")
        executable.chmod(0o755)
        client = loop.OpenCode(self.fixture.root, str(executable))
        client.info = lambda session: {"model": model or loop.MODEL_REF, "outcome": outcome}
        client.is_active = lambda session: active
        calls = []
        client.api = lambda *args, **kwargs: calls.append(args) or {}
        return client, calls

    def test_recovered_provider_error_uses_successful_idle_server_not_sticky_cli_exit(self):
        for code in (0, 1):
            with self.subTest(code=code):
                client, calls = self.cli_client(code)
                log = self.fixture.base / "provider.log"
                info = client.execute("ses_owned", "fixture", log, 30)
                self.assertEqual(info["cliExitCode"], code)
                self.assertEqual(info["outcome"], "succeeded")
                self.assertIn("provider.transport", log.read_text())
                self.assertFalse(calls)

    def test_cli_exit_one_does_not_accept_failed_active_or_wrong_model_session(self):
        for settings in ({"outcome": "failed"}, {"outcome": "interrupted"},
                         {"outcome": None}, {"active": True},
                         {"model": {**loop.MODEL_REF, "variant": "low"}}):
            with self.subTest(settings=settings):
                client, calls = self.cli_client(**settings)
                with self.assertRaises(loop.Halt):
                    client.execute("ses_owned", "fixture", self.fixture.base / "provider.log", 30)
                self.assertIn(("post", "/api/session/ses_owned/interrupt"), calls)

    def test_other_cli_exit_and_signals_still_halt(self):
        client, calls = self.cli_client(2)
        with self.assertRaisesRegex(loop.Halt, "OpenCode exited 2"):
            client.execute("ses_owned", "fixture", self.fixture.base / "provider.log", 30)
        self.assertIn(("post", "/api/session/ses_owned/interrupt"), calls)
        (self.fixture.base / "fake-opencode").write_text(
            "#!/usr/bin/env python3\nimport os,signal\nos.kill(os.getpid(), signal.SIGTERM)\n")
        with self.assertRaisesRegex(loop.Halt, "OpenCode exited -15"):
            client.execute("ses_owned", "fixture", self.fixture.base / "signal.log", 30)

    def test_cli_exit_one_still_requires_durable_report_and_passing_required_checks(self):
        for fault, message in (("missing-report", "durable run report"), ("failed-check", "Required check failed")):
            with self.subTest(fault=fault):
                fixture = GitFixture()
                try:
                    client = FakeOpenCode(fixture, fault)
                    execute = client.execute
                    client.execute = lambda *args: {**execute(*args), "cliExitCode": 1}
                    runner = loop.Runner(fixture.root, client=client)
                    client.runner = runner
                    with self.assertRaisesRegex(loop.Halt, message):
                        runner.run(1, 30)
                    self.assertTrue((runner.journal / "active.json").exists())
                finally:
                    fixture.close()

    def test_last_task_discovery_still_gets_first_group_review(self):
        runner, client = self.runner(issue=False)
        def discover(context, state, report):
            if context["selection"]["mode"] == "implement" and context["selection"]["target"] == "F2":
                state["findings"] = [{"id": "R1", "batch": "B1", "status": "open", "disposition": "actionable"}]
                state["batches"][0]["status"] = "changes-requested"
                report["newFindingIds"] = ["R1"]
        client.transform = discover
        self.assertEqual(runner.run(10, 30)["status"], "complete")
        self.assertEqual(client.calls, ["implement", "implement", "fix", "verify", "review", "complete"])

    def test_verifier_cannot_repair_runtime_code(self):
        self.seed_findings()
        runner, client = self.runner()
        runner.run(1, 30)
        def repair(context, state, report):
            self.fixture.write("arithmetic.py", "print(2 + 3)\n")
            report["changedPaths"].append("arithmetic.py")
        client.transform = repair
        with self.assertRaisesRegex(loop.Halt, "Product changes forbidden"):
            runner.run(1, 30)

    def test_gate_cannot_disappear(self):
        state = self.fixture.state()
        state["externalReleaseGates"] = [{"id": "hardware", "status": "unverified"}]
        self.fixture.write(loop.STATE, state)
        self.fixture.commit()
        runner, client = self.runner()
        client.transform = lambda context, state, report: state.update(externalReleaseGates=[])
        with self.assertRaisesRegex(loop.Halt, "release gate"):
            runner.run(1, 30)

    def test_metadata_only_in_progress_is_not_a_unit(self):
        runner, client = self.runner()
        def churn(context, state, report):
            state["tasks"][0].update(status="in-progress", note="Still planning")
        client.transform = churn
        with self.assertRaisesRegex(loop.Halt, "completed outcome|planning-only split"):
            runner.run(1, 30)

    def checkpoint(self, context, state, report, *, result="Identified SDK metadata mismatch"):
        checkpoint = {"issue": "Build-tool compatibility", "diagnosis": result,
                      "progress": result, "nextSteps": ["Repair compatibility and rerun clean build"],
                      "evidence": report["checks"][0]["evidence"]}
        report["troubleshooting"] = checkpoint
        report["checks"].append({"kind": "diagnostic", "command": "inspect build-tool metadata",
                                 "exitCode": 1, "evidence": checkpoint["evidence"]})
        self.fixture.write(checkpoint["evidence"], result + "\n")
        if context["selection"]["mode"] == "implement":
            state["tasks"][0].update(status="in-progress", troubleshooting={**checkpoint, "record": context["report"]})
        else:
            for finding in state["findings"]:
                finding.update(status="open", troubleshooting={**checkpoint, "record": context["report"]})
            state["batches"][0]["status"] = "changes-requested"

    def test_troubleshooting_continues_in_fresh_session_before_next_task(self):
        runner, client = self.runner(issue=False)
        def diagnose(context, state, report):
            if len(client.calls) == 1:
                self.checkpoint(context, state, report)
        client.transform = diagnose
        self.assertEqual(runner.run(8, 30)["status"], "complete")
        self.assertEqual(client.calls, ["implement", "implement", "implement", "review", "complete"])
        self.assertEqual(len(client.sessions), 5)

    def test_multiple_troubleshooting_sessions_can_make_new_diagnostic_progress(self):
        runner, client = self.runner(issue=False)
        def diagnose(context, state, report):
            if len(client.calls) <= 3:
                self.checkpoint(context, state, report, result=f"Tested distinct hypothesis {len(client.calls)}")
        client.transform = diagnose
        self.assertEqual(runner.run(10, 30)["status"], "complete")
        self.assertEqual(client.calls[:4], ["implement"] * 4)

    def test_troubleshooting_repeated_evidence_is_rejected(self):
        runner, client = self.runner(issue=False)
        client.transform = lambda c, s, r: self.checkpoint(c, s, r)
        runner.run(1, 30)
        with self.assertRaisesRegex(loop.Halt, "Repeated troubleshooting"):
            runner.run(1, 30)

    def test_troubleshooting_cosmetic_diagnosis_cannot_replay_same_output(self):
        runner, client = self.runner(issue=False)
        def diagnose(context, state, report):
            self.checkpoint(context, state, report, result=f"Hypothesis {len(client.calls)}")
            self.fixture.write(report["troubleshooting"]["evidence"], "unchanged output\n")
        client.transform = diagnose
        runner.run(1, 30)
        with self.assertRaisesRegex(loop.Halt, "replay unchanged evidence"):
            runner.run(1, 30)

    def test_troubleshooting_needs_concrete_evidence_and_next_steps(self):
        runner, client = self.runner(issue=False)
        def incomplete(context, state, report):
            self.checkpoint(context, state, report)
            report["troubleshooting"]["nextSteps"] = []
        client.transform = incomplete
        with self.assertRaisesRegex(loop.Halt, "Troubleshooting needs"):
            runner.run(1, 30)

    def test_fix_troubleshooting_does_not_consume_product_fix_attempts(self):
        self.seed_findings()
        runner, client = self.runner()
        def diagnose(context, state, report):
            if len(client.calls) <= 3:
                self.checkpoint(context, state, report, result=f"Tooling repair hypothesis {len(client.calls)}")
        client.transform = diagnose
        runner.run(3, 30)
        self.assertEqual(json.loads((runner.journal / "fix-attempts.json").read_text()), {})
        client.transform = None
        runner.run(1, 30)
        self.assertEqual(loop.selection(self.fixture.state())["mode"], "verify")

    def test_troubleshooting_cannot_claim_task_complete(self):
        runner, client = self.runner(issue=False)
        def finish(context, state, report):
            self.checkpoint(context, state, report)
            state["tasks"][0]["status"] = "implemented"
        client.transform = finish
        with self.assertRaisesRegex(loop.Halt, "retain the selected task in progress"):
            runner.run(1, 30)

    def test_orchestration_repair_needs_reason_paths_and_passing_check(self):
        runner, client = self.runner(issue=False)
        def tamper(context, state, report):
            path = loop.PROMPT
            self.fixture.write(path, "Changed instructions without repair provenance\n")
            report["changedPaths"].append(path)
        client.transform = tamper
        with self.assertRaisesRegex(loop.Halt, "evidenced scoped orchestration repair"):
            runner.run(1, 30)

    def test_orchestration_prompt_repair_runs_baseline_regressions(self):
        runner, client = self.runner(issue=False)
        def repair(context, state, report):
            self.fixture.write(loop.PROMPT, "Scoped fixture orchestration correction\n")
            report["changedPaths"].append(loop.PROMPT)
            report["orchestrationRepair"] = {"reason": "Reproduced fixture instruction defect",
                                             "paths": [loop.PROMPT], "evidence": report["checks"][0]["evidence"]}
        client.transform = repair
        original = loop.run_command
        checks = []
        def checked(command, root, **kwargs):
            if command == [sys.executable, "scripts/test-implementation-loop.py"]:
                checks.append(command)
                return "Controlled passing regression execution"
            return original(command, root, **kwargs)
        with patch.object(loop, "run_command", side_effect=checked):
            runner.run(1, 30)
        self.assertEqual(len(checks), 1)

    def test_orchestration_repair_cannot_expand_permissions(self):
        runner, client = self.runner(issue=False)
        def expand(context, state, report):
            path = state["runner"]["permissions"]
            self.fixture.write(path, [{"action": "*", "resource": "*", "effect": "allow"}])
            report["changedPaths"].append(path)
            report["orchestrationRepair"] = {"reason": "Not an authorized grant", "paths": [path],
                                             "evidence": report["checks"][0]["evidence"]}
        client.transform = expand
        with self.assertRaisesRegex(loop.Halt, "evidenced scoped orchestration repair"):
            runner.run(1, 30)

    def test_saved_driver_repair_reexecs_only_remaining_budget(self):
        runner, client = self.runner(issue=False)
        path = "scripts/implementation-loop.py"
        def repair(context, state, report):
            with (self.fixture.root / path).open("a") as stream:
                stream.write("\n# Controlled fixture driver correction\n")
            report["changedPaths"].append(path)
            report["orchestrationRepair"] = {"reason": "Fixture driver correction", "paths": [path],
                                             "evidence": report["checks"][0]["evidence"]}
        client.transform = repair
        original = loop.run_command
        def checked(command, root, **kwargs):
            if command == [sys.executable, "scripts/test-implementation-loop.py"]:
                return "Controlled passing regression execution"
            return original(command, root, **kwargs)
        class Replaced(Exception):
            pass
        with patch.object(loop, "run_command", side_effect=checked), patch.object(loop.os, "execv", side_effect=Replaced) as execute:
            with self.assertRaises(Replaced):
                runner.run(5, 30)
        arguments = execute.call_args.args[1]
        self.assertEqual(arguments[-4:], ["--max-runs", "4", "--timeout-seconds", "30"])
        self.assertFalse((runner.journal / "active.json").exists())
        self.assertEqual(self.fixture.git("status", "--porcelain"), "")
        self.assertEqual(self.fixture.git("rev-parse", "HEAD"), self.fixture.git("rev-parse", "origin/main"))

    def test_group_review_can_checkpoint_and_resume(self):
        state = self.fixture.state()
        for task in state["tasks"]:
            task["status"] = "implemented"
        state["batches"][0].update(status="ready-for-review", review={
            "units": [{"id": "entry", "scope": "Normal workflow"}, {"id": "recovery", "scope": "Recovery integration"}],
            "completed": {}, "blocked": {}})
        self.fixture.write(loop.STATE, state)
        self.fixture.commit()
        runner, client = self.runner(issue=False)
        client.review_limit = 1
        self.assertEqual(runner.run(1, 30)["status"], "paused")
        self.assertEqual(self.fixture.state()["batches"][0]["status"], "ready-for-review")
        self.assertEqual(runner.run(3, 30)["status"], "complete")
        self.assertEqual(client.calls, ["review", "review", "complete"])

    def test_blocked_selection_can_save_a_new_independent_lane(self):
        state = self.fixture.state()
        state["tasks"][0]["status"] = "blocked"
        self.fixture.write(loop.STATE, state)
        self.fixture.commit()
        runner, client = self.runner(issue=False)
        def lane(context, state, report):
            state["tasks"][1].update(dependsOn=[], independent=True)
            report["taskAdjustments"] = [{"id": "F2", "reason": "Independent approved work"}]
        client.transform = lane
        self.assertEqual(runner.run(1, 30)["status"], "paused")
        self.assertEqual(loop.selection(self.fixture.state())["target"], "F2")

    def seed_review(self, units=None):
        state = self.fixture.state()
        for task in state["tasks"]:
            task["status"] = "implemented"
        state["batches"][0].update(status="ready-for-review", review=review_record())
        if units:
            state["batches"][0]["review"]["units"] = units
        self.fixture.write(loop.STATE, state)
        self.fixture.commit()

    def test_review_cannot_repair_runtime_or_build_inputs(self):
        self.seed_review()
        runner, client = self.runner(issue=False)
        def repair(context, state, report):
            self.fixture.write("build.gradle.kts", "// not a reviewer-owned input\n")
            report["changedPaths"].append("build.gradle.kts")
        client.transform = repair
        with self.assertRaisesRegex(loop.Halt, "Product changes forbidden"):
            runner.run(1, 30)

    def test_verifier_may_add_focused_test_without_runtime_change(self):
        self.seed_findings()
        runner, client = self.runner()
        runner.run(1, 30)
        def regression(context, state, report):
            self.fixture.write("tests/regression.py", "assert 2 + 3 == 5\n")
            report["changedPaths"].append("tests/regression.py")
        client.transform = regression
        self.assertEqual(runner.run(1, 30)["status"], "paused")
        self.assertEqual(self.fixture.state()["findings"][0]["status"], "verified")

    def test_runtime_file_cannot_masquerade_as_check_output(self):
        self.seed_review()
        runner, client = self.runner(issue=False)
        def masquerade(context, state, report):
            self.fixture.write("runtime.py", "print(5)\n")
            report["checks"].append({"kind": "required", "command": "fake evidence", "exitCode": 0, "evidence": "runtime.py"})
            report["changedPaths"].append("runtime.py")
        client.transform = masquerade
        with self.assertRaisesRegex(loop.Halt, "evidence must be owned"):
            runner.run(1, 30)

    def test_task_owned_gate_needs_explicit_retained_evidence(self):
        state = self.fixture.state()
        state["externalReleaseGates"] = [{"id": "measurement", "status": "unverified", "authority": "task", "task": "F1"}]
        self.fixture.write(loop.STATE, state)
        self.fixture.commit()
        runner, client = self.runner()
        def measured(context, state, report):
            evidence = report["checks"][0]["evidence"]
            state["externalReleaseGates"][0].update(status="passed", evidence=evidence, record=context["report"])
            report["gateUpdates"] = [{"id": "measurement", "evidence": evidence}]
        client.transform = measured
        self.assertEqual(runner.run(1, 30)["status"], "paused")
        self.assertEqual(self.fixture.state()["externalReleaseGates"][0]["status"], "passed")

    def test_operator_gate_cannot_be_waived_even_with_report(self):
        state = self.fixture.state()
        state["externalReleaseGates"] = [{"id": "rights", "status": "unverified", "authority": "operator"}]
        self.fixture.write(loop.STATE, state)
        self.fixture.commit()
        runner, client = self.runner()
        def waive(context, state, report):
            evidence = report["checks"][0]["evidence"]
            state["externalReleaseGates"][0].update(status="not-applicable", record=context["report"])
            report["gateUpdates"] = [{"id": "rights", "evidence": evidence}]
        client.transform = waive
        with self.assertRaisesRegex(loop.Halt, "operator/task authority"):
            runner.run(1, 30)

    def test_unreported_gate_pass_is_refused(self):
        state = self.fixture.state()
        state["externalReleaseGates"] = [{"id": "measurement", "status": "unverified", "authority": "task", "task": "F1"}]
        self.fixture.write(loop.STATE, state)
        self.fixture.commit()
        runner, client = self.runner()
        client.transform = lambda context, state, report: state["externalReleaseGates"][0].update(status="passed", record=context["report"])
        with self.assertRaisesRegex(loop.Halt, "Unreported release gate change"):
            runner.run(1, 30)

    def test_review_plan_can_split_without_narrowing_parent_scope(self):
        self.seed_review()
        runner, client = self.runner(issue=False)
        def split(context, state, report):
            batch = state["batches"][0]
            batch.update(status="ready-for-review", reviewedCommit=None)
            batch["review"]["completed"] = {}
            batch["review"]["units"][0]["children"] = [
                {"id": "workflow", "scope": "Workflow and usability"},
                {"id": "recovery", "scope": "Privacy/recovery/integration"}]
            report["reviewedUnitIds"] = []
        client.transform = split
        self.assertEqual(runner.run(1, 30)["status"], "paused")
        client.transform = None
        client.review_limit = 1
        self.assertEqual(runner.run(3, 30)["status"], "complete")
        self.assertEqual(client.calls, ["review", "review", "review", "complete"])

    def test_review_cannot_remove_remaining_scope(self):
        self.seed_review([{"id": "workflow", "scope": "Workflow"}, {"id": "recovery", "scope": "Recovery"}])
        runner, client = self.runner(issue=False)
        def omit(context, state, report):
            review = state["batches"][0]["review"]
            review["units"].pop()
            review["completed"].pop("recovery")
            report["reviewedUnitIds"] = ["workflow"]
        client.transform = omit
        with self.assertRaisesRegex(loop.Halt, "removed review obligations"):
            runner.run(1, 30)

    def test_review_checkpoint_with_findings_resumes_after_fresh_fix_verification(self):
        self.seed_review([{"id": "workflow", "scope": "Workflow"}, {"id": "recovery", "scope": "Recovery"}])
        runner, client = self.runner()
        client.review_limit = 1
        self.assertEqual(runner.run(10, 30)["status"], "complete")
        self.assertEqual(client.calls, ["review", "fix", "verify", "review", "complete"])

    def test_blocked_review_scope_is_preserved_and_can_be_unblocked(self):
        self.seed_review([{"id": "workflow", "scope": "Workflow"}, {"id": "recovery", "scope": "Recovery"}])
        runner, client = self.runner(issue=False)
        def unavailable(context, state, report):
            batch = state["batches"][0]
            batch.update(status="ready-for-review", reviewedCommit=None)
            batch["review"]["completed"].pop("recovery")
            batch["review"]["blocked"] = {"recovery": {"reason": "Device unavailable", "evidence": report["checks"][0]["evidence"]}}
            report.update(reviewedUnitIds=["workflow"], blockedReviewUnitIds=["recovery"])
        client.transform = unavailable
        runner.run(1, 30)
        self.assertEqual(loop.selection(self.fixture.state())["mode"], "blocked")
        def available(context, state, report):
            state["batches"][0]["review"]["blocked"] = {}
            report["reviewUnblocks"] = [{"id": "B1", "unitIds": ["recovery"], "reason": "Device availability checked",
                                        "evidence": report["checks"][0]["evidence"]}]
        client.transform = available
        self.assertEqual(runner.run(1, 30)["status"], "paused")
        client.transform = None
        self.assertEqual(runner.run(3, 30)["status"], "complete")

    def test_review_coverage_change_needs_explicit_scoped_invalidation(self):
        self.seed_review([{"id": "workflow", "scope": "Workflow"}, {"id": "recovery", "scope": "Recovery"}])
        runner, client = self.runner()
        client.review_limit = 1
        runner.run(1, 30)
        def invalidate(context, state, report):
            state["batches"][0]["review"]["completed"] = {}
            report["reviewInvalidations"] = [{"id": "B1", "unitIds": ["workflow"], "reason": "Repair changes the reviewed workflow"}]
        client.transform = invalidate
        runner.run(1, 30)
        client.transform = None
        self.assertEqual(runner.run(10, 30)["status"], "complete")
        self.assertEqual(client.calls, ["review", "fix", "verify", "review", "review", "complete"])

    def test_review_split_cannot_silently_narrow_the_parent(self):
        self.seed_review()
        runner, client = self.runner(issue=False)
        def narrow(context, state, report):
            batch = state["batches"][0]
            batch.update(status="ready-for-review", reviewedCommit=None)
            batch["review"]["completed"] = {}
            batch["review"]["units"][0].update(scope="Only cosmetic checks", children=[{"id": "small", "scope": "One icon"}])
            report["reviewedUnitIds"] = []
        client.transform = narrow
        with self.assertRaisesRegex(loop.Halt, "preserve pending parent scope"):
            runner.run(1, 30)

    def test_planning_split_cannot_include_runtime_implementation(self):
        runner, client = self.runner(issue=False)
        def mixed(context, state, report):
            state["tasks"][0].update(status="in-progress", children=["F1a"])
            state["tasks"].append({"id": "F1a", "batch": "B1", "status": "pending", "dependsOn": [],
                                   "outcome": "Concrete fixture child", "checks": ["fixture check"]})
            self.fixture.write("arithmetic.py", "print(5)\n")
            report["changedPaths"].append("arithmetic.py")
        client.transform = mixed
        with self.assertRaisesRegex(loop.Halt, "Product changes forbidden"):
            runner.run(1, 30)

    def planning_with_support(self, context, state, report):
        if context["selection"]["target"] != "F1" or state["tasks"][0].get("children"):
            return
        state["tasks"][0].update(status="in-progress", children=["F1a"])
        state["tasks"].append({"id": "F1a", "batch": "B1", "status": "pending", "dependsOn": [],
                               "outcome": "Concrete fixture child", "checks": ["fixture check"]})
        path = context["report"].replace(".json", "-planning-check.py")
        self.fixture.write(path, "# Read-only planning validation, not application source\nassert 2 + 3 == 5\n")
        report["checks"][0]["supportPaths"] = [path]
        report["changedPaths"].append(path)

    def test_planning_check_helper_does_not_halt_fresh_child_continuation(self):
        runner, client = self.runner(issue=False)
        client.transform = self.planning_with_support
        self.assertEqual(runner.run(10, 30)["status"], "complete")
        self.assertEqual(client.calls, ["implement"] * 4 + ["review", "complete"])
        self.assertFalse((runner.journal / "active.json").exists())

    def test_planning_unlisted_helper_is_still_rejected(self):
        runner, client = self.runner(issue=False)
        def unlisted(context, state, report):
            self.planning_with_support(context, state, report)
            report["checks"][0].pop("supportPaths")
        client.transform = unlisted
        with self.assertRaisesRegex(loop.Halt, "Product changes forbidden"):
            runner.run(1, 30)

    def test_check_support_cannot_whitelist_runtime_or_old_report(self):
        for path in ["runtime.py", "build.gradle.kts", loop.PROMPT,
                     f"{loop.REPORTS}/older-run-check.py",
                     f"{loop.REPORTS}/current-checks/../../../runtime.py"]:
            with self.subTest(path=path):
                runner, client = self.runner(issue=False)
                report = {"runId": "current", "checks": [{"supportPaths": [path]}]}
                with self.assertRaisesRegex(loop.Halt, "Check support must be owned"):
                    runner.check_support_paths(report)

    def test_check_support_schema_and_missing_file_are_rejected(self):
        runner, client = self.runner(issue=False)
        path = f"{loop.REPORTS}/current-check.py"
        for support in [path, [None], [path, path]]:
            with self.subTest(support=support):
                with self.assertRaisesRegex(loop.Halt, "unique path list"):
                    runner.check_support_paths({"runId": "current", "checks": [{"supportPaths": support}]})
        with self.assertRaisesRegex(loop.Halt, "Missing check support"):
            runner.check_support_paths({"runId": "current", "checks": [{"supportPaths": [path]}]})

    def test_check_support_symlink_cannot_escape_run_namespace(self):
        runner, client = self.runner(issue=False)
        path = f"{loop.REPORTS}/current-check.py"
        destination = self.fixture.root / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.symlink_to(self.fixture.root / "probe.txt")
        with self.assertRaisesRegex(loop.Halt, "Check support must be owned"):
            runner.check_support_paths({"runId": "current", "checks": [{"supportPaths": [path]}]})

    def test_check_support_must_be_committed_byte_for_byte(self):
        runner, client = self.runner(issue=False)
        client.transform = self.planning_with_support
        execute = client.execute
        def tamper(*args):
            result = execute(*args)
            helper = next((self.fixture.root / loop.REPORTS).glob("*-planning-check.py"))
            helper.write_text("# Unsaved check helper change\n")
            return result
        client.execute = tamper
        with self.assertRaisesRegex(loop.Halt, "not committed byte-for-byte"):
            runner.run(1, 30)
        self.assertTrue((runner.journal / "active.json").exists())

    def test_gate_pass_by_wrong_task_is_refused(self):
        state = self.fixture.state()
        state["externalReleaseGates"] = [{"id": "measurement", "status": "unverified", "authority": "task", "task": "F2"}]
        self.fixture.write(loop.STATE, state)
        self.fixture.commit()
        runner, client = self.runner()
        def wrong_task(context, state, report):
            evidence = report["checks"][0]["evidence"]
            state["externalReleaseGates"][0].update(status="passed", record=context["report"], evidence=evidence)
            report["gateUpdates"] = [{"id": "measurement", "evidence": evidence}]
        client.transform = wrong_task
        with self.assertRaisesRegex(loop.Halt, "operator/task authority"):
            runner.run(1, 30)

    def test_blocked_mode_cannot_spin_on_metadata(self):
        state = self.fixture.state()
        state["tasks"][0]["status"] = "blocked"
        self.fixture.write(loop.STATE, state)
        self.fixture.commit()
        runner, client = self.runner()
        def churn(context, state, report):
            state["tasks"][0]["note"] = "Still blocked"
            report["taskAdjustments"] = [{"id": "F1", "reason": "No new runnable work"}]
        client.transform = churn
        with self.assertRaisesRegex(loop.Halt, "establish evidenced runnable"):
            runner.run(1, 30)


if __name__ == "__main__":
    unittest.main(verbosity=2)
