# Test architecture

## Status and contract boundary

Initial technical proposal; test frameworks and the backend runtime/database/
migration stack are not yet selected. Kotlin Multiplatform is selected for the
client/shared core (D-260).
jOOQ and the same client/server state types/processing code are selected (D-261),
with locally generated die/coin inputs permitted and server acceptance authoritative.
No executable test suite or application is authorized by this document.

The [E2E strategy](../05-verification/e2e-strategy.md) owns the confirmed D-243/D-244
boundary: normal Android app, real running backend, synthetic dynamic data/art,
both players through UI, clean state between independent tests and setup helpers
restricted to users/saved decks. These architectural choices must preserve it.

## Proposed layers and tools

| Layer | Candidate tools | Purpose |
| --- | --- | --- |
| Shared deterministic core | `kotlin.test`, JVM JUnit Jupiter and property/model-based testing tools to select. | The same command processor/state objects on client and server; transitions, relevant-state tuples, history edits, grouping, identity/reference invariants and supplied/recorded-randomness handling. |
| Shared client coordination | Coroutine test scheduler, fake transport/storage at this lower layer only. | Prediction/reconciliation, ordered delivery, ambiguous outcomes, durable recovery, account/incarnation fencing and process-restart sequences. |
| Server integration | jOOQ (selected); Spring Boot test support, JUnit Jupiter, Testcontainers PostgreSQL if the recommended backend is selected. | Real queries/constraints/transactions, schema-to-shared-object mapping, concurrent requests, idempotency, outbox and API behavior. |
| Migrations | Same Flyway migrations against real PostgreSQL, if selected. | Empty install, upgrades from retained supported versions, data preservation and incompatible/failed migration handling. |
| Android components | Compose UI testing plus real canvas pointer gestures/accessible semantics. | Menus, text input, gesture arbitration, scene targeting, accessible alternatives and drawing integration. |
| Full-stack acceptance | Coordinated instrumentation on multiple Android instances; Compose testing and UI Automator as candidates. | Full UI journeys through a running service/database, including reconnect, restart, multi-device and concurrent activity. |
| Performance | Android Macrobenchmark/frame metrics and targeted core/server benchmarks. | Frame pacing, input response, image load, history catch-up and bounded Undo/Redo work on declared reference workloads. |

Do not substitute an in-memory database such as H2 for PostgreSQL integration or
migration coverage. Lower-layer fakes are useful, but cannot count as D-244 E2E.
Run shared-core fixtures on Android/JVM and on each additional supported target
when that platform is introduced; a JVM-only pass is not cross-platform proof.

## Shared-engine and random-input verification — D-261

- Verify dependency boundaries so prediction and server execution invoke the same
  shared processor and state types, not merely implementations passing similar
  fixtures. jOOQ/schema types must not leak into the shared core.
- Compare semantic transitions, mandatory cleanup, grouping and replay on equal
  state/command/random inputs. Interleaved current-state execution can legitimately
  differ from prediction; verify reconciliation without double application.
- In lower-layer tests, control the random-input adapter and count acquisitions.
  One new connected die/coin request samples once; prediction, server processing,
  retry, restart recovery and replay retain that input/result. The server validates
  its range/shape and never independently rerolls it. Invalid/rejected input cannot
  commit a result or trigger an automatic replacement invocation.
- In normal-app E2E, observe the locally displayed roll/flip while delaying its
  response, then compare accepted history and the other player's UI. Reconnect
  and restart must preserve the same outcome with no duplicate event. Do not add
  a test-only result picker or set game outcomes via a helper API.
- Prove uncontended N-card/minus-one mulligans complete their intended draws;
  independently test changed-membership partial outcomes. The proposed shared
  Shuffle-input algorithm needs deterministic cross-target/canonicalization tests
  before adoption; code sharing alone does not select or prove that protocol.
- Test jOOQ schema generation and actual queries against the same migrated database
  used by integration tests; mapping round trips preserve shared semantic state.

See T-599 and T-611–T-613 in [acceptance](../05-verification/acceptance.md).

## Canvas-specific verification

Full-canvas rendering removes the automatic per-card widget tree. Provide normal
application accessibility semantics for objects/actions and enough stable UI
identity/geometry to locate visible targets. Drive actual pointer gestures for
drag, snapping, overlapping-card selection and multi-touch arbitration; invoking
an accessibility action alone does not prove those gesture paths work.

Assert visible outcomes through the rendered table and ordinary inspectors/history
screens. Supplement with render/image assertions where useful, without making all
tests brittle whole-screen pixel comparisons. A semantics label alone does not
prove the card was drawn correctly. Read-only diagnostics can explain failures,
not replace the user-visible acceptance oracle. Do not add hidden test-only action
dispatchers, alternate screens or direct domain calls to make E2E easier.

## Harness and failure evidence

Coordinate two normal Android instances, adding a third for same-account/device
cases. Isolate the whole backend environment per independent test, including
workers, queues, sessions and external-service test deliveries. Testcontainers is
a proposed provisioning tool, not a substitute for the E2E reset contract.

Use external traffic/process controls for lost responses, disconnects, service
crashes and competing arrival orders, without manufacturing gameplay state through
test APIs. Preserve app/backend state within restart/recovery checkpoints. Gather
screenshots, UI traces and content-safe logs before cleanup; failed readiness/reset
fails setup rather than permitting a test against uncertain state.

## Remaining technical work

Select exact libraries/versions, multi-device runner/orchestration, real canvas
gesture/observation APIs, service/reset provisioning, deterministic race controls,
CI/device matrix, synthetic fixtures and performance budgets. Map tests to stable
acceptance IDs and the grouping matrix; do not equate this tool proposal with
completed coverage or resolution of the outstanding specification review findings.
