import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class BuildDeviceApkTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "scripts").mkdir()
        shutil.copy(Path(__file__).resolve().parents[2] / "scripts/build-device-apk.sh",
                    self.root / "scripts/build-device-apk.sh")
        self.write_executable("scripts/gradle.sh", """#!/usr/bin/env bash
printf '%s\\n' "$@" >> "$TEST_LOG"
[[ ${FAIL_BUILD:-false} != true ]] || exit 1
variant=release
[[ $1 != *assembleDebug ]] || variant=debug
mkdir -p "androidApp/build/outputs/apk/$variant"
touch "androidApp/build/outputs/apk/$variant/androidApp-$variant.apk"
""")
        self.write_executable("adb", """#!/usr/bin/env bash
printf 'adb %s\\n' "$*" >> "$TEST_LOG"
if [[ $1 == devices ]]; then printf '%s\\n' "$TEST_DEVICES"; fi
""")
        self.env = dict(os.environ, ATW_API_ORIGIN="https://tabletop.example:10000",
                        ATW_ADB=str(self.root / "adb"), TEST_LOG=str(self.root / "log"),
                        TEST_DEVICES="List of devices attached\nphone device\nemulator-5564 device")
        for key in ("ATW_ADB_PORT", "ATW_ADB_HOST", "FAIL_BUILD"):
            self.env.pop(key, None)

    def write_executable(self, name, content):
        path = self.root / name
        path.write_text(content)
        path.chmod(0o755)

    def run_script(self, *args):
        return subprocess.run(["bash", "scripts/build-device-apk.sh", *args],
                              cwd=self.root, env=self.env, capture_output=True, text=True)

    def log(self):
        path = self.root / "log"
        return path.read_text() if path.exists() else ""

    def test_debug_selects_phone_and_enables_switching(self):
        result = self.run_script("debug")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn(":androidApp:assembleDebug", self.log())
        self.assertIn("-PatwAccountSwitcher=true", self.log())
        self.assertIn("-PatwCatalogTestOrigin=\n", self.log())
        self.assertIn("adb -s phone push", self.log())
        self.assertIn("adb -s phone install -r", self.log())
        self.assertNotIn("adb -s emulator-", self.log())

    def test_release_and_explicit_serial(self):
        result = self.run_script("release", "emulator-5564")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn(":androidApp:assembleRelease", self.log())
        self.assertIn("-PatwSelfHosted=true", self.log())
        self.assertIn("-PatwAccountSwitcher=false", self.log())
        self.assertIn("adb -s emulator-5564 install -r", self.log())

    def test_missing_unauthorized_and_ambiguous_devices_never_build(self):
        for devices in ("", "phone unauthorized", "phone device\nother device", "emulator-5564 device"):
            with self.subTest(devices=devices):
                self.env["TEST_DEVICES"] = devices
                result = self.run_script("debug")
                self.assertNotEqual(0, result.returncode)
                self.assertNotIn("assemble", self.log())

    def test_failed_build_does_not_install_stale_apk(self):
        self.env["FAIL_BUILD"] = "true"
        result = self.run_script("release")
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn(" install ", self.log())
        self.assertNotIn(" push ", self.log())

    def test_invalid_variant_or_insecure_origin_never_touches_device(self):
        self.assertEqual(2, self.run_script("invalid").returncode)
        self.env["ATW_API_ORIGIN"] = "http://127.0.0.1:8080"
        self.assertEqual(2, self.run_script("debug").returncode)
        self.assertEqual("", self.log())


if __name__ == "__main__":
    unittest.main()
