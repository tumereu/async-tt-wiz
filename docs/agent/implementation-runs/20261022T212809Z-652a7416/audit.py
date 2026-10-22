"""Read-only scope/evidence/state audit; not a new execution of the affected tests."""
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess
import xml.etree.ElementTree as ET

BASE = Path("docs/agent/implementation-runs/20261022T212809Z-652a7416")
START = "b1340f1eba27967e7b271178f1688a5b63f8a271"
STATE = "docs/agent/implementation-state.json"
REPORT = str(BASE) + ".json"


def git(*args):
    return subprocess.check_output(["git", *args])


def load(path):
    return json.loads(Path(path).read_text())


def sha(data):
    return hashlib.sha256(data).hexdigest()


report = load(REPORT)
before = json.loads(git("show", START + ":" + STATE))
after = load(STATE)
api = runpy.run_path("scripts/implementation-loop.py")
choice = dict(mode="implement", target="I10b1", findingIds=[])
assert git("rev-parse", "HEAD").decode().strip() == START
assert git("branch", "--show-current").decode().strip() == "master"
assert not git("diff", "--cached", "--name-only").strip()
assert before["mode"] == "active" and before["authorization"] == "authorized"
assert before["runner"]["model"] == "openai/gpt-6.1-sol#high"
assert "D-304" in Path(before["authorizationRecord"]).read_text()
assert api["selection"](before) == choice
api["validate_state"](after)
api["validate_transition"](before, after, choice, report)
old = {task["id"]: task for task in before["tasks"]}
new = {task["id"]: task for task in after["tasks"]}
assert new["I10b1"]["status"] == "implemented" and new["I10b1"]["implementationRecord"] == REPORT
assert all(new[key] == value for key, value in old.items() if key != "I10b1")
assert after["runs"] == before["runs"] + [REPORT] and after["activeRun"] is None
assert all(after[key] == value for key, value in before.items() if key not in ("tasks", "runs"))
assert api["selection"](after) == dict(mode="implement", target="I10b2", findingIds=[])
print("PASS D-304/exact high model/baseline/priority; only I10b1 implemented; I10b2 next, parents/B3/reviews/gates unchanged")

outputs = {}
for name, expected in (("affected-tests", 1), ("rollback-recheck", 0), ("controller-recheck", 0)):
    record = load(BASE / (name + ".json"))
    assert record["exitCode"] == expected
    assert sha(record["output"].encode()) == record["outputSha256"]
    outputs[name] = record["output"]
    print("COMMAND", name, "exitCode", expected, "outputSha256", record["outputSha256"])
assert "BUILD SUCCESSFUL" in outputs["rollback-recheck"] and "BUILD SUCCESSFUL" in outputs["controller-recheck"]
assert "PASS normal Android runtime has no mandatory GMS/Firebase dependency" in outputs["affected-tests"]

cases = {}
initial_failures = set()
for phase in ("initial", "rollback-recheck", "controller-recheck"):
    records = load(BASE / (phase + "-junit.json"))
    for item in records:
        assert sha(item["xml"].encode()) == item["sha256"]
        root = ET.fromstring(item["xml"])
        assert not list(root.iter("skipped")) and not list(root.iter("error"))
        for case in root.iter("testcase"):
            key = case.attrib["classname"], case.attrib["name"]
            failed = case.find("failure") is not None
            if phase == "initial" and failed:
                initial_failures.add(key)
                assert "NOT_OWNER" in case.find("failure").attrib["message"]
            else:
                assert not failed
            cases[key] = (phase, not failed)
assert len(initial_failures) == 1
assert all(passed for phase, passed in cases.values())
assert all(cases[key] == ("rollback-recheck", True) for key in initial_failures)
for module in ("protocol", "server", "android"):
    selected = [(key, phase) for key, (phase, passed) in cases.items() if f".{module}." in key[0]]
    print("PASS retained final", module, len(selected), "distinct cases")
    for (class_name, name), phase in sorted(selected):
        print("CASE", phase, class_name, name)
print("PASS all", len(cases), "distinct affected cases, no skips; initial overall red is diagnostic, corrected rollback test alone rerun")
print("SCOPE final controller recheck covers queue-count surface, pending-selection/transfer composition and different-account clear; initial unchanged session/pure/socket/partial tests reused, not newly rerun")

text = outputs["affected-tests"] + outputs["rollback-recheck"]
containers = set(re.findall(r"(?:container=|is starting: )([a-f0-9]{64})", text))
volumes = set(re.findall(r"volumes=\[([a-f0-9]{64})\]", text))
assert len(containers) >= 4 and len(volumes) >= 4
for kind, identifiers in (("container", containers), ("volume", volumes)):
    for identifier in sorted(identifiers):
        command = ["docker", "inspect", "--type", kind, identifier] if kind == "container" else ["docker", "volume", "inspect", identifier]
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        assert result.returncode != 0 and (b"No such" in result.stdout or b"no such" in result.stdout), result.stdout
        print("CLEANUP_ABSENT", kind, identifier)
print("PASS exact owned test containers/anonymous volumes absent; no device, Compose deployment or unrelated-resource cleanup performed")

ledger = Path("docs/agent/coverage/ledger.json")
assert ledger.read_bytes() == git("show", START + ":" + str(ledger))
items = [item for item in load(ledger)["items"] if "I10" in item["implementationDependencies"]]
assert [item["id"] for item in items] == report["sourceIds"]
assert all(item["completion"] == "unexecuted" for item in items)
print("PASS", len(items), "source assignments retained; partial transfer assertions do not fabricate full native/Ready/start scenario completion")

inputs = [Path(name) for name in report["changedPaths"] if name.endswith(".kt")]
inputs += [Path("server/src/main/kotlin/org/asyncttwiz/server/game/PreparationStore.kt"),
    Path("server/src/main/resources/db/migration/V8__prepared_deck_copies.sql"),
    Path("androidApp/build/outputs/apk/debug/androidApp-debug.apk")]
inputs += sorted(Path(".").glob("**/gradle.lockfile"))
inputs += sorted(Path(".").glob("**/build.gradle.kts"))
inputs += [Path("gradle/libs.versions.toml"), Path("gradle/wrapper/gradle-wrapper.properties")]
identities = {str(path): {"sha256": sha(path.read_bytes()), "bytes": path.stat().st_size} for path in inputs}
(BASE / "input-identities.json").write_text(json.dumps(identities, indent=2) + "\n")
for name, identity in identities.items():
    print("INPUT", name, "SHA256", identity["sha256"], "BYTES", identity["bytes"])
print("PASS final source/build/lock/APK identities; unchanged V8 compact range format tested through actual persistence/restart, no schema migration")

# Applicable documentation checks: newly edited Markdown links/fences, JSON and whitespace.
for name in report["changedPaths"]:
    if not name.endswith(".md"):
        continue
    path = Path(name); text = path.read_text()
    assert len(re.findall(r"^```", text, re.M)) % 2 == 0
    for target in re.findall(r"\[[^\]]+\]\(([^)]+)\)", text):
        if "://" not in target and not target.startswith("#"):
            assert (path.parent / target.split("#")[0]).exists(), (name, target)
actual = set(git("diff", "--name-only").decode().splitlines()) | set(git("ls-files", "--others", "--exclude-standard").decode().splitlines())
assert actual == set(report["changedPaths"]), (actual, report["changedPaths"])
runner = object.__new__(api["Runner"])
runner.root = Path.cwd(); runner.state_path = Path(STATE); runner.state_relative = STATE
runner.role_paths(before, choice, report, actual, REPORT)
for check in report["checks"]:
    assert check["kind"] == "diagnostic" or check["exitCode"] == 0
    assert Path(check["evidence"]).is_file()
    assert all(Path(name).is_file() and name in actual for name in check.get("supportPaths", []))
for name in actual - {str(BASE / "audit.txt")}:
    data = Path(name).read_bytes()
    assert b"\r" not in data and data.endswith(b"\n"), name
    assert all(line.rstrip() == line for line in data.splitlines()), name
    if name.endswith(".json"):
        json.loads(data)
subprocess.run(["git", "diff", "--check"], check=True)
print("PASS report/state/role/references/exact owned paths and applicable Markdown/JSON/whitespace checks")
print("RETAIN I10b2 native controls/inspection/feedback/row transitions, I11/I12 Ready/start/two-Android lifecycle, independent B3 review, I14 gameplay, I28/I31 rematch, I33-I36/B8 final suite/UI/compatibility and all external gates")
