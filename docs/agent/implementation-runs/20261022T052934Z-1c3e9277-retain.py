"""Losslessly envelope this unit's captured stdout so Git whitespace checks preserve every byte."""
import hashlib
import json
from pathlib import Path

prefix = "docs/agent/implementation-runs/20261022T052934Z-1c3e9277"
report = json.loads(Path(prefix + ".json").read_text())
for check in report["checks"]:
    path = Path(check["evidence"])
    if path == Path(prefix + "-handoff.txt"):
        continue  # The calling audit's live stdout is not an input to itself.
    assert str(path).startswith(prefix + "-") and path.suffix == ".txt"
    data = path.read_bytes()
    try:
        record = json.loads(data)
    except json.JSONDecodeError:
        record = None
    if not isinstance(record, dict) or "outputSha256" not in record:
        record = {"command": check["command"], "exitCode": check["exitCode"],
                  "outputSha256": hashlib.sha256(data).hexdigest(), "output": data.decode("utf-8")}
        path.write_text(json.dumps(record, indent=2, ensure_ascii=False) + "\n")
    assert record["command"] == check["command"] and record["exitCode"] == check["exitCode"]
    assert hashlib.sha256(record["output"].encode()).hexdigest() == record["outputSha256"]
    print("RETAINED exact stdout bytes in JSON envelope", path, "exit", record["exitCode"], "stdout-sha256", record["outputSha256"])
