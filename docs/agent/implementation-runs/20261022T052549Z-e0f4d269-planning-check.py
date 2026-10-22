"""Read-only, run-owned I02 split validation; never starts a runner or product."""
import hashlib
import json
from pathlib import Path
import runpy
import subprocess


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


report_path = "docs/agent/implementation-runs/20261022T052549Z-e0f4d269.json"
state_path = "docs/agent/implementation-state.json"
report = json.loads(Path(report_path).read_text())
before = json.loads(git("show", report["startCommit"] + ":" + state_path))
after = json.loads(Path(state_path).read_text())
api = runpy.run_path("scripts/implementation-loop.py")
choice = {"mode": "implement", "target": "I02", "findingIds": []}
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
print("PASS exact supplied baseline/master, empty index, D-304 authorization and I02 priority")
print("PASS existing driver schema and planning-only transition helpers; no runner started")

assert after["activeRun"] is None
assert after["runs"] == before["runs"] + [report_path]
for key in before:
    if key not in {"tasks", "runs"}:
        assert before[key] == after[key], key
old = {t["id"]: t for t in before["tasks"]}
new = {t["id"]: t for t in after["tasks"]}
assert old["I01"]["status"] == "implemented"
assert new["I02"]["status"] == "in-progress"
assert new["I02"]["children"] == ["I02a", "I02b"]
assert set(new) - set(old) == {"I02a", "I02b"}
assert all(new[i] == t for i, t in old.items() if i != "I02")
for identifier, dependencies in (("I02a", ["I01"]), ("I02b", ["I02a"])):
    task = new[identifier]
    assert task["status"] == "pending" and task["batch"] == "B1"
    assert task["outcome"] and task["checks"] and task["dependsOn"] == dependencies
    assert task["planningRecord"] == report_path
assert api["selection"](after) == {"mode": "implement", "target": "I02a", "findingIds": []}
print("PASS exactly two same-B1 pending children; next selection I02a; no open-parent cycle")
print("PASS unrelated tasks, reviews, findings, model, authorization and release gates unchanged")

for path in report["sourcePaths"]:
    assert Path(path).is_file(), path
ledger_path = "docs/agent/coverage/ledger.json"
ledger_bytes = Path(ledger_path).read_bytes()
assert ledger_bytes == subprocess.check_output(["git", "show", report["startCommit"] + ":" + ledger_path])
ledger = json.loads(ledger_bytes)
items = [i for i in ledger["items"] if "I02" in i["implementationDependencies"]]
assert [i["id"] for i in items] == report["planning"]["sourceIds"]
for item in items:
    assertion = next(a for a in item["assertions"] if a["check"] == "I02.credentials")
    assert assertion["status"] == "pending" and item["completion"] == "unexecuted"
print(f"PASS {len(items)} exact I02 source IDs retained pending; ledger SHA256 {hashlib.sha256(ledger_bytes).hexdigest()}")
print("PASS source references exist; no Markdown/UI document edits or false feature/test completion")

actual = set(git("diff", "--name-only").splitlines()) | set(git("ls-files", "--others", "--exclude-standard").splitlines())
assert actual == set(report["changedPaths"]), (actual, report["changedPaths"])
runner = object.__new__(api["Runner"])
runner.root = Path.cwd()
runner.state_path = Path(state_path)
runner.state_relative = state_path
runner.role_paths(before, choice, report, actual, report_path)
for path in actual:
    assert Path(path).is_file()
    data = Path(path).read_bytes()
    assert b"\r" not in data and (not data or data.endswith(b"\n")), path
    assert all(line.rstrip() == line for line in data.splitlines()), path
subprocess.run(["git", "diff", "--check"], check=True)
print("PASS exact owned changed paths, run-owned check support, role boundary and whitespace")
print("NOT RUN: product builds/tests/device journeys; this is the pre-coding split only")
