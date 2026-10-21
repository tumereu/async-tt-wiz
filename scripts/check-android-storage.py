#!/usr/bin/env python3
"""Check packaged I01d2 interfaces/exclusions. Compilation is NOT device execution."""
import fnmatch
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import tomllib
import urllib.request
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parent.parent
DOMAINS = {"root", "file", "database", "sharedpref", "external", "device_root",
           "device_file", "device_database", "device_sharedpref"}


def run(*command):
    print("$", " ".join(map(str, command)), flush=True)
    result = subprocess.run(list(map(str, command)), capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return result.stdout


def tree_from_aapt(text):
    """Decode aapt2's element/string-attribute tree (the rules contain only strings)."""
    stack = []
    root = None
    for line in text.splitlines():
        element = re.match(r"^(\s*)E: ([\w-]+)", line)
        if element:
            indent, name = element.groups()
            node = ET.Element(name)
            while stack and stack[-1][0] >= len(indent):
                stack.pop()
            if stack:
                stack[-1][1].append(node)
            else:
                assert root is None
                root = node
            stack.append((len(indent), node))
        attribute = re.match(r'^\s*A: (\w+)="([^"]*)"', line)
        if attribute:
            stack[-1][1].set(*attribute.groups())
    assert root is not None
    return root


def validate_rules(root, extraction):
    assert root.tag == ("data-extraction-rules" if extraction else "full-backup-content")
    if extraction:
        assert [child.tag for child in root] == ["cloud-backup", "device-transfer"]
        sections = list(root)
    else:
        sections = [root]
    for section in sections:
        assert len(section) == len(DOMAINS)
        assert all(child.tag == "exclude" and child.attrib.get("path") == "." for child in section)
        assert {child.attrib.get("domain") for child in section} == DOMAINS


def main():
    os.chdir(ROOT)
    versions = tomllib.loads(Path("gradle/libs.versions.toml").read_text())["versions"]
    version = versions["sqldelight"]
    coordinate = f"app.cash.sqldelight:android-driver:{version}="
    for module in ("androidApp", "client-storage"):
        locks = Path(module, "gradle.lockfile").read_text()
        assert coordinate in locks
        print("PASS strictly locked Android driver", module, coordinate)
        print("LOCKED Android SQLite interfaces", *[line for line in locks.splitlines()
              if line.startswith("androidx.sqlite:")], sep="\n")
    pom_url = f"https://repo.maven.apache.org/maven2/app/cash/sqldelight/android-driver/{version}/android-driver-{version}.pom"
    data = urllib.request.urlopen(pom_url, timeout=60).read()
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    pom = ET.fromstring(data)
    licenses = [(node.findtext("m:name", namespaces=ns), node.findtext("m:url", namespaces=ns))
                for node in pom.findall("m:licenses/m:license", ns)]
    assert licenses and all("apache" in (name + " " + url).lower() for name, url in licenses)
    print("UPSTREAM", pom_url, "SHA256", hashlib.sha256(data).hexdigest(), licenses)
    sources_url = pom_url[:-4] + "-sources.jar"
    sources = urllib.request.urlopen(sources_url, timeout=60).read()
    with zipfile.ZipFile(io.BytesIO(sources)) as jar:
        driver_source = jar.read(next(name for name in jar.namelist() if name.endswith("/AndroidSqliteDriver.kt"))).decode()
    assert "database: SupportSQLiteDatabase," in driver_source
    assert "database.beginTransactionNonExclusive()" in driver_source and "database.close()" in driver_source
    print("QUALIFIED public raw-database constructor / Android transaction / close boundary",
          sources_url, "SHA256", hashlib.sha256(sources).hexdigest())

    tools = Path(os.environ["ANDROID_HOME"], "build-tools/36.0.0")
    apk_path = Path("androidApp/build/outputs/apk/debug/androidApp-debug.apk")
    test_path = Path("androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk")
    aapt = tools / "aapt2"
    manifest = run(aapt, "dump", "xmltree", apk_path, "--file", "AndroidManifest.xml")
    print("ACTUAL PACKAGED MANIFEST\n", manifest)
    assert re.search(r"allowBackup\([^\n]*\)=(?:false|\(type 0x12\)0x0)(?:\n|$)", manifest)
    assert manifest.count('android:name') >= 1 and 'org.asyncttwiz.android.MainActivity' in manifest
    assert ".TestActivity" not in manifest and "testOnly" not in manifest
    resources = run(aapt, "dump", "resources", apk_path)
    for attribute, name, extraction in [("fullBackupContent", "backup_rules", False),
                                         ("dataExtractionRules", "data_extraction_rules", True)]:
        resource_line = next(line for line in resources.splitlines()
                             if re.search(r"resource 0x[0-9a-f]+ (?:[\w.]+:)?xml/" + name + r"$", line))
        print("ACTUAL RESOURCE", resource_line)
        resource_id = re.search(r"resource (0x[0-9a-f]+)", resource_line).group(1)
        assert re.search(attribute + r"[^\n]*=@" + resource_id, manifest)
        text = run(aapt, "dump", "xmltree", apk_path, "--file", f"res/xml/{name}.xml")
        print("ACTUAL PACKAGED EXCLUSIONS\n", text)
        tree = tree_from_aapt(text)
        validate_rules(tree, extraction)
        source = ET.parse(f"androidApp/src/main/res/xml/{name}.xml").getroot()
        validate_rules(source, extraction)
        def structure(node):
            return node.tag, node.attrib, [structure(child) for child in node]
        assert structure(source) == structure(tree)
        # Mutation regressions: a missing private domain or transfer policy cannot pass.
        section = tree[1] if extraction else tree
        section.remove(section[0])
        try:
            validate_rules(tree, extraction)
        except AssertionError:
            pass
        else:
            raise AssertionError("Missing private exclusion accepted")
    print("PASS compiled manifest references the exact verified legacy/cloud/device-transfer rules; all private storage excluded")

    with zipfile.ZipFile(apk_path) as apk, zipfile.ZipFile(test_path) as tests:
        assert "assets/1.db" not in apk.namelist()
        assert not any(name.endswith(".db") for name in apk.namelist())
        assert hashlib.sha256(tests.read("assets/1.db")).hexdigest() == "5f88c2a52575c080088c59052091eb8749414a8d0ce25158186e06af1647d252"
        dex = b"".join(apk.read(name) for name in apk.namelist() if re.fullmatch(r"classes\d*\.dex", name))
        for name in ("app/cash/sqldelight/driver/android/AndroidSqliteDriver", "org/asyncttwiz/storage/RawAndroidDatabase",
                     "org/asyncttwiz/storage/AndroidClientStorage", "org/asyncttwiz/android/StorageViewModel"):
            assert ("L" + name + ";").encode() in dex, name
        for text in ("Local storage could not be opened. Your data has not been erased.",
                     "Local data needs a newer app. Update the app without uninstalling.", "Retry"):
            assert text.encode() in dex
        test_dex = b"".join(tests.read(name) for name in tests.namelist() if re.fullmatch(r"classes\d*\.dex", name))
        state = json.loads(Path("docs/agent/implementation-state.json").read_text())
        count = 0
        for source_path in sorted(Path("androidApp/src/androidTest").rglob("*.kt")):
            assert any(fnmatch.fnmatchcase(source_path.as_posix(), pattern) for pattern in state["runner"]["reviewTestPaths"])
            assert source_path.stem.encode() in test_dex
            methods = re.findall(r"@Test\s+fun\s+(\w+)", source_path.read_text())
            assert methods
            for method in methods:
                assert method.encode() in test_dex
            count += len(methods)
            print("COMPILED, NOT EXECUTED approved normal-app instrumentation", source_path, methods)
        assert count >= 14
        print("PASS", count, "compiled test methods; retained-v1 data is test-only, no fixture DB in app")

    adapter = Path("client-storage/src/main/kotlin/org/asyncttwiz/storage/AndroidClientStorage.kt").read_text()
    owner = Path("androidApp/src/main/kotlin/org/asyncttwiz/android/StorageViewModel.kt").read_text()
    assert "noBackupFilesDir" in adapter and "setForeignKeyConstraintsEnabled(true)" in adapter
    assert "Looper.myLooper() != Looper.getMainLooper()" in adapter
    assert "deleteDatabase(" not in adapter and "delete(" not in adapter and "SQLiteOpenHelper(" not in adapter
    assert "Executors.newSingleThreadExecutor" in owner and "worker.shutdown()" in owner
    assert "main.post { if (!cleared) state = next }" in owner
    print("INSPECTED production path/main-thread guard, raw pool-FK/error-handler policy, single-worker open/close and cleared-owner callback fencing")
    print("NOT Android execution, transport backup exercise, auth/account isolation, full-tree sync, signed upgrade or release qualification")


if __name__ == "__main__":
    main()
