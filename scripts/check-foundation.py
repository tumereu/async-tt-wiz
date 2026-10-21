#!/usr/bin/env python3
"""Validate I01a's actual build outputs, signing, test paths and SDK/JVM boundary.

Run after the documented clean build. Does not start a device or claim E2E passed.
"""
import fnmatch
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parent.parent
MODULES = ["core", "protocol", "client-storage", "androidApp", "server", "e2e"]


def run(command):
    print("\n$ " + " ".join(map(str, command)), flush=True)
    result = subprocess.run(list(map(str, command)), stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True)
    print(result.stdout.rstrip())
    assert result.returncode == 0, (command, result.returncode)
    return result.stdout


def main():
    os.chdir(ROOT)
    state = json.loads(Path("docs/agent/implementation-state.json").read_text())
    test_paths = sorted(p.as_posix() for module in MODULES
                        for p in Path(module, "src").rglob("*.kt")
                        if any(part.endswith("Test") or part == "test" for part in p.parts))
    assert test_paths
    for path in test_paths:
        matches = [pattern for pattern in state["runner"]["reviewTestPaths"]
                   if fnmatch.fnmatchcase(path, pattern)]
        assert matches, f"Unreviewable test path: {path}"
        print("PASS reviewTestPaths:", path, "->", matches)
    for kind in ["commonTest", "jvmTest", "test", "androidTest"]:
        assert any(f"/src/{kind}/" in p for p in test_paths), kind
    # A later KMP instrumentation test may use this approved spelling; it does not
    # enable the new plugin's unapproved androidDeviceTest default path.
    representative = "core/src/androidInstrumentedTest/kotlin/ExampleTest.kt"
    assert any(fnmatch.fnmatchcase(representative, p) for p in state["runner"]["reviewTestPaths"])
    print("PASS representative future androidInstrumentedTest path (not an executed test)")

    for module in MODULES:
        path = Path(module, "gradle.lockfile")
        assert path.is_file() and "org.jetbrains.kotlin:kotlin-stdlib:2.4.21=" in path.read_text()
        print("PASS checked-in lock candidate:", path, hashlib.sha256(path.read_bytes()).hexdigest())
    plugin_lock = Path("buildscript-gradle.lockfile")
    assert "com.android.tools.build:gradle:9.3.3=" in plugin_lock.read_text()
    assert "org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21=" in plugin_lock.read_text()
    print("PASS plugin classpath lock candidate:", hashlib.sha256(plugin_lock.read_bytes()).hexdigest())

    count = 0
    for module, task in [("core", "jvmTest"), ("protocol", "jvmTest"), ("server", "test"), ("e2e", "test")]:
        reports = sorted(Path(module, "build/test-results", task).glob("TEST-*.xml"))
        assert reports, (module, "no executed test reports")
        for path in reports:
            data = path.read_text()
            suite = ET.fromstring(data)
            assert int(suite.attrib["tests"]) > 0
            assert all(int(suite.attrib[key]) == 0 for key in ["skipped", "failures", "errors"])
            count += int(suite.attrib["tests"])
            print("\nActual JUnit report:", path)
            print(data.rstrip())
    assert count >= 8
    print("PASS executed JVM test count:", count)

    for path, expected in [
        (Path("core/build/libs/core-jvm-0.1.0.jar"), 65),
        (Path("protocol/build/libs/protocol-jvm-0.1.0.jar"), 65),
        (Path("core/build/intermediates/compile_library_classes_jar/androidMain/bundleAndroidMainClassesToCompileJar/classes.jar"), 61),
        (Path("protocol/build/intermediates/compile_library_classes_jar/androidMain/bundleAndroidMainClassesToCompileJar/classes.jar"), 61),
    ]:
        with zipfile.ZipFile(path) as jar:
            classes = [name for name in jar.namelist() if name.endswith(".class")]
            assert classes, path
            majors = {struct.unpack(">H", jar.read(name)[6:8])[0] for name in classes}
            assert majors == {expected}, (path, majors)
        print("PASS classfile major:", path, expected, "classes=", len(classes))

    sdk = Path(os.environ["ANDROID_HOME"])
    tools = sdk / "build-tools/36.0.0"
    apk = Path("androidApp/build/outputs/apk/debug/androidApp-debug.apk")
    test_apk = Path("androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk")
    signing = []
    for path in [apk, test_apk]:
        badging = run([tools / "aapt2", "dump", "badging", path])
        assert "minSdkVersion:'29'" in badging and "targetSdkVersion:'36'" in badging
        signature = run([tools / "apksigner", "verify", "--verbose", "--print-certs",
                         "--min-sdk-version", "29", path])
        signing.append(re.search(r"Signer #1 certificate SHA-256 digest: (\w+)", signature).group(1))
        print("APK SHA256:", path, hashlib.sha256(path.read_bytes()).hexdigest())
    assert signing[0] == signing[1], "Application and instrumentation must use the same debug signer"
    print("PASS normal/test APK signatures compatible with API29; no release-key/upgrade qualification claimed")
    lint = Path("androidApp/build/reports/lint-results-debug.txt")
    print("\nActual Android lint report:")
    print(lint.read_text().rstrip())
    assert not re.search(r"\b[1-9][0-9]* errors?\b", lint.read_text())
    print("PASS foundation artifacts; Android runtime acceptance remains I01f, full matrix I35")


if __name__ == "__main__":
    main()
