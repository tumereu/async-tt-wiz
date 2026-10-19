# Adversarial specification review — 2026-10-19

## Handoff and scope

Reviewed repository baseline: `e468c8c8ac90ca4d66df0fc9c7108c6620ef04be`.
This is a review report, **not an amendment, owner decision, stage transition or
implementation authorization**. The specifications were not changed by this review.
The next session should fix documentation and verification contracts, not build an
application. Read `AGENTS.md`, the design index and current session handoff first.

The review covered all five design tiers: product/experience, every Tier 3
contract, both technical proposals, acceptance scenarios and the E2E strategy.
Decision provenance was checked against the register, pruned gaps and existing
audit. This was a static adversarial review, not an executable test run or a
certification of exhaustive coverage. Existing audit findings are identified as
such rather than presented as new owner decisions.

**Overall:** the core policies are substantially clearer than the executable
behavior contracts. Many individually reasonable rules do not yet have a defined
composition. Several missing observable outcomes are called protocol/schema/UI
details, while superseded requirements remain embedded in current contracts and
tests. More exception-by-exception questioning would not solve that structure.

### Classification

- **Derived repair:** existing decisions determine the result; synchronize prose
  and tests without asking the owner to approve it again.
- **Contract completion:** write a general rule and worked outcomes from existing
  decisions. Escalate only a demonstrated remaining contradiction.
- **Unresolved composition:** this report gives a concrete counterexample with
  competing observable outcomes. A recommendation is not an approved resolution.
- **Technical:** representation, resource accounting or protocol work constrained
  by the behavior, not a new gameplay-policy question.

High means a risk of wrong shared state, lost context/information, or implementing
an obsolete contract. Medium means significant ambiguity, UX inconsistency or an
unproven technical guarantee. Priorities concern specification fixes, not current
application defects; no application exists.

## Findings at a glance

| ID | Priority | Finding | Disposition |
| --- | --- | --- | --- |
| R-01 | High | Superseded Undo remains normative in requirements, glossary and tests. | Derived repair; known drift, additional concrete locations. |
| R-02 | High | R1 exclusions coexist with apparent R1 deliverables and acceptance cases. | Derived repair; known scope drift. |
| R-03 | High | Whole-drop wording overpromises across independently accepted bulk mutations. | Derived ordering outcome; clarify prevalidation versus later conflicts. |
| R-04 | High | Mutation granularity lacks a general invariant-preserving cleanup rule. | Contract completion; identify any genuine D-176 conflict. |
| R-05 | High | Undo validation scope is unclear with an active internal node and a retained future. | Unresolved composition, not reopening the fork policy. |
| R-06 | High | Suffix equality alone does not define whether a Redo target can be reinserted. | Contract completion; target-transition ambiguity demonstrated. |
| R-07 | High | Exposure lifecycle omits offline inspection and rejected optimistic revelations. | Contract completion; observable policy conflict may remain. |
| R-08 | High | Historical annotation placement is more than a protocol detail. | Contract completion; distinguish recording and referenced contexts. |
| R-09 | Medium | Selection of several views of one source lacks generic target normalization. | Derived source/occurrence distinction; complete multiplicity contracts. |
| R-10 | Medium | Stack equality's semantic contents are undefined, not just its encoding. | Contract completion; possible unresolved comparison scope. |
| R-11 | Medium | Append-or-start grouping algorithm cannot express all forward companions as written. | General algorithm/contract completion, not a pairwise questionnaire. |
| R-12 | High | Recovery lacks an explicit play-session boundary across lobby reuse. | Derived lifecycle safety plus technical fencing. |
| R-13 | Medium | A suffix-count cap does not itself bound Undo work. | Technical cost model and performance verification. |
| R-14 | Medium | Context-menu target scope is left open although it changes which cards mutate. | Behavior contract, not merely later menu layout. |
| R-15 | Medium | Universal text trimming has an undefined boundary at account credentials. | Account/security contract scope clarification. |

## R-01 — Current documents still require obsolete Undo behavior

**Evidence.** [Product requirements and terminology](../design/03-contracts/product.md)
lines 64, 213–217 and 244 say Undo follows a parent or discards a captured
transition; the glossary defines it as author-independent pointer movement.
[Grouping](../design/03-contracts/history-grouping.md) lines 7–9 calls one-parent
Undo an invariant. [Action acceptance](../design/03-contracts/optimistic-locking.md)
lines 51–54 says D-161's captured transition is unchanged.
[History](../design/03-contracts/history-and-undo.md) lines 402–403 and 673–674
repeat stale/pointer behavior outside the explicitly historical subsection.
[Storage](../design/04-technical/sync-and-storage.md) lines 306–308 calls action
nodes immutable despite its earlier tree-edit caveat.

The [acceptance table](../design/05-verification/acceptance.md) still has directly
incompatible outcomes, notably T-006/T-287 (author-independent Undo),
T-172/T-185/T-186/T-288 (retain paths removed by Undo), T-173/T-176/T-178/T-180/
T-275/T-319 (Undo exposing actions), T-293/T-294 (captured-transition races),
T-193 (combined Untap node), and T-281 (one invocation Undo for a multi-card
reorder). New T-520/T-522/T-531 and D-176/D-224 onward say otherwise.

**Counterexample.** A makes an independent move, B taps a different card, then A
presses Undo. T-006/T-287 and the glossary remove B's action by pointer movement;
D-231/T-520 remove A's move and retain B's tap. Both cannot be the current oracle.

**Why precedence notes are insufficient.** They allow a careful reader to recover
the intended policy, but leave individual requirement IDs unsafe to implement,
cite, or convert into tests. T-198 already shows a better pattern: a stable ID
explicitly marked superseded, with a link to its replacement.

**Fix direction — derived, no owner question.** Make every active definition use
D-224–D-236. Preserve provenance outside normative sections. For each stable old
T-ID either update the case or explicitly retire its old outcome and link the
replacement; do not leave contradictory assertions under “Required outcome.”
Separate logical tree revision from any immutable physical event-log proposal.

**Verification.** The same mixed-author, revealing-target, concurrent-device and
bulk fixtures must have one current expected outcome wherever referenced. Audit
all “parent,” “regardless of author,” “immutable,” and “D-161 unchanged” hits in
context; do not blindly remove valid personal backward traversal.

## R-02 — Excluded scope still looks included to downstream design

**Evidence.** D-241's explicit override is in
[product scope](../design/03-contracts/product.md) lines 90–101, but “Included”
immediately lists configurable areas, custom objects and editable groups
(118–126). The [action catalog](../design/03-contracts/game-actions.md) has
A-006/A-014 broad random/reorder capabilities, A-009/A-013/A-023 authoring and
A-032 export, despite its scope gate. The
[Android screen inventory](../design/03-contracts/android-experience.md) lines
97–99 still lists compare, recovery-center and response-request responsibilities;
lines 383–384 presents editable grouping as a workflow.
[API deliverables](../design/04-technical/architecture-and-operations.md) lines
124–139 still require grouping edits, presence, configuration and exports before
implementation. Acceptance T-021/T-051/T-240/T-310 and the worked board examples
also contain excluded tooling or superseded recovery assumptions.

**Failure mode.** A session implementing the “complete API deliverables” or turning
each coverage row into an E2E journey can silently add features explicitly cut
from R1. Conversely, applying the exclusion too broadly could delete confirmed
Blank/token, automatic grouping or branch-recovery capabilities.

**Fix direction — derived.** Give capability rows and deliverables explicit
current scope/status, or move excluded inventories to a historical/proposal
section. Update “Included,” workflows, API deliverables and acceptance together.
Distinguish internal generic primitives from an authorized user-facing editor.
Do not manufacture a roadmap for excluded features.

**Verification.** Trace T-544 to every active screen/action/API inventory; preserve
the confirmed-capability exceptions listed in D-241. A proposed technical list
must not become an implicit R1 requirement.

## R-03 — Whole selected-drop validity versus per-action acceptance

**Evidence.** [Pile overflow](../design/03-contracts/board-and-controls.md) lines
385–395 forbids moving only the fitting part of a selection.
[Bulk actions](../design/03-contracts/game-actions.md) lines 342–354 requires
individual immediately published mutations and says whole-drop validation can
happen before mutation creation. [Online ordering](../design/03-contracts/history-and-undo.md)
lines 422–435 and acceptance T-372/T-376 require independent results and permit
opponent interleaving, not an uninterrupted atomic bulk block.

**Counterexample.** A pile initially contains X. A drags selected Y/Z onto it;
the local final size three is valid. Server accepts Y. B's W then joins, making
the pile three. Z arrives last and must fail capacity validation. The resulting
move is partial even though the original drop was valid. Preflight at drag time
does not protect the later server state.

**What the general rules already decide.** Per-request validation cannot accept Z
as a fourth member, silently split it, erase B's W, or compensate by rolling back
Y as an automatic new mutation. Arrival order and individual outcomes are settled.

**Contract defect, not a new race-policy question.** Reading “reject the whole
drop” as a guarantee against concurrent partial acceptance would require
coordination inconsistent with the later independent-publication rules. Applying
D-176/D-183/D-184 gives the outcome above: Y and W remain accepted; Z fails. This
is the same ordinary-ordering principle as D-239, not a new exception to invent.

**Fix direction — derived.** Explicitly distinguish whole-selection prevalidation
from later per-mutation conflicts. Preserve D-39/D-66's rejection of an initially
invalid overflowing placement, but do not promise atomic server acceptance for a
valid bulk drop. Document the partial authoritative result and difference-only
reconciliation. Do not introduce reservations, atomic bulk commits or whole-queue
cancellation to satisfy an overbroad reading of the older wording.

**Verification.** Add the exact Y/W/Z interleaving, both interleaving orders,
source movement during a selected drag, and reconciliation/selection outcomes.
T-077 plus T-372 separately do not test their interaction.

## R-04 — “One mutation” needs invariant closure, not endless cleanup cases

**Evidence.** [History mutation sequences](../design/03-contracts/history-and-undo.md)
lines 979–997 leaves cleanup/reset decomposition open while forbidding combined
individual edits. [Source lifetime](../design/03-contracts/combat-stack-and-projections.md)
lines 247–307 requires combat cleanup and last-live token snapshots.
[Slot cleanup](../design/03-contracts/combat-stack-and-projections.md) lines
468–483 removes fully empty pairs immediately. The
[grouping inventory](../design/03-contracts/history-grouping.md) lines 171–174
treats automatic resets/disappearance/cleanup as recorded move effects, whereas
other passages emphasize individually navigable changes.

**Counterexamples.**

1. Move a source off Battlefield, then record occurrence cleanup separately:
   the intermediate boundary has a combat occurrence without a Battlefield source.
2. Delete a projection before separately deleting its incident links: a boundary
   has dangling live endpoints. Removing links first avoids that violation but
   creates a distinct, interruptible unlink state.
3. Reset a token's modified corner/counters before it disappears: the surviving
   Stack snapshot can capture defaults rather than D-242's pre-disappearance state.
4. Split last-occurrence removal and empty-pair deletion: the intermediate state
   contradicts “remove immediately when both sides are empty.”

These are not four unrelated feature questions. They expose a missing general
definition of a valid atomic transition and its mandatory dependent effects.

**Fix direction — contract completion.** Define which changes are independent
user operations versus structural/invariant maintenance belonging to an operation.
Require every published boundary to satisfy invariants. Specify a common effect
ordering, including capture-before-disappearance, and record the exact inverse.
Keep genuine bulk card/occurrence edits individually navigable under D-176.
If mandatory cleanup is considered several independent mutations, supply a valid
sequence and interruption outcomes; do not assume invalid intermediate states
are invisible because group animation is concurrent.

**Verification.** Assert invariants and exact before/after/source snapshot state
after **every node**, forward and backward, including interruption between nodes.
Do not test only the shortcut's final board.

## R-05 — Retained single-child futures are outside the stated Undo fork test

**Evidence.** [Selective Undo](../design/03-contracts/history-and-undo.md) lines
34–45 compares later mutations in the timeline. Its fork prohibition at 183–196
checks multiple children on the target-to-active-head path. Explorer activation
at 275–281/692–695 can make an internal node current while retaining its future.
T-496 explicitly requires retained internal-node activation.

**Counterexample.** Retain `root → A → B → C`, with B your latest own tap of X
and C an opponent untap of X. Explicitly activate B. B is not a branching point:
it has only one child, C, and nothing follows B on the active path. Undo's stated
active suffix is empty. Deleting B would nevertheless change retained C's
original tap-state tuple, or strand its ancestry.

**Competing outcomes.** Block removal because C remains a replay dependency;
edit/replay C as well; or reinterpret the inactive continuation boundary. These
are observable changes to retained history, not just node-ID encoding. The report
does not select one. “Every subsequent mutation” could include C, but the current
timeline/path wording and N scope do not make that interpretation explicit.

**Fix direction.** Define validation/mutation scope for active internal nodes and
their retained single-child futures. Apply existing preservation/replay rules
first. Do not reopen D-233's settled multiple-child policy, resurrect original
copies of Undo-edited paths, or automatically activate C to avoid the case.

**Verification.** Test empty versus independent/dependent retained future,
root/internal activation, and a personal viewer on C. State which nodes count
toward N and which remain unchanged.

## R-06 — Redo needs a target contract as well as unchanged suffix tuples

**Evidence.** [Selective Undo/Redo](../design/03-contracts/history-and-undo.md)
lines 34–40/104–107 tests every subsequent mutation; lines 221–224 acknowledges
reinsertion-precondition work. The [domain model](../design/03-contracts/domain-model.md)
lines 306–310 says replay/Undo use recorded accepted values, not renewed arithmetic.
T-505/T-516 cover changed later tuples, but not a changed insertion state with
an empty suffix.

**Counterexample with a surviving anchor.**

1. P, by B: red counter `0 → 1`.
2. Q, by A: an independent note on another card.
3. R, by A: red counter `1 → 2`.
4. A undoes latest-own R; it is queued immediately after Q.
5. B undoes latest-own P; Q is independent and stays applied. Tree is `root → Q`,
   counter is 0. Q still exists as R's anchor; no missing-anchor excuse is needed.
6. A requests Redo R. There are no subsequent nodes to fail the suffix check.

Restoring R's recorded `1 → 2` over current 0, recomputing it as `0 → 1`, and
blocking it are different behaviors. The suffix-only predicate does not choose
between them. This is not the already settled case of an opponent's later
increment blocking reinsertion, nor a new live-action previous-value lock.

**Fix direction — contract completion first.** Specify target applicability,
recorded input/result fidelity and reversible transition requirements at the
insertion boundary before running suffix replay. Apply the same general rule to
Clone source snapshots, ordered moves, occurrence creation, and mandatory cleanup.
If recorded replay requirements determine rejection, document that derivation;
otherwise explicitly flag the remaining choice. Do not silently treat Redo as a
fresh D-187 invocation or demand old-value locks for ordinary live edits.

**Verification.** Add P/Q/R, missing live target, changed selected face/default,
and a removed/restored source. Check Redo retention/no skipping on failure.

## R-07 — Exposure has no complete lifecycle outside accepted online mutations

**Evidence.** [Exposure coverage](../design/03-contracts/history-and-undo.md)
lines 878–904 classifies what the interface actually displayed, not accepted
command names or remembered knowledge. [Inspection](../design/03-contracts/board-and-controls.md)
lines 263–276 exposes covered faces locally. D-182 requires downloaded read-only
access with no offline mutations; acceptance T-368 explicitly leaves newly logged
offline exposure unresolved. D-180 immediately predicts actions; D-185 corrects
rejected predictions without adding a gameplay node or branch.

**Counterexample A — offline.** Download a covered hand, disconnect, then hold a
card never explicitly inspected before. Showing retained face text follows the
inspection/read-only promise; recording every actual exposure calls for a new
record while disconnected. Denial, an unpublished local audit record, and no new
logging are not equivalent. Generic “read-only” wording does not say whether an
exposure audit record is an allowed non-gameplay record.

**Counterexample B — optimistic rejection.** A predicts drawing the covered top
card and sees its identity. B's earlier move makes A's source guard fail. A's
draw has no accepted mutation and no automatic branch, but the exposure happened.
Erasing the prediction cannot erase the knowledge. The same problem occurs if a
prediction displays a face before a possible rejection or process death.

**Fix direction.** Define one exposure lifecycle independent of successful board
mutation: trigger, actor, viewed boundary/revision, exposed references, durability,
publication, deduplication, and visibility after rejection/rollback. Explicitly
state the permitted read-only-offline behavior. Do not sneak an offline gameplay
queue or automatic conflict branch back in. A separate exposure record is a
candidate mechanism, not an approved product outcome in this report.

**Why this is not only schema work.** Whether inspection is available and whether
the opponent can later discover it are user-visible promises. Apply D-89/D-91/
D-182 first; any remaining incompatibility needs one focused resolution, not a
fresh question for every inspection entry point.

**Verification.** Cover both counterexamples, restart before publication, explicit
inspection on inactive history, and a multi-face inspector showing the other
printed face. Deduplication must not suppress genuinely different exposure.

## R-08 — Historical annotations need two contexts, not an unspecified placement

**Evidence.** [Historical pings/comments](../design/03-contracts/visual-action-language.md)
lines 96–99 prohibits non-contiguous groups or retroactive appearance. Lines
146–152 preserves selected timeline context; 204–206 says adding a historical
ping does not activate a continuation and calls placement a protocol decision.
Comments are ordinary current-path nodes (223–235). Groups are contiguous
non-overlapping path segments under domain invariant D-006. D-179 says passive
historical review is not branching intent.

**Counterexample.** Shared path is E → F → G. While personally reviewing E,
submit a ping/reply about E without choosing historical continuation. It must not
silently fork at E. Appending a record at G can preserve causal order but does
not by itself explain how it belongs to E's old contiguous group, when E's review
shows it, or which context its Ping/Unping toggle modifies. Inserting it back at
E changes intervening history and violates effective-time restrictions.

**Already derived.** Do not implicitly branch, rewrite E's payload, merge
non-contiguous groups, retarget a dependent reference or display future pings at
earlier boundaries. A comment referring to E protects E under D-224; that is
settled, not another gap.

**Fix direction — contract completion.** Distinguish the boundary where an
annotation was recorded from the historical event/object it refers to. Specify
current-head versus historical rendering, path membership, Undo ownership,
numbering/toggle scope, and dependency checks against those contexts. Separate
ping overlays from ordinary comment nodes where their existing contracts differ.
Only escalate a concrete residual contradiction after that mapping; do not call
observable context reassociation “just protocol.”

**Verification.** Add E/F/G, a second author, inactive E, Undo E with a dependent
annotation, and review before/after the annotation's effective boundary.

## R-09 — Normalize source targets separately from occurrence targets

**Evidence.** [Live projections](../design/03-contracts/domain-model.md) lines
405–410 leaves duplicate-source selection and endpoint routing open.
[Bulk controls](../design/03-contracts/game-actions.md) lines 326–348 applies
per-card operations; [Stack multiplicity](../design/03-contracts/combat-stack-and-projections.md)
lines 393–413 permits several views of one source and leaves duplicate selection
handling open. D-242 adds live sources in other locations and frozen read-only
sources.

**Counterexample.** Select real source X and its two Stack effects. +1 Red applied
once per selected visual view increments X three times; applied once per real
card increments it once. Remove effect, in contrast, must address each selected
occurrence separately. A drag into a real destination should not move X three
times or mistake later source-guard failures for three independent cards.

**Fix direction — derived distinction, finish the contract.** Define an
operation's target identity domain once: source-card operations normalize aliases
to real card IDs; occurrence operations retain occurrence IDs; historical
references preserve their own identities. Resolve targets and applicability
before emitting mutations. Do not turn two real independent token clones into
one target or merge independent Stack effects. Explicitly cover Clone and
frozen-source actions rather than assuming the generic rule settles every
multiplicity question.

**Verification.** One source plus several views, two independent sources, mixed
live/frozen effects, selected occurrence removal, Clone and projection dragging.
This should replace repeated per-view exception lists, not create new controls.

## R-10 — “Current Stack state” does not define semantic equality

**Evidence.** [Limited locking](../design/03-contracts/optimistic-locking.md)
lines 36–46 says equality is settled and only snapshot encoding/representation
remains technical. T-389–T-395 covers additions/removals/reorder and unrelated
non-Stack work. D-242 now makes displayed Stack card fields live aliases to
sources across locations.

**Counterexample.** Both clients see the same ordered Stack occurrences. B edits
a referenced source's corner text or moves it to Graveyard, changing the live
display/defaults but not Stack membership/order/placement. A's pending addition
contains the previously seen Stack state. If equality includes rendered source
fields it fails; if it compares occurrence structure only it can pass.

Those are different acceptance outcomes. A canonical JSON encoding cannot decide
which logical fields belong in the snapshot. A similar question applies to
source-disappearance snapshot changes, occurrence links and purely personal
presentation settings.

**Fix direction.** Define a semantic Stack-state projection before choosing an
encoding: identities, order, shared layout, source references and any other
intentionally guarded fields. Justify inclusions against D-187's limited-locking
purpose. Do not weaken the confirmed whole-Stack equality predicate to
top-card equality or an opponent-activity detector. Any proposed exclusion of
live source fields must be labeled a reconciliation proposal until provenance
supports it.

**Verification.** Add source-field edit, source move/reset, token disappearance,
link change and local-only sort/camera change while an addition is in flight.
Assert acceptance as well as serialization equality.

## R-11 — Grouping needs pending companions and a deterministic resolution model

**Evidence.** [Grouping algorithm shape](../design/03-contracts/history-grouping.md)
lines 26–49 says incoming X either appends to the current group or begins one.
The confirmed table at 61–79 requires forward taps, a special same-tuple
interleaving rule, isolated Stack entries, backward tracking/comments, and
forward closure/random runs. Remaining precedence is acknowledged at 124–142.

**Counterexample.** Same-author prefix is `Hand → Battlefield M1; tap T`.
If the next action is another matching move M2, D-164 puts all three together.
If it is isolated Stack entry S, the general forward-tap rule points T toward S.
A permanently append-only decision when T arrives cannot represent both results
without provisional companion handling or revising a group boundary later.
Other useful composition tests are `[View; dismiss; tracking; Stack entry]` and
`[edit; roll; tracking; edit]`.

**Fix direction — algorithm/contract completion.** Specify anchor categories,
pending forward companions, backward attachments, explicit exceptions and
highest-precedence author/path boundaries. State what is provisional while no
next anchor exists and what may change when a group grows. A provisional
presentation group must never delay durable mutation publication. Preserve
every exact node and ping effective boundary if markers are revised.

This is not a request to reopen the approved family memberships or ask the owner
every pair of action kinds. Derive the examples first; only irreconcilable
precedence needs a product choice. Drop excluded manual split/join from the R1
algorithm rather than using it as a recovery mechanism.

**Verification.** Use prefix-extension tests: rendering after each arriving action,
then final grouping, including a reviewer already inside the growing group.
Also compare batch versus incremental synchronization of the same path.

## R-12 — Distinguish a persistent lobby from each play-session incarnation

**Evidence.** [Returned lobby](../design/03-contracts/accounts-decks-and-lobby.md)
lines 349–382 reuses preparation and starts fresh play without reopening ended
sessions. [Recovery](../design/03-contracts/history-and-undo.md) lines 389–410
preserves original account/game/request context. The
[action envelope](../design/03-contracts/game-actions.md) lines 20–29 and
[protocol proposal](../design/04-technical/sync-and-storage.md) lines 120–151
do not explicitly scope every request to one play incarnation. T-539 protects
late acceptance/Undo after ending, but not a delayed ordinary action after a new
start.

**Counterexample.** An End turn request from session 1 has not committed. Opponent
accepts a previously submitted result, both return to the same lobby and later
start session 2. The old request is delivered/recovered against the lobby's
persistent game ID. It has no card ID or move-source guard to fail and is a valid
current-state relative action unless the session identity is checked.

**Derived outcome.** It must not advance session 2. That would retarget original
work to a different play session and defeat the fresh-start/non-reopening
boundary. Returning an already committed session-1 outcome must likewise not
reapply its snapshot to session 2. No new owner policy is necessary for that
safety consequence.

**Fix direction.** Specify play-incarnation scope for gameplay, Undo/Redo stacks,
result references and request recovery, distinct from persistent lobby identity.
Encoding/generation/fencing is technical work. Apply an equivalent scope audit to
invitation attempts when reinviting the same lobby, without inventing a new
duplicate-invitation policy. Do not add whole-head locks to ordinary same-session
edits or clear protected recovery records merely to avoid old deliveries.

**Verification.** Delayed/uncommitted and already-committed requests across ending,
return, fresh start, app restart and account reauthentication. Include an
object-free command such as End turn so incidental missing-card checks do not
mask the defect.

## R-13 — N is not a complete bound on work

**Evidence.** [Bounded Undo work](../design/03-contracts/history-and-undo.md)
lines 213–224 motivates N with responsiveness. Stack projections are unlimited
(combat/Stack section 13.12); Shuffle stores a whole-container permutation;
source removal can fan out into occurrence/link/snapshot cleanup. Bulk invocations
also contain a variable number of individual mutations.

**Counterexample.** A zero-suffix Undo can still restore a token with many linked
effects or process a large shuffle payload. One later mutation may inspect or
change a large collection. Limiting suffix node count alone does not cap those
costs, initial snapshot loading, reference validation or cache reconstruction.

**Fix direction — technical, not another gameplay limit.** Define the cost model
for target validation, suffix replay and dependent effects, with measured budgets
and appropriate indexing/incremental summaries or resource accounting. Preserve
the confirmed hard N and never accept an unchecked suffix. Do not silently add a
Stack count cap contrary to D-134 or reinterpret N as history expiry.

**Verification.** Measure worst-case fan-out/payload costs with short suffixes,
not only thousand-node histories. Specify correctness when a work limit is hit
and distinguish it from malformed input or a product count limit.

## R-14 — Unselected context-card targeting is a behavior choice, not geometry

**Evidence.** [Selection controls](../design/03-contracts/board-and-controls.md)
lines 220–230 preserves selection when opening Actions in selection-edit mode.
Lines 937–958 gives selection parity but explicitly leaves target scope for an
unselected context card open. T-127 repeats the omission. In contrast, ordinary
tap/drag on an unselected card has a precise clear-and-single-target contract.

**Counterexample.** A/B are selected. Double tap unselected C and choose +1 Red,
Move to Graveyard or Clone. Does the action affect only C while preserving A/B,
affect A/B, or add C to the scope? These produce different shared mutations,
counts and copied cards. Selection preservation alone does not select a scope.

**Fix direction.** Specify target binding separately from menu placement: the
context object, normalized action scope, when that scope is captured, and how a
selection change while the menu/editor is open affects it. Reuse ordinary
selection conventions where justified, without silently overriding D-34's
preservation requirement. Accessible Actions and projection/source menus need
the same binding rule. If existing decisions cannot choose the C-versus-A/B
outcome, present that one concrete scope choice, not every menu action.

**Verification.** Selected versus unselected context object, selection-edit mode
on/off, unfinished Set editor, changed selection and disappearing target. Assert
the mutated IDs, not just visible selection highlighting.

## R-15 — “Trim all user text” must be scoped away from credential assumptions

**Evidence.** [Text normalization](../design/03-contracts/domain-model.md) lines
33–41 and [board trimming](../design/03-contracts/board-and-controls.md) lines
865–870 says all user-entered text is edge-trimmed before validation/recording.
The later [account contract](../design/03-contracts/accounts-decks-and-lobby.md)
lines 19–33 requires email/password authentication while leaving validation and
normalization open. The broad rule does not explicitly include or exclude
credentials.

**Counterexample.** Register with password ` secret ` and later log in with
`secret`, or paste a password with a trailing newline. Trimming as generic text,
treating bytes as credential input, and rejecting whitespace at registration are
different authentication behaviors. Applying different defaults to registration,
login and reset can make the account unusable. Username/email equivalence also
must not be inherited accidentally from tabletop text formatting.

**Fix direction — account/security contract.** Scope D-59 to its approved
descriptive/user-content fields or explicitly reconcile credential handling.
Define consistent registration/login/reset credential rules, separate from
display-name/email normalization. This report does not approve a password policy
or select an authentication provider. Do not trim secrets merely because a
shared text editor helper does so.

**Verification.** Boundary whitespace and Unicode input through registration,
login and reset; preserve account-email privacy. Put account-specific assertions
in the authentication contract, not the tabletop string-pattern tests.

## Fix-session plan

1. **Establish one current normative surface (R-01/R-02).** Keep stable IDs, make
   superseded cases explicit, remove excluded scope from current deliverables.
   Do not append another global precedence disclaimer as the whole fix.
2. **Complete generic transition contracts (R-03–R-06/R-09).** Define command
   scope, target normalization, target applicability, invariant closure and replay
   dependencies separately. Then derive action-family rows and adversarial tests.
3. **Complete context and information contracts (R-07/R-08/R-10–R-12/R-14).**
   Separate play identity, recorded boundary, referenced boundary, source identity,
   occurrence identity and personal presentation. Do not make them all one “base.”
4. **Keep technical/security work correctly classified (R-13/R-15).** Define
   required outcomes before representations; do not require every library/schema
   choice to finish behavior specification.
5. **Synchronize affected tiers and verification.** Prefer the existing primary
   contract plus linked summaries, not a new parallel requirements document.
   Use synthetic fixtures and real-backend/normal-Android E2E designs under
   D-243/D-244, supplemented by model/protocol/property tests. No test or app
   implementation is authorized by this report.

### Candidate owner-review batch — only if derivation does not settle it

The next session should attempt spec-derived resolutions before asking anything.
The strongest concrete candidates are R-05's retained-future validation scope,
R-06's changed Redo target transition, R-07's offline/unaccepted exposure lifecycle,
R-10's semantic Stack comparison,
and R-14's unselected-context target scope. These are **not five approved new
questions**. Batch any demonstrated residual choices with existing-spec outcomes,
sources and clearly labeled proposals, as D-237 requires. R-08/R-11 should first
receive coherent general context/grouping contracts, not separate per-sequence
approval requests.

### Not findings / do not reopen

- Latest-own Undo, no skipping, server-ordered distinct Undo requests, strict
  Redo order, player-specific clearing and cross-device stacks are settled.
- D-233/D-234's fork restrictions and potentially stranded Redo entry are explicit
  approved limitations, not defects to “repair” with a new recovery branch.
- A dependent comment blocking removal is settled. Personal deleted-node viewing
  has parent fallback; a surviving node shows its state in the edited tree.
- Graveyard/Exile are unordered; Library order dependencies remain significant.
- D-242's live Stack sources and token-only last-live read-only fallback are
  settled; do not revive freeze-on-zone-departure or creation-time snapshots.
- Ordinary action races use current applicability and arrival order. Rejected
  ordinary work does not implicitly branch; result-suggestion races are settled.
- Exact wire IDs, N's numeric value, animation timing, hit geometry, notification
  cadence, provider choice and test framework are not inherently product gaps.
- No finished-game archive, extra authoring tool, manual group editor, offline
  gameplay queue or later delivery stage is authorized by this review.

## Completion criteria for the documentation fix

- One discoverable current outcome per active requirement/scenario ID.
- Concrete outcomes for every counterexample above, with proposals distinguished
  from approved or spec-derived behavior.
- Invariants at every published boundary and deterministic forward/backward replay.
- Accepted history, pending predictions, exposure records and personal review do
  not silently substitute for each other.
- A documented reason when an item is technical/later UI rather than a missing
  observable behavior; no blanket “implementation detail” escape hatch.
- Markdown links/anchors, stable-ID consistency and whitespace checked; only
  task-owned paths saved with `./scripts/save.sh`, committed and pushed under the
  project instructions. Report blockers without bypassing checks.
