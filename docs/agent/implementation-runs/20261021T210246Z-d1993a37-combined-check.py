"""Read-only I01d combined qualification; Android execution is deliberately I01f."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET


PREFIX = "docs/agent/implementation-runs/20261021T210246Z-d1993a37"


def generated_hashes():
    root = Path("client-storage/build/generated/sqldelight/code/ClientDatabase/debug")
    return {str(path.relative_to(root)): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in sorted(root.rglob("*.kt"))}


def retained_output(path):
    record = json.loads(Path(path).read_text())
    assert record["exitCode"] == 0
    assert hashlib.sha256(record["output"].encode()).hexdigest() == record["outputSha256"]
    return record["output"]


def main():
    # Compare actual independent clean regenerations, not merely two build variants.
    marker = "PASS SQLDelight real generated schema/query sources identical for both Android variants: "
    hashes = generated_hashes()
    assert len(hashes) >= 10
    for path in [PREFIX + "-storage-artifacts.json",
                 "docs/agent/implementation-runs/20261021T204702Z-a524f8a6-storage-artifacts.json"]:
        line = next(line for line in retained_output(path).splitlines() if line.startswith(marker))
        variants = json.loads(line[len(marker):])
        assert variants.keys() == {"debug", "release"}
        assert hashes == variants["debug"] == variants["release"]
        print("PASS independent clean generated schema/query identity against", path)
    print("ACTUAL regenerated hashes", json.dumps(hashes, sort_keys=True))

    source = Path("client-storage/src/test/kotlin/org/asyncttwiz/storage/ClientStorageTest.kt").read_text()
    expected = set(re.findall(r"@Test\s+fun\s+(\w+)", source))
    suite_path = Path("client-storage/build/test-results/testDebugUnitTest/TEST-org.asyncttwiz.storage.ClientStorageTest.xml")
    suite = ET.parse(suite_path).getroot()
    actual = {test.attrib["name"].removesuffix("()") for test in suite.findall("testcase")}
    assert len(expected) == 16 and actual == expected
    assert int(suite.attrib["tests"]) == 16
    assert all(int(suite.attrib[key]) == 0 for key in ("failures", "errors", "skipped"))
    print("ACTUAL repeated JUnit", suite_path, "\n", suite_path.read_text())
    print("PASS all 16 source-defined storage tests executed, none omitted or skipped")

    # Child completion evidence is retained and was not fabricated by this parent.
    state = json.loads(Path("docs/agent/implementation-state.json").read_text())
    tasks = {task["id"]: task for task in state["tasks"]}
    for identifier in ("I01d1", "I01d2"):
        task = tasks[identifier]
        assert task["status"] == "implemented"
        child = json.loads(Path(task["implementationRecord"]).read_text())
        assert child["target"] == identifier and child["mode"] == "implement"
        required = [check for check in child["checks"] if check["kind"] == "required"]
        assert required
        for check in required:
            assert check["exitCode"] == 0
            retained_output(check["evidence"])
        print("PASS retained child required outputs and completion", identifier, task["implementationRecord"])

    # No source/build/fixture changes are required to qualify their combined outcome.
    paths = subprocess.check_output(["git", "diff", "--name-only", "HEAD"], text=True).splitlines()
    assert all(path == "client-storage/README.md" or path.startswith("docs/agent/") for path in paths)
    assert tasks["I01f"]["dependsOn"] == ["I01a", "I01c", "I01d", "I01e"]
    assert tasks["I01f"]["status"] == "pending"
    print("PASS combined outcome uses unchanged production/host/instrumentation/build/lock/fixture inputs")
    print("PASS Android actual-driver/controller/launcher execution remains I01f, no reverse dependency")
    print("NOT device/OEM-backup/signed-upgrade/account-isolation/full-tree or independent B1 review qualification")


if __name__ == "__main__":
    main()
