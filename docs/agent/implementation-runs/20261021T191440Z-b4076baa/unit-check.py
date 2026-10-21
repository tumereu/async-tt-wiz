#!/usr/bin/env python3
"""Check-only I01c2a handoff, local document links and exact owned-resource cleanup."""
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess

RUN = "20261021T191440Z-b4076baa"
PREFIX = Path("docs/agent/implementation-runs") / RUN
REPORT = PREFIX.with_suffix(".json")
STATE = Path("docs/agent/implementation-state.json")
START = "4b4ec73df3ec4cb64d3c8c6b8f4ec47949347dad"


def command(*args):
    return subprocess.check_output(args, text=True).strip()


report = json.loads(REPORT.read_text())
state = json.loads(STATE.read_text())
before = json.loads(command("git", "show", f"{START}:{STATE}"))
assert report["runId"] == RUN and report["startCommit"] == START
assert report["sessionID"] == "ses_ea01cea54ffe4Z3wrBtOW63Fjw"
assert report["mode"] == "implement" and report["target"] == "I01c2a"
assert report["outcome"] == "progress"
assert not report["findingIds"] and not report["newFindingIds"] and not report["reviewedUnitIds"]
assert state["activeRun"] is None
assert state["runs"] == before["runs"] + [REPORT.as_posix()]
for key in before.keys() - {"runs", "tasks"}:
    assert state[key] == before[key], key
for old, new in zip(before["tasks"], state["tasks"], strict=True):
    if old["id"] == "I01c2a":
        assert new["status"] == "implemented"
        assert new["implementationRecord"] == REPORT.as_posix()
        for key in old.keys() - {"status"}:
            assert new[key] == old[key], key
    else:
        assert new == old, old["id"]
assert command("git", "diff", START, "--", "server/src/main/resources/db/migration") == ""
print("PASS only I01c2a advanced; original outcome/checks/dependencies, ancestors, B1 review, runner/model/grants, findings and gates preserved")

changed = set(command("git", "diff", START, "--name-only").splitlines())
changed.update(command("git", "ls-files", "--others", "--exclude-standard").splitlines())
# The capture wrapper creates this current output only after this process returns.
changed.add((PREFIX / "unit-validation.json").as_posix())
assert changed == set(report["changedPaths"]), (changed - set(report["changedPaths"]), set(report["changedPaths"]) - changed)
assert len(report["changedPaths"]) == len(set(report["changedPaths"]))
for path in sorted(changed):
    file = Path(path)
    if file.exists():
        assert all(not line.endswith((b" ", b"\t")) for line in file.read_bytes().splitlines()), path
    assert path.startswith(PREFIX.as_posix()) or path in {
        STATE.as_posix(), "server/README.md", "scripts/check-server-foundation.py",
        "server/src/test/kotlin/org/asyncttwiz/server/database/OwnedPostgres.kt",
        "server/src/test/kotlin/org/asyncttwiz/server/database/WriterOwnershipTest.kt",
        "server/src/main/java/org/asyncttwiz/server/database/WriterOwnership.java",
        "server/src/main/java/org/asyncttwiz/server/database/OwnershipDatabase.java",
        "server/src/main/java/org/asyncttwiz/server/database/OwnershipFailure.java",
    }, path
print("PASS exact changed-path set and whitespace across tracked and new owned paths")

containers, volumes = set(), set()
for check in report["checks"]:
    evidence = Path(check["evidence"])
    assert evidence.parent == PREFIX
    if evidence.name == "unit-validation.json":
        continue
    record = json.loads(evidence.read_text())
    assert record["exitCode"] == check["exitCode"]
    assert shlex.split(check["command"]) == record["command"], check["command"]
    assert hashlib.sha256(record["output"].encode()).hexdigest() == record["outputSha256"]
    if check["kind"] == "required":
        assert check["exitCode"] == 0
    containers.update(re.findall(r"OWNED (?:integration|codegen) PostgreSQL container=([a-f0-9]{64})", record["output"]))
    for value in re.findall(r"CLEANUP removed owned (?:integration|codegen) container=[a-f0-9]+ volumes=\[([^]]*)\]", record["output"]):
        volumes.update(v.strip() for v in value.split(",") if v.strip())
    print("PASS retained", check["kind"], evidence, "exit=", record["exitCode"])
remaining = command("docker", "ps", "-a", "--format", "{{.ID}}").splitlines()
assert not any(owned.startswith(current) for owned in containers for current in remaining)
remaining_volumes = set(command("docker", "volume", "ls", "--format", "{{.Name}}").splitlines())
assert not (volumes & remaining_volumes)
assert containers and volumes
print(f"PASS exact resource cleanup: {len(containers)} owned database containers and {len(volumes)} anonymous volumes absent; unrelated resources untouched")

for markdown in [Path("server/README.md"), PREFIX / "scope.md"]:
    for target in re.findall(r"\]\(([^)]+)\)", markdown.read_text()):
        if "://" not in target:
            target = target.split("#")[0]
            assert not target or (markdown.parent / target).exists(), (markdown, target)
print("PASS edited Markdown local links; no UI-document edit/publication required")
print("PASS I01c2a handoff only; Spring/HTTP/process, image, parent qualification, independent B1 review and all release obligations remain pending")
