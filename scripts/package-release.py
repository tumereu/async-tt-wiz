#!/usr/bin/env python3
"""Package an already-built APK locally. Never build, sign, install or publish."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


REPO = Path(__file__).resolve().parents[1]
VERSION_PATH = "release/version.json"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def git(*arguments):
    result = subprocess.run(["git", "-C", str(REPO), *arguments], capture_output=True)
    require(result.returncode == 0, "Git source pin unavailable; use the committed source checkout")
    return result.stdout


def read_metadata(content):
    metadata = json.loads(content)
    require(metadata["manifestVersion"] == 1, "Unsupported version manifest")
    require(metadata["applicationId"] == "org.asyncttwiz.android", "Package identity changed")
    previous = 0
    require(bool(metadata["allocations"]), "Missing version allocation")
    for version in metadata["allocations"]:
        code = version["versionCode"]
        require(type(code) is int and previous < code <= 2100000000, "Version allocations must strictly increase")
        require(re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?", version["versionName"]),
                "Invalid versionName")
        previous = code
    # These are explicit source declarations, not an inferred compatibility window.
    require(bool(metadata["protocol"]) and bool(metadata["schemas"]) and bool(metadata["sourcePaths"]),
            "Missing protocol/schema/source declarations")
    return metadata


def inspect_apk(apk, aapt, apksigner):
    badging = subprocess.run([aapt, "dump", "badging", str(apk)], capture_output=True, text=True)
    require(badging.returncode == 0, "aapt could not inspect the APK")
    package = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'",
                        badging.stdout, re.MULTILINE)
    minimum = re.search(r"^sdkVersion:'([0-9]+)'", badging.stdout, re.MULTILINE)
    target = re.search(r"^targetSdkVersion:'([0-9]+)'", badging.stdout, re.MULTILINE)
    require(package is not None and minimum is not None and target is not None, "APK identity/SDK metadata missing")
    identity = {"applicationId": package[1], "versionCode": int(package[2]), "versionName": package[3],
                "minSdk": int(minimum[1]), "targetSdk": int(target[1]),
                "debuggable": "application-debuggable" in badging.stdout.splitlines()}
    verification = subprocess.run([apksigner, "verify", "--verbose", "--print-certs", str(apk)],
                                  capture_output=True, text=True)
    certificates = sorted(set(match.lower() for match in re.findall(
        r"^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$",
        verification.stdout, re.MULTILINE)))
    verified = verification.returncode == 0 and bool(certificates)
    signing = {"status": "verified" if verified else "unsigned-or-invalid", "verified": verified,
               "certificateSha256": certificates, "verificationExitCode": verification.returncode}
    return identity, signing


def package_apk(args):
    output = args.output.resolve()
    require(not output.is_relative_to(REPO.resolve()), "Output must be outside the source checkout")
    require(not output.exists(), "Output directory already exists; do not overwrite a retained artifact")
    require(args.apk.is_file(), "Already-built APK is missing")
    require(re.fullmatch(r"[0-9a-f]{40}", args.source_commit), "Use the full lowercase source commit")
    require(git("rev-parse", "HEAD").decode().strip() == args.source_commit, "Source pin is not checkout HEAD")
    require(not git("status", "--porcelain", "--untracked-files=normal").strip(), "Source checkout must be clean")
    metadata_bytes = git("show", f"{args.source_commit}:{VERSION_PATH}")
    metadata = read_metadata(metadata_bytes)
    version = metadata["allocations"][-1]
    sources = {VERSION_PATH: hashlib.sha256(metadata_bytes).hexdigest()}
    for path in metadata["sourcePaths"]:
        require(not Path(path).is_absolute() and ".." not in Path(path).parts, "Source paths must be repository-relative")
        sources[path] = hashlib.sha256(git("show", f"{args.source_commit}:{path}")).hexdigest()
    input_hash = sha256(args.apk)
    if args.expected_sha256:
        require(re.fullmatch(r"[0-9a-f]{64}", args.expected_sha256), "Expected APK SHA-256 must be lowercase hex")
        require(input_hash == args.expected_sha256, "APK checksum differs from expected SHA-256")
    if args.expected_certificate_sha256:
        require(re.fullmatch(r"[0-9a-f]{64}", args.expected_certificate_sha256), "Expected certificate SHA-256 must be lowercase hex")

    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".atw-package-", dir=output.parent) as temporary:
        staging = Path(temporary)
        apk = staging / "artifact.apk"
        shutil.copyfile(args.apk, apk)
        require(sha256(apk) == input_hash, "APK changed while copying")
        identity, signing = inspect_apk(apk, args.aapt, args.apksigner)
        require(sha256(apk) == input_hash, "APK changed during inspection")
        require(all(identity[key] == value for key, value in {
            "applicationId": metadata["applicationId"], **version, "minSdk": 29, "targetSdk": 36
        }.items()), "APK package/version/SDK does not match the committed allocation")
        if args.kind == "release":
            require(not identity["debuggable"], "A debuggable APK cannot be labeled release")
        expected = args.expected_certificate_sha256
        signing["expectedCertificateSha256"] = expected
        signing["identityMatched"] = bool(expected and signing["verified"] and
                                          signing["certificateSha256"] == [expected])
        # A missing/invalid signature is preserved honestly as a non-release artifact.
        if signing["verified"] and expected:
            require(signing["identityMatched"], "APK signing certificate differs from the expected identity")
        status = "signed" if signing["verified"] else "unsigned-or-invalid"
        filename = f"{metadata['applicationId']}-{version['versionName']}-{version['versionCode']}-{args.kind}-{status}.apk"
        apk.rename(staging / filename)
        manifest = {
            "manifestVersion": 1, "artifact": {"file": filename, "sha256": input_hash, **identity},
            "kind": args.kind, "signing": signing,
            "artifactSigningReady": args.kind == "release" and signing["identityMatched"],
            "releaseReady": False, "qualification": "UNVERIFIED",
            "source": {"commit": args.source_commit, "inputSha256": sources,
                       "buildProvenance": "Caller-supplied source pin; build provenance not independently verified"},
            "protocol": metadata["protocol"], "schemas": metadata["schemas"],
            "supportStatus": metadata["supportStatus"],
            "limitations": ["No device, upgrade, mixed-version, image or external-release gate is qualified by packaging"]
        }
        manifest_path = staging / "manifest.json"
        manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        (staging / "SHA256SUMS").write_text(
            f"{input_hash}  {filename}\n{sha256(manifest_path)}  manifest.json\n", encoding="ascii")
        # Publish only the complete local bundle; never touch the source tree or a network host.
        os.rename(staging, output)
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--kind", required=True, choices=("release", "test"))
    parser.add_argument("--aapt", default="aapt", help="Existing Android SDK aapt executable")
    parser.add_argument("--apksigner", default="apksigner", help="Existing Android SDK apksigner executable")
    parser.add_argument("--expected-sha256")
    parser.add_argument("--expected-certificate-sha256", help="Public certificate digest, not a key/password")
    args = parser.parse_args()
    try:
        manifest = package_apk(args)
    except (OSError, ValueError, KeyError, TypeError) as error:
        parser.exit(2, f"Packaging failed: {error}\n")
    print(f"{args.output.resolve()}/manifest.json: signing={manifest['signing']['status']}; releaseReady=false; qualification=UNVERIFIED")


if __name__ == "__main__":
    main()
