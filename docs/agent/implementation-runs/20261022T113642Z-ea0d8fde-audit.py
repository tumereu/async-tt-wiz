"""I05 parent: reuse exact child evidence, not another build/device qualification."""
import base64
import hashlib
import json
from pathlib import Path
import runpy
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PREFIX = 'docs/agent/implementation-runs/20261022T113642Z-ea0d8fde'
STATE = 'docs/agent/implementation-state.json'
START = '96f55ecd53ffcf60a8608d2cfa18220c4cc8f470'
CHILDREN = {'I05a': 'e41785b32522ba47ffb3bf6a4567bbf9936c0feb',
            'I05b': 'f13b5cf01b263d99ebb6496a21c6dda7c10729a5'}


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def load(path):
    return json.loads((ROOT / path).read_text())


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def emit(label, value):
    print(label, json.dumps(value, sort_keys=True))


def output(path):
    value = load(path)
    raw = value['output'].encode() if 'output' in value else base64.b64decode(value['outputBase64'], validate=True)
    assert sha(raw) == value['outputSha256'], path
    return value, raw.decode()


def bundle(path):
    decoded = {}
    for name, entry in load(path).items():
        raw = base64.b64decode(entry['base64'], validate=True) if 'base64' in entry else (ROOT / entry['path']).read_bytes()
        assert sha(raw) == entry['sha256'] and len(raw) == entry['bytes'], (path, name)
        decoded[name] = raw
    return decoded


def green_xml(raw):
    cases = list(ET.fromstring(raw).iter('testcase'))
    assert cases and not any(list(case.iter(tag)) for case in cases for tag in ('failure', 'error', 'skipped'))
    return len(cases)


def audit():
    before = json.loads(git('show', START + ':' + STATE))
    api = runpy.run_path(str(ROOT / 'scripts/implementation-loop.py'))
    assert api['selection'](before) == {'mode': 'implement', 'target': 'I05', 'findingIds': []}
    assert before['authorization'] == 'authorized' and not before['findings']
    assert next(b for b in before['batches'] if b['id'] == 'B1')['status'] == 'reviewed'
    tasks = {t['id']: t for t in before['tasks']}
    assert tasks['I05']['children'] == list(CHILDREN)
    prefixes = {}
    for identifier, commit in CHILDREN.items():
        task = tasks[identifier]
        assert task['status'] == 'implemented'
        record = task['implementationRecord']
        report = load(record)
        assert report['target'] == identifier
        prefixes[identifier] = record[:-5]
        hashes = {}
        for path in report['changedPaths']:
            # Only state and later child's intentional inputs can differ from I05a.
            if path == STATE or (identifier == 'I05a' and path in load(tasks['I05b']['implementationRecord'])['changedPaths']):
                continue
            raw = (ROOT / path).read_bytes()
            assert raw == git('show', commit + ':' + path), path
            hashes[path] = sha(raw)
        checks = []
        for check in report['checks']:
            path = check['evidence']
            assert git('show', commit + ':' + path) == (ROOT / path).read_bytes(), path
            value, _ = output(path)
            assert value['exitCode'] == check['exitCode']
            assert check['kind'] == 'diagnostic' or check['exitCode'] == 0
            checks.append({k: check[k] for k in ('evidence', 'kind', 'exitCode')})
        emit('REUSED HISTORICAL CHILD, NOT NEW CHECK EXECUTIONS',
             dict(id=identifier, savedCommit=commit, record=record, checks=checks, savedBytesSha256=hashes))
        counts = {name: green_xml(raw) for name, raw in bundle(record[:-5] + '/host-junit.json').items()}
        emit('REUSED LOSSLESS GREEN HOST XML (last selected scopes only)', counts)
        for name in ('cleanup-device.json', 'cleanup-environment.json'):
            assert output(record[:-5] + '/' + name)[0]['exitCode'] == 0
    a, b = prefixes['I05a'], prefixes['I05b']
    # I05b did not modify the already exercised record/parser/scheduling/navigation path.
    unchanged = ['androidApp/src/main', 'client-storage/src/main', 'androidApp/src/main/AndroidManifest.xml',
                 'e2e/src', 'tests', 'core', 'protocol', 'server', 'e2e/environment.py', 'e2e/compose.json']
    delta = git('diff', '--name-only', CHILDREN['I05a'], START, '--', *unchanged).decode().splitlines()
    assert set(delta) == {'androidApp/src/main/kotlin/org/asyncttwiz/android/CardArtwork.kt',
                          'client-storage/src/main/kotlin/org/asyncttwiz/storage/ArtworkNetwork.kt',
                          'client-storage/src/main/kotlin/org/asyncttwiz/storage/ArtworkRepository.kt'}, delta
    emit('ONLY PRODUCTION SOURCE DELTA AFTER RECORDS (qualified by I05b)', delta)
    identities = load(b + '/artifact-identities.json')
    for path, expected in identities.items():
        raw = (ROOT / path).read_bytes()
        assert sha(raw) == expected['sha256'] and len(raw) == expected['bytes'], path
    emit('CURRENT EXACT SOURCE/TEST/FIXTURE/LOCK/APK/IMAGE IDENTITIES, NOT REBUILT', identities)
    for path, expected in load(a + '/artifact-identities.json').items():
        if path.startswith(('androidApp/src/', 'client-storage/src/', 'tests/fixtures/')):
            assert sha((ROOT / path).read_bytes()) == expected['sha256'], path
    for path in sorted((ROOT / a).glob('runner-*.json')):
        entries = bundle(str(path.relative_to(ROOT)))
        count = list(ET.fromstring(entries['result-0.xml']).iter('testcase'))
        emit('HISTORICAL NATIVE CATALOG SEGMENT (diagnostic originals retained)',
             dict(path=str(path.relative_to(ROOT)), cases=len(count),
                  failures=sum(bool(list(c.iter('failure'))) for c in count), entries=list(entries)))
        if 'passed.json' in entries:
            assert green_xml(entries['result-0.xml']) == 1
            emit('PASSING RETAINED TAIL OBSERVATION', json.loads(entries['catalog-observation.json']))
    for name in ('android-catalog-recheck.json',):
        assert 'OK (1 test)' in output(a + '/' + name)[1]
    for name in ('android-final.json', 'android-pending-recheck.json'):
        assert 'OK (1 test)' in output(b + '/' + name)[1]
    print('REUSED INTEGRATION: actual API29 hosted MTGJSON -> disk catalog -> exact two faces -> Coil/BitmapFactory/cache/text; delayed disposal/rebind checked separately. Native catalog consent/retry/restart/update spans retained segments, not a wholly green original run. Final hosted art run covers last decoder success change, not all earlier native assertions on that binary.')
    ledger = runpy.run_path(str(ROOT / 'scripts/coverage-ledger.py'))
    emit('UNCHANGED PARTIAL SOURCE ASSIGNMENTS, NOT WHOLE SCENARIO PASSES', ledger['validate'](load('docs/agent/coverage/ledger.json')))
    print('PASS I05 scoped outcome reconciled; no missing ordinary integration seam. No new build/device/full-suite run, no independent review. I06 next; B2 review after I06-I08; I33-I36 and all external gates retained.')


def validate():
    print('Checking I05 evidence-only handoff.', flush=True)
    before = json.loads(git('show', START + ':' + STATE))
    after, report = load(STATE), load(PREFIX + '.json')
    api = runpy.run_path(str(ROOT / 'scripts/implementation-loop.py'))
    choice = {'mode': 'implement', 'target': 'I05', 'findingIds': []}
    assert git('rev-parse', 'HEAD').decode().strip() == START
    assert git('branch', '--show-current').decode().strip() == 'master'
    assert not git('diff', '--cached', '--name-only').strip()
    api['validate_state'](after)
    api['validate_transition'](before, after, choice, report)
    assert after['activeRun'] is None and after['runs'] == before['runs'] + [PREFIX + '.json']
    for old, new in zip(before['tasks'], after['tasks'], strict=True):
        assert old == new if old['id'] != 'I05' else new == {
            **old, 'status': 'implemented', 'implementationRecord': PREFIX + '.json', 'qualification': new['qualification']}
    assert all(before[k] == after[k] for k in before if k not in ('tasks', 'runs'))
    assert api['selection'](after) == {'mode': 'implement', 'target': 'I06', 'findingIds': []}
    actual = set(git('diff', '--name-only', START).decode().splitlines()) | set(git('ls-files', '--others', '--exclude-standard').decode().splitlines())
    assert actual == set(report['changedPaths']), actual ^ set(report['changedPaths'])
    for path in actual:
        assert path == STATE or path.startswith(PREFIX)
        text = (ROOT / path).read_text()
        assert text.endswith('\n') and all(line == line.rstrip() for line in text.splitlines()), path
        if path.endswith('.json'):
            json.loads(text)
    subprocess.run(['git', 'diff', '--check'], cwd=ROOT, check=True)
    assert 'PASS I05 scoped outcome reconciled' in (ROOT / (PREFIX + '-audit.txt')).read_text()
    print('PASS I05-only closure, exact owned JSON/text/Python outputs and whitespace. No Markdown/UI edit. B2 pending, next I06; authorization/model/runner/permissions/reviews/findings/external gates unchanged.')


if __name__ == '__main__':
    {'audit': audit, 'validate': validate}[sys.argv[1]]()
