# 7. Architecture and operations

Status: technical-design proposals unless explicitly confirmed. Native Android,
Kotlin Multiplatform (D-260) and D-243's dynamic game-data boundary are confirmed.
Shared client/server game-state objects and processing, permitted local die/coin
generation and server-side jOOQ access are selected by D-261.
D-263 accepts the recommended stack and architecture directions, including Spring
Boot/PostgreSQL/Flyway, Compose Canvas, ordered gameplay WebSocket plus HTTPS/JSON,
Ktor Client/kotlinx.serialization and the E2E tool combination. The table below
separates these selections from design work. D-280 now selects MTGJSON records and
Scryfall artwork. D-284's [protocol](protocol-v1.md), [storage/replay mechanics](storage-mechanics.md)
and [runtime/verification profile](runtime-and-verification-profile.md) complete
the technical mechanisms/parameters previously labelled open in this overview.
Authentication/catalog/notification focused documents complete their selected
defaults. Component APIs, generated schemas/build locks and measured qualification
remain implementation artifacts, not new owner-policy decisions. D-278 supersedes
client crash-durable outgoing/exposure guarantees. This planning does not authorize application code or a
delivery-stage transition. D-115's behavior/review/UI/implementation gates remain;
not every schema/library choice is a prerequisite for finishing behavior work.

Focused documents: [client](client-architecture.md), [server](server-architecture.md),
[storage](storage-architecture.md), and [test architecture](test-architecture.md).
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

| Area | Selected direction or proposed candidate | Detail / completion reference |
| --- | --- | --- |
| Shared client language/core | Kotlin Multiplatform selected (D-260) | Module/API details; future platform integrations, not delivery commitments. |
| Android UI/rendering | Compose Canvas game area/styled Compose shell (D-263); Android 10+ / API 29 (D-273) | Scene/animation/input detail, accessibility/automation, exact target/compile SDK and dependency/device matrix; do not silently raise the accepted minimum. |
| Prediction/presentation | Accepted replica + volatile request journal + derived display (D-277/D-278) | Protocol v1 specifies result/delta/baseline correlation and handoff; UI controls/timing remain the UI gate. |
| Android distribution/compatibility | Signed APK downloads/manual updates, stable package/signing identity; compatible upgrades preserve data and mixed app versions can share games (D-273) | Artifact/signing workflow, release host, protocol negotiation/compatibility windows and supported migrations; no required Play Store or custom in-app updater. |
| Local persistence | Transactional SQLite through SQLDelight selected (D-265) | Storage mechanics specifies accepted records versus volatile outgoing work, migrations/backup isolation; actual DDL/generated queries are implementation artifacts. |
| Background work | WorkManager/non-FCM best-effort polling (D-263/D-269) | Notification profile fixes 15-minute requested cadence/retries/account fences, not a deadline or exact-alarm permission. Foreground sync is independent. |
| Shared service | Kotlin/JVM Spring Boot modular monolith (D-263), deployable through Docker Compose (D-272) | Supported versions/runtime resources and module boundaries; production deployment belongs in a separate repository, not a required VPS/cloud platform. |
| Shared database | PostgreSQL selected (D-263) | Schema/version/capacity and application recovery contract; production backup/restore automation and policy belong in the separate operations repository (D-272). |
| Storage layout | Relational/versioned-payload layout (D-265); complete retained current-game tree then incremental changes (D-276) | Storage mechanics supplies tables/indexes/locks/retention; protocol v1 supplies gzip chunk/manifest/staging/pin contract. No default lazy offshoots or routine full-tree updates. |
| Server data access | jOOQ selected (D-261) | Version/edition, schema code generation and explicit transactional query design; generated records stay server-only. |
| Database migrations | Flyway with versioned SQL selected (D-263) | Compatible versions/editions and deployment/upgrade procedure. |
| Transport | HTTPS/JSON and ordered gameplay WebSocket (D-263); durable server inbox/order/outcomes (D-275), volatile client recovery (D-278) | Protocol v1/storage mechanics specify envelopes, result/status/revision/sequence/fence, single-writer ingress and transaction boundaries; runtime profile supplies backpressure. Receipt is not acceptance. |
| Client networking/serialization | Ktor Client and kotlinx.serialization selected (D-263) | Platform engines, wire schemas/codecs, timeout/recovery and schema evolution. |
| Randomness | Fresh request-bound client Shuffle seed; server initial shuffles/Random starter (D-274) | Runtime v1 fixes secure entropy/SHA-256 counter stream/unbiased Fisher–Yates/UUID byte order and seed encoding; literal vectors supplement recorded-outcome replay, not competitive fairness proof. |
| Build/API contracts | Gradle Kotlin DSL and OpenAPI for HTTP selected (D-263); explicit WebSocket schemas | Module/build/version matrix, jOOQ generation and HTTP/socket schema consistency. |
| Security/session integration | Spring Security/Keystore, non-expiring revocable sessions (D-263/D-268), successful-reset all-session revocation (D-279) | Auth profile fixes opaque token/verifier, Argon2id/normalization/rates and SMTP reset handling. D-289 uses ordinary new-request authorization, allows in-flight completion and best-effort remote Logout; no special reset/commit race protocol or new account-management UI. |
| Image loading | Coil conditionally accepted, subject to canvas integration (D-263) | Canvas image lifecycle/decoding/cache integration and compatibility validation. |
| Verification | Kotlin/JUnit + Appium/UiAutomator2 + Docker Compose E2E; Testcontainers (D-263) | Runtime profile supplies device/workload/performance targets and T-647–T-663 failure oracles. Actual compatible build locks/executable suites/measurements await authorization. |
| Push | Optional FCM + best-effort non-GMS polling (D-269), routing/suppression defaults (D-282) | Notification profile fixes registration, data-only hints, coalescing/dedup/routing/cadence; delivery never establishes game correctness/read progress. |
| Card source | MTGJSON records/startup check + Scryfall lazy art (D-280), arbitrary client definitions retained/shared by server (D-271) | Downloader profile completes update/mapping/cache/safe URL handling. Provider/upstream permission/attribution remains release clearance, not an undecided provider. |

These selections are not permission to start a scaffold or install packages.
See [notifications/background work](notifications-and-background-work.md) for
D-269's optional-provider boundary versus proposed polling/scheduling mechanisms.

### Architecture completion order — D-263

1. Specify command/sync envelopes, ordering, request/result identities and recovery.
2. Define server schema/transactions and client durability/reconciliation, including
   tree revisions, snapshots, random inputs and migrations.
3. Complete scene/input/accessibility boundaries and normal-app gesture testability.
4. Define security/session mechanisms, external adapters and application container/
   recovery interfaces; production deployment/backups are external under D-272.
5. Pin compatible versions and CI/device/performance budgets.

This is technical specification work, not a new implementation plan or gate pass.
Exact reference devices/resources, production catalog/art rights, release mechanics
and retention/privacy expectations still need completion; unspecified details are not automatically fresh
product questions. D-272 settles Docker Compose deployability and assigns production
hosting/deployment/backups to a separate repository; do not keep VPS/provider/budget
selection or backup automation in this repository's implementation backlog.

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

### Client downloads and local catalog/art storage — D-270

**Confirmed:** obtain card records and artwork from public sources through client
downloads and store/reuse them locally. Do not require bundled licensed assets or
route ordinary catalog/art downloads through our backend. This does not require
downloading all images at installation or retaining every downloaded asset forever;
provider, initial download scope, update cadence and cache budgets remain design.

The backend still owns shared game state and retains essential per-game definitions,
face identities/defaults and recorded history under D-65/D-243/D-259/D-266. A public
catalog cache is not an authoritative private game. D-271 permits arbitrary
client-supplied definitions as accepted game data, with structural/security checks
but no official-provider authenticity requirement. This does not permit executing
supplied scripts or reinterpreting games from each device's newest catalog. Exact
validation/version-resolution protocol remains technical work. Public availability
is not a blanket content license. See [card data and artwork](card-data-and-art.md) for Cockatrice's current
sources and the selected MTGJSON/Scryfall downloader profile.

D-271 confirms lazy image downloads/cache, separate from required card records,
and client-supplied deck definitions retained by the service before game start.
The [definition contract](../03-contracts/domain-model.md#game-definitions-and-local-catalog-independence--d-271)
makes the service's retained game definitions available even when a recipient's
general catalog lacks an opponent's card. Deck imports already require resolved
entries under D-198, not all images or latest-catalog parity. A bounded startup
MTGJSON `Meta.json` check and Scryfall lazy art are selected by D-280; the
[catalog design](card-data-and-art.md#selected-providers-and-startup-update--d-280)
distinguishes that optimization from game-definition correctness.

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

Protocol v1 now provides the concrete human-readable wire/operation contract.
As the first contract artifact of an explicitly authorized implementation slice,
encode it in OpenAPI/JSON Schema and check round-trip examples **before** building
dependent endpoints/clients. This mechanical encoding is not permission to choose
different product semantics. It must cover:

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
Keystore-backed Android credential protection. D-268 requires permanent-login
convenience through a non-expiring revocable token without saved passwords, not
idle/absolute session expiry. See [authentication/session design](authentication-and-sessions.md)
for selected token/reset/security mechanisms, including D-279 reset-time revocation; no new
account-management screens or arbitrary provider defaults are approved. End-to-end
encryption remains unselected. D-272 permits owner-operated Compose deployment,
possibly initially on a Raspberry Pi, but does not add an in-app server browser,
multi-server account federation or packaged end-user hosting UI.

D-249 excludes credentials from card-text trimming: preserve password whitespace
and Unicode consistently across registration/login/reset. Specify account email/
username normalization and password strength separately, with Unicode and paste
fixtures and no secret logging. No provider/helper default may silently normalize
passwords differently at different entry points.

## 7.6 Reliability and operational contracts

Proposed correctness targets:

- No acknowledged shared operation is lost under the declared service recovery
  model. The backup/recovery model must quantify its actual limits.
- D-278 permits loss of outgoing work/unreported exposure/last unsaved input on
  process death. Accepted server records and atomic accepted replica/cursor saves
  remain durable. Restart synchronizes the server; no outgoing journal replay.
- A retry never duplicates a committed action.
- A partially failed notification does not roll back game state.
- Failed migrations preserve original durable data and offer a recovery route.

### Container delivery and external operations — D-272

**Confirmed:** the important deployment requirement is **Docker Compose
deployability**. The owner may initially try a Raspberry Pi; a VPS, cloud provider,
managed database or Kubernetes is not required. **Production deployment and backups
are handled in a separate repository.** Do not create that repository, production
Compose manifests, host provisioning, certificate/proxy setup, backup jobs or
restore automation as application-project deliverables here.

This repository retains the application side of the boundary:

- Container packaging/build contract, explicit image versions, supported runtime/
  architecture information and documented configuration/secrets inputs. Do not
  embed hostnames, production credentials or operator-specific volume paths.
- Service interfaces needed by Compose: database connection configuration, ports,
  health/readiness, persistent-state inventory, graceful stop/restart behavior and
  the supported migration/startup contract. Exact names/mechanisms remain design.
- Application-owned Flyway migrations, supported schema/client/image compatibility
  and recovery correctness, not the operator's deployment/migration orchestration.
- The D-262/D-263 Docker Compose **E2E** environment and Testcontainers integration
  tests remain application-repository work; they are not production deployment.

For a Raspberry Pi trial, recommend a 64-bit OS and compatible ARM64 JVM/PostgreSQL
images. Check the eventual image/dependency architecture support and measure
memory/storage/performance on the actual hardware; no Pi model, 32-bit ARM support,
capacity guarantee or completed multi-architecture build matrix is selected here.

The external repository owns host/network/TLS/proxy/secrets provisioning, production
Compose wiring, deployment/rollback execution, monitoring/alert infrastructure and
backup/restore scheduling/storage/retention/rehearsals. Application requirements
still constrain that integration: do not lose essential definitions/history/request
outcomes by treating checkpoints as the only durable data. State and required
secrets must be inventoried; externally agreed recovery point/time limits and
restore evidence cannot be inferred from local commit durability. Moving backup
work out of this repository is not a zero-data-loss/availability promise or a waiver
of privacy/erasure requirements. Earlier Caddy/VPS/WAL-backup suggestions are not
selected operational tools/policies by this focused scope decision.

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
- Application persistent-state/secret inventory and migration/compatibility/recovery
  interface for the external operations repository. Production backup frequency,
  recovery point/time limits, restore/deploy/rollback/incident procedures belong
  there (D-272), not new application-repository automation tasks.
- Metrics for sync lag, failed/conflicted operations, replay integrity, database
  pressure, notification failures, and errors without leaking game content.
- D-273 application artifact/signing/release mechanics, compatible upgrade paths
  and crash-reporting consent; signed APK/manual update/API 29 direction is settled,
  production host/operator cost and provisioning are external under D-272.

## 7.7 No implementation shortcuts across specification gaps

Do not resolve open questions by quietly adopting a library's defaults. In
particular, database cascade deletion, notification delivery semantics, conflict
merging, random generators, and Android backup settings can violate this
product's recovery and trust model. Their behavior must be specified explicitly.
