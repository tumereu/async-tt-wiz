#!/usr/bin/env python3
"""Inventory upstream licenses/editions for I01a's directly selected artifacts.

Not provider/art clearance, a legal approval, or a complete distribution notice.
"""
from pathlib import Path
import tomllib
import urllib.request
import xml.etree.ElementTree as ET

versions = tomllib.loads(Path("gradle/libs.versions.toml").read_text())["versions"]
artifacts = [
    ("central", "org.jetbrains.kotlin", "kotlin-gradle-plugin", "kotlin"),
    ("central", "org.jetbrains.kotlin", "kotlin-stdlib", "kotlin"),
    ("central", "org.jetbrains.kotlinx", "kotlinx-serialization-json", "serialization"),
    ("google", "com.android.tools.build", "gradle", "agp"),
    ("google", "androidx.compose", "compose-bom", "compose-bom"),
    ("google", "androidx.activity", "activity-compose", "activity"),
    ("google", "androidx.test", "runner", "androidx-test-runner"),
    ("google", "androidx.test.ext", "junit", "androidx-test-junit"),
    ("central", "org.junit.jupiter", "junit-jupiter", "junit"),
]
repositories = {"central": "https://repo.maven.apache.org/maven2",
                "google": "https://dl.google.com/dl/android/maven2"}
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
for repository, group, artifact, version_key in artifacts:
    version = versions[version_key]
    url = f"{repositories[repository]}/{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"
    root = ET.fromstring(urllib.request.urlopen(url, timeout=60).read())
    licenses = [(node.findtext("m:name", namespaces=ns), node.findtext("m:url", namespaces=ns))
                for node in root.findall("m:licenses/m:license", ns)]
    assert licenses, f"No declared license: {url}"
    print(f"{group}:{artifact}:{version}\n  {url}\n  licenses={licenses}")
    assert all("apache" in (name + " " + link).lower() or "eclipse" in (name + " " + link).lower()
               for name, link in licenses)
print("PASS directly selected foundation artifacts declare Apache/EPL licenses; no paid edition selected")
print("NOT a transitive redistribution-notice audit or rights-attribution release-gate clearance")
