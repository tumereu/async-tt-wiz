"""Read-only validation of the bounded I11 shared/server and native delivery split."""
import hashlib
import json
from pathlib import Path
import runpy
import subprocess


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


report_path = "docs/agent/implementation-runs/20261022T233436Z-cd34094d.json"
state_path = "docs/agent/implementation-state.json"
report = json.loads(Path(report_path).read_text())
before = json.loads(git("show", report["startCommit"] + ":" + state_path))
after = json.loads(Path(state_path).read_text())
api = runpy.run_path("scripts/implementation-loop.py")
choice = {"mode": "implement", "target": "I11", "findingIds": []}
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
assert old["I09"]["status"] == old["I10"]["status"] == "implemented"
assert set(new) - set(old) == {"I11a", "I11b"}
assert all(new[identifier] == task for identifier, task in old.items() if identifier != "I11")
assert all(new["I11"][key] == value for key, value in old["I11"].items() if key != "status")
assert new["I11"]["status"] == "in-progress"
assert new["I11"]["children"] == report["planning"]["children"] == ["I11a", "I11b"]
for identifier, dependencies in (("I11", ["I10"]), ("I11a", ["I10"]), ("I11b", ["I11a"])):
    task = new[identifier]
    assert task["batch"] == "B3" and task["planningRecord"] == report_path
    assert task["outcome"] and task["checks"] and task["dependsOn"] == dependencies
    if identifier != "I11":
        assert task["status"] == "pending" and not task.get("children")
assert api["selection"](after) == {"mode": "implement", "target": "I11a", "findingIds": []}
print("PASS exact supplied baseline/master, empty index, D-304 authorization and I11 priority")
print("PASS existing state/transition validators; two pending B3 implementation children; next I11a")
print("PASS unrelated tasks/reviews/findings, authorization/model/grants and external release gates unchanged")

for path in report["sourcePaths"] + report["planning"]["contractPaths"]:
    data = Path(path).read_bytes()
    assert data == subprocess.check_output(["git", "show", report["startCommit"] + ":" + path]), path
    print(f"UNCHANGED SOURCE {path} SHA256 {hashlib.sha256(data).hexdigest()}")
ledger = json.loads(Path("docs/agent/coverage/ledger.json").read_text())
items = [item for item in ledger["items"] if "I11" in item["implementationDependencies"]]
assert [item["id"] for item in items] == report["planning"]["sourceIds"]
for item in items:
    assertion = next(assertion for assertion in item["assertions"] if assertion["check"] == "I11.start")
    assert assertion["status"] == "pending" and item["completion"] == "unexecuted"
print(f"PASS {len(items)} exact I11.start assignments retained pending/unexecuted; no new product pass")

wire = Path("protocol/src/commonMain/kotlin/org/asyncttwiz/protocol/GameWire.kt").read_text()
service = Path("server/src/main/kotlin/org/asyncttwiz/server/game/GameService.kt").read_text()
preparation = Path("protocol/src/commonMain/kotlin/org/asyncttwiz/protocol/Preparation.kt").read_text()
assert '@SerialName("transferPreparedCard")' in wire and '@SerialName("ready")' not in wire
assert "val ready: Boolean = false" in preparation
assert "is GameAction.TransferPreparedCard -> applyTransfer" in service
assert "else -> applyInvitation" in service
print("CONFIRMED current seam: prepared copy/ready flags, invitation/selection/transfer actions; no Ready/start action")
print("PLANNED I11a: shared initial model/random vectors plus durable server starter/Ready/once-only initialization")
print("PLANNED I11b: native starter/Ready/withdrawal and accepted production initial table, both-orientation journey")

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
print("NOT CLAIMED: implemented start/table, complete cross-task assertions, independent review or release clearance")
