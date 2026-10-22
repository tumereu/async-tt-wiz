"""Read-only I10 vertical split validation; no controller or product execution."""
import hashlib
import json
from pathlib import Path
import runpy
import subprocess


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


report_path = "docs/agent/implementation-runs/20261022T194247Z-e9e3e478.json"
state_path = "docs/agent/implementation-state.json"
report = json.loads(Path(report_path).read_text())
before = json.loads(git("show", report["startCommit"] + ":" + state_path))
after = json.loads(Path(state_path).read_text())
api = runpy.run_path("scripts/implementation-loop.py")
choice = {"mode": "implement", "target": "I10", "findingIds": []}
assert git("rev-parse", "HEAD") == report["startCommit"]
assert git("branch", "--show-current") == "master"
assert not git("diff", "--cached", "--name-only")
assert before["mode"] == "active" and before["authorization"] == "authorized"
assert "D-304" in Path(before["authorizationRecord"]).read_text()
assert before["runner"]["model"] == "openai/gpt-6.1-sol#high"
assert not any(api["needs_attention"](f) for f in before["findings"])
assert api["selection"](before) == choice
api["validate_state"](after)
api["validate_transition"](before, after, choice, report)
print("PASS exact supplied baseline/master, empty index, D-304 authorization and I10 priority")
print("PASS existing driver state/transition validators; no controller/product execution")

assert after["activeRun"] is None
assert after["runs"] == before["runs"] + [report_path]
for key in before:
    if key not in {"tasks", "runs"}:
        assert before[key] == after[key], key
for identifier in ("B1", "B2"):
    batch = next(b for b in before["batches"] if b["id"] == identifier)
    assert batch["status"] == "reviewed" and api["review_complete"](batch)
old = {task["id"]: task for task in before["tasks"]}
new = {task["id"]: task for task in after["tasks"]}
assert old["I09"]["status"] == "implemented"
assert new["I10"]["status"] == "in-progress"
assert new["I10"]["children"] == report["planning"]["children"] == ["I10a", "I10b"]
assert new["I10"]["dependsOn"] == ["I09"]
assert set(new) - set(old) == {"I10a", "I10b"}
assert all(new[i] == task for i, task in old.items() if i != "I10")
for identifier, dependencies in (("I10a", ["I09"]), ("I10b", ["I10a"])):
    task = new[identifier]
    assert task["status"] == "pending" and task["batch"] == "B3"
    assert task["outcome"] and task["checks"] and task["dependsOn"] == dependencies
    assert task["planningRecord"] == report_path
assert api["selection"](after) == {"mode": "implement", "target": "I10a", "findingIds": []}
print("PASS implemented I09, two pending B3 vertical delivery children; next I10a")
print("PASS unrelated tasks/reviews/findings, runner/model/grants, authorization and release gates unchanged")

ledger_path = "docs/agent/coverage/ledger.json"
ledger_bytes = Path(ledger_path).read_bytes()
assert ledger_bytes == subprocess.check_output(["git", "show", report["startCommit"] + ":" + ledger_path])
items = [i for i in json.loads(ledger_bytes)["items"] if "I10" in i["implementationDependencies"]]
assert [i["id"] for i in items] == report["planning"]["sourceIds"]
for item in items:
    assertion = next(a for a in item["assertions"] if a["check"] == "I10.preparation")
    assert assertion["status"] == "pending" and item["completion"] == "unexecuted"
print(f"PASS {len(items)} exact I10 assignments pending; unchanged ledger SHA256 {hashlib.sha256(ledger_bytes).hexdigest()}")

for path in report["sourcePaths"] + report["planning"]["contractPaths"]:
    data = Path(path).read_bytes()
    assert data == subprocess.check_output(["git", "show", report["startCommit"] + ":" + path]), path
    print(f"UNCHANGED SOURCE {path} SHA256 {hashlib.sha256(data).hexdigest()}")

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
print("PASS exact owned state/report/evidence/support paths, JSON/source references and whitespace")
print("NOT RUN: product builds/tests/device journeys; planning-only, no runtime or UI-document edits")
