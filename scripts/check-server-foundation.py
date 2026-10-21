#!/usr/bin/env python3
"""Check actual database foundation outputs/boundaries and print executed test XML."""
import fnmatch
import hashlib
import json
from pathlib import Path
import struct
import tomllib
import xml.etree.ElementTree as ET
import zipfile

versions = tomllib.loads(Path("gradle/libs.versions.toml").read_text())["versions"]
lock = Path("server/gradle.lockfile").read_text()
for coordinate, key in [("org.flywaydb:flyway-core", "flyway"), ("org.flywaydb:flyway-database-postgresql", "flyway"),
                        ("org.jooq:jooq", "jooq"), ("org.jooq:jooq-codegen", "jooq"),
                         ("org.postgresql:postgresql", "postgresql"), ("org.testcontainers:testcontainers", "testcontainers")]:
    assert f"{coordinate}:{versions[key]}=" in lock
    print("PASS pinned/locked:", coordinate, versions[key])

state = json.loads(Path("docs/agent/implementation-state.json").read_text())
for path in sorted(Path("server/src/test").rglob("*.kt")):
    assert any(fnmatch.fnmatchcase(path.as_posix(), pattern) for pattern in state["runner"]["reviewTestPaths"])
    print("PASS approved test source:", path)

reports = sorted(Path("server/build/test-results/test").glob("TEST-*.xml"))
assert reports
tests = 0
for path in reports:
    root = ET.fromstring(path.read_text())
    assert all(int(root.attrib[key]) == 0 for key in ("skipped", "failures", "errors")), path
    tests += int(root.attrib["tests"])
    print("Actual JUnit report:", path)
    print(path.read_text())
assert tests >= 10
ownership = ET.parse("server/build/test-results/test/TEST-org.asyncttwiz.server.database.WriterOwnershipTest.xml").getroot()
assert int(ownership.attrib["tests"]) >= 12
print("PASS actual server JVM21 tests without skipped/failing cases:", tests)
for name, minimum in (("ServiceProcessTest", 7), ("ServiceRuntimeTest", 2), ("SpringStartupTest", 1)):
    root = ET.parse(f"server/build/test-results/test/TEST-org.asyncttwiz.server.{name}.xml").getroot()
    assert int(root.attrib["tests"]) >= minimum
    print("PASS executed Spring HTTP/process/admission checks:", name, root.attrib["tests"])
for line in lock.splitlines():
    if line.startswith("org.jooq:jooq:"):
        assert line.startswith(f"org.jooq:jooq:{versions['jooq']}="), "Spring BOM drifted qualified jOOQ version"
assert f"org.springframework.boot:spring-boot:{versions['spring-boot']}=" in lock

jar = Path("server/build/libs/server-0.1.0.jar")
with zipfile.ZipFile(jar) as archive:
    classes = [name for name in archive.namelist() if name.endswith(".class")]
    assert classes
    assert {struct.unpack(">H", archive.read(name)[6:8])[0] for name in classes} == {65}
    assert "org/asyncttwiz/server/generated/tables/ServiceWriter.class" in classes
    assert "org/asyncttwiz/server/database/MigrationCommand.class" in classes
    assert "org/asyncttwiz/server/database/WriterOwnership.class" in classes
    assert "org/asyncttwiz/server/ServiceCommand.class" in classes
    assert "org/asyncttwiz/server/SpringService.class" in classes
    assert not any("/tools/" in name for name in classes), "Code generator leaked into runtime"
print("PASS real server/generated-field Java21 classfiles; runtime JAR SHA256:", hashlib.sha256(jar.read_bytes()).hexdigest())

for directory in ("server/build/install/server/lib", "androidApp/build/outputs/apk/debug"):
    assert Path(directory).is_dir(), directory
libraries = [path.name for path in Path("server/build/install/server/lib").glob("*.jar")]
assert not any(any(forbidden in name for forbidden in ("testcontainers", "jooq-codegen", "jooq-meta", "docker-java", "junit")) for name in libraries)
print("PASS migration distribution excludes generation/integration tooling:", sorted(libraries))
generated = sorted(Path("server/build/generated-src/jooq").rglob("*.java"))
assert len(generated) >= 6
for path in generated:
    print("Real generated source SHA256:", path, hashlib.sha256(path.read_bytes()).hexdigest())
for module in ("core", "protocol", "client-storage", "androidApp", "e2e"):
    for path in Path(module, "src").rglob("*.*"):
        if path.suffix in (".kt", ".java"):
            assert "org.asyncttwiz.server.generated" not in path.read_text(), path
print("PASS generated persistence/ownership/Spring stays server-only; service health/ownership is foundation only, not image/gameplay workers")
