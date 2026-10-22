"""Generate the I03 handoff from this unit's actual commands and owned diff."""
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[3]
PREFIX = 'docs/agent/implementation-runs/20261022T070751Z-df665f98'
DEVICE = '/tmp/opencode/atw-device-df665f9811114af58b9f3861313df8c1'
ENV = '/tmp/opencode/atw-e2e-df665f9811114af58b9f3861313df8c1'
GRADLE = './scripts/gradle.sh --no-daemon --console=plain '
ORIGIN = '-PatwApiOrigin=http://127.0.0.1:46277 '
checks = []


def check(suffix, command, exit_code=0, kind='required', support=()):
    checks.append({'kind': kind, 'command': command, 'exitCode': exit_code, 'evidence': PREFIX + suffix,
                   **({'supportPaths': [PREFIX + item for item in support]} if support else {})})


check('-affected-build.txt', GRADLE + '-PatwApiOrigin=http://127.0.0.1:18080 :androidApp:testDebugUnitTest :client-storage:testDebugUnitTest :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest :androidApp:lintDebug verifyModuleBoundaries')
check('-device-test-build.txt', GRADLE + '-PatwApiOrigin=http://127.0.0.1:18080 :androidApp:assembleDebugAndroidTest :e2e:compileTestKotlin')
check('-runner-tests.txt', 'python3 -m unittest discover -s tests -p test_device_runner.py -v')
check('-startup-tests.txt', 'python3 -m unittest discover -s tests -p test_device_app.py -v')
check('-environment.txt', f'python3 scripts/e2e-environment.py --directory {ENV} provision')
check('-device-provision.txt', f'python3 scripts/e2e-devices.py --directory {DEVICE} provision --config {PREFIX}-device-config.json', support=('-device-config.json',))
check('-configured-apks.txt', GRADLE + ORIGIN + ':androidApp:assembleDebug :androidApp:assembleDebugAndroidTest')
prepare = f'python3 scripts/e2e-devices.py --directory {DEVICE} prepare-app --environment {ENV} --apk {ROOT}/androidApp/build/outputs/apk/debug/androidApp-debug.apk'
check('-prepare-app.txt', prepare)
check('-profile-device.txt', f'python3 scripts/run-device-tests.py --directory {DEVICE} profile')
check('-prepare-shell.txt', prepare)
check('-shell-ui.txt', f'python3 scripts/run-device-tests.py --directory {DEVICE} shell', 1, 'diagnostic')
check('-navigation-fix-build.txt', GRADLE + ORIGIN + ':androidApp:assembleDebug :androidApp:assembleDebugAndroidTest :androidApp:lintDebug')
check('-unroute-new-apk.txt', f'python3 scripts/e2e-devices.py --directory {DEVICE} unroute')
check('-prepare-navigation-fix.txt', prepare)
check('-shell-ui-fixed.txt', f'python3 scripts/run-device-tests.py --directory {DEVICE} shell')
for suffix in ('-presentation.txt', '-presentation-layout.txt', '-presentation-flags.txt',
               '-presentation-api29-window.txt', '-presentation-api29-fixed-test.txt'):
    check(suffix, f'python3 {PREFIX}-presentation.py', 1, 'diagnostic', ('-presentation.py',))
check('-presentation-reset.txt', f'python3 scripts/e2e-environment.py --directory {ENV} reset')
check('-presentation-api29-reset.txt', f'python3 {PREFIX}-presentation.py', support=('-presentation.py',))
check('-final-affected.txt', GRADLE + ORIGIN + ":androidApp:testDebugUnitTest --tests '*AuthControllerTest' :androidApp:lintDebug :client-storage:verifySqlDelightMigration")
check('-final-apks.txt', GRADLE + ORIGIN + ':androidApp:assembleDebug :androidApp:assembleDebugAndroidTest')
check('-final-logout-checks.txt', GRADLE + ORIGIN + ":androidApp:testDebugUnitTest --tests '*AuthControllerTest' :androidApp:lintDebug :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest")
check('-device-cleanup.txt', f'python3 scripts/e2e-devices.py --directory {DEVICE} cleanup')
check('-environment-cleanup.txt', f'python3 scripts/e2e-environment.py --directory {ENV} cleanup')
check('-retention.txt', f'python3 {PREFIX}-retain.py', kind='diagnostic', support=('-retain.py',))
check('-retention-final.txt', f'python3 {PREFIX}-retain.py', support=('-retain.py',))
check('-validation-initial.txt', f'python3 {PREFIX}-validate.py', 1, 'diagnostic', ('-validate.py', '-report.py'))
check('-validation.txt', f'python3 {PREFIX}-validate.py', support=('-validate.py', '-report.py'))

paths = set(subprocess.check_output(['git', 'diff', '--name-only', '1151d680ad86a784013a6751da23364748088006'], cwd=ROOT).decode().splitlines())
paths.update(subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard'], cwd=ROOT).decode().splitlines())
paths.update((PREFIX + '.json', PREFIX + '-validation.txt'))
report = {
    'runId': '20261022T070751Z-df665f98', 'sessionID': 'ses_e9d8ff772ffeQ9pXgJ2JDrrUdI',
    'startCommit': '1151d680ad86a784013a6751da23364748088006', 'mode': 'implement', 'target': 'I03',
    'findingIds': [], 'newFindingIds': [], 'reviewedUnitIds': [], 'outcome': 'progress',
    'summary': 'Implemented only I03: N01/N02 native authenticated Menu/Games shell, fixed Logout, portrait drawer and landscape persistent non-gameplay navigation, native Back/fullscreen/rotation and authorized personal destination restoration. Real SQLite account activation/Logout isolates and deletes private records/cursors/drafts, preserves public cache, finishes interrupted local cleanup and fences old UI/private-write callbacks. Protected credentials clear locally without waiting for best-effort remote revocation; offline startup can explicitly Logout. Affected host/storage/Android profile checks, a complete real normal-APK/Compose UI A/B account/navigation/restart/reauth/Logout journey and a targeted actual fullscreen/rotation/keyboard/Back correction pass. Final exception-shield/offline-control changes have passing affected host/lint/final-APK checks. Diagnostics and exact cleanup are retained. I04 is next; B1 review, real deck/contact/game data/sync/recovery, final qualification/UI comparison and all release gates remain open.',
    'selectionReason': 'Clean supplied master/index and exact startCommit match; D-304 explicitly authorizes application implementation, I01/I02 are implemented and there is no finding or due capability review. I03 is the earliest ready task before I04, not another foundation qualification. Affected scope is authenticated native navigation/lifecycle, personal profile/context, protected local Logout and callback/account isolation, plus the existing startup/owned-device runner seam. Use incremental builds, focused real SQLite/controller/Android tests and the normal UI journey; recheck observed drawer/fullscreen failures only. Reuse passing profile/full-journey evidence and test final small Logout changes with affected host checks rather than repeating a green full journey, clean build, fault matrix or full suite.',
    'checks': checks, 'changedPaths': sorted(paths),
    'sourceIds': ['P-043', 'P-047', 'P-065', 'T-397', 'T-480', 'T-481', 'T-482', 'T-543', 'T-600', 'T-601',
                  'T-621', 'T-647', 'T-666', 'T-676', 'T-677', 'T-682', 'F03', 'F07', 'F08', 'F09', 'F10',
                  'F11', 'F12', 'F13', 'F14', 'F25', 'F26', 'F27', 'F28'],
    'sourcePaths': ['docs/agent/implementation-plan.md', 'docs/agent/delivery-first-execution.md',
                    'docs/agent/coverage/ledger.json', 'docs/design/03-contracts/accounts-decks-and-lobby.md',
                    'docs/design/03-contracts/android-experience.md', 'docs/design/04-technical/authentication-and-sessions.md',
                    'docs/design/02-experience/ui/review.md', 'docs/design/02-experience/ui/feedback.md'],
    'coverage': {
        'scope': 'I03.shell portions only; source-pinned ledger assignments/full scenarios are not relabeled executed passes.',
        'delivered': ['Menu/Games/Manage decks/Contacts shell routes; N01/N02 logo/icons/Back/Close/persistent versus drawer navigation and fixed footer',
                      'Real authorized-token restart and same-account personal destination recovery; no navigation request/resubmission or transfer of unfinished UI',
                      'Real SQLite same-profile retention, atomic cross-account and Logout private deletion with public-cache independence; actual Android Keystore/profile cleanup and interrupted-cleanup completion',
                      'Immediate volatile account-state clearing, stale UI/private-save generation fencing, offline and failed-remote-revocation local Logout; failure lock/retry without destructive migration fallback',
                      'Actual API29 status/navigation bars hidden in both orientations and after keyboard return, scrolled account action and native drawer Back'],
        'remaining': ['T-397/T-676/F07/F08/F10/F13 synchronized game rows, Games-before-Invitations and long-list/lifecycle/gameplay-drawer checks remain I09-I16/I31/I34/I36',
                      'T-481/T-482/T-543/T-600/T-601/T-621/T-647/T-666 game/outcome/inspection/late-server-effect and multi-device reset portions remain I04/I13/I14/I18/I26/I31/I34; no outgoing journal or special reset race is added',
                      'T-677/T-682/F25-F28 cross-feature context/hold/actions/exits/dialogs and all final interface comparisons remain with owning feature tasks and I32/I36/B8']
    },
    'diagnostics': [
        'Initial normal UI drawer Back failed. Replaced mirrored Material DrawerState with one controller-owned modal overlay; the complete fresh real UI journey then passed.',
        'Native captures exposed persistent API29 status bar after rotation. Focus/resume/layout-post experiments alone failed; observed decor flags=5894 already included fullscreen while status remained visible, IME=false and window fullscreen absent. The API29 fullscreen window flag passes actual inset, portrait/landscape, native Back and keyboard-return checks. Failed experiments remain diagnostic, not passing claims.',
        'One targeted test compilation failed because Espresso was not an installed dependency; use the existing platform InsetsController to hide the real IME, with no dependency/grant changes. A later diagnostic registration failed after five existing synthetic registrations in the same rate window, consistent with the existing registration limit. An explicit owned-environment scenario reset restored setup; the final targeted check passed. No rate limit or assertion was weakened.',
        'Full Appium screenshots are explicitly before the last fullscreen correction, not current final visual acceptance. Final presentation evidence checks actual device insets. Subsequent small remote-callback shielding and offline-startup Logout-link additions have affected host/lint/final-APK coverage; the older screenshots/whole-journey output are not falsely claimed rerun on the final binary.'
    ],
    'retainedObligations': [
        'Only I03 advances. I04 owns reset/deep links/mail/second-device B1 journeys; B1 remains pending until I04 and independent complete-capability review.',
        'Games/Manage decks/Contacts routes truthfully state unavailable capabilities, not fabricated empty server data. I05-I12 deliver their real data/synchronized listings and I13/I14/I26/I31 deliver game/request/outcome/read-only recovery. Future feature workers must join these account-generation/private-profile boundaries.',
        'No source requirement is globally passed by partial shell tests. I34 owns final full suite and deferred broad foundation/fault/long-list/multi-device assertions, I35 compatibility/signed upgrades/arm64, and I36/B8 the complete final interface-to-mock comparison/fixes. I33 physical measurement and all external release gates remain unverified.',
        'No subagent, other loop, deployment, paid resource, UI-document publication/edit, permission/authorization/model/runner change or release-gate update. Owned device/Appium/ADB/public routes/Compose resources are cleaned; large synthetic APK/AVD artifacts stay private with retained identities.'
    ]
}
(ROOT / (PREFIX + '.json')).write_text(json.dumps(report, indent=2) + '\n')
print('Generated I03 report with', len(paths), 'owned changed paths and', len(checks), 'actual retained command results')
