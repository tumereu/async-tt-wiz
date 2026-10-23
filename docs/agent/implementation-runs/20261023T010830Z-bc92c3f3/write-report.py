"""Assemble this run's handoff artifact from its actual captured commands and exact owned diff."""
from pathlib import Path
import json
import shlex
import subprocess

prefix = Path(__file__).resolve().parent.relative_to(Path.cwd())
report_path = str(prefix) + '.json'
checks = []
helpers = {
    'native-check.py': ['native-check.py', 'control.py', 'offload.py'],
    'prepare-apps.py': ['prepare-apps.py', 'offload.py'],
    'setup-devices.py': ['setup-devices.py'],
    'retain.py': ['retain.py'],
    'retain-cleanup.py': ['retain-cleanup.py'],
}
for path in sorted(prefix.glob('*.json')):
    saved = json.loads(path.read_text())
    if not isinstance(saved, dict) or not {'command', 'exitCode', 'outputSha256'} <= saved.keys(): continue
    check = dict(kind='required' if saved['exitCode'] == 0 else 'diagnostic',
        command=shlex.join(saved['command']), exitCode=saved['exitCode'], evidence=str(path))
    for argument in saved['command']:
        if Path(argument).name in helpers:
            check['supportPaths'] = [str(prefix / name) for name in helpers[Path(argument).name]]
    checks.append(check)
checks.insert(0, dict(kind='diagnostic',
    command="python3 scripts/capture-check.py docs/agent/implementation-runs/20261023T010830Z-bc92c3f3/affected-client.json -- ./scripts/gradle.sh --no-daemon --console=plain :androidApp:testDebugUnitTest --tests '*LobbyStartTest' --tests '*GameSessionTest' --tests '*LobbyPreparationTest' --tests '*PreparationTransferSessionTest' --tests '*AuthControllerTest' --tests '*GameLobbyIntegrationTest' :androidApp:assembleDebug :androidApp:lintDebug verifyModuleBoundaries",
    exitCode=1, evidence=str(prefix / 'capture-path-diagnostic.txt')))
checks.append(dict(kind='required', command='python3 ' + str(prefix / 'handoff.py') + ' > ' + str(prefix / 'handoff.txt') + ' 2>&1',
    exitCode=0, evidence=str(prefix / 'handoff.txt'), supportPaths=[str(prefix / 'handoff.py')]))
paths = set(subprocess.check_output(['git', 'diff', '--name-only'], text=True).splitlines())
paths.update(subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard'], text=True).splitlines())
paths.add(report_path)
report = dict(
    runId='20261023T010830Z-bc92c3f3', sessionID='ses_e99b29b8cffempl832e6eswlf7',
    startCommit='aa0d133bc4b62eee96a54b998130c5685959536d', mode='implement', target='I11b',
    findingIds=[], newFindingIds=[], reviewedUnitIds=[], outcome='progress',
    summary='Completed I11b native explicit starter/Ready/withdrawal and the real accepted production initial table. Same ordered session/pure prediction model, with no local start, implicit Random roll, Sideboard play route, demo state or fake future actions. Both actual normal APKs create synthetic accounts/decks/invitation, preserve first Ready before acceptance/leave, prepare the recipient, and converge on one play/root after an interrupted second-Ready acknowledgement and real backend restart. Both-orientation table/drawer/reopen, server-first app restart and native Back are evidenced; eleven originals retain accepted outcomes, two independent Library orders, recorded Random starter, 20 life, turn-1 Untap and no opening draw. Thirty final affected host cases, incremental APK/lint/boundaries, focused reset regression and real populated V9 reset/owned cleanup pass. Native diagnostic stages and passing retained tail are accurately distinguished; final replica-correlation repair is covered by affected host rechecks/compile, not falsely called a new native final-binary run. Only I11b closes; I11 parent/I12/B3 first review and later qualification/release obligations remain open.',
    selectionReason='Clean supplied master/startCommit, active D-304 authorization, reviewed B1/B2, verified B2-R01 and implemented I11a agree with the earliest-ready I11b selection. No due fix or full-group review precedes it. Deliver the native usable preparation-to-table capability of the saved I11 split, not another qualification child. Affected scope is readiness/starter session/controller/navigation prediction and accepted-play correlation, production initial Canvas/scene/accessibility geometry, native both-player start/recovery, and the necessary V9 reset-store oracle. Reuse I11a unchanged shared randomness/server start evidence; execute affected client/native/build/reset checks only. No full suite, clean build, unchanged fault matrix, child review, subagent, competing loop, deployment, paid resource, permission/model/authorization change or release-gate update.',
    sourceIds=['P-011','P-045','P-046','P-050','P-051','P-052','P-053','P-054','P-061','T-400','T-401','T-402','T-403','T-404','T-405','T-406','T-433','T-434','T-435','T-436','T-437','T-438','T-439','T-446','T-447','T-634','T-635','T-655','T-696','F33','F60'],
    sourceDisposition='Partial I11.start native readiness/initial table/recovery assertions against accounts-decks-and-lobby D-191/D-200-D-205/D-274, server authority/model D-259, ordered recovery D-275/D-278/D-289 and current L01/L02/L05/G01 direction. Source assignment is not whole-scenario or final UI acceptance. I12 retains cancellation/list/preparation/start overlap and complete selected-source isolation; later tasks retain gameplay/history/rematch/final qualification.',
    evidenceComposition=[
        'native-journey.json is diagnostic: UI-created initial setup/import passed before the test scrolled only to Starting player heading instead of the offscreen choice. Exact-control scrolling repaired the oracle; no product change.',
        'native-retained-starter.json is diagnostic: retained setup then actual Ready/withdrawal/acceptance/recipient preparation/own-only choice clearing/no prestart roll, interrupted ack/backend restart and both-orientation table/drawer/reopen assertions passed before the test incorrectly expected restart to skip the restored Games list.',
        'native-retained-restart.json is the passing retained tail: reopen from the normal Games list, exact initial table after both process restarts and native Back. It does not newly repeat prior setup/readiness/table orientations. Original failed output/JUnit/captures are preserved.',
        'After native checks only GameSession accepted-replica retirement/correlation changed, preventing HTTP-summary high-water or an ignored older correlated revision from prematurely retiring prediction. All 30 final affected host cases and normal APK compile pass; inputs.json distinguishes installed native and final compiled artifacts.',
        'Initial missing output-directory wrapper failure and partial tmpfs APK-readback diagnostic are retained. Scoped owned completed-readback offloading preserves bytes/hashes and same peer preparation passes. No unrelated temporary cleanup, safeguard weakening or runtime hook.'
    ],
    remainingObligations=[
        'Selected I11 parent audits exact I11a/I11b evidence; I12 completes list/lobby/Cancel-versus-start/acceptance/preparation/isolation overlaps. First independent B3 review remains after I09-I12.',
        'I13-I16 extend this same accepted model/processor/table with gameplay prediction/Canvas interactions; I18 mulligans, I22-I26 history/full recovery, I28/I31 rematch/integrated lifecycle.',
        'I32/I34 retain large-text/reduced-motion, previously recorded recovered HTTP-error/migration-fixture corrections, deferred uncommon assertions and final full suite. I33 measurements, I35 compatibility/upgrade, I36/B8 complete interface-to-mock comparison/fixes/rechecks and all external release gates remain required/unverified.'
    ], checks=checks, changedPaths=sorted(paths))
Path(report_path).write_text(json.dumps(report, indent=2) + '\n')
print('Wrote', report_path, 'with', len(checks), 'actual check references and', len(paths), 'owned paths')
