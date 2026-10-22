"""Read-only validation of the planning-only I10b transfer implementation split."""
import hashlib
import json
from pathlib import Path
import runpy
import subprocess


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


report_path = "docs/agent/implementation-runs/20261022T211529Z-904fa077.json"
state_path = "docs/agent/implementation-state.json"
report = json.loads(Path(report_path).read_text())
before = json.loads(git("show", report["startCommit"] + ":" + state_path))
after = json.loads(Path(state_path).read_text())
api = runpy.run_path("scripts/implementation-loop.py")
choice = {"mode": "implement", "target": "I10b", "findingIds": []}
assert git("rev-parse", "HEAD") == report["startCommit"]
assert git("branch", "--show-current") == "master"
assert not git("diff", "--cached", "--name-only")
assert before["authorization"] == "authorized" and before["mode"] == "active"
assert api["selection"](before) == choice
api["validate_state"](after)
api["validate_transition"](before, after, choice, report)
assert after["activeRun"] is None
assert after["runs"] == before["runs"] + [report_path]
assert all(before[key] == after[key] for key in before if key not in {"runs", "tasks"})
old = {task["id"]: task for task in before["tasks"]}
new = {task["id"]: task for task in after["tasks"]}
assert old["I09"]["status"] == old["I10a"]["status"] == "implemented"
assert set(new) - set(old) == {"I10b1", "I10b2"}
assert all(new[identifier] == task for identifier, task in old.items() if identifier != "I10b")
assert all(new["I10b"][key] == value for key, value in old["I10b"].items() if key != "status")
assert new["I10b"]["status"] == "in-progress"
assert new["I10b"]["children"] == report["planning"]["children"] == ["I10b1", "I10b2"]
assert new["I10b"]["splitRecord"] == report_path
for identifier, dependencies in (("I10b1", ["I10a"]), ("I10b2", ["I10b1"])):
    task = new[identifier]
    assert task["status"] == "pending" and task["batch"] == "B3"
    assert task["outcome"] and task["checks"] and task["dependsOn"] == dependencies
    assert task["planningRecord"] == report_path
assert api["selection"](after) == {"mode": "implement", "target": "I10b1", "findingIds": []}
print("PASS exact baseline/master, D-304 authorization, original priority and existing state/transition validators")
print("PASS two pending B3 implementation children; next I10b1; original I10b outcome/checks preserved verbatim")
print("PASS unrelated tasks/reviews/findings, authorization/model/grants and external release gates unchanged")

for path in report["sourcePaths"] + report["planning"]["contractPaths"]:
    data = Path(path).read_bytes()
    assert data == subprocess.check_output(["git", "show", report["startCommit"] + ":" + path]), path
    print(f"UNCHANGED SOURCE {path} SHA256 {hashlib.sha256(data).hexdigest()}")
ledger = json.loads(Path("docs/agent/coverage/ledger.json").read_text())
items = [item for item in ledger["items"] if "I10" in item["implementationDependencies"]]
assert [item["id"] for item in items] == report["planning"]["sourceIds"]
for item in items:
    assertion = next(assertion for assertion in item["assertions"] if assertion["check"] == "I10.preparation")
    assert assertion["status"] == "pending" and item["completion"] == "unexecuted"
print(f"PASS {len(items)} exact I10 source assignments retained with unchanged pending/unexecuted ledger accounting")

protocol = Path("protocol/src/commonMain/kotlin/org/asyncttwiz/protocol/GameWire.kt").read_text()
preparation = Path("protocol/src/commonMain/kotlin/org/asyncttwiz/protocol/Preparation.kt").read_text()
session = Path("androidApp/src/main/kotlin/org/asyncttwiz/android/GameSession.kt").read_text()
assert '@SerialName("selectDeck")' in protocol and "Transfer" not in protocol
assert "val firstIndex: String, val quantity: Int" in preparation
assert "if (!ready(game.id) || pending(game.id)" in session
print("CONFIRMED implementation seam: original copy ranges/selection-only actions and per-game single-pending gate")
print("PLANNED I10b1: per-copy ordered transfers, identity membership/guards and multiple-in-flight original prediction/recovery")
print("PLANNED I10b2: native taps/drags/accessibility/inspection, fixed fade/row transition and real both-orientation journey")

actual = set(git("diff", "--name-only").splitlines()) | set(git("ls-files", "--others", "--exclude-standard").splitlines())
assert actual == set(report["changedPaths"]), (actual, report["changedPaths"])
runner = object.__new__(api["Runner"])
runner.root = Path.cwd()
runner.state_path = Path(state_path)
runner.state_relative = state_path
runner.role_paths(before, choice, report, actual, report_path)
for check in report["checks"]:
    assert check["kind"] == "required" and check["exitCode"] == 0
    assert Path(check["evidence"]).is_file()
    assert all(Path(path).is_file() and path in actual for path in check.get("supportPaths", []))
for path in actual:
    data = Path(path).read_bytes()
    assert b"\r" not in data and (not data or data.endswith(b"\n")), path
    assert all(line.rstrip() == line for line in data.splitlines()), path
subprocess.run(["git", "diff", "--check"], check=True)
print("PASS exact owned state/report/evidence/support paths, JSON/references and whitespace")
print("NOT RUN: product/build/device/full-suite checks; no product input or UI document changed")
print("NOT CLAIMED: transfer delivery, Ready/start/play, independent review, final qualification or release clearance")
