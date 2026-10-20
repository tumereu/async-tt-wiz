# Client architecture

## Status and scope

**Selected — D-260:** Kotlin Multiplatform, Android first. Preserve the option of
future iOS and desktop clients using the same shared core. Desktop may have a
different UI and control scheme; neither extra platform is an initial-delivery
commitment. No application code or delivery-stage transition is authorized.

**Proposed rendering direction:** the owner expects the game area probably needs
to be entirely canvas-rendered for animation and card dragging. Use styled Compose
components for surrounding menus and ordinary application screens where useful.
This is not yet selection of a particular renderer/library or final screen layout.

## Proposed module boundaries

| Boundary | Responsibilities | Must not depend on |
| --- | --- | --- |
| Shared domain | State, deterministic recorded-event application/replay, structural invariants, relevant-state comparison and grouping semantics. | Android, Compose, Spring, database rows, networking or frame clocks. |
| Shared client coordination | Accepted replica, provisional predictions, reconciliation, ordered request delivery/recovery and repository coordination. | Touch-only gestures, screen geometry or Android services. |
| Shared data/protocol models | Versioned wire/data models, deck parsing and catalog interpretation. Keep wire compatibility distinct from internal model evolution. | ORM entities, platform widgets or a hard-coded licensed catalog. |
| Presentation | Board scene, animation/playback, camera, hit testing, selection, gesture arbitration and screen state. Share useful pieces without requiring identical mobile/desktop layouts or controls. | Ownership of authoritative game state. |
| Platform adapters | Durable local storage, transport execution, credentials, notifications, lifecycle and image loading. | Changing domain semantics to match platform/library defaults. |

These are logical boundaries, not a scaffold or a fixed number of build modules.
If Kotlin is selected for the server, it can consume the shared domain's JVM
target, not client coordination or the UI. Server authentication, execution order,
authoritative randomness and transactions remain server responsibilities.

## Canvas scene and Compose shell

- Render tabletop objects, projections, links, pings and table animations in the
  canvas game area. Menus, text editors, inspectors and other overlays can remain
  Compose components rather than being hand-painted controls.
- Maintain stable object identities and a scene/hit-test representation separate
  from domain state. Use the same geometry for drawing and pointer targeting,
  including camera transforms, overlap, clipping and visible-card selection rules.
- A drag's intermediate pointer positions are local presentation; completing an
  applicable action produces the specified request(s), not one request per frame.
  Predictions remain provisional. Preserve captured target scope and cancellation.
- Derive visual transitions from state differences and recorded history. Animation
  time must not determine event order, random results, grouping or synchronization.
- Supply meaningful accessible object/action semantics alongside the canvas.
  A painted object is not automatically accessible or discoverable by UI tests.
  This support is part of the normal app, not test-only controls or direct domain
  action injection; see [test architecture](test-architecture.md).

## State and lifecycle constraints

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

- Exact Compose/canvas APIs and reusable presentation scope.
- Local SQLite access/migrations, transport and image-cache libraries.
- Durable recovery versus UI-state restoration and account isolation.
- Version compatibility across Kotlin, Android tooling, Compose and future targets.
- Accessible canvas semantics, UI automation and performance verification details.

Reference: [Kotlin Multiplatform and Compose FAQ](https://kotlinlang.org/docs/multiplatform/faq.html).
