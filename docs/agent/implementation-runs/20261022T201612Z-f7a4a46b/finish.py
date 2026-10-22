"""Prepare and audit this one-unit report/state/evidence handoff; no product qualification rerun."""
from pathlib import Path
import base64
import hashlib
import json
import re
import runpy
import shlex
import subprocess
import xml.etree.ElementTree as ET

prefix = Path(__file__).resolve().parent.relative_to(Path.cwd())
state_path = 'docs/agent/implementation-state.json'
report_path = str(prefix) + '.json'
start = '627006fc035932e0fe959efe772b970c77360f49'
def git(*args): return subprocess.check_output(['git', *args], text=True).strip()
before = json.loads(git('show', start + ':' + state_path))
after = json.loads(Path(state_path).read_text())
choice = dict(mode='implement', target='I10a2', findingIds=[])
checks = []
for path in sorted(prefix.glob('*.json')):
    record = json.loads(path.read_text())
    if not isinstance(record, dict) or not {'command', 'exitCode'} <= record.keys(): continue
    support = []
    if any('native-check.py' in arg for arg in record['command']): support = [str(prefix / name) for name in ('native-check.py', 'control.py', 'offload.py')]
    if any('reattach.py' in arg for arg in record['command']): support = [str(prefix / name) for name in ('reattach.py', 'offload.py')]
    for arg in record['command']:
        if arg.startswith(str(prefix)) and arg.endswith('.py') and arg not in support: support.append(arg)
    check = dict(kind='required' if record['exitCode'] == 0 else 'diagnostic',
        command=f'python3 scripts/capture-check.py {path} -- ' + shlex.join(record['command']),
        exitCode=record['exitCode'], evidence=str(path))
    if support: check['supportPaths'] = support
    checks.append(check)
output = str(prefix / 'handoff.txt')
checks.append(dict(kind='required', command=f'python3 {prefix}/finish.py > {output} 2>&1', exitCode=0,
    evidence=output, supportPaths=[str(prefix / 'finish.py')]))
report = dict(runId='20261022T201612Z-f7a4a46b', sessionID='ses_e9abe365fffeZWsY5OMuB00npc', startCommit=start,
    mode='implement', target='I10a2', findingIds=[], newFindingIds=[], reviewedUnitIds=[], outcome='progress',
    summary='Delivered native Choose/Change deck, private border-only selected draft, one bottom confirmation and contextual Import/empty-collection return on the existing ordered selection path. Shared pure provisional preparation is separate from accepted revisioned state, original uncertain requests and UI draft; receipts/results cannot rewind state or retire prediction before correlated acceptance. Thirty-six distinct affected host cases pass across 35 baseline and eight targeted executions, plus incremental APK/lint/boundaries and V8 reset-store regression. A complete real API29 non-GMS normal-APK picker/import/draft/cancel/confirm/reopen/restart/missing-art/source-rename-Overwrite-cover-delete journey passes in both orientations. Native selection accessibility was repaired and rechecked. A targeted retained-state tail verifies recipient acceptance preserves the exact inviter copy and recipient-private empty picker, both orientations; the Accept itself succeeded in the diagnostic attempt before its wrong bare-title assertion, not newly repeated in the passing tail. Read-only SQL proves one select plus one accept and two revision/outbox effects without duplication. Exact inputs/JUnit/captures and owned cleanup retained; compile/Review-target/tmpfs/title-oracle failures remain diagnostic. Only I10a2 closes; parent audits, I10b transfers, I11/I12 Ready/start/cancel/two-Android overlaps, B3 independent review and final qualification/release gates remain pending.',
    selectionReason='Supplied clean master/startCommit and active D-304 authorization match the actual repository: B1/B2 reviewed, B2-R01 verified, I09/I10a1 implemented and no fix or capability review due. I10a2 is the earliest ready child and completes the usable native picker rather than adding an internal qualification layer. Affected scope is production session preparation/prediction/result-revision correlation, picker/controller/import/auth/navigation/UI wiring and the V8 empty-store oracle required by the native journey. Targeted host tests/incremental APK/lint and one real picker journey plus its missing retained acceptance seam cover that scope; reuse unchanged I10a1 server durability and qualified device adapters. No full suite, clean build, unchanged fault matrix or new review/split; source obligations and release gates are retained.',
    sourceIds=['P-044','P-045','P-052','P-053','P-059','T-400','T-401','T-402','T-403','T-404','T-405','T-440','T-441','T-442','T-443','T-444','T-445','T-608','T-680','T-682','T-683','T-684','F20','F21','F30','F31','F32','F34','F35','F36'],
    sourceDisposition='Partial I10.preparation assignments under accounts/decks/lobby D-189/D-191/D-199/D-202/D-205/D-221/D-294, server authority D-259 and request recovery D-275/D-278/D-285/D-289. This run delivers picker/import-return/selected-copy display/prediction/recovery/source-isolation assertions. All cross-task complete-scenario ledger assignments remain truthful: transfers, Ready/start/play/two-player overlaps, rematch and final qualification are not claimed executed.',
    remainingObligations=[
        'I10a/I10 separately selected exact-child evidence closures; no ancestor completes here. I10b supplies per-copy one/all tap/drag/accessibility transfers and fixed fading feedback on this same session/model.',
        'I11/I12 actual Ready/starter/start/randomness/initial table/sideboard exclusion/cancel and complete two-Android preparation/acceptance/readiness/start/isolation overlaps; this one-device account-switch tail only checks retained acceptance and private picker.',
        'I14 broader gameplay prediction/recovery; I28/I31 rematch/copy restoration/combined journeys. B3 first independent capability review still follows I09-I12 together.',
        'I34 final full suite/deferred uncommon assertions, older migration-fixture reconciliation and I09 recovered-HTTP-error correction/recheck; I33 performance, I35 compatibility and I36/B8 complete interface-to-mock comparison/fixes/rechecks. All external release gates unchanged/unverified.'
    ], checks=checks, changedPaths=[])
Path(report_path).write_text(json.dumps(report, indent=2) + '\n')
actual = set(git('diff', '--name-only').splitlines()) | set(git('ls-files', '--others', '--exclude-standard').splitlines())
report['changedPaths'] = sorted(actual)
Path(report_path).write_text(json.dumps(report, indent=2) + '\n')
api = runpy.run_path('scripts/implementation-loop.py')
assert git('rev-parse', 'HEAD') == start and git('branch', '--show-current') == 'master'
assert not git('diff', '--cached', '--name-only')
assert before['authorization'] == 'authorized' and before['runner']['model'] == 'openai/gpt-6.1-sol#high'
assert api['selection'](before) == choice
api['validate_state'](after); api['validate_transition'](before, after, choice, report)
assert api['selection'](after) == dict(mode='implement', target='I10a', findingIds=[])
assert after['activeRun'] is None and after['runs'] == before['runs'] + [report_path]
old = {t['id']: t for t in before['tasks']}; new = {t['id']: t for t in after['tasks']}
assert new['I10a2']['status'] == 'implemented'
assert all(new[key] == value for key, value in old.items() if key != 'I10a2')
for key in before:
    if key not in ('tasks', 'runs'): assert before[key] == after[key], key
runner = object.__new__(api['Runner']); runner.root = Path.cwd(); runner.state_path = Path(state_path); runner.state_relative = state_path
runner.role_paths(before, choice, report, actual, report_path)
print('PASS supplied baseline/authorization/model/priority and current driver state/transition/path guards; only I10a2 implemented, next I10a')
print('PASS all other tasks, batches/reviews/findings/authorization/permissions/external release gates unchanged')
for check in checks:
    assert Path(check['evidence']).is_file()
    assert check['kind'] == 'diagnostic' or check['exitCode'] == 0
    for path in check.get('supportPaths', []): assert path in actual and Path(path).is_file()
identities = json.loads((prefix / 'input-identities.json').read_text())
for path, identity in identities.items(): assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == identity['sha256'], path
cases = set()
for name in ('host-junit-baseline.json', 'host-junit-targeted.json'):
    for text in json.loads((prefix / name).read_text()).values():
        root = ET.fromstring(text)
        assert not any(list(root.iter(tag)) for tag in ('failure','error','skipped'))
        cases.update((case.attrib['classname'], case.attrib['name']) for case in root.iter('testcase'))
assert len(cases) == 36
cleaned = json.loads(Path('/tmp/opencode/atw-device-f7a4a46b000000000000000000000001/cleaned.json').read_text())
assert cleaned['state']['cleaned'] and cleaned['listeners'] == [] and all(item is None for item in cleaned['currentProcesses'])
assert json.loads(Path('/tmp/opencode/atw-e2e-f7a4a46b000000000000000000000001/cleaned.json').read_text())['ownedResourcesRemoved']
for name in actual:
    path = Path(name)
    if path.suffix == '.png': continue
    data = path.read_bytes()
    assert b'\r' not in data and (path.suffix == '.xml' or not data or data.endswith(b'\n')), name
    assert all(line.rstrip() == line for line in data.splitlines()), name
    if path.suffix == '.json':
        record = json.loads(data)
        if isinstance(record, dict) and {'base64','sha256','bytes'} <= record.keys():
            raw = base64.b64decode(record['base64']); assert len(raw) == record['bytes'] and hashlib.sha256(raw).hexdigest() == record['sha256']
        if isinstance(record, dict) and 'outputSha256' in record:
            raw = record['output'].encode() if 'output' in record else base64.b64decode(record['outputBase64'])
            assert hashlib.sha256(raw).hexdigest() == record['outputSha256'], name
for path in (prefix / 'README.md',):
    text = path.read_text(); assert sum(line.startswith('```') for line in text.splitlines()) % 2 == 0
    for link in re.findall(r'\[[^\]]*\]\(([^)]+)\)', text):
        if '://' not in link and not link.startswith('#'): assert (path.parent / link.split('#')[0]).exists()
subprocess.run(['git', 'diff', '--check'], check=True)
assert set(report['changedPaths']) == actual
print('PASS retained checks/36 distinct host cases/final input hashes/lossless artifacts/JSON/docs/whitespace/exact changed paths and owned cleanup')
print('PASS report ready for mandatory scoped save/push; no additional green product/full-suite/device qualification rerun')
