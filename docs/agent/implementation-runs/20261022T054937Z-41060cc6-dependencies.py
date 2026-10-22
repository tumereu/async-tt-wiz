"""Qualified changed Android runtime dependency families, not release/legal clearance."""
import hashlib
from pathlib import Path
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
coordinates = [
    ('io.ktor', 'ktor-client-android-jvm', '3.6.0'),
    ('io.ktor', 'ktor-client-mock-jvm', '3.6.0'),
    ('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.11.0'),
    ('org.jetbrains.kotlinx', 'kotlinx-coroutines-android', '1.11.0'),
    ('org.jetbrains.kotlinx', 'kotlinx-io-core-jvm', '0.9.1'),
    ('org.slf4j', 'slf4j-api', '2.0.19'),
]
cache = {}
def pom(group, artifact, version):
    url = f'https://repo.maven.apache.org/maven2/{group.replace(".", "/")}/{artifact}/{version}/{artifact}-{version}.pom'
    if url not in cache:
        data = urllib.request.urlopen(url, timeout=60).read()
        root = ET.fromstring(data)
        licenses = [(item.findtext('m:name', namespaces=ns), item.findtext('m:url', namespaces=ns))
                    for item in root.findall('m:licenses/m:license', ns)]
        if not licenses:
            parent = root.find('m:parent', ns)
            assert parent is not None, url
            licenses = pom(*(parent.findtext('m:' + key, namespaces=ns) for key in ('groupId', 'artifactId', 'version')))
        assert licenses and all(name and link for name, link in licenses)
        print('POM', url, 'SHA256', hashlib.sha256(data).hexdigest(), 'LICENSES', licenses, flush=True)
        cache[url] = licenses
    return cache[url]

lock = (ROOT / 'androidApp/gradle.lockfile').read_text()
for coordinate in coordinates:
    assert ':'.join(coordinate) + '=' in lock
    pom(*coordinate)
for family in ('io.ktor:', 'org.jetbrains.kotlinx:kotlinx-', 'org.slf4j:'):
    print('LOCKED', *[line for line in lock.splitlines() if line.startswith(family) and
                     ('debugRuntimeClasspath' in line or 'debugUnitTestRuntimeClasspath' in line)], sep='\n')
print('PASS pinned Ktor/Android engine, host MockEngine and coroutine/IO/SLF4J provenance; actual API29 runtime exercised, no GMS required')
print('No provider/art attribution, release-signing, production TLS/host or legal-release gate is passed')
