"""Check only this run's state/report/docs/evidence; not independent B1 review."""
import base64
import fnmatch
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

RUN = "20261021T220006Z-199d4d9d"
PREFIX = "docs/agent/implementation-runs/" + RUN
START = "8613abcbaef284043b1b969bc1451f238cded87f"
report = json.loads(Path(PREFIX + ".json").read_text())
state = json.loads(Path("docs/agent/implementation-state.json").read_text())
before = json.loads(subprocess.check_output(["git", "show", START + ":docs/agent/implementation-state.json"]))
assert report["startCommit"] == START and report["mode"] == "implement" and report["target"] == "I01e2b"
assert report["findingIds"] == report["newFindingIds"] == report["reviewedUnitIds"] == []
assert state["activeRun"] is None
assert state["runs"] == before["runs"] + [PREFIX + ".json"]
for key in before:
    if key not in {"tasks", "runs"}:
        assert state[key] == before[key], key
old_tasks = {t["id"]: t for t in before["tasks"]}
for task in state["tasks"]:
    old = old_tasks[task["id"]]
    if task["id"] == "I01e2b":
        assert old["status"] == "pending" and task["status"] == "implemented"
        for key in old:
            if key != "status":
                assert task[key] == old[key], key
        assert task["implementationRecord"] == PREFIX + ".json"
    else:
        assert task == old, task["id"]
print("PASS only selected I01e2b advances; parents/I01f/B1 review/findings/model/permissions/authorization/external gates preserved")

assert len(report["changedPaths"]) == len(set(report["changedPaths"]))
actual = set(subprocess.check_output(["git", "diff", START, "--name-only"], text=True).splitlines())
actual |= set(subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard"], text=True).splitlines())
actual.add(PREFIX + "-boundary-check.json")
assert actual == set(report["changedPaths"]), actual ^ set(report["changedPaths"])
allowed = {"docs/agent/implementation-state.json", "e2e/README.md", "e2e/environment.py", "server/build.gradle.kts",
           "server/src/test/kotlin/org/asyncttwiz/server/database/ComposeIsolationTest.kt", "scripts/check-e2e-reset.py", "tests/test_e2e_environment.py"}
assert all(p in allowed or p.startswith(PREFIX) for p in actual)
for relative in actual - {PREFIX + "-boundary-check.json"}:
    raw = Path(relative).read_bytes()
    raw.decode("utf-8")
    assert raw.endswith(b"\n"), relative
    assert not any(line.endswith((b" ", b"\t")) for line in raw.splitlines()), relative
for target in re.findall(r"\]\(([^)]+)\)", Path("e2e/README.md").read_text()):
    if not target.startswith(("http:", "https:", "#")):
        assert (Path("e2e") / target.split("#", 1)[0]).is_file(), target
for path in ("server/src/test/kotlin/org/asyncttwiz/server/database/ComposeIsolationTest.kt", "tests/test_e2e_environment.py"):
    assert any(fnmatch.fnmatch(path, pattern) for pattern in state["runner"]["reviewTestPaths"])
print("PASS exact owned changed-path set; UTF-8/newline/whitespace/README links; new tests match unchanged approved test-only paths")

for check in report["checks"]:
    if check["evidence"] == PREFIX + "-boundary-check.json":
        continue
    value = json.loads(Path(check["evidence"]).read_text())
    assert value["exitCode"] == check["exitCode"]
    raw = value["output"].encode() if "output" in value else base64.b64decode(value["outputBase64"])
    assert hashlib.sha256(raw).hexdigest() == value["outputSha256"]
    if check["kind"] == "required":
        assert check["exitCode"] == 0
    assert check["evidence"] in actual
    for support in check.get("supportPaths", []):
        assert support in actual and support.startswith(PREFIX)
final = json.loads(Path(PREFIX + "-isolation-final.json").read_text())["output"]
inputs = json.loads(next(line.removeprefix("QUALIFIED-INPUTS ") for line in final.splitlines() if line.startswith("QUALIFIED-INPUTS ")))
for path, expected in inputs["hashes"].items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == expected, path
reset_inputs = json.loads(next(line.strip().removeprefix("QUALIFIED-RESET-INPUTS ") for line in final.splitlines() if line.strip().startswith("QUALIFIED-RESET-INPUTS ")))
for path, expected in reset_inputs.items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == expected, path
for expected in ("PASS repeat reset 1", "PASS repeat reset 2", "active old PostgreSQL/SMTP connections terminated",
                 "PASS dirty failed environment -> explicit empty reset", "PASS shared foreign target refused",
                 "2244 continuous probes", "PASS Compose cleanup preserves separate Testcontainers",
                 "PASS separate Testcontainers startup/migration/cleanup preserves live Compose", "BUILD SUCCESSFUL"):
    assert expected in final, expected
assert final.count("PASS public CLI failed reset is bounded/nonzero") == 2
snapshots = {line.split(" ", 1)[0]: json.loads(line.split(" ", 1)[1]) for line in final.splitlines()
             if line.startswith(("BEFORE-UNRELATED-INVENTORY ", "AFTER-UNRELATED-INVENTORY "))}
assert snapshots["BEFORE-UNRELATED-INVENTORY"] == snapshots["AFTER-UNRELATED-INVENTORY"]
print("PASS final exact runtime/fixture/checker inputs; repeated dirty/failure resets, old connections, both cleanup directions and unchanged unrelated inventory evidenced")

records = json.loads(Path(PREFIX + "-junit-results.json").read_text())
totals = {"isolation": 0, "service": 0, "shared": 0}
for record in records:
    raw = record["xml"].encode("utf-8")
    assert hashlib.sha256(raw).hexdigest() == record["sha256"]
    suite = ET.fromstring(raw)
    assert suite.attrib == record["suite"]
    assert all(suite.attrib[k] == "0" for k in ("skipped", "failures", "errors"))
    category = "isolation" if "/composeIsolationTest/" in record["source"] else "service" if record["source"].startswith("server/") else "shared"
    totals[category] += int(suite.attrib["tests"])
    if category == "isolation":
        original = json.loads(next(line.removeprefix("RETAINED-JUNIT ") for line in final.splitlines() if line.startswith("RETAINED-JUNIT ")))
        assert record["sha256"] == original["sha256"]  # Original XML retained losslessly, not whitespace-rewritten.
assert totals == {"isolation": 1, "service": 34, "shared": 6}, totals
for suffix, count in (("unit-final", 18), ("adapter-regressions", 11), ("foundation-data", 21)):
    text = json.loads(Path(PREFIX + "-" + suffix + ".json").read_text())["output"]
    assert f"Ran {count} tests" in text and "\nOK\n" in text and "skipped=" not in text
assert "PASS real-image Compose lifecycle qualification" in json.loads(Path(PREFIX + "-compose-regressions.json").read_text())["output"]
print("PASS retained command byte hashes; actual XML totals 1 isolation/34 server/6 shared with no skips; 18 controller/11 adapter/21 fixture-ledger tests and Compose regressions")
print("PASS implemented/verified/releasable remain distinct; parent integration/I01f/B1/full product and all external gates stay open")
