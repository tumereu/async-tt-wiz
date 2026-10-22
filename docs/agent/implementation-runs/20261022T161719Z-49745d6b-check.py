"""Run-owned check of B2-R01 fix evidence and the exact scoped handoff."""
import base64
import hashlib
import json
from pathlib import Path
import runpy
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PREFIX = 'docs/agent/implementation-runs/20261022T161719Z-49745d6b'
START = '7d8119e3d2448b2faaac93fd8b6fb7eb648d2e07'
STATE = 'docs/agent/implementation-state.json'


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT).decode().strip()


def load(path):
    return json.loads((ROOT / path).read_text())


def junit():
    records = []
    for path in sorted((ROOT / 'androidApp/build/test-results/testDebugUnitTest').glob('TEST-*.xml')):
        raw = path.read_bytes()
        root = ET.fromstring(raw)
        assert root.get('failures') == root.get('errors') == root.get('skipped') == '0'
        assert root.get('name') == 'org.asyncttwiz.android.DeckReauthenticationTest'
        records.append({'path': str(path.relative_to(ROOT)), 'sha256': hashlib.sha256(raw).hexdigest(),
                        'base64': base64.b64encode(raw).decode(), 'tests': int(root.get('tests'))})
    assert len(records) == 1 and records[0]['tests'] == 5
    (ROOT / (PREFIX + '-final-junit.json')).write_text(json.dumps(records, indent=2) + '\n')
    print('Retained five final targeted reauthentication tests: all passed, no skipped cases.')


def validate():
    print('Checking exact B2-R01 fix handoff, affected evidence and unchanged external obligations.', flush=True)
    before = json.loads(subprocess.check_output(['git', 'show', START + ':' + STATE], cwd=ROOT))
    after, report = load(STATE), load(PREFIX + '.json')
    api = runpy.run_path(str(ROOT / 'scripts/implementation-loop.py'))
    selected = {'mode': 'fix', 'target': 'B2', 'findingIds': ['B2-R01']}
    assert api['selection'](before) == selected
    api['validate_state'](after)
    api['validate_transition'](before, after, selected, report)
    assert before['tasks'] == after['tasks'] and after['activeRun'] is None
    assert after['runs'] == before['runs'] + [PREFIX + '.json']
    assert api['selection'](after) == {'mode': 'verify', 'target': 'B2', 'findingIds': ['B2-R01']}
    assert all(before[key] == after[key] for key in before if key not in ('runs', 'findings', 'batches'))
    actual = set(git('diff', '--name-only', START).splitlines()) | set(git('ls-files', '--others', '--exclude-standard').splitlines())
    assert actual == set(report['changedPaths']), actual ^ set(report['changedPaths'])
    assert not git('diff', '--cached', '--name-only')
    assert git('rev-parse', 'HEAD') == START
    for check in report['checks']:
        assert check['kind'] != 'required' or check['exitCode'] == 0
        assert (ROOT / check['evidence']).stat().st_size > 0
    for suffix, expected in [('-affected-junit.json', 33), ('-final-junit.json', 5)]:
        records = load(PREFIX + suffix)
        assert sum(r['tests'] for r in records) == expected
        for record in records:
            raw = base64.b64decode(record['base64'], validate=True)
            assert hashlib.sha256(raw).hexdigest() == record['sha256']
            root = ET.fromstring(raw)
            assert root.get('failures') == root.get('errors') == root.get('skipped') == '0'
            print('PASS retained JUnit', root.get('name'), root.get('tests'))
    wiring = (ROOT / 'androidApp/src/main/kotlin/org/asyncttwiz/android/AuthViewModel.kt').read_text()
    assert wiring.count('authenticationRequired = controller::authenticationRequired') == 2
    assert 'deckImport.connectSave(saveApi, controller::sessionToken' in wiring
    for path in actual:
        text = (ROOT / path).read_text()
        assert text.endswith('\n') and all(line == line.rstrip() for line in text.splitlines()), path
        if path.endswith('.json'):
            json.loads(text)
    subprocess.run(['git', 'diff', '--check'], cwd=ROOT, check=True)
    print('PASS fix-only state transition, production read/save callback wiring, exact owned paths, JSON/whitespace. '
          'No design-document links changed; authorization/model/grants/tasks/review coverage/release gates retained. '
          'Fresh independent verification is next; no native/full-suite/final UI/release pass claimed.')


if __name__ == '__main__':
    {'junit': junit, 'validate': validate}[sys.argv[1]]()
