"""Retain immutable upstream runtime-base source/licensing provenance, not legal clearance."""
import hashlib
import json
from pathlib import Path
import subprocess
import urllib.request

base = json.loads(Path("server/build/application-image.json").read_text())["base"]
print(subprocess.check_output(["docker", "buildx", "imagetools", "inspect", base], text=True))
revision = "511f9356dc4d50932a0a5f8cfb0f87ed1aef4f07"
url = f"https://raw.githubusercontent.com/adoptium/containers/{revision}/21/jre/ubuntu/noble/Dockerfile"
with urllib.request.urlopen(url, timeout=30) as response:
    source = response.read()
text = source.decode()
assert "21.0.12.1" in text and "ubuntu:24.04" in text
assert "Apache License, Version 2.0" in text
print("IMMUTABLE upstream Dockerfile URL", url, "SHA256", hashlib.sha256(source).hexdigest())
print(text)
print("PASS official pinned Temurin JRE21 source/version provenance; runtime OpenJDK legal files inspected by image tests")
print("NOT arm64 execution, complete image redistribution notices, legal/rights clearance or release readiness")
