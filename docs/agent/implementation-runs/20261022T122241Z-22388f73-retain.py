"""Retain exact executed output/JUnit and current service/shared inputs; no test reruns."""
import base64
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

prefix = Path('docs/agent/implementation-runs/20261022T122241Z-22388f73')
gradle = ['./scripts/gradle.sh', '--no-daemon', '--console=plain']
checks = {
    'compile': (gradle + [':protocol:jvmTest', ':server:compileKotlin'], 1),
    'compile2': (gradle + [':protocol:jvmTest', ':server:compileKotlin'], 0),
    'tests1': (gradle + [':protocol:jvmTest', ':server:test', '--tests', 'org.asyncttwiz.server.decks.DeckHttpTest',
                       '--tests', 'org.asyncttwiz.server.database.FoundationDatabaseTest', '--tests',
                       'org.asyncttwiz.server.accounts.AccountHttpTest', 'verifyModuleBoundaries'], 1),
    'tests2': (gradle + [':protocol:jvmTest', ':server:test', '--tests', 'org.asyncttwiz.server.decks.DeckHttpTest',
                       '--tests', 'org.asyncttwiz.server.database.FoundationDatabaseTest', '--tests',
                       'org.asyncttwiz.server.accounts.AccountHttpTest', 'verifyModuleBoundaries'], 1),
    'tests3': (gradle + [':protocol:jvmTest', ':server:test', '--tests', 'org.asyncttwiz.server.decks.DeckHttpTest',
                       'verifyModuleBoundaries'], 0),
}
for name, (command, exit_code) in checks.items():
    raw = Path(str(prefix) + '-' + name + '.txt')
    target = raw.with_suffix('.json')
    if raw.exists():
        data = raw.read_bytes()
        target.write_text(json.dumps(dict(command=command, exitCode=exit_code,
            outputSha256=hashlib.sha256(data).hexdigest(), output=data.decode('utf-8')),
            indent=2, ensure_ascii=False) + '\n')
    retained = json.loads(target.read_text())
    assert retained['exitCode'] == exit_code
    assert retained['outputSha256'] == hashlib.sha256(retained['output'].encode()).hexdigest()
print('PASS exact original command bytes preserved in whitespace-safe lossless JSON envelopes; diagnostics stay red')

junit = {}
for pattern in ('protocol/build/test-results/jvmTest/TEST-*.xml',
                'server/build/test-results/test/TEST-org.asyncttwiz.server.decks.DeckHttpTest.xml'):
    for path in sorted(Path('.').glob(pattern)):
        data = path.read_bytes()
        root = ET.fromstring(data)
        assert int(root.attrib['tests']) > 0
        assert all(int(root.attrib[key]) == 0 for key in ('failures', 'errors', 'skipped'))
        junit[str(path)] = dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest(),
                               base64=base64.b64encode(data).decode(), tests=int(root.attrib['tests']))
assert sum(item['tests'] for path, item in junit.items() if path.startswith('protocol/')) == 7
assert sum(item['tests'] for path, item in junit.items() if path.startswith('server/')) == 5
Path(str(prefix) + '-junit.json').write_text(json.dumps(junit, indent=2) + '\n')
print('PASS lossless actual XML: 7 shared protocol tests (3 new definition cases) and 5 real service/database/HTTP tests; no skipped/red required cases')

changed = subprocess.check_output(['git', 'diff', '--name-only'], text=True).splitlines() + \
    subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard'], text=True).splitlines()
sources = [Path(p) for p in changed if not p.startswith('docs/agent/')]
sources += [Path(p) for p in ('gradle/libs.versions.toml', 'server/gradle.lockfile', 'protocol/gradle.lockfile',
    'androidApp/gradle.lockfile', 'docs/agent/coverage/ledger.json',
    'tests/fixtures/catalogs/manifest.json', 'server/database-images.properties')]
sources += list(Path('server/build/generated-src/jooq').rglob('*.java'))
sources += list(Path('server/build/install/server/lib').glob('*.jar'))
sources += [Path('androidApp/build/outputs/apk/debug/androidApp-debug.apk')]
identities = {str(p): dict(bytes=p.stat().st_size, sha256=hashlib.sha256(p.read_bytes()).hexdigest())
              for p in sorted(set(sources))}
Path(str(prefix) + '-identities.json').write_text(json.dumps(identities, indent=2) + '\n')
print(f'PASS retained {len(identities)} exact current source/lock/catalog-manifest/ledger/generated-jOOQ/installed-runtime/normal-APK identities')

owned = set()
import re
for name in checks:
    output = json.loads(Path(str(prefix) + '-' + name + '.json').read_text())['output']
    owned.update(re.findall(r'(?:container=|is starting: )([a-f0-9]{64})', output))
current = set(subprocess.check_output(['docker', 'ps', '-aq', '--no-trunc'], text=True).splitlines())
assert not (owned & current), owned & current
print(f'PASS all {len(owned)} exact codegen/integration/reaper containers from this run absent; each PostgreSQL helper retains exact owned-volume cleanup')
print('No Compose/device provision, image publication, external provider request, broad suite or repeated green qualification executed')
