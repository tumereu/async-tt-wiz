"""Read-only unit/doc/evidence checks. Never constructs or launches the driver."""
import ast
import base64
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess
import tomllib
import xml.etree.ElementTree as ET

REPORT = "docs/agent/implementation-runs/20261021T170500Z-fc15d7bd.json"
SELF_OUTPUT = "docs/agent/implementation-runs/20261021T170500Z-fc15d7bd-evidence/unit-validation.json"
STATE = "docs/agent/implementation-state.json"


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


report = json.loads(Path(REPORT).read_text())
before = json.loads(git("show", report["startCommit"] + ":" + STATE))
after = json.loads(Path(STATE).read_text())
choice = {"mode": "implement", "target": "I01a", "findingIds": []}
api = runpy.run_path("scripts/implementation-loop.py")
assert git("rev-parse", "HEAD") == report["startCommit"]
assert git("branch", "--show-current") == "master"
assert not git("diff", "--cached", "--name-only")
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
assert new_tasks["I01a"]["status"] == "implemented"
assert all(new_tasks[i] == value for i, value in old_tasks.items() if i != "I01a")
assert not report["findingIds"] and not report["newFindingIds"] and not report["reviewedUnitIds"]
print("PASS exact baseline/master, D-304, supplied priority and driver pure state/transition helpers")
print("PASS only I01a advanced; I01/other children, findings, review coverage and gates unchanged")

actual = set(git("diff", "--name-only").splitlines())
actual.update(git("ls-files", "--others", "--exclude-standard").splitlines())
actual.add(SELF_OUTPUT)  # capture-check.py writes it after this subprocess exits.
assert set(report["changedPaths"]) == actual
assert len(report["changedPaths"]) == len(actual)
protected = ["AGENTS.md", "scripts/implementation-loop.py", "scripts/test-implementation-loop.py",
             "docs/agent/implementation-agent.md", "docs/agent/implementation-permissions.json",
             before["authorizationRecord"]]
for path in protected:
    assert path not in actual
    assert Path(path).read_bytes() == subprocess.check_output(["git", "show", report["startCommit"] + ":" + path])
assert not any(p.startswith("docs/design/") for p in actual), "No UI/document publication edit in this unit"
print("PASS exact owned changed-path set; no runner/profile/authorization/UI packet changes")

evidence_paths = {c["evidence"] for c in report["checks"]}
assert evidence_paths == {p.as_posix() for p in Path(SELF_OUTPUT).parent.glob("*.json")} | {SELF_OUTPUT}
for check in report["checks"]:
    assert check["kind"] in {"required", "diagnostic"}
    assert check["command"]
    if check["kind"] == "required":
        assert check["exitCode"] == 0
    if check["evidence"] == SELF_OUTPUT:
        continue
    evidence = json.loads(Path(check["evidence"]).read_text())
    data = (evidence["output"].encode("utf-8") if "output" in evidence
            else base64.b64decode(evidence["outputBase64"]))
    assert hashlib.sha256(data).hexdigest() == evidence["outputSha256"]
    assert evidence["exitCode"] == check["exitCode"]
print("PASS all actual outputs retained losslessly with SHA256; red development attempts diagnostic only")

for path in actual - {SELF_OUTPUT}:
    candidate = Path(path)
    if path.endswith(".json"):
        json.loads(candidate.read_text())
    elif path.endswith(".py"):
        ast.parse(candidate.read_text(), filename=path)
    elif path.endswith(".toml"):
        tomllib.loads(candidate.read_text())
    elif path.endswith(".xml"):
        ET.parse(candidate)
    if path.endswith(".md"):
        for link in re.findall(r"(?<!!)\[[^\]]*\]\(([^)]+)\)", candidate.read_text()):
            target = link.split("#", 1)[0]
            if not target or "://" in target or target.startswith("mailto:"):
                continue
            assert (candidate.parent / target).exists(), (path, target)
    if path != "gradle/wrapper/gradle-wrapper.jar":
        # Preserve evidence bytes in JSON rather than discarding command-output whitespace.
        for number, line in enumerate(candidate.read_bytes().splitlines(), 1):
            assert line.rstrip(b" \t") == line, (path, number, "trailing whitespace")
print("PASS edited Markdown local links, JSON/TOML/XML/Python syntax and owned-file whitespace")

assert hashlib.sha256(Path("gradle/wrapper/gradle-wrapper.jar").read_bytes()).hexdigest() == \
    "7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d"
assert "84fbba45c7f4c64abc77460e1c00f541e9f960e3c7ed2538f1ede19eacd873ae" in \
    Path("gradle/wrapper/gradle-wrapper.properties").read_text()
assert Path("gradlew").stat().st_mode & 0o111
assert Path("scripts/gradle.sh").stat().st_mode & 0o111
print("PASS pinned wrapper checksum and executable entry points")
print("PASS next pending implementation would be I01b; this validator does not execute it")
