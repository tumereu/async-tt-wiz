"""Generate this unit's report from actual retained checks, then validate the exact handoff."""
from pathlib import Path
import json
import re
import runpy
import shlex
import subprocess

prefix = Path(__file__).resolve().parent.relative_to(Path.cwd())
report_path = str(prefix) + '.json'
state_path = 'docs/agent/implementation-state.json'
start = '83ab5f69f46a5352f1c6e20f667df3ac9d55fc77'
def git(*args): return subprocess.check_output(['git', *args], text=True).strip()

before = json.loads(git('show', start + ':' + state_path))
after = json.loads(Path(state_path).read_text())
choice = dict(mode='implement', target='I09a', findingIds=[])
checks = []
for path in sorted(prefix.glob('*.json')):
    data = json.loads(path.read_text())
    if isinstance(data, dict) and set(('command', 'exitCode', 'output')).issubset(data):
        check = dict(kind='required' if data['exitCode'] == 0 else 'diagnostic',
            command=shlex.join(data['command']), exitCode=data['exitCode'], evidence=str(path))
        support = [p for p in data['command'] if isinstance(p, str) and p.startswith(str(prefix)) and p.endswith('.py')]
        if support: check['supportPaths'] = support
        checks.append(check)
finish_output = str(prefix / 'handoff-check.txt')
checks.append(dict(kind='required', command='python3 ' + str(prefix / 'finish.py') + ' > ' + finish_output + ' 2>&1',
    exitCode=0, evidence=finish_output, supportPaths=[str(prefix / 'finish.py')]))
report = dict(runId='20261022T163108Z-f7fc716d', sessionID='ses_e9b8c45bbffeqetGdBpnhucgFE', startCommit=start,
    mode='implement', target='I09a', findingIds=[], newFindingIds=[], reviewedUnitIds=[], outcome='progress',
    summary='Delivered normal-APK contacts/search/unilateral add, personal fantasy-name/reroll/touch-select-all dialog and atomic persistent named game+first invitation, server UTC date/preparation/attempt identities, participant-private Games/Invitations and exact waiting-lobby routing. 32 affected shared/client/auth/PostgreSQL/deck host cases are retained green without skips; incremental APK/lint/boundaries and a complete two-normal-APK API29 non-GMS real-backend journey pass. The native journey covers Cancel/Back/blank validation, suggested and edited text replacement, rotation, waiting-lobby reopen/navigation/process restart, recipient name/date and another same-account installation contacts/game synchronization. Initial select-all defect is repaired; red compile/device/tool-helper/tmpfs attempts remain diagnostics with passing affected corrections, not relabelled successes. Exact source/APK/image/JUnit/captures and owned cleanup retained. Only I09a closes; I09b decisions/durable ordered processor, I10-I12 preparation/Ready/start/cancellation, first B3 review, final broad qualification/UI comparison and external gates remain pending.',
    selectionReason='D-304 active authorization, clean initial master/index and exact supplied commit agree with I09a as earliest ready selection after independently reviewed B1/B2 and verified B2-R01. Implement the planned usable contacts/named-create/waiting-lobby vertical before I09b ordered invitation decisions; no competing existing-game HTTP writes. Affected checks cover new shared payloads, account-private HTTP persistence/global request outcomes/V6 forward migration, actual native input/navigation/reauth/account fences and two normal installations. Reuse unchanged environment/account/catalog qualification, incremental builds only; no full suite, clean build or unchanged fault matrix.',
    sourceIds=['P-043','P-045','P-062','T-398','T-414','T-415','T-416','T-417','T-418','T-484','T-636','T-637','T-638','T-639','T-667','T-677','T-681','T-690','T-696','F13','F14','F16','F23','F24','F37','F48','F59'],
    sourceDisposition='Partial I09.invitations delivery under D-195-D-197/D-208-D-209/D-218/D-241/D-259/D-287/D-289/D-294-D-297. Existing ledger remains unchanged: cross-task scenarios are not marked wholly executed. Sources are accounts-decks-and-lobby, authentication-and-sessions, protocol-v1, storage-mechanics, P01/P02/P05/L01 and cumulative F01-F88.',
    remainingObligations=[
        'I09b implements recipient Accept/Decline and exact-game Reinvite with the first shared ordered socket/durable game inbox/worker/outcome path. No acceptance/Ready/start or existing-game write is faked in I09a.',
        'I10-I12 prepared copies/Ready/start/cancellation, lifecycle overlaps including P-072/T-669 and the full prepared two-Android start; I13/I14 game/rebase/recovery extensions; I28/I31 rematch/combined multi-instance metadata. B3 first independent review remains after I09-I12 together.',
        'I32/I34/I36 retain larger text/tablet/keyboard-accessibility and complete both-orientation mock comparison. Narrow landscape contacts use the reachable stacked form below the 600dp content-width two-column threshold; this responsive adaptation and current unavailable later-feature labels are not a final UI pass.',
        'I34 retains the final full suite and deferred foundation assertions, including older AccountRepositoryTest/PasswordResetTest/FoundationDatabaseTest and foundation-script migration-count fixtures needing reconciliation with the final schema. Actual current V5-to-V6 retained account/deck/definition/outcome migration and affected deck upgrade checks pass now; broader checks were not run or claimed.',
        'I35 compatibility/upgrade/image architecture, I36/B8 final full interface-to-mock inventory/review/fixes and all external release gates remain unchanged/unverified. No deployment, paid resources, subagent, extra loop, model/grant/authorization/runner change or UI-document edit.'
    ], checks=checks, changedPaths=[])

Path(report_path).write_text(json.dumps(report, indent=2) + '\n')
actual = set(git('diff', '--name-only').splitlines()) | set(git('ls-files', '--others', '--exclude-standard').splitlines())
report['changedPaths'] = sorted(actual)
Path(report_path).write_text(json.dumps(report, indent=2) + '\n')
api = runpy.run_path('scripts/implementation-loop.py')
assert git('rev-parse', 'HEAD') == start and git('branch', '--show-current') == 'master'
assert not git('diff', '--cached', '--name-only')
assert before['authorization'] == 'authorized' and 'D-304' in Path(before['authorizationRecord']).read_text()
assert before['runner']['model'] == 'openai/gpt-6.1-sol#high'
assert api['selection'](before) == choice
api['validate_state'](after)
api['validate_transition'](before, after, choice, report)
assert api['selection'](after) == dict(mode='implement', target='I09b', findingIds=[])
assert after['activeRun'] is None and after['runs'] == before['runs'] + [report_path]
for key in before:
    if key not in ('runs', 'tasks'): assert before[key] == after[key], key
old = {t['id']: t for t in before['tasks']}; new = {t['id']: t for t in after['tasks']}
assert new['I09a']['status'] == 'implemented'
assert all(new[key] == value for key, value in old.items() if key != 'I09a')
assert new['I09']['status'] == 'in-progress' and new['I09b']['status'] == 'pending'
print('PASS exact initial baseline/authorization/priority; only I09a completes; current driver state/transition guards; next I09b')
print('PASS all other tasks/batches/reviews/findings/model/authorization/permissions/external gates unchanged')

runner = object.__new__(api['Runner']); runner.root = Path.cwd(); runner.state_path = Path(state_path); runner.state_relative = state_path
runner.role_paths(before, choice, report, actual, report_path)
for check in report['checks']:
    assert Path(check['evidence']).is_file(), check
    assert check['kind'] == 'diagnostic' or check['exitCode'] == 0
    for support in check.get('supportPaths', []): assert Path(support).is_file() and support in actual
for path in actual:
    p = Path(path)
    if p.suffix == '.png': continue
    data = p.read_bytes()
    # Native hierarchy XML is an exact tool artifact and need not have a final newline.
    assert b'\r' not in data and (p.suffix == '.xml' or not data or data.endswith(b'\n')), path
    assert all(line.rstrip() == line for line in data.splitlines()), path
    if p.suffix == '.json': json.loads(data)
for path in ('server/README.md', 'server/LOBBY.md', str(prefix / 'README.md')):
    text = Path(path).read_text()
    assert sum(line.startswith('```') for line in text.splitlines()) % 2 == 0, path
    for link in re.findall(r'\[[^\]]*\]\(([^)]+)\)', text):
        if '://' not in link and not link.startswith('#'):
            assert (Path(path).parent / link.split('#')[0]).exists(), (path, link)
identities = json.loads((prefix / 'input-identities.json').read_text())
import hashlib
for path, identity in identities.items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == identity['sha256'], path
print('PASS retained final source/lock/APK/image hashes, exact owned changed paths, JSON/links/fences/whitespace and truthful diagnostic/required checks')
subprocess.run(['git', 'diff', '--check'], check=True)
assert set(report['changedPaths']) == actual
print('PASS safe handoff report; no new full-suite/build/device qualification run; save/push still required')
