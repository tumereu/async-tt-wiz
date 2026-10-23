"""Run-owned read-only handoff, evidence, independent fixtures and exact cleanup audit."""
import base64
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess
import xml.etree.ElementTree as ET

PREFIX = "docs/agent/implementation-runs/20261023T000449Z-24e73bbc"
STATE = "docs/agent/implementation-state.json"


def command(*args):
    return subprocess.check_output(args, text=True).strip()


report = json.loads(Path(PREFIX + ".json").read_text())
before = json.loads(command("git", "show", report["startCommit"] + ":" + STATE))
after = json.loads(Path(STATE).read_text())
api = runpy.run_path("scripts/implementation-loop.py")
selection = {"mode": "implement", "target": "I11a", "findingIds": []}
assert command("git", "rev-parse", "HEAD") == report["startCommit"]
assert command("git", "branch", "--show-current") == "master"
assert not command("git", "diff", "--cached", "--name-only")
assert api["selection"](before) == selection
api["validate_state"](after)
api["validate_transition"](before, after, selection, report)
assert after["activeRun"] is None
assert after["runs"] == before["runs"] + [PREFIX + ".json"]
assert all(after[key] == value for key, value in before.items() if key not in {"runs", "tasks"})
for old, new in zip(before["tasks"], after["tasks"]):
    if old["id"] == "I11a":
        assert new["status"] == "implemented" and new["implementationRecord"] == PREFIX + ".json"
        assert all(new[key] == value for key, value in old.items() if key != "status")
    else:
        assert old == new
assert api["selection"](after) == {"mode": "implement", "target": "I11b", "findingIds": []}
print("PASS exact supplied clean baseline/master/index, D-304 authorization, I11a selection and driver state/transition validators")
print("PASS only I11a advances; I11/I11b/I12/B3, reviews/findings/model/grants/gates unchanged; next I11b")

assert subprocess.check_output(["python3", "tests/fixtures/random-v1.py"]) == Path(
    "protocol/src/commonTest/kotlin/org/asyncttwiz/protocol/RandomGolden.kt").read_bytes()
print("PASS literal Kotlin golden file matches independent Python hashlib/struct/UUID reference, pinned to preexisting specification blocks/seven-member orders")
subprocess.run(["python3", "scripts/coverage-ledger.py"], check=True)

# Decode lossless raw command-produced JUnit bytes, retaining the failed first test
# and overlaying only actual later targeted executions, not rewriting old results.
cases = {}
for suffix in ("-initial-junit.json", "-final-junit.json", "-model-junit.json", "-random-junit.json"):
    suites = json.loads(Path(PREFIX + suffix).read_text())
    for path, saved in suites.items():
        data = base64.b64decode(saved["base64"], validate=True)
        assert hashlib.sha256(data).hexdigest() == saved["sha256"]
        root = ET.fromstring(data)
        assert root.attrib == saved["tests"]
        assert int(root.attrib["skipped"]) == 0 and int(root.attrib["errors"]) == 0
        print("ACTUAL JUNIT", suffix, path, root.attrib)
        for case in root.findall("testcase"):
            key = (case.attrib["classname"], case.attrib["name"])
            cases[key] = case.find("failure") is None and case.find("error") is None and case.find("skipped") is None
assert len(cases) == 74 and all(cases.values()), [key for key, passed in cases.items() if not passed]
print("PASS composite 74 distinct evidenced test cases after actual targeted corrections: 31 shared, 19 server/PostgreSQL/socket, 24 client-host; no skipped tests")
print("LIMIT initial build/affected run were red diagnostics, not passing whole commands; exact targeted rechecks resolve the evidenced failures")
print("INPUT SUPERSESSION final Play CounterColor palette correction affects no initialized values (all maps empty); final PlayTest and both-target/server compilation retained. Final literal-boundary test-only expected-value change has its exact RandomV1Test rerun. Earlier socket/start assertions are reused for unchanged empty initial-state behavior, not called a new final binary run.")

logs = []
for suffix in ("-initial-build.json", "-affected-tests.json", "-targeted-recheck.json", "-final-model-check.json",
    "-literal-boundaries.json", "-handoff-diagnostic.json"):
    saved = json.loads(Path(PREFIX + suffix).read_text())
    assert hashlib.sha256(saved["stdout"].encode("utf-8")).hexdigest() == saved["sha256"]
    logs.append(saved["stdout"])
print("PASS lossless raw UTF-8 command-output envelopes/hashes; original jOOQ whitespace is preserved, not stripped to pass a hook")
owned = set(); cleaned = set(); volumes = set()
for log in logs:
    owned.update(re.findall(r"OWNED (?:codegen|integration) PostgreSQL container=([a-f0-9]{64})", log))
    cleaned.update(re.findall(r"CLEANUP removed owned (?:codegen|integration) container=([a-f0-9]{64})", log))
    for group in re.findall(r"CLEANUP removed owned .*? volumes=\[([^\]]*)\]", log):
        volumes.update(re.findall(r"[a-f0-9]{64}", group))
assert owned and owned == cleaned
remaining = set(command("docker", "container", "ls", "-a", "--no-trunc", "--format", "{{.ID}}").splitlines())
remaining_volumes = set(command("docker", "volume", "ls", "--format", "{{.Name}}").splitlines())
assert not (owned & remaining) and not (volumes & remaining_volumes)
print("PASS exact run-owned database cleanup", len(owned), "containers", len(volumes), "anonymous volumes; no broad cleanup or unrelated-resource mutation")

actual = set(command("git", "diff", "--name-only").splitlines()) | set(command("git", "ls-files", "--others", "--exclude-standard").splitlines())
assert actual == set(report["changedPaths"]), (actual ^ set(report["changedPaths"]))
runner = object.__new__(api["Runner"])
runner.root = Path.cwd(); runner.state_path = Path(STATE); runner.state_relative = STATE
runner.role_paths(before, selection, report, actual, PREFIX + ".json")
for check in report["checks"]:
    assert check["kind"] == "diagnostic" if check["exitCode"] else check["kind"] == "required"
    assert Path(check["evidence"]).is_file()
    for path in check.get("supportPaths", []):
        assert Path(path).is_file() and path in actual
for path in actual:
    data = Path(path).read_bytes()
    assert b"\r" not in data and (not data or data.endswith(b"\n")), path
    assert all(line.rstrip() == line for line in data.splitlines()), path
    if path.endswith(".json"):
        json.loads(data)
for name in ("server/GAMES.md", "server/README.md"):
    text = Path(name).read_text()
    assert len(re.findall(r"^```", text, re.MULTILINE)) % 2 == 0
    for target in re.findall(r"\]\(([^)]+)\)", text):
        if "://" not in target and not target.startswith("#"):
            assert (Path(name).parent / target.split("#")[0]).exists(), (name, target)
assert "## Initial play and readiness — I11a" in Path("server/GAMES.md").read_text()
subprocess.run(["git", "diff", "--check"], check=True)
print("PASS changed documentation links/fences, all owned JSON/references/whitespace, exact report changedPaths and implement-role paths")

inputs = [path for path in actual if not path.startswith("docs/agent/implementation-runs/") and path != STATE]
inputs += ["gradle/libs.versions.toml", "gradle.properties", "build.gradle.kts", "protocol/build.gradle.kts", "server/build.gradle.kts",
    "androidApp/build.gradle.kts", "core/gradle.lockfile", "protocol/gradle.lockfile", "server/gradle.lockfile", "androidApp/gradle.lockfile",
    "protocol/src/jvmMain/kotlin/org/asyncttwiz/protocol/DeckDigest.kt", "protocol/src/androidMain/kotlin/org/asyncttwiz/protocol/DeckDigest.kt",
    "androidApp/build/outputs/apk/debug/androidApp-debug.apk", "server/build/libs/server-0.1.0.jar", "protocol/build/libs/protocol-jvm-0.1.0.jar"]
for path in sorted(set(inputs)):
    print("FINAL INPUT", path, "SHA256", hashlib.sha256(Path(path).read_bytes()).hexdigest())
print("NOT CLAIMED native Ready/table acceptance (I11b), complete I12/two-Android/cancel/rematch/gameplay/history scenarios, independent B3 review, full-suite/final UI/compatibility/physical measurements or any external release gate")
