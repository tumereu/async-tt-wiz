# 3. History, review, and undo

This is the core behavioral contract. Branching, recovery, instant sync, low-tap
UX, deliberate interruption and limited optimistic locking are owner
decisions; detailed mechanisms below remain proposed where not explicitly confirmed.

**Initial-delivery connectivity (D-182):** making game actions and shared controls
requires a connection. Downloaded history remains readable/reviewable offline;
personal traversal is not shared Undo or activation. Do not create offline game
mutations or an action queue for later synchronization. D-180 optimistic updates
and D-187's permissive acceptance/limited guards apply online. Connection loss
during an already-started online request is a narrower recovery problem, not permission to
continue playing offline; its exact outcome/retry flow remains open.

D-183 clarifies that online-only does **not** mean one action at a time or no
local send buffer. A small local queue holds individual actions for concurrent
sending. Normal interaction applies locally immediately and must not routinely
wait for network acknowledgements. This is online latency hiding, not extended
offline play or an atomic queued sequence.

<a id="30-current-undo-model--parent-pointer-navigation-d-159d-160"></a>

## 3.0 Current Undo/Redo model — editing the mutation tree

**D-224–D-235 supersede parent-pointer-only Undo/Redo.** Undo removes your latest
still-applied mutation from the existing tree; Redo reinserts it, under the safety
and stack rules below. These operations edit the tree, **not create branches**.
Personal review and explicit historical activation remain distinct; their control
mapping, interaction with tree edits and view-follow behavior remain open.
Do not infer acceptance of the assistant's shared remembered-Redo-head proposal.

### Selective mutation Undo/Redo — D-224

**Confirmed safety rule:** allow deletion/insertion of a mutation in a timeline
only if **every subsequent mutation has an identical relevant-state `(from, to)`
tuple in the original and candidate timelines**. Replay the subsequent mutations
in their recorded order and compare both their pre- and post-relevant states,
not merely operation applicability, equal deltas or equal final board state.
If any subsequent mutation cannot replay or either side of its tuple differs,
the selective Undo/Redo is unavailable. This is not an appended compensating edit.

**Tree editing — D-232:** Undo/Redo alter the existing mutation tree, not create
a revised branch beside an untouched pre-Undo path. On an otherwise eligible
linear path `root → A → B`, removing A produces `root → B`; reinserting A at the
corresponding boundary restores `root → A → B`, subject to replay validation.
The labels here identify logical mutations, not an approved node-ID/version schema.
Do not retain an original alternative continuation solely because Undo/Redo edited
the path, duplicate the suffix as a new branch, or append a compensating mutation.
The removed mutation is available through the player's Redo stack, not a required
explorer branch. Clearing that stack does not restore the removed node; persistence
or recovery of removed records after clearing remains open.

Explicit interruptions can still create branches; the explorer/filtering rules
apply to branches that actually exist. Undo itself does not acquire historical
continuation intent or require the next ordinary current-head edit to fork.
The D-233 fork boundary below prevents Undo from changing existing side branches.
Event-comment/ping references, review cursors, IDs/versioning and cached states
still need explicit handling. This decision does not approve silently deleting
an unrelated branch/comment or a
specific retargeting policy. Nor does it require physical storage erasure or
select an immutable-log versus mutable-record implementation.

Each mutation kind must define its **relevant state**. Compare the same mutation's
original accepted transition against its candidate transition, preserving target/
field identity and recorded choices/results; do not substitute another object
with an equal value. Relevant state is not automatically the whole board or even
the whole card: the owner explicitly allows removing a card move while retaining
a later tap of that same card. Exact per-mutation state definitions remain work
to do; this rule does not approve the superseded 25-rule locking table.

Owner examples:

- Same counter: your `0 → 1`, then opponent's `1 → 2`. Removing your increment
  would make theirs `0 → 1`: **Undo unavailable**, despite the same +1 delta and
  both increments being valid normal online actions.
- Move a card, then opponent taps it: **Undo of the move is allowed** when the
  move is otherwise removable and the tap's relevant before/after state is
  identical. Keep the tap; do not compare unrelated position as part of tapping.
- Tap one card, then opponent untaps another: **Undo of your tap is allowed**
  when the later mutation's relevant tuple is unchanged.

**Container-order clarification — D-225/D-226:** Library is ordered; Graveyard
and Exile are unordered, with no order-related actions. Relevant state includes
ordering dependencies when the operation depends on them, not only fields it
writes. With Library `[A, B, C]` top first,
removing an otherwise eligible move of A before a later draw of B is unavailable:
replay must neither draw A instead nor silently take B from below the top.
Conversely, moving A to Graveyard and then independently moving B there permits
removing A's move when otherwise eligible. Graveyard/Exile contents/absolute indices
must not create an ordering dependency for B's move; keep B's membership and
other relevant transitions unchanged. This is not a whole-container contents lock.
D-226 removes the earlier Graveyard/Exile top/bottom, reorder and other
order-related controls, including visual-placement variants. Browser sorting is
still presentation only; do not infer ordering policy for other containers.

The same suffix-tuple test applies to **Redo reinsertion**. In this selective
design, Undo is not offered for a target that revealed new information; this is
distinct from reviewing/activating a retained historical state. Replay reuses
stored random results and exposure context, not new random samples or UI reveals.

**Redo invalidation after new play — D-227/D-228:** after Undo, only your own new
gameplay edit clears your Redo queue. Previously removed mutations do not remain
available through Redo merely because reinsertion would still pass D-224's tuple
test. D-232 does not require an explorer copy of the removed path; retention or
recovery of removed records after queue clearing remains open.
The confirmed clearing case is a successful new gameplay mutation by the player
who used Undo. Opponent actions do **not** clear that player's queue; retain its
entries and recheck reinsertion against current state under D-224, including N.
An opponent edit may make Redo unavailable without clearing the queue. Clearing
is player-specific, not a shared game-wide reset. D-235 below confirms cross-device
stack synchronization. Rejected/no-op requests and exact optimistic/server clearing
timing remain open. Do not generalize this to personal review/navigation or treat
Redo reinsertion itself as new play that necessarily clears remaining entries.

**Shared game state across devices — D-235:** each player's Undo/Redo stack
belongs to that account in that game and synchronizes across their devices; it
is not device-local. Undo on a phone can be Redone on a tablet, subject to the
same current eligibility checks. Latest-own targeting includes that player's
actions from any device. Successful Undo/Redo and stack clearing are reflected
across devices, including last-undone-first order and blocked entries.

Shared game state does not mean one author-independent stack for both players:
preserve D-231 own-action targeting and D-228 player-specific clearing. A successful
new gameplay edit from either of your devices clears your stack for that game;
an opponent's action still does not. Exact simultaneous-device/stale-request
handling, sync timing and protocol remain open. This decision does not approve
transfer of pending-request queues, unfinished input or personal review cursors.

**Redo order — D-229:** restore the **most recently undone mutation first**,
like a normal Undo/Redo stack. If you Undo A and then B, successful Redo restores
B and then A. This is the order of Undo operations, not a sort by the mutations'
original timeline positions. Each reinsertion still requires D-224's relevant-state
tuple test within N; insertion anchors are not settled by this ordering rule.
**D-230 blocked-entry rule:** if that next mutation cannot be reinserted, disable
Redo and keep the stack intact. Do not skip to an older eligible entry or discard
the blocked entry. Opponent activity still does not clear the stack (D-228).

**Undo target order — D-230/D-231:** Undo may target only **your most recent
still-applied action on the current timeline**, even if the opponent acted later.
Never target an opponent's action or an arbitrarily selected earlier own action.
Later opponent mutations remain applied and must pass D-224's unchanged-tuple
replay test. If that next Undo target
fails the replay checks, exceeds N or is information-revealing, disable Undo;
do not search backward for an older eligible target. After successful Undo, the
preceding still-applied own action becomes the next candidate. If there is no own
action, Undo is unavailable; do not fall back to an opponent's action. This
author filter is not permission to skip a blocked latest own action. The rule
governs Undo, not personal review or explicit historical activation; interaction
with the latter remains open. Do not replace it with parent-pointer Undo.

**Fork boundary — D-233:** Undo cannot remove a branching point or reach back
past one. A branching point is a tree node with multiple child continuations;
if the target itself or any node on the path from it to the current active head
is a branching point, disable Undo even if all replay checks would pass. Do not
skip the blocked latest own target, reattach the alternatives or replay all branches
to make removal possible. Explorer filtering does not remove a branch and cannot
bypass this boundary. Actions strictly after the latest fork remain candidates
under the usual latest-own, replay, exposure and N rules. For example:

- `root → A`, with children B (active) and C (alternative): A cannot be undone.
- `root → A → F`, with F's children B (active) and C (alternative): A cannot
  be undone either; Undo would reach back past F.
- In the same tree, an eligible own action B can be undone without removing F
  or changing C. No branching point lies at or after B on its active path.

**Redo fork boundary — D-234:** if a fork created after Undo would require
reinserting the queued mutation before that fork, disable Redo and retain the
stack. Do not skip/discard the blocked entry, reinsert it at the current head,
rewrite all branches or create a recovery branch. This is a structural block
even when relevant-state replay would pass. Opponent branching does not clear
your stack (D-228); your own new gameplay edit still clears it (D-227).

The owner explicitly acknowledged that the historical mutation may then have
no way to be applied again with the current controls. Keeping the stack is not
a promise of eventual replay or a requirement for a new recovery control.
Performing a similar operation as a fresh gameplay action is not restoring that
historical mutation and remains subject to ordinary acceptance and queue clearing.
Exact insertion anchors, archive/restore effects and integration with explicit
historical activation remain open; do not assume they provide an escape hatch.

**Confirmed bounded work:** impose a hard limit **N** on the subsequent actions
subject to replay/validation, so a target followed by e.g. a thousand opponent
actions cannot trigger a noticeably slow eligibility calculation. A valid but
over-limit candidate is unavailable; do not validate only a convenient prefix or
permit a changed unchecked tail. Exact N and counting of mutation nodes versus
multi-mutation invocations remain open; no time-based grouping or gameplay limit
is implied.

**Still open:** complete relevant-state definitions, insertion target's own preconditions,
Redo insertion anchors,
invalidation cases beyond D-227/D-228, concurrent/stale checks, pending-request
interaction, pointer/view
updates, tree/node versioning, grouping and references to removed/replayed nodes.
Removal changes the existing tree (D-232), not just a candidate branch. D-223's
visibility rule still applies to actual remaining alternatives; affected comment
references and side branches need separate handling. Earlier parent-pointer
fixtures below describe that superseded rewind model, not blanket approval
to use it instead of this selective rule. U-001 and older exposure/Redo recovery
wording must be interpreted with this distinction until controls are settled.

### Earlier parent-pointer rewind model — scope under revision

**Earlier confirmed model (D-159/D-160/D-222).** The game has one
shared active game-state pointer, separate from each participant's review cursor.
It can point to an internal node even when that node has retained descendants.
Undo moves this pointer to its parent: one tree node, regardless of author.
It does not search for the requesting participant's last action, invert an old
edit while keeping later edits, or undo an entire presentation/invocation group.
The older historical-rollback preview proposal is not the Undo interaction.

Retain the undone nodes and their original outcomes. Reviewing them alone does
not activate them; explicitly making a retained node current selects that state.
Undo is a shared control change, not another board-action child node. It targets
a specific node-to-parent transition; if that transition is no longer valid,
discard it silently and reconcile local state (D-161; section 3.7).

**Confirmed branching and Redo clarification (D-222):** the game-state tree has a
root containing the initial state; each subsequent node records a mutation.
When Undo moves back, **remember the last active head and Redo toward that head**.
For `root → A → B → C`, Undo to B and then A remembers C; forward Redo follows
A → B → C, not a guessed child or a mandatory path picker at every fork.
Retained alternative children do not by themselves make this remembered path
ambiguous. Restore recorded states/results, without new mutations or randomness.

After Undo, interrupting/making changes branches from the selected node and makes
the new continuation the active head. Keep the previous continuation; Redo is
not a way to replay its mutations onto the new branch. A **graphical tree
explorer** lets either player review and **explicitly activate any retained
branch/node**, including an internal node or the root. Activation changes shared
current state; viewing/scrubbing alone remains personal. A new edit after such
historical activation continues from that selected state under D-179.

**D-222/D-223 visibility:** hide inactive continuations that consist of **only one
player's actions, no hidden-information exposure and no event comments**, with a
separate **Show hidden branches** toggle in the explorer. Mixed-player,
exposure-bearing and event-comment continuations stay visible in full, including ordinary tails.
Hiding is presentation filtering, not deletion or access control; the active path
and retained states remain available. Classify the alternative continuation,
not the shared prefix; exact nested-branch presentation remains open.

**Confirmed comment exception (D-223):** an event comment keeps the **whole
continuation visible even if all its actions are by one player and no hidden
information was exposed**. This explicitly preserves D-95/D-160's exception
alongside D-222's single-player filtering condition. It affects explorer visibility,
not shared activation, grouping, information-exposure classification or retention.

The remembered-head scope across participants/devices, concurrent/stale Redo and
tree activation, memory updates after explicit activation, and detailed pre-fork
presentation remain open. D-161's stale-Undo rule is unchanged and is not blanket
approval of the same policy for other controls.

D-176 clarifies the node unit: **one individual mutation**, not a bulk operation,
combined shortcut or playback group. One Undo still follows one parent. Earlier
one-node combined-action mappings are superseded wherever they combine distinct
mutations. For an actual draw followed by advancement, Undo first restores Draw
tracking while keeping the drawn card, then restores the pre-draw state. Bulk
and other combined invocations likewise require one press per constituent node.

## 3.1 Three positions that must never be confused

- **Shared head / active state:** synchronized current game-state pointer. It may
  be an internal node with retained descendants after explicit activation, not the furthest
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

A node records **one mutation to game state** (D-176): for example, moving one
card, tapping one card, or changing one counter. Its recorded transition supports
deterministic application and rollback. A user invocation can issue many such
operations; it is not itself a node. Bulk movement, tapping, edits, Clone and
occurrence removal issue individual operations in a sensible recorded order.
Keep existing source/spatial ordering defaults; do not invent a sorting workflow.

Every mutation is immediately recorded locally and published. D-179 refines its
accepted parent: an ordinary edit is validated and appended at the current shared
head, not necessarily the client's last-seen node. Only deliberate continuation
intent authorizes branching from a historical node. D-232 Undo/Redo instead edit
the existing tree and do not supply interruption intent. Persist and publish each online mutation under normal dependency
constraints. Neither invocation completion nor animation/group completion is a
publication boundary. The client state pointer always identifies a mutation node
(or the root), never a group.

An interruption is possible at the root or after any published atomic action,
including an action inside a group. There is no boundary inside an atomic
mutation. Every intermediate mutation boundary remains navigable, including
inside a bulk invocation or concurrent group animation. Normal playback steps
may span a group; pausing and timeline scrubbing select an exact node.

The engine does not infer/enforce response windows, casting, payment, or resolution.
D-176 supersedes the older policy of choosing node granularity by possible MTG
reaction windows. Node granularity follows individual mutations, not inferred
game timing. Players judge when to respond; there is no priority state, Pass,
or runtime card-rule adjudication (D-105).

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

**Confirmed connection-loss presentation (D-215):** when disconnected, show a
**Reconnecting...** dialog that **blocks gameplay actions**. D-182's connection
requirement includes shared Undo and branch controls; do not create offline
mutations behind the dialog. This is an actual connection-loss gate, not a
routine wait for each online acknowledgement. Preserve downloaded read-only
access; exact dialog navigation/dismissal and reconnect-readiness criteria remain
open. The dialog does not declare unresolved requests failed. D-216 separately
confirms automatic recovery below.

**Confirmed reconnect recovery (D-216):** after reconnecting, **automatically
recover already-sent online actions**, without manual resending or executing an
action twice. Preserve each original request's identity/conditions and recover
its actual outcome; any delivery retry belongs to that same request, not a fresh
user action. Already-committed mutations and recorded random results must not
be applied/generated again. Reconcile accepted/rejected outcomes using D-185;
recovery does not turn a rejected action into a new automatically resubmitted
action, cancel other valid work or silently retarget requests. Missing responses
remain unresolved until recovered, not presumed rejection.

This is recovery of online requests that were already sent, not permission for
offline gameplay or approval of an offline queue. Exact outcome lookup/retry
protocol, reconnect readiness, recovery ordering and treatment of online-buffered
but not-yet-sent actions remain open. Preserve D-161's silent handling of stale
Undo, D-184 delivery ordering and D-183 responsiveness during normal online play.

**Session continuity (D-217):** app restarts preserve login while the session is
valid. If it expires, ask for login again; after successful same-account login,
return to the same game and recover pending already-sent actions under D-216.
Authentication expiry is not proof that an action failed and must not erase its
recovery context. Exact session mechanics and logout/account-switch handling
remain open; see [session continuity](accounts-decks-and-lobby.md#session-continuity-and-reauthentication--d-217).

**Confirmed presentation (D-180):** update local game state immediately to hide
network latency; do not wait for server acceptance before showing the action. If
the server rejects it, animate the local mutation backward as if it were undone.
The reversal is the explanation, not a required textual error/confirmation flow.
This is local reconciliation, not a shared Undo request, new mutation, historical
continuation intent or branch activation. Incoming mutations from the other
participant are also applied with animations, rather than unexplained board
replacement. Preserve D-177 grouping and personal historical review positions;
incoming history does not silently move a historical cursor.

**Confirmed online pipeline (D-183):** queue actions individually in a small local
send queue and send concurrently. Each action applies or fails individually under
its own D-187 validation/exception guards; one rejection is not a whole-sequence
rejection or a reason to cancel another valid action. Do not gate normal interaction behind an
earlier acknowledgement or turn the queue into an atomic bulk commit. D-180
animations reconcile individual results and incoming opponent mutations.

**Confirmed ordering (D-184):** each client sends actions in the order it made
them; the server validates and applies them in arrival order. Keep multiple
actions in flight without waiting for earlier acknowledgements. Preserve order
in delivery rather than adding per-action dependency metadata or server-side
reordering to reconstruct client intent. Actions from the other player may
interleave; each action still passes or fails its own current-head checks. A
rejection does not block evaluation of later arrivals.

**Confirmed reconciliation (D-185):** reconcile the displayed current board
against accepted history plus remaining valid local predictions. Animate only
the actual difference needed to reach that state; do not visibly rewind/replay
unaffected work. Preserve accepted local and opponent changes and still-valid
pending predictions. If a move fails but the following tap succeeds, animate
the card back to its authoritative location while keeping it tapped, without
briefly untapping it or restoring a stale whole-card snapshot. Correct later
predictions that genuinely become invalid too, rather than cancelling the entire
pending sequence. Acknowledgement alone causes no extra animation or duplicate
application; newly learned authoritative differences still need reconciliation.

This is presentation reconciliation, not permission to manufacture server results,
retarget pending requests or change their identity/conditions. Preserve unresolved
request context until its actual result is established. D-177's deliberate history
traversal and interruption-resume rollback/playback remain distinct and unchanged;
D-185 removes unnecessary visible rewind/replay from ordinary online reconciliation.

Acknowledgement of a predicted mutation must not apply it twice. Animation does
not gate persistence, publication or server acceptance. Exact animation scheduling,
ordered-transport selection, reference handling, queue sizing/saturation and
connection-loss recovery mechanics remain open; these must preserve D-216's
automatic/no-duplicate/no-manual-resend behavior, concurrent sending,
individual outcomes and D-185 difference-only corrections rather than introduce
a blanket wait-for-response interaction gate. Final timing, reduced-motion/
accessibility presentation and layout remain later design work.

Proposed action lifecycle:

1. The user completes a direct operation or the necessary inputs for an action.
2. Persist its state transition and outbound operation atomically, and display
   the result locally without waiting for a network round trip.
3. If connected, begin publication immediately in client action order. Do not
   wait for a group to end, a timer, screen exit, or another user action.
4. In arrival order, the service validates structural preconditions and commits
   history, branch changes, and notification outbox records atomically.
5. Mark the local result synchronized on acknowledgement; otherwise retain it
   visibly as pending, retryable, or failed work.

Publishing submits the requested operation and any limited movement/Stack guards,
explicit interruption intent and its chosen boundary when applicable, action data,
and group references (D-187).
Exact wire fields remain technical design. “Publish” names a protocol
operation, not an extra interaction. Transport batching is allowed only without
deliberately waiting to gather a group; every action boundary remains intact.

- Without explicit historical-continuation intent, attempt the action at the
  current shared head. A changed head alone is not grounds for rejection or a fork.
- **Confirmed default acceptance (D-187):** apply ordinary actions to current state
  unless performing the requested operation is genuinely impossible. Changed
  orientation, text, counters or a game/card revision is not by itself a conflict.
  Absolute edits write the requested value; relative edits operate on current
  applicable data. Do not compare previous field values for taps, notes, Set or
  other ordinary edits. Preserve unrelated fields and record actual accepted
  before/after data for deterministic review/Undo, not a stale whole-card snapshot.
  Access, valid input/references, applicability and structural invariants remain;
  this is not permission to edit another game or perform arithmetic on unusable
  text. Keep existing operation meanings, not arbitrary substitution or new effects.
- Optimistic conflict guards are **exceptions**, currently card movement and
  making Stack projections/entries. They can reject stale intent even when an
  unguarded operation could be performed. Do not spread them into general
  compare-previous-value checks. The [limited-locking policy](optimistic-locking.md)
  supersedes the earlier 25-rule proposal, not approves it.
- Example: moving a card into an empty position requires that the card still be
  at its stated source and the destination still be empty. Another unrelated
  action may have occurred without invalidating either check. This example does
  not prohibit approved drops onto occupied ordinary piles; those actions need
  their own destination conditions.
- **Stack safeguard (D-187, clarified by owner):** moving a real card to Stack or
  creating a Stack projection includes the client's current Stack state in the
  request. Compare it with authoritative current Stack state when processing;
  if different, reject that request. Compare and apply atomically, so two additions
  with the same expected state cannot both change Stack successfully. This guards
  against unintentionally covering a concurrent opponent entry, but uses **state
  equality**, not separate opponent/seen/read-receipt detection or a whole-game
  head check. Subsequent local additions use the locally predicted Stack state
  including earlier queued additions; do not wait for acknowledgements. Exact
  snapshot encoding/equality representation and provisional-ID mapping remain
  technical design, not the compare-and-reject behavior.
- **Counter update (D-186 as revised by D-187):** +/- still applies its delta to
  current numeric data. Set now writes its requested value without comparing the
  previous number/text. A stale Set may replace a concurrent adjustment in arrival
  order; record the actual overwritten value for Undo. D-187 supersedes D-181's
  orientation/note comparisons and D-186's Set comparison, not relevant-field
  preservation, numeric applicability or D-185 reconciliation.
- Only an explicit UI choice to go back and continue, recorded as an interruption
  flag with the selected historical boundary, authorizes a new historical branch.
  Its first accepted mutation creates and activates that continuation. D-232
  Undo/Redo edit the existing tree instead of supplying continuation intent.
- Initial delivery does not permit offline actions or reconnect replay of an
  offline gameplay queue (D-182). While online, local acceptance and branch
  activation remain provisional until acknowledged. D-180 specifies animated
  rejection feedback, composed under D-185. Retain recoverable online work;
  exact in-flight recovery and animation scheduling remain open.
- Incomplete selections/forms may be cancelled without shared effects. Once an
  action is completed, use Undo rather than assuming it is still private.
- Do not require a pre-send review, editing mode, group completion, branch name,
  or explanation before synchronizing.

Immediate means starting synchronization in send order as connectivity allows,
without waiting for acknowledgements, not promising zero latency or Android
background execution that the platform cannot guarantee. Exact latency targets
remain open.

## 3.4 Sensible grouping

Groups control navigation/playback steps and representation, never mutation
creation, publication, parent links or interruptibility (D-176/D-177). Changes in
one group animate concurrently during a playback step; they remain an ordered
sequence of individual nodes. The client pointer is a node, not a group.

D-162–D-175 specify sequence-based presentation groups and timeline group-start
markers. Same non-Stack move tuples group, including interleaved taps; real-card
Stack moves and Stack projection creations remain separately visible. Ordinary
card edits, manual face/cover edits, token removal, player values/counters and
persistent-link creation/removal group together. Token creation and Clone group
together with interleaved customization edits. Event comments join the preceding
group without starting a new one. Combat-area creation and projection
creation/repositioning/removal group together. Each explicit View/Reveal opening
starts a separate review group. Stack rearrangements group separately from
entries/removals; each explicit Stack-effect removal stays separately visible.
Bulk removals issue individual occurrence-removal mutations (D-176). View/Reveal dismissal/combat
closure belongs with the following action. Same-target
Shuffles group; dice/coin runs start separately from prior play and attach to the
following action. Phase/turn tracking joins the previous group. Same-author pings
add no group-start marker, and Undo/resuming does not artificially reset grouping. See the
[primary grouping rules and open precedence cases](history-grouping.md) for scope,
the highest-precedence author-change boundary, remaining action families and
the MTG face-change rules check. Presentation groups still never change node-level
Undo or interruption. Exact action/event mappings are not all complete;
one invocation = one group is not adopted.

D-174 forbids non-token Remove/delete entirely: cards move between locations,
not out of the game. Token removal/disappearance remains supported, distinct from
projection removal. There is no automatic-grouping question for a non-token
Remove action, and this restriction does not change pointer-based Undo/history.

D-176 distinguishes invocation, individual mutation node and playback group.
D-159/D-160 set **one parent-node move per Undo**. Multiple mutation nodes do not
require extra invocation taps. Specify constituent mutations and their order,
not whether bulk/combined edits deserve an atomic node. Grouping and optional
split/join never change parent links or Undo stepping.

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

Combat-area creation, individual projection declaration and individual source
tapping remain distinct mutations even when a shortcut invokes them together
(D-176, superseding D-148/D-149's combined-node mappings). Untap all likewise
issues individual untaps; phase advancement is another mutation. See section
3.11. D-119
requires ping numbers across the single grouped event; exact ordering mutation
and split/join/coalescing effects remain behavior questions, not permission to
merge actions or lose historical ping context.
Every mutation remains an effective boundary; exact ping revision/attachment
semantics remain open.

D-124/D-125 require area-specific projection lifetime in recorded states: source
departure removes combat projections/incident links, whereas Stack effects
survive until explicitly removed/resolved. Retain enough occurrence/source/link
context for normal undo, branch review, and downloaded offline recovery, including
Stack effects with removed token sources. Exact cleanup operation/undo composition and
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
- D-177 supersedes the older next-action-only playback proposal: a normal playback
  step spans a group and animates its changes concurrently. Exact mutation-level
  navigation remains available through the paused timeline.

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

**Confirmed D-179:** reviewing, pausing or scrubbing to an old state is not itself
permission to branch. The user must deliberately choose to go back and continue
from that state; the client carries that intent in an explicit flag and chosen
boundary in its server message. The earlier Undo-supplies-intent provision is
superseded by D-232 tree editing. Final control naming/layout and wire schema
remain undecided.

For a path `root → A → B → C → D`, explicitly continuing after B with X creates
`root → A → B → X`. The old path through C and D remains recoverable. On
successful publication, the branch ending at X is active.

An explicit interruption makes its new branch current; no second-player vote is
needed. This is deliberate, not an accidental consequence of concurrent online
play. D-179 supersedes D-13's automatic conflict forks and P-11's proposed
activation protocol. Ordinary edits instead pass or fail their action-specific
checks at the current head (section 3.3). The displaced line is retained and
available through explicit return/activation.
Normal Undo goes to the active node's parent, not automatically to the displaced
line's former tip.

Exact handling of simultaneous explicit interruptions and queued follow-up work
after another interruption remains open. The former automatic-conflict protocol
does not settle it. Merely editing an inactive historical view cannot silently
authorize another activation; continuation from there requires deliberate intent.

The operation records:

- Fork boundary B, original continuation/branch, and its current head D.
- Author and optional reason, new branch ID, reaction actions, and new head X.
- Previous active selection, expected revision, and resulting control revision.

No second-player approval is required. A visible activity entry explains the
switch. A participant reviewing the old continuation is notified and can choose
to inspect it or navigate to the divergence; the UI must not silently teleport
their cursor past unreviewed actions.

D-222 confirms the graphical tree explorer and explicit activation of any
retained branch/node, not just a branch tip. Activation changes shared current
state, not the contents of either line. Any archive/restore interaction still
needs its own behavior; this is not approval of archival controls' details.

The preserved suffix is not automatically replayed on top of a reaction. It may
refer to objects, positions, or values that are no longer present. A future reuse
tool must preview and validate every copied action; automatic replay is out of
the proposed first release.

## 3.6 Review progress

Confirmed D-177: automatic or step-by-step animated playback advances through
groups. The player can stop automatic playback and drag the horizontal bottom
timeline left/right to select an exact mutation boundary and continue there.
Mutation-level navigation and group stepping are different presentation scales
over the same tree. See section 3.12 for returning after an interruption.

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

**D-224/D-232 precedence:** this section's parent-pointer operations/recovery table
describe the earlier rewind model. They are not a prohibition on the selective
removal/reinsertion contract in section 3.0, nor permission to selectively remove
an information-revealing target. Reconciliation of these controls, U-001 exposure
recovery and remembered-head versus reinsertion Redo remains open. Do not silently
apply D-161's captured-parent stale policy to selective timeline editing, require
an immutable tree, or preserve a pre-Undo alternative branch under D-232.

Requirement U-001: **Every user-authored logical game edit has a discoverable
undo or recovery path, except submitted game-wide chat (D-94) and acceptance of
an ending suggestion / return to lobby (D-212).** This includes
state edits, random actions, draft edits, publication, grouping, event-linked
comments, setup corrections, branch switching/archival and result suggestions.
Submitted game-wide chat is not undoable. Chat edit/delete, unsent-delivery
cancellation, and ordinary text-editor draft behavior remain separate decisions.
D-211 makes a proposed win/loss/draw ordinary shared, undoable board state. The
other player must accept before both return to lobby. This supersedes D-210's
unilateral, non-undoable End game design; do not apply that exception to suggestions.
D-212 separately confirms that acceptance/return to lobby is **not undoable**.
Do not use Undo to reverse that transition or reopen the ended play session;
pending suggestions remain ordinary undoable board state. D-213 restores the
last prepared decks/mainboard/sideboard, clears both Ready and requires both
players to ready again for a fresh game. D-214 defers finished-session action-history
archives/review to optional future development outside R1. Current-game history,
retained branches, Undo, exposure/comment preservation and downloaded read-only
access are unchanged. See the [lifecycle contract](accounts-decks-and-lobby.md).
Non-undoable acceptance and deferring finished-session review do
not authorize deleting prior history.

In the superseded rewind model, Undo preserves the immutable tree and moves the shared active pointer one parent
back (D-159). It has no author filter, inverse-patch/dependency analysis, or
whole-invocation targeting. Later work stays on retained descendants rather than
being transformed or erased. At the root there is no earlier game-state node.
To repair a value while keeping later play active, perform its normal edit now;
this does not require a separate historical-Undo mode or correction wizard.

After replacement play, only single-player/no-exposure/no-comment inactive
continuations qualify for default hiding (D-222/D-223). Mixed-player, exposure
and event-comment continuations remain visible in full. Hidden nodes retain
identity, payloads, ancestry and stored random results;
the filter never changes game state. See section 3.9 for exposure classification.

D-176 supersedes the old combined-node mappings for advance-and-close,
declarations and Untap + advance. Undo follows one constituent mutation's parent,
not the invocation or playback step. An actual draw plus advancement has separate
move and tracking mutations. Do not fabricate a draw node for an empty Library.
Interruption at any intermediate mutation follows the ordinary tree model; later
mutations remain on their original continuation, not replayed onto the response.

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
| Pending win/loss/draw suggestion | Ordinary parent-node Undo; the suggestion is board state and does not end play without other-player acceptance (D-211). |
| Accepted ending / return to lobby | Both players return after acceptance. Acceptance is not undoable; Undo cannot reverse this transition or reopen ended play (D-212). Pending suggestions remain undoable under D-211. |
| Die/coin result | Restore prior board state while retaining the information-gaining action/result as a history branch (D-89/D-90); redo reuses that result. Other random-operation classification remains open. |

Undo is a synchronized control move, not another board-action child node: its
control record can retain prior/new references without becoming the next gameplay
parent. This avoids Undo merely undoing a previous Undo record on the next press.
Redo follows the remembered last active head (D-222); explicit tree activation
can instead select any retained node. Both use original stored states/outcomes,
not new action invocations or random samples. Review alone never changes the active pointer.

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
- D-182 excludes offline Undo creation. A provisional online Undo still targets
  its captured transition; discard only an invalid control request, not retained
  game nodes. Online continuation edits after Undo retain their chosen boundary
  and D-179 background intent. Exact recovery if the connection drops during
  these requests remains open; do not silently erase or retarget work.
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

Confirmed D-159/D-160/D-222/D-223: every undone continuation is retained. Default
hiding requires a single-player continuation without exposure or event comments,
available through Show hidden branches. Mixed-player/exposure/event-comment
continuations remain visible in full. No last-exposure cutoff, permanent board
markers or ping overlays are required.

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
back while preserving the existing path. D-223 explicitly preserves D-160's
whole-comment-continuation visibility even for a single-player/no-exposure path.
Retain the complete path, including ordinary trailing nodes, without a last-comment cutoff.
This differs from D-94 game-wide chat, which is outside game Undo. Exact comment
group association, revision/edit/delete, and dependent/concurrent protocols remain
later review, not an unresolved question of whether paths are already branches.

Confirmed branch-level filtering (D-222/D-223 refine D-160, replacing D-91's cutoff):

1. Retain every node of the abandoned continuation after replacement play.
2. If that continuation contains exposure, an event comment or actions by both
   players, keep all of it visible, including ordinary ancestors/intervening/trailing nodes.
3. A single-player continuation with neither exposure nor event comments qualifies
   for hiding behind Show hidden branches.
4. Visibility does not move the active pointer or hide unrelated alternatives.
   An exposure in the shared prefix alone does not make an ordinary abandoned
   suffix an exposure-bearing continuation.

Example: from `root → A → View B → C → roll D → E`, use five Undo presses to
return to root, then create X. Keep the whole original continuation A–E visible,
not only the prefix ending at D. For an all-ordinary A–E by a single player,
retain the same nodes but hide that alternative by default; if both players
authored it, keep it visible. Either person can inspect/activate retained states;
inspection alone never activates them. D-222 makes Redo follow its remembered
head. Exact exposure capture/coalescing, pre-fork
presentation and concurrent/non-Undo control details remain open.

## 3.10 Randomness and branching

D-87 confirms die/coin results as automatically queued/synchronized records of
actor, request, and actual result. Review/redo reuse that concrete result rather
than rerolling; these tools do not automatically change card or counter state.
Initial delivery permits new random actions only online (D-182). Exact random
source/algorithm and action-boundary protocols still require review; touch entry
is in the board contract.

D-92 supersedes D-88's opponent-action seed-reset rule: every explicit new die
roll or coin flip gets a fresh random draw, including a new invocation after
undo or on another branch. No gameplay seed epoch/draw-position/reset mechanism
is required for these tools. A fresh draw may coincidentally equal an earlier
result; do not force a different value.

Distinguish a new invocation from review, replay, redo, or retransmission of the
same operation: those reuse its stored outcome/idempotency result, never resample.
An undone die/coin result remains in history under D-89–D-91; fresh generation
does not erase that information branch. Exact generator/source and online
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

Online execution must store concrete outcomes that deterministic replay,
including downloaded offline review, can apply identically. Offline generation
is excluded from initial delivery (D-182). The algorithm and entropy policy must
be specified before implementation; see the open decisions.

## 3.11 Confirmed shortcut event mappings (D-149–D-151)

**Current rule: D-176 supersedes the older combined-node mappings named in this
heading.** The heading is retained for existing references. Invocations issue
individual mutations; every mutation publishes immediately and is one Undo step.

| Invocation | Mutation sequence |
| --- | --- |
| Declare without tapping | Create an absent area as needed; create or reposition each affected combat occurrence individually. Preserve real source orientation. |
| Declare + tap | Issue the required area/occurrence mutations and individual source taps. Do not combine a declaration and tap, or several cards, into one node. |
| Untap all + advance | Individual untaps in a sensible recorded order, then the tracking advance. |
| Draw one + advance | The actual card move, if any, followed by the tracking advance. |
| Advance-and-close | Distinct tracking and area/occurrence changes, not a single node containing all effects. Exact cleanup decomposition/order remains to be specified consistently. |

Retain existing shortcut effects, target placement and one-invocation access;
this changes the node model, not which game operations are offered. Existing
bulk source/spatial ordering defaults remain. Exact cleanup/reset decomposition
and ordering still need contracts, but combining multiple individual edits into
a node is not an available fallback.

D-111 still applies: empty/no-change untaps or draws do not block advancement.
Record only actual mutations, not invented untaps, drawn cards or an empty-draw
mutation. Exposure-bearing continuations retain D-160 visibility. Navigation and
retries reuse stored outcomes; they neither resample nor replay the old future
onto a new branch. No runtime priority/legality enforcement is added.

## 3.12 Resume, playback and exact timeline navigation (D-177)

**Confirmed:** when returning after the opponent interrupted the player's chain:

1. Begin at the player's last local game state, not by replacing the board with
   an unexplained latest-state snapshot.
2. Traverse the old path backward fairly quickly with animations to the
   interruption/common-ancestor node, deterministically rolling back mutations.
3. Traverse the new active continuation forward with animated automatic playback
   or player-controlled steps. Each group is one playback step; its constituent
   changes animate concurrently while remaining ordered mutation nodes.
4. The player can interrupt automatic playback. While paused, show a horizontal
   event timeline at the bottom of the screen. Dragging it left/right selects
   an exact mutation boundary, including a boundary inside a displayed group.
5. Continue playback from that node, or explicitly choose to continue play there
   before using ordinary game controls. D-179 requires deliberate continuation
   intent for a historical branch, not just a paused cursor. D-232 Undo/Redo edit
   the tree without that intent. Without explicit continuation intent, ordinary edits use D-187 current-state
   acceptance/limited guards; a navigation/group boundary does not become a parent node.

Backward/forward playback and scrubbing are personal traversal, not new Undo,
activation or mutation publications. Preserve retained alternatives and pending
local work. Pausing the animation does not pause synchronization or change the
shared active continuation. A displayed animation between endpoints is not an
invented tree state: a paused/scrubbed game state identifies a concrete node.

The owner has specified the playback/timeline behavior, not its final geometry,
animation speed, automatic-versus-manual preference default or accessibility
layout. These details remain for the appropriate later design work. Every
mutation publishes immediately regardless of playback or grouping.
