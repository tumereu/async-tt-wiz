# Notifications and background work

## Confirmed boundary — D-269

FCM is optional, not a prerequisite for app use, login or game synchronization.
The app must operate on otherwise-supported Android devices without Google Play
services, and without Play Store distribution or developer Firebase setup for the
non-FCM path. The owner considers periodic polling an acceptable fallback and
suggests AlarmManager as a possible mechanism. This does not select an exact
scheduler, polling interval, build-flavor matrix or new in-app settings screen.

Preserve D-219's invitation/grouped-opponent-activity notifications, no covered card
names and no delay to immediate game publication. Both push and polling-generated
notifications respect Android permissions/channel controls (D-220). Declining
notifications does not block login/gameplay or authorize another delivery path to
bypass the user's choice. The [Android contract](../03-contracts/android-experience.md#59-notifications)
owns user-visible scope; additional alert categories remain excluded under D-241.

## Proposed delivery and scheduling design

- Keep foreground gameplay on D-263's WebSocket/revision-recovery path; do not wait
  for a scheduled worker to send ordinary actions. Do not maintain an always-on
  background socket solely for notifications.
- Use FCM as a replaceable delivery adapter where configured/available. Missing
  service configuration, unavailable Play services or failed token registration
  must not prevent the app/backend's non-FCM path from working. Push-delivery
  tokens are not authentication tokens. No FCM-required app startup check.
- Prefer D-263's selected WorkManager for persistent, network-constrained polling
  on non-FCM installations. It does not require Google Play services. Android's
  periodic-work minimum is 15 minutes, **not** a guaranteed 15-minute detection
  deadline; Doze, standby, battery restrictions and network loss can delay work.
  The actual configured cadence remains to design against battery/server cost.
- AlarmManager remains an alternative for a demonstrated scheduling need, not a
  way to escape Android's background/network limits. Repeating alarms are inexact;
  exact alarms require special access on modern Android and are not justified by
  ordinary invitation/activity polling. Recommend no exact-alarm permission or
  persistent foreground service merely to obtain faster polling. If alarms are
  used, specify reboot rescheduling and bounded worker execution explicitly.
- Poll a lightweight authorized invitation/activity summary/change cursor, not
  every game's entire history. Obtain necessary records through ordinary APIs;
  use normal revision/baseline recovery for game state. Exact endpoint/cursor,
  paging, retention fallback, retry/backoff and jitter remain technical design.
- Make a notification worker's fetch/observation distinct from a player viewing
  history or covered information: it must not create gameplay, inspection/exposure,
  review-progress or read-receipt events. Notification deduplication progress is
  local delivery bookkeeping, not a new shared per-device game-state variant.
- Coalesce/deduplicate across repeated polls and push/poll overlap using stable
  account/activity identities; restarting or changing delivery path must not
  repeatedly alert for the same unchanged activity. No exactly-once OS display
  guarantee. Fetch/push is a hint, never an instruction to replay a game command.
- Bind registrations, workers, stored progress and pending results to the actual
  account/session. Cancel obsolete jobs on Logout/account switching and fence
  already-running callbacks before storage/display, not cancellation alone.
  Respect revoked access without saved-password reauthentication (D-268).
- App foreground/reconnect refreshes current server records even if delivery was
  absent or Android prevented background execution. Push/poll failure never rolls
  back a committed action, fabricates rejection or loses request/exposure recovery.

The preceding two-channel proposal, foreground suppression, all-device routing,
FCM retry/TTL/payload details and exact aggregation windows are still proposals;
optional FCM approval does not finalize them. Optionality does not require a new
R1 mute/provider configuration UI. Exact SDK packaging/init/privacy choices remain
technical work, including how an installation avoids FCM registration entirely.
No app, scheduler or executable tests are implemented or authorized here.

References checked 2026-10-20:
[Android periodic work](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work),
[AlarmManager scheduling and restrictions](https://developer.android.com/develop/background-work/services/alarms),
[FCM Android setup/distribution](https://firebase.google.com/docs/cloud-messaging/android/client).
