# Native account entry — I02b

The normal `MainActivity` now opens A01/A02/A02b with actual account HTTP v1.
Registration returns to Sign in with the email populated and password cleared;
login hands the server's safe `AccountIdentity` to the authenticated boundary.
The Menu/Games shell, context and complete Logout isolation remain **I03**;
Forgot password currently explains that reset is not available in this build,
and real reset/deep links/mail remain **I04**. There is no guest or fake user.

## Configure and build

Use an explicit origin, without a path, user info, query or fragment:

```sh
./scripts/gradle.sh --no-daemon --console=plain \
  -PatwApiOrigin=https://your-account-host.example \
  :androidApp:assembleDebug :androidApp:lintDebug :androidApp:testDebugUnitTest
```

No production host is guessed or embedded. An unconfigured build presents a safe
configuration error on submit, not a simulated login. HTTPS uses platform trust;
redirect following, cookie login, network logging and disk HTTP caching are not
enabled. Authorization is attached only to the fixed account read/revoke routes,
never to URLs, artwork or a global HTTP client header. Responses are bounded to
16 KiB and connect/socket/whole-request timeouts are bounded.

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

## Focused checks

Host tests: `AccountApiTest`, `AuthControllerTest`. Actual API29 instrumentation:
`ProtectedSessionStoreTest` (real key/nonce/reopen/tamper/key-loss/origin tests),
`AccountEntryTest` (native exact input/rotation plus real Ktor/server credentials),
`AuthSavedStateTest` (actual saved-state parcel), and the existing `StartupTest`.
All are under the unchanged approved review-test paths.

After ordinary image provision, configured APK build and `prepare-app`, the owned
runner supports these **independent scenarios**:

```sh
python3 scripts/run-device-tests.py --directory /tmp/opencode/atw-device-<32-hex> auth
# AGP connected tests uninstall the app-under-test. Re-run the documented
# prepare-app command with the same normal APK before a following native scenario.
python3 scripts/run-device-tests.py --directory /tmp/opencode/atw-device-<32-hex> accounts
```

`auth` selects five existing/new startup/Keystore/input tests. The separately
selectable `AuthSavedStateTest` has its own retained focused execution. `accounts`
is Kotlin/JUnit/Appium on the normal APK: invalid/corrected registration, private
email/password login, visibility/native clipboard paste, both orientations,
scrolled full-size actions, native Back, force-stop/protected-token relaunch and
revoked-token login handling. Every independent scenario explicitly resets the
owned Compose stores and app data; retained restart checkpoints never do so.

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
claim of final UI acceptance. I02 parent reconciles child evidence; B1 independent
review still follows I01–I04 together. Full same-account recovery, cross-account
Logout cleanup, reset/second device, social/game privacy and full scenarios remain
with I03/I04/I09/I13/I14. Broad device/upgrade/architecture/full-suite and complete
interface-to-mock comparison remain I34/I35/I36/B8. No release gate is passed here.
