# Tier 4 — Technical design proposals

- [Client architecture](client-architecture.md): selected Kotlin Multiplatform
  and Compose Canvas direction, shared core, accepted scene/gesture-coordinator
  boundaries and platform-specific interfaces. D-267 corrects normal card input
  to single-tap Actions and double-tap tap/untap; mode double taps remain tentative.
- [Android distribution and compatibility](android-distribution-and-compatibility.md):
  signed APKs/manual installation, Android 10+ and safe compatible upgrades/mixed
  app versions (D-273), with exact protocol/build/version details still to design.
- [Server architecture](server-architecture.md): Spring Boot/PostgreSQL/Flyway
  selections, jOOQ, authority and transaction boundaries.
- [Storage architecture](storage-architecture.md): selected relational/versioned
  server storage, SQLDelight client categories/migrations, and separation of sync
  revision retention from gameplay history and request recovery.
- [Authentication and sessions](authentication-and-sessions.md): non-expiring
  revocable login tokens without saved passwords (D-268), account-safe recovery,
  and security/reset mechanisms still proposed.
- [Notifications and background work](notifications-and-background-work.md):
  optional FCM and best-effort polling fallback (D-269), recommended WorkManager
  scheduling, Android limits and account-safe delivery independent of game sync.
- [Card data and artwork](card-data-and-art.md): client public-source downloads
  and local reuse (D-270), lazy images and arbitrary client-supplied game definitions
  shared despite stale catalogs (D-271), Cockatrice source research, tentative
  MTGJSON startup checks and the alternative Scryfall recommendation, not licensing
  approval.
- [Test architecture](test-architecture.md): selected Docker backend/database
  boundary; selected Kotlin/JUnit + Appium/UiAutomator2 + Docker Compose scenarios,
  canvas UI automation and real-PostgreSQL migration/concurrency testing.
- [Synchronization and storage](sync-and-storage.md): local durability, queues,
  server authority/replicas versus UI state, publication, branching, replay,
  selected revision recovery/checkpoint state verification, optional older
  checksum-chain proposal, recovery, access and retention. D-274 selects shared
  seeded Fisher–Yates Shuffle and server-resolved Random starter at game start;
  exact generator/encoding/version details remain to specify.
- [Architecture and operations](architecture-and-operations.md): proposed system
  boundaries, technology/provider choices, APIs, security and reliability. D-272
  requires Docker Compose deployability; production deployment/backups belong in a
  separate repository, with application container/migration interfaces defined here.

These are proposals unless specifically confirmed. Native Android, Kotlin
Multiplatform with a core reusable by possible future iOS/desktop clients (D-260),
and dynamic game/card data and artwork (D-243) are selected. Android alone is
required for initial delivery; a future desktop interface may differ from mobile.
Client/server game-state types and processing must be shared, local die/coin
generation is permitted, and jOOQ is selected for server data access (D-261).
D-263 accepts the recommended technology/architecture directions: Spring Boot
modular monolith, PostgreSQL/Flyway, Compose Canvas with Compose surrounding UI,
SQLite, ordered gameplay WebSocket plus HTTPS/JSON reads/accounts/catalog,
Ktor Client/kotlinx.serialization, Gradle Kotlin DSL/OpenAPI, Spring Security with
revocable sessions and Keystore-backed credentials, and the proposed E2E tool
combination plus Testcontainers for smaller integration tests. WorkManager is
selected for appropriate Android background recovery, not the foreground sync path.
D-265 selects SQLDelight for client SQLite and accepts the storage layout; Coil
remains conditional on canvas integration. D-266 confirms rolling sync-revision
retention with fresh baseline recovery, retained gameplay history and rebuildable
checkpoint caches; exact limits/protocols remain design work, not a history expiry
policy. Exact protocols, schemas, versions,
providers, support targets and application operational interfaces remain design work.
D-272 assigns production hosting/deployment/backups to a separate repository;
Raspberry Pi hosting is a possible first trial, not a selected capacity guarantee.
This agreement authorizes
documentation planning, not implementation or a delivery-stage transition. The
[E2E strategy](../05-verification/e2e-strategy.md) defines the confirmed normal-app,
real-backend test boundary and synthetic data/art requirement (D-244).
Mechanisms must implement the [behavior contracts](../03-contracts/README.md),
not replace missing behavior decisions with library defaults.

Tier numbers are reading/detail levels, not delivery stages. Technical work is
prepared for the relevant implementation slice; the entire technical design is
not a prerequisite for finishing behavior specification.
