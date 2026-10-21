"""Run-owned I01e2 integration qualification, not an independent B1 review."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path.cwd()
PREFIX = ROOT / "docs/agent/implementation-runs/20261021T223211Z-d5a50a9e"
sys.path.insert(0, str(ROOT))
from e2e import environment as e

spec = importlib.util.spec_from_file_location("lifecycle", ROOT / "scripts/check-e2e-environment.py")
lifecycle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lifecycle)


def retain_xml(label, locations, require_pass=True):
    records = []
    for module, task in locations:
        paths = sorted((ROOT / module / "build/test-results" / task).glob("TEST-*.xml"))
        assert paths, (module, task)
        for path in paths:
            raw = path.read_bytes()
            suite = ET.fromstring(raw)
            assert int(suite.attrib["tests"]) > 0
            records.append({"source": str(path.relative_to(ROOT)), "sha256": hashlib.sha256(raw).hexdigest(),
                            "xml": raw.decode("utf-8"), "suite": suite.attrib})
    destination = Path(str(PREFIX) + "-" + label + "-junit.json")
    e.write_json(destination, records)
    print("RETAINED-JUNIT", json.dumps({"path": str(destination.relative_to(ROOT)), "sha256": e.sha(destination),
                                       "tests": sum(int(r["suite"]["tests"]) for r in records)}, sort_keys=True), flush=True)
    if require_pass:
        assert all(r["suite"][k] == "0" for r in records for k in ("skipped", "failures", "errors"))


def compare_inventory():
    before = json.loads(Path(str(PREFIX) + "-inventory.json").read_text())
    until = time.monotonic() + 20
    while lifecycle.inventory() != before and time.monotonic() < until:
        time.sleep(.2)
    after = lifecycle.inventory()
    print("BASELINE-UNRELATED-INVENTORY", json.dumps(before, sort_keys=True), flush=True)
    print("CURRENT-UNRELATED-INVENTORY", json.dumps(after, sort_keys=True), flush=True)
    assert after == before, "Owned leak or unrelated resource change"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=["baseline", "isolation-1", "isolation-2", "failed-retain", "failed-service-retain", "retain", "final"])
    args = parser.parse_args()
    if args.phase == "baseline":
        baseline = lifecycle.inventory()
        destination = Path(str(PREFIX) + "-inventory.json")
        assert not destination.exists()
        e.write_json(destination, baseline)
        print("INITIAL-UNRELATED-INVENTORY", json.dumps(baseline, sort_keys=True), flush=True)
        print("TOOLS", e.docker("version", "--format", "{{json .}}"), e.docker("compose", "version"), flush=True)
        return
    if args.phase == "failed-retain":
        retain_xml("isolation-diagnostic", [("server", "composeIsolationTest")], require_pass=False)
    elif args.phase == "failed-service-retain":
        retain_xml("service-diagnostic", [("server", "test")], require_pass=False)
    elif args.phase.startswith("isolation-"):
        compare_inventory()
        images, inputs = e.image_inputs()
        print("QUALIFIED-INPUTS", json.dumps({"images": images, "hashes": inputs}, sort_keys=True), flush=True)
        command = ["./scripts/gradle.sh", "--no-daemon", "--console=plain", "--no-build-cache",
                   ":server:composeIsolationTest", "--rerun-tasks"]
        print("COMMAND", json.dumps(command), flush=True)
        result = subprocess.run(command)
        compare_inventory()
        retain_xml(args.phase, [("server", "composeIsolationTest")])
        assert result.returncode == 0, result.returncode
        print("PASS combined retained/reset/failure/ownership/private-endpoint/egress qualification with separate live Testcontainers witness", flush=True)
    elif args.phase == "retain":
        retain_xml("regressions", [("server", "test"), ("core", "jvmTest"), ("protocol", "jvmTest")])
    else:
        compare_inventory()
        images, inputs = e.image_inputs()
        print("FINAL-QUALIFIED-INPUTS", json.dumps({"images": images, "hashes": inputs}, sort_keys=True), flush=True)
        for relative in ("server/build/application-image.json", "e2e/build/adapters/image.json"):
            print("IMAGE-MANIFEST", relative, (ROOT / relative).read_text(), flush=True)
        manifest = json.loads((ROOT / "server/build/application-image.json").read_text())
        for name, expected in manifest["libraries"].items():
            assert e.sha(ROOT / "server/build/install/server/lib" / name) == expected, name
        for relative, expected in manifest["generatedSchema"].items():
            assert e.sha(ROOT / "server/build/generated-src/jooq" / relative) == expected, relative
        print("PASS exact clean-build image/runtime/generated-schema identities, isolated ownership cleanup and unchanged unrelated inventory; no Android/product/release claim", flush=True)


if __name__ == "__main__":
    main()
