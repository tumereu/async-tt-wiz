# Adversarial specification review — October 20 follow-up

Reviewed baseline: `57f6a753bcdfc339a59244ec050bf66a86cadcb6`.

## Outcome

**F-01 is withdrawn following the owner's correction (D-254).** It incorrectly
treated UI/menu state as defining an authoritative group. UI actions send requests;
the server creates events, and deterministic logic groups those events. The
conflicting menu-bound-group wording has been corrected, not promoted into another
owner decision. F-02–F-06 remain review findings, not accepted resolutions.

This is a static review of the five design tiers, current decisions and the
previous reviews' dispositions. Counterexamples are reasoning fixtures, not
executed tests. No application exists. **This report changes no product rule or
acceptance oracle, selects no proposal and authorizes no stage transition.**

The earlier R/S findings are not reopened wholesale. In particular, keep D-251's
tap boundaries, D-252's side-sensitive holds, D-253's constituent-only full-path
requirement, D-245's retained-future checks and D-247's Stack comparator.

| ID | Priority | Finding | Classification |
| --- | --- | --- | --- |
| F-01 | Withdrawn | The review assumed a Ping menu binds an authoritative group. | Incorrect premise; D-254 confirms server-created events and deterministic grouping. |
| F-02 | High | Successful Redo has no explicit shared-pointer result when its insertion boundary is an activated internal state. | Observable control-contract completion, not merely anchor encoding. |
| F-03 | Medium | Protected late exposure can refer to a history revision already removed or changed by an eligible Undo. | Derived durability/reference completion; causal participation needs an explicit trace. |
| F-04 | Medium | Ordered lowering needs an explicit non-placement outcome for disappearing tokens in mixed moves. | Derived algorithm/verification completion, not a new token policy. |
| F-05 | Medium | The complete category-transition matrix is not yet a total mapping of confirmed gameplay actions. | Concrete missing mapping: pending result suggestion. |
| F-06 | Medium | Active contracts still require combined full paths and retain superseded grouping language. | Confirmed-rule editorial repair; no owner decision needed. |

Priority denotes specification risk, not an observed software defect.

## F-01 — Captured ping scope versus a later recording event

**Withdrawn, not a product gap.** The owner specifically rejected this ping
finding: UI actions do not define groups. Menu target binding under D-248 is not
group binding. The server processes requests, creates effective events and groups
those events deterministically. Current-context explicit Unping of an absent
marker is a no-op, not amendment of an old group, conversion into Ping or a new
stale-group lock. T-602 and the primary grouping/annotation/composition contracts
record the corrected request/event distinction. The unrelated findings below
were not selected or resolved by this correction.

<details>
<summary>Original F-01 reasoning — withdrawn premise, retained as review history</summary>

**Evidence.** [Grouping](../design/03-contracts/history-grouping.md), lines
117–130, makes the recording group the operational Ping/Unping scope and binds
that group and target when the context menu opens. It forbids reinterpreting
recorded toggle scopes. [Annotations](../design/03-contracts/visual-action-language.md),
lines 106–134, instead places a newly submitted historical annotation at current
shared context, with its **owning group equal to its recording group**. The older
referenced event is separate. [Context binding](../design/03-contracts/board-and-controls.md),
lines 231–243, prevents later selection changes from redirecting the bound targets.

**Trace.** Alice's current move group G contains ping X. On her phone she opens
X's menu, offering Unping and binding G/X. Before she invokes it, her tablet taps
an unrelated card, starting group H under D-251. All object IDs and G still exist.
Repeat with the request already sent but delayed until after the tablet's tap,
and with an intervening opponent action instead.

An Unping bound to G removes G's X. Rebinding to current H addresses a different,
initially unpinged context. Treating G as the referenced event but H as the owner
does not preserve the original Unping operation either. One possible derivation
is that the existing ping keeps owner G while its later removal is recorded at H,
so owning-group-equals-recording-group refers to **creation**, not every subsequent
effective change. That distinction is not explicit in the common scope rule.
Repeat with initially absent X and a captured **Ping** command: there is then no
existing ping record whose original ownership can settle the new addition.

**What is already determined:** no silent cross-event removal, no reinterpretation
as Ping, no retroactive overlay, no automatic branch, and no change to D-251.
Stable IDs and Ping-mode exit alone do not decide a still-open context command.

**Required repair:** give bound ping commands a complete context-lifetime rule,
including existing-record removal versus a new addition, when the original scope
becomes inapplicable and how an already-sent request is handled. First apply
stable-record identity and original-scope applicability; do not silently approve
either stale-group rejection or old-group amendment as a new blanket policy.
Closing a menu before submission cannot alone settle a request already in flight.
Distinguish the bound owning scope from the historical *referenced* event and
effective recording context explicitly.

**Verification:** extend T-565/T-570/T-584 and GM-21 with menu-open → context
change → submit, delayed arrival, Ping versus Unping, selected targets and both
same-account/opponent interleavings. Assert addressed record, effective placement,
ordinals, group ownership and final marker presence. T-584 currently opens the
menu **after** the new tap group exists and does not settle this trace.

This is a residual composition issue, not a request to reconsider tap grouping or
add a ping-ownership picker.

</details>

## F-02 — Redo's tree result does not fully specify its active state

**Evidence.** [History](../design/03-contracts/history-and-undo.md), lines
109–121 and 196–203, defines recorded-transition reinsertion and stack order.
Lines 235–248 explain retained-future validation and give an explicit **Undo**
pointer example. Lines 311–321 and 861–864 separate shared activation from tree
editing. They do not give the corresponding successful-Redo pointer transition
at an activated internal boundary.

**Trace, with no missing anchor, dependency, exposure, fork or budget problem:**

1. Retain `root → A → B`, with Alice's independent tap A and Bob's note B.
2. Alice undoes A, producing `root → B`, B current, A queued for Redo at root.
3. Bob explicitly activates root. B remains its single retained future. This is
   not Alice's new gameplay, so it does not clear her queue.
4. Alice requests Redo A. Its recorded input matches root and B's tuple is
   unchanged. Reinsertion yields `root → A → B`.

The resulting **tree** is clear. Is shared current state now A, or still root?
Activating B automatically would contradict the retained-future distinction, but
excluding that option does not choose between A and root. The difference is
visible immediately and decides where the next ordinary edit applies.

The normal leaf case must also be explicit: Undo A from `root → A`, then Redo,
must not accidentally restore a retained node while leaving the playable board
at root merely because the pointer's old node survived.

**Required repair:** specify successful-Redo shared-pointer behavior relative to
the insertion boundary, separately from personal cursors and continuation tips.
Preserve D-245's no-automatic-future-activation and D-238's surviving personal
cursors. A proposed rule to advance current state to A when reinsertion occurs
immediately after its selected boundary would resolve this example, but **this
report does not select it**. Establish the derivation or identify that exact
remaining choice; node IDs cannot determine it on the owner's behalf.

**Verification:** extend T-558/T-562 and the retained-future E2E journey with
successful Redo at root/internal activation, an empty retained future and an
independent retained future. Assert tree, shared pointer, both personal cursors,
stack consumption and the parent used by the next ordinary edit.

This is narrower than reopening Undo/Redo policy or every stale activation race.

## F-03 — Late exposure recovery after its viewed revision was edited away

**Evidence.** [Exposure lifecycle/participation](../design/03-contracts/history-and-undo.md),
lines 943–955 and 980–1005, retains viewed revisions, pre-send disclosure and
causal control participation. Lines 1007–1011 promise a route to original context.
[Reference lifetime](../design/03-contracts/domain-model.md), lines 777–786,
distinguishes historical absence and dependencies, but its ordinary dependency
case assumes the reference already exists when a tree edit is validated.

**Trace.** At node A, Alice's phone durably captures an explicit hidden-side
inspection and shows the content, but has not published the exposure. Her tablet
has no knowledge of it. A distinct server Undo removes latest-own, otherwise
eligible A before the exposure arrives; alternatively an earlier independent
mutation is removed, revising surviving A's board. Subsequent own play can clear
A's Redo entry. The phone then recovers the protected exposure.

The service cannot have rejected an earlier valid Undo based on a record it had
not received. Conversely, rejecting the later exposure solely because its viewed
node/revision is no longer current would break the established durability promise.
Rebinding to A's revised state or its parent would misreport what was shown.

**Derived constraints, not new proposals:** retain the actual captured content
and original revision/provenance; do not recreate A as an active gameplay branch,
overwrite accepted newer work, substitute a different viewed context or mistake
legitimate historical absence for a malformed ID. D-238 still governs personal
cursors; it is not permission to rewrite the exposure's provenance.

**Required completion:** specify the discoverable read-only provenance route when
no matching node/revision remains in the current tree, and distinguish capture
time from authoritative effective ordering for latest-own/Redo participation.
Apply D-184's server processing order rather than silently backdating a shared
barrier using client clocks. Physical retention/encoding follows these outcomes;
this is not a universal snapshot feature for every reference type.

**Verification:** extend T-560/T-595/T-600 with exposure publication delayed across
eligible tree deletion, surviving-node revision and Redo clearing. Check original
shown content, current board/tree, discoverability, control order, idempotent
recovery and no recreated branch. Existing Logout tests preserve an outstanding
record but do not exercise its history context being edited meanwhile.

## F-04 — Successful disappearance is not a destination prefix anchor

**Evidence.** [Ordered lowering](../design/03-contracts/operation-composition.md),
lines 64–91, emits S in order and inserts later members after the preceding
successfully placed member. [Token moves](../design/03-contracts/board-and-controls.md),
lines 1094–1112, require mixed selections to remove tokens and move ordinary cards
normally. Tokens never occupy the configured Library destination.

**Trace.** Move captured `[token T, ordinary C]` from Hand to Library top `[X]`,
without concurrency. T disappears successfully. C must still move to top, yielding
`[C,X]`; a successful request for T must not produce an after-T insertion anchor.
With `[C,T,D]`, C becomes the destination prefix anchor, T disappears and D should
insert after C. All three individual boundaries must be valid.

“Successfully placed” can reasonably already exclude T. That is the derived
interpretation, **not a claim that the token policy is undecided**. The current
algorithm/examples and T-591–T-593 do not explicitly cover this composition.

**Required completion:** distinguish an accepted disappearance, a surviving
placement/no-op anchor and an invalidated formerly live prefix anchor. Skip the
first when constructing destination-prefix dependencies, but do not turn the last
into permission to choose a new gap. Preserve original sequence and all ordinary
entry resets. Add mixed-token top/bottom and mulligan-return traces; a source
token does not become an invariant-excluded Library tray member.

## F-05 — A transition matrix needs a total action classifier

**Evidence.** The [grouping matrix](../design/05-verification/grouping-matrix.md),
lines 10–33 and 64–69, maps literal families and requires operation coverage.
[Pending result suggestions](../design/03-contracts/accounts-decks-and-lobby.md),
lines 322–356, are confirmed ordinary gameplay mutations, unlike non-undoable
acceptance. Yet no matrix category names them. The
[grouping inventory](../design/03-contracts/history-grouping.md), line 252, still
mixes result handling with proposed setup/lifecycle work.

**Trace.** One author records `card note; suggest draw; tap; move` while the
suggestion remains pending. Is the suggestion part of E, an isolated anchor,
a backward companion or outside grouping input? Those choices produce different
groups and can change which context a subsequent ping addresses. Being
“undoable board state” settles tree participation, not this category.

**Required completion:** explicitly map pending suggestions in the central
classifier, separating them from acceptance/return to lobby. An E-family mapping
is a plausible proposal, **not established by this report**. Derive from current
family scope if possible; otherwise batch the narrow remaining mapping rather
than inventing a whole end-game grouping policy. Test leading, preceding-edit,
tap-run, following-ping and author-change contexts. Do not call 156 transition
cells exhaustive operation coverage while a confirmed input is unmapped.

## F-06 — Residual active contradictions need direct edits

These are not unresolved owner choices:

| Active location at baseline | Contradictory or obsolete assertion | Current source to apply |
| --- | --- | --- |
| [Game actions](../design/03-contracts/game-actions.md), lines 83–86 | Targeted **combined** shortcuts must have full paths. | D-253, P-035, operation composition; only constituents require parity. |
| [Combat](../design/03-contracts/combat-stack-and-projections.md), lines 121–125 and 674–678 | Both declaration combinations require full context paths; nothing is quick-only. | D-253 and corrected T-269. |
| [Android](../design/03-contracts/android-experience.md), lines 24–26 and 49–58 | Every combined quick action needs a full path; advance-and-close has non-quick access; absent-area behavior is open. | D-253 and T-597; absent closure is a no-op. |
| [History](../design/03-contracts/history-and-undo.md), lines 623–625 | Same-tuple moves group “including interleaved taps.” | D-251: a tap after an anchor starts another group; matching moves never merge backward across it. |
| [Grouping inventory](../design/03-contracts/history-grouping.md), lines 238–239 and 262–264 | Lists move grouping with interleaved taps and says not to reopen same-tuple tap interleaving, without the new boundary qualification. | D-251 and GM-01/GM-03/GM-04. |
| [Visual worked example](../design/03-contracts/visual-action-language.md), lines 292–293 | Says the player can Undo an interruption “to return.” | Undo edits that branch; return/activation is separate (history lines 747–748 and 849–853). |
| [Domain Message entity](../design/03-contracts/domain-model.md), line 66; [sync messages](../design/04-technical/sync-and-storage.md), lines 255–260 | Generic edit/delete revisions and message edit/delete remain open without distinguishing excluded submitted-chat/comment controls. | D-240/D-241; delivery bookkeeping is not a revision-feature promise. |

Update the assertions themselves, not another precedence disclaimer. Historical
decision entries remain provenance and should not be rewritten as if the older
decisions never existed. Other old “open” labels should be narrowed only after
applying their current rules, not closed by a broad completion claim.

## Recommended next batch

1. F-01 is withdrawn; do not reopen it. Review F-02's exact pointer transition. Attempt
   derivation first; batch only any genuinely remaining observable choices.
2. Complete F-03/F-04 against existing durability, identity and token rules.
3. Finish F-05's classifier and synchronize F-06's active text with current rules.
4. Add stable acceptance cases and full-stack journey variants for those traces.
   These remain specifications until implementation is authorized.

Randomness/prediction agreement also needs a technical proof obligation: an
ordinary uncontended shuffle-and-draw must work, not routinely fail its captured
draw guards because client/server sampled unrelated orders. The current contract
correctly permits partial results when accepted Shuffle input differs; that is
not a reason to reopen substitution, add a routine acknowledgement barrier or
approve a particular randomness authority/algorithm here.

Replay budgets/N, IDs/schema, transport/recovery encoding, provider rights,
retention/erasure, notification cadence and final Android UI remain real work in
their existing technical/service/UI lanes. This review does not convert them into
new gameplay decisions or certify the rest of the specification as exhaustive.
