"""Run-owned I01e parent qualification; not independent B1 review or device E2E."""
import argparse
import base64
import hashlib
import importlib.util
import json
from pathlib import Path
import shlex
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path.cwd()
PREFIX = Path("docs/agent/implementation-runs/20261021T233041Z-1b76a4c6")
START = "0bfe353006e27624594c87ba870ad3ffad746783"
sys.path.insert(0, str(ROOT))
from e2e import environment as e

spec = importlib.util.spec_from_file_location("lifecycle", ROOT / "scripts/check-e2e-environment.py")
lifecycle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lifecycle)


def path(label):
    return Path(str(PREFIX) + "-" + label + ".json")


def write(label, value):
    destination = path(label)
    assert not destination.exists(), destination
    destination.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n")


def compare_inventory():
    before = json.loads(path("inventory").read_text())
    until = time.monotonic() + 20
    while lifecycle.inventory() != before and time.monotonic() < until:
        time.sleep(.2)
    after = lifecycle.inventory()
    print("BASELINE-UNRELATED-INVENTORY", json.dumps(before, sort_keys=True), flush=True)
    print("CURRENT-UNRELATED-INVENTORY", json.dumps(after, sort_keys=True), flush=True)
    assert before == after, "Owned leak or unrelated resource change"


def child_provenance():
    state = json.loads(Path("docs/agent/implementation-state.json").read_text())
    tasks = {t["id"]: t for t in state["tasks"]}
    for name in ("I01e1", "I01e2", "I01e2a", "I01e2b"):
        task = tasks[name]
        assert task["status"] == "implemented"
        record_path = task["implementationRecord"]
        raw = Path(record_path).read_bytes()
        assert raw == subprocess.check_output(["git", "show", START + ":" + record_path])
        record = json.loads(raw)
        for check in record["checks"]:
            evidence = check["evidence"]
            data = Path(evidence).read_bytes()
            assert data == subprocess.check_output(["git", "show", START + ":" + evidence])
            result = json.loads(data)
            assert result["exitCode"] == check["exitCode"]
            if check["kind"] == "required":
                assert check["exitCode"] == 0
            output = result["output"].encode() if "output" in result else base64.b64decode(result["outputBase64"])
            assert hashlib.sha256(output).hexdigest() == result["outputSha256"]
            for support in check.get("supportPaths", []):
                assert Path(support).read_bytes() == subprocess.check_output(["git", "show", START + ":" + support])
        print("CHILD-PROVENANCE", name, record_path, hashlib.sha256(raw).hexdigest(),
              "checks", len(record["checks"]), "all retained bytes and required exits match", flush=True)
    assert tasks["I01e"]["children"] == ["I01e1", "I01e2"]
    assert state["authorization"] == "authorized" and state["mode"] == "active"
    assert state["runner"]["model"] == "openai/gpt-6.1-sol#high" and state["findings"] == []
    print("PASS implemented child provenance, retained original diagnostics, explicit D-304 scope and exact model; no group review due", flush=True)


def retain_xml(diagnostic=False):
    records = []
    locations = [("server", "test"), ("core", "jvmTest"), ("protocol", "jvmTest")]
    if not diagnostic:
        locations.append(("server", "composeIsolationTest"))
    for module, task in locations:
        sources = sorted((ROOT / module / "build/test-results" / task).glob("TEST-*.xml"))
        assert sources, (module, task)
        for source in sources:
            raw = source.read_bytes()
            suite = ET.fromstring(raw)
            records.append({"source": str(source.relative_to(ROOT)), "sha256": hashlib.sha256(raw).hexdigest(),
                            "xml": raw.decode(), "suite": suite.attrib})
    label = "diagnostic-junit" if diagnostic else "junit"
    write(label, records)
    for record in records:
        assert int(record["suite"]["tests"]) > 0
        if not diagnostic:
            assert all(record["suite"][key] == "0" for key in ("skipped", "failures", "errors"))
    print("RETAINED-JUNIT", json.dumps({"path": str(path(label)), "sha256": e.sha(path(label)),
                                       "tests": sum(int(r["suite"]["tests"]) for r in records)}, sort_keys=True), flush=True)


def final():
    compare_inventory()
    images, inputs = e.image_inputs()
    print("FINAL-QUALIFIED-INPUTS", json.dumps({"images": images, "hashes": inputs}, sort_keys=True), flush=True)
    for relative in ("server/build/application-image.json", "e2e/build/adapters/image.json"):
        print("IMAGE-MANIFEST", relative, (ROOT / relative).read_text(), flush=True)
    manifest = json.loads((ROOT / "server/build/application-image.json").read_text())
    assert manifest["sourceCommit"] == START
    for name, expected in manifest["libraries"].items():
        assert e.sha(ROOT / "server/build/install/server/lib" / name) == expected, name
    for relative, expected in manifest["generatedSchema"].items():
        assert e.sha(ROOT / "server/build/generated-src/jooq" / relative) == expected, relative
    retain_xml()
    print("PASS clean locked image/runtime/generated-schema identities, lossless no-skip XML, owned cleanup and unchanged unrelated inventory", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=["baseline", "checks", "remaining", "final", "diagnostic-retain"])
    phase = parser.parse_args().phase
    if phase == "baseline":
        assert subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip() == START
        assert not subprocess.check_output(["git", "diff", "--cached", "--name-only"], text=True).strip()
        write("inventory", lifecycle.inventory())
        print("INITIAL-UNRELATED-INVENTORY", path("inventory").read_text(), flush=True)
        print("TOOLS", e.docker("version", "--format", "{{json .}}"), e.docker("compose", "version"), flush=True)
        child_provenance()
    elif phase == "diagnostic-retain":
        retain_xml(diagnostic=True)
        compare_inventory()
    elif phase == "final":
        final()
    else:
        commands = [
            ("server-image", "python3 scripts/build-server-image.py"),
            ("adapter-image", "python3 scripts/build-synthetic-adapters.py"),
            ("service-regressions", "./scripts/gradle.sh --no-daemon --console=plain --no-build-cache :core:jvmTest :protocol:jvmTest :server:test verifyModuleBoundaries --rerun-tasks"),
            ("controller-tests", "python3 -m unittest discover -s tests -p test_e2e_environment.py -v"),
            ("adapter-tests", "python3 -m unittest discover -s tests -p test_synthetic_adapters.py -v"),
            ("foundation-tests", "python3 -m unittest discover -s tests -p test_foundation_data.py -v"),
            ("adapter-image-tests", "python3 scripts/check-synthetic-adapters-image.py"),
            ("isolation", "./scripts/gradle.sh --no-daemon --console=plain --no-build-cache :server:composeIsolationTest --rerun-tasks"),
            ("lifecycle", "python3 scripts/check-e2e-environment.py"),
            ("fixtures", "python3 scripts/check-synthetic-catalogs.py"),
            ("coverage", "python3 scripts/coverage-ledger.py"),
        ]
        if phase == "remaining":
            # The initial sequence's failed service output remains untouched.
            # Its separately executed post-diagnostic full suite must pass first.
            assert json.loads(path("service-final").read_text())["exitCode"] == 0
            commands = commands[3:]
        for label, command in commands:
            assert not path(label).exists(), "Never overwrite retained command output"
            print("COMMAND", command, flush=True)
            result = subprocess.run(["python3", "scripts/capture-check.py", str(path(label)), "--", *shlex.split(command)])
            assert result.returncode == 0, (label, result.returncode)
            compare_inventory()
        print("PASS all fresh parent integration checks; no Android, account/gameplay acceptance or independent B1 review claim", flush=True)


if __name__ == "__main__":
    main()
