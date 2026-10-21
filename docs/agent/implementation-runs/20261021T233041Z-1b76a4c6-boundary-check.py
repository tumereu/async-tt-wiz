"""Validate this parent implementation handoff, not an independent B1 review."""
import base64
import fnmatch
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import xml.etree.ElementTree as ET

RUN = "20261021T233041Z-1b76a4c6"
PREFIX = "docs/agent/implementation-runs/" + RUN
START = "0bfe353006e27624594c87ba870ad3ffad746783"
report = json.loads(Path(PREFIX + ".json").read_text())
state = json.loads(Path("docs/agent/implementation-state.json").read_text())
before = json.loads(subprocess.check_output(["git", "show", START + ":docs/agent/implementation-state.json"]))
assert report["runId"] == RUN and report["sessionID"] == "ses_e9f32838cffeKVb4xvm77Tqmh5"
assert report["mode"] == "implement" and report["target"] == "I01e" and report["startCommit"] == START
assert report["outcome"] == "progress" and state["activeRun"] is None
assert report["findingIds"] == report["newFindingIds"] == report["reviewedUnitIds"] == []
assert state["runs"] == before["runs"] + [PREFIX + ".json"]
for key in before:
    if key not in {"tasks", "runs"}:
        assert state[key] == before[key], key
old_tasks = {t["id"]: t for t in before["tasks"]}
for task in state["tasks"]:
    old = old_tasks[task["id"]]
    if task["id"] == "I01e":
        assert old["status"] == "in-progress" and task["status"] == "implemented"
        assert all(old_tasks[c]["status"] == "implemented" for c in task["children"])
        assert all(task[k] == v for k, v in old.items() if k != "status")
        assert task["implementationRecord"] == PREFIX + ".json"
    else:
        assert task == old, task["id"]
print("PASS only selected parent I01e closes after implemented children; ancestors/I01f/B1/findings/model/permissions/authorization/external gates unchanged")

actual = set(subprocess.check_output(["git", "diff", START, "--name-only"], text=True).splitlines())
actual |= set(subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard"], text=True).splitlines())
actual.add(PREFIX + "-boundary-check.json")
assert len(report["changedPaths"]) == len(set(report["changedPaths"]))
assert actual == set(report["changedPaths"]), actual ^ set(report["changedPaths"])
test_path = "server/src/test/kotlin/org/asyncttwiz/server/ServiceProcessTest.kt"
assert any(fnmatch.fnmatch(test_path, pattern) for pattern in state["runner"]["reviewTestPaths"])
allowed = {"docs/agent/implementation-state.json", "e2e/README.md", test_path}
assert all(p in allowed or p.startswith(PREFIX) for p in actual)
for p in actual - {PREFIX + "-boundary-check.json"}:
    raw = Path(p).read_bytes()
    raw.decode("utf-8")
    assert raw.endswith(b"\n") and not any(line.endswith((b" ", b"\t")) for line in raw.splitlines()), p
    if p.endswith(".json"):
        json.loads(raw)
    if p.endswith(".py"):
        compile(raw, p, "exec")
for target in re.findall(r"\]\(([^)]+)\)", Path("e2e/README.md").read_text()):
    if not target.startswith(("http:", "https:", "#")):
        assert (Path("e2e") / target.split("#", 1)[0]).is_file(), target
assert not subprocess.check_output(["git", "diff", "--cached", "--name-only"], text=True).strip()
print("PASS exact owned paths, approved test-only logging scope, unchanged runtime/build inputs; JSON/Python/UTF-8/whitespace and README links; no unrelated index work")

for check in report["checks"]:
    assert check["evidence"] in actual
    for support in check.get("supportPaths", []):
        assert support in actual and support.startswith(PREFIX)
    if check["evidence"] == PREFIX + "-boundary-check.json":
        continue
    value = json.loads(Path(check["evidence"]).read_text())
    assert value["exitCode"] == check["exitCode"]
    assert shlex.split(check["command"]) == value["command"]
    raw = value["output"].encode() if "output" in value else base64.b64decode(value["outputBase64"])
    assert hashlib.sha256(raw).hexdigest() == value["outputSha256"]
    if check["kind"] == "required":
        assert check["exitCode"] == 0
print("PASS actual command vectors, lossless command-output hashes, required exits0; failures remain explicit diagnostics")

def output(label):
    return json.loads(Path(PREFIX + "-" + label + ".json").read_text())["output"]

text = output("isolation")
for expected in ("PASS repeat reset 1", "PASS repeat reset 2", "active old PostgreSQL/SMTP connections terminated",
                 "PASS dirty failed environment -> explicit empty reset", "PASS shared foreign target refused",
                 "2254 continuous probes", "PASS Compose cleanup preserves separate Testcontainers",
                 "PASS separate Testcontainers startup/migration/cleanup preserves live Compose", "BUILD SUCCESSFUL",
                 "PASS 144 provider objects", "MEASURED-EGRESS", "MEASURED-TOPOLOGY"):
    assert expected in text, expected
assert text.count("PASS public CLI failed reset is bounded/nonzero") == 2
assert "PASS real-image Compose lifecycle qualification" in output("lifecycle")
assert "PASS clean locked image/runtime/generated-schema identities" in output("final")
assert output("service-final").count("PASS immediate JDBC round trip after actual startup iteration=") == 30
assert "37 tests completed, 1 failed" in output("service-regressions")
assert "0.155" in output("adapter-image-tests")
for label, count in (("controller-tests", 18), ("adapter-tests", 11), ("foundation-tests", 21), ("adapter-image-tests", 11)):
    assert f"Ran {count} tests" in output(label) and "\nOK\n" in output(label)
inventories = [json.loads(line.split(" ", 1)[1]) for line in output("remaining-sequence").splitlines()
               if line.startswith(("BASELINE-UNRELATED-INVENTORY ", "CURRENT-UNRELATED-INVENTORY "))]
assert len(inventories) == 16 and all(i == inventories[0] for i in inventories)
inputs = json.loads(next(line.split(" ", 1)[1] for line in output("final").splitlines() if line.startswith("FINAL-QUALIFIED-INPUTS ")))
for p, expected in inputs["hashes"].items():
    assert hashlib.sha256(Path(p).read_bytes()).hexdigest() == expected, p
print("PASS fresh integrated repeated resets/failures, private services/egress, all144 source hashes, live separate Testcontainers, both cleanup directions and exact unchanged image inputs")

for label, expected_tests, expected_failures in (("junit", 44, 0), ("diagnostic-junit", 43, 1)):
    records = json.loads(Path(PREFIX + "-" + label + ".json").read_text())
    tests = failures = 0
    for record in records:
        raw = record["xml"].encode()
        assert hashlib.sha256(raw).hexdigest() == record["sha256"]
        suite = ET.fromstring(raw)
        assert suite.attrib == record["suite"] and suite.attrib["skipped"] == suite.attrib["errors"] == "0"
        tests += int(suite.attrib["tests"])
        failures += int(suite.attrib["failures"])
    assert (tests, failures) == (expected_tests, expected_failures), (label, tests, failures)
print("PASS lossless retained XML:37 service +6 shared +1 isolation pass with no skips; original1 failure preserved, not claimed root-cause fixed")
print("PASS I01e implementation qualification only; I01/I01f, first independent B1 review, whole product scenarios and release readiness remain open")
