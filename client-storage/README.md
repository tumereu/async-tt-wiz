# Client storage foundation — I01d1/I01d2

Real SQLDelight-generated SQLite schema/queries and a blocking, driver-neutral
repository. Host tests use **temporary file-backed SQLite**, not mocked queries.
The normal `:androidApp` now initializes the production Android driver off-main,
with safe failure presentation and backup/device-transfer exclusions (I01d2).
Android build/artifact qualification is **not runtime qualification**: actual
Android-driver instrumentation execution remains **I01f**, after device-adapter
qualification. I01d parent combined qualification and B1 independent review remain open.

## Boundaries

- `accepted_cursor` and `accepted_record`: downloaded accepted records with
  versioned JSON payloads and a signed-64-bit accepted cursor, committed together.
  `commitAccepted` makes incremental upserts/deletes and advances the cursor in one
  transaction; `accepted` reads a coherent snapshot. Its expected revision checks
  the **local persistence base**, not server gameplay intent/admission.
- `personal_state`: saved UI payloads/drafts, independently keyed by scope/key.
- `public_cache`: refetchable public catalog/art metadata payloads. Eviction never
  deletes accepted records, cursors or personal state. This is not yet a downloader,
  image cache policy, derived game checkpoint implementation or complete baseline.
- No outgoing-request, prediction, provisional-board or unreported-exposure table.
  There is no authoritative local game. Restart cannot reconstruct lost requests
  from this API. Required accepted definitions are not public-cache entries.

Repository methods return adapter-owned values, never SQLDelight-generated types;
generated code stays in `org.asyncttwiz.storage.internaldb`, outside `:core` and
`:protocol`. Payloads preserve exact text/version; feature decoders/semantic
validation arrive with their owning features. Scope strings are storage keys,
**not authentication**. Account/session callback fences, Logout clearing and
Keystore credentials remain I02/I03; full game records/sync/staging I14/I26.
There is no full-tree coverage flag or claim in this foundation schema.

## Open/migration contract for platform adapters

`ClientStorage.open { rawDriver }` consumes/owns a **synchronous raw driver**.
It must have foreign keys enabled on **every connection**, no automatic schema
create/upgrade/downgrade, no active transaction, and no destructive corruption
handler. A one-off `PRAGMA foreign_keys = ON` is insufficient for JDBC's per-operation
connections; host tests configure the driver property instead. Android configures
foreign keys on its connection pool before entering this boundary.

The boundary verifies foreign keys, version/required column shape and integrity,
creates only an empty unversioned database, or runs the generated v1→v2 migration.
Creation/migration, post-validation and `user_version` publication are in **one
SQLite transaction**. Newer versions, malformed/unversioned existing schemas and
failed migrations return `StorageOpenResult.Failed`, close the driver and expose
no repository. Failure never wipes/deletes/recreates an existing database. The
structured failure contains no private SQL/path/payload/exception text. The caller
must show an explicit recoverable failure, not continue writes or reinstall.
`close` is idempotent; all further operations reject. Calls/close on one instance
are serialized; this is not a multi-process database-ownership scheme.

All operations block. The Android caller must schedule them off the UI thread
and own the repository lifecycle; host success does not prove Android execution.

## Android adapter and normal launcher — I01d2

`AndroidClientStorage.open(file)` uses **SQLDelight's AndroidSqliteDriver 2.4.1**
over an Android `SQLiteDatabase`, not the JDBC driver. A small internal
`SupportSQLiteDatabase` bridge uses public Android/AndroidX APIs; AndroidX's own
raw wrapper is internal. There is **no SQLiteOpenHelper**, whose automatic
create/version/migrate or default corruption-deletion policy could run before
the repository validates the retained file. The raw open has no localized
collators/schema callbacks, enables connection-pool foreign keys, and supplies
a corruption handler that stops/closes without deleting/recreating the file.
The shared boundary alone owns transactional DDL, validation and `user_version`.
An Android main-thread open is rejected before file I/O. No exception/SQL/private
path or payload is returned in `StorageOpenResult.Failed`.

The normal launcher creates `StorageViewModel` through its ordinary activity
ViewModel store. It opens `noBackupFilesDir/client-storage/replica.db` on one
owned executor, survives activity recreation without opening another repository,
and queues close **after** any in-progress open when the activity really finishes.
Close never blocks the UI; worker shutdown drains that queue and late UI callbacks
are ignored after clearing. Process death relies on SQLite/OS rollback/close, not
an Android `Application.onTerminate` callback or a durable outgoing queue. The
repository is private to the worker; future features must keep operations there
or supply an equivalent asynchronous boundary. I02 replaces the branded scaffold
with account entry; this initialization inserts **no account/game/fixture records**.

During opening the scaffold reports progress. Failure leaves no usable repository
and explicitly says that local storage could not open and data was not erased;
a newer schema instead tells the user to update **without uninstalling**. Retry
reopens the same retained file, only after a failure, not in a loop or concurrently
with the initial open. There is no automatic wipe/repair, fake login or offline game.

The database and its journal/sidecars live under Android's private no-backup
directory. The manifest also keeps `allowBackup=false` and references explicit
legacy backup plus Android12+ **cloud and device-transfer** exclusions, including
credential-encrypted/device-encrypted databases, files, preferences and external
app data. This is intentional defense against OEM transfer behavior, not a claim
that every OEM transport was physically exercised. Future Keystore/token work is
I02; no private token/draft can be opted into these rules by default. Public cache
remains logically separate/refetchable even though this foundation shares one
excluded database; system cache/no-backup directories are never backed up. This
does not implement account Logout clearing or callback isolation (I03).

## Retained schema

`src/main/sqldelight/databases/1.db` is a populated **synthetic foundation** fixture,
not private user data or a released app database. SHA-256:
`5f88c2a52575c080088c59052091eb8749414a8d0ce25158186e06af1647d252`.
It contains two distinct scopes/records, exact cursor `9007199254740993` and a
Unicode draft. V1 payloads implicitly use version 1. Actual generated `1.sqm`
adds explicit payload-version columns defaulting to 1 and the public cache table;
existing text/cursors remain unchanged. Payload versions are positive at the
repository/open boundary; the new public-cache table also has a SQL constraint.
SQL uses the conservative **SQLite 3.18 dialect** (API29 SQLite is 3.22); no newer
UPSERT/RETURNING syntax. Host SQLite is newer and is not Android compatibility proof.

The Gradle migration verifier compares this retained schema to current `.sq`
definitions. Host tests additionally check populated upgrade/reopen and failures
after earlier generated ALTERs: both real SQLite SQL errors and simulated I/O
interruption roll back DDL/data/version and preserve prior file bytes, close the
driver and return no writable repository. Successful retry uses the same file.
Corrupt files, invalid paths, missing columns, orphan data and future schemas
are refused without erasure. The fixture generator is an explicit maintenance
command (`python3 scripts/create-storage-fixture.py --write`), refusing an existing
fixture; do not regenerate/replace migration history to make a check pass.

## Reproduce (JDK21, SDK36; Android bytecode Java17/minSdk29)

From the repository root:

```sh
./scripts/gradle.sh --no-daemon --console=plain --no-build-cache \
  :client-storage:clean :core:clean :protocol:clean \
  :client-storage:testDebugUnitTest :client-storage:verifySqlDelightMigration \
  :client-storage:assembleRelease \
  :core:jvmTest :protocol:jvmTest \
  :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest \
  :client-storage:lintDebug :androidApp:lintDebug verifyModuleBoundaries
python3 scripts/check-storage-foundation.py
python3 scripts/check-android-storage.py
python3 scripts/coverage-ledger.py
```

SQLDelight plugin/runtime/JDBC adapter **2.4.1**, host Xerial SQLite JDBC
**3.53.4.0**, Gradle9.7/Kotlin2.4.21/AGP9.3.3. SQLDelight 2.3.2+ upstream explicitly
supports AGP9 built-in Kotlin; this pairing is qualified by actual builds/tests.
Exact transitive/tooling graphs are strictly locked. Intentional lock refresh:
`./scripts/gradle.sh --no-daemon --console=plain --write-locks resolveAndLockAll`.
The artifact checker records upstream POM hashes and Apache/BSD license declarations,
not redistribution-notice completeness or operator rights clearance.

Storage host tests are in the approved `client-storage/src/test` path and run under the
build's **JVM21** with JUnit6. They include generated-query atomicity/rollback,
close/reopen, exact cursor/text retention, scope/category separation, concurrent
local-base writers and coherent snapshot reads, populated retained migrations and
non-destructive failures. APK assembly/lint and packaged-resource checks verify
build/API interfaces only; no Android driver/device test has executed in I01d2.

The normal app's approved `androidApp/src/androidTest` instrumentation contains
14 compiled tests across `StorageDriverTest`, `StorageViewModelTest`
and `StartupTest`. They cover production-driver fresh create/commit, actual SQL
rollback (including prior removals and a new cursor), reopen, public/personal
separation, exact long cursors/Unicode, the shared populated v1 asset/generated
migration, real third-statement migration failure/rollback and successful retry,
future/unversioned/malformed/corrupt schema refusal, pool FK enforcement on two
workers, closed-handle refusal, UI-thread rejection, actual controller failure/
retry/cleared-owner drain and normal-launcher recreation. Fixture assets belong
only to the instrumentation APK; the normal APK has no fixture database. Test
cleanup owns UUID-scoped directories, never the launcher's private database.
Controller tests use a test-owned Application path, not a test-only activity or
application action endpoint; normal-launcher tests still use `MainActivity`.

**Pending I01f device command**, after its adapter has selected the owned ADB
server and serial (never run against a personal installation):

```sh
ANDROID_SERIAL=<owned-serial> ./scripts/gradle.sh --no-daemon --console=plain \
  :androidApp:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.asyncttwiz.android.StorageDriverTest,org.asyncttwiz.android.StorageViewModelTest,org.asyncttwiz.android.StartupTest
```

I01f must retain actual per-device test XML/results, screenshots/hierarchy and
owned-device/port cleanup. Compilation is not a pass for those tests; controller
and launcher resource/failure assertions remain unexecuted until then. No reverse
I01d→I01f dependency is introduced. Full crash-during-upgrade, signed install
replacement and the supported-device matrix remain I35; all release gates stay open.

Source constraints: storage-architecture.md (client categories/migrations),
storage-mechanics.md (D-278 client persistence/crash matrix), sync-and-storage.md
(authority/accepted base), and android-distribution-and-compatibility.md (safe
migration failure). T-662 is partially qualified at this foundation boundary;
T-632/T-647/T-649 and P-059/P-060/P-063/P-065 constrain later integrated behavior,
not fully passed scenarios here. No F01–F88 UI behavior is redesigned by this unit.
