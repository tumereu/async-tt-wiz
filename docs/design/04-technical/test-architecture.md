# Test architecture

## Status and contract boundary

Technology directions are selected; exact harness mechanisms/versions remain
technical design. Kotlin Multiplatform is selected for the client/shared core
(D-260); D-263 selects Spring Boot/PostgreSQL/Flyway and the test stack below.
jOOQ and the same client/server state types/processing code are selected (D-261),
with locally generated die/coin inputs permitted and server acceptance authoritative.
**Selected environment — D-262:** E2E/scenario tests run the real backend and
database in Docker, with at least one normal Android app connected to them.
**Selected tools — D-263:** Docker Compose and host-side Kotlin/JUnit with the
Appium Java client/UiAutomator2 for E2E; Testcontainers for smaller server/database
integration tests and native Compose/instrumentation tests for focused components.
One-device smoke tests do not replace D-244's
two-player real-UI journeys or the additional device for multi-device cases.
No executable test suite or application is authorized by this document.

The [E2E strategy](../05-verification/e2e-strategy.md) owns the confirmed D-243/D-244
boundary: normal Android app, real running backend, synthetic dynamic data/art,
both players through UI, clean state between independent tests and setup helpers
restricted to users/saved decks. These architectural choices must preserve it.

## Proposed layers and tools

| Layer | Tools / remaining candidates | Purpose |
| --- | --- | --- |
| Shared deterministic core | `kotlin.test`, JVM JUnit Jupiter and property/model-based testing tools to select. | The same command processor/state objects on client and server; transitions, relevant-state tuples, history edits, grouping, identity/reference invariants and supplied/recorded-randomness handling. |
| Shared client coordination | Coroutine test scheduler, fake transport/storage at this lower layer only. | Prediction/reconciliation, ordered delivery, ambiguous outcomes, durable recovery, account/incarnation fencing and process-restart sequences. |
| Server integration | jOOQ, Spring Boot test support, JUnit Jupiter and Testcontainers PostgreSQL (D-261/D-263). | Real queries/constraints/transactions, schema-to-shared-object mapping, concurrent requests, idempotency, outbox and API behavior. |
| Migrations | Same selected Flyway migrations against real PostgreSQL (D-263). | Empty install, upgrades from retained supported versions, data preservation and incompatible/failed migration handling. |
| Android components | Compose UI testing plus real canvas pointer gestures/accessible semantics. | Menus, text input, gesture arbitration, scene targeting, accessible alternatives and drawing integration. |
| Full-stack acceptance | Selected host-side Kotlin/JUnit Jupiter scenarios using the Appium Java client and UiAutomator2 driver; Docker Compose for the Docker backend/database environment (D-263). | Normal APK on one or more real/emulated Android devices; coordinated full UI journeys, reconnect, restart, multi-device and concurrent activity. |
| Performance | Android Macrobenchmark/frame metrics and targeted core/server benchmarks. | Frame pacing, input response, image load, history catch-up and bounded Undo/Redo work on declared reference workloads. |

Do not substitute an in-memory database such as H2 for PostgreSQL integration or
migration coverage. Lower-layer fakes are useful, but cannot count as D-244 E2E.
Run shared-core fixtures on Android/JVM and on each additional supported target
when that platform is introduced; a JVM-only pass is not cross-platform proof.

## Recommended full-stack scenario system — D-262 environment

The recommendation in this section is **accepted under D-263**. Device/port
allocation, reset/failure-control mechanisms and exact compatible versions remain
to specify; acceptance of the tools is not a passing canvas integration test.

Use a **host-side Kotlin/JUnit runner**, separate from the application and from
Android instrumentation test execution. Its Appium Java-client sessions drive the
normal Android app via the **Appium UiAutomator2 driver**. The same scenario can
address player A and B explicitly, control external failure conditions, and collect
evidence from each device. Scenarios use readable UI-step helpers, not a second
application/game engine or a required Gherkin/BDD layer.

Selected topology; detailed wiring remains to specify:

```text
Host: Kotlin/JUnit scenario runner
  -> Appium/UiAutomator2 -> Android app A [and B / additional device]
                              -> exposed backend endpoint
Docker Compose environment:
  Spring Boot backend -> PostgreSQL
  synthetic catalog/art source; isolated test deliveries as needed
```

The runner and emulator need not be containers. Prefer running the Android emulator
on a host/CI worker with hardware acceleration; Docker is required for backend/DB,
not for nested Android virtualization. A physical Android device can use the same
Appium scenario interface. Versions must be compatible across Appium, its driver,
Java client, Android API and build tooling; do not silently raise the application's
minimum supported API to match an automation driver's minimum.

Use one instance for independent entry/deck smoke scenarios that need no opponent.
Use two for game setup, gameplay and synchronization, and a third for same-account
multi-device scenarios. Pin each session to its device ID and allocate distinct
driver ports. A one-device run must report its limited coverage, not label the
whole two-player acceptance suite passed or substitute an API-controlled opponent.

Docker Compose is selected for this reusable full-stack environment; keep
Testcontainers as the selected lifecycle tool for smaller server/database tests.
Do not let both mechanisms independently own/reset the same E2E environment.
Before each independent scenario, provision/reset isolated state, apply normal
migrations, verify actual backend/catalog/database readiness, initialize only the
permitted users/decks and start fresh app data. Preserve all state across an
in-scenario restart/reconnect checkpoint. Capture failure evidence before cleanup.

Expose only required service endpoints to devices; Docker service names resolve
inside its network, not automatically from Android. Use a configured host endpoint
or per-device ADB reverse mapping, including catalog/art URLs. Define test TLS/trust
configuration without disabling production security or bypassing authentication.

Appium does not understand painted canvas objects automatically. The normal app's
accessible object identities/bounds support discovery; actual W3C pointer gestures
exercise tap/hold/drag/selection/pinch paths. Assert through visible table rendering
and normal inspectors/history, not test-only scene/action APIs. Use bounded waits
for observable UI conditions rather than sleeps or requiring perpetual animation
to become globally idle. Keep animations enabled for animation-behavior scenarios.

Native Compose/instrumentation tests remain useful at the component/gesture layer.
Maestro was considered for simpler declarative UI journeys; Appium is selected here
for explicit multi-device orchestration, custom pointer sequences and process/
failure coordination. Real canvas integration still needs validation; no framework
automatically solves canvas targeting.

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
selected for smaller integration tests; Docker Compose is selected for E2E.
Neither is a substitute for the E2E reset contract.

Use external traffic/process controls for lost responses, disconnects, service
crashes and competing arrival orders, without manufacturing gameplay state through
test APIs. Preserve app/backend state within restart/recovery checkpoints. Gather
screenshots, UI traces and content-safe logs before cleanup; failed readiness/reset
fails setup rather than permitting a test against uncertain state.

## Remaining technical work

Specify compatible Appium/Kotlin/JUnit/Docker Compose versions, build integration,
multi-device orchestration, real canvas gesture/observation APIs,
service/reset provisioning, deterministic race controls,
CI/device matrix, synthetic fixtures and performance budgets. Map tests to stable
acceptance IDs and the grouping matrix; do not equate selected tools/proposed mechanisms with
completed coverage or resolution of the outstanding specification review findings.

References: [Appium architecture](https://appium.io/docs/en/latest/intro/),
[UiAutomator2 driver](https://appium.github.io/appium-uiautomator2-driver/),
[Android emulator networking](https://developer.android.com/studio/run/emulator-networking).
