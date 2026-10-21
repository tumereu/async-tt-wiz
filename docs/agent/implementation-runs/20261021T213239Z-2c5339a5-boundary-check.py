"""Check this implementation handoff, documentation and retained evidence only."""
import base64
import copy
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path.cwd()
RUN = "20261021T213239Z-2c5339a5"
PREFIX = "docs/agent/implementation-runs/" + RUN
START = "d0b1c6ef4bf78c9bb2f89dba2a166270e7e62223"
report = json.loads(Path(PREFIX + ".json").read_text())
state = json.loads(Path("docs/agent/implementation-state.json").read_text())
before = json.loads(subprocess.check_output(["git", "show", START + ":docs/agent/implementation-state.json"]))
assert report["startCommit"] == START and report["mode"] == "implement" and report["target"] == "I01e2a"
assert report["findingIds"] == report["newFindingIds"] == report["reviewedUnitIds"] == []
assert state["activeRun"] is None
assert state["runs"] == before["runs"] + [PREFIX + ".json"]
for key in before:
    if key not in {"tasks", "runs"}:
        assert state[key] == before[key], key
old_tasks = {t["id"]: t for t in before["tasks"]}
for task in state["tasks"]:
    old = old_tasks[task["id"]]
    if task["id"] == "I01e2a":
        assert old["status"] == "pending" and task["status"] == "implemented"
        for key in old:
            if key != "status":
                assert old[key] == task[key], key
        assert task["implementationRecord"] == PREFIX + ".json"
    else:
        assert task == old, task["id"]
print("PASS only selected child implemented; original parent/next-child/group review/findings/model/permissions/authorization/release gates preserved")

assert len(report["changedPaths"]) == len(set(report["changedPaths"]))
actual = set(subprocess.check_output(["git", "diff", START, "--name-only"], text=True).splitlines())
actual |= set(subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard"], text=True).splitlines())
actual.add(PREFIX + "-boundary-check.json")  # The enclosing capture writes after this check returns.
assert actual == set(report["changedPaths"]), (actual ^ set(report["changedPaths"]))
for relative in report["changedPaths"]:
    path = Path(relative)
    if relative == PREFIX + "-boundary-check.json":
        continue  # This invocation's enclosing capture writes it after return.
    raw = path.read_bytes()
    assert raw.endswith(b"\n"), relative
    assert not any(line.endswith((b" ", b"\t")) for line in raw.splitlines()), relative
for target in re.findall(r"\]\(([^)]+)\)", Path("e2e/README.md").read_text()):
    if not target.startswith(("http:", "https:", "#")):
        assert (Path("e2e") / target.split("#", 1)[0]).is_file(), target
print("PASS exact owned changed-path set, UTF-8 text/newline/whitespace and README relative links")

for check in report["checks"]:
    if check["evidence"] == PREFIX + "-boundary-check.json":
        continue
    value = json.loads(Path(check["evidence"]).read_text())
    assert value["exitCode"] == check["exitCode"]
    raw = value["output"].encode() if "output" in value else base64.b64decode(value["outputBase64"])
    assert hashlib.sha256(raw).hexdigest() == value["outputSha256"]
    if check["kind"] == "required":
        assert check["exitCode"] == 0
    assert check["evidence"] in report["changedPaths"]
    for support in check.get("supportPaths", []):
        assert support in report["changedPaths"] and support.startswith(PREFIX)
final = json.loads(Path(PREFIX + "-compose-final.json").read_text())["output"]
inputs = json.loads(next(line.removeprefix("QUALIFIED-INPUTS ") for line in final.splitlines() if line.startswith("QUALIFIED-INPUTS ")))
for path, expected in inputs["hashes"].items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == expected, path
assert "PASS 144 provider objects/catalog/gzip/checksum/art hashes" in final
assert "PASS real-image Compose lifecycle qualification" in final
assert "unrelated inventory unchanged" in final
for suffix, count in (("unit-final", 13), ("adapter-regressions", 11), ("foundation-data", 21)):
    text = json.loads(Path(PREFIX + "-" + suffix + ".json").read_text())["output"]
    assert f"Ran {count} tests" in text and "\nOK\n" in text and "skipped=" not in text
print("PASS exact final runtime input hashes, retained command-output hashes/exit classifications; 13 controller, 11 adapter and 21 fixture/ledger tests, no skips")
print("PASS host infrastructure only; reset/Testcontainers isolation/I01f/parents/B1/release obligations remain open")
