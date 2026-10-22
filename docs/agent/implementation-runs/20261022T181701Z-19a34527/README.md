# I09b2 — native invitation decisions and recovery

Delivered P01 recipient Accept/Decline and exact retained-lobby in-slot Reinvite
through I09b1's real `/v1/socket`, not an existing-game HTTP write. Acceptance
opens the same named/date-stamped lobby and is not Ready/start.

`GameConnection` uses the already-qualified OkHttp 4.12 runtime with bearer headers,
no redirects, normal TLS outside isolated debug loopback and bounded text decoding.
It is now an explicit Android compile dependency; debug/test and release compile
locks are qualified. `GameSession` is main-thread process memory: negotiate, **open
a fresh stream**, subscribe, then dispatch captured UUID scopes. Same-process
reconnect uses fenced resume and original status recovery; only `notAdmitted`
originals resend under the new fence. Pending receipts remain uncertain. Results
settle requests; monotonic decimal revisions alone install accepted summaries.
Ordinary independent invitation requests can be in flight together; the UI prevents
two simultaneous decisions for the same invitation. I10–I14 extend this same path.

The normal ViewModel owns it across rotation. Same-account sign-in retains pending
intent; Logout/different-account sign-in clears queues and fences callbacks. A new
process reads the server and opens fresh streams without reconstructing lost work.
The installation UUID is non-secret personal device metadata, not a credential or
outgoing journal. No offline invitation action is created while disconnected.

## Actual evidence

- `socket-open-and-integration-tests.json` and `host-junit.json`: **23 passing
  affected host cases**, including real production LobbyController/AuthController
  wiring, original reauthentication, role/scope/navigation, stale HTTP/revision/result
  convergence and queue/account fences. No skipped cases.
- `native-journey-visible-status-corrected.json`: one passing **two-normal-APK**
  API29 non-GMS journey against the real containerized backend. Both synthetic
  accounts and all contacts/game/actions are UI-created. Decline → retained inviter
  lobby → Reinvite → interrupted Accept → same-process reconnect → same not-ready
  accepted lobby in both orientations → native Back/reopen → process restart.
- The successful captures have prefixes `runner-e293dc9c81304bc7ae9db5fe77d5820d`
  (inviter) and `runner-5589c1d9a06943bd8b4fc27abaf96bf4` (recipient). The recipient's
  `uncertain-accept-portrait` capture precedes the owned socket interruption.
  `observe-*.json` preserves game/date/preparation and old/new attempt identities;
  `ordered-native-outcomes.json` proves exactly three accepted original requests
  with ordinals 1–3, not duplicate decision/reinvite effects. SQL is observation
  only; the helper pauses/resumes only the owned backend and cuts its owned relay.
- Incremental APK, instrumentation assembly, lint and module boundaries pass;
  `release-compile-lock-check.json` adds strict release compile lock/API checking,
  not release signing/distribution. `input-identities.json` pins the tested inputs
  and actual normal APK. Server source is unchanged; I09b1's server durability
  evidence is reused, not re-executed as a new matrix. The local image was rebuilt
  incrementally to run the newly delivered native journey, never deployed/published.
- The V6-only reset oracle obstructed a failed-journey retry. `e2e/environment.py`
  now asserts real V7 and **every** inbox/outbox/revision/stream store is empty.
  `reset-oracle-regression.json` rejects retained rows in each new store;
  `actual-reset.json` retains the actual owned PostgreSQL/adapter reset baseline.
- `cleanup-first.json`, `cleanup-second.json`, `cleanup-environment.json`: exact
  owned Appium/emulators/ADB mappings/processes and Compose containers/volumes/network/
  relay/secrets removed; no unrelated resources adopted or stopped.

## Red attempts and corrections

All failed commands/captures remain diagnostic. The first socket implementation
incorrectly resumed nonexistent streams; the native test exposed `streamFenced`.
Fresh `openStream`, retained-only `resumeStream` and corresponding host regression
now pass. The initial dependency command exposed missing compile locks; intentional
qualification and final strict builds pass. Existing device guards correctly
refused an unpaired peer and changed combined APK: explicit pairing and exact
unroute/reattach corrected setup, without bypasses. Tmpfs exhaustion truncated a
redundant readback; `offload.py` preserves only this run's exact APK readbacks on
the repository build filesystem with hashes, freeing space without unrelated
deletion. Landscape registration/first-scroll-container/observer-working-directory
test assumptions were corrected; the final test waits for the visible accepted
status instead of an offscreen opponent label. No product failure is called green.

## Remaining obligations / D-308 deferred observation

- **I34 correction/recheck:** after the forced transport interruption, an inviter
  may retain the old HTTP reachability error even while the newer accepted state
  and socket have recovered. See successful inviter `same-game-landscape` and
  `accepted-inviter-portrait` captures and `LobbyController.refresh`'s success copy
  retaining `state.error`. Retry clears it; accepted state/actions are not lost or
  rolled back. Keep automatic recovered-error clearing as a concrete required
  final-qualification correction/recheck, not a claimed passing assertion or a
  reason for another early detail loop. This does not waive readable recovery UI.
- I09b/I09 remain open for separately selected small child-evidence audits; no
  child review or scaffold-certification layer. B3 first independent review still
  follows all I09–I12.
- I10–I12 retain preparation/copies/Ready/start/cancel and P-072/T-669 cancellation
  order, T-457/T-458/T-460 prepared-copy preservation and two-Android prepared start.
  I13/I14/I26 retain gameplay/rebase/full-tree extensions on this processor;
  I28/I31 retain T-681 rematch metadata and combined recovery.
- I34 full suite/deferred uncommon assertions, I35 compatibility and I36/B8 complete
  interface-to-mock comparison remain unexecuted here. No P/T/F cross-task scenario
  or external gate is marked wholly passed by this child.
