# Tier 4 — Technical design proposals

- [Client architecture](client-architecture.md): selected Kotlin Multiplatform
  and Compose Canvas direction, shared core and platform-specific interfaces.
- [Server architecture](server-architecture.md): Spring Boot/PostgreSQL/Flyway
  selections, jOOQ, authority and transaction boundaries.
- [Test architecture](test-architecture.md): selected Docker backend/database
  boundary; selected Kotlin/JUnit + Appium/UiAutomator2 + Docker Compose scenarios,
  canvas UI automation and real-PostgreSQL migration/concurrency testing.
- [Synchronization and storage](sync-and-storage.md): local durability, queues,
  server authority/replicas versus UI state, publication, branching, replay,
  selected revision recovery/checkpoint state verification, optional older
  checksum-chain proposal, recovery, access and retention.
- [Architecture and operations](architecture-and-operations.md): proposed system
  boundaries, technology/provider choices, APIs, security, deployment and reliability.

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
Room Multiplatform versus SQLDelight remains unresolved; Coil is conditional on
canvas integration. Exact protocols, schemas, versions, hosting/providers, support
targets and operational policies remain design work. This agreement authorizes
documentation planning, not implementation or a delivery-stage transition. The
[E2E strategy](../05-verification/e2e-strategy.md) defines the confirmed normal-app,
real-backend test boundary and synthetic data/art requirement (D-244).
Mechanisms must implement the [behavior contracts](../03-contracts/README.md),
not replace missing behavior decisions with library defaults.

Tier numbers are reading/detail levels, not delivery stages. Technical work is
prepared for the relevant implementation slice; the entire technical design is
not a prerequisite for finishing behavior specification.
