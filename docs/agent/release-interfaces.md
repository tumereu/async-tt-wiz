# I35 local versioned APK and signing interfaces

Implementation only: **execution and compatibility qualification UNVERIFIED**.
This is not release approval, a distribution service, or a deployment workflow.
Product authority: [runtime/packaging](../design/04-technical/runtime-and-verification-profile.md#runtime-and-packaging),
[Android compatibility](../design/04-technical/android-distribution-and-compatibility.md),
and [I35](implementation-plan.md#b8--whole-game-usability-qualification-and-delivery).

## Version allocation and declared formats

`release/version.json` is the Git allocation ledger. Entry 1 / `0.1.0` records the
previous hardcoded build; entry 2 / `0.1.1` is the next allocated build. Append an
entry with a strictly larger Android `versionCode` before each new distributable
version. Never edit/delete/reuse a prior allocation, including one already given
to testers. Serialize allocation through the integration owner; reconcile branch
collisions before building. Git retains allocation provenance. Gradle and the
packager both consume the last entry and reject non-increasing/out-of-range codes.
No command-line version override, timestamp version, or automatic Git mutation.

Package remains `org.asyncttwiz.android`, minSdk 29, target/compile SDK 36. APK
version and protocol version are independent. The manifest declares **only** the
currently implemented protocol major 1 / minor 0, engine 1, command 1 and read
format 1. `GameHello`/`GameWelcome`/`GameWire.valid` and server `GameHub` are the
actual negotiation/admission anchors. `TreeSync`/`GameplaySync` read v1 trees,
boards and mutations; `DeckValidation` accepts definition schema 1. Their paths
are recorded in `sourcePaths`, not a new compatibility inspector.

Local SQLDelight schema is 2 with the existing forward 1→2 migration; database
version 0 means an empty new store, not permission to replace an existing store.
Server Flyway migration head is 16. `OwnershipDatabase.validateSchema` requires
successful validation with no pending migrations; it does not start against an
arbitrary older schema. These are source declarations, **not** claims that all
retained APK/server combinations or upgrades have passed. When real readers,
negotiation or migrations change, update these declarations and their source
anchors in the same change and queue affected qualification. No universal/future
compatibility range is declared.

## External signing inputs

Each group is all-or-none. A Gradle property takes precedence over its environment
equivalent. Put properties only in the operator's private Gradle user configuration
(not this repository's `gradle.properties`), or supply the following environment
references. No key creation, secret discovery or release-key acquisition is part of
this interface.

| Gradle property | Environment reference | Meaning |
| --- | --- | --- |
| `atwReleaseStoreFile` | `ATW_RELEASE_STORE_FILE` | Existing external release keystore path |
| `atwReleaseStorePasswordFile` | `ATW_RELEASE_STORE_PASSWORD_FILE` | External UTF-8 store-password file path |
| `atwReleaseKeyAlias` | `ATW_RELEASE_KEY_ALIAS` | Alias in that store (not a password) |
| `atwReleaseKeyPasswordFile` | `ATW_RELEASE_KEY_PASSWORD_FILE` | External UTF-8 key-password file path |
| `atwTestStoreFile` | `ATW_TEST_STORE_FILE` | Existing external stable CI/test keystore path |
| `atwTestStorePasswordFile` | `ATW_TEST_STORE_PASSWORD_FILE` | External test store-password file path |
| `atwTestKeyAlias` | `ATW_TEST_KEY_ALIAS` | Test alias |
| `atwTestKeyPasswordFile` | `ATW_TEST_KEY_PASSWORD_FILE` | External test key-password file path |

Paths resolve relative to the repository root unless absolute; canonical files
must exist **outside the checkout**, including through symlinks. Password files
contain exactly one nonempty line; one terminal LF/CRLF is removed, not password
whitespace. The authorized build reads these explicitly configured files; it does
not embed passwords in the app, version manifest or package bundle. Do not log
secret values, enable shell tracing, or publish private Gradle configuration/cache
contents. Operator owns file permissions, custody and backup of signing continuity.

No release references → AGP produces an **unsigned** release APK. Partial references
fail configuration. There is no debug/test fallback for release. A configured
release keystore must differ from the default debug and configured test keystore
paths; the operator must also ensure the actual release certificate/key is distinct
(different file paths alone cannot prove different keys).

No test references → keep AGP's existing persistent local debug signer unchanged.
Retain that legitimate local Android user-home keystore for local upgrades; do not
delete/regenerate it between builds. Configured test references sign debug and its
instrumentation through AGP's normal mechanism and let authorized CI retain a stable
test identity across workers. Never use the release key for CI/debug, copy keys into
Git/images/APKs, or regenerate a key as an upgrade workaround. A debug/test install
cannot be converted into a release-key install under the same package by `-r`;
qualify upgrades within each stable signing lineage, without wiping user data.

## Executor commands (not executed by the writer)

Use the integrated saved source commit, not the writer's base commit. Capture
actual output with `scripts/capture-check.py` under the **executor's** run namespace.
Do not repeat these if the combined affected queue already supplies identical
source-pinned evidence. No clean build/full suite or platform inspection is needed
to exercise this small helper.

```sh
python3 -m unittest discover -s tests -p test_package_release.py -v
./gradlew --no-daemon --max-workers=2 :androidApp:assembleRelease :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest
```

Run the affected Gradle command with no signing groups first if no operator-owned
keys are available; the release output must remain unsigned and local debug/test
behavior unchanged. A later authorized signing executor runs the same affected
assembly with its preconfigured external test/release references. Missing keys do
not waive this signing exercise. No secret values are command arguments.

The helper takes **already-built** artifacts; it runs only local Git reads, SDK
`aapt dump badging` and `apksigner verify --verbose --print-certs`, not a build,
signer, install, network request, or publication. Supply existing SDK tool paths
explicitly when not on PATH. Example for an authorized signed release:

```sh
SOURCE_COMMIT=$(git rev-parse HEAD)
APK="$PWD/androidApp/build/outputs/apk/release/androidApp-release.apk"
OUT="/tmp/opencode/i35-packages/$SOURCE_COMMIT-release"
python3 scripts/package-release.py --apk "$APK" --output "$OUT" \
  --source-commit "$SOURCE_COMMIT" --kind release \
  --aapt "$ANDROID_SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION/aapt" \
  --apksigner "$ANDROID_SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION/apksigner" \
  --expected-sha256 "$BUILT_APK_SHA256" \
  --expected-certificate-sha256 "$RELEASE_CERTIFICATE_SHA256"
cd "$OUT"
sha256sum -c SHA256SUMS
```

`BUILD_TOOLS_VERSION` selects the executor's already-installed SDK tools;
`BUILT_APK_SHA256` is its retained build artifact hash. The expected certificate
digest is a **public** lowercase 64-hex SHA-256 supplied by the key custodian via a
trusted channel, not a secret or a newly discovered key. It must match the sole
current verified signer. Signing rotation/multi-signer releases need their own
supported continuity design/qualification; this interface does not invent one.

Without release signing, use `androidApp-release-unsigned.apk`, omit the expected
certificate, and choose a fresh output directory. Use `--kind test` and the debug
APK/test certificate for test-lineage bundles. An absent/invalid APK signature
produces explicit `unsigned-or-invalid` filenames and manifest status, never a
signed/release-ready claim; missing SDK tools or malformed/mismatched identity,
checksum or verified certificate fail the command. An existing output directory is
never overwritten. All output must be outside the source checkout; no large APK
belongs in Git. A clean source checkout and explicit full HEAD pin are required.
If pending in-tree executor evidence would dirty that checkout, capture to its
external run-owned staging directory first, then retain those exact bytes under
the executor's committed run namespace; do not erase evidence to make it clean.

Output is one versioned APK, `manifest.json`, and `SHA256SUMS` covering both files.
The helper verifies package/version/minSdk/targetSdk against the allocation and
inspects a staged copy, checking its bytes before/after inspection. The manifest
contains the APK hash, actual identity/debuggable flag, signature-verification
status/public certificate digest, committed source pin/anchor hashes, and declared
protocol/schema support. Source pin is caller-supplied build provenance, **not** an
independent proof of how the APK was built. Hashes detect byte changes, not trusted
distribution/authenticity by themselves. `artifactSigningReady` means only a
non-debuggable release artifact verified against the expected certificate.
`releaseReady` stays **false** and qualification **UNVERIFIED** even when signed:
packaging cannot satisfy the remaining software/device/operator gates.

## Normal retained-data install upgrade

End users open a verified signed APK from the operator's chosen artifact URL in
Android's normal package installer and approve **Update**. No custom updater,
selected hosting provider, Play/GMS dependency or public push is introduced.

For authorized qualification, use the existing native journey adapters under the
[whole-lifecycle resource wrapper](test-resource-plan.md#enforced-lease-command):
hold `windows-devices` + `windows-routing`, plus `shared-e2e-backend` if reused,
from provision/initial install through journey/upgrade and exact cleanup. The
following is the **replacement step inside that owned lifecycle**, not permission
to install outside a lease or launch a second device runner:

```sh
adb -s "$SERIAL" install -r "$VERIFIED_NEW_APK"
```

First install the prior supported signed allocation and create retained login,
downloads and accepted data using normal UI. Verify the new bundle/hash and same
package/certificate, require a larger allocated versionCode, then replace with
`install -r` (or normal installer Update). Reopen normally and check retained login,
downloads, accepted state/history and normal server synchronization. Do **not** use
uninstall, `pm clear`, reset, `-d`, `-r` with an unrelated signer, or a destructive
migration to make it pass. Process replacement may lose outgoing unadmitted memory
and predictions (D-278); it must not fabricate new invocations. An install/signature
or migration refusal is a failure to diagnose, not a reason to erase data. The
local 1→2 schema path is not proof of a versionCode 1→2 APK upgrade.

## Remaining owners / no waiver

- **Integration supervisor / execution queue:** integrate the small version/signing
  build hunks alongside other leased Gradle edits, pin the connected candidate,
  execute the helper regression and combined affected assembly/packaging scopes,
  retain failures and dispatch fresh repairs/verification. Writer ran none of these.
- **I35 qualification executor:** API29/current Android, phones/tablets/non-GMS,
  signed process replacement/retained-data upgrades, incompatible refusal and real
  mixed-version games; service config/health/explicit migrations/shutdown/restart,
  amd64/arm64 image execution. All unperformed scopes remain **UNVERIFIED**, not
  waived by a manifest or prior partial device evidence.
- **Operator/key custodian:** existing external release key, stable distinct CI/test
  identity, certificate continuity, custody and permitted signing. No production key
  or secret was supplied, searched for, generated or read by this writer.
- **Operator/release owner:** choose artifact URL/host and provide actual external
  signing/distribution/rights/hardware gate evidence. This unit changes no gates or
  authoritative state. I34 final suites, I36 handoff and independent B8 capability
  reviews/verification remain mandatory; no release or software completion claim.
