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

## Selected routing and technical delivery — D-282/D-284

D-282 confirms two Android channels, **Invitations** and **Game activity**, activity
coalesced per game, delivery to every logged-in registered installation, and local
suppression of that game's activity notification while its game screen is actively
displayed. Review/playback still counts as displaying the game; backgrounded screens
do not. Suppression is delivery bookkeeping, not proof the player watched a group.
Invitation notifications are not suppressed merely because another game is open.

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
  Select 15-minute periodic work with network-required constraint, flex allowed by
  Android, no exact-alarm permission or persistent foreground service. This is a
  requested cadence, never a delivery guarantee. Use exponential retry 30 seconds
  to 6 hours for transient failures; respect server Retry-After. FCM-unregistered/
  unavailable installations use this path; foreground always refreshes directly.
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

Use account-scoped monotonically ordered activity IDs. `/activity?after=cursor`
returns invitation attempts and changed game summaries, latest activity/group IDs,
next cursor and actual retention floor, up to 100 entries/page. Retain hints 30 days;
below-floor clients receive explicit summary-reset with current outstanding invites/
latest game activity and initialize dedup state without replaying every old alert.
This is not a history/read-receipt API. Never poll a whole game tree to find alerts.

For each opponent-author/group, coalesce using 2-second quiet window, maximum
10 seconds from first pending activity. Group extension updates the same per-game
notification ID (silent update if already alerted for that group); next group may
alert again, still under one per-game notification. Game sync never waits for this
window. Invitation ID is the attempt identity. Stable `(account,game,group/activity)`
deduplication prevents push/poll overlap from repeatedly alerting unchanged work.
Write delivery watermark/notification replacement identity before posting; a crash
can lose an alert rather than repeatedly post it. No exactly-once OS guarantee.

FCM uses **data-only** hints with opaque account/session registration generation,
game/attempt/activity IDs, no names/card/chat content or auth token. The app validates
current account/session, fetches authorized summary and applies suppression/channel
checks before posting. Never allow Android auto-displayed payloads to bypass Logout
or foreground suppression. FCM TTL 24 h, per-game collapse where available; service
transient retries 1/5/15/60 min bounded by TTL, permanent invalid registration disables
that registration. Polling/foreground covers missing hints. No push-delivery SLA.

Runtime notification permission on Android versions requiring it is requested in
context when enabling invitation/activity alerts, not a login gate. Default channel
importance is normal/default, no custom sound/override of user choice. No R1 in-app
global/per-game mute. Provide a normal non-FCM build/configuration path with no
Firebase initialization/registration, plus an optional configured FCM adapter; no
required startup Google-service request or new provider-selection UI.

Registrations are session+installation bound. Logout revokes/removes registration,
cancels posted account notifications/jobs and increments local account generation;
fence late worker/socket callbacks before writing/posting. Reset-time revocation
invalidates all old registrations; foreground/session check prevents stale displays.
Muted/foreground-suppressed activity advances local dedup progress, not review
progress. Other installations apply their own local suppression independently.
No app, scheduler or executable tests are implemented or authorized here.

References checked 2026-10-20:
[Android periodic work](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work),
[AlarmManager scheduling and restrictions](https://developer.android.com/develop/background-work/services/alarms),
[FCM Android setup/distribution](https://firebase.google.com/docs/cloud-messaging/android/client).
