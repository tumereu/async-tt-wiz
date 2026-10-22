"""Generate this unit's durable JSON report from its exact owned paths/check records."""
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[3]
RUN = '20261022T054937Z-41060cc6'
BASE = 'docs/agent/implementation-runs/' + RUN
DEVICE = '/tmp/opencode/atw-device-41060cc611114af58b9f3861313df8c1'
ENV = '/tmp/opencode/atw-e2e-41060cc622224af58b9f3861313df8c1'
GRADLE = './scripts/gradle.sh --no-daemon --console=plain'
configured = GRADLE + ' -PatwApiOrigin=http://127.0.0.1:18080'
checks = []
def check(suffix, command, code=0, kind='required', support=()):
    value = dict(kind=kind, command=command, exitCode=code, evidence=BASE + '-' + suffix + '.txt')
    if support:
        value['supportPaths'] = [BASE + '-' + path for path in support]
    checks.append(value)

check('build-initial', GRADLE + ' --write-locks resolveAndLockAll :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest', 1, 'diagnostic')
check('build-locks', GRADLE + ' --write-locks resolveAndLockAll :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest')
check('android-tests', configured + ' :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest :androidApp:lintDebug verifyModuleBoundaries', 1, 'diagnostic')
check('lint-fixed', configured + ' :androidApp:assembleDebug :androidApp:lintDebug')
check('auth-device', 'python3 scripts/run-device-tests.py --directory ' + DEVICE + ' auth', 1, 'diagnostic')
check('reset-recovery', 'python3 scripts/e2e-environment.py --directory /tmp/opencode/atw-e2e-41060cc611114af58b9f3861313df8c1 reset', 1, 'diagnostic')
check('reset-regression', 'python3 -m unittest discover -s tests -p test_account_environment.py -v')
check('auth-device-fixed', 'python3 scripts/run-device-tests.py --directory ' + DEVICE + ' auth', 1, 'diagnostic')
for name in ('input-device-recheck', 'input-device-passing'):
    check(name, 'python3 ' + BASE + '-targeted-device.py', 1, 'diagnostic', ('targeted-device.py',))
check('input-device-final', 'python3 ' + BASE + '-targeted-device.py', support=('targeted-device.py',))
for name in ('accounts-ui', 'accounts-ui-recheck'):
    check(name, 'python3 scripts/run-device-tests.py --directory ' + DEVICE + ' accounts', 1, 'diagnostic')
check('accounts-ui-paste', 'python3 scripts/run-device-tests.py --directory ' + DEVICE + ' accounts')
check('saved-state', 'python3 ' + BASE + '-targeted-device.py AuthSavedStateTest', 1, 'diagnostic', ('targeted-device.py',))
check('saved-state-fixed', 'python3 ' + BASE + '-targeted-device.py AuthSavedStateTest', support=('targeted-device.py',))
check('runner-final', 'python3 -m unittest discover -s tests -p test_device_runner.py -v')
check('final-lint-compile', configured + ' :androidApp:lintDebug :e2e:compileTestKotlin')
check('packaged-backup', 'python3 scripts/check-android-storage.py')
check('dependencies', 'python3 ' + BASE + '-dependencies.py', support=('dependencies.py',))
check('network-storage-recheck', configured + " :androidApp:testDebugUnitTest --tests '*AuthControllerTest.failuresKeepCorrectionUsableButUnsavedTokenDoesNotEnter'")
check('device-cleanup', 'python3 scripts/e2e-devices.py --directory ' + DEVICE + ' cleanup')
check('environment-cleanup', 'python3 scripts/e2e-environment.py --directory ' + ENV + ' cleanup')
check('retention', 'python3 ' + BASE + '-retain.py', 0, 'diagnostic', ('retain.py',))
check('retention-final', 'python3 ' + BASE + '-retain.py', support=('retain.py',))
check('handoff', 'python3 ' + BASE + '-handoff.py', support=('handoff.py', 'report.py'))

def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT).decode().splitlines()

paths = set(git('diff', 'HEAD', '--name-only')) | set(git('ls-files', '--others', '--exclude-standard'))
paths |= {BASE + '.json', BASE + '-handoff.txt'}
assert all(path.startswith(('androidApp/', 'e2e/', 'tests/', BASE)) or path in
           ('docs/agent/implementation-state.json', 'gradle/libs.versions.toml') for path in paths)
report = dict(
    runId=RUN, sessionID='ses_e9dd79982ffeTh6kl0k7V5SVLw',
    startCommit='db9e1f9da76211a3eb9d92658ba4ad2ad040965e', mode='implement', target='I02b',
    findingIds=[], newFindingIds=[], reviewedUnitIds=[], outcome='progress',
    summary='I02b implemented: native A01/A02/A02b, real bounded Ktor/serialization HTTP, exact passwords and safe account identity, nonexportable AndroidKeyStore AES-GCM token protection/restore/revocation handling. Initial eight host tests pass before a separate lint failure; an added network/storage assertion passes alone. Six distinct API29 non-GMS instrumentation tests pass across four initial green startup/Keystore cases and targeted exact-input/saved-state rechecks; a complete one-device Kotlin/JUnit/Appium account/error/paste/visibility/orientation/scroll/Back/login/restart/revocation journey and ordinary public-revoke checkpoint pass. The stale V1-only independent-reset oracle is repaired to require V2 plus empty accounts/sessions. Actual evidence, source/artifact identities and owned cleanup retained. I02 parent, I03 shell/Logout, I04 reset/second device, B1 independent review and final qualification/release gates remain open.',
    selectionReason='The clean supplied master/startCommit matches, D-304 authorizes application work, I02a is implemented and no finding or due review precedes this earliest pending child. Deliver actual normal-APK account entry rather than another scaffold-certification layer. Affected checks cover native forms/input/rotation/saved-state, exact client HTTP/error/authorization handling, real device Keystore and normal app restart, the existing storage-open gate and packaged backup policy, and the account-aware synthetic reset seam. Use incremental builds and affected regressions; reuse four green instrumentation/initial host assertions and rerun only failed or newly added assertions. No subagent, parallel loop, deployment, paid resource or model/permission/authorization change.',
    checks=checks, changedPaths=sorted(paths),
    sourceIds=['P-043', 'P-047', 'T-396', 'T-419', 'T-420', 'T-422', 'T-572', 'T-573', 'T-621', 'T-657', 'T-696', 'F04', 'F05', 'F06', 'F11', 'F58'],
    sourcePaths=['docs/design/03-contracts/accounts-decks-and-lobby.md', 'docs/design/03-contracts/android-experience.md',
                 'docs/design/04-technical/authentication-and-sessions.md', 'docs/design/02-experience/ui/review.md',
                 'docs/design/02-experience/ui/feedback.md', 'docs/agent/coverage/ledger.json', 'server/README.md'],
    diagnosticDisposition=[
        'Gradle Kotlin DSL java extension shadowed the java package in URI validation; explicit URI import fixes compilation. Debug loopback policy initially omitted includeSubdomains; explicit false fixes lint without broadening cleartext trust.',
        'Actual independent reset found the old foundation oracle still expected only V1/service_writer. The real service was healthy, but the oracle correctly refused its new tables. Add exact V1/V2 inventory and explicit zero account/session counts; mutation regression and subsequent actual fresh scenario resets pass. Preserve old failures and exact old-environment cleanup.',
        'Four startup/real-Keystore cases passed in the initial five-test red instrumentation command. The exact-input oracle used a password whose trimmed form was seven scalars, so invalid_field was correct, not invalid_credentials. A longer exact whitespace/decomposed/emoji secret makes both comparison candidates valid-length and the one-test real-client/server recheck passes.',
        'Targeted check helper initially assumed AGP left its app-under-test installed; AGP had uninstalled it. Reinstall/launch the same qualified normal artifact without a data-clear fallback. The helper then read launch observation as a combined descriptor; read the actual ready.json instead. Both diagnostic tracebacks remain retained.',
        'First Appium attempt tried parent XPath inside a context-limited element subtree. Root XPath locates the actual native EditText. The next attempt used a long Ctrl-V key and only checked substring visibility; it reached a credential mismatch. Use one ordinary Ctrl-V keypress and assert exact visible pasted text before submission. The complete real account/relaunch/revocation journey passes; product password normalization was not changed.',
        'Saved-state parcel assertions succeeded, but the initial test tried another Compose input action after manually saving the lifecycle, when that artificial save had disposed its hierarchy. Clear volatile controller fields directly after save instead; the focused saved-state test passes with the actual no-password assertions unchanged.',
        'The first retention helper rewrote its own still-open stdout and corrupted only its audit-output envelope despite exit 0. That file is retained as a diagnostic, not behavioral pass evidence. Exclude retention stdout from conversion; the corrected artifact/cleanup identity audit passes. No product check was replayed for this repair.',
        'The first scoped save stopped before commit/push on raw AGP JUnit XML CRLF whitespace. Preserve that exact XML in a lossless output/outputSha256 JSON envelope at the same retained path, including original CR bytes, rather than normalize/discard it or relax hooks. Stage only the same owned work plus this diagnostic and rerun the small evidence/staged-whitespace audit before retrying the same save.'
    ],
    evidenceNotes='Retained .txt outputs and CRLF AGP XML use lossless JSON string envelopes with outputSha256 to preserve original CR/ANSI/trailing whitespace while allowing staged-whitespace validation (the .xml filename is retained for provenance; decode its envelope before parsing). The first retention.txt self-write diagnostic is intentionally not a valid envelope. Runner JUnit XML/screenshots/hierarchies are retained under this run prefix; all accounts/passwords pictured are synthetic known fixtures. No APK, raw private DB, key, bearer token, unredacted network/Appium payload log or production data is committed. The initial eight-test host XML was overwritten by its targeted recheck; original successful task output remains, and the final one-test XML is retained. Green assertions in red commands are reused explicitly, never relabeled as whole-command green. NativeStartupTest updated entry assertions compile; the complete NativeAccountsTest exercises that startup UI instead of repeating the old standalone startup run.',
    retainedObligations=[
        'I02 selected parent must reconcile I02a/I02b exact evidence/current inputs and any missing seam; this unit does not close it or independently review B1.',
        'I03: actual Menu/Games navigation, context/resume and complete immediate Logout/private-data/callback/account isolation. Current authenticated boundary shows only the real safe username. I04: real Forgot password/reset/deep-link/mail/revocation and second-device B1 journeys; reset is explicitly unavailable in this build, not simulated.',
        'I09/I13/I14 and later: social/game object authorization, full account-owned synchronization and same-account request/recovery behavior. The listed requirement/scenario/UI IDs have only their account-entry assertions exercised here.',
        'I32/I34/I35/I36/B8: broad usability/accessibility, complete product/full-suite/deferred infrastructure/device/fault/reproducibility/upgrade/arm64 qualification and full interface-to-mock inventory/comparison/fixes. Current platform warnings are not waived final checks; no final F/UI item or physical performance is claimed passed.',
        'All external release gates remain unchanged/unverified. Configure a real HTTPS host only through authorized release/operator work; no production host or credentials were guessed.'
    ])
(ROOT / (BASE + '.json')).write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n')
print('Generated report with', len(paths), 'exact owned paths and', len(checks), 'retained check entries')
