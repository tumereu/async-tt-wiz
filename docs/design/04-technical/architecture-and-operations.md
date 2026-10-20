# 7. Architecture and operations

Status: future technical-design proposals unless explicitly confirmed. Native
Android and D-243's dynamic game-data boundary are confirmed; no particular stack
or catalog provider is selected. D-115 places current work in stage 1 behavior
specification;
adversarial behavior review is stage 2, UI drafts/decisions stage 3, and iterative
implementation stage 4+. Do not treat these technical proposals as current-stage
decisions or require every schema/library choice before behavior work can finish.

## 7.1 Proposed boundaries

### Android application

- Native UI and platform integration.
- Pure domain/replay module for state transitions and structural validation.
- Board/object/container primitives plus explicitly specified manual-play
  conveniences, including D-112–D-114 projections/assignments/Stack quick actions.
  No automatic card-text rules, legality validators, or inferred game effects.
- Local repository for cached game records, drafts, queue, and review state.
- Synchronization worker and transport adapter.
- Card catalog/deck parsing adapter and image cache.
- Notification/deep-link integration and account/credential management.

The domain layer must not require Android widgets, network availability, or a
running service to replay a downloaded game. Reducer semantics must match the
service's validation semantics through shared code or cross-platform fixtures.

### Shared service

- Authentication, invitations, and membership.
- Game history/control transactions and structural validation.
- Incremental sync and operation-result lookup.
- Durable notification outbox and delivery workers.
- Card data distribution/cache integration where needed.
- Retention, administration and observability; no user-facing deck/game export
  deliverable is implied (D-241/D-214).

A modular monolith is the proposed starting point; independent microservices
are not needed simply to separate these responsibilities.

## 7.2 Technology decisions still to approve

| Area | Proposed candidate | Decision still needed |
| --- | --- | --- |
| Android UI/language | Kotlin and Jetpack Compose | Minimum Android API; exact libraries/versions. |
| Local persistence | Transactional SQLite via Room or equivalent | Schema, migrations, encryption requirements. |
| Background work | Android-managed persistent work scheduling | Retry policy, foreground behavior, battery restrictions. |
| Shared service | Single deployable application | Runtime/language, hosting and operator model. |
| Shared database | Transactional relational database | Engine, schema, backup/restore and capacity. |
| Transport | Versioned HTTPS API; optional realtime hints | Exact resource/command model and streaming choice. |
| Push | Platform-compatible notification delivery | Provider, device registration, privacy and failure policy. |
| Card source | Generic/custom definitions; MTG catalog adapter for the initial use case | Provider permission/terms, descriptive fields, update policy. |

These candidates are not permission to start a scaffold or install packages.

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
- Context messages, personal review state, result acceptance/fresh play and
  deck/catalog access; no cross-device review-progress/read-receipt service.
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
credential/session storage and exact reset mechanics remain technical design or
explicit behavior gaps, not an unselected login method. End-to-end
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
