"""Focused evidence tooling qualification; disposable Git only, no product/model calls."""
import base64
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("evidence", ROOT / "scripts/implementation-evidence.py")
evidence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evidence)
fixture_spec = importlib.util.spec_from_file_location("runner_tests", ROOT / "scripts/test-implementation-loop.py")
fixtures = importlib.util.module_from_spec(fixture_spec)
fixture_spec.loader.exec_module(fixtures)


class EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.GitFixture()
        self.root = self.fixture.root
        self.report = evidence.loop.REPORTS + "/fixture.json"
        self.output = evidence.loop.REPORTS + "/fixture/check.json"
        self.context = {"runId": "fixture", "sessionID": "ses_fixture", "report": self.report,
                        "state": evidence.loop.STATE, "startCommit": self.fixture.git("rev-parse", "HEAD"),
                        "selection": {"mode": "implement", "target": "F1", "findingIds": []}}
        self.details = {"summary": "Delivered fixture", "selectionReason": "First ready task",
                        "checks": [{"kind": "required", "command": "fixture check", "exitCode": 0,
                                    "evidence": self.output}]}
        self.fixture.write(self.output, {"command": ["fixture"], "exitCode": 0, "output": "ok\r\n ",
                                      "outputSha256": hashlib.sha256(b"ok\r\n ").hexdigest()})
        state = self.fixture.state()
        state["tasks"][0].update(status="implemented", implementationRecord=self.report)
        state["runs"].append(self.report)
        self.fixture.write(evidence.loop.STATE, state)

    def tearDown(self):
        self.fixture.close()

    def test_report_and_validate_without_custom_helper_or_staging(self):
        report = evidence.make_report(self.root, self.context, self.details)
        self.assertEqual(set(report["changedPaths"]), {self.report, self.output, evidence.loop.STATE})
        self.assertEqual(evidence.validate(self.root, self.report)["next"]["target"], "F2")
        self.assertEqual(self.fixture.git("diff", "--cached", "--name-only"), "")

    def test_supplied_identity_cannot_be_replaced(self):
        for key in ("runId", "sessionID", "startCommit", "mode", "target"):
            with self.subTest(key=key), self.assertRaises(evidence.loop.Halt):
                evidence.make_report(self.root, self.context, {**self.details, key: "other"})

    def test_attempted_finding_subset_is_not_overwritten(self):
        self.context["selection"].update(mode="fix", findingIds=["R1", "R2"])
        report = evidence.make_report(self.root, self.context, {**self.details, "findingIds": ["R1"]})
        self.assertEqual(report["findingIds"], ["R1"])

    def test_pending_foreground_self_capture_is_explicit_and_run_owned(self):
        (self.root / self.output).unlink()
        evidence.make_report(self.root, self.context, self.details)
        self.assertEqual(evidence.validate(self.root, self.report, self.output)["pendingCapture"], self.output)
        with self.assertRaises(FileNotFoundError):
            evidence.validate(self.root, self.report)
        for path in ("runtime.py", evidence.loop.REPORTS + "/older/check.json", "../escape"):
            with self.subTest(path=path), self.assertRaises(evidence.loop.Halt):
                evidence.validate(self.root, self.report, path)

    def test_unreported_file_and_output_tampering_rejected(self):
        evidence.make_report(self.root, self.context, self.details)
        self.fixture.write("surprise.py", "unreported\n")
        with self.assertRaisesRegex(evidence.loop.Halt, "changedPaths"):
            evidence.validate(self.root, self.report)
        (self.root / "surprise.py").unlink()
        record = evidence.loop.load(self.root / self.output)
        record["output"] = "changed output"
        self.fixture.write(self.output, record)
        with self.assertRaisesRegex(evidence.loop.Halt, "hash"):
            evidence.validate(self.root, self.report)

    def test_red_diagnostic_stays_red_and_failed_required_rejected(self):
        record = evidence.loop.load(self.root / self.output)
        record["exitCode"] = 1
        self.fixture.write(self.output, record)
        self.details["checks"][0].update(kind="diagnostic", exitCode=1)
        self.details["checks"].append({"kind": "required", "command": "state audit", "exitCode": 0,
                                      "evidence": self.report.replace(".json", "-audit.txt")})
        self.fixture.write(self.details["checks"][1]["evidence"], "state audit\n")
        evidence.make_report(self.root, self.context, self.details)
        evidence.validate(self.root, self.report)
        self.details["checks"][0]["kind"] = "required"
        evidence.make_report(self.root, self.context, self.details)
        with self.assertRaisesRegex(evidence.loop.Halt, "Required check failed"):
            evidence.validate(self.root, self.report)

    def test_junit_retains_exact_red_xml_without_sanitization(self):
        raw = b'<testsuite tests="1" failures="1"><testcase name="red"><failure>bad \r\n</failure></testcase></testsuite>'
        path = "build/test-results/red.xml"
        destination = self.root / path
        destination.parent.mkdir(parents=True)
        destination.write_bytes(raw)
        output = evidence.loop.REPORTS + "/fixture/junit.json"
        evidence.retain_junit(self.root, output, [path])
        saved = evidence.loop.load(self.root / output)[path]
        self.assertEqual(base64.b64decode(saved["base64"]), raw)
        self.assertEqual(saved["tests"]["failures"], "1")

    def test_reconcile_exact_committed_child_and_detect_changed_inputs(self):
        self.fixture.write("runtime.py", "original input\n")
        evidence.make_report(self.root, self.context, self.details)
        self.fixture.commit()
        commit = self.fixture.git("rev-parse", "HEAD")
        output = evidence.loop.REPORTS + "/parent/reuse.json"
        result = evidence.reconcile(self.root, output, [self.report + "@" + commit])
        self.assertEqual(result["changedInputsSinceLastChild"], [])
        self.fixture.write("runtime.py", "later input\n")
        result = evidence.reconcile(self.root, output, [self.report + "@" + commit])
        self.assertEqual(result["changedInputsSinceLastChild"], ["runtime.py"])
        self.fixture.commit()
        result = evidence.reconcile(self.root, output, [self.report + "@" + commit])
        self.assertEqual(result["changedInputsSinceLastChild"], ["runtime.py"])
        self.fixture.write(self.output, {"exitCode": 0, "output": "tampered"})
        with self.assertRaisesRegex(evidence.loop.Halt, "committed bytes"):
            evidence.reconcile(self.root, output, [self.report + "@" + commit])

    def test_reconcile_child_delivered_during_parent_split(self):
        state = self.fixture.state()
        state["tasks"][0].update(status="in-progress", children=["F1a"])
        state["tasks"].append({"id": "F1a", "batch": "B1", "status": "implemented", "implementationRecord": self.report})
        self.fixture.write(evidence.loop.STATE, state)
        details = {**self.details, "implementationUnits": [
            {"id": "F1a", "sourceIds": ["T-fixture"], "evidence": self.output}]}
        evidence.make_report(self.root, self.context, details)
        self.fixture.commit()
        commit = self.fixture.git("rev-parse", "HEAD")
        result = evidence.reconcile(self.root, evidence.loop.REPORTS + "/parent/reuse.json", ["F1a=" + self.report + "@" + commit])
        self.assertEqual(result["children"][0]["task"], "F1a")
        with self.assertRaisesRegex(evidence.loop.Halt, "not delivered"):
            evidence.reconcile(self.root, evidence.loop.REPORTS + "/parent/bad.json", ["F2=" + self.report + "@" + commit])

    def test_capture_creates_nested_output_and_preserves_non_utf8_failure(self):
        output = self.root / "nested/captured.json"
        result = subprocess.run([sys.executable, str(ROOT / "scripts/capture-check.py"), str(output), "--",
                                 sys.executable, "-c", "import sys;sys.stdout.buffer.write(b'\\xff\\r\\n ');sys.exit(3)"],
                                capture_output=True)
        self.assertEqual(result.returncode, 3)
        saved = json.loads(output.read_text())
        self.assertEqual(base64.b64decode(saved["outputBase64"]), b"\xff\r\n ")

    def test_old_child_output_is_input_not_current_ancestor_closure(self):
        before = fixtures.initial_state()
        before["tasks"][0].update(status="in-progress", children=["F1a"])
        before["tasks"].append({"id": "F1a", "batch": "B1", "status": "pending"})
        old = evidence.loop.REPORTS + "/older/check.txt"
        self.fixture.write(old, "old child passed\n")
        self.fixture.write(evidence.loop.STATE, before)
        self.fixture.commit()
        self.context.update(runId="closure", report=evidence.loop.REPORTS + "/closure.json",
                            startCommit=self.fixture.git("rev-parse", "HEAD"))
        self.context["selection"]["target"] = "F1a"
        after = copy.deepcopy(before)
        after["tasks"][0].update(status="implemented", implementationRecord=self.context["report"])
        after["tasks"][-1]["status"] = "implemented"
        after["runs"].append(self.context["report"])
        self.fixture.write(evidence.loop.STATE, after)
        details = {**self.details, "checks": [{"kind": "required", "command": "old child check", "exitCode": 0,
                                               "evidence": old}],
                   "ancestorClosures": [{"id": "F1", "reason": "Reconciled parent", "evidence": old}]}
        evidence.make_report(self.root, self.context, details)
        with self.assertRaisesRegex(evidence.loop.Halt, "current-run"):
            evidence.validate(self.root, self.context["report"])
        fresh = evidence.loop.REPORTS + "/closure/reconciliation.txt"
        self.fixture.write(fresh, "Current parent reconciliation: reused older/check.txt; missing seam checked\n")
        details["checks"].append({"kind": "required", "command": "actual scoped reconciliation", "exitCode": 0, "evidence": fresh})
        details["ancestorClosures"][0]["evidence"] = fresh
        evidence.make_report(self.root, self.context, details)
        evidence.validate(self.root, self.context["report"])


class ManagedEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.GitFixture()
        self.root = self.fixture.root
        self.worker_path = evidence.loop.REPORTS + "/worker.json"
        self.worker_output = evidence.loop.REPORTS + "/worker/check.txt"
        self.accept_path = evidence.loop.REPORTS + "/accept.json"
        self.accept_output = evidence.loop.REPORTS + "/accept/reconcile.txt"

    def tearDown(self):
        self.fixture.close()

    def worker(self, mode="implement", depends=False):
        before = self.fixture.state()
        if mode == "implement" and depends:
            before["tasks"][1]["dependsOn"] = ["F1"]
        elif mode in {"review", "verify"}:
            for task in before["tasks"]:
                task["status"] = "implemented"
            before["batches"][0]["status"] = "ready-for-review"
            if mode == "verify":
                before["findings"] = [{"id": "R1", "batch": "B1", "status": "fixed-awaiting-check", "disposition": "actionable"}]
                before["batches"][0].update(status="fixes-awaiting-check", review=fixtures.review_record("prior-code"))
        before["externalReleaseGates"] = [{"id": "operator", "status": "unverified"}]
        self.fixture.write(evidence.loop.STATE, before)
        self.fixture.commit()
        self.start = self.fixture.git("rev-parse", "HEAD")
        after = copy.deepcopy(before)
        if mode == "implement":
            after["tasks"][1].update(status="implemented", implementationRecord=self.worker_path)
            changes = [{"collection": "tasks", "id": "F2", "before": before["tasks"][1], "after": after["tasks"][1]}]
            self.fixture.write("probe.txt", "worker source change\n")
        else:
            batch = after["batches"][0]
            batch.update(status="reviewed", reviewedCommit=self.start, review=fixtures.review_record(self.start if mode == "review" else "prior-code"))
            changes = [{"collection": "batches", "id": "B1", "before": before["batches"][0], "after": batch}]
            if mode == "verify":
                after["findings"][0]["status"] = "verified"
                changes.append({"collection": "findings", "id": "R1", "before": before["findings"][0], "after": after["findings"][0]})
        self.fixture.write("tests/regression.py", "# focused regression fixture\n")
        self.fixture.write(self.worker_output, "worker scoped check output\n")
        self.context = {"runId": "worker", "sessionID": "ses_worker", "startCommit": self.start,
                        "state": evidence.loop.STATE, "report": self.worker_path,
                        "selection": {"mode": mode, "target": "F2" if mode == "implement" else "B1", "findingIds": ["R1"] if mode == "verify" else []}}
        self.details = {"summary": "Bounded worker fixture", "selectionReason": "Useful speculative development",
                        "findingIds": ["R1"] if mode == "verify" else [], "reviewedUnitIds": ["capability"] if mode == "review" else [],
                        "checks": [{"kind": "required", "command": "fixture affected check", "exitCode": 0, "evidence": self.worker_output}],
                        "proposal": {"sourceIds": ["T-fixture"], "sourcePaths": ["probe.txt"], "testPaths": ["tests/regression.py"],
                                     "allowedPaths": ["probe.txt", "tests/regression.py", self.worker_path, self.worker_output], "changes": changes}}
        return evidence.make_report(self.root, self.context, self.details)

    def save_worker(self):
        evidence.validate_proposal(self.root, self.worker_path)
        self.fixture.commit()
        self.worker_commit = self.fixture.git("rev-parse", "HEAD")
        return evidence.validate_proposal(self.root, self.worker_path, saved=True)

    def acceptance(self, advance=True):
        worker = evidence.loop.load(self.root / self.worker_path)
        if advance and worker["mode"] == "implement":
            state = self.fixture.state()
            state["tasks"][0]["status"] = "implemented"
            self.fixture.write(evidence.loop.STATE, state)
        else:
            self.fixture.write("unrelated.txt", "unrelated accepted code advanced\n")
        self.fixture.commit()
        self.head = self.fixture.git("rev-parse", "HEAD")
        self.before_path = self.fixture.base / "before.json"
        self.before_path.write_bytes((self.root / evidence.loop.STATE).read_bytes())
        after = self.fixture.state()
        applied = copy.deepcopy(worker)
        for change in applied["proposal"]["changes"]:
            change["before"] = next((x for x in after[change["collection"]] if x["id"] == change["id"]), None)
        if worker["mode"] == "implement":
            after = evidence.proposed_state(after, applied, self.worker_path)
            after["runs"].pop()
            for task in after["tasks"]:
                if task.get("implementationRecord") == self.worker_path:
                    task["implementationRecord"] = self.accept_path
            if after["tasks"][0]["status"] == "implemented":
                after["batches"][0]["status"] = "ready-for-review"
        else:
            after = evidence.proposed_state(after, applied, self.worker_path)
            after["runs"].pop()
        after["runs"].append(self.accept_path)
        self.fixture.write(evidence.loop.STATE, after)
        self.fixture.write(self.accept_output, "fresh latest-state reconciliation: worker inputs reused, affected seam checked\n")
        context = {**self.context, "runId": "accept", "sessionID": "ses_integrator", "report": self.accept_path, "startCommit": self.head}
        details = {k: copy.deepcopy(v) for k, v in self.details.items() if k != "proposal"}
        details.update(workerProposal={"report": self.worker_path, "commit": self.worker_commit},
                       inspectedCommit=self.start if worker["mode"] in {"review", "verify"} else self.head,
                       checks=[{"kind": "required", "command": "actual latest reconciliation", "exitCode": 0, "evidence": self.accept_output}])
        for key in ("implementationUnits", "ancestorClosures"):
            for entry in details.get(key, []):
                entry["evidence"] = self.accept_output
        return evidence.make_report(self.root, context, details)

    def validate_acceptance(self):
        return evidence.validate_acceptance(self.root, self.accept_path, self.before_path, self.root / evidence.loop.STATE)

    def test_readonly_speculative_worker_and_saved_branch_identity(self):
        self.worker(depends=True)
        state_bytes = (self.root / evidence.loop.STATE).read_bytes()
        self.assertEqual(evidence.loop.selection(self.fixture.state())["target"], "F1")
        result = self.save_worker()
        self.assertEqual(result["commit"], self.worker_commit)
        self.assertEqual(result["branch"], "main")
        self.assertEqual(result["sessionID"], "ses_worker")
        self.assertEqual((self.root / evidence.loop.STATE).read_bytes(), state_bytes)
        self.assertEqual(self.fixture.git("status", "--porcelain"), "")

    def test_latest_acceptance_after_other_branch_progress_not_worker_head(self):
        self.worker(depends=True)
        self.save_worker()
        self.acceptance()
        result = self.validate_acceptance()
        self.assertNotEqual(self.start, self.head)
        self.assertEqual(result["latestStateCommit"], self.head)
        self.assertEqual(result["next"]["mode"], "review")
        self.assertEqual(self.fixture.state()["externalReleaseGates"][0]["status"], "unverified")

    def test_speculative_split_units_defer_serial_scheduling_but_accept_fresh_checks(self):
        self.worker(depends=True)
        target = self.details["proposal"]["changes"][0]["after"]
        target["children"] = ["F2a"]
        child = {"id": "F2a", "batch": "B1", "status": "implemented", "dependsOn": [],
                 "outcome": "Useful child", "checks": ["affected check"], "implementationRecord": self.worker_path}
        self.details["proposal"]["changes"].append({"collection": "tasks", "id": "F2a", "before": None, "after": child})
        self.details["implementationUnits"] = [{"id": "F2a", "sourceIds": ["T-fixture"], "evidence": self.worker_output}]
        evidence.make_report(self.root, self.context, self.details)
        self.save_worker()
        self.acceptance()
        self.validate_acceptance()
        report = evidence.loop.load(self.root / self.accept_path)
        report["implementationUnits"][0]["evidence"] = self.worker_output
        report["checks"].append({"kind": "required", "command": "old worker check", "exitCode": 0, "evidence": self.worker_output})
        self.fixture.write(self.accept_path, report)
        with self.assertRaisesRegex(evidence.loop.Halt, "current-run"):
            self.validate_acceptance()

    def test_acceptance_cannot_skip_original_prerequisite_or_current_priority(self):
        self.worker(depends=True)
        self.save_worker()
        self.acceptance(advance=False)
        with self.assertRaisesRegex(evidence.loop.Halt, "Latest selection"):
            self.validate_acceptance()

    def test_acceptance_cannot_skip_a_newly_due_independent_capability_review(self):
        self.worker()
        self.save_worker()
        state = self.fixture.state()
        state["tasks"].append({"id": "F3", "batch": "B2", "status": "implemented", "independent": True, "dependsOn": []})
        state["batches"].append({"id": "B2", "status": "ready-for-review", "reviewedCommit": None})
        self.fixture.write(evidence.loop.STATE, state)
        self.fixture.commit()
        self.acceptance()
        with self.assertRaisesRegex(evidence.loop.Halt, "Latest selection"):
            self.validate_acceptance()

    def test_acceptance_rejects_selected_task_drift_instead_of_copying_old_worker_state(self):
        self.worker()
        self.save_worker()
        state = self.fixture.state()
        state["tasks"][1]["outcome"] = "Updated integration contract"
        self.fixture.write(evidence.loop.STATE, state)
        self.fixture.commit()
        self.acceptance()
        with self.assertRaisesRegex(evidence.loop.Halt, "Selected task changed"):
            self.validate_acceptance()

    def test_worker_cannot_edit_authoritative_state_even_if_allowlisted(self):
        self.worker()
        self.details["proposal"]["allowedPaths"].append(evidence.loop.STATE)
        state = self.fixture.state()
        state["tasks"][1]["status"] = "implemented"
        self.fixture.write(evidence.loop.STATE, state)
        evidence.make_report(self.root, self.context, self.details)
        with self.assertRaisesRegex(evidence.loop.Halt, "read-only"):
            evidence.validate_proposal(self.root, self.worker_path)

    def test_review_readonly_source_inputs_need_no_write_lease(self):
        self.worker("review")
        self.details["proposal"]["allowedPaths"].remove("probe.txt")
        evidence.make_report(self.root, self.context, self.details)
        evidence.validate_proposal(self.root, self.worker_path)

    def test_managed_cli_uses_separate_boundaries_without_writing_or_staging_state(self):
        self.worker()
        script = str(ROOT / "scripts/implementation-evidence.py")
        before_bytes = (self.root / evidence.loop.STATE).read_bytes()
        subprocess.run([sys.executable, "-B", script, "validate-proposal", self.worker_path],
                       cwd=self.root, capture_output=True, check=True)
        self.assertEqual((self.root / evidence.loop.STATE).read_bytes(), before_bytes)
        self.save_worker()
        self.acceptance()
        after_bytes = (self.root / evidence.loop.STATE).read_bytes()
        subprocess.run([sys.executable, "-B", script, "validate-acceptance", self.accept_path,
                        "--before", str(self.before_path), "--after", evidence.loop.STATE],
                       cwd=self.root, capture_output=True, check=True)
        self.assertEqual((self.root / evidence.loop.STATE).read_bytes(), after_bytes)
        self.assertEqual(self.fixture.git("diff", "--cached", "--name-only"), "")

    def test_worker_scope_and_state_role_guards(self):
        self.worker()
        valid = copy.deepcopy(self.details)
        for fault in ("unleased", "unrelated-task", "gate", "controls", "wrong-before", "red"):
            details = copy.deepcopy(valid)
            if fault == "unleased": details["proposal"]["allowedPaths"].remove("probe.txt")
            elif fault == "unrelated-task":
                old = self.fixture.state()["tasks"][0]
                details["proposal"]["changes"].append({"collection": "tasks", "id": "F1", "before": old, "after": {**old, "status": "implemented"}})
            elif fault == "gate":
                old = self.fixture.state()["externalReleaseGates"][0]
                details["proposal"]["changes"].append({"collection": "externalReleaseGates", "id": "operator", "before": old,
                                                       "after": {**old, "status": "passed", "record": self.worker_path}})
            elif fault == "controls": details["proposal"]["changes"][0]["collection"] = "runner"
            elif fault == "wrong-before": details["proposal"]["changes"][0]["before"] = None
            elif fault == "red": details["checks"][0]["exitCode"] = 1
            evidence.make_report(self.root, self.context, details)
            with self.subTest(fault=fault), self.assertRaises(evidence.loop.Halt):
                evidence.validate_proposal(self.root, self.worker_path)

    def test_review_worker_cannot_repair_runtime_or_build_inputs(self):
        self.assert_no_runtime_repair("review")

    def test_verifier_worker_cannot_repair_runtime_or_build_inputs(self):
        self.assert_no_runtime_repair("verify")

    def assert_no_runtime_repair(self, mode):
        self.worker(mode)
        for path in ("probe.txt", "build.gradle.kts"):
            self.fixture.write(path, "forbidden runtime/build repair\n")
            if path not in self.details["proposal"]["allowedPaths"]:
                self.details["proposal"]["allowedPaths"].append(path)
            evidence.make_report(self.root, self.context, self.details)
            with self.subTest(path=path), self.assertRaisesRegex(evidence.loop.Halt, "Product changes forbidden"):
                evidence.validate_proposal(self.root, self.worker_path)

    def test_review_and_verification_keep_independent_code_anchor_after_head_advance(self):
        self.worker("review")
        self.save_worker()
        self.acceptance()
        self.assertEqual(self.validate_acceptance()["inspectedCommit"], self.start)
        after = self.fixture.state()
        self.assertEqual(after["batches"][0]["reviewedCommit"], self.start)
        self.assertEqual(after["batches"][0]["review"]["completed"]["capability"], self.start)
        report = evidence.loop.load(self.root / self.accept_path)
        report["inspectedCommit"] = self.head
        self.fixture.write(self.accept_path, report)
        with self.assertRaisesRegex(evidence.loop.Halt, "independent inspected"):
            self.validate_acceptance()

    def test_fresh_verifier_can_accept_without_replacing_first_review_coverage(self):
        self.worker("verify")
        self.save_worker()
        self.acceptance()
        self.validate_acceptance()
        self.assertEqual(self.fixture.state()["findings"][0]["status"], "verified")
        self.assertEqual(self.fixture.state()["batches"][0]["review"]["completed"], {"capability": "prior-code"})

    def test_acceptance_cannot_invent_independent_review_coverage(self):
        self.worker("review")
        self.save_worker()
        self.acceptance()
        report = evidence.loop.load(self.root / self.accept_path)
        report["reviewedUnitIds"].append("not-inspected-by-worker")
        self.fixture.write(self.accept_path, report)
        with self.assertRaisesRegex(evidence.loop.Halt, "independently proposed scope"):
            self.validate_acceptance()

    def test_saved_proposal_checks_actual_upstream_not_tracking_ref(self):
        self.worker()
        evidence.validate_proposal(self.root, self.worker_path)
        self.fixture.git("add", ".")
        self.fixture.git("commit", "-m", "Unpushed worker")
        with self.assertRaisesRegex(evidence.loop.Halt, "actual upstream"):
            evidence.validate_proposal(self.root, self.worker_path, saved=True)

    def test_reconcile_managed_child_tracks_accepted_source_and_test_inputs(self):
        self.worker()
        self.save_worker()
        self.acceptance()
        self.validate_acceptance()
        self.fixture.commit()
        accepted = self.fixture.git("rev-parse", "HEAD")
        output = evidence.loop.REPORTS + "/later/reuse.json"
        result = evidence.reconcile(self.root, output, [self.accept_path + "@" + accepted])
        self.assertEqual(result["changedInputsSinceLastChild"], [])
        self.fixture.write("probe.txt", "later integration input\n")
        result = evidence.reconcile(self.root, output, [self.accept_path + "@" + accepted])
        self.assertEqual(result["changedInputsSinceLastChild"], ["probe.txt"])

    def test_acceptance_rejects_stale_snapshot_unrelated_changes_and_control_gate_bypass(self):
        self.worker()
        self.save_worker()
        self.acceptance()
        valid_state = self.fixture.state()
        valid_report = evidence.loop.load(self.root / self.accept_path)
        before_bytes = self.before_path.read_bytes()
        for fault in ("snapshot", "unrelated", "controls", "gate", "task", "missing-review", "stale-check"):
            after, report = copy.deepcopy(valid_state), copy.deepcopy(valid_report)
            if fault == "snapshot": self.before_path.write_text("{}\n")
            elif fault == "unrelated":
                self.fixture.write("surprise.py", "not an acceptance output\n")
                report["changedPaths"].append("surprise.py")
            elif fault == "controls": after["runner"]["model"] = "wrong-model"
            elif fault == "gate": after["externalReleaseGates"][0].update(status="passed", record=self.accept_path)
            elif fault == "task": after["tasks"][0]["outcome"] = "unrelated narrowing"
            elif fault == "missing-review": fixtures.reviewed(after["batches"][0], self.head)
            elif fault == "stale-check": report["checks"][0]["evidence"] = self.worker_output
            self.fixture.write(evidence.loop.STATE, after)
            self.fixture.write(self.accept_path, report)
            with self.subTest(fault=fault), self.assertRaises(evidence.loop.Halt):
                self.validate_acceptance()
            self.before_path.write_bytes(before_bytes)
            if fault == "unrelated": (self.root / "surprise.py").unlink()


if __name__ == "__main__":
    unittest.main(verbosity=2)
