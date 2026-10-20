# 7. Architecture and operations

Status: technical-design proposals unless explicitly confirmed. Native Android,
Kotlin Multiplatform (D-260) and D-243's dynamic game-data boundary are confirmed.
Shared client/server game-state objects and processing, permitted local die/coin
generation and server-side jOOQ access are selected by D-261.
D-263 accepts the recommended stack and architecture directions, including Spring
Boot/PostgreSQL/Flyway, Compose Canvas, ordered gameplay WebSocket plus HTTPS/JSON,
Ktor Client/kotlinx.serialization and the E2E tool combination. The table below
separates these selections from remaining design work; no catalog provider is
selected. This planning does not authorize application code or a
delivery-stage transition. D-115's behavior/review/UI/implementation gates remain;
not every schema/library choice is a prerequisite for finishing behavior work.

Focused proposals: [client](client-architecture.md),
[server](server-architecture.md), and [test architecture](test-architecture.md).
This document retains cross-cutting API, data-source, security and operational work.

## 7.1 Proposed boundaries

### Android application and reusable core

- Kotlin Multiplatform core (selected, D-260), with Android-first delivery and
  no required iOS/desktop implementation. Keep shared logic independent of layouts
  and input schemes so a future desktop client can have a different interface.
- Compose Canvas game area with styled Compose menus/surrounding controls (D-263);
  scene/input architecture and exact component APIs remain design work.
- Pure domain/replay module for state transitions and structural validation,
  shared on the JVM with the server as the same game-state objects and command
  processing implementation, not separately implemented prediction logic (D-261).
- Board/object/container primitives plus explicitly specified manual-play
  conveniences, including D-112–D-114 projections/assignments/Stack quick actions.
  No automatic card-text rules, legality validators, or inferred game effects.
- Local repository for cached game records, drafts, queue, and review state.
- Synchronization worker and transport adapter.
- Card catalog/deck parsing adapter and image cache.
- Notification/deep-link integration and account/credential management.

The domain layer must not require Android widgets, network availability, or a
running service to replay a downloaded game. Reducer semantics must match the
service's semantics through the same shared game-state types and processing code
(D-261); parity fixtures supplement, not replace, that code-sharing requirement.
Die/coin samples may be acquired locally once and supplied to shared processing;
server acceptance/recording remains authoritative, not a second independent roll.

### Shared service

- Authentication, invitations, and membership.
- Game history/control transactions and structural validation.
- Incremental sync and operation-result lookup.
- Durable notification outbox and delivery workers.
- Card data distribution/cache integration where needed.
- Retention, administration and observability; no user-facing deck/game export
  deliverable is implied (D-241/D-214).

A modular monolith is the selected starting point (D-263); independent microservices
are not needed simply to separate these responsibilities.

## 7.2 Technology agreement and remaining choices

| Area | Selected direction or proposed candidate | Decision still needed |
| --- | --- | --- |
| Shared client language/core | Kotlin Multiplatform selected (D-260) | Module/API details; future platform integrations, not delivery commitments. |
| Android UI/rendering | Compose Canvas game area with styled Compose surrounding UI selected (D-263) | Scene/animation/input design, accessibility/automation mapping, minimum Android API and versions. |
| Local persistence | Transactional SQLite selected (D-263); Room Multiplatform or SQLDelight | Choose between those access libraries; schema, migrations, encryption and cache/recovery boundaries. |
| Background work | WorkManager for appropriate Android background recovery selected (D-263) | Retry/lifecycle policy and battery restrictions; foreground gameplay sync must not wait for scheduled work. |
| Shared service | Kotlin/JVM Spring Boot modular monolith selected (D-263) | Supported versions, hosting/operator model and detailed module boundaries. |
| Shared database | PostgreSQL selected (D-263) | Schema, backup/restore, version and capacity. |
| Server data access | jOOQ selected (D-261) | Version/edition, schema code generation and explicit transactional query design; generated records stay server-only. |
| Database migrations | Flyway with versioned SQL selected (D-263) | Compatible versions/editions and deployment/upgrade procedure. |
| Transport | HTTPS/JSON for accounts/catalog/reads; ordered WebSocket for gameplay with durable revision recovery selected (D-263) | Commands/envelopes, admission order, reconnect/resume, pagination, idempotency and compatibility; a socket alone is not durable delivery. |
| Client networking/serialization | Ktor Client and kotlinx.serialization selected (D-263) | Platform engines, wire schemas/codecs, timeout/recovery and schema evolution. |
| Build/API contracts | Gradle Kotlin DSL and OpenAPI for HTTP selected (D-263); explicit WebSocket schemas | Module/build/version matrix, jOOQ generation and HTTP/socket schema consistency. |
| Security/session integration | Spring Security, revocable sessions and Keystore-backed Android credential protection selected (D-263) | Session/token storage/refresh/revocation, password/reset mechanisms, email delivery and rate limits; no new account-management UI scope. |
| Image loading | Coil conditionally accepted, subject to canvas integration (D-263) | Canvas image lifecycle/decoding/cache integration and compatibility validation. |
| Verification | Kotlin/JUnit + Appium/UiAutomator2 + Docker Compose E2E; Testcontainers for smaller integration tests selected (D-263) | Exact versions, runners/devices, reset/failure controls, CI and measured performance budgets. |
| Push | Platform-compatible notification delivery | Provider, device registration, privacy and failure policy. |
| Card source | Generic/custom definitions; MTG catalog adapter for the initial use case | Provider permission/terms, descriptive fields, update policy. |

These selections are not permission to start a scaffold or install packages.

### Architecture completion order — D-263

1. Specify command/sync envelopes, ordering, request/result identities and recovery.
2. Define server schema/transactions and client durability/reconciliation, including
   tree revisions, snapshots, random inputs and migrations.
3. Complete scene/input/accessibility boundaries and normal-app gesture testability.
4. Define security/session mechanisms, external adapters and deployment/recovery.
5. Pin compatible versions and CI/device/performance budgets.

This is technical specification work, not a new implementation plan or gate pass.
Support targets, hosting/operator budget, production catalog/art rights,
distribution and retention/privacy expectations still need owner constraints;
unspecified details are not automatically fresh product questions.

## 7.3 Card catalog and decks

### Dynamic game data — D-243

**Confirmed:** game/card definitions, descriptive metadata and artwork are
dynamically supplied data, not a real licensed catalog bundled into the Android
application, backend executable or distributable service image. Keep them
replaceable without changing/rebuilding application code. This is a licensing-
motivated separation, not a conclusion that dynamic loading itself grants rights.

The normal data-loading/import/image path must also accept synthetic datasets and
artwork for automated tests; no hard-coded real-card identities or test-only
parsing/rendering/gameplay path. Environment configuration may select the source.
The same normal Android app and real backend run against that source in
[full-stack tests](../05-verification/e2e-strategy.md).

Data is not executable game logic. Preserve confirmed manual-play behavior and
retained per-game/per-face essential data/defaults: catalog updates must not
silently reinterpret existing games/history. Dynamic delivery does not require
re-fetching every card on every action, eliminate downloaded read-only access,
approve arbitrary scripts or a custom-data editor, or select a provider. Exact
source/package/schema/version/cache/update mechanics remain technical design;
production distribution, attribution, image and storage rights still need review.

The specification must select a card-data source and define:

- Stable card/printing identifiers, multiple faces, oracle updates, image rights,
  attribution, allowed caching, rate limits, and availability fallbacks.
- A versioned essential card record retained with a game so external updates do
  not unexpectedly rewrite history. D-65 specifically confirms retaining resolved
  per-face corner defaults for provider-independent Reset and stability across
  updates. D-182 still requires a game-service connection for the Reset action.
- Import grammar, supported deck-export formats, normalization, aliases,
  localization, ambiguous names, and unavailable printings.
- Custom/proxy entries and how players correct mistaken catalog matches.
- Offline deck availability and predownload behavior for a game.
- Text-only operation when images fail and accessible full card text.

Card text and metadata are display/import data, not executable rules. Deck
legality checks and mechanics recognition are outside the generic engine scope.

### Corner-default source feasibility (18 October 2026)

D-64 conditionally approves using available source metadata for default corner
text; it does not select a provider. Research findings, not release commitments:

| Candidate | Documented fields and acquisition |
| --- | --- |
| [Scryfall card objects](https://scryfall.com/docs/api/cards) | String-valued `power`, `toughness`, `loyalty`, and `defense`, including applicable face-level fields; optional/nullable. [Bulk exports](https://scryfall.com/docs/api/bulk-data) are available, alongside card API access. Direct documentation fetch returned 403 here; these findings were checked against indexed official documentation. |
| [MTGJSON Card (Atomic)](https://mtgjson.com/data-models/card/card-atomic/) | Optional string `power`, `toughness`, `loyalty`, and `defense` fields. [Downloads](https://mtgjson.com/downloads/all-files/) include AtomicCards and AllPrintings; the latter also offers database formats. Model/download pages were read directly. |

These sources make the requested metadata feasible, but do not guarantee every
record has every field. Preserve symbolic printed strings rather than calculating
them or coercing them to numeric values. The catalog adapter can build one default
corner string from suitable metadata; the engine continues to edit plain text.

MTGJSON's [license page](https://mtgjson.com/license/) was inspected, but provider
selection still needs a complete terms/attribution review, including upstream
card content and image rights. Neither metadata availability nor that page
settles image redistribution/caching rights. Also compare face/printing identity,
coverage, update/distribution policy, offline storage, and missing-data behavior.

D-65 confirms a blank editable corner fallback when metadata is unavailable,
retained per-game/per-face defaults, and Reset using the selected face. Provider
choice, exact source-version mapping, and explicit refresh policy remain open.

## 7.4 API specification deliverables

Before implementation, produce a complete machine-readable API contract plus
human examples covering:

- Authentication/session lifecycle, device registration, invitations, membership.
- Game creation/setup/list/read, incremental history sync, node retrieval.
- Automatic publication, permissive current-state acceptance with limited
  movement/Stack optimistic guards (D-187),
  explicit interruption intent and activation, switch branch,
  archive/restore and selective undo/redo, with automatic grouping metadata.
- Context messages, result acceptance/fresh play and deck/catalog access. Personal
  review/menu/camera state is UI-only, not an API-owned game record (D-259);
  requests may carry explicit viewed-context references without synchronizing a
  review cursor. No cross-device review-progress/read-receipt service.
- Group/event-scoped generic pings with source-action context, one standard
  marker/no label-style picker (D-77), and separate optional messages; approved
  placement/area transitions, not a configuration editor or presence channel.
  No cast/target-declaration API or rules-completion state.
- Operation-result lookup, privacy/service data-deletion obligations and supported
  app versions. Manual group edits, branch comparison/renaming, fuller deck
  editing/export, custom authoring and response requests are not R1 API deliverables.
- Exact request/response/error fields, limits, pagination, ordering, timestamps,
  revision domains, continuation identities, accepted/rejected action and explicit
  interruption results, idempotency retention, and cancellation semantics.
- D-258 atomic shared-history switch/both-Redo reset and idempotent recovery; D-259
  state ownership, accepted-replica versus prediction bookkeeping and synchronization
  revision/checkpoint integrity. D-264 selects gap/duplicate recovery, atomic
  replica/cursor updates, periodic server-head checks and checkpoint state digests,
  not per-node/Merkle hashes or a mandatory event chain. The older
  [checksum-chain proposal](sync-and-storage.md#proposed-revision-checksums-and-correction-points--d-259)
  is an unselected alternative constrained by mutable gameplay trees, same-head
  revisions and difference-only repair;
  it is not a blockchain/consensus requirement or a new whole-head action lock.
- Persistent lobby versus play-incarnation fencing on every gameplay/control/
  exposure request, result reference and per-player Undo/Redo stack. Old requests
  never mutate fresh play; original committed outcomes never overwrite it.
- Unauthorized versus nonexistent resources, validation errors, stale revision,
  rate limits, unavailable dependencies, and incompatible schema versions.

Operation IDs and revisions, not client clocks, determine causality. User-facing
timestamps are explanatory metadata. Payload size and batch limits must not
allow a typical deck load or shuffle to fail unexpectedly.

## 7.5 Security and privacy

- Enforce per-game membership on every read/write and object reference.
- TLS in transit; platform-protected credentials on Android; no secrets in logs.
- Treat chat, deck imports, labels, and custom definitions as untrusted content.
- Validate uploads and exports; do not run card scripts or imported code.
- Do not put hidden card identities, tokens, or full game contents in routine
  analytics/crash reports.
- Define account recovery, lost device/session revocation, abuse handling,
  dependency updates, and deletion/export requests.
- Do not describe the product as confidential between opposing players.
  Both can access all in-game information by design.

Login before the main menu and available registration are required (D-188), not
guest entry. D-197 selects username/email/password registration, email/password
login and password reset by email. Other users see usernames, not account emails;
do not expose email in contact/invitation/game payloads to other users. Provider,
exact session/token storage and reset mechanics remain technical design or
explicit behavior gaps. D-263 selects Spring Security, revocable sessions and
Keystore-backed Android credential protection, not new account-management screens
or arbitrary provider defaults. End-to-end
encryption and self-hosting are also open choices, not implied by “private game.”

D-249 excludes credentials from card-text trimming: preserve password whitespace
and Unicode consistently across registration/login/reset. Specify account email/
username normalization and password strength separately, with Unicode and paste
fixtures and no secret logging. No provider/helper default may silently normalize
passwords differently at different entry points.

## 7.6 Reliability and operational contracts

Proposed correctness targets:

- No acknowledged shared operation is lost under the declared service recovery
  model. The backup/recovery model must quantify its actual limits.
- Process death after a successful local-save indication does not lose the draft
  under ordinary app/device operation. Device loss requires a separately defined
  backup/export policy.
- A retry never duplicates a committed action.
- A partially failed notification does not roll back game state.
- Failed migrations preserve original durable data and offer a recovery route.

Still required before implementation:

- Reference devices, game/history sizes, cold-load and action latency budgets.
- Undo/Redo cost accounting beyond suffix count N: target validation, input/output
  payload, snapshot/reference loading, cleanup fan-out, replay and cache rebuild.
  Set measured CPU/memory/latency budgets with indexing/incremental summaries;
  exceeding validation budget leaves the operation unavailable and state unchanged,
  never partially validated. Do not add a Stack count cap/history expiry (R-13).
- Local feedback and action-sync-start budgets that enforce immediate sync
  without waiting for group closure or deliberate batching.
- API latency/availability targets, expected load, rate/storage limits.
- Backup frequency, recovery point/time objectives, and tested restore steps.
- Deployment, rollback, database migration, client compatibility, and incident
  procedures.
- Metrics for sync lag, failed/conflicted operations, replay integrity, database
  pressure, notification failures, and errors without leaking game content.
- Operator ownership, cost assumptions, distribution/signing, release channels,
  and crash-reporting consent.

## 7.7 No implementation shortcuts across specification gaps

Do not resolve open questions by quietly adopting a library's defaults. In
particular, database cascade deletion, notification delivery semantics, conflict
merging, random generators, and Android backup settings can violate this
product's recovery and trust model. Their behavior must be specified explicitly.
