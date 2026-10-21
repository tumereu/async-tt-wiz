"""Validate this unit's handoff and retained results, not independent B1 review."""
import base64
import fnmatch
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import xml.etree.ElementTree as ET

RUN = "20261021T223211Z-d5a50a9e"
PREFIX = "docs/agent/implementation-runs/" + RUN
START = "9b9ff6c2cf8f7f3e35fd7b522526a54cc4dcc486"
report = json.loads(Path(PREFIX + ".json").read_text())
state = json.loads(Path("docs/agent/implementation-state.json").read_text())
before = json.loads(subprocess.check_output(["git", "show", START + ":docs/agent/implementation-state.json"]))
assert report["startCommit"] == START and report["mode"] == "implement" and report["target"] == "I01e2"
assert report["runId"] == RUN and report["sessionID"] == "ses_e9f6812aaffeQ1bvG8Q31eEjaq"
assert report["findingIds"] == report["newFindingIds"] == report["reviewedUnitIds"] == []
assert report["outcome"] == "progress" and state["activeRun"] is None
assert state["runs"] == before["runs"] + [PREFIX + ".json"]
for key in before:
    if key not in {"tasks", "runs"}:
        assert state[key] == before[key], key
old_tasks = {t["id"]: t for t in before["tasks"]}
for task in state["tasks"]:
    old = old_tasks[task["id"]]
    if task["id"] == "I01e2":
        assert old["status"] == "in-progress" and task["status"] == "implemented"
        for key in old:
            if key != "status":
                assert task[key] == old[key], key
        assert task["implementationRecord"] == PREFIX + ".json"
        assert all(old_tasks[c]["status"] == "implemented" for c in task["children"])
    else:
        assert task == old, task["id"]
print("PASS only selected parent I01e2 closes after implemented children; ancestors/I01f/B1/findings/model/permissions/authorization/external gates unchanged")

actual = set(subprocess.check_output(["git", "diff", START, "--name-only"], text=True).splitlines())
actual |= set(subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard"], text=True).splitlines())
actual.add(PREFIX + "-boundary-check.json")
assert len(report["changedPaths"]) == len(set(report["changedPaths"]))
assert actual == set(report["changedPaths"]), actual ^ set(report["changedPaths"])
allowed = {"docs/agent/implementation-state.json", "e2e/README.md",
           "server/src/test/kotlin/org/asyncttwiz/server/ServiceProcessTest.kt",
           "server/src/test/kotlin/org/asyncttwiz/server/database/OwnedPostgres.kt",
           "server/src/test/kotlin/org/asyncttwiz/server/database/OwnedPostgresStartupTest.kt",
           "server/src/test/kotlin/org/asyncttwiz/server/database/WriterOwnershipTest.kt"}
assert all(path in allowed or path.startswith(PREFIX) for path in actual)
for path in actual - {PREFIX + "-boundary-check.json"}:
    raw = Path(path).read_bytes()
    raw.decode("utf-8")
    assert raw.endswith(b"\n"), path
    assert not any(line.endswith((b" ", b"\t")) for line in raw.splitlines()), path
    if path.endswith(".json"):
        json.loads(raw)
    if path.endswith(".py"):
        compile(raw, path, "exec")
    if path.endswith(".kt"):
        assert any(fnmatch.fnmatch(path, pattern) for pattern in state["runner"]["reviewTestPaths"]), path
for target in re.findall(r"\]\(([^)]+)\)", Path("e2e/README.md").read_text()):
    if not target.startswith(("http:", "https:", "#")):
        assert (Path("e2e") / target.split("#", 1)[0]).is_file(), target
assert not subprocess.check_output(["git", "diff", "--cached", "--name-only"], text=True).strip()
print("PASS exact owned path set, no staged unrelated work, test-only repair scope, unchanged reviewTestPaths, JSON/Python/UTF-8/whitespace/README links")

for check in report["checks"]:
    if check["evidence"] == PREFIX + "-boundary-check.json":
        continue
    value = json.loads(Path(check["evidence"]).read_text())
    assert value["exitCode"] == check["exitCode"]
    assert shlex.split(check["command"]) == value["command"]
    raw = value["output"].encode() if "output" in value else base64.b64decode(value["outputBase64"])
    assert hashlib.sha256(raw).hexdigest() == value["outputSha256"]
    if check["kind"] == "required":
        assert check["exitCode"] == 0
    assert check["evidence"] in actual
    for support in check.get("supportPaths", []):
        assert support in actual and support.startswith(PREFIX)
print("PASS actual command vectors, exits and lossless output hashes; every failed experiment remains diagnostic, all required checks pass")

def output(suffix):
    return json.loads(Path(PREFIX + "-" + suffix + ".json").read_text())["output"]

for label, probes in (("isolation-final-1", 2223), ("isolation-final-2", 2244)):
    text = output(label)
    for expected in ("PASS repeat reset 1", "PASS repeat reset 2", "active old PostgreSQL/SMTP connections terminated",
                     "PASS dirty failed environment -> explicit empty reset", "PASS shared foreign target refused",
                     f"{probes} continuous probes", "PASS Compose cleanup preserves separate Testcontainers",
                     "PASS separate Testcontainers startup/migration/cleanup preserves live Compose", "BUILD SUCCESSFUL",
                     "PASS 144 provider objects", "MEASURED-EGRESS", "MEASURED-TOPOLOGY"):
        assert expected in text, (label, expected)
    assert text.count("PASS public CLI failed reset is bounded/nonzero") == 2
    inputs = json.loads(next(line.removeprefix("QUALIFIED-INPUTS ") for line in text.splitlines() if line.startswith("QUALIFIED-INPUTS ")))
    for path, expected in inputs["hashes"].items():
        assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == expected, path
    inventories = [json.loads(line.split(" ", 1)[1]) for line in text.splitlines()
                   if line.startswith(("BASELINE-UNRELATED-INVENTORY ", "CURRENT-UNRELATED-INVENTORY "))]
    assert len(inventories) == 4 and all(i == inventories[0] for i in inventories)
assert "PASS real-image Compose lifecycle qualification" in output("lifecycle-final")
assert "PASS exact clean-build image/runtime/generated-schema identities" in output("final-identities")
print("PASS repeated post-repair integrated provision/restart/reset/failure/privacy/egress/ownership/cleanup with live separate Testcontainers; exact clean image inputs unchanged")

for label, expected_tests in (("regressions", 43), ("isolation-1", 1), ("isolation-2", 1),
                              ("isolation-diagnostic", 1), ("service-diagnostic", 37)):
    records = json.loads(Path(PREFIX + "-" + label + "-junit.json").read_text())
    tests = failures = 0
    for record in records:
        raw = record["xml"].encode("utf-8")
        assert hashlib.sha256(raw).hexdigest() == record["sha256"]
        suite = ET.fromstring(raw)
        assert suite.attrib == record["suite"]
        assert suite.attrib["skipped"] == suite.attrib["errors"] == "0"
        tests += int(suite.attrib["tests"])
        failures += int(suite.attrib["failures"])
    assert tests == expected_tests, (label, tests)
    assert failures == (1 if label.endswith("diagnostic") else 0), (label, failures)
assert output("loopback-service-final").count("PASS immediate JDBC round trip after actual startup iteration=") == 30
assert "DIAGNOSTIC fresh startup trials=30 recoveredTransientFailures=0" in output("startup-diagnostic")
assert output("startup-network-stress").count("PASS immediate JDBC round trip after actual startup iteration=") == 150
for label, count in (("controller-tests", 18), ("adapter-tests", 11), ("foundation-tests", 21)):
    text = output(label)
    assert f"Ran {count} tests" in text and "\nOK\n" in text and "skipped=" not in text
print("PASS actual lossless XML: final 37 server/6 shared + two isolation runs without skips; original failures preserved; 30 final loopback starts, deterministic readiness and 50 Python regressions")
print("PASS I01e2 implemented only; software verification, first group review and release readiness remain distinct; no UI-document or gate change")
