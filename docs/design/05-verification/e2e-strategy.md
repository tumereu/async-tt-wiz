# Full-stack Android test strategy

## Confirmed direction — D-243/D-244

- Game/card definitions, descriptive metadata and artwork are dynamically loaded
  data, not a licensed catalog compiled into the Android app or backend.
- Automated tests use dummy game data and original synthetic artwork, not real
  card names, text, images or a live third-party catalog.
- Acceptance is exercised through full end-to-end scenarios: a running real
  backend and the normal native Android application, including both players.
- Run the backend and database in Docker, with at least one normal Android app
  connected to that environment (D-262). Single-device smoke coverage does not
  replace the two-player journeys or same-account multi-device scenarios.
- Reset backend state between every independent test, including user data.
- Convenience calls may initialize users and saved decks before the scenario.
  Otherwise perform user actions only through the UI.

These are confirmed requirements, not an implementation authorization. The
fixture examples, journey decomposition and harness mechanisms below are a design
to implement later. No Android automation framework, backend stack or provider
has been selected. The [test architecture](../04-technical/test-architecture.md#recommended-full-stack-scenario-system--d-262-environment)
recommends Docker Compose and host-side Kotlin/JUnit scenarios driving normal
Android apps through Appium/UiAutomator2; those tools remain proposals.
See [dynamic data](../04-technical/architecture-and-operations.md#dynamic-game-data--d-243)
and the [product contract](../03-contracts/product.md).

## Real system boundary

Run the normal application with its normal authentication, import, rendering,
gestures, local storage, synchronization, request recovery and history code. Point
it at an isolated running backend and its test catalog/image source using
environment configuration. Do not substitute an in-memory fake backend, mocked
sync/replay, alternate test screens, an authentication bypass, or direct calls to
application/domain methods. The same app behavior and backend validation must
run as for users; test instrumentation may drive and observe UI but not replace it.

Independent entry/deck smoke scenarios may use one Android instance. Two-player
journeys use separate Android app instances/devices/emulators. Same-account
multi-device scenarios add a further instance. An opponent's actions must also
use the Android UI, not an API bot. Reusable UI steps are welcome: helpers such
as “log in,” “invite,” “ready,” “draw” and “drag” still perform the real gestures,
text entry and controls, with no hidden state injection.

Required outcomes are asserted through visible app behavior on the relevant
instances, including inspection and history screens. Backend logs and read-only
diagnostics can explain failures; they cannot replace the UI acceptance result.
Tests of APIs or internal state directly belong to the additional lower-level
test layers, not a passing E2E scenario.

## Initialization exception and scenario boundary

Before the first scenario action, convenience calls may:

1. Create the test accounts with known credentials.
2. Create their named saved decks from the selected synthetic catalog, including
   mainboard/sideboard and supported deck metadata.

Use normal validation/storage invariants for those records. Helpers may not
create contacts, invitations, lobbies, Ready state, active games, placed cards,
history trees, Undo/Redo entries, or pending requests. Build those through the UI.
Do not use helpers to change users/decks halfway through a scenario, to revive
deleted data, or to bypass the feature under test. Registration, deck import,
rename, re-import and deletion tests perform those operations through the UI;
helpers must not pre-perform the operation being tested. Registration and initial
import create their records through UI; rename/re-import/deletion may start with
an existing initialized account/deck but must exercise the actual UI change.

A journey with many checkpoints is one test. It may include restarts,
disconnections, both players and several games returned to the same lobby. Do
not reset between its checkpoints: persistence/recovery is what it is testing.
Each independent test starts from a clean environment, never from a previous
test's game. Convenience initialization must be declared in the test definition.

## Isolation and reset lifecycle

Proposed harness lifecycle, implementing the confirmed per-test reset:

1. Provision or reset an isolated backend before each test, also after a previous
   failure. The empty baseline contains only the selected synthetic catalog and
   required service configuration, not accounts or games.
2. Clear all mutable test state: accounts/credentials/sessions, saved decks,
   contacts, invitations/lobbies/games, history and control stacks, operation IDs
   and stored outcomes, device registrations, jobs/outboxes and mutable caches or
   test mailbox/push deliveries. Quiesce old workers/connections so they cannot
   repopulate the baseline or deliver into the next test.
3. Start fresh Android app data for each independent test, including credentials,
   downloaded catalogs/images, games and pending recovery records. Do not clear
   app data during a restart/reconnect/relogin checkpoint within a test.
4. Load the pinned fixture dataset through the normal catalog delivery path, run
   the declared user/deck initialization, then drive the UI scenario.
5. Collect failure evidence before cleanup; stop the instances and clean up the
   backend. The next test's precondition reset remains mandatory even if this
   teardown fails.

Reset is harness administration, not an extra gameplay API. Keep reset and seed
capabilities inaccessible to production clients/deployments and never target a
real user backend. A failed reset/readiness check fails setup; do not run against
possibly dirty state. Parallel tests need separate complete backend state and
Android app instances; a database-only reset is insufficient if workers or other
stores are shared. Exact reset mechanism and service topology remain technical
choices.

## Synthetic dynamic catalog

Use a small versioned dataset of invented names, descriptive text, printing IDs,
faces and original generated/drawn art. Keep fixture art provenance/license clear;
do not copy real card layouts, logos or art into fixtures. Shared schema and the
normal delivery/import/image paths are the contract, not a test-only catalog
parser or built-in set of special-case card IDs.

Suggested fixture coverage:

| Fixture role | What it exercises |
| --- | --- |
| Ordinary cards in supported type categories | Common text imports, browser columns, contextual Stack destinations and game-local deck copies. |
| Two faces with different art and defaults | Face selection, cover/inspection, per-face Reset and recorded history. |
| Numeric, slash-pair, symbolic and absent corner defaults | Plain-text editing, shortcuts, blank metadata fallback and preservation rather than rules calculation. |
| Token definitions and Blank | Catalog search, creation, Clone, disappearance and last-live Stack inspection. |
| Similar names and multiple printings | Unknown/ambiguous import errors and explicit printing resolution without substitution. |
| Distinctive original images, plus missing/broken images | Successful real image loading, face/cover behavior and text fallback. |
| Catalog versions A and B | Dynamic replacement/update for new resolutions without silently rewriting existing game records/defaults. |

Provide at least a second dataset with different names/IDs/art to prove the app
does not depend on the primary fixture's identities. Supply all automated model,
protocol and UI tests with synthetic data too. Do not download licensed material
as test setup or depend on external catalog availability. This does not settle
production provider rights, attribution, redistribution or caching permissions.

## Initial full E2E journeys

Each row is an independently reset test family; variants are independent tests
unless the row explicitly describes continuity. Use synthetic data throughout.
T-IDs link to [existing acceptance outcomes](acceptance.md), not new gameplay.

| Journey | Setup exception | UI path and main checks | Existing coverage |
| --- | --- | --- | --- |
| Entry, import and first game | None; the two users register through UI. | Register/login → import a named deck with sideboard → correct an unknown/ambiguous import → choose cover → add opponent → invite/accept → prepare decks/starter → both Ready. Check own-Ready clearing, 20 life, turn 1 Untap, no automatic hand draw and no Sideboard access in play. | T-396–T-405, T-417–T-454. |
| Invitation lifecycle | Users and saved decks only. | Inviter prepares/readies before acceptance → recipient declines → inviter re-invites to same lobby → accepts → leave/reopen without cancellation → cancel an unstarted lobby. Check retained preparation and current-game listing. | T-414–T-416, T-448–T-463, T-542. |
| Manual play and result round trip | Users and saved decks only. | Invite/start → use mulligan actions → draw/move/tap → edit life/corner/counter/note → View/Reveal and inspect from both sides → suggest result → opponent catches up through final board changes and the singleton suggestion before its acceptance dialog → accepts → restored lobby with both unready → Ready/start a fresh game. Repeat arrival while earlier groups are paused and removed/inactive suggestion variants. Assert exact visible changes, immediate sync and no inferred game effects. | T-007, T-078–T-090, T-102–T-123, T-406–T-413, T-464–T-472, T-603–T-604. |
| Saved-deck copy isolation | Users and saved decks only. | Select decks in a lobby → edit/re-import, rename and delete saved sources through Manage decks → return to lobby/start → confirm selected preparation is unchanged. Exercise Replace and Save as new in separate variants. | T-428–T-432, T-491–T-492, T-542. |
| Combat and live Stack sources | Users and saved decks only. | Start and place cards through normal moves → create combat/Stack projections → edit through projections → move real source to Graveyard/Exile → create another ability there → remove effects. Check live write-through, normal resets, combat cleanup and independent effects. | T-220–T-245, T-545–T-546. |
| Token disappearance | Users and saved decks only. | Start → create a catalog token/Blank → customize/Clone → create Stack effects → change source again → Remove or move token to a disappearance destination → inspect surviving effects → remove one. Check last-live read-only information and no replacement source. | T-157–T-167, T-547–T-548. |
| Selective Undo/Redo | Users and saved decks only. | Start → make independent own/opponent edits → Undo/Redo → create a same-counter dependency and a referring event comment in separate variants → check blocked latest-own target/no skipping → verify ordinary own new play clears own queue and ordinary opponent play does not. Inspect the edited tree without an invented original branch; deliberate-switch resets are a separate journey below. | T-501–T-523, T-534, T-541. |
| Interruption and personal review | Users and saved decks only. | Start → publish an ordinary continuation → opponent reviews and deliberately continues from an earlier boundary → returning player watches/pauses/scrubs playback → inspect/activate retained nodes → test fork boundaries and deleted-cursor parent fallback. No API-created history. | T-337–T-348, T-493–T-500, T-524–T-528, T-535–T-537. |
| Connection loss and process restart | Users and saved decks only. | Start → perform a UI action with an externally delayed/lost response → disconnect → verify gameplay blocking/read-only downloaded review → restart/reconnect → observe automatic recovery once and stable recorded random outcomes. Preserve app data during this test. | T-475–T-482, T-532. |
| Locally sampled random results | Users and saved decks only. | Start → Roll die/Flip coin through normal UI with externally delayed responses → observe provisional results → compare both players' accepted history → lose a committed response/restart/reconnect. Verify the same sample/result once throughout, no independent server reroll, offline invocation or automatic replacement roll; do not inject a chosen outcome through helper calls. | T-169–T-171, T-181, T-612. |
| Same-account multi-device | Users and saved decks only. | Log the same player in on two normal instances, opponent on a third → start through UI → action on phone, Undo on tablet, Redo on phone → distinct concurrent Undo presses → normal independent edits. Check synchronized player-specific stack and no exclusive-device barrier. | T-483–T-485, T-529–T-533. |
| Logout privacy | Users and saved decks only. | As A initiate an unknown-outcome action or durably captured logged exposure not yet transmitted → Logout/process death → login as B → confirm no access/publication of A data → login as A and recover once. Do not clear app data/reset between checkpoints. | T-543, T-600–T-601. |
| Dynamic catalog and artwork | Users only; import decks through UI. | Start using catalog A → inspect art/faces/defaults → publish catalog B externally → resolve/import new definitions normally → reopen/review the existing game → simulate missing images and inspect text fallback. Repeat with the alternate dataset without rebuilding the app/backend. | T-023, T-122–T-124, T-549–T-550. |
| Bulk conflicts and normalized targeting | Users and saved decks only. | Through UI select/drop multiple cards; externally arrange both server arrival orders against opponent UI moves. Select a source and its effects, apply card edits/Clone versus occurrence removal. Open selected/unselected Actions and change selection during Set. Check exact mutated identities and partial authoritative placement, not only highlights. | T-554–T-557, T-566–T-571. |
| Retained future and Redo target fidelity | Users and saved decks only. | Build dependent/independent paths through UI, vary personal review, explicitly activate internal nodes, then Undo. In separate no-switch fixtures build P/Q/R counter/note sequence and remove R/P before Redo R. Inspect unchanged retained nodes and blocked stacks; do not carry a pre-switch Redo queue through activation under D-258. | T-558, T-562–T-563, T-609. |
| Exposure and incarnation recovery | Users and saved decks only. | Start → recorded inspection → offline replay versus blocked new covered inspection. In separate variants reject an online revealing prediction with external traffic control, restart/recover its exposure, then end/start fresh play while an old UI-originated request remains delayed. No forged request/history setup. | T-559–T-561, T-576–T-577. |
| Context/grouping and credential boundaries | None for credential variants; users/decks only for gameplay variants. | Register/reset/login with valid whitespace/Unicode passwords; exercise every reachable category-transition cell and longer prefix trace in the grouping matrix, including old/new X pings around a new tap group, a menu/request spanning a same-account tablet's intervening event and strict singleton ending suggestions. Assert server-derived grouping, menu labels, toggle records, numbering, mode and exact cursor—not only final groups; menus never bind groups. Rejected inputs do not require impossible accepted-event fixtures. | T-564–T-565, T-572–T-575, T-583–T-584, T-602–T-604; GM-01–GM-25. |
| Historical execution and reused preparation | Users and saved decks only. | Build live E, explicitly continue at earlier empty Stack with F, inspect both branches and retry; separately delay old UI Ready/preparation/cancellation through start/end/returned lobby, including multi-device and committed-loss variants. | T-579–T-582. |
| Inspection controls and event participation | Users and saved decks only. | Hold covered/multi-face cards on each side online/offline; use Inspect other side versus Flip. Build Undo/Redo before logged inspection and own hold, then inspect an inactive context and recover after restart. Check controls and filtering, not merely log presence. | T-072–T-073, T-559–T-561, T-585–T-587. |
| Live reads, ordered lowering and references | Users and saved decks only. | Through both UIs interleave face changes with Reset, source edits with Clone, arrivals/departures with Shuffle; perform ordered top/bottom/subset transfers, remove linked/selected tokens and review every valid boundary. No API-created tray token or four-member pile. | T-588–T-596. |
| Composite scope and partial outcomes | Users and saved decks only. | Use individual full paths outside quick windows, advance-and-close with absent/replaced area, moved-side Untap with tracking interleaving and captured-count mulligans. Assert bound side, relative processing, individual nodes and actual partial results. | T-597–T-599. |
| Server-owned state and deliberate history switches | Users and saved decks only. | Through UI build valid queues for both players, then activate/interruption/return and archive-and-return in independent variants. Compare UI-only navigation, metadata-only changes and no-op/rejected controls. Delay a switch response, create a newer Undo entry, then recover the original request without another reset. Vary client camera/menu/review state and optimistic edits; inspect shared convergence without transferring UI choices. | T-527–T-528, T-605–T-609. |

This is an initial journey inventory, not complete executable coverage of every
T-ID or a claim that any test has run. Keep scoped variants focused and map each
assertion to the current contract; older superseded scenarios do not override
newer rules. Additional account-reset/email and notification journeys must also
cross the normal app/backend paths; select their service harnesses when the
underlying technical delivery is designed.

The [grouping matrix](grouping-matrix.md) is the full planned category/prefix
expansion requested for playtesting. Each independent variant resets; continuing
prefix checkpoints within one variant do not. The real UI must create every
measured event. Observing intermediate nodes via personal review adds no gameplay
event; return to the same live context before extending a prefix. Fault controls
can delay UI-originated requests to establish the declared interleaving, never
forge a request, exposure or history branch.

## Fault controls, synchronization and evidence

Infrastructure can delay/drop traffic, stop/restart the app or service, publish
fixture catalog versions, or expire a session to arrange an external condition.
Those controls are not additional user/deck initialization permissions: never
use them to move a card, mutate history, forge a committed outcome or insert an
in-flight request. Initiate all user commands in the UI. Observe actual recorded
random outcomes; test legal ranges and stable replay/recovery, not a hard-coded
shuffled hand or resampling until an expected result appears.

Wait for observable UI conditions with bounded timeouts, not arbitrary sleeps or
directly forcing sync completion. Race variants must control delivery sufficiently
to establish the arrival order under test and verify both visible outcomes. Keep
screenshots/video, UI hierarchy, app/backend logs, fixture version, device/app
versions and relevant request correlation for failures, without real credentials
or personal data. Never turn a failure into a pass by silently rerunning it.

Model/property/protocol/fault/compatibility/operational tests remain valuable
alongside E2E, but do not replace these real-backend/normal-app acceptance paths.
T-610's conditional checksum/verified-base cases are technical protocol/model
checks for the proposed mechanism, not an approved hash format or a substitute
for UI-observed convergence in T-608/T-609.
Every-node invariants/exact inverse and batch-versus-incremental grouping also
need lower-level property tests. T-578's CPU/memory/payload/fan-out measurements
are technical performance tests, not an invented product Stack-count limit or a
claim that UI timing alone proves bounded replay cost.
Future implementation slices must include traceable full-stack UI scenarios;
tooling, device matrix, timeout budgets and CI scheduling are later technical work.
