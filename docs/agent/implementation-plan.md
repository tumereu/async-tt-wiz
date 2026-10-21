# Draft implementation task plan

Prepared 2026-10-21 under D-300/D-301, **after** the isolated environment tests and
the UI agent's completed batch. **Planning only; application implementation and
the autonomous runner are not activated.** The owner asked to treat the finished
UI as good enough to move toward implementation, not perfect or frozen. This
planning assumption does not certify all design gates or approve extra features.
The later D-303 owner follow-up adds the explicit final interface-to-mock review
and correction gate under B8.

Read the [loop/review contract](implementation-loop.md),
[progress record](implementation-state.json), [delivery gates](delivery-workflow.md)
and [environment evidence](e2e-environment-smoke-2026-10-21.md) with this plan.

## Baseline and intended result

- Repository/UI baseline: `f3bd2cc65f6ca885dc48daeba4355a2ba9bb00df`.
- [Finished UI packet](../design/02-experience/ui/review.md): D-292–D-299,
  cumulative **F01–F88**, 45 primary states + four continuations / 98 captures.
  Manifest SHA-256:
  `3a57abfbdbcc28cff6a42e3cbd36beb01875dfd9164f3d0c98beb8f10cbfbd3f`.
- Use that packet as the initial layout/interaction reference, preserving explicit
  owner feedback and supersessions. It is inert design material, not application
  code to port or an assertion that every overlay is already drawn. Retired G00/H07
  are provenance; **do not restore their UI**. H07 retirement did not remove the
  older archive/restore behavior contract.
  Remaining copy-audit deletions stay proposals, not a blanket instruction to
  remove necessary labels/errors. Layout trials are initial implementation
  references under the owner's planning assumption, not new product-policy grants.
- [Product requirements](../design/03-contracts/product.md),
  [current scope](../design/02-experience/gaps.md), detailed behavior contracts and
  the [technical index](../design/04-technical/README.md) constrain implementation.
  The selected Kotlin/KMP/Compose Canvas, Spring Boot/PostgreSQL/Flyway/jOOQ,
  SQLDelight and ordered WebSocket/HTTP stack stays. Complete generated artifacts
  and qualify exact dependency versions during implementation; the smoke fixture
  is not a complete application version catalog.
- Deliver **the agreed R1**, not merely a demonstration game: two cooperative
  people can import/prepare decks, play manually, understand asynchronous changes,
  correct mistakes, review/continue history and return to their prepared lobby.
  Android only, both orientations, no competitive/rules-referee machinery.

Do not postpone all testing, usability or recovery until the final batch. Every
task carries its own relevant tests and usable presentation. Later batches add
capabilities; their explicit pending status is not a defect in an earlier batch.
Once a capability is delivered, its ordinary failure/recovery behavior is part of
that delivery, not permission to leave a crash/data-loss hole until final hardening.

## Work-unit and dependency rules

The IDs below are bounded implementation selections, not promises that each will
fit a particular token budget. Follow listed order within a batch and finish its
review/fixes before starting the next batch by default. Dependencies are the
earlier listed tasks plus earlier reviewed batches, unless a safe independent
lane is explicitly recorded when blocked.

A fresh agent may split a task that is too large into stable child IDs such as
`I14a/I14b`, with concrete outcomes, checks and dependencies, **before coding it**.
Use a planning-only split unit first, leaving the parent in progress and children
pending for subsequent fresh units; do not spend most of the wall-time budget
coding an oversized task before deciding to split it.
Keep the parent open until every child passes; inherit the same review batch and
do not add reviews after each child. Splitting is technical planning, not a request
for routine owner approval or permission to broaden scope. Do not call a whole
multi-session feature complete because one internal layer was committed.

Each task starts by recording its exact P-/T-/F-IDs, contract sections and checks.
The seeds below aid selection, not an exhaustive substitute for the primary
contracts. I01 establishes complete coverage accounting for **P-001–P-072,
T-001–T-715 and applicable F01–F88**, including explicit superseded/conditional/
excluded dispositions. A test inherits its feature's scope; obsolete examples
cannot reintroduce excluded authoring, offline actions or stronger reset races.
Later findings extend coverage inside approved behavior, not invent new policy.
Where a scenario spans several capabilities, record the completed assertions and
remaining task dependencies separately; do not claim the full scenario passed
until it actually runs end to end. Seed IDs do not pull later features forward.

## B1 — Install, enter and resume the normal app

**Review after I01–I04 together.** Outcome: a real installed Android app uses the
real containerized backend for ordinary account entry/navigation/recovery.

| Task | Implementation and completion outcome |
| --- | --- |
| I01 | Establish reproducible Gradle modules, version locks and JVM21/API29→SDK36 clean builds; shared semantic/protocol boundaries, Spring image/config/health and PostgreSQL/Flyway/jOOQ generation; minimal SQLDelight storage/migration path. Add task-to-requirement/test coverage ledger and stable synthetic datasets/art. Provide exact build/unit/integration/E2E commands, isolated Compose and Testcontainers ownership, the measured WSL/Windows device adapter, readiness/reset/evidence/cleanup. No production orchestration or alternate test app. |
| I02 | Implement register/login, persistent revocable tokens/Keystore, ordinary authorization and server account privacy; native A01/A02 forms/errors with consistent whitespace/Unicode passwords. Actual requests/storage, not a fake login or hard-coded user. |
| I03 | Implement N01/N02 authenticated shell and Menu/Games navigation, fixed Logout, portrait drawer/landscape persistent navigation and native Back/fullscreen/rotation behavior. Restore valid login/context; Logout clears local private work/downloads immediately and fences old-account callbacks. Account-owned data synchronizes, unfinished UI stays personal. |
| I04 | Complete A03/A04 email reset/deep-link handling and synthetic mailbox adapter; successful reset revokes old sessions for **new** requests, invalid/expired links do not. Run normal-app registration/login/reset/logout/resume journeys through Kotlin/JUnit/Appium, including restart and a second account/device. No special ordering against already-authorized gameplay. |

**Sources:** accounts/decks/lobby, Android experience, auth, runtime/test architecture.
**Seeds:** P-001/P-043/P-047/P-059/P-065/P-066; T-396/T-397/T-419–T-422,
T-543/T-572/T-573/T-621/T-656/T-657/T-666/T-676/T-677.
**Review journey:** fresh install → register → login → navigate/rotate/keyboard →
restart → ordinary reset → explicit Logout → another account. Check reachable
controls, meaningful errors, privacy and no accidental submissions, not reset-era
message scheduling puzzles.

## B2 — Import and maintain usable decks

**Review after I05–I08.** Outcome: users acquire dynamic records, import common
lists and manage saved decks without artwork/network hiccups destroying work.

| Task | Implementation and completion outcome |
| --- | --- |
| I05 | Normal client catalog downloader, MTGJSON-compatible synthetic source, staged local records/cache and bounded nonblocking startup metadata check. Lazy printing/face art with text fallback and safe bounded downloads; provider adapters/configuration, not licensed bundled data or a mandatory backend art proxy. |
| I06 | Common quantity/name/printing and mainboard/sideboard import parsing/resolution; preserved text and actionable line errors; D03/D04/D04b resolved preview and atomic named save. Unknown/ambiguous input cannot silently substitute/drop cards or overwrite a good deck. |
| I07 | D01/D02/D05/D06/D08 collection/details/rename/in-deck cover/delete/full-art inspection, same-account synchronization, keyboard/long-list/rotation handling. No individual card-count deck editor, export, trash or invented cover upload. |
| I08 | D07 contextual Overwrite(default)/additive Merge and ordinary complete-definition HTTP POST; immutable account-private content identities/validation and atomic attachment. Retry cannot double-Merge; conflicting claimed external names/IDs cannot rewrite another saved/game definition. Preserve these snapshots for later lobby copies and stale opponents. |

**Sources:** account/deck contract, domain model, card-data/art, protocol/storage.
**Seeds:** P-044/P-048/P-049/P-056/P-067; T-399/T-423–T-432/T-491/T-492,
T-625–T-629/T-659/T-667/T-679.
**Review journey:** download synthetic catalog → invalid then corrected import →
inspect/rename/cover → overwrite/merge → restart/reopen; simulate unavailable art
and catalog update failure. Review usable workflows, not decorative pixel matching.

## B3 — Two people prepare and start the same game

**Review after I09–I12.** Outcome: normal UIs can create, prepare and start an
actual persistent shared game, without helpers injecting invitations/game state.

| Task | Implementation and completion outcome |
| --- | --- |
| I09 | P01/P02/P05 contacts/search/add and atomic named create+first invitation, generated name/reroll/select-all dialog, server creation date and retained game identity. Establish the real shared per-game ordering/durable request/outcome path here, reused and extended in I13, not temporary unguarded lifecycle writes. Recipient Accept/Decline and inviter Reinvite target the exact game; no mutual contact approval/presence gate. |
| I10 | L01–L04/L04b saved-deck picker with personal border draft/one bottom confirmation; immutable prepared copies and lobby-only sideboard. One/all tap+drag transfers, captured IDs, immediate requests, no double dispatch; fixed fading feedback and short disappearing-row transition, accessible alternatives. Establish accepted/predicted/UI separation for preparation, extended in I14 rather than a second authoritative local model. |
| I11 | Shared initial model/definitions/containers, starter/readiness/preparation-round and incarnation boundaries; both Ready start once with independently recorded shuffles, 20 life, turn-1 Untap and no opening draw. Render that real initial state on the production table surface, extended in I15/I16, not retired G00/a test-only screen. Implement shared seeded randomness/canonical ordering plus independent golden vectors, preserving normal later-game interfaces. |
| I12 | Complete lobby/list synchronization, own-only Ready clearing/withdrawal, acceptance after inviter preparation, leaving versus L06 Cancel, cancellation versus start and retained original outcomes. Produce real two-Android UI entry/import/invite/preparation/start journeys and selected-source edit/delete isolation. |

**Sources:** accounts/decks/lobby, domain/composition, protocol/storage/randomness.
**Seeds:** P-045/P-046/P-050–P-054/P-061/P-062/P-072; T-398/T-400–T-406,
T-414–T-418/T-433–T-463/T-635/T-655/T-669/T-680–T-684/T-690/T-696.
**Review journey:** A invites/prepares/readies → B declines → A reinvites → B
accepts/prepares/readies → both see the same start. Include cancel and ordinary
start/preparation overlap; do not add host Start, extra Send or online-presence rules.

## B4 — Responsive shared basic tabletop

**Review after I13–I16.** Outcome: both players can draw, place, rotate and edit
life on a real shared table with immediate feedback and reliable ordinary recovery.

| Task | Implementation and completion outcome |
| --- | --- |
| I13 | Extend the shared initial model and durable request path with deterministic gameplay command/state/replay types and individual mutation records, relevant-state/unchanged-field definitions and server-created grouping foundation. Extend per-game durable admission/FIFO processing, dedup/outcomes/transactions/outbox and limited move/Stack guards. Start with ordinary moves/orientation/life and extend the same processor, not separate client/server implementations. |
| I14 | Extend preparation's accepted replica + volatile prediction + UI-only separation to gameplay; ordered multiple-in-flight requests, stable UUID references, original-outcome recovery, stream fencing, incremental updates and coherent complete current-tree baseline infrastructure. Server-first crash restart/no outgoing journal, late acceptance and difference-only rebase; low-level deterministic conflict/fault tests. No sequence-hole gates, offline gameplay or speculative-disclosure side channel. |
| I15 | Production Canvas scene/layout with shared draw/hit/accessibility geometry: equal card/pile footprints and uniform zoom, owner borders, portrait/landscape docks/hands and compact overflow. One gesture coordinator for single-tap Actions/double-tap rotation/hold inspection, drag/selection/pan/pinch/cancel. Qualify actual normal-app Canvas targeting early, including animated bounds; focused component tests may supplement, not replace, UI acceptance. |
| I16 | Wire G01/G03/G10/U01 basic table, ordinary piles and Library→Hand/board/special-zone moves, life actions, correct captured selection and connected/read-only behavior. Run both UIs with delayed acknowledgements, independent/same-card moves, disconnect/reconnect and process restart. Verify no old-ack rewind, double application, fake offline mutation or animation-gated sending. |

**Sources:** domain/board/game actions/composition/limited locking, client/server
architecture, prediction/playback, protocol, request recovery and storage.
**Seeds:** P-009/P-012/P-015–P-021/P-040/P-041/P-059/P-062–P-065/P-070;
T-037/T-038/T-057/T-064–T-077/T-349–T-386/T-475–T-485,
T-608/T-609/T-611–T-619/T-636–T-639/T-642–T-644/T-647–T-649/T-664/T-673,
T-685/T-686/T-697.
**Review journey:** both players draw/place/tap/change life rapidly, pan an
overflowing Hand, rotate and reconnect. Measure responsiveness separately from
recognition delay; review visible failures and credible overlapping edits, not
every theoretically possible packet/lifecycle permutation.

## B5 — Complete manual-play tools and combat/Stack

**Review after I17–I21.** Outcome: the full agreed manual tabletop toolkit works
together, not only basic movement. Retain one shared model/processor/UI semantics.

| Task | Implementation and completion outcome |
| --- | --- |
| I17 | G04/G06/G07/G13 direct packed card/player actions: corner strings/arithmetic/Set/Reset, all six counters, life, notes, owner, face/cover and permitted inspection. Retained current-state preview/menu lifetimes, absent-player blank versus zero/card-counter dash, input/keyboard, disclosure and assigned-hold-versus-name-tooltip arbitration. |
| I18 | Complete ordered Library/contents controls: top/bottom/subsets, Draw N, View/Reveal fixed accepted references, shuffle and both captured-hand mulligan compositions. Retained face defaults, destination resets, ordinary partial outcomes and deterministic recorded random results; no refilling trays, target substitution or extra guessed exposure records. |
| I19 | Catalog/Blank token creation, Clone-as-token, token disappearance and explicit token-only removal; persistent generic Link to card/player endpoints (including selection-to-endpoint), unique undirected pairs/repeated-Link no-op, Links-list removal and endpoint cleanup. Add native-UI/server/recovery/history tests for those links, separately from real-source relationships and event-scoped numbered Ping/Unping. Distinguish source identity/relationship highlight from actual selection/occurrence scope and preserve target binding across reflow/sync. No real-card Remove action. |
| I20 | G08/G08b/G11/G12 turn/phase/step picker, life/player delta drags, standalone Table actions/die/coin and full quick-action catalog. Three contextual positions plus tap-Next/hold-End, panning overflow, ending-player fill and explicit alternatives. Release-only relative edits; no hold+tap dispatch, implicit untap/draw, priority, extra first-strike step or rules inference. |
| I21 | G02 combat paired slots/insertions/cleanup and real/effect Stack entries: live write-through, owner/source relationship, multiple independent effects, disappeared-token last-live snapshots, contextual destinations/removal and declared combined actions. Compact divider-only geometry at common card scale, complete contents/count badges and full non-quick constituent paths. |

**Sources:** board/game actions, operation composition, combat/Stack, communication,
grouping, domain/randomness; the packet's **complete** quick/icon catalogs.
**Seeds:** P-014/P-022–P-030/P-032–P-041/P-046/P-058/P-061/P-071;
T-078–T-245/T-346–T-348/T-389–T-395/T-407–T-413/T-545–T-557,
T-566–T-571/T-588–T-599/T-612/T-619/T-620/T-634/T-665/T-668/T-670,
T-688–T-691/T-693/T-694/T-698/T-699/T-702–T-707/T-709–T-712.
**Review journey:** a representative manual game using mulligans, edits, hidden
inspection, tokens, combat and Stack effects, including source departure. Check
action discoverability/touch cost, correct visible identities and no double
dispatch; do not infer card legality, secrecy enforcement or competitive fairness.

## B6 — Understand, review and correct asynchronous play

**Review after I22–I26.** Outcome: complete current-game history, recovery and
deliberate continuation are usable without losing either player's accepted work.

| Task | Implementation and completion outcome |
| --- | --- |
| I22 | Complete automatic category/prefix grouping with all implemented tools, event-comment requests/composer/retained references, pings and deterministic representative-icon fixtures. H01/H02/H04/H05 exact-event centered timeline, group Previous/Next/play/pause and live/background catch-up. Net endpoint animation/retargeting, not waypoint queues; continuing sync and stable paused cursor. |
| I23 | Server-owned selective latest-own Undo and last-undone-first Redo, exact relevant transition and retained-successor checks/N, one mutation per press, shared same-account stacks and correct clearing. G03 explicit route and tap-Undo/hold-Redo arbitration; no skipped/revealing/fork-crossing targets or duplicate retries. |
| I24 | H03 explicit historical intent, first-successful-mutation fork, retained alternatives and ordinary late acceptance. H06 compact pannable **group** tree, real fork junctions/internal current, local selection distinct from Make current and captured exact target stable through growth/reranking. Preserve exact-event access; representative icon is never the activation target. |
| I25 | Existing hidden-path rules plus contract-defined branch archive/restore/return and metadata recovery through compact contextual/advanced routes, without restoring retired H07 or cluttering normal switching. Switching alone archives/deletes nothing; apply deliberate-switch Redo resets and no-op/retry distinctions. Resolve unpictured presentation from current patterns; a genuine behavior contradiction is a blocker, not license to remove the feature. |
| I26 | Complete branched-tree automatic full download/coherent staging and incremental recovery, retained parent metadata/deleted-cursor ancestor fallback, rebuildable checkpoints and offline downloaded read-only review. Exercise a fresh third instance, paused review during edits, changed active path, service restart and pruned-delta full recovery. No lazy missing branches, history age expiry or finished-session archive. |

**Sources:** history/Undo, grouping and grouping matrix, visual language, sync/
storage, prediction/playback, request recovery; D-299 group-icon document fixtures.
**Seeds:** P-002–P-008/P-017/P-023/P-040/P-042/P-057/P-063/P-064;
T-246–T-345/T-493–T-541/T-558–T-565/T-574–T-587/T-602–T-607,
T-616–T-618/T-640/T-641/T-645/T-646/T-651–T-653/T-663/T-671/T-672,
T-674/T-675/T-689/T-692/T-695/T-700/T-708/T-713–T-715; GM-01–GM-25.
**Review journey:** opponent acts while away → catch-up/play/pause/scrub → correct
an eligible mistake → deliberately continue earlier → inspect/switch both paths
→ reopen on a third instance. Check understandable board transitions and faithful
recovery, not imagined competitive information-security guarantees.

## B7 — Communicate, end/rematch and receive activity

**Review after I27–I31.** Outcome: an ordinary asynchronous game can finish, restart
from preparation and remain reachable across devices/background work.

| Task | Implementation and completion outcome |
| --- | --- |
| I27 | G09 direct chat/composer/unread presentation and ordinary account/incarnation binding, clearly separated from I22's event-comment route. Game-wide chat is separate from selected history, viewing stays personal and offscreen messages are not read merely by opening. No sent-message editing/deletion/Undo/read receipts/presence. Integrate communication with B6's comment retention/Undo/grouping tests. |
| I28 | Table-action win/loss/draw suggestion and explicit other-player acceptance, strict singleton ending groups and final-board-before-dialog playback. Restore last prepared lobby copies, clear both Ready and start a fresh incarnation only after both ready again. Add necessary unpictured states using the established UI patterns, not a score archive or End-turn-as-end-game. |
| I29 | P03/P04 active shared-game Delete with extra actor confirmation warning that the shared game is removed for both; no opponent consent/deletion vote. Retain captured incarnation and actual outcome/retry handling, distinct from setup cancellation and cooperative result acceptance. Either participant can delete; both lose shared access/listing. Test deletion while the opponent is absent as well as stale-confirmation/retry boundaries; no personal hide/trash or promise of instant physical erasure. |
| I30 | Invitations/Game activity channels, synthetic notification deliveries, per-game coalescing, registered-installation routing and local foreground suppression. Working non-GMS best-effort polling and optional FCM adapter/configuration; OS permission/channel behavior and notification navigation. Sync works when neither notification route ran. No new settings/mutes/provider dependency. |
| I31 | Full integrated two-player/three-instance journeys across chat, end/start, list navigation, restart/relogin and notification/background return. Exercise ordinary overlapping lifecycle requests without retargeting or duplicate effects; finish previously pending cross-batch recovery/UI assertions for delivered B1–B7 capabilities. No production credentials or API-controlled opponent. |

**Sources:** account/lobby/lifecycle, visual language, history/Android experience,
notification/auth profiles and full E2E strategy.
**Seeds:** P-022/P-043/P-055/P-065/P-068/P-069/P-072;
T-464–T-489/T-532/T-538–T-543/T-576/T-577/T-581/T-582/T-600/T-601,
T-603/T-604/T-622–T-624/T-647/T-658/T-677/T-678/T-701.
**Review journey:** communicate during review → review final changes/result →
accept → prepare/start again → return from background notification → test active
deletion separately. Review actual missed/double effects and navigation; D-289
already permits authorized in-flight work to finish during reset/Logout.

## B8 — Whole-game usability, qualification and delivery

**Review after I32–I36, including the final interface-to-mock review below, then
targeted fixes/verification.** Outcome: the agreed scope is integrated and
evidenced, not just a collection of locally passing parts.

| Task | Implementation and completion outcome |
| --- | --- |
| I32 | Whole-game usability pass against F01–F88 and completed packet: both orientations, long lists/hands/Stack, keyboard/large text/cutouts, native Back, reachability/accessibility, tooltip/gesture arbitration and text/art failure. Finish unpictured zone menus, inspectors, result/error states from existing rules. Improve demonstrable real-use friction; no arbitrary decorative redesign or demand for mockup pixel identity. |
| I33 | Measure realistic scene/core/server/sync workloads against the runtime profile: gesture recognition versus provisional response, frames/retargeting, image loading, large retained histories, bounded replay/backpressure and memory. Fix observed stalls/OOM/race effects. Keep emulator tooling timings separate from physical-phone frame evidence; do not silently relax budgets or add gameplay caps. |
| I34 | Complete requirement/test coverage and full-stack synthetic journeys, model/property/golden, real-PostgreSQL migrations/concurrency/fault, component/Canvas, normal-APK two-player/three-device, coherent full recovery and suite reset/isolation. Run a clean repeated suite; preserve failures and fix their causes. No skipped required checks, fake backend/action injection or unexplained unmapped current requirement. |
| I35 | Supported Android/API29/current/tablet/non-GMS and compatible mixed-version/upgrade qualification; stable test/release signing interfaces and versioned APK/hash/protocol metadata. Validate container/config/health/migration/shutdown/restart and amd64/arm64 application image interfaces, not production Compose/backups or a Pi-capacity promise. Keys remain external; install upgrades normally without wiping supported data. |
| I36 | Reconcile final delivered scope, evidence, retained-data/erasure interfaces and documented limitations; prepare installation/test/operator-interface notes and release-readiness checklist. Prepare the complete final interface-to-mock comparison inventory and normal-app capture/journey evidence below for the independent B8 review. Record provider/art rights/attribution, release signing/host and unavailable reference-hardware checks as explicit external gates, never fabricated clearance. All actionable final-review issues get fix/targeted verification runs before completion. |

**Sources:** all current contracts, [E2E journeys](../design/05-verification/e2e-strategy.md),
[runtime/device budgets](../design/04-technical/runtime-and-verification-profile.md),
distribution/compatibility and external operations boundary.
**Seeds:** P-001–P-072 with phase-only/excluded cases explicitly classified;
all current applicable T-001–T-715, grouping fixtures and cumulative UI direction.
**Review journey:** complete representative manual/asynchronous play from fresh
entry to rematch, plus measured workloads and targeted credible failure traces.
Review integrated behavior, not a new unlimited specification audit.

### Final interface-to-mock review — required B8 completion gate

Owner follow-up D-303, 2026-10-21: explicitly review **all implemented interfaces against
the mocks at the end, and fix the differences**. I32's earlier usability pass does
not replace this final comparison after I33–I36 and intervening changes.

- I36 prepares a complete comparison inventory against the **current full UI
  packet**, cumulative F01–F88 and explicit supersessions: every current mocked
  screen, dialog, overlay and scroll continuation in portrait and landscape
  (currently 49 states / 98 captures). Retired G00/H07 are not targets to restore.
  Record the mock/state/orientation, normal-app route and matching captured app
  evidence, comparison result and any finding IDs; do not sample only gameplay or
  the happy path. Account/navigation, decks/import, contacts/invitations, lobby,
  table/actions/inspectors, history and chat all belong in the inventory.
- The fresh independent **B8 reviewer** exercises those interfaces in the normal
  app and compares their final layout, controls, icons, labels, displayed state,
  selection/feedback and documented interactions with the current references.
  Inspect scrolling/keyboard/overflow and live transitions where a still capture
  alone is insufficient. Inventory additional implemented but unpictured states
  too, checking the primary contracts and established UI patterns rather than
  inventing missing mocks or omitting those interfaces.
  Use synthetic accounts/catalog/art and equivalent app states: example card names,
  dates and random outcomes in a mock are data, not literals to hard-code/reproduce.
- Every unexplained implementation/mock discrepancy becomes a concrete finding
  **to fix**, not an automatic `nonblocking-polish` exemption. Normal platform
  rasterization is not a pixel-identity requirement. A necessary responsive
  adaptation or intentional difference must have an explicit current-contract or
  owner-feedback basis and retained rationale; an agent's aesthetic preference is
  not permission to silently depart from the mocks. Conflicting behavior/feedback
  must be reconciled, not copied blindly from a superseded picture.
- Fresh fix units repair the discrepancies; fresh verification units compare the
  affected interfaces against the mocks again, rerun relevant interaction/UI
  regression checks and update the comparison evidence. A fix that changes another
  interface adds that interface to the recheck scope. Preserve the original
  findings/evidence and their verified corrections, not just new screenshots.
- B8 cannot be marked reviewed or the project complete until the inventory covers
  every current target/orientation, all discrepancies are verified fixed or have an
  explicit source-backed intentional-difference disposition, and no actionable UI
  comparison finding remains. Missing device access or capture/interaction evidence
  is a recorded blocker, never a presumed visual pass.

## Completion and remaining activation work

**Software completion** requires all task/child outcomes, batch reviews and
targeted fix verifications, the final interface-to-mock review and verified UI
corrections, complete approved-scope traceability, reproducible passing required
checks, no unresolved actionable defects and committed/pushed evidence. A missing
physical measurement or required environment check is not a
pass. Report the affected gate/blocker honestly and continue independent work.

**Release readiness** additionally needs the documented rights/attribution,
signing/distribution and external operator/hardware prerequisites. These are not
permission for agents to acquire paid services, publish publicly, handle personal
production data, deploy, or work in the separate operations repository. Distinguish
implemented software, verified software and externally releasable software in the
final report; do not label the whole project done with concealed remaining gates.

Before starting application work:

- Owner explicitly authorizes the implementation loop/scope after the design
  gates; update the state record with that authorization, not an agent's inference.
- Use the separately authorized [thin driver](implementation-runner.md) and inspect
  its qualification/per-session permissions within approved boundaries. D-302
  pins `openai/gpt-6.1-sol#high`; supply explicit session-count/wall-time budgets.
  Building this tooling does not activate product execution or authorize I01.
- Recheck toolchain/device preflight from the measured topology; qualify the
  **normal app/backend** in B1/B4 instead of claiming the browser fixture passed it.
- Inventory physical-phone/current-Android/tablet/non-GMS access, upgrade signing
  and amd64/arm64 test capacity now. Record unavailable resources and independent
  work, not a fabricated pass or a surprise late-B8 prerequisite. Exercise intended
  bootstrap/build/test/device adapter commands under the actual per-session
  permissions; do not infer permission-profile qualification from a separate smoke.
- Reconcile activation/stage wording with the explicit owner authorization. The
  runner's `complete` additionally requires the recorded external release gates;
  if software is implemented but required verification or external readiness is
  blocked, report those dimensions separately and stop honestly, without waiving
  hardware/rights/signing/operator prerequisites or self-authorizing new resources.
- Establish the per-task run/evidence records and concrete commands in I01. Exact
  build commands cannot truthfully be reported as existing before builds exist.

No implementation approval, automatic stage advance or perfection claim is
implied by drafting this plan or by the environment smoke result.
