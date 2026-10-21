"""Validate this unit's documented, retained and role-scoped handoff."""
import fnmatch
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import xml.etree.ElementTree as ET

directory = Path(__file__).resolve().parent.relative_to(Path.cwd())
report_path = directory.with_suffix(".json")
report = json.loads(report_path.read_text())
state_path = Path("docs/agent/implementation-state.json")
state = json.loads(state_path.read_text())
baseline = json.loads(subprocess.check_output(["git", "show", report["startCommit"] + ":" + str(state_path)], text=True))
assert state["activeRun"] is None
assert state["runs"] == baseline["runs"] + [str(report_path)]
assert state["externalReleaseGates"] == baseline["externalReleaseGates"]
for key in ("runner", "authorization", "authorizationRecord", "mode", "batches", "findings"):
    assert state[key] == baseline[key], key
for task, before in zip(state["tasks"], baseline["tasks"], strict=True):
    if task["id"] == "I01c3":
        assert task["status"] == "implemented"
        assert task["implementationRecord"] == str(report_path)
        assert task["outcome"] == before["outcome"] and task["checks"] == before["checks"]
    else:
        assert task == before, task["id"]
assert report["findingIds"] == report["newFindingIds"] == report["reviewedUnitIds"] == []
assert report["mode"] == "implement" and report["target"] == "I01c3" and report["outcome"] == "progress"
assert any(fnmatch.fnmatchcase("tests/server/image_lifecycle.py", pattern)
           for pattern in state["runner"]["reviewTestPaths"])

for check in report["checks"]:
    evidence = Path(check["evidence"])
    if evidence.name == "unit-validation.json":
        continue  # This actual command's output is written by capture.py on exit.
    assert evidence.parent == directory and evidence.is_file(), evidence
    retained = json.loads(evidence.read_text())
    assert shlex.split(check["command"]) == retained["command"], evidence
    assert check["exitCode"] == retained["exitCode"]
    assert retained["outputSha256"] == hashlib.sha256(retained["output"].encode()).hexdigest()
    if check["kind"] == "required":
        assert check["exitCode"] == 0
    for support in check.get("supportPaths", []):
        assert Path(support).parent == directory and Path(support).is_file()

builds = []
for name in ("clean-image-build.json", "repeated-clean-image-build.json"):
    output = json.loads((directory / name).read_text())["output"]
    builds.append(json.loads(output.split("server/build/application-image.json ", 1)[1]))
for key in ("libraries", "distributionSha256", "generatedSchema", "base", "platform", "dockerfileSha256"):
    assert builds[0][key] == builds[1][key], key
assert builds[1] == json.loads(Path("server/build/application-image.json").read_text())
assert builds[1]["dockerfileSha256"] == hashlib.sha256(Path("server/Dockerfile").read_bytes()).hexdigest()
assert "@sha256:" in builds[1]["base"] and builds[1]["platform"] == "linux/amd64"
for name in ("image-lifecycle.json", "repeated-image-lifecycle.json"):
    output = json.loads((directory / name).read_text())["output"]
    assert "Ran 4 tests" in output and "\nOK\n" in output
    assert builds[1]["imageId"] in output
    identity = json.loads(next(line.split("IMAGE IDENTITY ", 1)[1] for line in output.splitlines() if line.startswith("IMAGE IDENTITY ")))
    assert identity["descriptor"]["mediaType"] == "application/vnd.oci.image.index.v1+json"
    assert "Temurin-21.0.12.1+1" in output
    assert output.count("PASS exact owned containers/network/volume/temporary secrets removed") == 4
    assert "PASS unrelated baseline resources left intact" in output
    assert "PASS exported actual image contains exactly the locked runtime JAR hashes" in output
    assert "PASS image single-writer takeover" in output

count = 0
for path in Path("server/build/test-results/test").glob("TEST-*.xml"):
    root = ET.parse(path).getroot()
    assert all(int(root.attrib[key]) == 0 for key in ("errors", "failures", "skipped"))
    count += int(root.attrib["tests"])
assert count == 34, count

for markdown in (Path("server/README.md"), directory / "scope.md", directory / "results.md"):
    for target in re.findall(r"\]\(([^)]+)\)", markdown.read_text()):
        if "://" not in target and not target.startswith("#"):
            assert (markdown.parent / target.split("#")[0]).exists(), (markdown, target)

changed = set(subprocess.check_output(["git", "diff", "--name-only", report["startCommit"]], text=True).splitlines())
changed.update(subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard"], text=True).splitlines())
changed.add(str(directory / "unit-validation.json"))
assert changed == set(report["changedPaths"]), (changed - set(report["changedPaths"]), set(report["changedPaths"]) - changed)
print("PASS repeated clean locked image payload/schema hashes match; OCI identity retained; two complete four-scenario image suites")
print("PASS 34 actual service/database regressions; no skips/errors/failures; same normal distribution")
print("PASS owned scope/state/report/evidence/support paths; authorization/grants/gates/review/other tasks unchanged; markdown links valid")
