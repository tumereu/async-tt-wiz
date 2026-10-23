"""Small run-owned state/evidence/native-outcome and safe-save audit, not another product suite."""
from pathlib import Path
import base64
import hashlib
import json
import re
import runpy
import subprocess
import xml.etree.ElementTree as ET

prefix = Path(__file__).resolve().parent.relative_to(Path.cwd())
state_path = 'docs/agent/implementation-state.json'
report_path = str(prefix) + '.json'
report = json.loads(Path(report_path).read_text())
def output(*args): return subprocess.check_output(args, text=True).strip()
before = json.loads(output('git', 'show', report['startCommit'] + ':' + state_path))
after = json.loads(Path(state_path).read_text())
api = runpy.run_path('scripts/implementation-loop.py')
selected = dict(mode='implement', target='I11b', findingIds=[])
assert output('git', 'rev-parse', 'HEAD') == report['startCommit']
assert output('git', 'branch', '--show-current') == 'master'
assert not output('git', 'diff', '--cached', '--name-only')
assert api['selection'](before) == selected
api['validate_state'](after)
api['validate_transition'](before, after, selected, report)
assert after['activeRun'] is None and after['runs'] == before['runs'] + [report_path]
assert all(after[key] == value for key, value in before.items() if key not in {'runs', 'tasks'})
for old, new in zip(before['tasks'], after['tasks']):
    if old['id'] == 'I11b':
        assert new['status'] == 'implemented' and new['implementationRecord'] == report_path
        assert all(new[key] == value for key, value in old.items() if key != 'status')
    else: assert old == new
assert api['selection'](after) == dict(mode='implement', target='I11', findingIds=[])
print('PASS clean supplied baseline/master/authorization/earliest priority, driver state/transition and only I11b advances; next I11 parent audit')

tests = json.loads((prefix / 'client-junit.json').read_text())
count = 0
for saved in tests.values():
    data = base64.b64decode(saved['base64'], validate=True)
    assert hashlib.sha256(data).hexdigest() == saved['sha256']
    root = ET.fromstring(data); assert root.attrib == saved['tests']
    assert all(int(root.attrib[key]) == 0 for key in ('failures', 'errors', 'skipped'))
    count += int(root.attrib['tests'])
assert count == 30
print('PASS exact 30 final affected JUnit cases without skips: 8 native-start controller/scene, 10 preparation, 8 transfer-session, 4 transport/session')

final = json.loads(sorted(prefix.glob('observe-final-*.json'))[-1].read_text())
committed = json.loads((prefix / 'committed-before-resume.json').read_text())
assert final['plays'] == committed['plays'] and len(final['plays']) == 1
assert final['games'] == committed['games'] and len(final['requests']) == 11
assert final['requests'] == committed['requests']
assert len({request['id'] for request in final['requests']}) == 11
assert all(request['result']['outcome'] == 'accepted' for request in final['requests'])
play = final['plays'][0]
assert play['currentNodeId'] == play['root']['id'] and play['root']['parentId'] is None
assert len(play['board']['cards']) == 11 and all(p['life'] == '20' for p in play['board']['players'])
assert play['board']['turn']['number'] == '1' and play['board']['turn']['step'] == 'UNTAP'
for shuffle in play['shuffles']:
    library = next(c for c in play['board']['containers'] if c['accountId'] == shuffle['accountId'] and c['zone'] == 'LIBRARY')
    assert library['cards'] == shuffle['order']
assert sorted(len(s['order']) for s in play['shuffles']) == [5, 6]
assert len({s['seed'] for s in play['shuffles']} | {play['starter']['seed']}) == 3
assert all(not c['cards'] for c in play['board']['containers'] if c['zone'] != 'LIBRARY')
assert all(c['zone'] != 'SIDEBOARD' for c in play['board']['containers'])
assert {card['definition'] for card in play['board']['cards']} == {definition['digest'] for definition in play['definitions']}
for path in prefix.glob('observe-*.json'):
    saved = json.loads(path.read_text())
    assert saved['games'][0]['id'] == final['games'][0]['id']
    assert saved['games'][0]['createdAt'] == final['games'][0]['createdAt']
print('PASS composite normal-APK evidence: 11 original outcomes, exact persistent game/date, single play/root, two concrete independent Library orders and Random result unchanged across lost-ack backend/app restart; no draw/reset/Sideboard')
print('LIMIT native setup/readiness/table assertions are retained in diagnostic stages; final passing retained tail supplies only missing restart/Back checks, not a repeated full native run')

inputs = json.loads((prefix / 'inputs.json').read_text())
for path, digest in inputs['finalSources'].items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == digest, path
assert hashlib.sha256(Path(inputs['finalCompiledApk']['path']).read_bytes()).hexdigest() == inputs['finalCompiledApk']['sha256']
print('PASS final source/build/lock/fixture and compiled APK input hashes; native installed APK distinct input supersession is explicit')
for i in (1, 2):
    assert Path('/tmp/opencode/atw-device-bc92c3f300000000000000000000000' + str(i), 'cleaned.json').is_file()
assert Path('/tmp/opencode/atw-e2e-bc92c3f3000000000000000000000001/cleaned.json').is_file()
reset = json.loads((prefix / 'actual-v9-reset.json').read_text())
assert reset['exitCode'] == 0
records = json.loads((prefix / 'owned-reset-cleanup.json').read_text())
for path, saved in records.items():
    data = base64.b64decode(saved['base64'], validate=True)
    assert hashlib.sha256(data).hexdigest() == saved['sha256'] and json.loads(data) == saved['record']
    if path.endswith('/reset-completed.json'):
        assert saved['record']['baseline']['migrations'] == [str(i) for i in range(1, 10)]
        assert 'game_play' in saved['record']['baseline']['tables']
print('PASS actual populated-V9 reset baseline includes game_play, and exact two-device/environment cleanup records retained')

actual = set(output('git', 'diff', '--name-only').splitlines()) | set(output('git', 'ls-files', '--others', '--exclude-standard').splitlines())
assert actual == set(report['changedPaths']), actual ^ set(report['changedPaths'])
runner = object.__new__(api['Runner']); runner.root = Path.cwd(); runner.state_path = Path(state_path); runner.state_relative = state_path
runner.role_paths(before, selected, report, actual, report_path)
for check in report['checks']:
    assert Path(check['evidence']).is_file()
    assert check['exitCode'] == 0 or check['kind'] == 'diagnostic'
    for path in check.get('supportPaths', []): assert path in actual and Path(path).is_file()
for path in sorted(actual):
    data = Path(path).read_bytes()
    if path.endswith('.png'):
        assert data.startswith(b'\x89PNG\r\n\x1a\n'); continue
    assert b'\r' not in data and (not data or data.endswith(b'\n') or path.endswith('.xml')), path
    assert all(line.rstrip() == line for line in data.splitlines()), path
    if path.endswith('.json'):
        saved = json.loads(data)
        if isinstance(saved, dict) and 'outputSha256' in saved:
            assert hashlib.sha256(saved['output'].encode()).hexdigest() == saved['outputSha256'], path
    if path.endswith('.md'):
        text = data.decode(); assert len(re.findall(r'^```', text, re.M)) % 2 == 0
        for target in re.findall(r'\]\(([^)]+)\)', text):
            if '://' not in target and not target.startswith('#'): assert (Path(path).parent / target.split('#')[0]).exists()
subprocess.run(['git', 'diff', '--check'], check=True)
print('PASS exact owned changedPaths/role scope, retained lossless outputs/hashes, JSON/document links/fences and staged-safe whitespace')
print('NOT CLAIMED I11 parent/I12/full B3 review, gameplay/mulligan/history/rematch, final full suite/complete UI comparison/compatibility/physical measurement or any release clearance')
