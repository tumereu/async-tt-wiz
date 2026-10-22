"""Generate this run's JSON report from retained actual command records and exact owned paths."""
from pathlib import Path
import json
import shlex
import subprocess

prefix = Path(__file__).resolve().parent.relative_to(Path.cwd())
report_path = str(prefix.with_suffix('.json'))
names = ['affected-host', 'environment-provision', 'device-provision', 'hosted-build', 'prepare-app',
         'native-journey', 'native-selector-recheck', 'native-container-recheck', 'rotation-fix-build',
         'unroute-for-update', 'prepare-rotation-fix', 'native-rotation-recheck', 'native-scroll-recheck',
         'native-final', 'retention', 'cleanup-device', 'cleanup-environment', 'handoff-initial']
checks = []
for name in names:
    path = prefix / (name + '.json')
    value = json.loads(path.read_text())
    check = dict(kind='required' if value['exitCode'] == 0 else 'diagnostic',
                 command=shlex.join(value['command']), exitCode=value['exitCode'], evidence=str(path))
    if name == 'native-final':
        check['supportPaths'] = [str(prefix / 'native-check.py')]
    if name == 'retention':
        check['supportPaths'] = [str(prefix / 'retain.py')]
    checks.append(check)
checks.append(dict(kind='diagnostic', command='/home/tuomas/Android/Sdk/platform-tools/adb -H 127.0.0.1 -P 15038 -s emulator-5564 logcat -d -s AndroidRuntime:E',
                   exitCode=0, evidence=str(prefix / 'rotation-crash.txt')))
checks.append(dict(kind='required', command='python3 ' + str(prefix / 'handoff-check.py'), exitCode=0,
                   evidence=str(prefix / 'handoff-check.json'), supportPaths=[str(prefix / 'handoff-check.py')]))
paths = set(subprocess.check_output(['git', 'diff', '--name-only'], text=True).splitlines())
paths.update(subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard'], text=True).splitlines())
paths.add(report_path)
report = dict(runId='20261022T114326Z-f8a9eff1', sessionID='ses_e9c93abbcffeyZswRXHGrxrCJ7',
    startCommit='405dcd3e82e703fb3fa47fd4cd95778ea1381db8', mode='implement', target='I06a',
    findingIds=[], newFindingIds=[], reviewedUnitIds=[], outcome='progress',
    summary='Implemented common text parsing/exact installed-catalog printing and complete-face resolution, preserved corrections/actionable line errors and native D03/D04/D04b input/read-only preview/Edit/Exit/Back. Focused real file-backed tests cover both fixture families, all their existing import cases, optional metadata, ambiguity and quantity/section arithmetic. Affected incremental host/account/controller tests, assembly/lint/boundaries pass. Complete final-binary API29 non-GMS Appium journey passes hosted catalog, correction/retry, section art/text preview, both orientations/rotation, long-preview controls and Logout/relogin cleanup. Reproduced preview-rotation crash is fixed with a regression; original test-selector/scroll/sign-in failures and crash output remain diagnostics. Exact current identities/JUnit/captures and owned cleanup retained. Only I06a closes; Save is visibly disabled until I06b real atomic persistence. No fake/local save, first B2 review, full-suite or release claim.',
    selectionReason='Clean supplied master/startCommit matches; D-304 authorization is active, B1 reviewed, findings empty and I05 implemented. I06a is the earliest ready child and delivers the usable import input/error/preview path before I06b persistence. Affected scope is new catalog resolution and native personal draft/account/navigation/rotation integration; focused parser/controller/account checks and actual normal-APK journey exercise it. Rechecks follow concrete new assertions or observed failures, not a full-suite/clean-build/unchanged I05 matrix ritual.',
    sourceIds=['P-044', 'P-048', 'T-423', 'T-424', 'T-425', 'T-426', 'T-427', 'T-573', 'T-628'],
    sourceDisposition='I06.import partial syntax/resolution/correction/preview assertions only; unchanged ledger retains I06b/I07/I08 and later saved-definition/copy/recovery obligations. D-198/D-199/D-271/D-289 and current D03/D04/D04b plus cumulative F01-F88 constrain presentation; no whole cross-feature scenario or feedback acceptance is claimed.',
    remainingObligations=[
        'I06b must replace the disabled Save with authenticated complete definitions/atomic named save, stable original-outcome retry and owned read-back, then exercise failure/restart/reopen. I06 parent reconciles children; B2 first independent review follows I05-I08.',
        'I07/I08 extend this same definition/result foundation with real collection/inspector and contextual Overwrite/Merge. No authoring UI, local-authority saved deck or temporary save endpoint was added.',
        'I34 retains full suite/broader fault assertions; I35 device/upgrade and I36/B8 complete final interface comparison remain. Current XML contains the final five parser and three import-controller cases; earlier complete Android host and Logout-regression passes are command records, not a fabricated new full XML run.',
        'I32/I36 should assess narrow missing-art thumbnail name fragments against the existing fallback rule; full adjacent names remain readable. Existing NativeShellTest lines 115/117 still contain pre-I05 unavailable-decks copy; I07 should update this older integration oracle and I34 must not claim it green beforehand. These concrete noncritical follow-ups are recorded in README, not silently waived.',
        'All external rights/signing/operator/hardware gates remain unchanged/unverified. No deployment, paid resources, licensed provider call, subagents, new loop, model/grant/runner change or UI-packet edit/publication occurred.'
    ], checks=checks, changedPaths=sorted(paths))
Path(report_path).write_text(json.dumps(report, indent=2) + '\n')
print('Generated report for I06a with', len(checks), 'actual checks and', len(paths), 'owned changed paths')
