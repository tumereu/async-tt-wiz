#!/usr/bin/env python3
"""Check-only I01c2b handoff, retained command bytes, document links and exact cleanup."""
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess

RUN = "20261021T192847Z-0d517a87"
PREFIX = Path("docs/agent/implementation-runs") / RUN
REPORT = PREFIX.with_suffix(".json")
STATE = Path("docs/agent/implementation-state.json")
START = "e938470b11ced68862d4ab3336fbfe226f6ec8dd"


def command(*args):
    return subprocess.check_output(args, text=True).strip()


report = json.loads(REPORT.read_text())
state = json.loads(STATE.read_text())
before = json.loads(command("git", "show", f"{START}:{STATE}"))
assert report["runId"] == RUN and report["startCommit"] == START
assert report["sessionID"] == "ses_ea00ffc7dffe222Pw4DlANTI32"
assert report["mode"] == "implement" and report["target"] == "I01c2b"
assert report["outcome"] == "progress"
assert not report["findingIds"] and not report["newFindingIds"] and not report["reviewedUnitIds"]
assert state["activeRun"] is None
assert state["runs"] == before["runs"] + [REPORT.as_posix()]
for key in before.keys() - {"runs", "tasks"}:
    assert state[key] == before[key], key
for old, new in zip(before["tasks"], state["tasks"], strict=True):
    if old["id"] == "I01c2b":
        assert new["status"] == "implemented" and new["implementationRecord"] == REPORT.as_posix()
        for key in old.keys() - {"status"}:
            assert new[key] == old[key], key
    else:
        assert new == old, old["id"]
assert command("git", "diff", START, "--", "server/src/main/resources/db/migration") == ""
print("PASS only selected I01c2b implemented; parents, dependencies, group review, grants/model/authorization and release gates unchanged")

changed = set(command("git", "diff", START, "--name-only").splitlines())
changed.update(command("git", "ls-files", "--others", "--exclude-standard").splitlines())
changed.add((PREFIX / "unit-validation.json").as_posix())
assert changed == set(report["changedPaths"]), (changed - set(report["changedPaths"]), set(report["changedPaths"]) - changed)
assert len(report["changedPaths"]) == len(set(report["changedPaths"]))
allowed = {
    STATE.as_posix(), "server/README.md", "server/build.gradle.kts", "server/gradle.lockfile",
    "gradle/libs.versions.toml", "scripts/check-server-foundation.py", "scripts/check-server-dependencies.py",
    "server/src/main/java/org/asyncttwiz/server/database/DatabaseSettings.java",
    "server/src/main/java/org/asyncttwiz/server/database/OwnershipFailure.java",
    "server/src/main/java/org/asyncttwiz/server/ServiceSettings.java",
    "server/src/main/java/org/asyncttwiz/server/ServiceCommand.java",
    "server/src/main/java/org/asyncttwiz/server/ServiceRuntime.java",
    "server/src/main/java/org/asyncttwiz/server/SpringService.java",
    "server/src/main/resources/service-logback.xml",
    "server/src/test/kotlin/org/asyncttwiz/server/ServiceSettingsTest.kt",
    "server/src/test/kotlin/org/asyncttwiz/server/ServiceProcessTest.kt",
    "server/src/test/kotlin/org/asyncttwiz/server/ServiceRuntimeTest.kt",
    "server/src/test/kotlin/org/asyncttwiz/server/SpringStartupTest.kt",
}
for path in sorted(changed):
    file = Path(path)
    if file.exists():
        assert all(not line.endswith((b" ", b"\t")) for line in file.read_bytes().splitlines()), path
    assert path.startswith(PREFIX.as_posix()) or path in allowed, path
print("PASS exact owned changed-path set and whitespace across tracked/new files")

containers, volumes, process_ids = set(), set(), set()
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
    process_ids.update(re.findall(r"(?:CLEANUP|PASS actual) service pid=([0-9]+)", record["output"]))
    print("PASS retained", check["kind"], evidence.name, "exit=", record["exitCode"])
assert set(PREFIX.glob("*.json")) - {PREFIX / "unit-validation.json"} == {Path(check["evidence"]) for check in report["checks"]} - {PREFIX / "unit-validation.json"}
remaining = command("docker", "ps", "-a", "--format", "{{.ID}}").splitlines()
assert not any(owned.startswith(current) for owned in containers for current in remaining)
remaining_volumes = set(command("docker", "volume", "ls", "--format", "{{.Name}}").splitlines())
assert not (volumes & remaining_volumes)
assert containers and volumes and process_ids
for pid in process_ids:
    cmdline = Path("/proc") / pid / "cmdline"
    if cmdline.exists():
        assert b"org.asyncttwiz.server.ServiceCommand" not in cmdline.read_bytes(), pid
# Class-specific JDK inventory also covers any subprocess interrupted before its PID was printed.
assert "org.asyncttwiz.server.ServiceCommand" not in command(str(Path(os.environ["JAVA_HOME"]) / "bin/jcmd"), "-l")
print(f"PASS cleanup: {len(containers)} exact owned databases, {len(volumes)} volumes and {len(process_ids)} recorded service PIDs absent; no service-command JVM remains")

for markdown in [Path("server/README.md"), PREFIX / "scope.md", PREFIX / "diagnostics.md"]:
    for target in re.findall(r"\]\(([^)]+)\)", markdown.read_text()):
        if "://" not in target:
            target = target.split("#")[0]
            assert not target or (markdown.parent / target).exists(), (markdown, target)
print("PASS edited Markdown links; no UI document/publication change")
print("PASS I01c2b only; image, parents, complete product acceptance, independent review and release gates remain open")
