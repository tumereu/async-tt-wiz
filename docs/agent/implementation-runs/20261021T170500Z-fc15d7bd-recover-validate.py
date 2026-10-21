"""Validate the owner-requested save recovery without launching another agent."""
import ast
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import runpy
import subprocess
import sys
import tempfile
import tomllib
import xml.etree.ElementTree as ET


sys.dont_write_bytecode = True
PREFIX = "docs/agent/implementation-runs/20261021T170500Z-fc15d7bd"
REPORT = PREFIX + ".json"
OUTPUT = PREFIX + "-evidence/recovery-validation.json"
STATE = "docs/agent/implementation-state.json"


def git(*args):
    return subprocess.check_output(["git", *args])


report = json.loads(Path(REPORT).read_text())
before = json.loads(git("show", report["startCommit"] + ":" + STATE))
after = json.loads(Path(STATE).read_text())
api = runpy.run_path("scripts/implementation-loop.py")
choice = {"mode": "implement", "target": "I01a", "findingIds": []}
assert git("rev-parse", "HEAD").decode().strip() == report["startCommit"]
assert git("branch", "--show-current").decode().strip() == "master"
api["validate_state"](after)
api["validate_transition"](before, after, choice, report)
assert api["selection"](after) == {"mode": "implement", "target": "I01b", "findingIds": []}
assert after["activeRun"] is None
assert after["runs"] == before["runs"] + [REPORT]
for key in before.keys() - {"tasks", "runs"}:
    assert before[key] == after[key], key
old = {t["id"]: t for t in before["tasks"]}
new = {t["id"]: t for t in after["tasks"]}
assert old.keys() == new.keys()
assert new["I01a"]["status"] == "implemented"
assert all(new[key] == value for key, value in old.items() if key != "I01a")
assert before["authorization"] == after["authorization"] == "authorized"
assert before["runner"] == after["runner"]
old_permissions = json.loads(git("show", report["startCommit"] + ":docs/agent/implementation-permissions.json"))
new_permissions = json.loads(Path("docs/agent/implementation-permissions.json").read_text())
assert new_permissions == [p for p in old_permissions if p not in [
    {"action": "edit", "resource": "scripts/implementation-loop.py", "effect": "deny"},
    {"action": "edit", "resource": "docs/agent/implementation-agent.md", "effect": "deny"}]]
assert "D-305" in Path(before["authorizationRecord"]).read_text()
print("PASS only I01a completed; I01b next; review/release/model/authorization state retained")
print("PASS owner-authorized D-305 controller/prompt edit grants only; all other permissions retained")

actual = set(git("diff", "HEAD", "--name-only", "-z").decode().split("\0")) - {""}
actual.update(set(git("ls-files", "--others", "--exclude-standard", "-z").decode().split("\0")) - {""})
actual.add(OUTPUT)  # capture-check.py writes this after validation exits.
assert set(report["changedPaths"]) == actual
assert len(report["changedPaths"]) == len(actual)
assert report["outcome"] == "progress"
assert report["saveFailure"]["commitCreated"] is False
assert report["saveFailure"]["pushAttempted"] is False
assert report["saveRecovery"]["originalOutcome"] == "failed"
evidence_paths = {c["evidence"] for c in report["checks"]}
assert evidence_paths == {p.as_posix() for p in Path(OUTPUT).parent.glob("*.json")} | {OUTPUT}
for check in report["checks"]:
    assert check["kind"] in {"required", "diagnostic"}
    if check["kind"] == "required":
        assert check["exitCode"] == 0
    if check["evidence"] == OUTPUT:
        continue
    evidence = json.loads(Path(check["evidence"]).read_text())
    data = (evidence["output"].encode() if "output" in evidence
            else base64.b64decode(evidence["outputBase64"]))
    assert hashlib.sha256(data).hexdigest() == evidence["outputSha256"]
    assert evidence["exitCode"] == check["exitCode"]
print("PASS exact owned paths and lossless command evidence; original failed save retained as diagnostic")

for path in actual - {OUTPUT}:
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
            if target and "://" not in target and not target.startswith("mailto:"):
                assert (candidate.parent / target).exists(), (path, target)
subprocess.run(["bash", "-n", "scripts/save.sh"], check=True)
print("PASS edited Markdown local links and JSON/TOML/XML/Python/shell syntax")

# Use the real Git check on the exact prospective index, including text filters,
# without replacing the real index or staging incidental/unrelated paths.
with tempfile.TemporaryDirectory(prefix="atw-save-validation-", dir="/tmp/opencode") as temp:
    env = dict(os.environ, GIT_INDEX_FILE=str(Path(temp, "index")))
    subprocess.run(["git", "read-tree", "HEAD"], env=env, check=True)
    subprocess.run(["git", "add", "--all", "--", *sorted(actual - {OUTPUT})], env=env, check=True)
    subprocess.run(["git", "diff", "--cached", "--check"], env=env, check=True)
    wrapper = subprocess.check_output(["git", "show", ":gradlew.bat"], env=env)
    assert b"\r" not in wrapper and b"\n" in wrapper
    assert Path("gradlew.bat").read_bytes().replace(b"\r\n", b"\n") == wrapper
print("PASS unchanged Git staged-whitespace check; LF stored wrapper preserves CRLF working file")
