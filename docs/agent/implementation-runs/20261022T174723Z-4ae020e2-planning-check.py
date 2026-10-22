"""Read-only I09b service/native split validation, not product qualification."""
import hashlib
import json
from pathlib import Path
import runpy
import subprocess


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


report_path = "docs/agent/implementation-runs/20261022T174723Z-4ae020e2.json"
state_path = "docs/agent/implementation-state.json"
report = json.loads(Path(report_path).read_text())
before = json.loads(git("show", report["startCommit"] + ":" + state_path))
after = json.loads(Path(state_path).read_text())
api = runpy.run_path("scripts/implementation-loop.py")
choice = {"mode": "implement", "target": "I09b", "findingIds": []}
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
print("PASS supplied baseline/master, empty index, D-304 authorization and I09b priority")

assert after["activeRun"] is None
assert after["runs"] == before["runs"] + [report_path]
for key in before:
    if key not in {"tasks", "runs"}:
        assert before[key] == after[key], key
for identifier in ("B1", "B2"):
    batch = next(b for b in before["batches"] if b["id"] == identifier)
    assert batch["status"] == "reviewed" and api["review_complete"](batch)
old = {t["id"]: t for t in before["tasks"]}
new = {t["id"]: t for t in after["tasks"]}
assert old["I09a"]["status"] == "implemented"
assert new["I09b"] == {**old["I09b"], "status": "in-progress",
    "children": ["I09b1", "I09b2"], "splitRecord": report_path}
assert report["planning"]["children"] == new["I09b"]["children"]
assert set(new) - set(old) == {"I09b1", "I09b2"}
assert all(new[i] == task for i, task in old.items() if i != "I09b")
for identifier, dependencies in (("I09b1", ["I09a"]), ("I09b2", ["I09b1"])):
    task = new[identifier]
    assert task["status"] == "pending" and task["batch"] == "B3"
    assert task["outcome"] and task["checks"] and task["dependsOn"] == dependencies
    assert task["planningRecord"] == report_path
assert api["selection"](after) == {"mode": "implement", "target": "I09b1", "findingIds": []}
assert api["progress_view"](before) != api["progress_view"](after)
print("PASS existing state/transition guards; original I09b outcome/checks preserved; next I09b1")
print("PASS all unrelated tasks/reviews/findings/model/grants/authorization/release gates unchanged")

for path in report["sourcePaths"] + report["planning"]["contractPaths"]:
    data = Path(path).read_bytes()
    if path != state_path:
        assert data == subprocess.check_output(["git", "show", report["startCommit"] + ":" + path]), path
    print(f"SOURCE {hashlib.sha256(data).hexdigest()} {path}")
ledger_path = "docs/agent/coverage/ledger.json"
items = [i for i in json.loads(Path(ledger_path).read_text())["items"] if "I09" in i["implementationDependencies"]]
assert [i["id"] for i in items] == report["planning"]["sourceIds"]
for item in items:
    assertion = next(a for a in item["assertions"] if a["check"] == "I09.invitations")
    assert assertion["status"] == "pending" and item["completion"] == "unexecuted"
print(f"PASS {len(items)} unchanged I09 ledger assignments; planning is no product pass")

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
    if path.endswith(".json"):
        json.loads(data)
subprocess.run(["git", "diff", "--check"], check=True)
print("PASS exact owned planning/report/support paths, JSON/references and whitespace")
print("NOT RUN: product tests/builds/device journeys; no runtime/build/UI-document changes")
