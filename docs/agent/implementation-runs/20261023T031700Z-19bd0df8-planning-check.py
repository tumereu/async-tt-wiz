"""Read-only validation of the bounded I12 cancellation and native journey split."""
import hashlib
import json
from pathlib import Path
import runpy
import subprocess


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


report_path = "docs/agent/implementation-runs/20261023T031700Z-19bd0df8.json"
state_path = "docs/agent/implementation-state.json"
report = json.loads(Path(report_path).read_text())
before = json.loads(git("show", report["startCommit"] + ":" + state_path))
after = json.loads(Path(state_path).read_text())
api = runpy.run_path("scripts/implementation-loop.py")
choice = {"mode": "implement", "target": "I12", "findingIds": []}
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
assert all(old[identifier]["status"] == "implemented" for identifier in ["I09", "I10", "I11"])
assert set(new) - set(old) == {"I12a", "I12b"}
assert all(new[identifier] == task for identifier, task in old.items() if identifier != "I12")
assert all(new["I12"][key] == value for key, value in old["I12"].items() if key != "status")
assert new["I12"]["status"] == "in-progress"
assert new["I12"]["children"] == report["planning"]["children"] == ["I12a", "I12b"]
for identifier, dependencies in (("I12", ["I11"]), ("I12a", ["I11"]), ("I12b", ["I12a"])):
    task = new[identifier]
    assert task["batch"] == "B3" and task["planningRecord"] == report_path
    assert task["outcome"] and task["checks"] and task["dependsOn"] == dependencies
    if identifier != "I12":
        assert task["status"] == "pending" and not task.get("children")
assert api["selection"](after) == {"mode": "implement", "target": "I12a", "findingIds": []}
print("PASS exact supplied baseline/master, empty index, D-304 authorization and I12 priority")
print("PASS existing state/transition validators; two pending B3 implementation children; next I12a")
print("PASS unrelated tasks/reviews/findings, authorization/model/grants and external gates unchanged")

for path in report["sourcePaths"] + report["planning"]["contractPaths"]:
    data = Path(path).read_bytes()
    assert data == subprocess.check_output(["git", "show", report["startCommit"] + ":" + path]), path
    print(f"UNCHANGED SOURCE {path} SHA256 {hashlib.sha256(data).hexdigest()}")
ledger = json.loads(Path("docs/agent/coverage/ledger.json").read_text())
items = [item for item in ledger["items"] if "I12" in item["implementationDependencies"]]
assert [item["id"] for item in items] == report["planning"]["sourceIds"]
for item in items:
    assertion = next(assertion for assertion in item["assertions"] if assertion["check"] == "I12.lobby")
    assert assertion["status"] == "pending" and item["completion"] == "unexecuted"
print(f"PASS {len(items)} exact I12.lobby assignments retained pending/unexecuted; no new product pass")

wire = Path("protocol/src/commonMain/kotlin/org/asyncttwiz/protocol/GameWire.kt").read_text()
service = Path("server/src/main/kotlin/org/asyncttwiz/server/game/GameService.kt").read_text()
screens = Path("androidApp/src/main/kotlin/org/asyncttwiz/android/LobbyScreens.kt").read_text()
assert '@SerialName("ready")' in wire and '@SerialName("cancelGame")' not in wire
assert 'game.lifecycle !in listOf("LOBBY", "ACTIVE")' in service
assert 'is GameAction.SetStarter, GameAction.Ready, GameAction.WithdrawReady -> applyReadiness' in service
assert 'controller.withdrawReady()' in screens and '"Cancel game"' not in screens
print("CONFIRMED current seam: Ready/start exists; cancellation action, terminal-result recovery and native controls remain")
print("PLANNED I12a: durable round-bound setup cancellation and coherent original-outcome/session/list lifecycle recovery")
print("PLANNED I12b: native title/context Cancel/L06 and two-Android preparation/start/cancel/source-isolation journeys")

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
print("NOT RUN: product/build/device/full-suite checks; no application input or UI document changed")
print("NOT CLAIMED: implemented cancellation, full cross-task scenarios, independent review or release clearance")
