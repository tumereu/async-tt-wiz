#!/usr/bin/env python3
"""Build a local-only runtime image from clean locked sources and disposable jOOQ DBs."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent.parent


def run(*args):
    print("COMMAND", json.dumps(args), flush=True)
    subprocess.run(args, cwd=ROOT, check=True)


def output(*args):
    return subprocess.check_output(args, cwd=ROOT, text=True).strip()


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--platform", choices=["linux/amd64", "linux/arm64"], default="linux/amd64")
    args = parser.parse_args()
    # No skip-build switch: an old installed distribution is not source provenance.
    run("./scripts/gradle.sh", "--no-daemon", "--console=plain", "--no-build-cache",
        ":core:clean", ":protocol:clean", ":server:clean", ":server:installDist", "verifyModuleBoundaries")
    context = ROOT / "server/build/image-context"
    context.mkdir()
    (context / "lib").mkdir()
    jars = sorted((ROOT / "server/build/install/server/lib").glob("*.jar"))
    assert jars
    forbidden = ("testcontainers", "jooq-codegen", "jooq-meta", "docker-java", "junit", "gradle")
    for jar in jars:
        assert not jar.is_symlink() and not any(name in jar.name for name in forbidden), jar.name
        shutil.copyfile(jar, context / "lib" / jar.name)
        (context / "lib" / jar.name).chmod(0o444)
    shutil.copyfile(ROOT / "server/Dockerfile", context / "Dockerfile")
    shutil.copyfile(ROOT / "server/image.dockerignore", context / ".dockerignore")
    base = re.search(r"^FROM (\S+)$", (context / "Dockerfile").read_text(), re.M).group(1)
    assert re.fullmatch(r"eclipse-temurin:[\w.-]+@sha256:[0-9a-f]{64}", base)
    libraries = {jar.name: digest(jar) for jar in jars}
    distribution_digest = hashlib.sha256(json.dumps(libraries, sort_keys=True).encode()).hexdigest()
    revision = output("git", "rev-parse", "HEAD")
    iid = ROOT / "server/build/application-image.id"
    # BuildKit's content cache alone does not make the base inspectable by name.
    # Retain the exact locally selected platform/base identity for qualification.
    run("docker", "pull", "--platform", args.platform, base)
    # There is no registry push/tag, Docker socket in a builder, or networked RUN.
    run("docker", "build", "--pull", "--network=none", "--platform", args.platform,
        "--iidfile", str(iid), "--label", "org.opencontainers.image.revision=" + revision,
        "--label", "org.asyncttwiz.distribution.sha256=" + distribution_digest, str(context))
    image_id = iid.read_text().strip()
    assert re.fullmatch(r"sha256:[0-9a-f]{64}", image_id)
    inspect = json.loads(output("docker", "image", "inspect", image_id))[0]
    assert args.platform == inspect["Os"] + "/" + inspect["Architecture"]
    manifest = {
        "imageId": image_id, "platform": args.platform, "base": base,
        "sourceCommit": revision,
        "dockerfileSha256": digest(context / "Dockerfile"),
        "distributionSha256": distribution_digest, "libraries": libraries,
        "generatedSchema": {str(p.relative_to(ROOT / "server/build/generated-src/jooq")): digest(p)
                            for p in sorted((ROOT / "server/build/generated-src/jooq").rglob("*.java"))},
        "qualification": "local build only; lifecycle tests separate; arm64 not qualified by an amd64 build",
    }
    destination = ROOT / "server/build/application-image.json"
    destination.write_text(json.dumps(manifest, indent=2) + "\n")
    print(destination.relative_to(ROOT), destination.read_text(), flush=True)


if __name__ == "__main__":
    main()
