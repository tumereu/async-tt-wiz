"""Read-only I06b delivery split validation; no product/controller/device execution."""
import hashlib
import json
from pathlib import Path
import runpy
import subprocess


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


report_path = "docs/agent/implementation-runs/20261022T121920Z-442a17be.json"
state_path = "docs/agent/implementation-state.json"
report = json.loads(Path(report_path).read_text())
before = json.loads(git("show", report["startCommit"] + ":" + state_path))
after = json.loads(Path(state_path).read_text())
api = runpy.run_path("scripts/implementation-loop.py")
choice = {"mode": "implement", "target": "I06b", "findingIds": []}
assert git("rev-parse", "HEAD") == report["startCommit"]
assert git("branch", "--show-current") == "master"
assert not git("diff", "--cached", "--name-only")
assert before["mode"] == "active" and before["authorization"] == "authorized"
assert "D-304" in Path(before["authorizationRecord"]).read_text()
assert before["runner"]["model"] == "openai/gpt-6.1-sol#high"
assert before["findings"] == []
assert api["selection"](before) == choice
api["validate_state"](after)
api["validate_transition"](before, after, choice, report)
print("PASS supplied baseline/master, empty index, D-304 authorization and actual I06b priority")
print("PASS existing driver state/planning-only transition validators; no controller started")

assert after["activeRun"] is None
assert after["runs"] == before["runs"] + [report_path]
for key in before:
    if key not in {"tasks", "runs"}:
        assert before[key] == after[key], key
b1 = next(batch for batch in before["batches"] if batch["id"] == "B1")
assert b1["status"] == "reviewed" and api["review_complete"](b1)
old = {task["id"]: task for task in before["tasks"]}
new = {task["id"]: task for task in after["tasks"]}
assert old["I06a"]["status"] == "implemented"
assert old["I06b"]["status"] == "pending"
assert new["I06b"]["status"] == "in-progress"
assert new["I06b"]["children"] == ["I06b1", "I06b2"]
assert set(new) - set(old) == {"I06b1", "I06b2"}
assert all(new[i] == task for i, task in old.items() if i != "I06b")
for key in old["I06b"]:
    if key != "status":
        assert old["I06b"][key] == new["I06b"][key], key
assert new["I06b"]["deliverySplitRecord"] == report_path
for identifier, dependencies in (("I06b1", ["I06a"]), ("I06b2", ["I06b1"])):
    task = new[identifier]
    assert task["status"] == "pending" and task["batch"] == "B2"
    assert task["outcome"] and task["checks"] and task["dependsOn"] == dependencies
    assert task["planningRecord"] == report_path
assert api["selection"](after) == {"mode": "implement", "target": "I06b1", "findingIds": []}
print("PASS exactly two pending B2 delivery children, explicit acyclic dependencies; next I06b1")
print("PASS every original parent outcome/check/dependency/provenance retained; ancestors not closed")
print("PASS unrelated tasks/reviews/findings/model/grants/authorization/release gates unchanged")

for path in report["sourcePaths"]:
    data = Path(path).read_bytes()
    assert data == subprocess.check_output(["git", "show", report["startCommit"] + ":" + path]), path
ledger_bytes = Path("docs/agent/coverage/ledger.json").read_bytes()
items = [i for i in json.loads(ledger_bytes)["items"] if "I06" in i["implementationDependencies"]]
assert [i["id"] for i in items] == report["planning"]["sourceIds"]
for item in items:
    assertion = next(a for a in item["assertions"] if a["check"] == "I06.import")
    assert assertion["status"] == "pending" and item["completion"] == "unexecuted"
assert 'enabled = false' in Path("androidApp/src/main/kotlin/org/asyncttwiz/android/DeckImportScreen.kt").read_text()
assert sorted(p.name for p in Path("server/src/main/resources/db/migration").glob("*.sql")) == [
    "V1__service_writer.sql", "V2__accounts_and_sessions.sql", "V3__password_reset.sql"]
print(f"PASS {len(items)} exact source IDs pending; unchanged ledger SHA256 {hashlib.sha256(ledger_bytes).hexdigest()}")
print("PASS source inputs unchanged, native Save still unavailable, existing migrations account/reset only")

actual = set(git("diff", "--name-only").splitlines()) | set(git("ls-files", "--others", "--exclude-standard").splitlines())
assert actual == set(report["changedPaths"]), (actual, report["changedPaths"])
runner = object.__new__(api["Runner"])
runner.root = Path.cwd()
runner.state_path = Path(state_path)
runner.state_relative = state_path
runner.role_paths(before, choice, report, actual, report_path)
for path in actual:
    data = Path(path).read_bytes()
    assert b"\r" not in data and (not data or data.endswith(b"\n")), path
    assert all(line.rstrip() == line for line in data.splitlines()), path
subprocess.run(["git", "diff", "--check"], check=True)
print("PASS exact owned documentation/support paths, JSON/source references and whitespace")
print("NOT RUN: product builds/tests/native journeys; planning-only, no runtime/UI-document edits")
