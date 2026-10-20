# Client architecture

## Status and scope

**Selected — D-260:** Kotlin Multiplatform, Android first. Preserve the option of
future iOS and desktop clients using the same shared core. Desktop may have a
different UI and control scheme; neither extra platform is an initial-delivery
commitment. No application code or delivery-stage transition is authorized.

**Selected — D-261:** client and server use the same Kotlin game-state objects
and command-processing implementation. Client prediction is execution of that
shared engine, not a separately written approximation. Die/coin randomness may
be generated locally; server acceptance still establishes the shared result.

**Selected — D-263:** Compose Canvas for the game area and styled Compose controls
around it; SQLite for client durability; Ktor Client/kotlinx.serialization for
network/protocol handling; Gradle Kotlin DSL for builds. WorkManager handles
appropriate Android background recovery, not ordinary foreground action delivery.
Use Keystore-backed credential protection. D-268 persists a non-expiring revocable
session token, not the password; reconnects/restarts/inactivity must not require
routine login. See [authentication/session design](authentication-and-sessions.md).
D-265 selects SQLDelight for SQLite access;
Coil is conditional on canvas integration. Exact versions,
scene/input design, storage schemas and final screen layouts remain incomplete.

## Proposed module boundaries

| Boundary | Responsibilities | Must not depend on |
| --- | --- | --- |
| Shared domain | The same game-state objects and command processing for client prediction and server execution, plus deterministic recorded-event application/replay, structural invariants, relevant-state comparison and grouping semantics. | Android, Compose, Spring, database rows, networking or frame clocks. |
| Shared client coordination | Accepted replica, provisional predictions, reconciliation, ordered request delivery/recovery and repository coordination. | Touch-only gestures, screen geometry or Android services. |
| Shared data/protocol models | Versioned wire/data models, deck parsing and catalog interpretation. Keep wire compatibility distinct from internal model evolution. | ORM entities, platform widgets or a hard-coded licensed catalog. |
| Presentation | Board scene, animation/playback, camera, hit testing, selection, gesture arbitration and screen state. Share useful pieces without requiring identical mobile/desktop layouts or controls. | Ownership of authoritative game state. |
| Platform adapters | Durable local storage, transport execution, credentials, notifications, lifecycle and image loading. | Changing domain semantics to match platform/library defaults. |

These are logical boundaries, not a scaffold or a fixed number of build modules.
The server consumes the shared domain's JVM target, not client coordination or
the UI. Server authentication, execution order, acceptance and transactions remain
server responsibilities. jOOQ database records are mapped to/from the shared
objects; they are not the game-state model or a second processing implementation.

## Shared processing and locally generated random inputs — D-261

Use one engine for command applicability, current-state reads, transitions,
mandatory cleanup, history/control effects and deterministic grouping. Both sides
use the same semantic input types and game-state objects. Adapters may translate
versioned wire or database records, but must not reimplement the game semantics.
Client prediction runs against accepted state plus valid pending predictions;
the server runs against its actual execution state in arrival order. Different
inputs caused by interleaving can produce different outcomes without either side
using different processing rules. Clients cannot certify their own acceptance.

Make nondeterministic execution inputs explicit: do not consult ambient clocks,
global PRNG state or platform-dependent collection iteration inside transitions.
Define identity/version mapping and server-only recording metadata separately so
client prediction can reuse the engine without fabricating authoritative events.

Separate random-input acquisition from deterministic processing:

1. For a connected new die/coin invocation, generate a fresh local sample once
   and bind it to that request. Normal UI invokes randomness, not a result picker.
2. Preserve that sample and original intent in durable request/recovery data
   before displaying the result. The same engine applies it provisionally and,
   if accepted, on the server. Exact wire encoding remains technical design.
3. The server checks access, lifecycle, operation applicability and valid input
   shape/range. It records the accepted result; it does not independently reroll
   a valid supplied sample merely because it is the authoritative service.
4. Retry, prediction recomputation, review and replay reuse the request input or
   accepted concrete result as appropriate. They do not consume another sample.
   Rejection is not permission to submit a fresh roll automatically. Preserve
   already-displayed exposure under the existing recovery rules.

This selects code sharing and permits local die/coin generation, not offline
action creation, client-defined authoritative events or a competitive randomness
proof system. Generator/entropy source, input encoding/versioning and supported
die bounds remain technical work. Server-originated lifecycle transitions use
the same engine with inputs supplied by the server; they do not require a client
to be present or grant the client authority over game start.

**Shuffle mechanism still proposed:** carry a fresh per-request random seed/input
and use a versioned shared permutation algorithm over canonical identities of the
execution state's current Library members. Do not submit the client's old member
list as the authoritative shuffled board. This can give identical uncontended
prediction/server order while handling arrivals/departures without a stale-member
lock. The canonicalization/algorithm/protocol must be specified and verified before
this mechanism is selected. See [synchronization](sync-and-storage.md#shared-processing-and-random-input-agreement--d-261).

## Canvas scene and Compose shell

D-267 accepts the scene/input architecture below. It does not select exact
geometry/timings, final screen layouts or the tentative Select/Ping double-tap
proposal; the [board contract](../03-contracts/board-and-controls.md#103-gesture-arbitration)
owns the corrected gesture meanings.

```text
Shared engine
  -> displayed state: live prediction OR chosen historical boundary
  -> scene/layout -> animation and camera transforms -> Canvas
```

The scene is a view, not a second game model. Visible objects carry stable semantic
identity, draw order/geometry, image/text references and current/target visual
transforms. Logical placement remains shared; pixel geometry, camera, previews
and animation progress remain personal. Desktop may use different layout/input
adapters while reusing the engine and useful scene primitives.

- Render tabletop objects, projections, links, pings and table animations in the
  canvas game area. Menus, text editors, inspectors and other overlays can remain
  Compose components rather than being hand-painted controls.
- Maintain stable object identities and a scene/hit-test representation separate
  from domain state. Use the same geometry for drawing and pointer targeting,
  including camera transforms, overlap, clipping and visible-card selection rules.
  An animated card is targeted where it is currently drawn, not at an invisible
  final destination. Accessibility bounds and real test gestures use that geometry.
- A drag's intermediate pointer positions are local presentation; completing an
  applicable action produces the specified request(s), not one request per frame.
  Predictions remain provisional. Preserve captured target scope and cancellation.
- Derive visual transitions from state differences and recorded history. Animation
  time must not determine event order, random results, grouping or synchronization.
  Semantic predictions may advance while visual transitions continue; sending
  never waits for animation, acknowledgements do not repeat it, and new results
  update visual targets without replaying unaffected work. Personal history
  playback remains distinct from current shared/predicted state.
- Supply meaningful accessible object/action semantics alongside the canvas.
  A painted object is not automatically accessible or discoverable by UI tests.
  This support is part of the normal app, not test-only controls or direct domain
  action injection; see [test architecture](test-architecture.md).

Use one gesture coordinator to arbitrate normal single-tap Actions versus
double-tap orientation, hold, drag, selection and camera gestures. Recognizing a
double tap must not first open/retract an Actions menu or emit a speculative
single-tap game edit. Immediate pressed/drag feedback is independent of completed
gesture dispatch. Captured identities, existing cancellation/selection rules and
server guards remain; do not silently redirect a drag after an incoming change.
The proposed mode-specific no-double-tap recognizer remains tentative in D-267.

Keep image decoding, database access and replay off the UI thread; cache suitable
artwork/text measurements, cull off-screen objects and avoid full-scene rebuilds
or large per-pointer allocations. Benchmark drag/zoom/crowded tables and concurrent
catch-up animation on reference devices; exact workloads/budgets remain to define.

## State and lifecycle constraints

The accepted replica, durable request/exposure recovery, disposable caches and
personal state are separate logical [storage categories](storage-architecture.md#client-storage-responsibilities)
under D-265. Fresh baseline/full sync replaces accepted replica data coherently,
not the original recovery records or all local application storage. Reconcile
pending predictions against the new baseline without inventing outcomes.

The [ownership matrix](../03-contracts/domain-model.md#state-ownership-and-request-boundary--d-259)
owns the accepted-replica/prediction/UI/recovery distinction. The
[sync design](sync-and-storage.md) owns durability and reconciliation mechanisms.
Do not interpret a reusable local core as offline game authority: new game actions
require a connection, while downloaded read-only review remains supported.

Keep replay, database work and image decoding away from the UI thread. Draw from
a prepared scene and cached artwork. Scope invalidation to affected visual state;
do not replay the full history or rebuild the whole scene for every pointer move.
Reference devices, scene/history sizes and measured frame/input budgets remain to
define in the performance plan; canvas alone is not a performance guarantee.

## Remaining technical work

- Exact Compose/canvas APIs, reusable presentation scope and conditional Coil
  integration, without treating library selection as proof of frame performance.
- SQLDelight schema/migrations, platform drivers, encryption and baseline/cache coverage.
- Ktor platform engines, kotlinx.serialization wire schemas and ordered WebSocket
  delivery/resume alongside HTTPS/JSON accounts/catalog/reads.
- Durable recovery versus UI-state restoration and account isolation.
- Version compatibility across Kotlin, Android tooling, Compose and future targets.
- Accessible canvas semantics, UI automation and performance verification details.

Reference: [Kotlin Multiplatform and Compose FAQ](https://kotlinlang.org/docs/multiplatform/faq.html).
