# I31 authored journey — UNCOMPILED / UNEXECUTED / UNVERIFIED

Source pin: `0436d6ec399ad821c19e24170f2e6ef01d637015`. No merges or product edits.
The supervisor integrates the parallel compiler/foreground/cancellation/location fixes;
this writer did not wait for them, run builds, inspect an app, or acquire devices.

## Queued selectors and inputs

- Native: `org.asyncttwiz.e2e.NativeIntegratedR1Test.uiCreatedForkChatResultRematchAndThreeInstanceRecovery`.
- Companion, invoked by that native method on instance three at separate retained checkpoints:
  `org.asyncttwiz.android.IntegratedLifecycleTest#fullRecoveredTreeRetainsExactUiCreatedOffshootAndCurrentPath`
  and `#realAuthViewModelLogoutClearsPrivateReviewAndDraftAcrossRotationAndClose`.
- Compile the affected Kotlin test source sets together, then package the normal debug APK and
  its instrumentation companion against the executor's actual backend/catalog origins. Use
  `--no-daemon --max-workers=2` on heavy Gradle commands. Do not rerun unchanged broad green suites.
- The native invocation **inside** the qualified foreground lifecycle is:
  `./scripts/gradle.sh --no-daemon --max-workers=2 --console=plain :e2e:deviceTest --tests org.asyncttwiz.e2e.NativeIntegratedR1Test.uiCreatedForkChatResultRematchAndThreeInstanceRecovery`.
  This is a queued command, not an executed check or a standalone device runner.
- Environment: `ATW_DEVICE_RUN` points to the first run directory; `ATW_NATIVE_PHASE=setup`
  (or unset), `ATW_START_STAGE` unset/`setup`. Existing `peer-run.txt` points to opponent;
  additional `third-run.txt` points to the fresh normal same-account installation's run directory.
  All three have existing `runner.properties`: distinct `owner`, `serial`, `adbPort`,
  `systemPort`, `appium`, and explicit `linuxAdb`. First also has `control`.
- All three profiles must use the same isolated synthetic backend/catalog and APK identity.
  Install the instrumentation companion on instance three before the journey. Retain APK,
  instrumentation, server/adapters image hashes, configured origins, profile/session/route
  identities and source pins. No production credentials or FCM configuration required.
- `control` must be an executor-owned binding of the existing game-start adapter
  (`20261023T010830Z-bc92c3f3/control.py`), not its hard-coded old environment. Its original
  setup operations (`first-ready`, `withdrawn`, `no-prestart-roll`, `other-ready-preserved`,
  `pause`, `commit-without-ack`, `resume`, `started`, `final`) retain their existing semantics;
  later phases use only existing `observe`, `pause`, `resume`. The observe shape is unchanged:
  games (id/name/createdAt/round/lifecycle/play), players, preparations (actor/ready/deck),
  plays, requests (id/command/result). SQL is supplementary read-only evidence, never setup or
  the UI success oracle. Bind committed-loss app suspension to the opponent, as in that adapter.

## Qualification gap — do not bypass

`e2e/reset_runner.py:21-27` qualifies exactly two peers and replaces `observed-peers.json`.
Calling `pair` repeatedly does not qualify three guests: it loses an earlier peer, which
`e2e/devices.py:258-283` correctly refuses as foreign discovery. Existing game-start and I12
drivers also construct exactly two profiles and select other tests. There is **no qualified
three-profile foreground lifecycle command at this pin**. This test deliberately requires its
third input before UI setup; a two-device fallback would not exercise the assigned outcome.

Next adapter owner needs a precise additional lease for `e2e/reset_runner.py` and
`tests/test_reset_runner.py`: qualify a complete owned peer set while preserving foreign-owner,
AVD/process, dedicated-port/session/forward checks. Extend the existing complete provision →
pair → wait → attach/install → one independent reset → native/companion → cleanup composition
in that next run's minimal driver namespace; reuse `devices`, `device_handoff`, `device_session`,
`device_routes`, `device_artifacts`, `environment` and existing control logic. Do not edit old
evidence, hand off live resources across owners, manually forge peer records, or add a runner
framework. Wrap **that entire foreground lifecycle including finally cleanup** with
`scripts/implementation-resources.py run --owner <executor-run> --resource windows-devices
--resource windows-routing --resource shared-e2e-backend -- <qualified lifecycle command>`
(omit backend lease only for demonstrated isolation).

## Authored assertions versus assigned coverage

Authored: reuse UI-only start; exact root/Life/fork-Draw/current-Turn identities; chat during
personal pause without mark-all-read; explicit arm/cancel/real continuation; same-account
phone-action/tablet-Undo/phone-Redo; relogin/restart/full recovered small tree (including the
inactive offshoot); all its boundaries reviewed offline without activation; normal Home/app
foreground return preserves personal pause; paused opponent steps through final board before
the singleton suggestion/dialog, actually accepts; same game/date/prepared decks, both unready;
explicit Random selection (starter retention remains **UNAPPROVED**), two fresh Ready requests,
new play/root with unchanged recorded start on restart; actual AuthViewModel UI Logout,
recreation/rotation/close, B draft isolation and A relogin; active shared Delete/Keep distinct
from Cancel, exact accepted original request scopes/IDs and removal from all three instances.

**Executed assertions: none.** Static source/ownership/whitespace checks are not compilation or
behavioral evidence. This is initial bounded I31 code, not completion of all assigned T-IDs.
The native artifact deliberately records `requiredScopeComplete=false` even after a green run.

Still owned by I31 supervisor/executor: qualify/run the three-profile lifecycle; actual OS
notification tap/authorized boundary route (existing adapters do not schedule/observe a qualified
background polling journey); ordinary captured lifecycle overlap/old-incarnation variants and
the separately required concurrent distinct Undo variant. This source covers Home/foreground,
not fake push/notification delivery or a polling deadline. Full uncertain pre/post-admission
logout/exposure variants are not this ordinary UI privacy checkpoint. Remaining e2e-strategy
families and I34–I36/fresh independent review/release obligations remain unpassed.

Checkpoints: `setup, history, recovery, result, rematch, privacy, delete` use existing NativePhases.
A continuation requires the same three installations/backend/data and `i31-identities.json`
in the same retained run directory. Resume only at an actually preserved phase boundary;
do not replay a partly completed phase or aggregate a green tail into a full journey.
Retain original JUnit, both companion command outputs, every `i31-*.txt`, phase/identity/summary
JSON, XML/PNG captures, failed output and whole-owner cleanup under the executor's namespace.
If compilation fails, report it first and leave all three selectors UNEXECUTED; split actual
failures into independent source-owned failureGroups before fresh fixing/verification.
