# Adversarial specification review — 2026-10-20

## Outcome and fix-session handoff

Reviewed baseline: `e4b8b029673a8fd409bb0379b78a0573dcbf5149`.

**The specifications are not yet a consistently composable behavior contract.**
The October 19 repairs substantially improve them, but execution context,
lifecycle scope and presentation identity still have incompatible or incomplete
definitions where features meet. Several remaining inventories encourage
case-by-case decisions where a shared operation/reference model is needed.

This report records **10 findings**, five high priority and five medium priority.
Priority means specification risk, not an observed application defect. There is
no application or executable test suite to run. This was a static review of all
five design tiers, the decision register, gap classification and previous audit;
counterexamples below are reasoning fixtures, not claimed test executions.

**No product specifications or acceptance outcomes were changed by this review.**
The report does not approve its recommendations, reopen settled choices, advance
delivery stages or authorize implementation/UI drafts. Read `AGENTS.md`,
[the design index](../README.md),
[the session handoff](session-handoff.md) and
[the pruned gap list](../design/02-experience/gaps.md) before fixing anything.

### How to use the findings

- **Derived repair:** current decisions determine the outcome; repair contracts
  and tests without another policy question.
- **Contract completion:** a general rule needs its operation mappings and exact
  observable outcomes. This is not permission to defer behavior to coding.
- **Unresolved composition:** the report demonstrates competing observable
  outcomes. First attempt a derivation; ask only if that leaves a real choice.
- **Residual drift:** a specific remaining instance of a previously identified
  problem, not a claim that the previous repair never happened.

Line references below refer to the reviewed baseline. Linked primary contracts,
not this report or decision-history wording alone, must own the eventual repairs.

## Findings at a glance

| ID | Priority | Finding | Classification |
| --- | --- | --- | --- |
| S-01 | High | Historical continuation and live Stack/movement guards disagree about the state being validated. | Derived context rule; synchronize contracts. |
| S-02 | High | Play-incarnation fencing leaves reused lobby preparation vulnerable to old, previously uncommitted requests. | Derived lifecycle safety; technical fencing after the outcome is explicit. |
| S-03 | High | Provisional playback groups also own operational Ping/Unping state. | Unresolved composition; not another tap-grouping policy question. |
| S-04 | High | Exposure records lack a complete mapping into latest-own Undo, stack clearing and branch classification. | Contract completion; demonstrate any residual policy choice. |
| S-05 | Medium | State-derived live operations lack a common input-binding rule, especially Shuffle, Reset and Clone. | Contract completion under D-187, not new stale-value locks. |
| S-06 | Medium | Ordered bulk operations specify final order but not a general valid lowering into individual mutations. | Algorithm/contract completion, not atomic bulk approval. |
| S-07 | Medium | Reference backlogs mix shared lifetime work with cases excluded by existing reachability invariants. | Generic lifetime/reachability mapping; preserve approved differences. |
| S-08 | Medium | Full-path combined shortcuts still lack concrete out-of-context meanings and consistent scope binding. | Known incomplete behavior; one composition contract rather than per-button improvisation. |
| S-09 | High | Logout protects sent requests, while exposure durability also requires protection before transmission. | Derived durability/account-isolation composition. |
| S-10 | Medium | Some active tests and inventories still contradict current rules or declare settled outcomes open. | Residual drift from the previous review. |

## S-01 — Validate guards in the action's execution context

**Evidence.** [History](../design/03-contracts/history-and-undo.md),
lines 550–595 and 724–747, distinguishes ordinary current-head acceptance from
explicit historical continuation. The latter validates at the selected boundary.
[Storage/publication](../design/04-technical/sync-and-storage.md), lines 156–174
and 214–218, says the same. Conversely,
[the limited-locking contract](../design/03-contracts/optimistic-locking.md),
lines 30–46, and [the Stack contract](../design/03-contracts/combat-stack-and-projections.md),
lines 16–25, unconditionally compare additions with the server's current Stack.
They do not state how this interacts with a historical execution boundary.

**Counterexample.** At B the Stack is empty. A creates effect E, making shared
current state C with Stack `[E]`. The other player deliberately continues from B,
creating effect F as a response before E.

- Historical validation at B compares expected `[]` against B's `[]`, allowing
  the new branch with F and retaining C/E.
- Literal comparison against shared-current C compares `[]` with `[E]` and rejects
  the requested historical response.

The same ambiguity affects a historical move whose source was subsequently moved
on the displaced continuation. An explicit flag does not help if its guards are
still evaluated against the wrong board.

**Derived direction.** D-179's explicit-boundary validation already supplies the
distinction. Define one execution-state selection step before applicability,
movement guards, Stack comparison and transition construction. Ordinary requests
use current shared state; deliberate continuations use the selected retained
state. Validate the boundary's current existence/revision separately. Do not mix
fields from both boards or add an unchanged-whole-game lock.

This does not weaken ordinary Stack equality, revive automatic conflict branches,
or decide every simultaneous-activation race. It clarifies what “current” means
inside an already-authorized historical operation.

**Verification.** Extend T-028/T-353/T-389/T-390 with the exact E/F example,
historical movement from a source absent at the live head, an invalid operation
at the chosen boundary, and delivery retry after another branch becomes active.
Assert both the new branch's board and the retained displaced continuation.

## S-02 — Fence lobby requests to a preparation round, not just a lobby ID

**Evidence.** [Lobby readiness](../design/03-contracts/accounts-decks-and-lobby.md),
lines 269–294, rejects late preparation changes after start. Lines 357–375 reset
both Ready values and require new readiness after ending. Lines 481–491 distinguish
play incarnations and invitation attempts, but do not scope lobby preparation
requests to the particular preparation round. [The protocol proposal](../design/04-technical/sync-and-storage.md),
lines 146–154, likewise fences gameplay and invitation responses, not a previous
round's Ready/deck/sideboard/starter/cancellation requests.

**Counterexample.** A readies on their phone. On A's tablet, a separate lobby
request is sent but delayed before server processing. Phone readiness and B's
Ready start play. The players end that play and return to the same lobby.
The tablet request arrives only now, while the persistent lobby is again in
the correct broad lifecycle state.

- An old **Ready** can satisfy the new round's gate without a fresh decision.
- An old deck/sideboard/starter edit can alter the newly restored preparation.
- An old **Cancel game** can cancel the returned lobby.

These are not retries of an already committed request. Idempotency alone does
not help: this is the first application of a request aimed at a previous round.
“Reject after start” also does not help once the state has returned to lobby.

**Derived direction.** The same no-retarget/fresh-readiness principle used in
R-12 applies to lifecycle phases as well as active play. Specify the original
preparation-round scope of lobby-changing intent. Recover committed old results
without reapplication, and prevent uncommitted old intent from mutating a later
round. Exact generation/version/fencing representation is technical work.

Do not make every compatible edit require an unchanged lobby revision. This is
an intent-lifetime boundary, not a blanket stale-value lock or a change to
server arrival-order processing within one round.

**Verification.** Add delayed first-delivery and committed-lost-response variants
for Ready, withdrawal, deck/preparation/starter changes and cancellation across
start → accepted ending → returned lobby. Include multiple devices, unchanged
deck IDs and an object-free Ready request so incidental missing-ID checks cannot
mask the problem. T-453/T-454/T-576/T-577 do not cover this round trip.

## S-03 — Derived playback groups cannot silently redefine ping operations

**Evidence.** [Sequence resolution](../design/03-contracts/history-grouping.md),
lines 52–77, allows provisional tap groups and later marker resolution. Taps join
matching anchors or attach forward to a different anchor. Yet
[the ping contract](../design/03-contracts/visual-action-language.md),
lines 126–138, 142–161 and 215–242, makes the recording group the owning event,
toggle scope and numbering sequence, and says pings keep their original group.
[The board contract](../design/03-contracts/board-and-controls.md), lines 737–775,
also makes event changes exit Ping mode and determines Ping versus Unping from
the current event's existing marker.

**Counterexample family.** Begin with one author:

```text
M1: Hand → Battlefield
Ping X
tap T
```

The next anchor may be another matching move M2, or an isolated Stack entry S.
Under D-250 the final group with M2 can include the whole sequence; with S the
tap goes forward, separate from M1. Now place a Ping/Unping of X after T, before
that next anchor arrives.

The specification must determine the menu label and toggled record **at that
prefix**, not after learning the future. If the operation initially addresses
M1's ping, moving its owning recording group forward can turn it into a
cross-event Unping or leave a removal without its original presence. If T has a
separate provisional event, later merging it with M1 can combine two independent
same-destination presences or change the ordinal/scope. Pinning ownership forever
instead conflicts with “owning event is the recording group” when playback
membership changes. Event-change exit from Ping mode is also unspecified here.

**Why this is not just an ID choice.** Different answers change whether X remains
pinged, what the next tap does, and whether input mode exits. A stable group UUID
does not decide those outcomes. The existing prefix tests T-574/T-575 omit an
already-present destination and an intervening toggle.

**Fix direction — composition first.** Specify which ping context/effective
operation identity is stable and which playback membership/ordinal is derived.
Then give deterministic prefix-extension outcomes that preserve recorded toggle
meaning, no retroactive overlays, author separation and one event's scope.
Do not use manual group editing, an owner picker, a publication delay, or an
extra split caused by taps as a workaround. Do not assume that decoupling ping
scope from groups is already approved: if that is necessary, establish precisely
which existing requirement would change and batch that residual choice.

**Verification.** Test both completions above after every prefix, with X already
present/absent, context Ping/Unping and Ping mode, two different destinations,
Undo/Redo regrouping, author change, and batch versus incremental sync. Assert
marker presence, addressed record, number and mode—not just final separators.

## S-04 — Complete the behavioral participation of exposure-only events

**Evidence.** [Exposure lifecycle](../design/03-contracts/history-and-undo.md),
lines 927–955, requires durable events independent of successful board mutations
and allows a rejected prediction's exposure to survive without a gameplay branch.
Lines 202–238 select the latest still-applied own action and validate retained
successors; lines 141–168 clear Redo on own new gameplay. Lines 978–988 classify
alternative continuations by exposure. The
[domain model](../design/03-contracts/domain-model.md), lines 133–137, still leaves
exposure node placement/coalescing open. These surfaces do not provide one mapping
for an exposure-only event into the controls and branch predicate.

**Counterexample A — Undo target.** A taps an already-visible unrelated card,
then inspects a covered card. Does the inspection become A's latest own revealing
candidate, disabling Undo without skipping, or is the tap still the candidate
because the inspection is in a separate audit stream? The words “event,”
“gameplay mutation” and “action” do not select a single result here.

**Counterexample B — Redo.** A undoes an ordinary edit, then performs that logged
inspection. Does Redo remain or clear? The new event is shared/durable, but it
does not edit a card. Classifying its storage separately cannot silently decide
whether it counts as own new gameplay.

**Counterexample C — branch classification.** An otherwise hideable alternative
is explicitly inspected while another path is shared-current. The exposure's
viewed context is on the alternative, recording context on the active path. State
which continuation's “contains exposure” predicate this changes. A rejected
revealing prediction presents the corresponding no-accepted-board-node case.

**Fix direction.** Define a common event-participation mapping: board transition,
tree membership/causal position, actor/Undo candidacy, Redo clearing, replay/N
participation, recording versus viewed dependency and visibility attribution.
Apply it to accepted revealing edits, standalone inspection, explicit View/Reveal,
and rejected-prediction exposure. First use existing exposure/no-skipping/context
rules; do not treat this report's competing outcomes as approved alternatives.

Physical encoding, coalescing and IDs follow that mapping. The mapping itself
is observable behavior. Do not erase exposures, fabricate successful draws,
add automatic branches, reopen offline event creation or create a permanent
board-marker feature to avoid it.

**Verification.** Add the three examples, a later independent opponent edit,
same-account devices, restart/retry, and an inactive historical inspection.
Assert actual Undo/Redo availability and explorer filtering in addition to the
existence of a truthful exposure record. T-559–T-561 currently establish logging
and connectivity, not all of these interactions.

## S-05 — Define when live state-derived inputs are bound

**Evidence.** [Live acceptance](../design/03-contracts/optimistic-locking.md),
lines 19–46 and 74–79, permits ordinary operations against current state and
limits optimistic guards to moves/Stack additions. The old batch's Clone/Reset/
Shuffle comparisons are explicitly unapproved. Meanwhile
[target normalization](../design/03-contracts/game-actions.md), lines 331–354,
says Clone copies a captured live source; [card defaults](../design/03-contracts/domain-model.md),
lines 337–383, use the selected face's default; and
[randomness](../design/03-contracts/history-and-undo.md), lines 1017–1036, requires
whole-Library Shuffle with a recorded concrete order. There is no shared live
input-binding table covering these cases.

**Counterexamples.**

1. Reset was opened on face F1; another device selects F2 before processing.
   “Current face” can mean the processing face or a captured face. Resetting to
   F1's default while leaving F2 selected is not the same operation as Reset F2.
2. Clone is invoked before another edit changes the source's counters/note.
   Sending an absolute new-token snapshot and asking the server to copy its
   current source have different outputs. Rejecting solely for changed fields
   would reintroduce an unapproved Clone lock.
3. Shuffle was predicted for `[A,B,C]`; a card arrives or leaves before acceptance.
   The old permutation is not a permutation of the current whole Library.
   Applying it can lose an arrival or resurrect a departed card; an old-membership
   comparison also needs justification under the limited-guard policy.

**Fix direction.** Separate explicit user parameters/identities, derived live
reads, predicted outputs and authoritative recorded outcomes. Map each operation
to that common model. D-187's current-state baseline should settle ordinary
state-derived reads unless a confirmed operation binds a particular input; state
the derivation instead of restoring the old 25-rule batch. Retain D-248's captured
target IDs—capturing the target does not automatically freeze all its fields.

For Shuffle, guarantee a duplicate-free permutation of the applicable accepted
membership and preservation of unrelated card state. For retry/replay, reuse the
accepted concrete result rather than invoking the live operation again. Entropy
source, provisional-result reconciliation and representation are technical work;
they cannot select a different user-visible operation by accident.

**Verification.** Pair each case with accepted actual before/after inspection,
changed input that remains applicable, missing live source, retry and Redo.
Include arrival/removal during Shuffle and an unchanged membership with changed
notes. T-563 covers historical reinsertion, not this live-input timing problem.

## S-06 — Specify a generic lowering for ordered multi-object edits

**Evidence.** [Ordered movement](../design/03-contracts/board-and-controls.md),
lines 592–611, forbids reversal through repeated insertion. [Bulk operations](../design/03-contracts/game-actions.md),
lines 372–406, and [combat insertion](../design/03-contracts/combat-stack-and-projections.md),
lines 593–606 and 729–751, require individual immediately published mutations and
preserved moved/unmoved order. T-095/T-280/T-281/T-347 mostly specify final order
or a “deterministic sequence,” not a reusable sequence-construction contract.

**Counterexamples.**

- Moving `[A,B]` onto the top of `[X,Y]` by moving A then B with the literal
  single-card “top” operation yields `[B,A,X,Y]`, which is forbidden.
- Reversing the execution order may preserve the destination but cannot simply
  reuse a top-card Draw guard for B while A is still above it.
- For same-slot `[A,B,C,D]`, moving `[B,D]` into an indicated gap needs to resolve
  the gap after accounting for moved members, without duplicates or accidentally
  addressing a neighbor that the invocation itself just moved.

Different valid lowerings can also expose different intermediate states. Those
states are selectable interruption boundaries, not invisible implementation steps.

**Fix direction — algorithm completion.** Define the generic ordered-collection
transformation once: normalized moved identities, their approved pre-action
sequence, destination edge/gap, untouched-member stability, source dependencies,
and individual emission order/anchors. Specify or explicitly constrain the valid
intermediate sequence, including same-container transformations. Then reuse it
for Library transfers, ordinary-pile ordering and combat slots where applicable.
Preserve their distinct capacity/containment rules rather than pretending all
three are the same container kind.

This need not be another owner policy vote. Do not add atomic multi-card nodes,
reservations, an ordering picker or automatic rollback of accepted siblings.
Continue per-request arrival-order validation under D-176/D-183/D-184; record
the authoritative partial result if interleaving invalidates a later member.

**Verification.** Assert every node, not just the final list: top/bottom insertion,
same-container subset moves, already-at-destination members, deduplicated aliases,
empty origins/pair cleanup and intervening opponent edits. Compare forward/reverse
traversal and interrupted prefixes with the declared lowering.

## S-07 — Classify reference lifetimes and prune unreachable cases

**Evidence.** [View/Reveal references](../design/03-contracts/board-and-controls.md),
lines 543–576, fix original identities/order and retain moved references, but
leave deletion/removal behavior unspecified. Token removal at lines 1085–1108
still leaves link/tray/selection references open.
[Projection lifetime](../design/03-contracts/combat-stack-and-projections.md),
lines 242–309, is now precise for combat cleanup and Stack token snapshots.
[Historical pings](../design/03-contracts/visual-action-language.md), lines 215–242,
retain original contexts instead of replacement objects. The
[invalid-ID invariant](../design/03-contracts/domain-model.md), lines 730–752,
does not distinguish intentionally absent historical targets from malformed IDs.

**First apply reachability, rather than inventing an edge case.** The current
top/bottom-N trays reference Library cards. Tokens disappear instead of entering
Library, and non-token cards cannot be deleted. Therefore a token disappearing
from a valid Library tray is not an R1 scenario that needs a new snapshot policy.
The broad token-removal backlog's reference to trays should be narrowed, not
expanded into another owner question. A normal tray card moving elsewhere is
already covered by D-47's fixed reference and location update.

**Reachable counterexample.** Link a live token to another card, select it, and
create a Stack effect; then Remove the token. The Stack behavior is defined. The
card link must not become a dangling live endpoint, a selection/bound editor must
not silently target a replacement, and a historical ping to that known token
must not become a malformed-ID error merely because the token disappeared
normally. These are related consequences of identity and reference kind, not
independent per-entry-point policy choices.

**Derived constraints.** Keep identities/provenance; do not refill trays or invent
a replacement card. Live structural endpoints cannot dangle. Frozen Stack effects
remain read-only with last-live state, combat occurrences clean up, and historical
references retain their recorded contexts. These are already different approved
reference kinds, not grounds to apply Stack freezing universally.

**Fix direction.** Provide one matrix for supported reference kinds versus target
movement, disappearance, eligible restoration, path switch and tree revision.
For each first mark reachable and invariant-excluded combinations; then specify
live dereference/action availability, retained display/context, mandatory cleanup,
and inverse. Distinguish valid historical absence, currently inapplicable live
actions and malformed/foreign IDs. Reuse that classification in D-67 applicability
and D-248 bound-editor checks.

Any genuinely missing retained-content choice must be identified explicitly;
this report does not approve a new last-live snapshot policy for every tray/link.
Do not broaden mixed-endpoint feature scope while completing approved links.

**Verification.** Add the reachable example and normal-card tray moves, explicit
token Remove versus destination disappearance, Undo/Redo where eligible, and
references whose source node survives an earlier tree edit. Assert that tokens
cannot become live Library/tray members instead of creating an impossible UI
fixture to test their removal. Check reference validity and access to original
context at every node. T-547/T-548/T-556/T-557 concentrate on projections, not the
complete matrix.

## S-08 — Combined shortcuts need meanings outside their quick-action context

**Evidence.** [Full-path parity](../design/03-contracts/board-and-controls.md),
lines 52–58, says quick visibility is not underlying access. [Advance-and-close](../design/03-contracts/combat-stack-and-projections.md),
lines 330–359, leaves out-of-combat and missing-area handling open and explicitly
says not to infer a later-phase rewind policy. [Untap/Draw shortcuts](../design/03-contracts/domain-model.md),
lines 643–684, leave participant scope/concurrent partial handling open.
[Mulligans](../design/03-contracts/accounts-decks-and-lobby.md), lines 451–479,
leave count capture and per-card sequencing open. These are known incomplete
contracts, not newly discovered feature requests.

**Counterexamples.**

1. During Cleanup, use the full-path advance-and-close action. Setting Postcombat
   main, advancing to the other player's Untap, or making this combination
   unavailable produce different state. The quick button's approved End-of-combat
   destination does not by itself specify the full-path result.
2. An Untap + advance invocation captures player A's cards; an opponent tracking
   action interleaves before its final tracking mutation. A fresh relative Next
   step and an explicit requested Upkeep transition can produce different results.
3. A turn player's battlefield contains a card moved there from the other side.
   Scope by configured region differs from scope by retained participant metadata;
   manual association authoring is excluded, and moves must not infer control.

**Fix direction.** Give composite commands a common contract for target scope,
explicit parameters versus current-state reads, constituent operations, and
partial/no-change outcomes. State the full-path operation's meaning independently
of when its quick entry point is visible. Use configured destination/participant
bindings or an explicit required destination choice where existing rules support
them, without inferring ownership or card rules.

D-111 already settles valid empty untaps/draws: advancement still happens.
D-183/D-184 already settle individual processing and interleaving; do not seek
new approval of that consequence. Resolve the remaining operation meanings in
one reviewable batch, clearly marking any recommendation that current rules
cannot derive. No arbitrary phase-only legality gate or extra confirmation chore
is approved here.

**Verification.** Add full-path actions before/after their quick window, an absent
combat area, same/different participant regions, and an intervening tracking
change between constituent nodes. Existing happy-path T-193/T-232/T-233 and
T-407–T-413 do not select every outcome above.

## S-09 — Logout must preserve initiated exposure recovery, not just sent work

**Evidence.** [Logout](../design/03-contracts/accounts-decks-and-lobby.md),
lines 60–76, removes private downloaded content and explicitly protects recovery
records for already-sent requests. [Exposure capture](../design/03-contracts/history-and-undo.md),
lines 934–955, happens durably before display and must survive rejection/restart;
already-initiated online records recover later. [Unsent online work](../design/03-contracts/history-and-undo.md),
lines 456–460 and 839–842, is still broadly left to technical buffer/cancellation
handling. T-543 tests Logout only with an already-sent request.

**Counterexample.** While online, durably capture exposure, display the covered
face, then lose the connection or background the app before transmitting the
exposure record. Log out. The record is initiated and required for truthful
exposure history, but does not meet the stated “already-sent” protection category.
A cleanup implementation following only that category could delete it. Preserving
it as ordinary visible cached content instead would violate account isolation.

**Derived direction.** Define protected recovery by the obligation still owed,
not a network-send boolean. In particular, actual exposure already initiated
online must remain recoverable and account-protected even before transmission.
Otherwise durable-before-display capture does not fulfill its promised lifetime.
Ordinary downloaded content still clears on Logout; another account cannot read
or publish the protected old account's record.

This is not permission for new offline inspections or general unsent gameplay
replay, and it does not decide every incomplete form's Logout lifetime. Technical
record categorization/protection must implement these distinct outcomes.

**Verification.** Extend T-543/T-560 with capture-before-send, Logout, process
death, login as B, then login as A and one-time recovery. Include capture failure
before display and a committed exposure with a lost response. Check the record's
eventual discoverability, not merely that a local file survived.

## S-10 — Remove residual contradictions from active tests and inventories

The previous review's R-01/R-02 repair was substantial, but a few current entries
still contradict it. This is residual drift, not a reason to reopen the policies.

| Location at baseline | Remaining defect | Existing rule to apply |
| --- | --- | --- |
| [Acceptance](../design/05-verification/acceptance.md), T-268, line 313 | Explicitly says “Parent-node Undo/recovery applies (D-159).” | Latest-own selective tree editing, D-224 onward; personal reverse traversal is separate. |
| Acceptance T-060, line 105 | The setup has four actual cards in one ordinary visual pile. | The D-39 three-member invariant means this cannot be a valid UI-created acceptance fixture. Preserve the visibility test with a valid pile/other occlusion, or explicitly label malformed-state coverage. |
| Acceptance T-174, line 219 | Starts by comparing an “undone covered-face inspection.” | Revealing targets cannot be selectively undone. Use a retained inactive inspection context, not an impossible current Undo outcome. |
| Acceptance T-232, line 277 | Says combined boundaries still require decisions. | Tracking then invariant-closed closure is already specified in section 13.10 and T-238/T-556. |
| Acceptance T-462, line 507 | Leaves cancelled-lobby restoration guarantees open. | D-241/T-542 explicitly excludes trash/restore; service retention remains separate. |
| [Board optional communication](../design/03-contracts/board-and-controls.md), lines 726–734 | Says comment path is retained through the comment when undone. | D-232 removes eligible comments for Redo without an original-path alternative; D-223 applies to genuine retained alternatives. |
| Board section 10.8, lines 783–792 | Calls create/duplicate/remove labeled generic pieces proposed essentials. | D-241 excludes general piece authoring; only confirmed token/Clone and occurrence tools remain. |
| [Account remaining decisions](../design/03-contracts/accounts-decks-and-lobby.md), lines 508–513 and 529 | Still lists optional areas as behavior decisions; attributes lobby placement to D-201. | Extra areas are excluded by D-241, placement is D-202, D-201 is starter choice. Do not turn this inventory into an owner-question queue. |
| [Domain state](../design/03-contracts/domain-model.md), lines 113–115; [Android context](../design/03-contracts/android-experience.md), lines 158–174 | Queue device scope and deleted-review-cursor handling still appear open. | D-235 settles per-account shared stacks, D-238 parent fallback; only remaining mechanics stay open. |

**Fix direction.** Correct or explicitly retire each active assertion at its own
stable requirement/scenario location. Do not append another precedence disclaimer
and leave contradictory individual oracles. Recheck surrounding “open” inventories
against the pruned Q-register; status must be narrowed, not closed wholesale.

**Verification.** Use valid R1-reachable fixtures for UI scenarios, distinct
malformed-state fixtures for model tests, and a source check for obsolete
parent-Undo/comment-retention/open-device-scope wording outside clearly historical
sections. Preserve stable IDs and decision history as provenance.

## Suggested fix-session sequence

1. **Establish shared command context (S-01/S-02/S-05).** Distinguish persistent
   lobby, preparation round, play incarnation, execution boundary, captured target
   and live derived inputs. Specify outcomes before wire fields. Do not collapse
   all these concepts into one revision or blanket optimistic lock.
2. **Close the group/event composition (S-03/S-04).** Define stable semantic
   participation and references versus derived playback groups, with prefix and
   cross-feature examples. These are the strongest candidates for a residual
   owner-review batch only if existing decisions cannot determine the outcomes.
3. **Complete generic transformations/lifetimes (S-06/S-07/S-08).** Reuse one
   ordered lowering, reference matrix and composite-command contract rather than
   adding exception-by-exception prose. Preserve confirmed area-specific behavior.
4. **Audit durability across every exit (S-09).** Start, display, transmit,
   rejection, connection loss, process death, Logout/account switch and recovery
   need consistent obligations. Unsent gameplay, unfinished input and already-shown
   exposure are not interchangeable states.
5. **Synchronize active surfaces and tests (S-10 plus all repairs).** Update primary
   contracts, Tier 1/2 summaries where affected, technical constraints and stable
   verification outcomes together. Mark derived outcomes versus proposals.

Do not ask ten new policy questions. Most work above is shared-contract completion
or a consequence of approved rules. Any remaining owner choice needs the concrete
trace, conflicting requirements, attempted derivation and a clearly labeled
recommendation, batched under D-237.

### Do not reopen / not findings

- D-245 retained-future checks, latest-own/no-skipping Undo, fork restrictions,
  potentially stranded Redo, and synchronized per-player stacks are settled.
- D-247's exact Stack-entry comparison fields are settled; S-01 concerns the
  board on which that comparator runs, not its field set.
- D-242's live real sources and token-only last-live Stack fallback are settled;
  S-07 must not replace them with universal snapshotting.
- Offline events remain forbidden under D-246. Recorded visible content stays
  readable, respecting participant-specific View presentation; another player's
  recorded exposure is not blanket permission for a new offline disclosure.
- Ordinary incompatible races use normal processing order and applicability.
  A missing scenario does not by itself create a new race-policy decision.
- Exact IDs, database shape, N/budget values, numeric limits, timing, geometry,
  provider choice, notification cadence and test framework remain their proper
  technical/UI/service work. Their observable constraints still need contracts.
- No additional authoring, archive, manual-group, response-request, offline-play,
  or non-Android feature is approved. No implementation or stage transition follows.

### Completion criteria

- Each counterexample has one specified outcome or an explicitly bounded set of
  allowed outcomes, traceable to current rules; residual proposals are labeled.
- No execution mixes a live head's guards with a historical boundary's board.
- Old requests cannot acquire fresh-round intent merely because a lifecycle
  label or persistent ID is reused.
- Group recomputation cannot silently change a recorded ping operation's meaning.
- Exposure records have explicit control, visibility and durability participation.
- Every emitted bulk/composite boundary is valid and deterministically reversible.
- All approved reference kinds distinguish legitimate absence from malformed IDs.
- Active requirement/scenario IDs have coherent current assertions and valid
  fixtures; source-linked documentation checks do not masquerade as passing app tests.
- Validate local links/anchors, IDs and whitespace, inspect the staged paths, then
  commit and push each completed batch with `./scripts/save.sh <edited paths...>`.
  Report failures without force-pushing, bypassing checks or discarding user work.
