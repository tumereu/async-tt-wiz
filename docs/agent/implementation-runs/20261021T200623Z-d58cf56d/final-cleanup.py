"""Check the settled handoff against the initial unrelated-resource inventory; never clean globally."""
import json
from pathlib import Path
import subprocess

directory = Path(__file__).parent
first = json.loads((directory / "first-image-lifecycle.json").read_text())["output"]
baseline = json.loads(first.splitlines()[0].split("UNRELATED RESOURCE BASELINE ", 1)[1])
for kind, query in (("containers", ("ps", "--all", "--quiet", "--no-trunc")),
                    ("networks", ("network", "ls", "--quiet", "--no-trunc")),
                    ("volumes", ("volume", "ls", "--quiet"))):
    actual = sorted(subprocess.check_output(["docker", *query], text=True).split())
    print(kind, json.dumps(actual))
    assert actual == sorted(baseline[kind]), (kind, actual, baseline[kind])
processes = subprocess.check_output(["ps", "-eo", "pid,args"], text=True).splitlines()
services = [line for line in processes if "/bin/java " in line and "org.asyncttwiz.server.ServiceCommand" in line]
print("Remaining installed service JVMs:", services)
assert not services
print("PASS no run-owned application/PostgreSQL/Testcontainers/reaper containers, networks, volumes or installed service JVMs remain")
print("PASS exact pre-existing unrelated resource inventory preserved; image/base caches intentionally retained; no global cleanup performed")
