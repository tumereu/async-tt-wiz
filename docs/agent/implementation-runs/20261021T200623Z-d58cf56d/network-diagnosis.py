"""Controlled Docker29 internal bridge HTTP experiment; exact owned cleanup."""
import importlib.util
import json
from pathlib import Path
import time
import urllib.error
import urllib.request

spec = importlib.util.spec_from_file_location("image_checks", "tests/server/image_lifecycle.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
module.MANIFEST = json.loads(Path("server/build/application-image.json").read_text())
case = module.ImageLifecycle("test_fresh_explicit_migration_health_shutdown_and_retained_restart")
try:
    case.setUp()
    service = case.app()
    info = module.inspect("container", service)
    print("ACTUAL INTERNAL NETWORK MAPPING", json.dumps(info["NetworkSettings"], sort_keys=True), flush=True)
    address = info["NetworkSettings"]["Networks"][case.network]["IPAddress"]
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    deadline = time.monotonic() + 30
    while True:
        try:
            with opener.open(f"http://{address}:8081/health/live", timeout=2) as response:
                assert response.status == 200 and response.read() == b"OK\n"
                print("PASS actual host-to-private-internal-bridge HTTP; no host publication needed", flush=True)
                break
        except (urllib.error.URLError, ConnectionError, TimeoutError):
            if time.monotonic() > deadline:
                raise
            time.sleep(0.1)
finally:
    case.doCleanups()
