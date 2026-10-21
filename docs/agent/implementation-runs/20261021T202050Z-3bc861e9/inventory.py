"""Read-only preflight/final resource inventory; never remove shared resources."""
import json
from pathlib import Path
import subprocess
import sys


def output(*args):
    return subprocess.check_output(args, text=True).strip()


resources = {
    "containers": sorted(output("docker", "ps", "--all", "--quiet", "--no-trunc").split()),
    "networks": sorted(output("docker", "network", "ls", "--quiet", "--no-trunc").split()),
    "volumes": sorted(output("docker", "volume", "ls", "--quiet").split()),
}
print("RESOURCE INVENTORY " + json.dumps(resources), flush=True)
if len(sys.argv) > 1:
    original = json.loads((Path(__file__).parent / "preflight.json").read_text())["output"]
    baseline = json.loads(original.splitlines()[0].split("RESOURCE INVENTORY ", 1)[1])
    assert resources == baseline, (resources, baseline)
    processes = output("ps", "-eo", "pid,args").splitlines()
    services = [line for line in processes if "/bin/java " in line and "org.asyncttwiz.server.ServiceCommand" in line]
    print("Remaining installed service JVMs:", services)
    assert not services
    print("PASS exact original unrelated resources preserved; owned containers/networks/volumes/service JVMs absent")
    print("Image/base caches deliberately retained; no publication/deployment/global cleanup")
else:
    for command in (("git", "status", "--porcelain=v1"), ("git", "rev-parse", "HEAD"),
                    ("docker", "version", "--format", "{{json .}}"),
                    ("docker", "info", "--format", "{{.Architecture}}")):
        print("COMMAND " + json.dumps(command), flush=True)
        print(output(*command), flush=True)
    print("POSTGRES PIN", Path("server/database-images.properties").read_text())
    print("IMAGE RECIPE", Path("server/Dockerfile").read_text())
    print("Native amd64 qualification only; I35 retains arm64/full compatibility; no release gate changed")
