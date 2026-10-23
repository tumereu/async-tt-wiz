"""Focused packaging-contract regressions; fake APK inspection is not device evidence."""

import argparse
from copy import deepcopy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("package_release", ROOT / "scripts/package-release.py")
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


class PackageReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(dir="/tmp/opencode" if Path("/tmp/opencode").is_dir() else None)
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.metadata = json.loads((ROOT / "release/version.json").read_text())
        self.args = argparse.Namespace(apk=self.directory / "input.apk", output=self.directory / "bundle",
            source_commit="a" * 40, kind="release", aapt="aapt", apksigner="apksigner",
            expected_sha256=None, expected_certificate_sha256=None)
        self.args.apk.write_bytes(b"synthetic APK bytes; inspection mocked")
        self.identity = {"applicationId": self.metadata["applicationId"], **self.metadata["allocations"][-1],
                         "minSdk": 29, "targetSdk": 36, "debuggable": False}
        self.signing = {"status": "unsigned-or-invalid", "verified": False,
                        "certificateSha256": [], "verificationExitCode": 1}

    def git(self, *args):
        if args[0] == "rev-parse":
            return (self.args.source_commit + "\n").encode()
        if args[0] == "status":
            return b""
        if args[1].endswith(":" + release.VERSION_PATH):
            return json.dumps(self.metadata).encode()
        return b"pinned synthetic source"

    def package(self):
        with patch.object(release, "git", side_effect=self.git), patch.object(
                release, "inspect_apk", return_value=(self.identity, self.signing)):
            return release.package_apk(self.args)

    def test_unsigned_manifest_checksum_and_no_overwrite(self):
        manifest = self.package()
        apk = self.args.output / manifest["artifact"]["file"]
        expected = hashlib.sha256(self.args.apk.read_bytes()).hexdigest()
        self.assertEqual(expected, manifest["artifact"]["sha256"])
        self.assertEqual(expected, release.sha256(apk))
        manifest_hash = release.sha256(self.args.output / "manifest.json")
        self.assertEqual(f"{expected}  {apk.name}\n{manifest_hash}  manifest.json\n",
                         (self.args.output / "SHA256SUMS").read_text())
        self.assertEqual(manifest, json.loads((self.args.output / "manifest.json").read_text()))
        self.assertEqual(self.metadata["protocol"], manifest["protocol"])
        self.assertEqual(self.metadata["schemas"], manifest["schemas"])
        self.assertEqual("unsigned-or-invalid", manifest["signing"]["status"])
        self.assertFalse(manifest["artifactSigningReady"])
        self.assertFalse(manifest["releaseReady"])
        self.assertEqual("UNVERIFIED", manifest["qualification"])
        with self.assertRaisesRegex(ValueError, "already exists"):
            self.package()

    def test_missing_apk_bad_checksum_and_wrong_version_leave_no_bundle(self):
        self.args.apk = self.directory / "missing.apk"
        with self.assertRaisesRegex(ValueError, "missing"):
            self.package()
        self.args.apk = self.directory / "input.apk"
        self.args.expected_sha256 = "0" * 64
        with self.assertRaisesRegex(ValueError, "checksum"):
            self.package()
        self.args.expected_sha256 = None
        self.identity["versionCode"] += 1
        with self.assertRaisesRegex(ValueError, "allocation"):
            self.package()
        self.assertFalse(self.args.output.exists())

    def test_signature_requires_expected_public_identity_but_never_qualifies_release(self):
        self.signing.update(status="verified", verified=True, certificateSha256=["b" * 64], verificationExitCode=0)
        self.args.expected_certificate_sha256 = "c" * 64
        with self.assertRaisesRegex(ValueError, "certificate differs"):
            self.package()
        self.args.expected_certificate_sha256 = "b" * 64
        manifest = self.package()
        self.assertTrue(manifest["artifactSigningReady"])
        self.assertFalse(manifest["releaseReady"])

    def test_allocations_are_monotone(self):
        metadata = deepcopy(self.metadata)
        metadata["allocations"].append(metadata["allocations"][-1])
        with self.assertRaisesRegex(ValueError, "strictly increase"):
            release.read_metadata(json.dumps(metadata))


if __name__ == "__main__":
    unittest.main()
