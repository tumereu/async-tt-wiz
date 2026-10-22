"""Read-only provenance for I02a's newly locked runtime dependencies, not release clearance."""
import hashlib
from pathlib import Path
import urllib.request
import xml.etree.ElementTree as ET

NS = {"m": "http://maven.apache.org/POM/4.0.0"}
lock = Path("server/gradle.lockfile").read_text().splitlines()
coordinates = ["org.springframework.security:spring-security-web", "org.springframework.security:spring-security-core",
               "org.springframework.security:spring-security-crypto", "com.ibm.icu:icu4j", "org.bouncycastle:bcprov-jdk18on"]


def pom(group, artifact, version):
    url = f"https://repo.maven.apache.org/maven2/{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"
    data = urllib.request.urlopen(url, timeout=60).read()
    root = ET.fromstring(data)
    licenses = [(node.findtext("m:name", namespaces=NS), node.findtext("m:url", namespaces=NS))
                for node in root.findall("m:licenses/m:license", NS)]
    print("POM", url, "sha256", hashlib.sha256(data).hexdigest(), "direct licenses", licenses)
    if not licenses:
        if (group, artifact, version) == ("org.bouncycastle", "bcprov-jdk18on", "1.84"):
            source = "https://raw.githubusercontent.com/bcgit/bc-java/r1rv84/LICENSE.html"
            text = urllib.request.urlopen(source, timeout=60).read()
            assert b"Permission is hereby granted, free of charge" in text
            print("EXACT upstream release-tag license", source, "sha256", hashlib.sha256(text).hexdigest(), text.decode())
            return [("Bouncy Castle MIT-style license", source)]
        parent = root.find("m:parent", NS)
        assert parent is not None, "Missing license provenance"
        return pom(*(parent.findtext(f"m:{key}", namespaces=NS) for key in ("groupId", "artifactId", "version")))
    return licenses


for coordinate in coordinates:
    entries = [line.split("=", 1)[0] for line in lock if line.startswith(coordinate + ":") and
               "runtimeClasspath" in line.split("=", 1)[1].split(",")]
    assert len(entries) == 1
    group, artifact, version = entries[0].split(":")
    licenses = pom(group, artifact, version)
    print(entries[0], "licenses including inherited provenance", licenses)
    jar = Path("server/build/install/server/lib", f"{artifact}-{version}.jar")
    assert jar.is_file()
    print("ACTUAL qualified distribution JAR", jar, "sha256", hashlib.sha256(jar.read_bytes()).hexdigest())
print("PASS exact locked Spring Security/Bouncy Castle/ICU provenance; actual compatibility/semantics checked by focused JVM/HTTP/PostgreSQL/image tests")
print("NOT final notices/legal/provider/art rights or Android/arm64/release qualification")
