"""Retain exact executed host results, native original outcomes and final input identities."""
from pathlib import Path
import hashlib
import json
import sys
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path.cwd()))
from e2e import environment as e

prefix = Path(__file__).parent
results = {str(p): p.read_text() for p in Path('androidApp/build/test-results/testDebugUnitTest').glob('TEST-*.xml')}
assert len(results) == 1
root = ET.fromstring(next(iter(results.values())))
assert len(list(root.iter('testcase'))) == 8 and not any(list(root.iter(tag)) for tag in ('failure', 'error', 'skipped'))
(prefix / 'host-junit-targeted.json').write_text(json.dumps(results, indent=2) + '\n')
baseline = json.loads((prefix / 'host-junit-baseline.json').read_text())
cases = set()
for group in (baseline, results):
    for text in group.values():
        root = ET.fromstring(text)
        assert not any(list(root.iter(tag)) for tag in ('failure', 'error', 'skipped'))
        cases.update((case.attrib['classname'], case.attrib['name']) for case in root.iter('testcase'))
assert len(cases) == 36
value = e.load(Path('/tmp/opencode/atw-e2e-f7a4a46b000000000000000000000001'))
with e.locked(Path(value['directory'])):
    e.owned(value)
    outcomes = json.loads(e.database(value, "SELECT json_agg(json_build_object('requestId',request_id,'ordinal',ingress_ordinal,'command',command::json,'result',result::json) ORDER BY ingress_ordinal) FROM game_inbox"))
    assert len(outcomes) == 2
    assert [item['command']['action']['type'] for item in outcomes] == ['selectDeck', 'acceptInvitation']
    assert all(item['result']['outcome'] == 'accepted' for item in outcomes)
    assert [item['ordinal'] for item in outcomes] == [1, 2]
    assert outcomes[0]['command']['scope'].get('invitationId') is None
    prepared = json.loads((prefix / 'observe-accepted.json').read_text())
    assert prepared['copy']['selectionRequestId'] == outcomes[0]['requestId']
    assert sum(entry['quantity'] for entry in prepared['copy']['entries']) == 9
    assert prepared['copy']['id'] in outcomes[0]['result']['createdIds']
    assert e.database(value, "SELECT count(*) FROM saved_deck WHERE id='" + prepared['copy']['sourceId'] + "'") == '0'
    assert e.database(value, 'SELECT count(*) FROM game_revision') == '2'
    assert e.database(value, 'SELECT count(*) FROM game_outbox') == '2'
    (prefix / 'ordered-native-outcomes.json').write_text(json.dumps(outcomes, indent=2) + '\n')
for name in ('server/build/application-image.json', 'e2e/build/adapters/image.json'):
    (prefix / (Path(name).parent.name + '-' + Path(name).name)).write_bytes(Path(name).read_bytes())
paths = set()
for directory in ('androidApp/src', 'protocol/src/commonMain', 'server/src/main', 'e2e/src/test'):
    paths.update(path for path in Path(directory).rglob('*') if path.is_file())
paths.update(Path(name) for name in ('androidApp/build.gradle.kts', 'androidApp/gradle.lockfile', 'gradle/libs.versions.toml',
    'androidApp/build/outputs/apk/debug/androidApp-debug.apk', 'e2e/environment.py', 'e2e/compose.json', 'e2e/relay.py'))
identities = {str(path): dict(sha256=hashlib.sha256(path.read_bytes()).hexdigest(), bytes=path.stat().st_size) for path in sorted(paths)}
(prefix / 'input-identities.json').write_text(json.dumps(identities, indent=2) + '\n')
print('PASS 36 distinct affected host cases (35 initial + eight targeted, seven reused), all green without skips')
print('PASS one original accepted selection then recipient acceptance, exact copied identities/definitions/partitions retained after source deletion')
print('PASS two committed revision/outbox effects, no duplicate selection; exact final normal APK/source/build/lock identities retained')
