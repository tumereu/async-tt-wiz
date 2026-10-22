"""Run-owned read-only handoff/evidence audit; does not run a new product suite."""
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess
import xml.etree.ElementTree as ET


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


root = Path.cwd()
prefix = Path("docs/agent/implementation-runs/20261022T175129Z-7080c0f4")
report_path = str(prefix) + ".json"
state_path = "docs/agent/implementation-state.json"
report = json.loads(Path(report_path).read_text())
before = json.loads(git("show", report["startCommit"] + ":" + state_path))
after = json.loads(Path(state_path).read_text())
api = runpy.run_path("scripts/implementation-loop.py")
choice = {"mode": "implement", "target": "I09b1", "findingIds": []}
assert git("rev-parse", "HEAD") == report["startCommit"]
assert git("branch", "--show-current") == "master"
assert not git("diff", "--cached", "--name-only")
assert before["mode"] == "active" and before["authorization"] == "authorized"
assert "D-304" in Path(before["authorizationRecord"]).read_text()
assert api["selection"](before) == choice
api["validate_state"](after)
api["validate_transition"](before, after, choice, report)
assert after["activeRun"] is None
assert after["runs"] == before["runs"] + [report_path]
for key in before:
    if key not in {"tasks", "runs"}:
        assert before[key] == after[key], key
old = {t["id"]: t for t in before["tasks"]}
new = {t["id"]: t for t in after["tasks"]}
assert new.keys() == old.keys()
assert all(new[i] == task for i, task in old.items() if i != "I09b1")
assert new["I09b1"]["status"] == "implemented"
assert new["I09b1"]["implementationRecord"] == report_path
assert api["selection"](after) == {"mode": "implement", "target": "I09b2", "findingIds": []}
assert Path("docs/agent/coverage/ledger.json").read_text() == git("show", report["startCommit"] + ":docs/agent/coverage/ledger.json") + "\n"
print("PASS exact baseline/master, empty index, D-304 authorization, I09b1 selection/transition; only selected task implemented; next I09b2")
print("PASS unrelated tasks/parents/B3 review/findings/ledger/model/permissions/authorization/external gates unchanged")

logs = {}
for check in report["checks"]:
    evidence = Path(check["evidence"])
    if evidence.suffix != ".json":
        continue
    record = json.loads(evidence.read_text())
    assert record["exitCode"] == check["exitCode"]
    if check["kind"] == "required":
        assert record["exitCode"] == 0
    raw = record["capturedLog"].encode()
    assert hashlib.sha256(raw).hexdigest() == record["sha256"]
    logs[evidence.stem] = record["capturedLog"]
    print("EVIDENCE", record["sha256"], str(evidence), "exitCode=" + str(record["exitCode"]))

# Reuse exact passing subsets from red development commands, never call the whole command green.
assert len(re.findall(r"^LobbyHttpTest > .* PASSED$", logs["initial-tests"], re.M)) == 4
assert len(re.findall(r"^GameDurabilityTest > .* PASSED$", logs["game-tests"], re.M)) == 5
assert "GameSocketTest > actual socket resume replaces old connection without fencing other installations and revoked credentials stop new requests() PASSED" in logs["game-tests"]
assert "GameSocketTest > real authenticated sockets decline reinvite stale decision accept and recover private original outcomes() PASSED" in logs["pipelined-socket-final"]
assert "PasswordResetHttpTest > actual Spring worker recovers encrypted pending mail and wrong key blocks readiness until corrected restart() PASSED" in logs["socket-and-mail-tests"]
xml = json.loads((prefix / "retained-junit.json").read_text())
shared = [ET.fromstring(text) for path, text in xml.items() if "protocol/" in path]
assert shared and all(r.attrib["tests"] == "3" and all(r.attrib[k] == "0" for k in ("failures", "errors", "skipped")) for r in shared)
assert "Task :protocol:jvmTest UP-TO-DATE" in logs["pipelined-socket-final"]
assert all(all(ET.fromstring(text).attrib[k] == "0" for k in ("failures", "errors", "skipped")) for text in xml.values())
assert "PASS semantic source imports contain no generated database/UI/server types" in logs["socket-and-mail-tests"]
print("PASS 15 distinct affected green cases: shared=3, durable PostgreSQL=5, actual sockets=2, prior lobby=4, prior SMTP recovery=1; no skips")
print("REUSE shared XML is the original execution; final protocol task was UP-TO-DATE, not newly executed")
print("REUSE four Lobby cases passed in initial red command; five durability and one resume case passed in game-tests red command")
print("REPAIR after game-tests: only GameService revision-list discriminator decoding changed; those six green methods do not call revisions(); affected revision/socket journey independently reran green")
print("REPAIR earlier: expected protocol refusals now use savepoint rollback under the epoch lock; raw DB failures keep ownership sanitization")

# Exact owned Testcontainers cleanup, without assuming unrelated host inventory remains static.
all_logs = "\n".join(logs.values())
containers = set(re.findall(r"OWNED (?:codegen|integration) PostgreSQL container=([0-9a-f]{64})", all_logs))
containers.update(re.findall(r"Container testcontainers/ryuk:[^ ]+ is starting: ([0-9a-f]{64})", all_logs))
volumes = set()
for text in re.findall(r"CLEANUP removed owned [^\n]+ volumes=\[([^]]*)\]", all_logs):
    volumes.update(v.strip() for v in text.split(",") if v.strip())
for kind, ids in (("container", containers), ("volume", volumes)):
    for identifier in sorted(ids):
        result = subprocess.run(["docker", kind, "inspect", identifier], capture_output=True, text=True)
        assert result.returncode != 0 and ("No such" in result.stderr or "no such" in result.stderr), (kind, identifier, result.stderr)
print(f"PASS exact owned cleanup: {len(containers)} PostgreSQL/codegen/Ryuk containers and {len(volumes)} database volumes absent; no unrelated resources touched")

actual = set(git("diff", "--name-only").splitlines()) | set(git("ls-files", "--others", "--exclude-standard").splitlines())
assert actual == set(report["changedPaths"]), (actual - set(report["changedPaths"]), set(report["changedPaths"]) - actual)
runner = object.__new__(api["Runner"])
runner.root = root
runner.state_path = Path(state_path)
runner.state_relative = state_path
runner.role_paths(before, choice, report, actual, report_path)
for path in sorted(actual):
    data = Path(path).read_bytes()
    assert b"\r" not in data and (not data or data.endswith(b"\n")), path
    assert all(line.rstrip() == line for line in data.splitlines()), path
    if path.endswith(".json"):
        json.loads(data)
    if path.endswith(".md"):
        text = data.decode()
        assert len(re.findall(r"^```", text, re.M)) % 2 == 0
        for target in re.findall(r"\[[^]]*\]\(([^)]+)\)", text):
            if "://" not in target and not target.startswith("#"):
                assert (Path(path).parent / target.split("#")[0]).exists(), (path, target)
    if not path.startswith("docs/agent/implementation-runs/") and path != state_path:
        print("CURRENT_INPUT", hashlib.sha256(data).hexdigest(), path)
for path in sorted(Path("server/build/generated-src/jooq").rglob("*.java")):
    print("GENERATED", hashlib.sha256(path.read_bytes()).hexdigest(), path)
for path in (Path("server/build/libs/server-0.1.0.jar"), Path("protocol/build/libs/protocol-jvm-0.1.0.jar")):
    assert path.is_file(), path
    print("BUILT_ARTIFACT", hashlib.sha256(path.read_bytes()).hexdigest(), path)
subprocess.run(["git", "diff", "--check"], check=True)
print("PASS exact owned changedPaths/role paths, lossless evidence, JSON, edited documentation links/fences and whitespace")
print("NOT CLAIMED: native/two-APK journey, full product suite, image/device qualification, independent B3 review, later preparation/gameplay/full-tree/UI/release gates")
