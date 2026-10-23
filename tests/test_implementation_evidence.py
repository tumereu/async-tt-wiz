"""Focused evidence tooling qualification; disposable Git only, no product/model calls."""
import base64
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


if __name__ == "__main__":
    unittest.main(verbosity=2)
