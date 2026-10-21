#!/usr/bin/env python3
"""Inventory qualified community artifact licenses and the immutable PG17 index.

Read-only public upstream requests. Not a transitive/image notice audit or release
rights clearance; never prints the temporary public-registry pull bearer token.
"""
import hashlib
import json
from pathlib import Path
import tomllib
import urllib.request
import xml.etree.ElementTree as ET

VERSIONS = tomllib.loads(Path("gradle/libs.versions.toml").read_text())["versions"]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def pom_licenses(group, artifact, version):
    url = f"https://repo.maven.apache.org/maven2/{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"
    root = ET.fromstring(urllib.request.urlopen(url, timeout=60).read())
    licenses = [(node.findtext("m:name", namespaces=NS), node.findtext("m:url", namespaces=NS))
                for node in root.findall("m:licenses/m:license", NS)]
    if not licenses:
        parent = root.find("m:parent", NS)
        assert parent is not None, f"No license or parent: {url}"
        return pom_licenses(*(parent.findtext(f"m:{key}", namespaces=NS)
                              for key in ("groupId", "artifactId", "version")))
    return url, licenses


for group, artifact, key in [
    ("org.flywaydb", "flyway-core", "flyway"),
    ("org.flywaydb", "flyway-database-postgresql", "flyway"),
    ("org.jooq", "jooq", "jooq"),
    ("org.jooq", "jooq-codegen", "jooq"),
    ("org.jooq", "jooq-meta", "jooq"),
    ("org.postgresql", "postgresql", "postgresql"),
    ("org.testcontainers", "testcontainers-postgresql", "testcontainers"),
    ("org.testcontainers", "testcontainers", "testcontainers"),
]:
    version = VERSIONS[key]
    url, licenses = pom_licenses(group, artifact, version)
    assert all(any(allowed in (name + " " + link).lower() for allowed in ("apache", "mit", "bsd"))
               for name, link in licenses), (url, licenses)
    print(f"{group}:{artifact}:{version}\n  license source: {url}\n  licenses: {licenses}")

properties = Path("server/database-images.properties").read_text().splitlines()
image = next(line.split("=", 1)[1] for line in properties if line.startswith("postgres="))
digest = image.split("@", 1)[1]
token_url = "https://auth.docker.io/token?service=registry.docker.io&scope=repository:library/postgres:pull"
token = json.load(urllib.request.urlopen(token_url, timeout=60))["token"]
url = "https://registry-1.docker.io/v2/library/postgres/manifests/" + digest
request = urllib.request.Request(url, headers={"Authorization": "Bearer " + token,
                                            "Accept": "application/vnd.oci.image.index.v1+json"})
with urllib.request.urlopen(request, timeout=60) as response:
    data = response.read()
    assert "sha256:" + hashlib.sha256(data).hexdigest() == digest == response.headers["Docker-Content-Digest"]
manifest = json.loads(data)
platforms = [entry for entry in manifest["manifests"] if entry["platform"]["os"] == "linux"]
assert {entry["platform"]["architecture"] for entry in platforms} >= {"amd64", "arm64"}
assert all(entry["annotations"]["org.opencontainers.image.version"] == "17.11-bookworm" for entry in platforms)
print("PASS official PostgreSQL17 immutable multi-platform index:", image)
for entry in platforms:
    print(json.dumps(entry, sort_keys=True))
license_url = "https://www.postgresql.org/about/licence/"
license_text = urllib.request.urlopen(license_url, timeout=60).read().decode()
assert "PostgreSQL License" in license_text and "Permission to use, copy, modify" in license_text
print("PostgreSQL License source:", license_url)
print("PASS community artifacts, no paid jOOQ/Flyway edition; actual JVM21/database compatibility is checked by the build/tests")
print("NOT redistribution notices, image-layer license audit, provider/art clearance or arm64 execution qualification")
