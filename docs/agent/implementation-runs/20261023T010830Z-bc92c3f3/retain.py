"""Retain exact affected JUnit, native/compiled input identities and setup diagnostics."""
from pathlib import Path
import base64
import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET

prefix = Path(__file__).parent
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def save(name, value): (prefix / name).write_text(json.dumps(value, indent=2) + '\n')

tests = {}
for path in sorted(Path('androidApp/build/test-results/testDebugUnitTest').glob('*.xml')):
    data = path.read_bytes(); root = ET.fromstring(data)
    assert all(int(root.attrib[key]) == 0 for key in ('failures', 'errors', 'skipped'))
    tests[str(path)] = dict(base64=base64.b64encode(data).decode(), sha256=sha(path), tests=root.attrib)
assert sum(int(value['tests']['tests']) for value in tests.values()) == 30
save('client-junit.json', tests)

diagnostics = []
installed = []
for i in (1, 2):
    directory = Path('/tmp/opencode/atw-device-bc92c3f300000000000000000000000' + str(i))
    for line in (directory / 'commands.jsonl').read_text().splitlines():
        record = json.loads(line)
        if record.get('exitCode', 0) != 0: diagnostics.append(record)
    path = directory / 'normal.apk'
    installed.append(dict(device=directory.name, bytes=path.stat().st_size, sha256=sha(path),
        installation=json.loads((directory / 'app-installed.json').read_text())))
assert len({item['sha256'] for item in installed}) == 1
save('setup-readback-diagnostic.json', diagnostics)

paths = set()
for directory in ('androidApp/src/main', 'protocol/src/commonMain', 'server/src/main', 'tests/fixtures/catalogs'):
    paths.update(str(p) for p in Path(directory).rglob('*') if p.is_file())
paths.update(['androidApp/src/test/kotlin/org/asyncttwiz/android/LobbyStartTest.kt',
    'e2e/src/test/kotlin/org/asyncttwiz/e2e/NativeGameStartTest.kt', 'e2e/environment.py', 'tests/test_environment_game_reset.py',
    'build.gradle.kts', 'gradle/libs.versions.toml', 'gradle.properties', 'androidApp/build.gradle.kts',
    'protocol/build.gradle.kts', 'server/build.gradle.kts', 'e2e/build.gradle.kts',
    'androidApp/gradle.lockfile', 'protocol/gradle.lockfile', 'server/gradle.lockfile', 'e2e/gradle.lockfile'])
final_apk = Path('androidApp/build/outputs/apk/debug/androidApp-debug.apk')
save('inputs.json', dict(finalSources={path: sha(Path(path)) for path in sorted(paths)},
    nativeInstalled=installed, finalCompiledApk=dict(path=str(final_apk), sha256=sha(final_apk), bytes=final_apk.stat().st_size),
    serverImage=json.loads(Path('server/build/application-image.json').read_text()),
    nativeInputSupersession='The native journey used the identical installed APK recorded above. Afterwards only GameSession accepted-replica revision retirement/correlation changed: HTTP summary high-water cannot retire prediction before its actual replica. All 30 affected final host cases and incremental normal APK compile pass. The earlier native output is reused for unchanged controls/rendering/once-only initialization, not represented as a new final-APK device run.'))
print('PASS retained 30 final affected host JUnit cases, exact two installed native APK identities, final source/build/fixture identities, compiled APK and current server image')
print('PASS retained original nonzero setup command output; partial readback is diagnostic, never an installed-artifact pass')
