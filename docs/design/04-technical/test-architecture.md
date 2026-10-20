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
D-272 additionally requires production Compose deployability but assigns production
deployment/backups to a separate repository. This E2E Compose environment stays in
the application project; it must not depend on that repository's production data,
host paths, credentials or backup jobs. Share the application image/runtime contract,
not a production stateful environment. Container/configuration/migration/restart
verification remains here; operational backup/restore/deploy rehearsal belongs
there against the documented application interface.
Do not let Compose and Testcontainers independently own/reset the same E2E environment.
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
  independently test changed-membership partial outcomes. D-274 selects shared
  versioned Fisher–Yates/unbiased sampling over canonical member IDs and a saved
  fresh client seed. Specify golden vectors for every supported generator/seed/
  ordering/algorithm version, then verify cross-target equality, input validation,
  empty/single/many-card permutations and bounded sampling without modulo bias.
  No unspecified Kotlin/library-default shuffle may replace that contract.
- Verify a retry/prediction restart/app upgrade retains seed/algorithm identity;
  recorded history/Redo uses the accepted concrete permutation without fresh
  entropy or later-member substitution. Distinct new invocations acquire fresh
  inputs, including after Undo; equal outcomes by chance remain valid.
- At game start, verify server-acquired independent Library inputs and Random
  starter resolution only at the start transition. Repeated option selection is
  not a roll. Recover a committed start after response loss without more setup
  randomization, life reset or extra client presence/confirmation. New incarnations
  with Random selected resolve afresh; unchanged defaults/option retention remain
  separate. This is not statistical proof of honest client randomness or anti-cheat.
- Test jOOQ schema generation and actual queries against the same migrated database
  used by integration tests; mapping round trips preserve shared semantic state.

See T-599, T-611–T-613 and T-634/T-635 in [acceptance](../05-verification/acceptance.md).

## Revision/checkpoint verification — D-264

Test missing/duplicate updates, local state/cursor transaction crashes and a silent
missing final update discovered by periodic server-head checks. Under restored
connectivity/access, accepted replicas must converge without relying on a later
gameplay event or a mandatory checksum chain. Separately corrupt materialized
state while preserving its revision and verify checkpoint state-digest detection
and safe rebuild. Compare only matching explicit scopes/boundaries/revisions;
UI/predictions and unrelated historical records are not part of a current-state
digest proof, even when D-276 has downloaded the complete tree.

Measure routine incremental transfer against changed data rather than full history
size; D-276 initial/full recovery intentionally transfers the complete retained
current-game tree. Large-tree fixtures belong to lower protocol/model tests; normal-app E2E
retains real UI-created history and recovery assertions. T-614–T-616 specify these
outcomes; T-610 remains conditional on an optional older event-chain mechanism,
not the selected baseline. Exact digest/encoding/cadence remains technical design.

Under D-265, test a client below the retained revision floor, missing intermediate
patches and pruning during a paged baseline transfer. Baseline-required recovery
must be coherent, preserve original requests/exposures/UI and complete all retained
history/branch pages under D-276. Partial staging cannot be marked complete or
silently left as default lazy branches. Original committed request outcomes remain recoverable independently from
delta availability. T-617/T-618 cover these obligations; exact window budget and
lease mechanisms now use storage mechanics. SQLDelight migrations preserve accepted
records; D-278 permits outgoing-memory loss, not provisional state restoration.
Server queries/migrations exercise
the accepted relational/versioned-payload layout, not a recursive whole-tree blob.

D-266 checkpoint-eviction variants delete only derived checkpoint caches, then
rebuild the same retained boundary/baseline from durable root/definitions/history.
Verify unchanged recorded outcomes and history access, with no new random sampling
or loss of request/exposure recovery. Checkpoint existence must not be required
for correctness after the rolling delta window has been pruned.

D-276 full-download variants include inactive, archived-retained and hidden
alternatives plus futures beyond an internally selected shared pointer. After a
complete coherent transfer, disconnect and review every retained continuation via
normal UI without new history fetches or shared activation. Check initial/root
data, definitions and control/reference payloads, not just visible branch counts.
Images remain separate/lazy and current-state checkpoints do not replace history.

At lower protocol/performance layers cover one long path with short offshoots and
heavily branched outliers exceeding a presumed `<2x` ratio. Verify compressed/chunked
streaming/staging, interruption/resource failure, coherent concurrent-change
catch-up and no full-board-per-node duplication or routine full-tree retransmission.
Exact budgets/formats/transfer leases remain to specify; a workload assumption is
not permission for a history cap. T-640/T-641 supplement corrected T-616–T-618.

## Canvas-specific verification

D-267 accepts the shared scene geometry/gesture-coordinator architecture and
corrects normal gestures: single tap opens Actions, double tap taps/untaps once.
T-619 verifies normal dispatch, selection binding, animated visible hit bounds,
camera/accessibility agreement and no per-frame requests. D-281 confirms the
Select/Ping two-independent-taps T-620 variant, no longer conditional. Count physical
touches accurately in budgets.

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

## Optional push and polling verification — D-269

Exercise the normal app/backend without Google Play services or FCM configuration;
core login/game sync must not depend on either. Include a non-Google Android
device/emulator in the notification/platform matrix, not only Google-enabled FCM
tests. In normal-app journeys, create invitations/opponent activity through UI and
observe actual OS notifications from the real polling path; server logs alone do
not prove notification display. Real FCM tests, where configured, need a compatible
Google-enabled device and cannot replace polling coverage. Use synthetic data.

At lower layers test scheduler constraints/retries, bounded summary/cursor reads,
deduplication across push/poll overlap/restarts and account-session fences on
already-running callbacks. Platform tests cover notification permission/channels,
idle/restricted execution and foreground catch-up with all background delivery
absent. A controlled lower-layer scheduler can prove logic, not guaranteed Android
wall-clock delivery. No production exact-alarm permission or shorter hidden polling
loop may be added simply to make tests timely. T-486–T-490 and T-622–T-624 record
the expected outcomes; this is verification design, not executed coverage.

## Client definition and image verification — D-271

Use normal client downloads/import/preparation against synthetic catalogs with a
record present only on one side; the recipient obtains the accepted game definition
from the real backend. Also create a new catalog token during play, restart and
exercise stale-catalog recovery. Verify lazy image requests/cache reuse and text
fallback independently of required game records. Different source versions cannot
silently change accepted faces/defaults/history. T-627 owns this UI/backend journey.

At lower layers test arbitrary client-supplied definitions without official-source
verification, differing content under colliding external IDs, structural/resource
rejection, safe text/URL handling and incomplete-definition transfer recovery.
T-628 does not authorize an in-app authoring editor or test-only card creation API.
D-280 confirms the startup MTGJSON metadata check (T-629); synthetic
source fixtures test changed content date/version, bounded checks and atomic update
failure without relying on the real public service. No executable coverage is
claimed by these specifications.

## Android release and compatibility verification — D-273

Exercise signed APK installation/manual in-place upgrade on the accepted API 29
floor and representative later phone/tablet versions, including a non-Google
device/emulator. Exact matrix remains to define; a newer automation driver's
requirements cannot silently drop API 29 from app coverage. Use native/platform
checks where needed rather than claiming an untested minimum.

Preserve synthetic app/backend state across upgrade checkpoints: existing valid
session, downloads, accepted definitions, UI/personal state and original pending
request/exposure recovery. Upgrade with compatible signing/package identity,
verify SQLDelight migrations, then recover an actual lost-response command once.
Never uninstall/clear data to make this test pass. Failed/interrupted migration
paths retain recoverability and must not silently wipe the database.

Run compatible different APK versions against the same backend/game. Separately
exercise an unsupported protocol/semantic version and verify safe update-required
handling before mutation, preserving original pending records for post-upgrade
recovery. Lower-layer decoder/version/migration tests supplement normal-app E2E;
no forced re-login, fresh random sample or duplicate invocation is inferred from
the upgrade. T-631–T-633 specify coverage, not executed tests or build automation.

## Durable command admission and recovery verification — D-275

With real PostgreSQL, distinguish inbox receipt from final execution/result. Stop
workers after admission, crash before/during/after command commit and lose replies;
verify one durable slot, ordered resumption, atomic state/result/revision/outbox and
no false acceptance. A duplicate final rejection/no-op is the original result, not
a fresh chance against newer state. Different semantic payload/scope under the same
identity fails without overwriting pending/final data; reauthentication/connection
headers alone do not change action intent. Lower-layer tests may inject malformed
protocol inputs, but normal-app E2E cannot manufacture commands/history via helpers.

Race obsolete/new logical streams against admission and worker execution, keeping
another same-account device active. Replaced streams cannot admit new work; their
already-admitted records survive and process under normal lifecycle/access checks.
Resume cannot let a newer unadmitted local suffix overtake older recoverable work,
or introduce stop-and-wait for ordinary pipelined traffic. Test cross-player ingress
interleavings, multiple independent games and per-command rejection without whole-
queue cancellation. Sequence allocation alone is not evidence of ordered ingress.

Normal-app/backend journeys use UI-originated online actions plus external traffic/
worker/process fault controls to verify receipt/pending/final distinction, restart,
same-ID recovery, original random inputs and old-incarnation/preparation fences.
Keep app/backend data throughout recovery checkpoints; no test-only queue/receipt
screen or injected action to make observations easy. T-636–T-639 supplement existing
ordering, lifecycle and outcome scenarios; exact controls/limits remain to design.

## Prediction, modes and endpoint animation verification — D-277

At lower coordination layers enumerate result-before-delta, delta-before-result,
duplicate result/update and independent-interleaving traces against accepted base,
immutable request journal, active/withdrawn prediction flags and displayed target.
Use shared applicability/guard processing, not a different conflict predictor.
Verify an expected M1 result after M1/M2/M3 predictions leaves the M3 target intact;
remote work is incorporated before re-evaluating the active overlay. Dependent
guard failures withdraw later predictions, while independent/originally applicable
later requests remain. Retain withdrawal separately in memory from request/outcome;
D-278 restart drops the entire overlay/journal and syncs server, never resurrects
cancelled predictions or recreates forgotten commands/exposure.

Test a locally withdrawn move accepted later: same committed-update path as remote/
other-device work, original author/ID, no speculative reactivation from intervening
applicability, result suppression, duplicate command or restored old suffix. Include
live and playback arrivals; a rejection of an already-withdrawn effect has no second
reverse animation. Protected random/exposure records remain unchanged.

Normal-app E2E originates all moves/notes/interruptions through actual UI and uses
external traffic controls to arrange the stated traces. Use rendered-frame/geometry
evidence to verify no acknowledgement-only excursion toward older positions, not
just eventual final-state assertions or diagnostic logs. Keep real animations on;
exact easing/duration is not yet an oracle. Native animation/scene tests supplement
frame evidence and verify retargeted drawing/hit/accessibility geometry.

Background/foreground with already-downloaded but unpresented changes must enter
grouped playback from saved presentation progress, not the sync cursor. Test paused
cursor preservation, open-group extension, concurrent catch-up/live handoff and
deliberate continuation versus mere pause/scrub. In live, remote batches apply
immediately; in playback, compute per-group endpoints even if many groups arrived
together. Keep every underlying node/reference/exposure boundary available for
exact navigation, including net-zero visual changes. T-642–T-646 supplement the
corrected existing D-177/D-180/D-185 scenarios; no executable coverage is claimed.

## Harness and failure evidence

T-647–T-663 and the completed protocol/storage/runtime/auth/catalog/notification
profiles supply exact crash-loss, parameter, format, device/performance and failure
oracles for this batch. [Random v1 literal vectors](../05-verification/random-v1-vectors.md)
are independent fixtures. Earlier unspecified encoding/cadence/budget labels above
are completed by those profiles, not permission to adopt library defaults. No
executable suite or measured result exists yet.

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
