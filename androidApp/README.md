# Android app

Native Kotlin/Compose two-player manual tabletop (Android 10/API 29+). The app
provides account entry/reset, contacts and invitations, saved deck import/management,
preparation, gameplay, chat and history/review. It uses `:core`/`:protocol` for shared
state/wire behavior and `:client-storage` for SQLite/catalog/art adapters. Shared
game state is server-authoritative; selection, camera and review position are local.

## Configure, build and test

Use JDK 21 and Android SDK platform 36, from the repository root:

```sh
./scripts/gradle.sh -PatwApiOrigin=https://tabletop.example.com \
  -PatwResetOrigin=https://tabletop.example.com :androidApp:assembleDebug
./scripts/gradle.sh :androidApp:testDebugUnitTest :androidApp:lintDebug
```

No production host is embedded. Origins have no path, credentials, query or fragment.
Unconfigured builds fail safely instead of simulating login. Release uses platform
TLS and forbids cleartext. Only debug builds allow explicit `http://127.0.0.1:<port>`
for isolated development with ADB reverse; see the root [local setup](../README.md#run-locally).
`atwCatalogTestOrigin`/`atwCatalogPath` configure debug-only synthetic public data;
release always uses MTGJSON/Scryfall. Set `atwArtContact` before real artwork use.

For a friend-installable APK and signing options, use
[`scripts/build-release-apk.sh`](../scripts/build-release-apk.sh) and the
[self-hosting guide](../deploy/README.md). Ordinary `assembleRelease` is unsigned
unless all external signing references are supplied. Version allocations live in
`release/version.json`; do not reuse installed version codes. Optional FCM builds
require all four `atwFcmApplicationId`, `atwFcmProject`, `atwFcmApiKey`, `atwFcmSender`
identifiers; normal builds neither initialize nor require Google services.

Host tests include controllers, API adapters and Compose/Robolectric checks.
`:androidApp:connectedDebugAndroidTest` runs instrumentation on a disposable device;
it may uninstall the target app. Do not run against a personal installation.

## Credentials and lifecycle

- `StorageViewModel` opens SQLite off-main, without destructive recovery. A newer
  or failed schema blocks entry with Retry/update guidance, never an automatic wipe.
- `AuthController`/ViewModels retain passwords, drafts and reset tokens only in
  volatile memory through rotation, not saved-instance state or persistent storage.
- Only the opaque session token persists, encrypted with an origin-bound Android
  Keystore AES-GCM key in a no-backup file. Startup checks `/account`; network
  failure preserves the token for Retry, while revocation/key loss requires login.
- Logout immediately clears visible private state and fences old-account callbacks.
  Off-main cleanup removes protected credentials and private replica/UI state while
  retaining public caches. A local cleanup marker completes interrupted Logout
  before token restoration. Remote revocation is bounded best effort, not a queue.
- Same-account reauthentication preserves context; account changes clear private
  state. Already-authorized server work may finish. Network failures do not imply
  rejection; same-process game recovery retains immutable original requests/IDs.
  Process death restores accepted state, not a durable outgoing/prediction journal.

## Reset links and synchronization

`atwResetOrigin` must match the server's `RESET_BASE_URL`. Cold/warm intents accept
only that HTTPS origin, exact `/reset-password` path and canonical token query;
links cannot choose the API host or auto-submit/login. A read-only preflight checks
usability; confirmation still validates atomically. Public verified app links need
an operator-owned `assetlinks.json` for the actual package/signing certificate;
otherwise the server's HTTPS browser form remains available.

Game commands use authenticated `/v1/socket` WebSockets; the proxy must pass upgrades.
Receipts mean admitted, results correlate original outcomes, and revisions/baselines
advance accepted state. Predictions and uncertain requests remain separate from
the accepted replica. See [server game notes](../server/GAMES.md) and
[client storage](../client-storage/README.md) for recovery/data boundaries.
