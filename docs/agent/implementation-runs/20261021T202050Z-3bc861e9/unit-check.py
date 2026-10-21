"""Check-only I01c parent transition, combined artifacts, evidence and documents."""
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import xml.etree.ElementTree as ET

DIRECTORY = Path(__file__).resolve().parent.relative_to(Path.cwd())
REPORT = DIRECTORY.with_suffix(".json")
STATE = Path("docs/agent/implementation-state.json")
report = json.loads(REPORT.read_text())
state = json.loads(STATE.read_text())


def output(*args):
    return subprocess.check_output(args, text=True).strip()


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


assert report["runId"] == "20261021T202050Z-3bc861e9"
assert report["startCommit"] == "8a0ed2e597aab68836e7f0ac65931aa0a458627d"
assert report["sessionID"] == "ses_e9fe056d1ffepD46ldFYqmFkTj"
assert report["target"] == "I01c" and report["mode"] == "implement" and report["outcome"] == "progress"
assert report["findingIds"] == report["newFindingIds"] == report["reviewedUnitIds"] == []
before = json.loads(output("git", "show", f"{report['startCommit']}:{STATE}"))
assert state["activeRun"] is None
assert state["runs"] == before["runs"] + [str(REPORT)]
for key in before.keys() - {"runs", "tasks"}:
    assert state[key] == before[key], key
for old, new in zip(before["tasks"], state["tasks"], strict=True):
    if old["id"] == "I01c":
        assert new["status"] == "implemented" and new["implementationRecord"] == str(REPORT)
        assert new["qualification"] and new["buildNotes"] == "server/README.md"
        for key in old.keys() - {"status"}:
            assert old[key] == new[key], key
    else:
        assert new == old, old["id"]


def check_children(task_id):
    task = next(task for task in state["tasks"] if task["id"] == task_id)
    assert task["status"] == "implemented"
    for child_id in task.get("children", []):
        child = next(task for task in state["tasks"] if task["id"] == child_id)
        path = Path(child["implementationRecord"])
        child_report = json.loads(path.read_text())
        assert child_report["target"] == child_id
        for check in child_report["checks"]:
            evidence = Path(check["evidence"])
            assert evidence.is_file()
            assert output("git", "hash-object", str(evidence)) == output("git", "rev-parse", f"{report['startCommit']}:{evidence}")
            if check["kind"] == "required":
                assert check["exitCode"] == 0
        print("PASS implemented child and preserved committed required/diagnostic evidence:", child_id)
        check_children(child_id)


check_children("I01c")
print("PASS only selected parent implemented; other tasks/batches/reviews/findings/model/authorization/grants/gates unchanged")

changed = set(output("git", "diff", "--name-only", report["startCommit"]).splitlines())
changed.update(output("git", "ls-files", "--others", "--exclude-standard").splitlines())
changed.add(str(DIRECTORY / "unit-validation.json"))
assert changed == set(report["changedPaths"])
assert len(changed) == len(report["changedPaths"])
for path in changed:
    assert path in {str(REPORT), str(STATE), "server/README.md"} or path.startswith(str(DIRECTORY) + "/"), path
print("PASS exact owned changed paths; no product/runtime/build input changed")

owned, cleaned, volumes, reapers = set(), set(), set(), set()
for check in report["checks"]:
    evidence = Path(check["evidence"])
    assert evidence.parent == DIRECTORY and str(evidence) in changed
    for support in check.get("supportPaths", []):
        assert Path(support).parent == DIRECTORY and support in changed and Path(support).is_file()
    if evidence.name == "unit-validation.json":
        continue  # capture-check.py writes the actual output after this process exits.
    record = json.loads(evidence.read_text())
    assert shlex.split(check["command"]) == record["command"]
    assert record["exitCode"] == check["exitCode"]
    assert hashlib.sha256(record["output"].encode()).hexdigest() == record["outputSha256"]
    assert check["kind"] == "required" and check["exitCode"] == 0
    text = record["output"]
    owned.update(re.findall(r"OWNED (?:integration|codegen) PostgreSQL container=([a-f0-9]{64})", text))
    cleaned.update(re.findall(r"CLEANUP removed owned (?:integration|codegen) container=([a-f0-9]{64})", text))
    reapers.update(re.findall(r"Container testcontainers/ryuk:[^\n]* is starting: ([a-f0-9]{64})", text))
    for value in re.findall(r"CLEANUP removed owned (?:integration|codegen) container=[a-f0-9]+ volumes=\[([^]]*)\]", text):
        volumes.update(v.strip() for v in value.split(",") if v.strip())
    print("PASS retained required command:", check["command"])
assert owned == cleaned and owned and volumes and reapers
assert not ((owned | reapers) & set(output("docker", "ps", "-aq", "--no-trunc").split()))
assert not (volumes & set(output("docker", "volume", "ls", "-q").split()))
print(f"PASS exact owned PostgreSQL/codegen cleanup: {len(owned)} containers, {len(volumes)} volumes, {len(reapers)} reapers")

build_output = json.loads((DIRECTORY / "clean-image-build.json").read_text())["output"]
build = json.loads(build_output.split("server/build/application-image.json ", 1)[1])
assert build == json.loads(Path("server/build/application-image.json").read_text())
assert build["sourceCommit"] == report["startCommit"] and build["platform"] == "linux/amd64"
assert "@sha256:" in build["base"]
assert build["dockerfileSha256"] == sha(Path("server/Dockerfile"))
actual_jars = {p.name: sha(p) for p in Path("server/build/install/server/lib").glob("*.jar")}
assert actual_jars == build["libraries"]
assert build["distributionSha256"] == hashlib.sha256(json.dumps(actual_jars, sort_keys=True).encode()).hexdigest()
generated = Path("server/build/generated-src/jooq")
assert {str(p.relative_to(generated)): sha(p) for p in generated.rglob("*.java")} == build["generatedSchema"]
print("PASS tested installed distribution and generated PostgreSQL bindings match every image-build payload hash")

regressions = json.loads((DIRECTORY / "service-regressions.json").read_text())["output"]
assert "BUILD SUCCESSFUL" in regressions and "PASS byte-identical independent generation" in regressions
assert len(re.findall(r"^\w+Test > .* PASSED$", regressions, re.M)) == 34
times = [int(v) for v in re.findall(r"SIGTERM-ms=([0-9]+)", regressions)]
assert len(times) == 10 and max(times) < 30000
print(f"PASS 34 executed server tests; ten actual SIGTERM exits, max {max(times)}ms")
for module, task, expected in (("core", "jvmTest", 3), ("protocol", "jvmTest", 3), ("server", "test", 34)):
    count = 0
    for path in sorted(Path(module, "build/test-results", task).glob("TEST-*.xml")):
        text = path.read_text()
        root = ET.fromstring(text)
        assert all(int(root.attrib[k]) == 0 for k in ("skipped", "errors", "failures"))
        count += int(root.attrib["tests"])
        if module != "server":  # server XML is already preserved by check-server-foundation.py.
            print("Actual shared JUnit XML:", path, text)
    assert count == expected, (module, count)

for name in ("image-lifecycle.json", "repeated-image-lifecycle.json"):
    text = json.loads((DIRECTORY / name).read_text())["output"]
    assert "Ran 4 tests" in text and "\nOK\n" in text and build["imageId"] in text
    assert text.count("PASS exact owned containers/network/volume/temporary secrets removed") == 4
    assert "PASS unrelated baseline resources left intact" in text
    assert "PASS exported actual image contains exactly the locked runtime JAR hashes" in text
    assert "PASS image single-writer takeover" in text and "Temurin-21.0.12.1+1" in text
    times = [float(v) for v in re.findall(r"actual image SIGTERM exited 143 in ([0-9.]+)s", text)]
    assert len(times) == 10 and max(times) < 30
    print(f"PASS {name}: four scenarios; ten actual PID1 SIGTERM exits, max {max(times)}s")

for markdown in (Path("server/README.md"), DIRECTORY / "scope.md", DIRECTORY / "results.md"):
    for target in re.findall(r"\]\(([^)]+)\)", markdown.read_text()):
        if "://" not in target and not target.startswith("#"):
            assert (markdown.parent / target.split("#")[0]).exists(), (markdown, target)
print("PASS edited Markdown links; no UI document/publication changes")
print("PASS I01c implemented only; I01/B1/product/device/arm64/release obligations remain open")
