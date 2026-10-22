"""Read-only I05 pre-coding split validation; no product/controller/device launch."""
import hashlib
import json
from pathlib import Path
import runpy
import subprocess


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


report_path = "docs/agent/implementation-runs/20261022T100621Z-a6dcf219.json"
state_path = "docs/agent/implementation-state.json"
report = json.loads(Path(report_path).read_text())
before = json.loads(git("show", report["startCommit"] + ":" + state_path))
after = json.loads(Path(state_path).read_text())
api = runpy.run_path("scripts/implementation-loop.py")
choice = {"mode": "implement", "target": "I05", "findingIds": []}
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
print("PASS exact supplied baseline/master, empty index, D-304 authorization and I05 priority")
print("PASS existing driver schema and planning-only transition; no controller started")

assert after["activeRun"] is None
assert after["runs"] == before["runs"] + [report_path]
for key in before:
    if key not in {"tasks", "runs"}:
        assert before[key] == after[key], key
b1 = next(batch for batch in before["batches"] if batch["id"] == "B1")
assert b1["status"] == "reviewed" and api["review_complete"](b1)
old = {task["id"]: task for task in before["tasks"]}
new = {task["id"]: task for task in after["tasks"]}
assert old["I04"]["status"] == "implemented"
assert new["I05"]["status"] == "in-progress"
assert new["I05"]["children"] == ["I05a", "I05b"]
assert set(new) - set(old) == {"I05a", "I05b"}
assert all(new[i] == task for i, task in old.items() if i != "I05")
for identifier, dependencies in (("I05a", ["I04"]), ("I05b", ["I05a"])):
    task = new[identifier]
    assert task["status"] == "pending" and task["batch"] == "B2"
    assert task["outcome"] and task["checks"] and task["dependsOn"] == dependencies
    assert task["planningRecord"] == report_path
assert api["selection"](after) == {"mode": "implement", "target": "I05a", "findingIds": []}
print("PASS reviewed B1 prerequisite, exactly two pending B2 delivery children; next I05a")
print("PASS unrelated tasks/reviews/findings/model/authorization/permissions/release gates unchanged")

for path in report["sourcePaths"]:
    assert Path(path).is_file(), path
ledger_path = "docs/agent/coverage/ledger.json"
ledger_bytes = Path(ledger_path).read_bytes()
assert ledger_bytes == subprocess.check_output(["git", "show", report["startCommit"] + ":" + ledger_path])
items = [i for i in json.loads(ledger_bytes)["items"] if "I05" in i["implementationDependencies"]]
assert [i["id"] for i in items] == report["planning"]["sourceIds"]
for item in items:
    assertion = next(a for a in item["assertions"] if a["check"] == "I05.catalog")
    assert assertion["status"] == "pending" and item["completion"] == "unexecuted"
print(f"PASS {len(items)} exact I05 source IDs retained pending; ledger SHA256 {hashlib.sha256(ledger_bytes).hexdigest()}")

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
print("PASS exact owned paths, run-owned check support, JSON/source references and whitespace")
print("NOT RUN: product builds/tests/device journeys; planning-only split, no UI-document edits")
