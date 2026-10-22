"""Read-only validation of this planning-only I10a implementation split."""
import hashlib
import json
from pathlib import Path
import runpy
import subprocess


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


report_path = "docs/agent/implementation-runs/20261022T194819Z-c1033a39.json"
state_path = "docs/agent/implementation-state.json"
report = json.loads(Path(report_path).read_text())
before = json.loads(git("show", report["startCommit"] + ":" + state_path))
after = json.loads(Path(state_path).read_text())
api = runpy.run_path("scripts/implementation-loop.py")
choice = {"mode": "implement", "target": "I10a", "findingIds": []}
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
old = {t["id"]: t for t in before["tasks"]}
new = {t["id"]: t for t in after["tasks"]}
assert old["I09"]["status"] == "implemented"
assert set(new) - set(old) == {"I10a1", "I10a2"}
assert all(new[i] == task for i, task in old.items() if i != "I10a")
assert all(new["I10a"][key] == value for key, value in old["I10a"].items() if key != "status")
assert new["I10a"]["status"] == "in-progress"
assert new["I10a"]["children"] == report["planning"]["children"] == ["I10a1", "I10a2"]
for identifier, dependencies in (("I10a1", ["I09"]), ("I10a2", ["I10a1"])):
    task = new[identifier]
    assert task["status"] == "pending" and task["batch"] == "B3"
    assert task["outcome"] and task["checks"] and task["dependsOn"] == dependencies
    assert task["planningRecord"] == report_path
assert api["selection"](after) == {"mode": "implement", "target": "I10a1", "findingIds": []}
print("PASS exact baseline/master, D-304 authorization, original priority and existing driver state/transition checks")
print("PASS two pending B3 implementation children; next I10a1; original I10a outcome/checks preserved verbatim")
print("PASS unrelated tasks, B3 review, findings, model/grants/authorization and external gates unchanged")

for path in report["sourcePaths"] + report["planning"]["contractPaths"]:
    data = Path(path).read_bytes()
    assert data == subprocess.check_output(["git", "show", report["startCommit"] + ":" + path]), path
    print(f"UNCHANGED SOURCE {path} SHA256 {hashlib.sha256(data).hexdigest()}")
ledger = json.loads(Path("docs/agent/coverage/ledger.json").read_text())
items = [i for i in ledger["items"] if "I10" in i["implementationDependencies"]]
assert [i["id"] for i in items] == report["planning"]["sourceIds"]
for item in items:
    assertion = next(a for a in item["assertions"] if a["check"] == "I10.preparation")
    assert assertion["status"] == "pending" and item["completion"] == "unexecuted"
print(f"PASS {len(items)} exact I10 source assignments preserved, still pending/unexecuted")

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
print("PASS exact owned state/report/evidence/support paths, JSON/references and whitespace")
print("NOT RUN: runtime/build/device/full-suite checks; no product input or UI document changed")
