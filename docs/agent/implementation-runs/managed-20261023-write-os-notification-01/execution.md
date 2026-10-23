# Existing I31 OS-notification checkpoint — UNCOMPILED / UNEXECUTED / UNVERIFIED

Source base/dependency: `06a7d34d74147c556a6852e9a7cd1857ec59ab87`.
Only `NativeIntegratedR1Test.kt` changed; no product, adapter, companion or state edits.
No merge/repair of another branch, build, test, APK, image or device operation occurred.

## Authored connection

The existing `history` phase still creates exactly its original Life and fork-Draw
through the two normal UIs. First phone now goes Home **before** the peer's Draw.
The actual `AndroidActivityDelivery` registration, periodic `ActivityWorker`, authorized
summary fetch, durable window and `ActivityNotifications` must produce one real
`game_activity` OS record with this account binding/game tag while first remains
backgrounded. There is no scheduling helper, forged push/intent/notification, worker
invocation, opponent API or business-state injection. Natural polling is observed
for up to 35 minutes, not asserted to have a delivery SLA. Existing sessions receive
read-only package heartbeats during the wait.

The normal AndroidDriver `openNotifications()` opens SystemUI. A unique clickable
row with Android title `Game activity` and app name `Async Tabletop Wizards` is tapped,
after matching the unique OS package/channel/binding/game record and persisted group
identity to the existing read-only observer's UI-created Draw group. MainActivity →
AuthViewModel → authorized `/activity/target` → TableScreen must select **before**
that group: root, life 20, zero drawn cards, paused. The peer remains current at Draw
with one card; Return to current reaches that exact Draw. Existing later boundaries,
groups and ordered result assertions are unchanged. The separate recovery Home/return
checkpoint remains; it is not relabelled as notification delivery.

Also authored: Life consumed by a real foreground refresh while peer reviews root,
with no posted notice; one coalesced first-phone OS notice; no notice after tap/display
and another observed ActivityWorker success. This is not exactly-once alert proof,
repeated-background delivery qualification or a permission/FCM matrix.

## Exact invocation/arguments for the execution queue

- Existing selector, no new test: `org.asyncttwiz.e2e.NativeIntegratedR1Test.uiCreatedForkChatResultRematchAndThreeInstanceRecovery`.
- Inside the existing qualified **whole** three-profile lifecycle:
  `./scripts/gradle.sh --no-daemon --max-workers=2 --console=plain :e2e:deviceTest --tests org.asyncttwiz.e2e.NativeIntegratedR1Test.uiCreatedForkChatResultRematchAndThreeInstanceRecovery`.
- Full journey: `ATW_NATIVE_PHASE=setup`, `ATW_START_STAGE=setup` or unset,
  `ATW_DEVICE_RUN` plus existing `peer-run.txt`, `third-run.txt`, `runner.properties`.
  No new runner property, Appium capability, insecure-server flag or driver argument.
- All observation argv use each existing `linuxAdb -H 127.0.0.1 -P <adbPort> -s <serial>`:
  `shell pm list packages`; `exec-out run-as org.asyncttwiz.android cat shared_prefs/activity-delivery.xml`;
  `shell dumpsys notification --noredact`; `shell logcat -d -v epoch -s WM-WorkerWrapper:I *:S`.
  Preferences are read-only; credential fingerprints/push tokens are redacted from
  retained output. No protected session/password file is read. Commands have 30-second
  bounds, joined children and retained argv/exit/output, including failures.

## Still required, owned by supervisor's fresh execution/qualification worker

Compile the affected e2e source set with the combined candidate once; run this selector
with the separately qualified three-profile adapter, real normal non-FCM APK (all four
FCM properties absent), current backend/catalog origins and existing companion.
Use fresh supported AOSP/non-Google profiles, initially API 29 as already qualified;
both players must lack Google Play services, be registered and retain allowed default
Game activity channel controls. Do **not** grant arbitrary permissions, enable a denied
channel or modify worker/runtime timing to obtain a pass. Later-API permission handling,
denial variants and optional real FCM remain with their original I30/I34–I36 owners.
The lifecycle's host timeout must cover the 35-minute observation bound **plus** remaining
phases/cleanup; `e2e/device_runner.py`'s unrelated 1,200-second bound is insufficient.

Wrap the **entire** provision/install/journey/finally-cleanup with existing
`implementation-resources.py run --owner <executor-run> --resource windows-devices
--resource windows-routing --resource shared-e2e-backend -- <qualified lifecycle>`
(omit backend lease only for proven isolation). No old-device adoption or manual
peer-record workaround. Original three-profile qualification is independent work.

Installed qualified UiAutomator2 8.7.0 source supports POST
`/appium/device/open_notifications`; see `adapter-source.json`. Actual guest dump
indentation/channel fields, SystemUI row ancestry/visibility/click, run-as reads,
WorkManager INFO completion format, registration/poll scheduling/network availability,
foreground suppression and authorized historical/current routing remain **unexecuted**.
Keep XML/PNG shade and target captures, `i31-notification-*.{json,txt}` from both phones,
`i31-os-notification.json`, identities/phases/JUnit and original failed diagnostics under
the executor's namespace. A timeout or missing primitive is a qualification blocker,
never permission to substitute Home/activateApp or fake notification delivery.
Partial phase continuations do not certify the whole journey. Independent verification,
capability reviews and all final gates remain required; `requiredScopeComplete=false`.
