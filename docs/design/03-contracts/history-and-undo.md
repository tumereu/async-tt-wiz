# 3. History, review, and undo

This is the core behavioral contract. Branching, recovery, instant sync, low-tap
UX, and automatic conflict branching are owner decisions; detailed mechanisms
below remain proposed where not explicitly confirmed.

## 3.0 Current Undo model — parent-pointer navigation (D-159/D-160)

**Confirmed.** The game has one
shared active game-state pointer, separate from each participant's review cursor.
It can point to an internal node even when that node has retained descendants.
Undo moves this pointer to its parent: one tree node, regardless of author.
It does not search for the requesting participant's last action, invert an old
edit while keeping later edits, or undo an entire presentation/invocation group.
The older historical-rollback preview proposal is not the Undo interaction.

Retain the undone nodes and their original outcomes. Reviewing them alone does
not activate them; explicitly making a retained node current can redo that state.
Undo is a shared control change, not another board-action child node. It targets
a specific node-to-parent transition; if that transition is no longer valid,
discard it silently and reconcile local state (D-161; section 3.7).

After Undo, making a new game edit creates a different continuation from the
active node. Do not delete the old continuation. If it has no qualifying exposure
or event comment, hide it from the normal tree and expose it through a
**show hidden branches** toggle. Hiding is presentation filtering, not deletion,
loss of recovery, or participant access control. This automatic hiding rule does
not authorize hiding unrelated interruption/conflict alternatives.

D-160 keeps the **whole abandoned continuation visible** if it contains a
qualifying exposure or event comment, including ordinary steps after the last
such node. This replaces D-91/D-95's visible-path cutoff, not D-89/D-91's exposure
classification or D-95's inclusion of event comments.
Ordinary undone history must remain available for forward return before a new
edit too; its exact pre-fork presentation is not yet specified.

Approved event boundaries do not change. Thus Draw + advance still produces two
nodes: Undo from its final node returns to the post-draw Draw-step state; a second
Undo returns to the pre-draw state. The one-node declaration, Untap + advance,
and advance-and-close shortcuts still take one Undo to reach their predecessor.
This replaces D-131's whole-invocation scope, not its distinct-reaction-point
requirement. Composite actions may require multiple Undo presses.

## 3.1 Three positions that must never be confused

- **Shared head / active state:** synchronized current game-state pointer. It may
  be an internal node with retained descendants after Undo, not the furthest
  published node on a stored continuation.
- **Review cursor:** boundary currently displayed to one participant.
- **Local head:** provisional active state including that participant's completed
  actions/control changes awaiting synchronization. Incomplete form input is not
  a game-state boundary.

The UI labels which state it displays. “Current board” means shared head, not
“the actions both people have reviewed.” A review cursor may lag the shared
head by many actions. Advancing it does not write another game-state action.

Instant synchronization updates downloaded history and the shared-head indicator;
it does not automatically advance a historical review cursor. An optional live
follow mode can track the shared head. Local edits advance the author's local
view immediately, with pending status until acknowledged.

The published future is a proposed continuation in the players' shared play,
even though it is the selected shared branch in the storage sense. The other
participant may interrupt any of it later.

## 3.2 Atomic actions and interruption boundaries

A recorded atomic action corresponds to an explicit operation, not every internal
field write. Examples: move a card, rotate it, add a ping, or change a named
counter. Distinguish this from a user action/invocation, which may produce multiple
recorded events/actions in one invocation. Each node is a separate Undo step
(D-159/D-160). Do not combine separate
user actions because the engine guesses they form a game mechanic. A deliberately
selected generic bulk operation can be atomic where no required response boundary
is hidden.

An interruption is possible at the root or after any published atomic action,
including an action inside a group. There is no boundary inside an atomic
action. Before committing a compound action, the player can inspect its parts;
when intermediate response points matter, record separate events/actions, which
may still belong to one user invocation/presentation group, but remain separate
Undo steps (D-159).

The engine does not infer/enforce response windows, casting, payment, or resolution.
D-131 requires the behavior specification to provide separate events wherever
MTG permits reactions at distinct points within a user action. These are real
review/interruption boundaries, not decorative rows hiding one indivisible
transition. Players still judge timing; there is no priority state, Pass, or
runtime card-rule adjudication (D-105). Preserve independently completed operations
so participants can choose the right interruption point themselves.

Example sequence, with only literal records:

1. Rotate card A.
2. Move card B from Hand to Stack.
3. Add a ping to the opponent's player marker, associated with action 2.
4. Move card B from Stack to Graveyard.
5. Change the counter labeled Life from 20 to 17.

The opponent may stop after action 3. Actions 4–5 remain on the old branch when
the opponent creates another continuation. The engine never calls the sequence
“cast B at opponent and resolve”; that interpretation belongs to the players.

## 3.3 Immediate action synchronization

**Confirmed:** completed game actions synchronize automatically, without Send.

Proposed action lifecycle:

1. The user completes a direct operation or the necessary inputs for an action.
2. Persist its state transition and outbound operation atomically, and display
   the result locally without waiting for a network round trip.
3. If connected and dependencies permit, begin publication immediately. Do not
   wait for a group to end, a timer, screen exit, or another user action.
4. The service validates structural preconditions and commits history, branch
   changes, and notification outbox records atomically.
5. Mark the local result synchronized on acknowledgement; otherwise retain it
   visibly as pending, retryable, or failed work.

Publishing submits the intended base boundary, continuation context, revision
preconditions, action data, and group references. “Publish” names a protocol
operation, not an extra interaction. Transport batching is allowed only without
deliberately waiting to gather a group; every action boundary remains intact.

- An action at the unchanged active head extends that continuation.
- The first completed action at an earlier boundary creates and activates a new
  branch atomically when synchronized. Further actions extend that new line.
- A concurrent game-edit conflict automatically creates a branch at the intended
  base, preserving both continuations. It does not wait for a conflict dialog.
  See [synchronization](../04-technical/sync-and-storage.md).
- Offline actions use the same local workflow and synchronize automatically on
  reconnect. Local branch activation remains provisional until acknowledged.
- Incomplete selections/forms may be cancelled without shared effects. Once an
  action is completed, use Undo rather than assuming it is still private.
- Do not require a pre-send review, editing mode, group completion, branch name,
  or explanation before synchronizing.

Immediate means starting synchronization as soon as connectivity and operation
dependencies allow, not promising zero network latency or Android background
execution that the platform cannot guarantee. Exact latency targets remain open.

## 3.4 Sensible grouping

Groups affect presentation and notifications, never atomicity or interruptibility.

D-162–D-171 specify sequence-based presentation groups and timeline group-start
markers. Same non-Stack move tuples group, including interleaved taps; real-card
Stack moves and Stack projection creations remain separately visible. Ordinary
card edits, manual face/cover edits and token removal group together. Combat
projection creation/repositioning groups together; View/Reveal dismissal/combat
closure belongs with the following action. Same-target
Shuffles group; dice/coin runs start separately from prior play and attach to the
following action. Phase/turn tracking joins the previous group. Same-author pings
add no group-start marker, and Undo/resuming does not artificially reset grouping. See the
[primary grouping rules and open precedence cases](history-grouping.md) for scope,
the highest-precedence author-change boundary, remaining action families and
the MTG face-change rules check. Presentation groups still never change node-level
Undo or interruption. Exact action/event mappings are not all complete;
one invocation = one group is not adopted.

D-129 distinguishes invocation, tree event, and presentation group. D-159/D-160
set **one parent-node move per Undo**, replacing D-131's whole-invocation scope.
D-131's reaction-point requirement remains: multiple MTG reaction points need
separate effective tree events/interruption boundaries, without extra invocation
taps. Multiple Undo presses are acceptable. Event count/placement still needs
case-by-case specification. Presentation groups and optional split/join never
change parent links or Undo stepping. Schemas follow behavior, not vice versa.

The [literal-action presentation](visual-action-language.md) groups related
recorded operations and their action-linked pings. For example, a move to the
Stack area and its pings can appear as one expandable unit without being
classified as a cast. Completed constituent actions still sync immediately and
keep their own boundaries; the unit is not one mutable historical node. Later
changes cannot retroactively alter an earlier boundary. Notes/messages remain
optional.

Under D-75 pings belong to just one group/event. Their overlays are not carried
onto later event states, but the records remain viewable when reviewing that
step in the event tree, including offline for downloaded games. This visibility
rule does not merge atomic boundaries or settle automatic group formation.

D-116 requires separately invoked combat-area creation, attacker declaration,
and tapping to remain distinct user actions when invoked separately. D-117's
combined multiselect shortcuts use the confirmed event mappings. D-148
records Declare + tap, including any automatic area creation, as one tree event:
no review/interruption between declaration and source tapping. One Undo restores
prior orientations and occurrence/slot/area context. D-149 also makes Declare
without tapping one event, including any automatic area creation. D-150/D-151
fix normal-step Untap + advance as one event and Draw + advance as two, with one
Undo per node (D-159); see section 3.11. D-119
requires ping numbers across the single grouped event; exact ordering mutation
and split/join/coalescing effects remain behavior questions, not permission to
merge actions or lose historical ping context.
Exact within-group effective boundaries and later ping revisions remain open.

D-124/D-125 require area-specific projection lifetime in recorded states: source
departure removes combat projections/incident links, whereas Stack effects
survive until explicitly removed/resolved. Retain enough occurrence/source/link
context for normal undo, branch review, and downloaded offline recovery, including
Stack effects with removed sources. Exact cleanup operation/undo composition and
concurrent reference handling remain under review; do not apply one universal
projection-removal rule in replay or recovery.

Proposed grouping rules:

- Group using explicit interaction context, contiguous actions, associated
  pings, author/session boundaries, and optional manual split/join. No card-text,
  area-name, or game-mechanics inference and no required intent-selection dialog.
- Start a new group at an explicit user split, a context change according to
  approved generic heuristics, or an interruption. D-163 confirms no combined
  authors; other proposed separators still need review.
- A group can be open and grow as actions synchronize. An opponent can review
  it immediately without waiting for closure. Closing a group is presentation
  metadata, not a gameplay commit or required user action.
- The user can optionally rename, split, or join adjacent compatible groups
  after publication. These reversible metadata edits synchronize automatically;
  they do not change action payloads, boundaries, or review positions.
- Never infer a priority pass, spell resolution, or turn completion from elapsed
  time, a card animation, or a group ending.
- Boundaries and action summaries remain visible when a group is collapsed.
- A review control opens the next group but advances one action at a time.
  “View group result” is an explicit shortcut, not the only navigation mode.

D-166 refines normal display/area-close progression: show the revealed/combat
state, then the following group result including dismissal plus its next action,
without a mandatory standalone dismissal stop. Detailed node-by-node inspection
remains available; the older proposed review-control wording above must not force
an extra stop between that closure and the next action.

Exact generic grouping heuristics remain an open specification item. They must
work without extra routine taps or interpreting game mechanics. A threshold may
close a presentation group or coalesce notifications, but must not delay
synchronization or imply a game-rule event.

## 3.5 Interruption and active-branch selection

D-105 removes priority tracking, UI, and priority-legality checks. Participants
judge when an MTG response is legal; the engine records the chosen interruption
at an action boundary without requiring a priority-holder or Pass record. Keep
normal structural validation, cooperative access, and branch-preservation rules.
Queueing/publishing a continuation does not imply the other player waived a
response opportunity or accepted its legality.

For a path `root → A → B → C → D`, interrupting after B with X creates
`root → A → B → X`. The old path through C and D remains recoverable. On
successful publication, the branch ending at X is active.

Proposed application of the same selection rule to automatic conflicts: a newly
created conflict branch becomes active when committed. Service commit order,
not device timestamps, serializes simultaneous branch activations. The displaced
line is retained, labeled, and available through explicit return/activation.
Normal Undo goes to the active node's parent, not automatically to the displaced
line's former tip.

Activation occurs on branch creation, not every subsequent append. If another
line later becomes active, already queued descendants continue on their assigned
branch without repeatedly reclaiming active selection. This avoids automatic
branch-selection ping-pong. A participant can explicitly reactivate that line.

The operation records:

- Fork boundary B, original continuation/branch, and its current head D.
- Author and optional reason, new branch ID, reaction actions, and new head X.
- Previous active selection, expected revision, and resulting control revision.

No second-player approval is required. A visible activity entry explains the
switch. A participant reviewing the old continuation is notified and can choose
to inspect it or navigate to the divergence; the UI must not silently teleport
their cursor past unreviewed actions.

Either participant can also activate an existing non-archived branch explicitly.
Activation changes the selected continuation, not the contents of either line.

The preserved suffix is not automatically replayed on top of a reaction. It may
refer to objects, positions, or values that are no longer present. A future reuse
tool must preview and validate every copied action; automatic replay is out of
the proposed first release.

## 3.6 Review progress

Review controls: previous action, next action, previous/next group, jump to a
selected boundary, view shared head, and interrupt here.

- Review progress is personal and may synchronize across a person's devices.
- Own actions are distinguishable from unseen actions by the other person.
- Reading an action does not prevent a later interruption at an older boundary.
- Branch changes preserve review positions on retained paths.
- The next required catch-up starts at the last common reviewed boundary on the
  active path; actions on an abandoned suffix do not count as seen on a new path.
- A manual jump to the head marks actions as skipped/viewed in summary, not as
  individually stepped through. Whether to expose this distinction to the other
  participant remains a product decision.
- Notifications and read markers are not simulated MTG priority passes.
- Newly synchronized actions can extend an open group while it is being
  reviewed. Update unseen counts without resetting the cursor or requiring the
  participant to reopen the group.

## 3.7 Full undo contract

Requirement U-001: **Every user-authored logical game edit has a discoverable
undo or recovery path, except submitted game-wide chat (D-94).** This includes
state edits, random actions, draft edits, publication, grouping, event-linked
comments, setup corrections, branch switching/archival, and result markers.
Submitted game-wide chat is not undoable. Chat edit/delete, unsent-delivery
cancellation, and ordinary text-editor draft behavior remain separate decisions.

Undo preserves the immutable tree and moves the shared active pointer one parent
back (D-159). It has no author filter, inverse-patch/dependency analysis, or
whole-invocation targeting. Later work stays on retained descendants rather than
being transformed or erased. At the root there is no earlier game-state node.
To repair a value while keeping later play active, perform its normal edit now;
this does not require a separate historical-Undo mode or correction wizard.

After replacement play, ordinary abandoned continuations are hidden by default
and discoverable through Show hidden branches. An exposure or event comment keeps
the entire affected continuation visible, including trailing ordinary nodes
(D-160). Hidden nodes retain identity, payloads, ancestry and stored random results;
the filter never changes game state. See section 3.9 for exposure classification.

Advance-and-close remains one Undo because D-132 makes it **one node**, containing
both tracking and complete area cleanup. D-148/D-149 declarations and D-150 normal-
step Untap + advance likewise have one node each. D-151 normal-step Draw + advance
has **two nodes and two Undo steps**: first restore Draw tracking while keeping
the drawn card, then return to the pre-draw state. Required reaction boundaries
must not be collapsed to reduce Undo taps. Interruption at a composite action's
intermediate node follows the same tree model; later constituent events remain
on their original continuation, not replayed onto the interruption.

Game-node Undo and recovery of non-node controls are distinct. Branch archival,
metadata changes and unfinished input still require discoverable recovery under
U-001; their control-specific recovery must not redefine normal Undo as a stack
of heterogeneous commands. Exact controls for those recovery paths remain proposed.

| Original operation | Required recovery behavior |
| --- | --- |
| Pending action not yet transmitted | Move the provisional pointer one parent back; retain its node/outcome for forward return. Exact queue representation remains technical design. |
| In-flight action with unknown outcome | Show the local pointer move immediately; resolve original delivery and synchronize the specific transition without losing the node or requiring a duplicate ordinary Undo press. If no longer valid, silently discard and reconcile (D-161). |
| Published current node | Move the active pointer to its parent, regardless of author or invocation/group membership. Retain descendants. |
| Older action followed by later play | Normal Undo still moves only the current active node to its parent. Repeated Undo or explicit tree activation can select an older state; do not selectively invert the old action or preview a suffix deletion. |
| Interruption | Normal Undo goes from the current node to its parent/fork. Explicitly activate the original retained continuation to return to its future, or use Delete branch and return. |
| Activate another branch/node | Normal Undo follows the newly active node's parent, not the previous selection. Previous states remain available through explicit tree activation. |
| Delete/archive branch | Provide branch restoration and metadata recovery separately from game-node Undo; restore selection if explicitly requested. |
| Group metadata edit | Provide recovery of previous title/boundaries without changing action nodes, parent links or game-node Undo stepping. |
| Submitted game-wide chat | No game Undo (D-94). Chat edit/delete and unsent-delivery policies remain open; do not promise generic message Undo. |
| Event-linked comment undone | Move to its parent. Retain the whole abandoned continuation visibly under D-160; no separate comment-only fork is needed. |
| Game completion/concession marker | Reopen the game at the prior logical state. |
| Die/coin result | Restore prior board state while retaining the information-gaining action/result as a history branch (D-89/D-90); redo reuses that result. Other random-operation classification remains open. |

Undo is a synchronized control move, not another board-action child node: its
control record can retain prior/new references without becoming the next gameplay
parent. This avoids Undo merely undoing a previous Undo record on the next press.
Redo through the tree selects the original stored state/outcome, not a new action
invocation or random sample. Review alone never changes the active pointer.

Rapid Undo must work before, during, and after acknowledgement. Pending/in-flight
nodes and pointer intentions must survive restart/retry without disappearing or
being applied twice.

**Confirmed stale-Undo behavior (D-161):**

- Capture the intended transition from a specific node to its parent when Undo
  is invoked. Do not publish a generic “step back from whatever is current”
  command or retarget the request after another participant changes state.
- If that transition is still valid, apply it once. If it is no longer valid,
  discard the request silently and reconcile the local current-game state, without
  moving an unrelated personal historical review cursor.
  No toast, warning, new notification/activity entry, confirmation or failed-work
  retry task is needed; the corrected client state is the feedback.
- Example: the client sees C and requests C → B, but D extends C before it applies.
  Leave D active and reconcile the client; do not undo D or jump from D to B.
- If both participants request C → B, one can apply it; the other becomes stale
  and is silently discarded, not reinterpreted as B → A. A deliberate second Undo
  based on the new B state is a different B → A transition.
- Reconnecting applies the same rule to offline/provisional Undo. Discard only
  the invalid control request, not retained game nodes or other queued edits.
  Further edits retain their intended bases and use normal publication/conflict
  rules; never silently rebase or erase them to reconcile the pointer.
- Delivery retry of the same request uses its recorded outcome/idempotency
  identity. If it already succeeded, return that result rather than applying it
  again or treating the changed pointer as a new stale rejection. Resolving an old
  result must not overwrite newer synchronized state with an obsolete snapshot.

Non-Undo branch switches/archival and exact forward-selection controls still need
their own stale-target behavior; D-161 is not blanket approval for every control.

## 3.8 Deleting an active interruption

“Delete this branch and return” is a single atomic control operation:

1. Show the branch to be archived and the exact return continuation and board.
2. Archive the selected branch and select the chosen return branch together.
3. Leave all action nodes and descendants recoverable.
4. Offer recovery of both archive state and branch selection. This is distinct
   from normal game-node Undo (D-159); exact recovery-control wording is open.

Default return target: the continuation displaced by this interruption, at its
currently retained head. If that target has changed, is archived, or is missing
from local storage, do not guess: show a selection/restore flow. The exact root
branch cannot be archived if doing so would leave no active branch.

Archiving a branch does not implicitly archive its descendant branches; show
descendants and offer an explicit, reversible multi-branch operation if desired.
Shared prefix actions are never deleted by branch archival.

## 3.9 Limits of undo

- A reveal can be reversed on the board but cannot erase a person's memory.
- Delivered push notifications cannot reliably be retracted.
- Exported files or screenshots cannot be recalled.
- An external account deletion or legally required data erasure is not a
  recoverable in-game operation. Its policy must explicitly disclose that.
- Navigation has Back/history behavior rather than shared game undo.
- Submitted game-wide chat is explicitly not undoable (D-94); event-linked
  comments instead remain in their tree paths even after undo (D-95).

The UI must explain these boundaries instead of promising physical reversibility.
No history compaction or retention policy may silently remove the promised
in-game undo capability.

### Information-gaining actions and undo branches

Confirmed D-159/D-160: every undone continuation is retained. After replacement
play, hide ordinary abandoned continuations behind Show hidden branches, but keep
the whole continuation visible if it contains an exposure or event comment.
This replaces the older ordinary-node removal and last-exposure cutoff, without
requiring permanent board markers or ping overlays.

Confirmed exposure coverage (D-89): library View/Reveal, otherwise-covered hand/
card-face viewing, covered-face hold inspection, and die/coin results. Ordinary
inspection of already-visible faces and every search keystroke are not included.
This records what the interface showed/generated, not what a player remembers.
D-91 generalizes this to any action exposing otherwise-hidden information,
including draws, moves, and face changes that display covered information to
either participant. Classification depends on actual interface exposure, not
the command name, printed game rules, or a guess about the person's knowledge.
An ordinary visible-face gesture/keystroke is not itself an exposure record;
if its result actually exposes hidden information, that exposure still qualifies.
Covered-face inspection logs an exposure without requiring a board-state edit
or automatic activation of a new gameplay continuation; exact record placement
remains open.

Retain actor, operation/source context, exposed references or concrete result,
and a route to the original step in the retained history. Distinguish undone/
inactive-line status. Inspecting that branch does not itself activate it or
reapply board changes. Branch archival cannot erase the promised exposure
record; exact discoverability and archive presentation remain open.

Ordinary summaries report that viewing occurred without automatically showing
covered card names/faces to the other participant. Original context remains
inspectable under cooperative full access; rolls retain actual recorded results.
"Permanent" here means retained across normal undo/branch operations, not an
exemption from eventual game/account erasure. Exact triggers/coalescing,
historical/offline views, durable capture/publication, schemas, and retention
remain Q-31. No competitive anti-cheat guarantee is implied.

D-95 includes event-linked comments as retention points alongside hidden-info
exposure. A submitted event comment is another node on the current tree path;
that path is already a branch like any other. Apply the ordinary chosen-base/
path rules, not a separate comment-only fork/activation mechanism. Undo moves
back while preserving the existing path. D-160 keeps its whole abandoned
continuation visible, including ordinary steps after the comment; there is no
last-comment cutoff.
This differs from D-94 game-wide chat, which is outside game Undo. Exact comment
group association, revision/edit/delete, and dependent/concurrent protocols remain
later review, not an unresolved question of whether paths are already branches.

Confirmed branch-level filtering (D-160, replacing D-91's cutoff):

1. Retain every node of the abandoned continuation after replacement play.
2. If that continuation contains any exposure or event comment, keep all of it
   visible, including ordinary ancestors/intervening/trailing nodes.
3. Otherwise hide it by default and make it available through Show hidden branches.
4. Visibility does not move the active pointer or hide unrelated alternatives.
   An exposure in the shared prefix alone does not make an ordinary abandoned
   suffix an exposure-bearing continuation.

Example: from `root → A → View B → C → roll D → E`, use five Undo presses to
return to root, then create X. Keep the whole original continuation A–E visible,
not only the prefix ending at D. For an all-ordinary A–E, retain the same nodes
but hide that alternative by default. Either person can inspect/activate retained
states; inspection alone never activates them. Exact exposure capture/coalescing,
pre-fork presentation, forward-navigation and non-Undo control details remain open.

## 3.10 Randomness and branching

D-87 confirms die/coin results as automatically queued/synchronized records of
actor, request, and actual result. Review/redo reuse that concrete result rather
than rerolling; these tools do not automatically change card or counter state.
Exact random source/algorithm, offline execution policy, and action-boundary
protocols still require review; touch entry is in the board contract.

D-92 supersedes D-88's opponent-action seed-reset rule: every explicit new die
roll or coin flip gets a fresh random draw, including a new invocation after
undo or on another branch. No gameplay seed epoch/draw-position/reset mechanism
is required for these tools. A fresh draw may coincidentally equal an earlier
result; do not force a different value.

Distinguish a new invocation from review, replay, redo, or retransmission of the
same operation: those reuse its stored outcome/idempotency result, never resample.
An undone die/coin result remains in history under D-89–D-91; fresh generation
does not erase that information branch. Exact generator/source and offline/
concurrent operation protocols remain open, without reopening seed-reset policy.

D-93 confirms the same rule for Shuffle: every explicit new invocation samples
a fresh order, including after undo; redo/review/retransmission reuse its recorded
order. A fresh shuffle may coincidentally reproduce a prior order. No gameplay
opponent-action seed lifecycle is required. D-93 does not expand shuffle controls
beyond the approved Shuffle all scope or by itself settle hidden-info exposure
classification for a covered shuffled order.

Shuffle, die roll, coin flip, and random selection are explicit actions with
stored outcomes. Re-rendering, reopening, replaying, or redoing an action must
not draw new randomness.

A new random-action invocation gets a fresh identity and retained context.
D-92/D-93 make new die/coin/Shuffle invocations fresh draws, not new outcomes for
transport retries. Store the concrete result/order with the operation. Full
information access means this is not a competitive fairness guarantee.

Offline and online execution must store concrete outcomes that deterministic
replay can apply identically. The algorithm and entropy policy must be specified
before implementation; see the open decisions.

## 3.11 Confirmed shortcut event mappings (D-149–D-151)

These mappings distinguish an effective tree event/interruption boundary from a
user invocation. D-159/D-160 make each node a separate Undo step. They do not
authorize runtime priority,
Pass, timing/legality adjudication, or automatic card-text effects.

| Invocation | Recorded events | Undo scope |
| --- | --- | --- |
| Declare without tapping (D-149) | One event: declaration and any automatic area creation together; preserve source orientation. | One Undo restores prior occurrence/slot/grouping/order/area context. |
| Untap all + advance during Untap (D-150) | One event: literal scoped untapping and Untap → Upkeep together. No separate response point inside Untap. | One Undo restores prior source orientations and phase/step. |
| Draw one + advance during Draw (D-151) | Two events: draw portion while tracking remains Draw, then Draw → Precombat main. The intermediate state is genuinely reviewable/interruptible. | First Undo returns to Draw while keeping the drawn card; second Undo returns to the pre-draw state (D-159/D-160). |

For Draw + advance, show the actual resulting card/location state after the first
event while still in Draw. Do not collapse the two events into an indivisible
transition or merely decorative rows; an opponent can branch at that boundary.
One invocation still completes without a second tap or Send. Completed events
queue/sync under normal immediate-publication rules, not a group-completion gate.
The preserved advance on the original continuation must not silently replay onto
an interruption branch. Undo/interruption at either node uses the ordinary
parent-pointer model; exact concurrent
publication protocols remain open.

D-111 still applies: no affected untap cards does not prevent Untap → Upkeep;
an empty Library draws no card but still advances, without automatic loss. Record
actual effects, not fictitious untaps/drawn cards. The approved event counts and
Draw-step boundary remain; exact no-change event wording/feedback remains open.

Undo retains both nodes. If replacement play abandons a suffix containing an
actual draw exposure, D-160 keeps its entire continuation visible, including the
ordinary advance. If only the advance is undone/replaced, the draw is in the
shared prefix; that ordinary abandoned advance can be hidden. Never erase the
draw or sample a new outcome merely to return forward through the tree.

Separately invoked constituent actions keep separate invocation/event/Undo
identities. These are specific normal-step mappings, not permission to rewind
tracking or infer event counts for unrelated/out-of-step combinations. Remaining
concurrency, no-change presentation, and reaction-point exceptions need design.
