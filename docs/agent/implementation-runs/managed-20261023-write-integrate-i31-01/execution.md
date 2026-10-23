# Connected I31 + three-peer candidate — UNCOMPILED / UNEXECUTED / UNVERIFIED

Use the final saved commit on `managed/managed-20261023-write-integrate-i31-01`,
containing base `6314593f246c6d7489f026e70cbb595c88c54fb6`, I31
`06a7d34d74147c556a6852e9a7cd1857ec59ab87` and peer adapter
`d99cb6ca605de18da680d385593b382d237bdd3e`. Record the actual tested 40-hex pin.
No build, behavioral test, package, device lifecycle or capability review ran here.

## Queue only missing affected execution

- Compile the two added Kotlin test source sets together:
  `./scripts/gradle.sh --no-daemon --max-workers=2 :e2e:compileTestKotlin :androidApp:compileDebugAndroidTestKotlin`.
  Reuse supervisor-confirmed input-valid evidence from the immutable-631 executor for
  unchanged runtime, helper, I33 and version-2 inputs. Its Android-test compile does
  not cover the new I31 companion. Do not repeat its unchanged green broad scopes.
- Adapter safety, one pass:
  `PYTHONPATH=.:tests python3 -m unittest test_reset_runner test_e2e_devices`.
  No Python modules were imported/executed by this writer. The original authored
  safety assertions and outputs are preserved, not converted into a pass.
- Build matching normal non-GMS debug APK and companion once with the actual isolated
  `-PatwApiOrigin=http://127.0.0.1:PORT` and
  `-PatwCatalogTestOrigin=http://127.0.0.1:PORT` configuration; retain actual origins,
  app/test APK hashes, source, signer, backend/catalog image and device identities.
  VersionCode 2 / 0.1.1 and the current production runtime are unchanged.
- Inside the complete resource-wrapped lifecycle only:
  `./scripts/gradle.sh --no-daemon --max-workers=2 --console=plain :e2e:deviceTest --tests org.asyncttwiz.e2e.NativeIntegratedR1Test.uiCreatedForkChatResultRematchAndThreeInstanceRecovery`.
  It invokes `org.asyncttwiz.android.IntegratedLifecycleTest#fullRecoveredTreeRetainsExactUiCreatedOffshootAndCurrentPath`
  and `#realAuthViewModelLogoutClearsPrivateReviewAndDraftAcrossRotationAndClose`
  on the same-account third installation at their actual retained checkpoints.

## Actual adapter and environment seam

Use `e2e.reset_runner.pair_owned(first, second, third)` once under `d.lock()` after
the same lifecycle owner provisions all three recorded instances and before wait,
attach/install/reset/use. `pair(first, second)` and its CLI remain two-peer APIs;
repeated pairs still cannot qualify three. Complete exact identity/configuration,
distinct owner/directory and nonoverlapping port validation plus every live Windows
snapshot precede inventory publication. Foreign discovery/AVD/process/session/route
refusals remain in force. Read the immutable adapter report's `callableHandoff` for
the existing provision-stage readiness guard and partial-I/O publication boundary.

The executor still owns extending its existing whole lifecycle composition to
three profiles; this merge is not a qualified three-profile driver. Never suppress
a generic Refusal, hand-adopt another owner's guest, or manually forge inventories.
Wrap the entire provision → pair_owned → wait → attach/install → one independent
reset → journey/companions/output collection → finally cleanup using
`scripts/implementation-resources.py run --owner EXECUTOR_RUN --resource windows-devices --resource windows-routing --resource shared-e2e-backend -- python3 EXISTING_OWNED_DRIVER.py`.
Omit the backend lease only for demonstrated isolation. Existing stuck-device
recovery remains exclusively with its separate owner; no recovery was touched here.

`ATW_DEVICE_RUN` selects first run directory, `peer-run.txt` selects opponent,
`third-run.txt` selects the fresh same-account normal installation. Each has
`runner.properties` with distinct owner/serial/adbPort/systemPort/appium and explicit
linuxAdb; first includes the executor-bound existing start `control.py`. All profiles
share one isolated synthetic backend/catalog and APK identity. Install companion on
third. Preserve the original start-adapter operations and observe JSON shape from
the unchanged I31 `execution.md`; SQL is supplementary observation, never UI setup
or the UI success oracle. No production credentials or FCM requirement.

## Static reconciliation and remaining scope

Current Library tap emits ShowContents; it cannot produce the authored exact
root/Life/fork-Draw/Turn tree alone. The journey instead uses the existing normal
Table actions Draw one, verifies the actual turn-player target in the UI and checks
its Hand at exact reviewed boundaries. The second actor still authors the real
fork; no starter policy, fixture, additional mutation or production rewrite is added.
Wait for normal review-context panel dismissal and companion playback readiness.
MainActivity/AuthViewModel, retainedTree/history, actual Logout/clear/recreate/close,
labels, result gate, chat, drawer, deletion and rematch APIs were source-checked only.

Original I31 archives remain byte-identical. Its `requiredScopeComplete=false`
remains: OS notification polling/tap/authorized-boundary route, ordinary captured
lifecycle overlap/old-incarnation variants, concurrent distinct Undo and full
uncertain pre/post-admission logout/exposure variants are NOT delivered by Home/
foreground or these privacy checkpoints. I31 supervisor retains those scopes.
No wait for an evolving notification writer. Independent verification/reviews,
I34–I36, B8 full interface/mock gate and final release/resource gates remain required.

Phases remain setup/history/recovery/result/rematch/privacy/delete. Start with
`ATW_NATIVE_PHASE=setup` (or unset), `ATW_START_STAGE` unset/setup. Continuations
require the same retained profiles/backend/data and `i31-identities.json`, now also
including drawPlayer; old checkpoints lacking it cannot resume this source's history
or recovery. Never replay a partial phase or call a green tail a full journey.
Retain original JUnit, instrumentation outputs, identities/phase JSON, UI XML/PNG,
control outputs and whole-owner cleanup. First real compile failure precedes every
unexecuted downstream scope; supervisor dispatches fresh source fixers by exact
failure/command/output/owner/path and narrow rerun, then independent verification.
