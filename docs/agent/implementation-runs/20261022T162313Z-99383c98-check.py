"""Run-owned evidence and handoff checks for independent B2-R01 verification."""
import base64
import hashlib
import json
from pathlib import Path
import runpy
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PREFIX = "docs/agent/implementation-runs/20261022T162313Z-99383c98"
START = "e419db2fec001ef7a02dc08b454751961160d891"
STATE = "docs/agent/implementation-state.json"


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT).decode().strip()


def load(path):
    return json.loads((ROOT / path).read_text())


print("Checking independent targeted B2-R01 verification evidence and handoff.", flush=True)
expected = {"AuthControllerTest": 12, "B2DeckIntegrationTest": 3, "DeckReauthenticationTest": 5}
records = []
for path in sorted((ROOT / "androidApp/build/test-results/testDebugUnitTest").glob("TEST-*.xml")):
    raw = path.read_bytes()
    root = ET.fromstring(raw)
    name = root.get("name").removeprefix("org.asyncttwiz.android.")
    assert name in expected and int(root.get("tests")) == expected.pop(name)
    assert root.get("failures") == root.get("errors") == root.get("skipped") == "0"
    records.append({"path": str(path.relative_to(ROOT)), "sha256": hashlib.sha256(raw).hexdigest(),
                    "base64": base64.b64encode(raw).decode(), "tests": int(root.get("tests")),
                    "testNames": [case.get("name") for case in root.findall("testcase")]})
    print("PASS fresh JUnit", name, root.get("tests"), "no skipped/failed/error cases")
assert not expected and sum(record["tests"] for record in records) == 20
(ROOT / (PREFIX + "-junit.json")).write_text(json.dumps(records, indent=2) + "\n")
tests = load(PREFIX + "-targeted-tests.json")
assert tests["exitCode"] == 0
assert hashlib.sha256(tests["output"].encode()).hexdigest() == tests["outputSha256"]
assert "> Task :androidApp:testDebugUnitTest\n" in tests["output"]
assert ":androidApp:testDebugUnitTest UP-TO-DATE" not in tests["output"]

# Tie manually inspected production transport/controller/UI branches to exact code.
for name in ["AuthController", "AuthViewModel", "DeckApi", "DeckImportController",
             "DeckLibraryController", "AccountEntry", "AuthenticatedShell", "AccountLocalData"]:
    path = "androidApp/src/main/kotlin/org/asyncttwiz/android/" + name + ".kt"
    raw = (ROOT / path).read_bytes()
    assert raw == subprocess.check_output(["git", "show", START + ":" + path], cwd=ROOT)
    print("INSPECTED unchanged production input", path, hashlib.sha256(raw).hexdigest())
wiring = (ROOT / "androidApp/src/main/kotlin/org/asyncttwiz/android/AuthViewModel.kt").read_text()
assert wiring.count("authenticationRequired = controller::authenticationRequired") == 2
assert "deckImport.connectSave(saveApi, controller::sessionToken" in wiring

before = json.loads(subprocess.check_output(["git", "show", START + ":" + STATE], cwd=ROOT))
after, report = load(STATE), load(PREFIX + ".json")
runner = runpy.run_path(str(ROOT / "scripts/implementation-loop.py"))
choice = {"mode": "verify", "target": "B2", "findingIds": ["B2-R01"]}
assert runner["selection"](before) == choice
runner["validate_state"](after)
runner["validate_transition"](before, after, choice, report)
assert after["tasks"] == before["tasks"] and after["activeRun"] is None
assert after["runs"] == before["runs"] + [PREFIX + ".json"]
assert all(before[key] == after[key] for key in before if key not in ("runs", "findings", "batches"))
assert next(batch for batch in after["batches"] if batch["id"] == "B2")["review"] == next(
    batch for batch in before["batches"] if batch["id"] == "B2")["review"]
assert runner["selection"](after) == {"mode": "implement", "target": "I09", "findingIds": []}
actual = set(git("diff", "--name-only", START).splitlines()) | set(
    git("ls-files", "--others", "--exclude-standard").splitlines())
assert actual == set(report["changedPaths"]), actual ^ set(report["changedPaths"])
assert all(path == STATE or path.startswith(PREFIX) for path in actual)
assert not git("diff", "--cached", "--name-only") and git("rev-parse", "HEAD") == START
for check in report["checks"]:
    assert check["kind"] == "required" and check["exitCode"] == 0
    assert (ROOT / check["evidence"]).stat().st_size > 0
    assert all((ROOT / support).stat().st_size > 0 for support in check.get("supportPaths", []))
for path in actual:
    text = (ROOT / path).read_text()
    assert text.endswith("\n") and all(line == line.rstrip() for line in text.splitlines()), path
    if path.endswith(".json"):
        json.loads(text)
subprocess.run(["git", "diff", "--check"], cwd=ROOT, check=True)
print("PASS exact verify-only transition and path boundary; B2-R01 verified; existing full B2 review retained.")
print("PASS authorization/model/grants/tasks/external gates unchanged; next selection I09, not executed here.")
print("PASS JSON/whitespace; no Markdown/UI-document links changed. No native/full-suite/release pass claimed.")
