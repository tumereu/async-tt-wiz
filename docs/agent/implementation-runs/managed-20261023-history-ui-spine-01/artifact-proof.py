"""Retain the requested immutable local native handoff identities; never install/deploy."""
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

run = Path(__file__).resolve().parent
root = Path.cwd()
sha = lambda data: hashlib.sha256(data).hexdigest()
git = lambda *args: subprocess.check_output(["git", *args], text=True).strip()
manifest_path = root / "server/build/application-image.json"
raw = manifest_path.read_bytes()
manifest = json.loads(raw)
source = manifest["sourceCommit"]
assert source == git("rev-parse", "HEAD")
subprocess.run(["git", "diff", "--quiet", source, "--", "androidApp", "client-storage", "protocol", "server", "core"], check=True)
distribution = root / "server/build/install/server/lib"
assert {p.name: sha(p.read_bytes()) for p in distribution.glob("*.jar")} == manifest["libraries"]
schema = root / "server/build/generated-src/jooq"
assert {str(p.relative_to(schema)): sha(p.read_bytes()) for p in schema.rglob("*.java")} == manifest["generatedSchema"]
image = json.loads(subprocess.check_output(["docker", "image", "inspect", manifest["imageId"]]))[0]
assert image["Id"] == manifest["imageId"]
assert image["Config"]["Labels"]["org.opencontainers.image.revision"] == source
assert image["Config"]["Labels"]["org.asyncttwiz.distribution.sha256"] == manifest["distributionSha256"]
migrations = sorted((root / "server/src/main/resources/db/migration").glob("*.sql"))
with zipfile.ZipFile(distribution / "server-0.1.0.jar") as jar:
    assert all(jar.read("db/migration/" + p.name) == p.read_bytes() for p in migrations)
(run / "server-application-image.json").write_bytes(raw)
urls = ["http://127.0.0.1:18880", "http://127.0.0.1:18882/prism/"]
apks = {}
for relative in ["androidApp/build/outputs/apk/debug/androidApp-debug.apk",
                 "androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk"]:
    path = root / relative
    with zipfile.ZipFile(path) as apk:
        dex = {name: apk.read(name) for name in apk.namelist() if name.endswith(".dex")}
    proof = {url: [{"dex": name, "byteOffset": data.find(url.encode()), "dexSha256": sha(data)}
                   for name, data in dex.items() if url.encode() in data] for url in urls}
    if relative.endswith("/androidApp-debug.apk"):
        assert all(proof.values()), "Actual normal DEX lacks configured API/catalog origins"
    apks[relative] = {"sha256": sha(path.read_bytes()), "bytes": path.stat().st_size, "actualDexUrls": proof}
print(json.dumps({"sourceCommit": source, "sourceTree": git("rev-parse", source + "^{tree}"),
    "apks": apks, "serverImage": manifest["imageId"], "serverManifestSha256": sha(raw),
    "retainedServerManifest": str((run / "server-application-image.json").relative_to(root)),
    "migrationSha256": {p.name: sha(p.read_bytes()) for p in migrations},
    "scope": "Local immutable build/provenance only; no device, native execution, push or deployment."}, indent=2))
