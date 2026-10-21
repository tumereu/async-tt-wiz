"""Read-only I01b source/doc/transition/evidence boundary check; launches no driver."""
import ast
import base64
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess

REPORT = "docs/agent/implementation-runs/20261021T181243Z-6b896081.json"
SELF_OUTPUT = "docs/agent/implementation-runs/20261021T181243Z-6b896081-evidence/unit-validation.json"
STATE = "docs/agent/implementation-state.json"


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


report = json.loads(Path(REPORT).read_text())
before = json.loads(git("show", report["startCommit"] + ":" + STATE))
after = json.loads(Path(STATE).read_text())
choice = {"mode": "implement", "target": "I01b", "findingIds": []}
api = runpy.run_path("scripts/implementation-loop.py")
assert git("rev-parse", "HEAD") == report["startCommit"]
assert git("branch", "--show-current") == "master"
assert not git("diff", "--cached", "--name-only"), "Inspect unexpected staged work before saving"
assert before["authorization"] == "authorized" and before["mode"] == "active"
assert "D-304" in Path(before["authorizationRecord"]).read_text()
assert before["runner"]["model"] == "openai/gpt-6.1-sol#high"
assert api["selection"](before) == choice
api["validate_state"](after)
api["validate_transition"](before, after, choice, report)
assert after["activeRun"] is None
assert after["runs"] == before["runs"] + [REPORT]
for key in before:
    if key not in {"tasks", "runs"}:
        assert before[key] == after[key], key
old_tasks = {t["id"]: t for t in before["tasks"]}
new_tasks = {t["id"]: t for t in after["tasks"]}
assert old_tasks.keys() == new_tasks.keys()
assert new_tasks["I01b"]["status"] == "implemented"
assert all(new_tasks[i] == value for i, value in old_tasks.items() if i != "I01b")
assert not report["findingIds"] and not report["newFindingIds"] and not report["reviewedUnitIds"]
print("PASS exact supplied baseline/master, D-304 authorization, priority and pure driver state/transition validation")
print("PASS only I01b implemented; I01/other tasks, findings, independent coverage and all external gates unchanged")

actual = set(git("diff", "--name-only").splitlines())
actual.update(git("ls-files", "--others", "--exclude-standard").splitlines())
actual.add(SELF_OUTPUT)
assert set(report["changedPaths"]) == actual and len(report["changedPaths"]) == len(actual)
protected = ["AGENTS.md", "scripts/implementation-loop.py", "scripts/test-implementation-loop.py",
             "docs/agent/implementation-agent.md", "docs/agent/implementation-permissions.json",
             before["authorizationRecord"]]
for path in protected:
    assert path not in actual
    assert Path(path).read_bytes() == subprocess.check_output(["git", "show", report["startCommit"] + ":" + path])
assert not any(p.startswith("docs/design/") for p in actual)
assert all(p.startswith(("docs/agent/coverage/", "tests/", "docs/agent/implementation-runs/20261021T181243Z-6b896081"))
           or p in {STATE, "scripts/coverage-ledger.py", "scripts/synthetic-catalogs.py", "scripts/check-synthetic-catalogs.py"}
           for p in actual)
print("PASS exact owned changed-path inventory and unchanged authorization/runner/profile/UI-document boundaries")

evidence_paths = {c["evidence"] for c in report["checks"]}
assert evidence_paths == {p.as_posix() for p in Path(SELF_OUTPUT).parent.glob("*.json")} | {SELF_OUTPUT}
for check in report["checks"]:
    assert check["kind"] in {"required", "diagnostic"} and check["command"]
    if check["kind"] == "required":
        assert check["exitCode"] == 0
    if check["evidence"] == SELF_OUTPUT:
        continue
    evidence = json.loads(Path(check["evidence"]).read_text())
    data = evidence["output"].encode() if "output" in evidence else base64.b64decode(evidence["outputBase64"])
    assert hashlib.sha256(data).hexdigest() == evidence["outputSha256"]
    assert evidence["exitCode"] == check["exitCode"]
print("PASS run-owned actual outputs, exits and lossless SHA256 records; final required checks are passing")

for path in actual - {SELF_OUTPUT}:
    candidate = Path(path)
    if path.endswith(".json"):
        json.loads(candidate.read_text())
    elif path.endswith(".py"):
        ast.parse(candidate.read_text(), filename=path)
    if path.endswith(".md"):
        for link in re.findall(r"(?<!!)\[[^\]]*\]\(([^)]+)\)", candidate.read_text()):
            target = link.split("#", 1)[0]
            if target and "://" not in target and not target.startswith("mailto:"):
                assert (candidate.parent / target).exists(), (path, target)
    if candidate.suffix in {".json", ".py", ".md", ".sha256"}:
        for number, line in enumerate(candidate.read_bytes().splitlines(), 1):
            assert line.rstrip(b" \t") == line, (path, number, "trailing whitespace")
print("PASS changed Markdown local links, JSON/Python syntax and source/doc/evidence whitespace")

ledger = runpy.run_path("scripts/coverage-ledger.py")
data = json.loads(Path(ledger["LEDGER"]).read_text())
assert ledger["validate"](data) == {"P": 72, "T": 715, "F": 88}
assert not data["executions"] and all(i["completion"] == "unexecuted" for i in data["items"])
assert all(a["status"] == "pending" for i in data["items"] for a in i["assertions"])
assert report["sourceCoverage"]["dispositions"] == {kind: sum(i["disposition"] == kind for i in data["items"])
                                                       for kind in report["sourceCoverage"]["dispositions"]}
print("PASS all 875 primary definitions/task/check portions accounted for; zero product execution/pass claims")
print("PASS I01b handoff scope; next I01c and remaining feature/device/review obligations are not executed by this validator")
