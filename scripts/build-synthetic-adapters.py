#!/usr/bin/env python3
"""Build a local-only, immutable-base test-adapter image; never publish/deploy."""
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parent.parent


def run(*args):
    print("COMMAND", json.dumps(args), flush=True)
    subprocess.run(args, cwd=ROOT, check=True)


def main():
    run("python3", "scripts/check-synthetic-catalogs.py")
    dockerfile = ROOT / "e2e/adapters/Dockerfile"
    base = re.search(r"^FROM (\S+)$", dockerfile.read_text(), re.M)[1]
    assert re.fullmatch(r"python:[\w.-]+@sha256:[0-9a-f]{64}", base)
    destination = ROOT / "e2e/build/adapters"
    destination.mkdir(parents=True, exist_ok=True)
    run("docker", "pull", "--platform", "linux/amd64", base)
    run("docker", "build", "--pull", "--network=none", "--platform", "linux/amd64",
        "--iidfile", str(destination / "image.id"), "-f", str(dockerfile), str(ROOT))
    image = (destination / "image.id").read_text().strip()
    inspect = json.loads(subprocess.check_output(["docker", "image", "inspect", image]))[0]
    assert inspect["Architecture"] == "amd64" and inspect["Config"]["User"] == "10001:10001"
    version = subprocess.check_output(["docker", "run", "--rm", "--network=none", "--read-only",
                                       "--entrypoint", "python3", image, "-c",
                                       "import sys,sqlite3,zlib; print(sys.version); print('SQLite',sqlite3.sqlite_version,'zlib',zlib.ZLIB_VERSION)"], text=True).strip()
    license_code = """from pathlib import Path
import hashlib,json,subprocess
paths=['/usr/local/lib/python3.13/LICENSE.txt','/usr/share/doc/libsqlite3-0/copyright','/usr/share/doc/zlib1g/copyright']
texts=[Path(p).read_text() for p in paths]
assert 'PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2' in texts[0]
assert 'public domain' in texts[1].lower()
assert 'zlib' in texts[2].lower()
print(json.dumps({'licenseFiles':{p:hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in paths},'packages':subprocess.check_output(['dpkg-query','-W','-f=${Package} ${Version}\\n'],text=True)},sort_keys=True))
"""
    provenance = json.loads(subprocess.check_output(["docker", "run", "--rm", "--network=none", "--read-only",
                                                     "--entrypoint", "python3", image, "-c", license_code], text=True))
    inputs = [dockerfile, ROOT / "e2e/adapters/Dockerfile.dockerignore", ROOT / "e2e/adapters/adapter.py",
              *sorted(p for p in (ROOT / "tests/fixtures/catalogs").rglob("*") if p.is_file())]
    manifest = {"imageId": image, "platform": "linux/amd64", "base": base, "versions": version,
                "baseProvenance": provenance,
                "inputs": {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in inputs},
                "dependencies": "Python stdlib only; no pip packages or networked build steps",
                "qualification": "Local build only; lifecycle/socket qualification separate; no arm64 or product acceptance"}
    (destination / "image.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps(manifest, indent=2), flush=True)


if __name__ == "__main__":
    main()
