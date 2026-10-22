"""Read-only I10a1 handoff, exact check bytes, input identities and owned cleanup audit."""
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess
import xml.etree.ElementTree as ET


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def sha(data):
    return hashlib.sha256(data).hexdigest()


base = Path("docs/agent/implementation-runs/20261022T195604Z-aa3e4430")
report_path = str(base) + ".json"
state_path = "docs/agent/implementation-state.json"
report = json.loads(Path(report_path).read_text())
before = json.loads(git("show", report["startCommit"] + ":" + state_path))
after = json.loads(Path(state_path).read_text())
api = runpy.run_path("scripts/implementation-loop.py")
choice = {"mode": "implement", "target": "I10a1", "findingIds": []}
assert git("rev-parse", "HEAD") == report["startCommit"]
assert git("branch", "--show-current") == "master"
assert not git("diff", "--cached", "--name-only")
assert before["authorization"] == "authorized" and before["mode"] == "active"
assert api["selection"](before) == choice
api["validate_state"](after)
api["validate_transition"](before, after, choice, report)
assert after["activeRun"] is None
assert after["runs"] == before["runs"] + [report_path]
assert all(before[key] == after[key] for key in before if key not in {"runs", "tasks"})
old = {t["id"]: t for t in before["tasks"]}
new = {t["id"]: t for t in after["tasks"]}
assert set(old) == set(new)
assert all(new[i] == task for i, task in old.items() if i != "I10a1")
assert new["I10a1"]["status"] == "implemented" and new["I10a1"]["implementationRecord"] == report_path
assert api["selection"](after) == {"mode": "implement", "target": "I10a2", "findingIds": []}
print("PASS exact baseline/master, D-304 authorization, supplied priority, state/role transition and next I10a2")
print("PASS only I10a1 closes; ancestors/B3 review, findings, model/grants/authorization and release gates unchanged")

ledger_path = "docs/agent/coverage/ledger.json"
ledger_bytes = Path(ledger_path).read_bytes()
assert ledger_bytes == subprocess.check_output(["git", "show", report["startCommit"] + ":" + ledger_path])
ledger = json.loads(ledger_bytes)
items = [i for i in ledger["items"] if "I10" in i["implementationDependencies"]]
assert [i["id"] for i in items] == report["sourceIds"]
assert all(i["completion"] == "unexecuted" for i in items)
print(f"PASS {len(items)} I10 assignments retain partial cross-task obligations, not complete-scenario execution passes")

outputs = []
for check in report["checks"]:
    if check["evidence"].endswith("audit.txt"):
        continue
    path = Path(check["evidence"])
    evidence = json.loads(path.read_text())
    data = evidence["output"].encode()
    assert sha(data) == evidence["outputSha256"]
    assert evidence["exitCode"] == check["exitCode"]
    assert check["kind"] == "diagnostic" or evidence["exitCode"] == 0
    outputs.append(data.decode())
    print(f"CHECK {check['kind']} exit={evidence['exitCode']} {path} SHA256 {sha(path.read_bytes())}")

retained = json.loads((base / "retained-junit.json").read_text())
expected = {"PreparationTest": 6, "GameWireTest": 3, "PreparedDeckTest": 4,
            "GameSocketTest": 3, "GameDurabilityTest": 5, "DeckHttpTest": 2,
            "GameSessionTest": 4, "GameLobbyIntegrationTest": 2}
found = {}
for item in retained:
    data = item["xml"].encode()
    assert sha(data) == item["sha256"]
    assert Path(item["path"]).read_bytes() == data
    suite = ET.fromstring(data)
    name = suite.attrib["name"].split(".")[-1].removesuffix("[jvm]")
    found[name] = int(suite.attrib["tests"])
    assert all(int(suite.get(k, "0")) == 0 for k in ("skipped", "failures", "errors"))
    assert all(not list(t) or all(c.tag not in {"failure", "error", "skipped"} for c in t) for t in suite.findall("testcase"))
    print(f"JUNIT {name} tests={suite.attrib['tests']} no skipped/failures/errors SHA256 {sha(data)}")
assert found == expected
print("PASS 29 actually executed affected tests: 9 shared, 14 PostgreSQL/socket/deck and 6 client host; not a full-suite claim")

combined = "\n".join(outputs)
owned = set(re.findall(r"OWNED (?:integration PostgreSQL|codegen PostgreSQL) container=([a-f0-9]{64})", combined))
cleaned = set(re.findall(r"CLEANUP removed owned (?:integration|codegen) container=([a-f0-9]{64})", combined))
assert len(owned) == 15 and owned == cleaned
current = subprocess.check_output(["docker", "ps", "-a", "--format", "{{.ID}}"], text=True).splitlines()
assert not any(identifier.startswith(short) for identifier in owned for short in current)
volumes = set(re.findall(r"volumes=\[([a-f0-9]{64})\]", combined))
current_volumes = set(subprocess.check_output(["docker", "volume", "ls", "-q"], text=True).splitlines())
assert len(volumes) == 15 and not volumes & current_volumes
print("PASS exact 15 owned codegen/test PostgreSQL containers and volumes were removed; unrelated resources not compared or changed")

inputs = []
for folder in ("protocol/src", "server/src", "core/src", "androidApp/src"):
    inputs.extend(p for p in Path(folder).rglob("*") if p.is_file())
for folder in ("protocol", "server", "core", "androidApp"):
    inputs.extend(p for p in Path(folder).glob("*") if p.suffix in {".kts", ".lockfile", ".properties"})
inputs.extend(p for p in Path("gradle").rglob("*") if p.is_file())
inputs.extend(Path(p) for p in ("build.gradle.kts", "settings.gradle.kts", "gradle.properties", "scripts/gradle.sh"))
for path in sorted(set(inputs)):
    print(f"INPUT {path} SHA256 {sha(path.read_bytes())}")
apks = list(Path("androidApp/build/outputs/apk/debug").glob("*.apk"))
assert len(apks) == 1
for path in apks:
    print(f"BUILT NOT DEVICE-EXERCISED {path} SHA256 {sha(path.read_bytes())}")

actual = set(git("diff", "--name-only").splitlines()) | set(git("ls-files", "--others", "--exclude-standard").splitlines())
assert actual == set(report["changedPaths"]), (actual, set(report["changedPaths"]))
runner = object.__new__(api["Runner"])
runner.root = Path.cwd()
runner.state_path = Path(state_path)
runner.state_relative = state_path
runner.role_paths(before, choice, report, actual, report_path)
for path in actual:
    data = Path(path).read_bytes()
    assert b"\r" not in data and (not data or data.endswith(b"\n")), path
    assert all(line.rstrip() == line for line in data.splitlines()), path
    if Path(path).suffix == ".json":
        json.loads(data)
    if Path(path).suffix == ".md":
        text = data.decode()
        assert sum(line.startswith("```") for line in text.splitlines()) % 2 == 0, path
        for target in re.findall(r"\[[^\]]*\]\(([^)]+)\)", text):
            if "://" in target or target.startswith("#"):
                continue
            assert (Path(path).parent / target.split("#")[0]).exists(), (path, target)
subprocess.run(["git", "diff", "--check"], check=True)
print("PASS exact owned paths, check bytes, JSON/references, changed documentation links/fences and whitespace")
print("REMAINING: I10a2 picker/native provisional wiring, I10b transfers, I11/I12 Ready/start/cancel and B3 independent review; I34-I36 broad/final qualification and all external gates")
