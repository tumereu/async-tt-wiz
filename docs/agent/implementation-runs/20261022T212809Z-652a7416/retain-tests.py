"""Retain actual JUnit output losslessly; not a product or broad-suite runner."""
import hashlib
import json
from pathlib import Path
import sys

phase = sys.argv[1]
root = Path("docs/agent/implementation-runs/20261022T212809Z-652a7416")
directories = [Path("server/build/test-results/test")] if phase == "rollback-recheck" else [Path("androidApp/build/test-results/testDebugUnitTest")] if phase == "controller-recheck" else [
    Path("protocol/build/test-results/jvmTest"), Path("androidApp/build/test-results/testDebugUnitTest"),
    Path("server/build/test-results/test")]
records = []
for directory in directories:
    for path in sorted(directory.glob("TEST-*.xml")):
        data = path.read_bytes()
        records.append({"path": str(path), "sha256": hashlib.sha256(data).hexdigest(), "xml": data.decode()})
assert records
(root / (phase + "-junit.json")).write_text(json.dumps(records, indent=2) + "\n")
print(f"Retained {len(records)} actual JUnit XML outputs for {phase}; original command outcome is unchanged")
