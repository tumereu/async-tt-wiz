# Adversarial review — current specifications after D-259

Reviewed baseline: `f249d3f4c6fea1397c13e16bd27e7d4cffa9fb7a`.

## Outcome and discussion boundary

The owner requests another adversarial review and a walkthrough of findings.
This report records seven actionable specification risks. It changes no product
contract or acceptance oracle and selects no implementation mechanism. No
application, executable tests, UI drafts or stage transition are authorized.

These are **derived contract/verification repairs, technical proof obligations and
wording defects**, not seven new product decisions. No new owner-only policy
choice has been established in this batch. Discuss concrete traces and permit
correction of the derivations rather than ask for approval of consequences already
required by the spec. An actual conflicting outcome found during repair must still
be escalated rather than silently invented.

The review read the tier summaries, principal state/action/history/lifecycle
contracts, technical proposals, grouping matrix and E2E strategy, with targeted
acceptance/provenance searches. It focuses on feature composition, lifecycle,
concurrency, recovery and observable test outcomes. This is static reasoning, not
executed application testing or proof that every possible defect is absent.

| ID | Priority | Risk | Classification / provenance |
| --- | --- | --- | --- |
| G-01 | High | A historical bulk command lacks an explicit once-only continuation handoff. | Derived composition completion; new concrete trace. |
| G-02 | High | Automatic request recovery lacks an explicit ordered first-application trace. | Technical protocol/verification completion under D-184/D-216. |
| G-03 | Medium | Late exposure can outlive the exact viewed tree revision. | Existing F-03, still unrepaired; durability/reference completion. |
| G-04 | Medium | A disappearing token must not become an ordered-destination anchor. | Existing F-04, still unrepaired; lowering/test completion. |
| G-05 | High | Independent optimistic/authoritative random orders can break ordinary mulligans. | Technical proof obligation already noted in the previous review; still not a selected mechanism. |
| G-06 | Medium | Leading pings expose an overbroad grouping-prose versus matrix ambiguity. | Documentation/classifier completion; new concrete trace. |
| G-07 | High | Active text still directs an implementer toward superseded behavior. | Existing F-06, expanded with root README and status/verification drift. |

Priority is specification/implementation risk, not an observed software defect.
F-01 remains withdrawn; F-02 and F-05 remain resolved by D-258 and D-257.

## G-01 — Historical bulk commands need a once-only continuation handoff

**Sources:** [operation context and composition](../design/03-contracts/operation-composition.md#select-execution-state-before-validating-an-operation),
especially execution-state selection and individual constituent processing;
[history publication](../design/03-contracts/history-and-undo.md#33-immediate-action-synchronization),
which says the first accepted mutation creates/activates the continuation;
T-028/T-029/T-343/T-353 and
[Android interruption flow](../design/03-contracts/android-experience.md#54-interruption-flow).

**Trace:** retain `root → B → C`, with C current. Deliberately continue at B and
invoke a selected-set edit producing X then Y. Both are independent, applicable
mutations. There is no concurrency or invalid target.

- Applying the historical flag and B independently to every member can create
  siblings `B → X` and `B → Y`, leaving X on an inactive continuation.
- Making only the first request historical works only if its outcome actually
  establishes the continuation. Repeat with X already satisfied at B (no node),
  or rejected while independent Y remains valid. Consuming the intent merely on
  submission could send Y to C, despite the explicit choice of B.
- A legitimate invocation must not require an acknowledgement between every
  constituent, an atomic multi-card node or a new automatic conflict branch.

**Derived repair:** make the intent lifetime explicit: no-op/rejection alone does
not establish a reaction branch; the first effective accepted reaction establishes
it once, and ordinary follow-ups do not each re-fork at B. Preserve individual
validation/publication and actual partial outcomes. Without a further deliberate
switch, successful X/Y should yield `B → X → Y` while C is retained separately.
Encoding the continuation handoff/provisional references is technical design, not
permission to add a sequencing dependency graph or a routine acknowledgement gate.

Separately distinguish **newly arriving ordinary follow-up requests**, which use
the then-current shared state under D-184/D-187, from **replay of an already
published displaced suffix**, which is prohibited. If another player interrupts
between constituents, the mere existence of the first reaction cannot silently
give later ordinary requests a new historical activation flag.

**Verification needed:** exact tree, shared pointer, parent of each accepted
member, both Redo queues and preserved future for first-member success/no-op/
rejection, lost response/retry, and an intervening opponent switch. Extend the
historical-execution and composite E2E journeys; current cases test these concerns
separately rather than their handoff.

## G-02 — Ordered recovery must preserve first application, not just deduplicate

**Sources:** [transport ordering](../design/04-technical/sync-and-storage.md#6-synchronization-and-storage)
requires order-preserving delivery, not just issuing concurrent HTTP requests in
order; [reconnect recovery](../design/03-contracts/history-and-undo.md#33-immediate-action-synchronization)
preserves original requests but explicitly leaves recovery ordering/readiness open.
D-183/D-184/D-216 and T-029/T-477–T-479 are the approved basis.

**Trace:** a client makes `Set counter 5`, then `+1`. Both requests are sent, but
the connection fails before either reaches the server. A concurrent recovery
worker lets the second request first reach processing before the first.
The result is `0 → 1 → 5`, rather than `0 → 5 → 6`. Each request executed once;
idempotency alone did not preserve the client's ordered delivery.

Repeat with Set already committed and its response lost: retrying it must instead
recover that original result without resetting the later accepted 6 to 5.

**Derived repair/proof obligation:** preserve original client order for requests
that still need first application, recover already-committed outcomes once, and
reconcile to current authoritative state rather than an old response snapshot.
Opponent requests may still interleave. Do not use client timestamps to reorder
server history, a whole-queue transaction or normal-play stop-and-wait. Recovery
readiness/transport mechanics must demonstrate these outcomes explicitly.

**Verification needed:** both unknown-outcome variants, several outstanding
requests, process death/reconnect, outcome lookup plus retry, and an opponent edit
between them. Assert actual transitions and final UI, not only request counts.

## G-03 — Late exposure after deletion/revision still needs its provenance route

**Sources:** existing
[F-03](adversarial-review-2026-10-20-follow-up.md#f-03--late-exposure-recovery-after-its-viewed-revision-was-edited-away),
[exposure lifecycle/participation](../design/03-contracts/history-and-undo.md#exposure-lifecycle-and-offline-inspection--d-246)
and [reference lifetime](../design/03-contracts/domain-model.md#28-reference-lifetime-and-reachability).

**Trace:** Alice's phone captures a logged hidden-side inspection at A and shows
the content, but publication is delayed. Her tablet successfully Undoes A before
the server knows of the inspection. Alternatively, an earlier independent Undo
changes A's materialized revision. New play can then clear the removed A's Redo
entry. The protected exposure eventually arrives.

The earlier Undo cannot have checked a record the server had not received. The
later record cannot be dropped, rebound to A's revised board or made to recreate
an active gameplay branch.

**Derived repair:** retain the original shown content/revision/provenance with a
discoverable read-only route even when there is no matching current tree node.
Use authoritative processing order for its effective control participation, not
client-clock backdating that retroactively invalidates completed Undo. Preserve
incarnation fencing and D-238 cursor fallback separately. This is specific
exposure provenance, not a universal snapshot/recovery feature.

**Verification needed:** deletion, surviving-node revision, Redo clearing,
Logout/restart and duplicate recovery; assert original content, discoverability,
current tree/board and latest-own/Redo effects. T-560/T-595/T-600 do not yet give
this combined trace a complete oracle.

## G-04 — Accepted disappearance is not successful placement

**Sources:** existing
[F-04](adversarial-review-2026-10-20-follow-up.md#f-04--successful-disappearance-is-not-a-destination-prefix-anchor),
[ordered lowering](../design/03-contracts/operation-composition.md#ordered-multi-object-lowering)
and the D-84/D-85 mixed-token move rule.

**Trace:** move `[token T, ordinary C]` to Library top `[X]`. T disappears; C must
still yield `[C,X]`, not fail an invented after-T anchor. Moving `[C,T,D]` must
yield `[C,D,X]`, retaining C as the preceding actual placement.

**Derived repair:** skip disappearing members when constructing destination
prefix anchors, without skipping their disappearance operations. Distinguish
them from a valid no-op member already occupying its destination, and from a
formerly placed anchor invalidated by the opponent. The last case must still
reject dependent insertions, not select a new gap. Preserve last-live capture,
entry resets, individual boundaries and valid sibling outcomes.

**Verification needed:** leading/middle/trailing tokens, Library top/bottom,
mulligan returns and concurrent removal of the last actual anchor. Extend
T-591–T-593 and the lowering/composite E2E journeys, not a malformed token-in-Library
tray fixture.

## G-05 — An uncontended shuffle-and-draw needs a prediction agreement mechanism

**Subsequent architecture direction — D-261:** client/server state types and
processing code must be shared; local die/coin sampling is permitted with no
independent server reroll. The [sync design](../design/04-technical/sync-and-storage.md#shared-processing-and-random-input-agreement--d-261)
now proposes a per-request Shuffle random input and shared versioned permutation
over canonical current members; T-599 explicitly requires uncontended success.
This is progress on the proof obligation, not selection/verification of the exact
Shuffle protocol or a claim that G-05 is fully resolved. Changed-member, immutable
Draw-target and no-acknowledgement-barrier requirements below remain.

**Sources:** [live Shuffle and composites](../design/03-contracts/operation-composition.md#bind-identities-and-choices-not-predicted-output-snapshots),
[mulligans](../design/03-contracts/accounts-decks-and-lobby.md#opening-hands-and-mulligan-shortcuts--d-192)
and the previous review's
[technical warning](adversarial-review-2026-10-20-follow-up.md#recommended-next-batch).

**Trace:** no competing actions occur. The client predicts shuffled order
`[A,B,…]` and sends Draw A. The server independently samples `[B,A,…]`. Shuffle
succeeds, but Draw A fails its correct top-card guard. Independent prediction
would make the required shortcut routinely return no intended hand even on a
healthy uncontended connection.

**Technical completion, not a new randomness choice:** specify and verify how
provisional randomness and the authoritative outcome agree in the normal case,
while retaining fresh new invocations, fixed retry outcomes, full-current-member
Shuffle, immutable in-flight draw targets and no routine acknowledgement barrier.
Do not silently fix this by substituting another drawn card or by declaring
routine uncontended failure to be acceptable partial execution. Choosing entropy,
seeds/validation or another compliant protocol is separate technical design.

**Verification needed:** ordinary uncontended N-card and minus-one mulligans
complete their intended draws, then separate changed-membership/conflict variants
retain actual partial outcomes. Retry/restart must not resample. T-599 and the
E2E mulligan journey need this explicit normal-case proof as well as race tests.

## G-06 — Leading ping-only context is not clearly excluded by tap-run prose

**Sources:** [sequence resolution](../design/03-contracts/history-grouping.md#deterministic-sequence-resolution),
rule 3, says a “taps/pings-only run” accepts its following substantive action.
The [matrix](../design/05-verification/grouping-matrix.md#complete-category-transition-matrix)
distinguishes pending T from leading annotation-only O: O→M is `|`, while T→M is
`+`. GM-16 covers `ping; tap; move`, not `ping; move`.

**Trace:** at root, one author records `ping X; move A`. A pings-only group meets
the prose's unqualified description, suggesting one group; the explicit O row
instead prescribes `ping X | move A`. Both readings are plausible without
changing any menu binding or letting UI requests define groups.

**Documentation repair from the existing classifier:** state precisely when the
pending T context exists and qualify forward anchor absorption accordingly.
The current matrix's O row gives the derived outcome for the no-tap trace; a
leading ping followed by a tap becomes the qualifying T run. Add the contrast
with `ping; tap; move`, including every-prefix ping scope/ordinal/mode assertions.
Changing the explicit O row rather than clarifying the prose would be a grouping
rule revision, not a silent editorial fix.

This finding does not revive F-01's withdrawn menu-bound-group premise or propose
another ownership picker.

## G-07 — Superseded instructions remain active, including the entry README

**Sources:** existing
[F-06](adversarial-review-2026-10-20-follow-up.md#f-06--residual-active-contradictions-need-direct-edits)
and these still-active assertions at this baseline:

| Location | Problem | Current basis for repair |
| --- | --- | --- |
| [Root README](../../README.md), lines 10–13 | Says conflicting edits create continuations and every game edit must be recoverable without the present eligibility/lifecycle limits. | D-179/D-187 forbid automatic conflict branches; current U-001/D-224/D-258 limits are explicit. |
| [Game actions](../design/03-contracts/game-actions.md), lines 90–93; [combat](../design/03-contracts/combat-stack-and-projections.md), lines 121–125 and 674–678; [Android](../design/03-contracts/android-experience.md), lines 24–26 and 49–58 | Still require full paths for combined shortcuts or say nothing can be quick-only; Android also leaves settled absent-area behavior open. | D-253 requires individual-constituent full paths, allows quick-only combinations, and operation composition defines absent closure as a no-op. |
| [History](../design/03-contracts/history-and-undo.md), lines 682–684; [grouping inventory](../design/03-contracts/history-grouping.md), lines 266–267 and 290–292 | Still describe matching moves across interleaved taps without D-251's new boundary. | Apply the primary current sequence rule and matrix; do not merge backward across a new tap run. |
| [Worked example](../design/03-contracts/visual-action-language.md), lines 297–298 | Says Undo of an interruption returns to the displaced continuation. | Undo edits that line; explicit return/activation is distinct. |
| [Message entity](../design/03-contracts/domain-model.md), line 126; [sync messages](../design/04-technical/sync-and-storage.md), lines 272–277 | Generic edit/delete revisions and “open” revision controls can reintroduce excluded submitted-chat/comment tools. | D-240/D-241; distinguish delivery records, ordinary comment Undo and new-message correction from dedicated Edit/Delete. |
| [Acceptance T-496](../design/05-verification/acceptance.md) | Still names remembered-head updates after activation as open work. | Remembered-head Redo is retired; D-258 clears both current queues on a qualifying switch. |

These are direct repair targets, not owner choices. Correct active assertions and
their affected oracles rather than add another top-level precedence warning.
Keep historical decision entries and clearly labelled original review reasoning
as provenance. Do not indiscriminately replace every historical occurrence.

## Walkthrough / next repair order

1. Explain G-01/G-02 together: one deliberate continuation, individually processed
   constituents, ordered first delivery and idempotent recovery are separate rules
   that must compose. Identify a genuine remaining choice only if a concrete trace
   cannot be resolved from those rules.
2. Complete the already-known G-03/G-04 durability/lowering traces with their exact
   contract and acceptance outcomes; no new token/exposure policy is needed.
3. Keep G-05 in technical design with an explicit uncontended-success obligation,
   not a new product question or permission to implement the protocol now.
4. Narrow G-06's prose against the current classifier, add the missing contrast
   trace, and synchronize G-07's active text across tiers and verification.

For any subsequent completed edit batch: run documentation checks, inspect the
exact diff, and save only its paths with `./scripts/save.sh`. This report itself
does not claim those repairs have been applied or tested.
