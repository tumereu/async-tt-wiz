# Native account entry, shell and reset — I02–I04

The normal `MainActivity` now opens A01/A02/A02b with actual account HTTP v1.
Registration returns to Sign in with the email populated and password cleared;
login opens the authenticated Menu/Games shell using the server's safe identity.
The normal app supplies N01/N02, personal destination restoration and local Logout,
plus A03/A04 reset requests and configured HTTPS email links. There is no guest or fake user.

## Configure and build

Use an explicit origin, without a path, user info, query or fragment:

```sh
./scripts/gradle.sh --no-daemon --console=plain \
  -PatwApiOrigin=https://your-account-host.example \
  -PatwResetOrigin=https://your-reset-host.example \
  :androidApp:assembleDebug :androidApp:lintDebug :androidApp:testDebugUnitTest
```

No production host is guessed or embedded. An unconfigured build presents a safe
configuration error on submit, not a simulated login. HTTPS uses platform trust;
redirect following, cookie login, network logging and disk HTTP caching are not
enabled. Authorization is attached only to the fixed account read/revoke routes,
never to URLs, artwork or a global HTTP client header. Responses are bounded to
16 KiB and connect/socket/whole-request timeouts are bounded.

`atwResetOrigin` must match the server's `RESET_BASE_URL` (an HTTPS origin, optionally
with an explicit port). It is independent of the API origin: a link cannot select
an API endpoint. Unconfigured builds reject links safely. Public verified app links
also require the operator's domain/assetlinks association for the actual package
and signing certificate; synthetic app-targeted intents do not qualify that gate.

For the isolated synthetic environment only, a **debug** build may use
`-PatwApiOrigin=http://127.0.0.1:<owned-backend-port>` with the existing public-only
ADB reverse mapping. Its debug-only network policy permits cleartext to exactly
that IPv4 loopback host, not arbitrary origins. Release builds omit that policy,
forbid cleartext and refuse to embed a non-HTTPS endpoint. No custom CA/trust bypass
is installed. See [device ownership/setup](../e2e/devices-README.md) and the
[server API/error contract](../server/README.md#account-http-v1--i02a).

## Credential and lifecycle boundary

- `AuthController` keeps fields only in volatile memory owned by `AuthViewModel`.
  Rotation retains that owner; finish/process death drops it. Neither passwords
  nor unfinished fields enter `SavedStateHandle`, `rememberSaveable`, SQLite or
  preferences. Native password paste/visibility is supported; the exact scalar
  string, including whitespace, newlines and decomposed Unicode, goes to JSON.
  Malformed scalars and the agreed password bounds fail before encoding.
- The normal local-storage open/error boundary still gates account entry.
  Network, hashing and credential-file work do not run on the UI thread. Busy
  submission is single-dispatch; errors retain editable input for correction.
  Finishing the owner fences queued callbacks and clears volatile fields.
- Only the opaque session token is persisted, as AES-256-GCM ciphertext in
  `no_backup/auth/session.bin`. The nonexportable AndroidKeyStore key has no
  biometric/age gate; every save gets a platform-generated random 96-bit nonce.
  Version and origin-bound authenticated data prevent accepting damaged or
  wrong-origin ciphertext. Atomic replacement never writes plaintext credentials.
- Startup decrypts off-main and checks `GET /account`. A network failure keeps the
  saved token and offers Retry without unauthorized entry. Missing/replaced key,
  corrupt ciphertext or explicit server revocation requires login. No password
  fallback or time expiry is introduced. A protection/save failure cannot enter
  authenticated UI; the new server token is revoked best effort.
- Existing packaged legacy/cloud/device-transfer backup exclusions cover all
  private domains, in addition to the no-backup path. Platform erasure/signing and
  external release gates are not claimed qualified here.

## Authenticated navigation and local Logout — I03

- N01 has the ATW logo and Games / Manage decks / Contacts with matching icons.
  Menu and Games are clickable navigation destinations, not expansion headers.
  Portrait has a dismissible drawer (Close, outside tap, native Back); landscape
  non-gameplay has persistent navigation. Logout is a fixed footer outside the
  scrolling list. Native Back dismisses the drawer before traversing local screen
  history; there are no app Back/home arrows or extra brand/title bars.
- `AuthViewModel` retains destination and local Back history through recreation.
  The last destination is saved best effort in the private SQLite profile and
  restored only after actual token authorization. Same-account reauthentication
  retains it. A different account atomically clears the old private replica and
  personal state before the shell can open. Navigation does not submit shared
  requests or pretend to synchronize unfinished input.
- Logout replaces all visible/in-memory account state and advances its callback
  generation immediately. Queued old-account destination writes and UI results
  are fenced. Off-main local cleanup deletes protected credentials, all accepted
  replica records/cursors and personal state, while retaining public catalog/art
  cache. A durable local-only cleanup marker finishes an interrupted deletion
  before a saved session can be restored. Cleanup failure locks login and offers
  Retry; this is not a database wipe/reinstall recovery or a physical-erasure claim.
- Ordinary server revocation runs on a separate bounded-time network worker, best
  effort, with no disk revocation queue and no dependency on successful delivery.
  A failed remote callback cannot undo or delay local Logout. Server account data
  and other installations' sessions are not deleted. Future feature workers must
  use the same account-generation/profile boundaries, not store outgoing journals.
- Fullscreen is restored on focus/resume. API29 additionally needs the legacy
  fullscreen window flag: observed rotation left the status bar visible despite
  decor fullscreen bits. Actual device checks cover both orientations, native
  drawer Back, keyboard opening, scrolled account controls and keyboard return.

The Games, Manage decks and Contacts routes currently state that their capabilities
are not yet available; they do **not** present fabricated empty server collections.
I05–I12 add the actual data and synchronized game rows; gameplay drawer behavior,
game/request context, recovery and offline review remain with I13/I14/I26/I31.
The current shell does not claim those later journeys are implemented.

## Password reset and email links — I04b

- Forgot password opens A03; success is deliberately neutral about account existence.
  The email stays editable after errors. A04 has one exact New password field,
  native paste/visibility, explicit Change password, Sign in and native Back.
  Both compact forms stay centered/bounded and scroll with short/keyboard viewports.
- `MainActivity` is the normal single-task browsable entry. Cold and warm VIEW
  intents accept only configured HTTPS origin/effective port, exact `/reset-password`,
  a single canonical 32-byte base64url `token` query, and no credentials/fragment.
  Raw intents are replaced immediately. No link can change the API host, carry
  authorization, auto-submit a password, auto-login or revoke existing sessions.
- The independent volatile `ResetController` checks the token by an ordinary
  body-only read API before showing an actionable password form. Invalid, expired
  and used links have only an error/Request another link route. Network failure
  during checking offers Retry; confirmation failures retain input for correction.
  Server confirmation rechecks validity atomically, not a client check's promise.
- Duplicate delivery preserves the active form/input; replacement, dismissal and
  finishing fence old reset callbacks and clear its token/password. Neither goes
  into saved state, personal SQLite, preferences or logs. Rotation retains only
  the ViewModel. Signed-in identity/profile remain separate while requesting or
  opening a link. Successful confirmation clears this installation's protected
  credential, fences older login callbacks and returns to Sign in with a notice;
  server reset revokes other sessions for new requests. Already-authorized work
  may finish (D-289). Same-account profile/context is not deleted by reset.
  Any earlier interrupted local Logout must still finish before login can reopen;
  a reset link is not a bypass of the existing private-cleanup gate.

Build the synthetic APK with the current owned backend origin and
`-PatwResetOrigin=https://reset.synthetic.invalid`, prepare two owned devices against
that same environment, then run:

```sh
python3 scripts/run-reset-devices.py pair --directory /tmp/opencode/atw-device-<first-32-hex> --peer /tmp/opencode/atw-device-<second-32-hex>
python3 scripts/run-reset-devices.py run --directory /tmp/opencode/atw-device-<first-32-hex> --peer /tmp/opencode/atw-device-<second-32-hex>
```

Pairing only allows read-only discovery of those exact live owned emulator identities
when ADB auto-discovers adjacent consoles; it does not adopt peer mappings/actions,
an unknown device, a replaced owner or another controller. The runner resets the
shared synthetic environment **once** and both app stores only at scenario start.
Kotlin/JUnit/Appium uses native player controls on both normal APKs, observes real
SMTP delivery, opens the real browsable intent and tests new-request rejection of
both old UI-created credentials. The test-only `SessionAuthorizationTest` uses a
private test-only synthetic snapshot only to observe authorization; it
cannot inject login/reset/game state. `ResetIntentTest` supplies actual native
exact Unicode/whitespace, duplicate warm callback and saved-instance-state checks;
Appium checks cold/warm manifest routing and rotation with retained password input.
No production token/mail body is committed, and public HTTPS/SMTP/verified-domain
hosting remains an external gate. Expiry uses I04a's controlled-clock assertions,
not a runtime test hook or a half-hour sleep. Final full UI comparison remains B8.

## Focused checks

Host tests: `AccountApiTest`, `AuthControllerTest`. Actual API29 instrumentation:
`ProtectedSessionStoreTest` (real key/nonce/reopen/tamper/key-loss/origin tests),
`AccountEntryTest` (native exact input/rotation plus real Ktor/server credentials),
`AuthSavedStateTest` (actual saved-state parcel), and the existing `StartupTest`.
All are under the unchanged approved review-test paths.
I03 adds `AccountLocalDataTest` (actual SQLite/profile/Keystore deletion and interrupted
cleanup) and `ShellPresentationTest` (normal launcher/real synthetic account,
fullscreen/rotation/keyboard/native drawer Back). File-backed host storage tests
also exercise same-account retention, cross-account deletion and public-cache isolation.

After ordinary image provision, configured APK build and `prepare-app`, the owned
runner supports these **independent scenarios**:

```sh
python3 scripts/run-device-tests.py --directory /tmp/opencode/atw-device-<32-hex> auth
# AGP connected tests uninstall the app-under-test. Re-run the documented
# prepare-app command with the same normal APK before a following native scenario.
python3 scripts/run-device-tests.py --directory /tmp/opencode/atw-device-<32-hex> accounts
python3 scripts/run-device-tests.py --directory /tmp/opencode/atw-device-<32-hex> profile
# As with auth, connected instrumentation uninstalls its target; prepare-app again.
python3 scripts/run-device-tests.py --directory /tmp/opencode/atw-device-<32-hex> shell
```

`auth` selects five existing/new startup/Keystore/input tests. The separately
selectable `AuthSavedStateTest` has its own retained focused execution. `accounts`
is Kotlin/JUnit/Appium on the normal APK: invalid/corrected registration, private
email/password login, visibility/native clipboard paste, both orientations,
scrolled full-size actions, native Back, force-stop/protected-token relaunch and
revoked-token login handling. Every independent scenario explicitly resets the
owned Compose stores and app data; retained restart checkpoints never do so.
`profile` runs two isolated real Android profile tests. `shell` runs a normal-APK
Kotlin/JUnit/Appium journey: UI-created A/B accounts, N01/N02, Back/Close, both
orientations, retained destination/restart, ordinary revocation/reauthentication,
Logout, signed-out restart and a clean second-account menu. Its screenshots predate
the final API29 status-bar correction; the separately retained final presentation
instrumentation checks that correction on actual device insets, not a presumed
visual pass. The complete final mock comparison remains I36/B8.

Its opt-in `RevokeSavedSessionTest` checkpoint reads only this synthetic app's
protected token and calls ordinary `DELETE /session`; no test-action product
endpoint or database injection. `requireSavedSession=true` requires an actual
token from the preceding UI login. A fresh standalone invocation instead checks
ordinary idempotent unknown-token revocation, not a fictional successful login.

The account schema joins the existing reset oracle: V1/V2 migrations and exactly
empty `account`/`account_session` tables are required before scenario readiness.
This repairs the stale V1-only foundation oracle, not a weakened reset barrier.

## Evidence and remaining scope

[I02b report](../docs/agent/implementation-runs/20261022T054937Z-41060cc6.json)
retains actual passing and diagnostic command output, raw JUnit XML, synthetic
native screenshots/hierarchies, pinned dependency/provenance and cleanup evidence.
Initial failed checks are not represented as green runs. Tests use only synthetic
accounts, known test passwords and the real isolated backend; no production data.

Sources/partial coverage: P-043/P-047, T-396/T-419/T-420/T-422/T-572/T-573/T-621/
T-657/T-696, F04/F05/F06/F11/F58 and D-249/D-268/D-289/D-292/D-297. The UI packet's
current A01/A02/A02b and cumulative feedback remain the layout baseline, not a
claim of final UI acceptance. I02's parent has reconciled its child evidence; B1's
independent review still follows I01–I04 together. I03 supplies local shell/profile
Logout isolation below. Full game/outcome recovery, reset/second device,
social/game privacy and full scenarios remain with I04/I09/I13/I14/I26/I31.
Broad device/upgrade/architecture/full-suite and complete
interface-to-mock comparison remain I34/I35/I36/B8. No release gate is passed here.

[I03's run](../docs/agent/implementation-runs/20261022T070751Z-df665f98.json)
retains the shell/profile/affected host checks, original red drawer/fullscreen/
test-compilation/rate-limit diagnostics, passing focused corrections, exact inputs
and owned cleanup. The shell portions of P-043/P-047/P-065, T-397/T-480/T-543/
T-621/T-666/T-676/T-677/T-682 and F03/F07–F09/F12/F14/F25/F26 are exercised here,
not the full cross-feature scenarios. I04 still owns reset/second-device B1 entry;
B1's independent capability review follows I01–I04 together.

[I04b's run](../docs/agent/implementation-runs/20261022T084055Z-9e1f1522.json)
retains the real two-device reset core (including both old-session refusals, exact
input, both-orientation A03/A04, used-link safety and valid-token resume) in the
original diagnostic full-run output, followed by a passing targeted retained
Logout/restart/different-account tail. The original red result is **not** relabeled
a wholly green journey. Offscreen landscape label waits were corrected; accepted
registration was not repeated. Account-switch login completed in portrait. The
remaining landscape password-field targeting/viewport assertion goes to I34/I35
(T-572/T-621), not a fabricated pass or a proven application defect; complete UI
comparison remains I36/B8. The native checkpoint preserves ActivityScenario's
non-secret launcher identity for teardown only after actual saved-state secrecy
assertions. Test-observer UID and helper working-directory failures are retained
as diagnostics. The final neutral-notice/pending-Logout guards have targeted host
checks and a final incremental APK build; the device APK predates those two isolated
guards, so its captures are not a new run on the final APK. I04's child audit and
the first independent B1 review remain separate.
