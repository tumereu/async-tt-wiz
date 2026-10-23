# Client storage

Android SQLDelight/SQLite storage for accepted server records and personal UI state,
plus separate public card catalog and artwork caches. Uses `:core`; generated SQL
types remain internal to this module, never part of the shared model or protocol.

## Build and test

From the repository root with JDK 21 and Android SDK 36:

```sh
./scripts/gradle.sh :client-storage:test :client-storage:verifySqlDelightMigration
./scripts/gradle.sh :client-storage:assembleRelease
```

Host tests use temporary file-backed SQLite, not mocked queries. Android driver
instrumentation is under `androidApp/src/androidTest`; run it only on a disposable
test device with `:androidApp:connectedDebugAndroidTest`. Host tests alone do not
prove Android-driver behavior. The retained synthetic `src/main/sqldelight/databases/1.db`
fixture and generated migrations verify upgrades; do not rewrite migration history.

## Storage boundaries

- `accepted_cursor`/`accepted_record` commit downloaded accepted data and cursor in
  one transaction. Expected-revision checks guard the **local persistence base**,
  not server gameplay admission. Game replica/tree storage stages and validates
  accepted data before publication; the server remains authoritative.
- `personal_state` stores local UI payloads independently. `public_cache` and the
  separate catalog/art directories hold refetchable public data; eviction must not
  delete accepted definitions, cursors or private UI state.
- There is no durable outgoing-request/prediction journal or authoritative local
  game. Process restart restores accepted data, not lost uncertain requests.
- Scope keys are not authorization. `activateAccount` retains same-account state
  and clears private state on owner change. `clearPrivateData` removes accepted
  records/cursors and personal state atomically, retaining public caches. Android's
  auth adapter owns credential protection and callback/account-generation fences.

## Safe open and migration

`ClientStorage.open { rawDriver }` owns a synchronous driver with foreign keys
enabled on every connection. No automatic create/upgrade/downgrade or destructive
corruption handler may run before this boundary. Creation of an empty database,
forward migration, validation and version publication are transactional. Newer,
malformed, corrupt or unsuccessfully migrated databases fail closed without
erasure; callers get a safe `StorageOpenResult.Failed` and no writable repository.

`AndroidClientStorage` uses SQLDelight's Android driver over a raw Android database,
not JDBC or an auto-migrating `SQLiteOpenHelper`. Opening on the main thread is
rejected. All repository methods block: schedule them off-main and serialize
access/close through the owner. `StorageViewModel` opens the app's private
no-backup database and drains queued work before closing. Backup/device-transfer
exclusions cover credentials and private data; failure recovery never means reinstall.

## Public data and import

- [Catalog installation and queries](CATALOG.md)
- [Deck text parsing and save mapping](IMPORT.md)
- [Lazy artwork and network/cache policy](ARTWORK.md)
