#!/usr/bin/env python3
"""Check-only parent handoff, retained results, exact cleanup and edited documents."""
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import xml.etree.ElementTree as ET

RUN = "20261021T195803Z-2deb1ecd"
PREFIX = Path("docs/agent/implementation-runs") / RUN
REPORT = PREFIX.with_suffix(".json")
STATE = Path("docs/agent/implementation-state.json")
START = "bb25687200efe0c454548f19a35f81c2c7203be2"


def command(*args):
    return subprocess.check_output(args, text=True).strip()


report = json.loads(REPORT.read_text())
state = json.loads(STATE.read_text())
before = json.loads(command("git", "show", f"{START}:{STATE}"))
assert report["runId"] == RUN and report["startCommit"] == START
assert report["sessionID"] == "ses_e9ff53240ffeQFdYKfbUV9CoSw"
assert report["mode"] == "implement" and report["target"] == "I01c2"
assert report["outcome"] == "progress"
assert not report["findingIds"] and not report["newFindingIds"] and not report["reviewedUnitIds"]
assert state["activeRun"] is None
assert state["runs"] == before["runs"] + [REPORT.as_posix()]
for key in before.keys() - {"runs", "tasks"}:
    assert state[key] == before[key], key
for old, new in zip(before["tasks"], state["tasks"], strict=True):
    if old["id"] == "I01c2":
        assert new["status"] == "implemented" and new["implementationRecord"] == REPORT.as_posix()
        assert new["qualification"] and new["buildNotes"] == "server/README.md"
        for key in old.keys() - {"status"}:
            assert new[key] == old[key], key
        for child_id in new["children"]:
            child = next(task for task in state["tasks"] if task["id"] == child_id)
            assert child["status"] == "implemented" and Path(child["implementationRecord"]).is_file()
            child_report = json.loads(Path(child["implementationRecord"]).read_text())
            assert child_report["target"] == child_id
            assert all(check["exitCode"] == 0 for check in child_report["checks"] if check["kind"] == "required")
    else:
        assert new == old, old["id"]
assert command("git", "diff", START, "--", "server/src", "server/build.gradle.kts", "server/gradle.lockfile", "gradle") == ""
print("PASS only selected I01c2 parent implemented after both children; product/build inputs unchanged")
print("PASS other tasks/batches/review coverage/findings/authorization/model/grants/external gates unchanged")

changed = set(command("git", "diff", START, "--name-only").splitlines())
changed.update(command("git", "ls-files", "--others", "--exclude-standard").splitlines())
changed.add((PREFIX / "unit-validation.json").as_posix())
assert changed == set(report["changedPaths"]), (changed - set(report["changedPaths"]), set(report["changedPaths"]) - changed)
assert len(report["changedPaths"]) == len(set(report["changedPaths"]))
for path in sorted(changed):
    assert path in {STATE.as_posix(), REPORT.as_posix(), "server/README.md"} or path.startswith(PREFIX.as_posix() + "/"), path
    if Path(path).exists():
        assert all(not line.endswith((b" ", b"\t")) for line in Path(path).read_bytes().splitlines()), path
print("PASS exact owned changed-path set and whitespace")

containers, cleaned, volumes, processes, reapers = set(), set(), set(), set(), set()
for check in report["checks"]:
    evidence = Path(check["evidence"])
    assert evidence.parent == PREFIX
    assert check["kind"] == "required" and check["exitCode"] == 0
    for support in check.get("supportPaths", []):
        assert Path(support).parent == PREFIX and support in changed and Path(support).is_file()
    if evidence.name == "unit-validation.json":
        continue  # The capture process retains this command's result after it exits.
    record = json.loads(evidence.read_text())
    assert record["exitCode"] == check["exitCode"]
    assert shlex.split(check["command"]) == record["command"], check["command"]
    assert hashlib.sha256(record["output"].encode()).hexdigest() == record["outputSha256"]
    output = record["output"]
    containers.update(re.findall(r"OWNED (?:integration|codegen) PostgreSQL container=([a-f0-9]{64})", output))
    cleaned.update(re.findall(r"CLEANUP removed owned (?:integration|codegen) container=([a-f0-9]{64})", output))
    for value in re.findall(r"CLEANUP removed owned (?:integration|codegen) container=[a-f0-9]+ volumes=\[([^]]*)\]", output):
        volumes.update(v.strip() for v in value.split(",") if v.strip())
    processes.update(re.findall(r"(?:CLEANUP|PASS actual) service pid=([0-9]+)", output))
    reapers.update(re.findall(r"Container testcontainers/ryuk:[^\n]* is starting: ([a-f0-9]{64})", output))
    print("PASS retained required check", evidence.name)
assert set(PREFIX.glob("*.json")) == {Path(check["evidence"]) for check in report["checks"]} - ({PREFIX / "unit-validation.json"} if not (PREFIX / "unit-validation.json").exists() else set())
assert containers == cleaned and containers and volumes and processes and reapers
remaining = command("docker", "ps", "-a", "--no-trunc", "--format", "{{.ID}}").splitlines()
assert not (set(remaining) & (containers | reapers))
remaining_volumes = set(command("docker", "volume", "ls", "--format", "{{.Name}}").splitlines())
assert not (volumes & remaining_volumes)
for pid in processes:
    cmdline = Path("/proc") / pid / "cmdline"
    assert not cmdline.exists() or b"org.asyncttwiz.server.ServiceCommand" not in cmdline.read_bytes(), pid
assert "org.asyncttwiz.server.ServiceCommand" not in command(str(Path(os.environ["JAVA_HOME"]) / "bin/jcmd"), "-l")
print(f"PASS cleanup: {len(containers)} owned PostgreSQL containers, {len(volumes)} volumes, {len(reapers)} recorded reapers and {len(processes)} service PIDs absent")

for name in ("clean-regressions.json", "repeated-clean-regressions.json"):
    output = json.loads((PREFIX / name).read_text())["output"]
    assert "BUILD SUCCESSFUL" in output and "PASS byte-identical independent generation" in output
    assert len(re.findall(r"^\w+Test > .* PASSED$", output, re.MULTILINE)) == 34
    times = [int(value) for value in re.findall(r"SIGTERM-ms=([0-9]+)", output)]
    assert len(times) == 10 and max(times) < 30000
    print(f"PASS {name}: 34 executed server tests; {len(times)} actual SIGTERM exits; maximum {max(times)} ms")

for module in ("core", "protocol"):
    reports = sorted(Path(module, "build/test-results/jvmTest").glob("TEST-*.xml"))
    assert reports
    count = 0
    for path in reports:
        text = path.read_text()
        root = ET.fromstring(text)
        assert all(int(root.attrib[key]) == 0 for key in ("skipped", "failures", "errors"))
        count += int(root.attrib["tests"])
        print("Actual shared JUnit report:", path)
        print(text)
    assert count > 0
    print(f"PASS {module} executed shared tests: {count}")

for markdown in [Path("server/README.md"), PREFIX / "scope.md", PREFIX / "results.md"]:
    for target in re.findall(r"\]\(([^)]+)\)", markdown.read_text()):
        if "://" not in target:
            target = target.split("#")[0]
            assert not target or (markdown.parent / target).exists(), (markdown, target)
print("PASS edited Markdown links; no UI document/publication change")
print("PASS I01c2 implemented only; image/ancestor integration/product/device/independent review/release gates open")
