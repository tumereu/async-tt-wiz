"""Pin actual host/native inputs, original ordered outcomes and final immutable preparation."""
from pathlib import Path
import hashlib
import json
import sys
import xml.etree.ElementTree as ET
sys.path.insert(0, str(Path.cwd()))
from e2e import environment as e

prefix = Path(__file__).parent
results = {str(p): p.read_text() for p in Path('androidApp/build/test-results/testDebugUnitTest').glob('TEST-*.xml')}
assert len(results) == 3
count = 0
for text in results.values():
    root = ET.fromstring(text)
    assert not any(list(root.iter(tag)) for tag in ('failure', 'error', 'skipped'))
    count += len(list(root.iter('testcase')))
(prefix / 'host-junit.json').write_text(json.dumps(results, indent=2) + '\n')
value = e.load(Path('/tmp/opencode/atw-e2e-802a24bd000000000000000000000001'))
with e.locked(Path(value['directory'])):
    e.owned(value)
    outcomes = json.loads(e.database(value, "SELECT json_agg(json_build_object('requestId',request_id,'ordinal',ingress_ordinal,'command',command::json,'result',result::json) ORDER BY ingress_ordinal) FROM game_inbox"))
    assert len(outcomes) == 48 and [x['ordinal'] for x in outcomes] == list(range(1, 49))
    assert len({x['requestId'] for x in outcomes}) == 48
    assert outcomes[0]['command']['action']['type'] == 'selectDeck'
    assert all(x['command']['action']['type'] == 'transferPreparedCard' for x in outcomes[1:])
    rejected = [x for x in outcomes if x['result']['outcome'] != 'accepted']
    assert len(rejected) == 1 and rejected[0]['result']['reason'] == 'sourceSectionChanged'
    # Two gestures capture the same exact first copy; the losing constituent is rejected, not substituted.
    conflict = outcomes[21:26]
    assert [x['command']['action']['cardIndex'] for x in conflict] == ['0', '0', '1', '2', '3']
    assert conflict[0]['command']['action']['cardId'] == conflict[1]['command']['action']['cardId']
    prepared = json.loads(e.database(value, 'SELECT deck::json FROM player_preparation WHERE deck IS NOT NULL'))
    assert prepared == json.loads((prefix / 'observe-final.json').read_text())['prepared']
    saved = json.loads(e.database(value, "SELECT json_agg(json_build_object('section',section,'definition',definition_digest,'quantity',quantity)) FROM deck_entry"))
    assert sum(x['quantity'] for x in saved if x['section'] == 'MAINBOARD') == 12
    assert sum(x['quantity'] for x in saved if x['section'] == 'SIDEBOARD') == 3
    assert e.database(value, 'SELECT count(*) FROM game_revision') == '47'
    assert e.database(value, 'SELECT count(*) FROM game_outbox') == '48' # Rejection has a result publication, not a state revision.
    (prefix / 'ordered-native-outcomes.json').write_text(json.dumps(outcomes, indent=2) + '\n')
    (prefix / 'final-preparation-and-source.json').write_text(json.dumps(dict(prepared=prepared, savedEntries=saved,
        requests=48, rejected=1, revisions=47, resultOutbox=48), indent=2) + '\n')
for name in ('server/build/application-image.json', 'e2e/build/adapters/image.json'):
    (prefix / (Path(name).parent.name + '-' + Path(name).name)).write_bytes(Path(name).read_bytes())
paths = set()
for directory in ('androidApp/src', 'protocol/src/commonMain', 'server/src/main', 'e2e/src/test'):
    paths.update(p for p in Path(directory).rglob('*') if p.is_file())
paths.update(Path(p) for p in ('androidApp/build.gradle.kts', 'androidApp/gradle.lockfile', 'gradle/libs.versions.toml',
    'androidApp/build/outputs/apk/debug/androidApp-debug.apk',
    'androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk', 'e2e/environment.py', 'e2e/compose.json'))
(prefix / 'input-identities.json').write_text(json.dumps({str(p): dict(sha256=hashlib.sha256(p.read_bytes()).hexdigest(), bytes=p.stat().st_size)
    for p in sorted(paths)}, indent=2) + '\n')
print(f'PASS {count} affected host cases without skips; original XML retained, not a new execution')
print('PASS 48 original ordered requests, one exact-copy conflict rejection, 47 accepted revisions and 48 result outbox records, no substituted/double dispatch')
print('PASS final 12/3 exact original preparation and immutable saved source; final normal APK/instrumentation/source/build/lock/image identities retained')
