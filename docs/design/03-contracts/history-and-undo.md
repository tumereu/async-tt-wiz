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

**D-224–D-236 supersede parent-pointer-only Undo/Redo.** Undo removes your latest
still-applied mutation from the existing tree; Redo reinserts it, under the safety
and stack rules below. These operations edit the tree, **not create branches**.
Personal review and explicit historical activation remain distinct. A personal
view head never selects the Undo target or limits its eligibility checks (D-245).
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
Dependent comment/ping references are protected by the replay rule below, not
a fresh choice to delete or retarget them. Review cursors, IDs/versioning and
cached states still need explicit handling. This decision does not approve
silently deleting an unrelated branch/comment or a specific retargeting policy.
Nor does it require physical storage erasure or
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
- Move a card, then the opponent adds an event comment referring to that move:
  **Undo of the move is blocked** because the comment's referenced context is
  part of its relevant state. Removing the move would change that context.
  This is a consequence of D-224, explicitly reiterated by the owner, not an
  unresolved choice to leave, delete or reattach a dependent comment. Apply the
  same dependency test to other historical references; an unrelated annotation
  is not automatically a dependency merely because it is later.

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

**Target transition fidelity (R-06; derived from recorded replay):** before suffix
validation, the target itself must be applicable at its original insertion boundary
with the recorded relevant input, choices and result. Restore its exact recorded
relevant `(from, to)` transition and mandatory effects, preserving unrelated fields.
Do not run a new live invocation, recompute arithmetic/defaults/Clone contents,
substitute a missing target or overwrite a different relevant pre-state. A missing
anchor/target or mismatching input blocks Redo and retains the next entry without
skipping it. The suffix being empty is not sufficient for eligibility.

Example: B's counter P is `0 → 1`, A's independent note Q follows, then A's counter
R is `1 → 2`. A undoes R; B can undo P while preserving Q. R's surviving anchor Q
now has counter 0. Redo R is blocked: neither `0 → 2` nor a fresh `0 → 1` restores
R's recorded `1 → 2`. Ordinary new live edits still use D-187, without old-value locks.

### Relevant-state and target domains

The same definitions govern target fidelity and each later replay comparison.
Include identities, fields read for the requested operation, fields written and
mandatory structural effects; exclude unrelated board/card fields. At minimum:

| Mutation family | Relevant input/output and dependencies |
| --- | --- |
| Orientation / scalar text / counter | Target and field/color identity, field presence and actual before/after value. Orientation does not read unrelated location. Relative numeric edits include their numeric input, not just delta. |
| Corner Reset / Clone | Selected face and retained default for Reset; source identity and copied fields/defaults for Clone, plus the new token identity/placement. Changed recorded inputs block historical reinsertion, even where a fresh invocation would succeed. |
| Move / ordered draw / reorder | Same source/target identities, source membership/placement, requested destination/gap/edge, capacity and ordering dependencies actually used. Draw requires the recorded top card to be top. Unordered Graveyard/Exile do not read unrelated members/indices. Include destination resets and cleanup effects. |
| Occurrence / area / link | Occurrence/source/area/slot or endpoint identities, existence, membership/role/order and affected links/pairs. Creation cannot substitute a different source; structural cleanup is part of the transition below. |
| Tracking / result suggestion | Actual tracking tuple or pending-suggestion identity/proposer/outcome and their recorded transition, not renewed relative progression. |
| Annotation / exposure / random | Recording context, referenced identities/context, effective before/after presence and concrete displayed/random result. Dependent references protect their context. Revealing targets cannot be selectively removed. |

Specific action mappings refine these domains; they may not broaden them into a
whole-board lock or omit a structural/read dependency to make replay pass.

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
stack synchronization. Rejected/no-op requests are not successful new gameplay
mutations and do not clear the stack; exact optimistic/server bookkeeping remains
technical work. An actual logged exposure is a successful effective action even
without a board edit, including exposure retained from a rejected prediction;
apply the participation table below. D-252 own-area holds are not such actions.
Do not generalize clearing to personal review/navigation or treat
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
an opponent's action still does not. D-236 below settles concurrent Undo requests.
Exact sync timing, protocol and other control races remain open. This decision does not approve
transfer of pending-request queues, unfinished input or personal review cursors.

**Server Undo requests and concurrent devices — D-236:** Undo is a request to
the server to Undo the player's then-current latest still-applied action under
the normal eligibility rules. Process distinct requests in server arrival order.
Two devices pressing Undo are equivalent to pressing Undo twice on one device:
if both successive candidates are eligible, remove both, one per request.
Do not pin both requests to the node the clients originally saw or discard the
second merely because the first changed that target. If the next candidate is
blocked or absent, the second request makes no further removal; it does not
skip a target or bypass replay, exposure, N or fork restrictions.

This supersedes the earlier D-161 fixed-node stale-Undo interpretation for these
selective Undo requests. The assistant's same-target collision-suppression
proposal was rejected. Distinct Undo invocations remain distinct from delivery
retries of one request: retries recover that request's original outcome without
another removal, including after a lost response/reconnect.

Concurrent gameplay from the same account's devices must work through normal
server arrival-order processing and existing action acceptance/reconciliation.
Do not require an exclusive active device, special collision-confirmation UX or
additional routine acknowledgement barriers. The owner does not prioritize
special multi-device UX. Exact transport, sync timing and remaining non-Undo
control races remain design work, not a reason to reopen this policy.

**Redo order — D-229:** restore the **most recently undone mutation first**,
like a normal Undo/Redo stack. If you Undo A and then B, successful Redo restores
B and then A. This is the order of Undo operations, not a sort by the mutations'
original timeline positions. Each reinsertion still requires D-224's relevant-state
tuple test within N; insertion anchors are not settled by this ordering rule.
**D-230 blocked-entry rule:** if that next mutation cannot be reinserted, disable
Redo and keep the stack intact. Do not skip to an older eligible entry or discard
the blocked entry. Opponent activity still does not clear the stack (D-228).

**Undo target order — D-230/D-231:** Undo may target only **your most recent
still-applied action on the current timeline**, including effective logged exposure
actions without a board edit, even if the opponent acted later.
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

**Fork boundary — D-233/D-245:** Undo cannot remove a branching point or reach back
past one. A branching point is a tree node with multiple child continuations;
if the target itself or any retained descendant is a branching point, disable
Undo even if all replay checks would pass. Neither the personal view head nor
an explicitly activated internal shared node truncates this check. Do not
skip the blocked latest own target, reattach the alternatives or replay all branches
to make removal possible. Explorer filtering does not remove a branch and cannot
bypass this boundary. Actions strictly after the latest fork remain candidates
under the usual latest-own, replay, exposure and N rules. For example:

- `root → A`, with children B (active) and C (alternative): A cannot be undone.
- `root → A → F`, with F's children B (active) and C (alternative): A cannot
  be undone either; Undo would reach back past F.
- In the same tree, an eligible own action B can be undone without removing F
  or changing C. No branching point lies at or after B on its active path.

**Retained linear future (D-245):** after the fork check there is at most one
retained successor chain after the target. Validate every event on that chain,
including events beyond an explicitly activated internal shared node, against its
recorded relevant tuple. Do not stop at either player's review cursor or the
shared active pointer. These later events all count toward N. Remove/reinsert
within the same tree, preserve surviving logical nodes and their contexts, and
recompute their states without activating the retained future. A surviving viewer
stays on its node in the revised tree (D-238).

On `root → A → B → C`, activating B does not make dependent C disappear from
validation. If B taps X and C untaps X, Undo B is blocked. If C independently
edits Y and all checks pass, removal yields `root → A → C`, the shared active
pointer falls to B's parent A, and a personal viewer on C remains on C. Simply
viewing B while C remains shared-current changes none of the ordinary checks.

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

N is a hard replay bound, not by itself a complete CPU/memory bound. Technical
design must also budget target validation, payload sizes, snapshot loading,
reference checks, mandatory-effect fan-out and cache reconstruction. Exhausting
a validation budget makes the candidate unavailable with no partial edit or
unchecked tail; do not add a product Stack-count cap or expire history. Verify
large targets/cleanup with zero or short suffixes as well as long histories.

**Remaining work, not an owner-question queue:** complete relevant-state and
reinsertion-precondition mappings from the agreed rules; document anchors,
pending-request/recovery bookkeeping, IDs/versioning, grouping and cache updates.
Numeric N/counting and wire/storage representation are technical specification
work, not new Undo policy. D-233/D-234 already settle fork boundaries, D-235 stack
device scope and D-236 server Undo requests. Dependent references fail D-224's
test rather than being silently retargeted. Rejected/no-op requests create no
successful new gameplay mutation and therefore do not trigger D-227 clearing;
actual retained exposure is a separate successful action as mapped below.

**Review cursor fallback — D-238:** if the node currently being viewed is deleted,
move that personal review cursor to the deleted node's parent and display the
state there. For `root → A → B`, a viewer at A falls back to root when eligible
Undo removes A, leaving `root → B`. Stay in personal review: do not jump to the
shared head, keep a detached old snapshot or create an alternative branch.
Apply the same rule again if that parent is subsequently deleted; when several
deletions arrive together, follow the parent chain to the nearest surviving
ancestor, including root. A cursor at a surviving node stays at that node.

Unlike a dependent comment, a personal cursor is not a later shared mutation
protected by the replay test and does not block an otherwise eligible Undo.
Cursor fallback itself changes no shared play and adds no mutation. Node-ID,
versioning and cache reconciliation remain technical specification work.
Any further proposed activation/control edge case must first be checked against current rules, not
assumed to need a new policy. See the [pruned gaps](../02-experience/gaps.md).
Removal changes the existing tree (D-232), not just a candidate branch; D-223's
visibility rule applies to actual alternatives. Earlier parent-pointer
fixtures below describe that superseded rewind model, not blanket approval
to use it instead of this selective rule. U-001 and older exposure/Redo recovery
wording must be interpreted with this distinction until controls are settled.

### Retained branches and explicit activation

The game has one shared active game-state pointer, separate from personal review.
It can explicitly select an internal node even when retained descendants exist.
Root contains the initial state; every later node records one mutation.
Ordinary post-Undo play extends the edited tree, not a preserved pre-Undo copy.
Explicit historical continuation creates a branch under D-179. A **graphical tree
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

Concurrent/stale activation, archive integration and detailed pre-fork
presentation remain contract work; remembered-head Redo and captured-parent Undo
are retired designs (D-224–D-236), not current controls. Each successful Undo
removes one eligible latest-own mutation. A revealing draw cannot be removed;
the later tracking advance can be removed when otherwise eligible.

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
it does not automatically advance a historical review cursor. An additional
live-follow feature is excluded from R1 (D-241). Local edits advance the author's local
view immediately, with pending status until acknowledged.
If the viewed node is deleted, D-238's parent fallback applies; preserving a
personal review position does not require retaining a deleted node or snapshot.

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

### Invariant-closed transitions (R-04)

An individual operation includes its mandatory dependent effects. Every published
node, and every forward/backward replay boundary, must satisfy structural
invariants. Cleanup is not an independently interruptible user operation where
that would leave a dangling reference or an invalid container/slot.

1. Capture the actual pre-state and any last-live source inspection state first.
2. Compute the requested individual change and required resets/disappearance,
   occurrence/link cleanup, and empty-pair cleanup from that state.
3. Validate and record the complete transition and exact inverse together. Publish
   one valid boundary, never an intermediate orphan projection/link/empty pair.

A source departure includes removing its combat occurrences and their incident
links, retaining other slots' membership/order and deleting fully empty pairs.
Token disappearance first captures its pre-disappearance state for surviving
Stack effects; never reset it to printed defaults before that capture. A retained
ordinary card's configured destination-entry counter/corner resets belong to its
move. Explicit occurrence removal includes incident-link and empty-pair cleanup.
Closing combat includes removing its contained occurrences/links/pairs, but never
edits real sources or removes Stack effects. Reverse traversal restores the exact
captured identities, fields, links, slots and live/frozen relationships together.

This does not combine independent operations: moving two cards, removing two
explicitly selected effects, tapping a source and declaring it, or changing
tracking and closing an area still produce separate nodes (D-176). Interruption
is possible between those valid nodes, not inside mandatory structural maintenance.

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
but not-yet-sent actions remain open. Preserve D-236 server-ordered Undo requests,
D-184 delivery ordering and D-183 responsiveness during normal online play.

**Play-session boundary (R-12; derived from D-212/D-213/D-216):** distinguish the
persistent lobby from each fresh play incarnation. Every gameplay/annotation/
exposure request, shared history control, result reference and per-player Undo/
Redo stack belongs to one incarnation. Preserve that identity during restart,
reauthentication and recovery. A delayed uncommitted request for ended play
cannot mutate the lobby or a later incarnation, even if an object-free command
such as End turn would otherwise apply. Return an already committed old outcome
without executing it again or replacing the new game's state with its snapshot.
Fresh play has a fresh root/stacks; protected old recovery records are not cleared
merely to avoid late delivery. Exact IDs/fencing/storage remain technical design.

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
and any explicitly selected historical reference context (D-187).
The server creates authoritative events from requests; deterministic logic groups
those events (D-254). A referenced historical group is not an instruction to assign
the new event to that group. UI/menu state and provisional grouping do not bind
authoritative group membership or introduce a group-mismatch rejection guard.
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
  snapshot encoding and provisional-ID mapping remain technical design. D-247
  defines the semantic comparison as the ordered current real-card/effect entries
  with occurrence/source identities, not their rendered/live fields, source
  location/live-versus-frozen status, links, layout coordinates or personal view.
  See [the comparator](optimistic-locking.md#semantic-stack-comparison--d-247).
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
  Remove action, and this restriction does not change selective Undo/history.

D-176 distinguishes invocation, individual mutation node and playback group.
Each successful Undo removes one eligible latest-own mutation (D-231/D-232).
Multiple mutation nodes do not require extra invocation taps. Grouping never
changes parent links or eligibility. Manual group editing is excluded from R1.

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
Stack effects with removed token sources. Invariant-closed cleanup belongs to the
triggering transition (section 3.2); do not apply one universal
projection-removal rule in replay or recovery.
D-242 retains read-only last-live source state only on token disappearance;
existing sources remain linked across locations, including Graveyard/Exile.
Replay/retries must reproduce the recorded source/effect and frozen inspection
context, not substitute a new card or silently use an activation-time snapshot.

Grouping constraints:

- Group using explicit interaction context, contiguous actions, associated
  pings and author/path boundaries. No card-text,
  area-name, or game-mechanics inference and no required intent-selection dialog.
- Determine group starts from the [sequence algorithm](history-grouping.md#deterministic-sequence-resolution).
  D-251 starts a tap group unless the current group contains only taps/pings;
  its following anchor joins, without merging the preceding group. Author
  boundaries always win. Navigation/Undo alone adds no artificial marker.
- A group can be open and grow as actions synchronize. An opponent can review
  it immediately without waiting for closure. Closing a group is presentation
  metadata, not a gameplay commit or required user action.
- R1 has no manual rename/split/join group controls (D-241).
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
Normal Undo removes the latest eligible own mutation, not automatically activating
the displaced line's former tip.

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

- Review progress is personal/local; cross-device review progress is excluded
  from R1 (D-241), unlike synchronized per-player Undo/Redo stacks.
- Own actions are distinguishable from unseen actions by the other person.
- Reading an action does not prevent a later interruption at an older boundary.
- Branch changes preserve review positions on retained paths.
- The next required catch-up starts at the last common reviewed boundary on the
  active path; actions on an abandoned suffix do not count as seen on a new path.
- A manual jump to the head marks local progress as skipped/viewed in summary,
  not individually stepped through. No opponent read-receipt feature in R1 (D-241).
- Notifications and read markers are not simulated MTG priority passes.
- Newly synchronized actions can extend an open group while it is being
  reviewed. Update unseen counts without resetting the cursor or requiring the
  participant to reopen the group.

## 3.7 Full undo contract

Requirement U-001: **In-scope game edits have discoverable Undo or recovery under
the eligibility and lifecycle limits in section 3.0.** This is not a promise
that every historical mutation can always be restored: revealing targets are
not selectively undoable, blocked entries are not skipped, own new play clears
Redo, and a fork can strand an entry (D-224/D-227/D-230/D-234). Submitted
game-wide chat (D-94) and acceptance of an ending suggestion / return to lobby
(D-212) have no gameplay Undo. Excluded authoring/manual-group/recovery-center
features are not recovery obligations. Ordinary unfinished input retains local
editing/cancellation; branch archival has its separate restoration path.
Submitted game-wide chat is not undoable. D-240 also excludes submitted-chat
editing/deletion: corrections use another message, with no planned expansions.
Unsent-delivery handling and ordinary text-editor draft behavior remain distinct.
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

To repair a value while keeping later play active, perform its normal edit now;
this does not require a separate historical-Undo mode or correction wizard.

After replacement play, only single-player/no-exposure/no-comment inactive
continuations qualify for default hiding (D-222/D-223). Mixed-player, exposure
and event-comment continuations remain visible in full. Hidden nodes retain
identity, payloads, ancestry and stored random results;
the filter never changes game state. See section 3.9 for exposure classification.

D-176 supersedes the old combined-node mappings for advance-and-close,
declarations and Untap + advance. Undo removes one eligible latest-own mutation,
not the invocation or playback step. An actual draw plus advancement has separate
move and tracking mutations. Do not fabricate a draw node for an empty Library.
Interruption at any intermediate mutation follows the ordinary tree model; later
mutations remain on their original continuation, not replayed onto the response.

Game-node Undo and recovery of non-node controls are distinct. Their recovery must
not redefine normal Undo as a stack of heterogeneous commands.

| Original operation | Required recovery behavior |
| --- | --- |
| Pending action not yet transmitted | Preserve the distinction between a local prediction and applied server history. Online buffer/cancellation mechanics remain technical work; they cannot authorize offline events or a captured-parent Undo request. |
| In-flight action with unknown outcome | Preserve original delivery identity and recover its actual result. A distinct Undo is processed in server arrival order under D-236, not pinned to the client's prediction. Reconcile without duplicate execution or losing unrelated pending work. |
| Published latest own action | Remove it only if all section 3.0 checks pass, preserve later transitions and put it on the shared per-player Redo stack. |
| Latest own action followed by opponent play | It remains the candidate; validate the entire retained suffix, including its relevant dependencies and N. Never undo the opponent or skip a blocked own candidate. |
| Interruption | Its eligible latest-own mutations can be removed without activating the displaced future. Explicit return/activation or Delete branch and return is separate. |
| Activate another branch/node | Activation does not itself create a gameplay mutation or select the personal review cursor as an Undo target. Apply latest-own targeting to the selected shared timeline and D-245 checks to all retained future events. |
| Delete/archive branch | Provide branch restoration and metadata recovery separately from game-node Undo; restore selection if explicitly requested. |
| Group metadata edit | No manual group editing in R1 (D-241); automatic regrouping preserves nodes and personal cursors. |
| Submitted game-wide chat | No game Undo (D-94), Edit or Delete (D-240). Corrections use another message; no expansions are planned. Unsent-delivery handling remains distinct; do not promise generic message Undo. |
| Event-linked comment undone | Remove the eligible latest-own comment from the existing tree, keeping it for Redo; do not manufacture an original alternative. Dependent later references can block removal. |
| Pending win/loss/draw suggestion | Ordinary eligible latest-own Undo removes the suggestion without ending play; acceptance remains a separate lifecycle transition (D-211/D-212). |
| Accepted ending / return to lobby | Both players return after acceptance. Acceptance is not undoable; Undo cannot reverse this transition or reopen ended play (D-212). Pending suggestions remain undoable under D-211. |
| Die/coin result / revealing inspection or draw | No selective Undo of an information-revealing target (D-224). Personal traversal/explicit activation of retained states is separate; recorded results are never resampled or erased. |

Undo/Redo are synchronized tree-edit controls, not board-action child nodes and
not entries to target with the next Undo. Redo reinserts the last-undone mutation
at its recorded boundary under target fidelity and suffix checks. Explicit tree
activation instead selects an existing node; review alone never changes shared play.

Rapid Undo must work before, during, and after acknowledgement. Pending/in-flight
requests and recovery context must survive restart/retry without disappearing or
being applied twice.

**Current server request semantics (D-236):** distinct requests are successive
presses against current latest-own candidates; retries recover one original
outcome. The former D-161 captured-parent/stale-collision rule is retired, not a
current acceptance oracle. Blocked/absent candidates cause no removal. Reconcile
predictions to current state without jumping unrelated personal review cursors
or overwriting newer state with an old response snapshot. Non-Undo branch controls
retain their separate referenced targets and applicability checks.

## 3.8 Deleting an active interruption

“Delete this branch and return” is a single atomic control operation:

1. Show the branch to be archived and the exact return continuation and board.
2. Archive the selected branch and select the chosen return branch together.
3. Leave all action nodes and descendants recoverable.
4. Offer recovery of both archive state and branch selection. This is distinct
   from selective game-mutation Undo; exact recovery-control wording is open.

Default return target: the continuation displaced by this interruption, at its
currently retained head. If that target has changed, is archived, or is missing
from local storage, do not guess: show a selection/restore flow. The exact root
branch cannot be archived if doing so would leave no active branch.

Archiving a branch does not implicitly archive its descendant branches; show
descendants and offer an explicit, reversible multi-branch operation if desired.
Shared prefix actions are never deleted by branch archival.

## 3.9 Limits of undo

- A revealing mutation cannot be selectively undone; viewing or explicitly
  activating a retained earlier state cannot erase a person's memory.
- Delivered push notifications cannot reliably be retracted.
- Exported files or screenshots cannot be recalled.
- An external account deletion or legally required data erasure is not a
  recoverable in-game operation. Its policy must explicitly disclose that.
- Navigation has Back/history behavior rather than shared game undo.
- Submitted game-wide chat is explicitly not undoable (D-94); event comments
  instead use eligible tree-editing Undo, without original-path copies (D-232).

The UI must explain these boundaries instead of promising physical reversibility.
No history compaction or retention policy may silently remove the promised
in-game undo capability.

### Information-gaining actions and undo branches

Actual retained alternatives follow D-222/D-223 filtering: only single-player
paths without exposure or comments qualify for default hiding. Undo creates no
alternative copy (D-232); revealing targets cannot be removed (D-224).

Exposure coverage, revised by D-252: explicit Inspect other side, library
View/Reveal, and die/coin results. Own-area hold inspection shows real faces
without an event; opponent-area hold does not show hidden faces. Ordinary
visible inspection and every search keystroke are not exposure records.
This records what the interface showed/generated, not what a player remembers.
D-91 generalizes this to any action exposing otherwise-hidden information,
including draws, moves, and face changes that display covered information to
either participant. Except for D-252's own-area hold, classification depends on
actual interface exposure, not the command name, printed rules or guessed knowledge.
An ordinary visible-face gesture/keystroke is not itself an exposure record;
if its result actually exposes hidden information, that exposure still qualifies.
Covered-face inspection logs an exposure without requiring a board-state edit
or automatic activation of a new gameplay continuation; exact record placement
follows the lifecycle below.

### Exposure lifecycle and offline inspection — D-246

- New event-producing inspection requires a connection, even with cached faces.
  D-252 own-area holds create no event and may read downloaded real faces offline;
  opponent-area holds never silently disclose hidden information.
  Offline traversal and reading already-recorded visible content remain available.
  An explicit Inspect other side/View/Reveal requiring a new event cannot bypass
  that requirement through cached content. Explain the connection requirement
  rather than silently dropping logging or queuing offline events.
- Online exposure records the actor, play incarnation, viewed boundary/revision,
  source/object and printed-face references, actual content/result shown, and its
  effective recording context. A multi-face inspector includes any otherwise-
  hidden face actually displayed. No guessing whether the player already knows it.
- Capture exposure durably before presenting new hidden content. A completed
  online inspection is an event, but need not move a card, activate a continuation
  or create an automatic gameplay branch. Record its viewed context separately
  from its recording context, as for historical annotations.
- If an online optimistic action displays hidden content then is rejected, retain
  that exposure and its prediction/request context. Reconciliation removes the
  failed board effect, not the record of what was shown. Preserve capture through
  process death and recover already-initiated online records after reconnect;
  this is not permission to start inspection/events offline.
- Deduplicate delivery retries of one recorded exposure, not distinct inspections
  or different exposed face/reference sets. Ordinary summaries report viewing
  without automatically disclosing covered names. Retained original context is
  inspectable online; offline reading must not itself create a new event.

Persistence/outcome linkage and event-stream encoding are technical work. Capture
must not depend solely on a gameplay mutation succeeding, nor require delaying
all ordinary online actions for server acknowledgement. If capture is unavailable,
do not show new hidden content without its required record.

### Exposure participation in controls and visibility

S-04 is completed by distinguishing a board transition from an effective logged
action. “No board edit” is not “no action.” Apply the same actor/no-skipping rules
to a successful explicit logged inspection as to another information-revealing
action; storage outside the mutation-node payload does not exempt it.

| Case | Effective placement / board transition | Undo / Redo | Visibility and replay participation |
| --- | --- | --- | --- |
| Own-area hold or permitted visible-face navigation | Local only; no record or board transition. | Does not change latest-own candidate or clear Redo. | No group/filter/replay/N participation. |
| Accepted revealing move/Flip or View/Reveal | Exposure is associated with its accepted mutation and actual effective boundary. | That revealing latest-own action blocks Undo; a successful new own action clears own Redo. | Count once as its event plus its reference/payload work; retain concrete shown content. |
| Explicit Inspect other side with no board edit | Effective exposure event at current recording context; viewed context retained separately. No inserted historical mutation or new gameplay branch. | It is the actor's latest own action and cannot be selectively removed. Do not skip it to Undo a prior tap. It clears that actor's Redo across devices. | V grouping anchor; recording-context membership and exact viewed references participate in suffix dependencies/N. |
| Actual logged exposure from a rejected prediction | Retain the actual disclosure as its own effective event, with failed request/prediction context; no invented successful draw/move. | The board rejection is not new play, but the actual completed disclosure is an exposure action with the same barrier/clearing behavior as standalone inspection. Capture failure before display creates neither. | No automatic gameplay branch; retain/deduplicate disclosure even across recovery. |

An exposure-only event has a stable causal position and effective revision at its
recording boundary, ordered with other accepted actions. It need not add a second
board-transition node or a child branch to an internal state with a retained
future. Personal views can distinguish before/after that effective event without
fabricating a card edit. Latest-own selection includes these effective actions on
the shared timeline, not only nodes that move cards. A later opponent edit does
not bypass the actor's revealing barrier or clear the other player's Redo.
Exposure records on the retained successor chain also enter full D-245 dependency
validation; their data/reference costs cannot be omitted from N/resource budgets.
Exact side-record/node encoding and same-boundary cursor representation are technical.

For filtering, “contains exposure” means its **recording-context** membership,
not every path merely inspected by that record. Inspecting an inactive alternative
from current play records the action on current play and links to the viewed
alternative without activating it. The referenced alternative does not become
exposure-bearing merely from that incoming reference; its original exposure/
comment/author records still decide filtering. Keep the original-context route
discoverable even if the alternative qualifies for Show hidden branches. A shared
prefix exposure alone still does not qualify an otherwise ordinary suffix.

Durability protects the obligation, not a send boolean: logged exposure captured
online before display survives a loss before transmission, process death, Logout
and account switching. Clear ordinary private downloads on Logout but retain this
account-protected recovery record. Another account may neither inspect nor publish
it. Original-account recovery records the actual exposure once, including for an
ended incarnation, without reopening play or overwriting a fresh game's board.

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

D-95 makes an event comment an ordinary current-path node; D-232 removal does not
retain an original copy. D-223 keeps a genuinely retained alternative containing
a comment visible in full, even with one author/no exposure, including its tail.
This differs from D-94 game-wide chat, which is outside game Undo. Historical
comment placement follows recording-versus-referenced contexts in the annotation
contract; D-241 excludes dedicated Edit/Delete. Encoding remains technical work.

Confirmed branch-level filtering (D-222/D-223 refine D-160, replacing D-91's cutoff):

1. Retain every node of a genuine alternative displaced by explicit continuation;
   Undo alone creates no alternative copy.
2. If that continuation contains exposure, an event comment or actions by both
   players, keep all of it visible, including ordinary ancestors/intervening/trailing nodes.
3. A single-player continuation with neither exposure nor event comments qualifies
   for hiding behind Show hidden branches.
4. Visibility does not move the active pointer or hide unrelated alternatives.
   An exposure in the shared prefix alone does not make an ordinary abandoned
   suffix an exposure-bearing continuation.

Example: explicitly continue from root beside `A → View B → C → roll D → E`.
Keep that actual alternative A–E visible in full; Undo cannot remove B/D to create
this outcome. Either person can inspect/activate retained states; inspection
alone never activates them. Exact exposure coalescing, pre-fork
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
A die/coin result cannot be selectively undone (D-224); fresh generation
does not erase earlier information. Exact generator/source and online
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
| Advance-and-close | Tracking advance first, then close the combat area with its mandatory structural cleanup. No separate orphan occurrence/link/empty-pair boundaries. |

Retain existing shortcut effects, target placement and one-invocation access;
this changes the node model, not which game operations are offered. Existing
bulk source/spatial ordering defaults remain. Mandatory effects follow section
3.2's invariant closure; independently requested changes remain separate nodes.

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
