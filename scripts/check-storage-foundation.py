#!/usr/bin/env python3
"""Check I01d1's actual host/Android-build artifacts; never claims Android execution."""
import fnmatch
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import struct
import subprocess
import tomllib
import urllib.request
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parent.parent


def main():
    os.chdir(ROOT)
    versions = tomllib.loads(Path("gradle/libs.versions.toml").read_text())["versions"]
    locks = Path("client-storage/gradle.lockfile").read_text()
    for coordinate in (f"app.cash.sqldelight:runtime-jvm:{versions['sqldelight']}",
                       f"app.cash.sqldelight:sqlite-driver:{versions['sqldelight']}",
                       f"org.xerial:sqlite-jdbc:{versions['sqlite-jdbc']}"):
        assert coordinate + "=" in locks
        print("PASS exact locked storage dependency:", coordinate)
    plugin_lock = Path("buildscript-gradle.lockfile").read_text()
    assert f"app.cash.sqldelight:gradle-plugin:{versions['sqldelight']}=" in plugin_lock
    # Retain the actual upstream artifact POM/license provenance (not release clearance).
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    for group, artifact, version in [
        ("app.cash.sqldelight", artifact, versions["sqldelight"])
        for artifact in ("gradle-plugin", "runtime-jvm", "sqlite-driver", "jdbc-driver", "sqlite-3-18-dialect")
    ] + [("org.xerial", "sqlite-jdbc", versions["sqlite-jdbc"])]:
        url = f"https://repo.maven.apache.org/maven2/{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"
        data = urllib.request.urlopen(url, timeout=60).read()
        root = ET.fromstring(data)
        licenses = [(node.findtext("m:name", namespaces=ns), node.findtext("m:url", namespaces=ns))
                    for node in root.findall("m:licenses/m:license", ns)]
        assert licenses and all("apache" in (name + " " + link).lower() or "bsd" in (name + " " + link).lower()
                                for name, link in licenses), (url, licenses)
        print(f"UPSTREAM {group}:{artifact}:{version}\n  {url}\n  POM SHA256 {hashlib.sha256(data).hexdigest()}\n  {licenses}")

    fixture = Path("client-storage/src/main/sqldelight/databases/1.db")
    digest = hashlib.sha256(fixture.read_bytes()).hexdigest()
    assert digest == "5f88c2a52575c080088c59052091eb8749414a8d0ce25158186e06af1647d252"
    with sqlite3.connect(f"file:{fixture}?mode=ro", uri=True) as db:
        assert db.execute("PRAGMA user_version").fetchone() == (1,)
        assert db.execute("PRAGMA integrity_check").fetchone() == ("ok",)
        assert db.execute("PRAGMA foreign_key_check").fetchall() == []
        assert db.execute("SELECT revision FROM accepted_cursor ORDER BY scope_id").fetchall() == [(9007199254740993,), (17,)]
        assert "payload_version" not in [row[1] for row in db.execute("PRAGMA table_info(accepted_record)")]
        print("RETAINED v1 SHA256", digest)
        print("RETAINED schema", db.execute("SELECT name, sql FROM sqlite_master WHERE type='table' ORDER BY name").fetchall())
        print("RETAINED synthetic records", db.execute("SELECT * FROM accepted_record ORDER BY scope_id").fetchall())
        print("RETAINED synthetic personal state", db.execute("SELECT * FROM personal_state").fetchall())

    state = json.loads(Path("docs/agent/implementation-state.json").read_text())
    test_paths = list(Path("client-storage/src/test").rglob("*.kt"))
    assert test_paths
    for path in test_paths:
        assert any(fnmatch.fnmatchcase(path.as_posix(), pattern) for pattern in state["runner"]["reviewTestPaths"])
        print("PASS approved host-test path", path)
    total = 0
    for module, task in [("client-storage", "testDebugUnitTest"), ("core", "jvmTest"), ("protocol", "jvmTest")]:
        paths = sorted(Path(module, "build/test-results", task).glob("TEST-*.xml"))
        assert paths
        for path in paths:
            data = path.read_text()
            suite = ET.fromstring(data)
            assert int(suite.attrib["tests"]) > 0
            assert all(int(suite.attrib[key]) == 0 for key in ("failures", "errors", "skipped"))
            total += int(suite.attrib["tests"])
            print("ACTUAL JUNIT", path)
            print(data)
    assert total >= 22
    print("PASS actual storage/shared tests (no skips):", total)

    generated = Path("client-storage/build/generated/sqldelight/code/ClientDatabase")
    hashes = {}
    for variant in ("debug", "release"):
        files = sorted((generated / variant).rglob("*.kt"))
        assert len(files) >= 10
        hashes[variant] = {str(path.relative_to(generated / variant)): hashlib.sha256(path.read_bytes()).hexdigest() for path in files}
    assert hashes["debug"] == hashes["release"]
    print("PASS SQLDelight real generated schema/query sources identical for both Android variants:", json.dumps(hashes, sort_keys=True))
    jar_path = Path("client-storage/build/intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar")
    with zipfile.ZipFile(jar_path) as jar:
        classes = [name for name in jar.namelist() if name.endswith(".class")]
        assert classes and any("internaldb/ClientDatabase" in name for name in classes)
        majors = {name: struct.unpack(">H", jar.read(name)[6:8])[0] for name in classes}
        assert majors.pop("org/asyncttwiz/storage/R.class") == 52  # AGP-generated empty resource class.
        assert set(majors.values()) == {61}
        print("PASS storage/SQLDelight bytecode Java17, classes", len(majors), "; AGP resource R.class Java8")
    for module in ("core", "protocol"):
        for source in Path(module, "src").rglob("*.kt"):
            assert "sqldelight" not in source.read_text().lower() and "storage.internaldb" not in source.read_text()
    print("PASS no persistence imports in semantic/protocol sources")
    for path in [Path("androidApp/build/outputs/apk/debug/androidApp-debug.apk"),
                 Path("androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk")]:
        assert path.is_file()
        print("NORMAL scaffold APK (not a runtime test)", path, hashlib.sha256(path.read_bytes()).hexdigest())
        tools = Path(os.environ["ANDROID_HOME"], "build-tools/36.0.0")
        badging = subprocess.run([str(tools / "aapt2"), "dump", "badging", str(path)], capture_output=True, text=True)
        assert badging.returncode == 0 and "minSdkVersion:'29'" in badging.stdout and "targetSdkVersion:'36'" in badging.stdout
        print(badging.stdout)
        signature = subprocess.run([str(tools / "apksigner"), "verify", "--verbose", "--min-sdk-version", "29", str(path)], capture_output=True, text=True)
        assert signature.returncode == 0
        print(signature.stdout)
        with zipfile.ZipFile(path) as apk:
            assert not any("sqlitejdbc" in name.lower() for name in apk.namelist()), "Host-only SQLite native library leaked into APK"
    for module in ("client-storage", "androidApp"):
        path = Path(module, "build/reports/lint-results-debug.txt")
        text = path.read_text()
        print("ACTUAL LINT", path, "\n", text)
        assert "0 errors" in text or text == "No issues found."
    result = subprocess.run([str(Path(os.environ["JAVA_HOME"], "bin/java")), "-version"], capture_output=True, text=True)
    assert result.returncode == 0 and 'version "21.' in result.stderr
    print("BUILD/HOST JVM", result.stderr)
    print("NOT Android-driver execution, auth/account isolation, sync/full-tree acceptance, redistribution notices or release-gate clearance")


if __name__ == "__main__":
    main()
