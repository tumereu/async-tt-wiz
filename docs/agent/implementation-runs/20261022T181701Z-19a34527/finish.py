"""Build and validate this unit's exact report/state/input handoff, without rerunning product qualification."""
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
start = '8158e42e98404eda9357389129c210d5f12102c7'
def git(*args): return subprocess.check_output(['git', *args], text=True).strip()
before = json.loads(git('show', start + ':' + state_path))
after = json.loads(Path(state_path).read_text())
choice = dict(mode='implement', target='I09b2', findingIds=[])
checks = []
helpers = [str(prefix / name) for name in ('run.py', 'native-check.py', 'control.py', 'offload.py')]
for path in sorted(prefix.glob('*.json')):
    data = json.loads(path.read_text())
    if isinstance(data, dict) and {'command', 'exitCode', 'output'} <= data.keys():
        support = [str(prefix / 'run.py')]
        if any('native-check.py' in arg for arg in data['command']): support = helpers
        for arg in data['command']:
            if arg.startswith(str(prefix)) and arg.endswith('.py') and arg not in support: support.append(arg)
        if any('reattach.py' in arg for arg in data['command']): support.append(str(prefix / 'offload.py'))
        checks.append(dict(kind='required' if data['exitCode'] == 0 else 'diagnostic', command=shlex.join(data['command']),
            exitCode=data['exitCode'], evidence=str(path), supportPaths=support))
output = str(prefix / 'handoff.txt')
checks.append(dict(kind='diagnostic', command=f'python3 {prefix}/finish.py > {output} 2>&1', exitCode=1,
    evidence=str(prefix / 'handoff-path-diagnostic.txt'), supportPaths=[str(prefix / 'finish.py')]))
checks.append(dict(kind='diagnostic', command=f'python3 {prefix}/finish.py > {output} 2>&1', exitCode=1,
    evidence=str(prefix / 'handoff-cleanup-diagnostic.txt'), supportPaths=[str(prefix / 'finish.py')]))
checks.append(dict(kind='required', command=f'python3 {prefix}/finish.py > {output} 2>&1', exitCode=0,
    evidence=output, supportPaths=[str(prefix / 'finish.py')]))
report = dict(runId='20261022T181701Z-19a34527', sessionID='ses_e9b2b5588ffeftG6SLCH8kbOty', startCommit=start,
    mode='implement', target='I09b2', findingIds=[], newFindingIds=[], reviewedUnitIds=[], outcome='progress',
    summary='Delivered native recipient Accept/Decline and exact retained-lobby in-slot Reinvite over the existing server socket/processor, with negotiated fresh open and fenced same-process resume, volatile ordered original requests, monotonic accepted-summary convergence and production auth/navigation/account cleanup. Twenty-three affected host cases, incremental normal APK/instrumentation/lint/boundaries and strict release compile locks pass. One complete two-normal-APK API29 non-GMS UI-created decline/reinvite/interrupted-Accept/reconnect/both-orientation/not-ready-lobby/Back/reopen/process-restart journey passes; stable game/name/date/preparation and changed attempt IDs plus exactly three accepted original inbox requests are retained. Native-exposed fresh-stream handshake defect and V7 reset oracle repaired with targeted evidence; failed setup/lock/observer/IME/scroll/tmpfs/changed-APK commands remain diagnostic. Exact tested inputs/captures/JUnit and owned cleanup retained. Only I09b2 closes; I09b/I09 audits, preparation/start/cancel, B3 review and final qualification remain. Noncritical persistent recovered HTTP error has concrete source/captures and an explicit I34 correction/recheck obligation, not a false pass.',
    selectionReason='Clean supplied master/startCommit, active D-304 authorization, reviewed B1/B2, no actionable findings and delivered I09b1 agree with I09b2 as earliest ready child. Complete the normal two-user invitation capability and its directly affected transport/controller/auth/navigation/UI/reset-store integration, reusing I09b1 server durability and existing environment qualification. Only affected incremental builds/checks ran; no full suite, clean build, repeated green fault matrix or new scaffold/review layer. Parent closure and subsequent B3 preparation/start remain separately selected.',
    sourceIds=['P-043','P-045','P-062','T-398','T-414','T-415','T-416','T-417','T-418','T-484','T-636','T-637','T-638','T-639','T-667','T-677','T-681','T-690','T-696','F13','F14','F16','F23','F24','F37','F48','F59'],
    sourceDisposition='Partial I09.invitations assertions under accounts-decks-and-lobby D-195/D-208/D-209/D-259/D-287/D-294 and protocol/request recovery D-275/D-285/D-289. Actual native decision/reinvite/reconnect and affected host assertions executed; existing complete P/T/F cross-task ledger assignments are not promoted to whole-scenario passes.',
    remainingObligations=[
        'I09b/I09 separately selected small child-evidence audits; B3 first independent full-capability review after I09-I12. No ancestor or review advances here.',
        'I10-I12 prepared copies/Ready/start/cancel, P-072/T-669 cancellation/reinvite both orders, T-457/T-458/T-460 prepared-state preservation and two-Android prepared start; I13/I14/I26 extend this same queue/model for gameplay/rebase/full-tree, I28/I31 T-681 rematch/combined recovery.',
        'I34 required recovered-error correction/recheck: successful inviter same-game-landscape/accepted-inviter-portrait captures show an old HTTP reachability error retained after accepted socket/HTTP state recovered. LobbyController.refresh success copy retains state.error; Retry clears it. Accepted state/actions are usable and do not rewind. Explicit deferred obligation under D-308, not optional behavior, a claimed cleared-error pass, or another early detail loop.',
        'I34 full suite/deferred uncommon assertions and older migration-count fixtures, I33 measurements, I35 compatibility/signing/image architecture, I36/B8 all-interface/both-orientation mock comparison and all external gates remain unverified/unchanged.'
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
assert api['selection'](after) == dict(mode='implement', target='I09b', findingIds=[])
assert after['activeRun'] is None and after['runs'] == before['runs'] + [report_path]
old = {t['id']: t for t in before['tasks']}; new = {t['id']: t for t in after['tasks']}
assert new['I09b2']['status'] == 'implemented'
assert all(new[key] == value for key, value in old.items() if key != 'I09b2')
for key in before:
    if key not in ('tasks', 'runs'): assert before[key] == after[key], key
runner = object.__new__(api['Runner']); runner.root = Path.cwd(); runner.state_path = Path(state_path); runner.state_relative = state_path
runner.role_paths(before, choice, report, actual, report_path)
print('PASS exact authorization/model/baseline/priority and current driver state/transition/path guards; only I09b2 completes, next I09b')
print('PASS all other tasks/batches/reviews/findings/authorization/permissions/external release gates unchanged')
for check in checks:
    assert Path(check['evidence']).is_file()
    assert check['kind'] == 'diagnostic' or check['exitCode'] == 0
    for path in check.get('supportPaths', []): assert path in actual and Path(path).is_file()
identities = json.loads((prefix / 'input-identities.json').read_text())
for path, identity in identities.items(): assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == identity['sha256'], path
host = json.loads((prefix / 'host-junit.json').read_text())
assert sum(len(list(ET.fromstring(text).iter('testcase'))) for text in host.values()) == 23
for text in host.values():
    assert not any(list(ET.fromstring(text).iter(tag)) for tag in ('failure','error','skipped'))
for n in (1, 2):
    cleaned = json.loads(Path('/tmp/opencode/atw-device-19a3452700000000000000000000000' + str(n) + '/cleaned.json').read_text())
    assert cleaned['state']['cleaned'] and cleaned['listeners'] == [] and all(item is None for item in cleaned['currentProcesses'])
assert json.loads(Path('/tmp/opencode/atw-e2e-19a34527000000000000000000000001/cleaned.json').read_text())['ownedResourcesRemoved']
for name in actual:
    path = Path(name)
    if path.suffix == '.png': continue
    data = path.read_bytes()
    assert b'\r' not in data and (path.suffix == '.xml' or not data or data.endswith(b'\n')), name
    assert all(line.rstrip() == line for line in data.splitlines()), name
    if path.suffix == '.json':
        obj = json.loads(data)
        if isinstance(obj, dict) and {'base64','sha256','bytes'} <= obj.keys():
            raw = base64.b64decode(obj['base64']); assert len(raw) == obj['bytes'] and hashlib.sha256(raw).hexdigest() == obj['sha256']
for name in (str(prefix / 'README.md'),):
    text = Path(name).read_text()
    assert sum(line.startswith('```') for line in text.splitlines()) % 2 == 0
    for link in re.findall(r'\[[^\]]*\]\(([^)]+)\)', text):
        if '://' not in link and not link.startswith('#'): assert (Path(name).parent / link.split('#')[0]).exists()
subprocess.run(['git', 'diff', '--check'], check=True)
assert set(report['changedPaths']) == actual
print('PASS actual retained checks/23 host cases/final input hashes/lossless artifacts/JSON/docs/whitespace/exact path set and owned cleanup')
print('PASS report prepared for mandatory scoped save/push; no full-suite/device journey repeated after its final green run')
