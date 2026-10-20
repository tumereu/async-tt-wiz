# Full-stack Android test strategy

## Confirmed direction — D-243/D-244

- Game/card definitions, descriptive metadata and artwork are dynamically loaded
  data, not a licensed catalog compiled into the Android app or backend.
- Automated tests use dummy game data and original synthetic artwork, not real
  card names, text, images or a live third-party catalog.
- Acceptance is exercised through full end-to-end scenarios: a running real
  backend and the normal native Android application, including both players.
- Reset backend state between every independent test, including user data.
- Convenience calls may initialize users and saved decks before the scenario.
  Otherwise perform user actions only through the UI.

These are confirmed requirements, not an implementation authorization. The
fixture examples, journey decomposition and harness mechanisms below are a design
to implement later. No Android automation framework, backend stack or provider
has been selected. See [dynamic data](../04-technical/architecture-and-operations.md#dynamic-game-data--d-243)
and the [product contract](../03-contracts/product.md).

## Real system boundary

Run the normal application with its normal authentication, import, rendering,
gestures, local storage, synchronization, request recovery and history code. Point
it at an isolated running backend and its test catalog/image source using
environment configuration. Do not substitute an in-memory fake backend, mocked
sync/replay, alternate test screens, an authentication bypass, or direct calls to
application/domain methods. The same app behavior and backend validation must
run as for users; test instrumentation may drive and observe UI but not replace it.

Two players use separate Android app instances/devices/emulators. Same-account
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
| Manual play and result round trip | Users and saved decks only. | Invite/start → use mulligan actions → draw/move/tap → edit life/corner/counter/note → View/Reveal and inspect from both sides → suggest result → opponent accepts → restored lobby with both unready → Ready/start a fresh game. Assert exact visible changes and no inferred game effects. | T-007, T-078–T-090, T-102–T-123, T-406–T-413, T-464–T-472. |
| Saved-deck copy isolation | Users and saved decks only. | Select decks in a lobby → edit/re-import, rename and delete saved sources through Manage decks → return to lobby/start → confirm selected preparation is unchanged. Exercise Replace and Save as new in separate variants. | T-428–T-432, T-491–T-492, T-542. |
| Combat and live Stack sources | Users and saved decks only. | Start and place cards through normal moves → create combat/Stack projections → edit through projections → move real source to Graveyard/Exile → create another ability there → remove effects. Check live write-through, normal resets, combat cleanup and independent effects. | T-220–T-245, T-545–T-546. |
| Token disappearance | Users and saved decks only. | Start → create a catalog token/Blank → customize/Clone → create Stack effects → change source again → Remove or move token to a disappearance destination → inspect surviving effects → remove one. Check last-live read-only information and no replacement source. | T-157–T-167, T-547–T-548. |
| Selective Undo/Redo | Users and saved decks only. | Start → make independent own/opponent edits → Undo/Redo → create a same-counter dependency and a referring event comment in separate variants → check blocked latest-own target/no skipping → verify own new play clears stack and opponent play does not. Inspect the edited tree without an invented original branch. | T-501–T-523, T-534, T-541. |
| Interruption and personal review | Users and saved decks only. | Start → publish an ordinary continuation → opponent reviews and deliberately continues from an earlier boundary → returning player watches/pauses/scrubs playback → inspect/activate retained nodes → test fork boundaries and deleted-cursor parent fallback. No API-created history. | T-337–T-348, T-493–T-500, T-524–T-528, T-535–T-537. |
| Connection loss and process restart | Users and saved decks only. | Start → perform a UI action with an externally delayed/lost response → disconnect → verify gameplay blocking/read-only downloaded review → restart/reconnect → observe automatic recovery once and stable recorded random outcomes. Preserve app data during this test. | T-475–T-482, T-532. |
| Same-account multi-device | Users and saved decks only. | Log the same player in on two normal instances, opponent on a third → start through UI → action on phone, Undo on tablet, Redo on phone → distinct concurrent Undo presses → normal independent edits. Check synchronized player-specific stack and no exclusive-device barrier. | T-483–T-485, T-529–T-533. |
| Logout privacy | Users and saved decks only. | Download/play as A with an already-sent unknown-outcome action → Logout → login as B → confirm no A content → login as A and recover once. Do not erase app data or reset backend between these steps. | T-543. |
| Dynamic catalog and artwork | Users only; import decks through UI. | Start using catalog A → inspect art/faces/defaults → publish catalog B externally → resolve/import new definitions normally → reopen/review the existing game → simulate missing images and inspect text fallback. Repeat with the alternate dataset without rebuilding the app/backend. | T-023, T-122–T-124, T-549–T-550. |
| Bulk conflicts and normalized targeting | Users and saved decks only. | Through UI select/drop multiple cards; externally arrange both server arrival orders against opponent UI moves. Select a source and its effects, apply card edits/Clone versus occurrence removal. Open selected/unselected Actions and change selection during Set. Check exact mutated identities and partial authoritative placement, not only highlights. | T-554–T-557, T-566–T-571. |
| Retained future and Redo target fidelity | Users and saved decks only. | Build dependent/independent paths through UI, vary personal review, explicitly activate internal nodes, then Undo. Build P/Q/R counter/note sequence through both players and remove R/P before Redo R. Inspect unchanged retained nodes and blocked stack. | T-558, T-562–T-563. |
| Exposure and incarnation recovery | Users and saved decks only. | Start → recorded inspection → offline replay versus blocked new covered inspection. In separate variants reject an online revealing prediction with external traffic control, restart/recover its exposure, then end/start fresh play while an old UI-originated request remains delayed. No forged request/history setup. | T-559–T-561, T-576–T-577. |
| Context/grouping and credential boundaries | None for credential variants; users/decks only for gameplay variants. | Register/reset/login with valid whitespace/Unicode passwords; play mixed anchor/tap sequences and add historical pings/comments from both players, viewing before/after effective boundaries. Observe final groups and preserved exact cursor under incremental sync. | T-564–T-565, T-572–T-575. |

This is an initial journey inventory, not complete executable coverage of every
T-ID or a claim that any test has run. Keep scoped variants focused and map each
assertion to the current contract; older superseded scenarios do not override
newer rules. Additional account-reset/email and notification journeys must also
cross the normal app/backend paths; select their service harnesses when the
underlying technical delivery is designed.

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
Every-node invariants/exact inverse and batch-versus-incremental grouping also
need lower-level property tests. T-578's CPU/memory/payload/fan-out measurements
are technical performance tests, not an invented product Stack-count limit or a
claim that UI timing alone proves bounded replay cost.
Future implementation slices must include traceable full-stack UI scenarios;
tooling, device matrix, timeout budgets and CI scheduling are later technical work.
