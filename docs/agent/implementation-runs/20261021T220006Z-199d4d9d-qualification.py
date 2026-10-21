"""Retain this run's actual isolation test XML and before/after resource evidence."""
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path.cwd()
PREFIX = ROOT / "docs/agent/implementation-runs/20261021T220006Z-199d4d9d"
sys.path.insert(0, str(ROOT))
from e2e import environment as e

spec = importlib.util.spec_from_file_location("lifecycle", ROOT / "scripts/check-e2e-environment.py")
lifecycle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lifecycle)
if "--retain-only" not in sys.argv:
    before = lifecycle.inventory()
    images, inputs = e.image_inputs()
    print("QUALIFIED-INPUTS", json.dumps({"images": images, "hashes": inputs}, sort_keys=True), flush=True)
    print("BEFORE-UNRELATED-INVENTORY", json.dumps(before, sort_keys=True), flush=True)
    args = ["./scripts/gradle.sh", "--no-daemon", "--console=plain", "--no-build-cache",
            ":server:composeIsolationTest", "--rerun-tasks"]
    print("COMMAND", json.dumps(args), flush=True)
    result = subprocess.run(args)
    # Ryuk's own JVM-death cleanup may finish just after Gradle returns.
    until = time.monotonic() + 15
    while lifecycle.inventory() != before and time.monotonic() < until:
        time.sleep(.2)
    after = lifecycle.inventory()
    print("AFTER-UNRELATED-INVENTORY", json.dumps(after, sort_keys=True), flush=True)
    assert after == before, "Owned resource leak or unrelated inventory change"
    assert result.returncode == 0, result.returncode
source = ROOT / "server/build/test-results/composeIsolationTest/TEST-org.asyncttwiz.server.database.ComposeIsolationTest.xml"
destination = Path(str(PREFIX) + "-junit-results.json")
records = []
for report in sorted((ROOT / "server/build/test-results").glob("*/TEST-*.xml")) + sorted((ROOT / "core/build/test-results").glob("*/TEST-*.xml")) + sorted((ROOT / "protocol/build/test-results").glob("*/TEST-*.xml")):
    raw = report.read_bytes()
    suite = ET.fromstring(raw)
    assert all(suite.attrib[k] == "0" for k in ("skipped", "failures", "errors"))
    records.append({"source": str(report.relative_to(ROOT)), "sha256": hashlib.sha256(raw).hexdigest(),
                    "xml": raw.decode("utf-8"), "suite": suite.attrib})
# JSON encodes original XML whitespace/CRs losslessly instead of altering actual
# command output to satisfy staged-whitespace checks. Decode xml to recover bytes.
e.write_json(destination, records)
suite = ET.fromstring(source.read_bytes())
assert suite.attrib["tests"] == "1" and all(suite.attrib[k] == "0" for k in ("skipped", "failures", "errors"))
print("RETAINED-JUNIT", json.dumps({"path": str(destination.relative_to(ROOT)), "sha256": e.sha(destination), "suite": suite.attrib}, sort_keys=True), flush=True)
print("PASS lossless JSON-encoded actual JUnit XML, including original whitespace; no skipped tests; dedicated isolation and ordinary service/shared suites retained", flush=True)
