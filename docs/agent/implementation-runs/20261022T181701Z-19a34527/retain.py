"""Retain actual current test/artifact identities and authorized read-only native server observations."""
from pathlib import Path
import hashlib
import json
import sys
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path.cwd()))
from e2e import environment as e

prefix = Path(__file__).parent
results = {}
for file in sorted(Path('androidApp/build/test-results/testDebugUnitTest').glob('TEST-*.xml')):
    root = ET.parse(file).getroot()
    assert not any(list(root.iter(tag)) for tag in ('failure', 'error', 'skipped'))
    results[str(file)] = file.read_text()
(prefix / 'host-junit.json').write_text(json.dumps(results, indent=2) + '\n')
print('PASS retained current affected host cases:', sum(len(list(ET.fromstring(text).iter('testcase'))) for text in results.values()))
value = e.load(Path('/tmp/opencode/atw-e2e-19a34527000000000000000000000001'))
with e.locked(Path(value['directory'])):
    outcomes = json.loads(e.database(value, "SELECT json_agg(json_build_object('requestId',request_id,'ordinal',ingress_ordinal,'command',command::json,'result',result::json) ORDER BY ingress_ordinal) FROM game_inbox"))
    assert len(outcomes) == 3
    assert [item['command']['action']['type'] for item in outcomes] == ['declineInvitation', 'reinvite', 'acceptInvitation']
    assert all(item['result']['outcome'] == 'accepted' for item in outcomes)
    assert [item['ordinal'] for item in outcomes] == [1, 2, 3]
    stream = json.loads(e.database(value, "SELECT json_agg(json_build_object('accountId',account_id,'installationId',installation_id,'processId',process_id,'streamId',stream_id,'generation',generation)) FROM game_stream"))
    (prefix / 'ordered-native-outcomes.json').write_text(json.dumps(dict(outcomes=outcomes, streams=stream), indent=2) + '\n')
    reset = json.loads((Path(value['directory']) / 'reset-completed.json').read_text())
    assert reset['baseline']['migrations'][-1] == '7'
    (prefix / 'actual-reset.json').write_text(json.dumps(reset, indent=2) + '\n')
for name in ('server/build/application-image.json', 'e2e/build/adapters/image.json'):
    (prefix / Path(name).name.replace('image.json', Path(name).parent.name + '-image.json')).write_bytes(Path(name).read_bytes())
paths = set()
for directory in ('androidApp/src', 'protocol/src/commonMain', 'server/src/main', 'e2e/src/test'):
    paths.update(path for path in Path(directory).rglob('*') if path.is_file())
paths.update(Path(name) for name in ('androidApp/build.gradle.kts', 'androidApp/gradle.lockfile', 'gradle/libs.versions.toml',
    'androidApp/build/outputs/apk/debug/androidApp-debug.apk', 'e2e/environment.py', 'e2e/compose.json', 'e2e/relay.py'))
identities = {str(path): dict(sha256=hashlib.sha256(path.read_bytes()).hexdigest(), bytes=path.stat().st_size) for path in sorted(paths)}
(prefix / 'input-identities.json').write_text(json.dumps(identities, indent=2) + '\n')
print('PASS exactly three original accepted native requests, stable scopes/attempts, current stream fences and real V7 fresh reset')
print('PASS final normal-APK/shared/server/client/test/lock/source identities retained; no re-executed server/full-suite qualification')
