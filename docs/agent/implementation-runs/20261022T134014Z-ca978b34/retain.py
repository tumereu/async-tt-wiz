"""Retain current inputs/results and read-only synthetic accepted-state corroboration."""
from pathlib import Path
import base64
import hashlib
import json
import sys
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path.cwd()))
from e2e import environment as e
out = Path(__file__).parent
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
paths = []
for directory in ('androidApp/src', 'protocol/src', 'server/src', 'tests/fixtures/catalogs'):
    paths.extend(p for p in Path(directory).rglob('*') if p.is_file())
paths.extend(Path('.').glob('**/gradle.lockfile'))
paths.extend([Path('gradle/libs.versions.toml'), Path('server/build/application-image.json'),
    Path('androidApp/build/outputs/apk/debug/androidApp-debug.apk'),
    Path('e2e/src/test/kotlin/org/asyncttwiz/e2e/NativeDeckManagementTest.kt'), Path('e2e/environment.py')])
(out / 'artifact-identities.json').write_text(json.dumps({str(p): {'sha256': sha(p), 'bytes': p.stat().st_size}
    for p in sorted(set(paths))}, indent=2) + '\n')
paths = list(Path('server/build/test-results/test').glob('*.xml')) + list(Path('androidApp/build/test-results/testDebugUnitTest').glob('*.xml'))
(out / 'host-junit-rechecks.json').write_text(json.dumps({str(p): {'base64': base64.b64encode(p.read_bytes()).decode(),
    'sha256': sha(p), 'bytes': p.stat().st_size} for p in paths}, indent=2) + '\n')
for name in ('application-image.json',):
    (out / name).write_bytes((Path('server/build') / name).read_bytes())

value = e.load(Path('/tmp/opencode/atw-e2e-ca978b34000000000000000000000001'))
with e.locked(Path(value['directory'])):
    names = e.database(value, "SELECT name FROM saved_deck ORDER BY name").splitlines()
    assert names == [f'Collection extra {i}' for i in range(1, 7)], names
    assert e.database(value, "SELECT count(*) FROM definition") == '3'
    assert e.database(value, "SELECT count(*) FROM deck_entry") == '18'
    outcomes = json.loads(e.database(value, "SELECT json_agg(json_build_object('kind',r.kind,'status',a.status,'response',a.response) ORDER BY r.kind) FROM request_identity r JOIN account_request a ON a.request_id=r.id WHERE r.kind IN ('updateDeck','deleteDeck')"))
    assert [x['kind'] for x in outcomes] == ['deleteDeck', 'updateDeck', 'updateDeck']
    assert all(x['status'] == 200 for x in outcomes)
    updates = sorted((json.loads(x['response']) for x in outcomes if x['kind'] == 'updateDeck'), key=lambda x: int(x['deck']['revision']))
    assert [x['deck']['revision'] for x in updates] == ['2', '3']
    assert all(x['deck']['name'] == 'Renamed collection' and x['deck']['mainboard'] == 6 and x['deck']['sideboard'] == 3 for x in updates)
    assert updates[-1]['deck']['cover']['name'] == 'Amberglass Chorus'
    (out / 'accepted-state.json').write_text(json.dumps({'scope': 'Read-only corroboration of normal UI effects, never setup/action injection',
        'remainingSavedDeckNames': names, 'retainedDefinitions': 3, 'remainingEntries': 18, 'originalOutcomes': outcomes}, indent=2) + '\n')
print('PASS retained current source/fixture/locks/APK/image identities, exact host recheck XML, six remaining UI-created decks, immutable definitions and original metadata/delete outcomes')
print('Native core/tail commands remain diagnostic where they failed; persisted-deletion final tail alone is a wholly green native command.')
