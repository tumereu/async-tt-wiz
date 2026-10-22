"""Small run-owned I02a evidence/state/docs audit; no extra product tests or controller launch."""
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess
import xml.etree.ElementTree as ET

prefix = "docs/agent/implementation-runs/20261022T052934Z-1c3e9277"
runpy.run_path(prefix + "-retain.py")
report = json.loads(Path(prefix + ".json").read_text())
state_path = "docs/agent/implementation-state.json"
state = json.loads(Path(state_path).read_text())


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


assert git("rev-parse", "HEAD") == report["startCommit"] == "ddf1aa71e887fe5001043e83fd8a74a8ad79596b"
assert git("branch", "--show-current") == "master"
assert not git("diff", "--cached", "--name-only")
before = json.loads(git("show", report["startCommit"] + ":" + state_path))
api = runpy.run_path("scripts/implementation-loop.py")
choice = {"mode": "implement", "target": "I02a", "findingIds": []}
assert api["selection"](before) == choice
api["validate_state"](state)
api["validate_transition"](before, state, choice, report)
assert state["activeRun"] is None
assert state["runs"] == before["runs"] + [prefix + ".json"]
assert api["selection"](state) == {"mode": "implement", "target": "I02b", "findingIds": []}
for key in before:
    if key not in {"tasks", "runs"}:
        assert state[key] == before[key], key
old = {t["id"]: t for t in before["tasks"]}
new = {t["id"]: t for t in state["tasks"]}
assert new["I02a"]["status"] == "implemented"
assert all(task == new[identifier] for identifier, task in old.items() if identifier != "I02a")
print("PASS exact baseline/master, D-304 authorization/priority, sole I02a implementation, next I02b; parent/B1/release gates/model/grants unchanged")

# Preserve actual original red runs, and reuse only each specifically green test.
# Only NullRequestCache, the capacity-test oracle and V1-only history-count oracle
# changed between initial run and their affected rechecks; unrelated green tests
# are evidence reuse, not a claim of a second/full green run.
combined = {}
for suffix in ("-tests-initial-xml.json", "-tests-affected-xml.json"):
    payload = json.loads(Path(prefix + suffix).read_text())
    for path, xml in payload.items():
        root = ET.fromstring(xml)
        for test in root.findall("testcase"):
            combined[(test.attrib["classname"], test.attrib["name"])] = (suffix, test)
        print("RETAINED original XML", suffix, path, "sha256", hashlib.sha256(xml.encode()).hexdigest(),
              "original counts", {k: root.attrib[k] for k in ("tests", "failures", "errors", "skipped")})
for path in sorted(Path("server/build/test-results/test").glob("TEST-*.xml")):
    xml = path.read_text()
    root = ET.fromstring(xml)
    assert root.attrib["name"] == "org.asyncttwiz.server.ServiceProcessTest"
    assert all(int(root.attrib[key]) == 0 for key in ("failures", "errors", "skipped"))
    for test in root.findall("testcase"):
        combined[(test.attrib["classname"], test.attrib["name"])] = ("-tests-schema.txt", test)
    print("ACTUAL final schema-process XML JSON", json.dumps({"path": str(path), "sha256": hashlib.sha256(xml.encode()).hexdigest(), "xml": xml}))
assert len(combined) == 27
for (classname, name), (source, test) in sorted(combined.items()):
    assert not any(test.find(tag) is not None for tag in ("failure", "error", "skipped")), (classname, name)
    print("PASS distinct affected test", classname, name, "original evidence", source)
print("PASS 27 distinct affected tests across original execution plus specifically affected passing rechecks; NOT a whole-suite or second full run")

image = json.loads(Path("server/build/application-image.json").read_text())
assert image["sourceCommit"] == report["startCommit"] and image["platform"] == "linux/amd64"
for name, expected in image["libraries"].items():
    path = Path("server/build/install/server/lib", name)
    assert hashlib.sha256(path.read_bytes()).hexdigest() == expected
for name, expected in image["generatedSchema"].items():
    path = Path("server/build/generated-src/jooq", name)
    assert hashlib.sha256(path.read_bytes()).hexdigest() == expected
assert {"org/asyncttwiz/server/generated/tables/Account.java", "org/asyncttwiz/server/generated/tables/AccountSession.java"} <= set(image["generatedSchema"])
image_output = json.loads(Path(prefix + "-image-accounts.txt").read_text())["output"]
assert "PASS actual local image register/login" in image_output and "\nOK\n" in image_output
assert image["imageId"] in image_output
print("PASS exact current image/distribution/generated jOOQ identities and real local account/restart/revocation result", image["imageId"])

ledger_path = "docs/agent/coverage/ledger.json"
assert Path(ledger_path).read_bytes() == subprocess.check_output(["git", "show", report["startCommit"] + ":" + ledger_path])
for item in json.loads(Path(ledger_path).read_text())["items"]:
    if "I02" in item["implementationDependencies"]:
        assert item["completion"] == "unexecuted"
        print("RETAINED partial/future source", item["id"], "not a full scenario/UI pass")
doc = Path("server/README.md")
text = doc.read_text()
assert text.count("```") % 2 == 0
for link in re.findall(r"\[[^\]]*\]\(([^)]+)\)", text):
    if not re.match(r"https?://", link):
        assert (doc.parent / link.split("#")[0]).exists(), link
assert "## Account HTTP v1 — I02a" in text
print("PASS applicable README links/fences/API anchor; no UI documents/publication or blanket coverage claims")

actual = set(git("diff", "--name-only").splitlines()) | set(git("ls-files", "--others", "--exclude-standard").splitlines())
assert actual == set(report["changedPaths"]), (actual - set(report["changedPaths"]), set(report["changedPaths"]) - actual)
for path in sorted(actual):
    data = Path(path).read_bytes()
    assert b"\r" not in data and (not data or data.endswith(b"\n")), path
    assert all(line.rstrip() == line for line in data.splitlines()), path
    if not path.startswith("docs/agent/implementation-runs/") and path != state_path:
        print("CURRENT owned input SHA256", path, hashlib.sha256(data).hexdigest())
runner = object.__new__(api["Runner"])
runner.root = Path.cwd()
runner.state_path = Path(state_path)
runner.state_relative = state_path
runner.role_paths(before, choice, report, actual, prefix + ".json")
subprocess.run(["git", "diff", "--check"], check=True)
print("PASS exact owned path set, role/support boundaries and byte-preserving whitespace-safe evidence; no unrelated work staged")
print("REMAINING I02b Android/Keystore, I03 shell/Logout isolation, I04 reset/multi-device, I09/I12/I13/I14 social/game/recovery, I34/I35/I36 qualification/reviews and all external release gates")
