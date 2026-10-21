"""Run-owned documentation, handoff and retained-evidence validation (no product edits)."""
import base64
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess

ROOT = Path.cwd()
PREFIX = "docs/agent/implementation-runs/20261021T185205Z-6533f389"
REPORT = PREFIX + ".json"
report = json.loads(Path(REPORT).read_text())
state = json.loads(Path("docs/agent/implementation-state.json").read_text())
baseline = json.loads(subprocess.check_output(["git", "show", report["startCommit"] + ":docs/agent/implementation-state.json"]))
assert subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip() == report["startCommit"]
assert report["runId"] == "20261021T185205Z-6533f389" and report["target"] == "I01c1" and report["mode"] == "implement"
assert state["activeRun"] is None
assert state["runs"] == baseline["runs"] + [REPORT]
for key in ("authorization", "authorizationRecord", "runner", "mode", "externalReleaseGates", "findings", "batches"):
    assert state[key] == baseline[key], key
for task, original in zip(state["tasks"], baseline["tasks"], strict=True):
    if task["id"] == "I01c1":
        assert original["status"] == "pending" and task["status"] == "implemented"
        assert task["implementationRecord"] == REPORT
        assert all(next(t for t in state["tasks"] if t["id"] == dependency)["status"] == "implemented" for dependency in task["dependsOn"])
    else:
        assert task == original, task["id"]
print("PASS selected task only; parents/reviews/findings/authorization/runner/release gates retained")

checked = set()
for check in report["checks"]:
    path = Path(check["evidence"])
    assert path.as_posix().startswith(PREFIX + "/")
    if not path.exists():
        assert path.as_posix() == PREFIX + "/unit-validation.json", path
        continue  # capture-check writes this invocation's output after this script exits.
    data = json.loads(path.read_text())
    output = data["output"].encode() if "output" in data else base64.b64decode(data["outputBase64"])
    assert hashlib.sha256(output).hexdigest() == data["outputSha256"], path
    assert data["exitCode"] == check["exitCode"]
    assert shlex.split(check["command"]) == data["command"], path
    if check["kind"] == "required":
        assert check["exitCode"] == 0, path
    assert path.as_posix() in report["changedPaths"]
    checked.add(path.as_posix())
    print("PASS lossless retained", check["kind"], path, "exit=", check["exitCode"])
all_outputs = {path.as_posix() for path in Path(PREFIX).glob("*.json")}
assert all_outputs <= checked

for path in ("server/README.md",):
    content = Path(path).read_text()
    assert content.count("```") % 2 == 0
    for link in re.findall(r"\]\(([^)]+)\)", content):
        if "://" not in link and not link.startswith("#"):
            assert (Path(path).parent / link.split("#")[0]).is_file(), (path, link)
print("PASS touched Markdown local links/fences and JSON records")

actual = set(subprocess.check_output(["git", "diff", "--name-only"], text=True).splitlines())
actual |= set(subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard"], text=True).splitlines())
actual.add(PREFIX + "/unit-validation.json")
assert len(report["changedPaths"]) == len(set(report["changedPaths"]))
assert actual == set(report["changedPaths"]), (sorted(actual - set(report["changedPaths"])), sorted(set(report["changedPaths"]) - actual))
assert not subprocess.check_output(["git", "diff", "--cached", "--name-only"], text=True).strip()
print("PASS exact owned changed-path accounting and no pre-existing staged changes")
cleanup = json.loads(Path(PREFIX, "docker-cleanup-final.json").read_text())["output"]
assert "org.asyncttwiz.owner" not in cleanup and "org.testcontainers" not in cleanup
assert "9f71282671af task07-verification" in cleanup
print("PASS owned containers gone; original unrelated task07-verification container preserved")
