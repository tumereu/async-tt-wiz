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

## Critical authoritative Undo/Redo rule — owner, 2026-10-25

**Undo/Redo is decided by GAME STATE AND EVENTS ONLY, never by what information
players have gained or seen. This rule supersedes every earlier conflicting
Undo/Redo restriction.**

An Undo or Redo is allowed **if and only if every event after it in the game tree
can still be applied with an identical `(before, after)` tuple for the fields
that event concerns in the pre-edit and post-edit trees**. Compare both endpoints
for every later event, not merely applicability or the final board. If any later
event cannot apply or its concerned tuple differs, refuse the Undo/Redo.

An event's **concerned fields** normally include the fields it edits, but also
fields it reads or depends on, such as a moved card's source location or the top
card taken by a draw. Do not include unrelated fields or infer dependencies from
what either player knows, inspected, revealed or remembers. Revealing information
is not itself a reason to refuse Undo/Redo.

For example, B's ping concerning A's attacker projection blocks undoing the attack; a ping concerning only the unchanged source card does not.

Concerned fields (owner rulings, 2026-10-25): moving a token to Graveyard deletes
it regardless of owner and does not concern its owner. A ping concerns only its
target card, projection or player, not its recording position or ancestry; a
projection ping still concerns that projection. A draw from a known (ordered) top layer
concerns the card taken and that it was top, not the card exposed beneath; an edge
draw from an unordered layer concerns all members of that layer only (layers have
stable IDs, so other layers' changes do not matter). Any other Library edit also
concerns the size and known-order flag of each layer it changed and the known-order
flag of their neighbours, which decide how the result is normalized. Display grouping is recomputed bookkeeping,
not concerned state; Library branch reseeds are semantic, and a later event's
recorded reseed input must never be silently rewritten. Undo is a convenience:
when uncertain, refuse rather than under-block; players can reverse manually.

Rules v17 temporary-mark edits concern their exact damage/EOT P/T/EOT note field
and edit eligibility, independently of permanent corner/note. Turn passage clears
all temporary marks in the turn event and concerns the complete set of marked
card IDs and values, including its empty-set witness. Zone resets concern all
three reset fields even when empty. One undo restores the turn and cleared marks;
retained pre-v17 events keep their recorded effects without new cleanup.

Rules v19 hand reveals concern the panel slot, source Hand, selected card locations
and disclosed faces. Random hand actions also concern the canonical candidate set
(selection subset, or whole Hand), recorded chosen identity, movement defaults and
destination effects, not unselected faces. Reveal Done concerns the exact remaining
tray and source locations; it preserves Hand order. Replay/undo use recorded effects
and never resample; older sealed events retain their original behavior.

Rules v20 player-target assignments (including placement retaining a player target
and target-departure fallback) concern the resolved defending account, not just a
null target-card ID. Random hand moves concern the chosen card's owner and record
that owner's destination. Sticky pair blocking and token tray cleanup are recorded
in the same transition and participate in exact concerned-field replay/undo.

Rules v21 Library-to-Stack uses the existing AddEffect event and its source
location/owner, live-source and Stack concerns. Its revealsLibrarySource flag is
part of the exact Stack-effect delta/concerns (also retained by copies); removing
the last revealing entry hides the Library source again. Pre-v21 events retain
their original eligibility and disclosure behavior on replay.

Rules v22 DeclineResult and WithdrawResult concern the complete pending result
suggestion's `(suggestion, null)` tuple, including its ID, author and outcome.
Withdraw is an ordinary suggester-only event, not Undo of the proposal. Undoing
either resolution restores that exact suggestion; it blocks removing its proposal
until the resolution itself is undone. Counter reset batches retain each existing
per-color counter tuple and undo atomically, without concerning P/T.

Rules v23 face-down Battlefield cards get a public 2/2 corner when covered (SetCovered
or a face-down move onto the Battlefield) and the shown face's printed corner when
uncovered. Those events concern the card's corner, faceId, definition and (for
SetCovered) its container, even when the assigned corner happens not to change.
The first move into a player's Temp pile creates that pile in the same event: its
delta adds the container and the exact inverse removes it, so a later event that
used the pile blocks undoing the creating move. Temp shuffles/looks/moves reuse the
Library concerns.
Rules v29 Temp piles 2–4 behave the same; turning a pile on/off is its own event
whose delta adds/removes the whole empty container, so a later move into the pile
blocks undoing its turn-on. History names them "Temp 1".."Temp 4".

Rules v24 face-down casts and manifests are ordinary moves: they concern the card's
covered/explicitlyCovered state, and a face-down Stack -> Battlefield resolve or
manifest concerns the assigned public corner, so a later edit of that corner (or
turning the card face up) blocks undoing them.

<a id="30-current-undo-model--parent-pointer-navigation-d-159d-160"></a>

## 3.0 Current Undo/Redo model — editing the mutation tree

**D-224–D-236 supersede parent-pointer-only Undo/Redo.** Undo removes your latest
still-applied mutation from the existing tree; Redo reinserts it, under the safety
and stack rules below. These operations edit the tree, **not create branches**.
Personal review and explicit historical activation remain distinct. A personal
view head never selects the Undo target or limits its eligibility checks (D-245).
Do not infer acceptance of the assistant's shared remembered-Redo-head proposal.

**Conservative convenience boundary — D-257:** Undo/Redo are conveniences for
reaching a desired board/event chain, not the only available tools. Prefer making
a complex edge case unavailable to adding surprising recovery/navigation behavior.
This does not override the authoritative rule above or the target/stack rules.
**D-258 confirms the scoped simplification below:** qualifying deliberate shared-history
switches clear both players' Redo queues. Ordinary head advancement, Undo/Redo and
personal review retain their distinct rules. F-02's advance-to-A proposal was not
selected; its historical-activation case instead has no surviving Redo entry.

### Selective mutation Undo/Redo — D-224

**Safety rule, corrected by the owner on 2026-10-25:** allow deletion/insertion
if and only if **every subsequent event in the game tree can be applied with an
identical concerned-field `(before, after)` tuple in the original and candidate
trees**. “Relevant state” and `(from, to)` below mean these concerned fields and
their before/after values. Replay the subsequent events
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
Retained side branches participate in the same concerned-field comparison.
Dependent ping references are protected by the replay rule below, not
a fresh choice to delete or retarget them. Review cursors, IDs/versioning and
cached states still need explicit handling. This decision does not approve
silently deleting an unrelated branch/reference or a specific retargeting policy.
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
- Later historical references block Undo only when their concerned-field tuples
  would change. An unrelated annotation is not automatically a dependency merely
  because it is later. Game chat is the only comment channel; there is no
  event-comments UI or Event details dialog.

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

The same concerned-tuple test applies to **Redo reinsertion**, including actions
that revealed information. Reviewing/activating a retained historical state remains
distinct from Undo/Redo. Replay reuses
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

The owner-rulings paragraph under the authoritative rule refines the broad
annotation/random row below: ping has target-only dependencies, presentation
groups are not concerned, and known-layer/hidden-layer draws differ. Do not infer
an ancestry lock or a whole-library lock from this generic family table.

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
| Annotation / exposure / random | Recording context, referenced identities/context, effective before/after presence and concrete displayed/random result. Dependent references protect their concerned context, not a player's knowledge. |

Specific action mappings refine these domains; they may not broaden them into a
whole-board lock or omit a structural/read dependency to make replay pass.

**Redo invalidation after ordinary new play — D-227/D-228:** after Undo, your own
successful new gameplay edit clears your Redo queue. Previously removed mutations
do not remain available through Redo merely because reinsertion would still pass D-224's tuple
test. D-232 does not require an explorer copy of the removed path; retention or
recovery of removed records after queue clearing remains open.
For ordinary continued play, clearing is triggered by a successful new gameplay
mutation by the player who used Undo. Ordinary opponent actions do **not** clear
that player's queue; retain its entries and recheck reinsertion against current
state under D-224, including N.
An opponent edit may make Redo unavailable without clearing the queue. Clearing
for ordinary play is player-specific. D-258's deliberate-switch reset below is a
separate game-wide control effect. D-235 below confirms stack synchronization.
Rejected/no-op requests are not successful new gameplay
mutations and do not clear the stack; exact optimistic/server bookkeeping remains
technical work. An accepted logged inspection is a successful effective action even
without a board edit; apply the participation table below. D-286 does not create
additional actions from rejected/different predictions. D-252 own-area holds are
not such actions.
Do not generalize clearing to personal review/navigation or treat
Redo reinsertion itself as new play that necessarily clears remaining entries.

### Redo invalidation on deliberate shared-history switches — D-258

As part of the **same successful server control/continuation operation**, clear
both players' Redo queues for this play incarnation when deliberately changing
the selected shared history through:

- activation of a different retained node or branch, including root/internal states;
- explicit interruption/continuation from the past that creates and activates a
  new continuation;
- return to another continuation when it actually changes the shared selection.

This is shared game state under D-259. It is not a device-local reset, an extra
gameplay mutation, deletion of the retained tree or a reset of other games. It
does not clear still-applied actions from latest-own Undo consideration; current
timeline targeting and the authoritative concerned-field rule still decide Undo.
No original-path copy or special recovery tool is required for cleared Redo entries.
Activating/restoring a historical board does not reload Redo queues from an old
node snapshot; queues are current server control state, not historical board fields.

| Operation | Redo effect |
| --- | --- |
| Qualifying deliberate shared-history switch | Clear both players' queues atomically with the switch, regardless of actor or whether board values happen to be equal. |
| Ordinary successful new play | Clear only its author's queue under D-227/D-228; ordinary opponent work preserves yours subject to eligibility. |
| Undo / Redo | Normal stack push/pop and validation; no blanket reset merely because the head changes. |
| Personal review, scrub/playback, zoom/pan, selection or open menus | None; these are UI-only, not shared history switches. |
| Same-current-target no-op or rejected switch | No switch-triggered clearing. Branch archive/restore is removed. |
| Delivery retry / recovered result of a switch | Return the original outcome, without another reset or reactivation. Never clear entries created after the original operation. |

On `root → A → B`, removing A may leave it queued on `root → B`. Explicitly
activating root then clears both queues. Redo A is unavailable; do not restore it
and improvise a shared-pointer result. A delayed Redo processed after this reset
has no entry to restore. If Redo commits first, process the later switch normally;
retries recover their original outcomes rather than reversing server order.

This supersedes the D-228/D-234 rule that a deliberate opponent interruption keeps
an existing Redo queue. It does **not** change ordinary opponent-edit retention.

**Shared game state across devices — D-235:** each player's Undo/Redo stack
belongs to that account in that game and synchronizes across their devices; it
is not device-local. Undo on a phone can be Redone on a tablet, subject to the
same current eligibility checks. Latest-own targeting includes that player's
actions from any device. Successful Undo/Redo and stack clearing are reflected
across devices, including last-undone-first order and blocked entries.

Shared game state does not mean one author-independent stack for both players:
preserve D-231 own-action targeting and D-228 player-specific clearing for ordinary
play. A successful ordinary new gameplay edit clears its author's queue; ordinary
opponent play does not. D-258 deliberate shared-history switches clear both queues.
D-236 below settles concurrent Undo requests.
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
skip a target or bypass the concerned-field comparison.

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
tuple test for every later event; insertion anchors are not settled by this ordering rule.
**D-230 blocked-entry rule:** if that next mutation cannot be reinserted, disable
Redo and keep the stack intact. Do not skip to an older eligible entry or discard
the blocked entry. Ordinary opponent activity does not clear the stack (D-228);
a qualifying deliberate shared-history switch clears it under D-258.

**Undo target order — D-230/D-231:** Undo may target only **your most recent
still-applied action on the current timeline**, including effective logged exposure
actions without a board edit, even if the opponent acted later.
Never target an opponent's action or an arbitrarily selected earlier own action.
Later opponent mutations remain applied and must pass D-224's unchanged-tuple
replay test. If that next Undo target
fails the concerned-field replay test, disable Undo;
do not search backward for an older eligible target. After successful Undo, the
preceding still-applied own action becomes the next candidate. If there is no own
action, Undo is unavailable; do not fall back to an opponent's action. This
author filter is not permission to skip a blocked latest own action. The rule
governs Undo, not personal review or explicit historical activation; interaction
with the latter remains open. Do not replace it with parent-pointer Undo.

**Retained forks — supersedes D-233's blanket boundary:** a fork is not an
independent refusal reason. Apply the authoritative concerned-field test to every
later event on every retained continuation. Neither explorer filtering, a personal
review cursor nor an internal shared active node truncates that validation. Do not
delete, substitute or retarget later events to make the comparison pass.

**Accidental interruption clarification — D-291:** with old `A → B → C` and new
`A → X → Y`, eligible latest-own Undo can remove Y then X. X is immediately after
the fork A. Removing X leaves A as the
current boundary and retains B→C without automatically activating it; return is
separate. Target order and the concerned-field rule still apply.
Recompute eligibility from the retained tree after each accepted Undo:
if removing the last action of a new continuation eliminates the actual fork,
there is no permanent “was once a fork” lock. Any next target still needs every
concerned-field check, including replay of the retained future beyond the shared
pointer; removing X does not by itself authorize Undo of A.

**Retained future (D-245, corrected by the owner rule):** validate every event on
every retained successor continuation after the target,
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

**Redo and history switches — D-258:** in normal current play, explicitly creating/
activating an interruption is a qualifying shared-history switch and clears both Redo
queues. The former stranded-but-retained queue example is superseded: Redo is
unavailable because the entry is gone, not a promised blocked restoration route.
Do not keep the queue through the switch, reinsert at the current head, rewrite
events to force a match or create a recovery branch. D-234's independent
no-cross-fork refusal is superseded by the authoritative concerned-field rule;
this does not recreate an entry already cleared by a history switch.

**Bounded validation work:** N may bound a unit of validation work, not redefine
eligibility. The earlier refusal of a tuple-preserving edit solely because its
suffix exceeds N is superseded. Validate the complete retained future; never
accept after checking only a convenient prefix or permit a changed unchecked tail.
Scheduling and efficient validation remain technical work, not another undo rule.

N is a work bound, not by itself a complete CPU/memory bound. Technical
design must also budget target validation, payload sizes, snapshot loading,
reference checks, mandatory-effect fan-out and cache reconstruction. Exhausting
a work budget must not be classified as a dependency failure or commit a partial
edit with an unchecked tail; do not add a product Stack-count cap or expire history.
Verify large targets/cleanup with zero or short suffixes as well as long histories.

**Remaining work, not an owner-question queue:** complete relevant-state and
reinsertion-precondition mappings from the agreed rules; document anchors,
pending-request/recovery bookkeeping, IDs/versioning, grouping and cache updates.
Numeric N/counting and wire/storage representation are technical specification
work, not new Undo policy. The owner rule supersedes D-233/D-234's blanket fork
refusals; D-258 governs switch resets, D-235 stack
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

Unlike a dependent historical reference, a personal cursor is not a later shared mutation
protected by the replay test and does not block an otherwise eligible Undo.
Cursor fallback itself changes no shared play and adds no mutation. Node-ID,
versioning and cache reconciliation remain technical specification work.
The same ancestor fallback applies after full recovery if old deltas were pruned;
retain the required parent/removal information. D-289 reaffirms this existing rule,
not a new gap or a separate recovery workflow.
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
Explicit historical continuation creates a branch under D-179 and clears both
Redo queues with that switch (D-258). The graphical tree only reviews retained
groups and closes on tap; Make current/shared activation UI is removed. Merely
viewing/scrubbing remains personal and clears no queue.

**D-222/D-223 visibility:** hide inactive continuations that consist of **only one
player's actions and no hidden-information exposure**, with a
separate **Show hidden branches** button in the explorer, color/icon showing state.
Mixed-player and exposure-bearing continuations stay visible in full, including ordinary tails.
Hiding is presentation filtering, not deletion or access control; the active path
and retained states remain available. Classify the alternative continuation,
not the shared prefix; exact nested-branch presentation remains open.

Branch archiving is removed, not an unresolved integration requirement.
Remembered-head Redo and captured-parent Undo are retired designs (D-224–D-236).
Each Undo removes the eligible latest-own move (one invocation). Draw and tracking events use the same
concerned-field test, independent of information revealed.

### Complete current-game downloads — D-276

**Confirmed:** initial download and fresh-baseline/full recovery obtain the
**complete retained current-game tree**, not only the active path or branch
summaries. Include inactive and presentation-hidden
continuations, and retained descendants beyond an internal shared pointer. Explorer
filtering and personal review do not determine which retained records are downloaded.
Shared prefixes are the same nodes, not copied once per branch.

Download current board/control/lifecycle state, retained root/initial data, required
definitions and recorded mutation/group/reference/exposure data needed by those
paths, plus applicable Undo/Redo payloads. Artwork stays separate and lazy. This
does not require a full board snapshot for every node, public-catalog download or
finished-session archives, which remain excluded from R1 under D-214.

Use compressed/chunked transfer as needed, automatically completing all tree pages;
lazy branch fetch is not the default. A partial/interrupted download is explicitly
incomplete, not absent history or a completed full sync. A coherent completed
download supports retained-branch read-only review offline; new shared actions and
logged inspection still require connection under the existing rules. Account-private
Logout cleanup/recovery protection is unchanged.

Normal play/reconnect with usable deltas synchronizes only changes, including
Undo/Redo's edits to existing topology and newly retained branches, not a full-tree
round-trip per action. The owner expects one long path with short offshoots, perhaps
less than twice the path size; this is a workload expectation, not an enforced
branch-size/history cap, a proven bound or permission to omit larger continuations.
See [transfer design](../04-technical/sync-and-storage.md#complete-current-game-transfer--d-276).

## 3.1 Three positions that must never be confused

- **Shared head / active state:** synchronized current game-state pointer. It may
  be an internal node with retained descendants after explicit activation, not the furthest
  published node on a stored continuation.
- **Review cursor:** boundary currently displayed to one participant.
- **Client predicted head (local head):** the received accepted server replica plus
  that client's provisional requested actions/control changes. It is latency hiding,
  not independently authoritative game state. Incomplete form input is UI-only,
  not a game-state boundary (D-259).

Track the accepted replica's server revision separately from these displayed
positions. A surviving shared node can have a revised materialized state after
Undo/Redo; its node ID, a personal cursor or a prediction is not a sync cursor.

**Stable references — D-289:** a new predicted mutation has a locally known UUID
which the server retains if accepted. Later requests reference that UUID in its
original play scope, without identifier remapping or waiting to learn an eventual
server revision/ordinal. Missing predicted nodes reject normally if their creation
did not succeed. Accepted revisions/ordinals qualify reads, caches and exact recorded
contexts; they are not an extra whole-head guard or mandatory future values for
node-reference requests. Existing specific inspection/reference protections remain.

The UI labels which state it displays. “Current board” means shared head, not
“the actions both people have reviewed.” A review cursor may lag the shared
head by many actions. Advancing it does not write another game-state action.

Instant synchronization updates downloaded history and the shared-head indicator;
it does not automatically advance a historical review cursor. D-277 explicitly
includes live presentation and grouped playback as described below; the earlier
generic live-follow exclusion under D-241 must not prohibit those modes. Review
progress remains personal, not synchronized presence/read receipts. Local edits
advance the live predicted view immediately, with pending status until final result.
If the viewed node is deleted, D-238's parent fallback applies; preserving a
personal review position does not require retaining a deleted node or snapshot.

The published future is a proposed continuation in the players' shared play,
even though it is the selected shared branch in the storage sense. The other
participant may interrupt any of it later.

### Live and playback presentation — D-277

**Entry-trigger supersession (r8/r10):** only opening behind head automatically
enters timeline mode; live resume/opponent changes/inspect/menu actions stay live.
Use the per-game/account last-seen node or hidden empty root and uncapped normal
group pacing, as section 3.12 defines. Older background-trigger wording below is
superseded, not the accepted/predicted-state reconciliation rules.

**Confirmed:** use two personal presentation modes over the same continuously
synchronized accepted tree/state and in-memory pending requests (D-278):

| Mode | Display and progression |
| --- | --- |
| Live | Display accepted live state plus valid local predictions. Apply incoming remote changes immediately on arrival at a coherent accepted revision, then re-evaluate the remaining original predictions. Do not delay visible remote changes for group closure, a playback step or an earlier acknowledgement. |
| Playback | Display the chosen concrete historical boundary and advance automatically or stepwise by recorded event groups. Accepted updates still synchronize/persist immediately, but extend the catch-up data without silently moving a paused/scrubbed cursor. A group, not the incoming network batch, is the normal presentation step. |

- Returning from background with unpresented game updates enters playback from
  the last locally displayed/reviewed boundary. Determine this from presentation
  progress, not only the latest downloaded revision: background sync may already
  have received the updates. Same-path updates need no rollback; a displaced path
  uses D-177's common-ancestor rollback before new-path group playback.
- Catching up through the currently known active continuation returns to live;
  later arriving changes then apply live. Arrival during catch-up extends what
  remains to watch, without losing updates in the handoff. This is not proof that
  the server can never advance again or a group-closure requirement.
- Deliberate gameplay interruption/continuation at a chosen boundary returns to
  live prediction as that new intent is provisionally applied. D-179 still requires
  explicit continuation intent; no new shared branch exists until server acceptance.
  Merely pausing/scrubbing automatic playback is **not** that interruption and
  remains playback. Normal rejection/recovery preserves original requests rather
  than silently converting a failed continuation into ordinary play.
- Acknowledgements/admission bookkeeping alone, with no newly presented game-state
  difference, do not manufacture a catch-up replay or a visible rewind of the
  player's own predicted actions. Existing deliberate personal history review
  remains stable; foregrounding does not require teleporting a retained cursor.
- Mode, presentation progress and animation are personal UI state. They do not
  select the server's head, alter grouping/command order, stop synchronization,
  add cross-device review receipts or permit offline actions. Reconnecting gates,
  logged inspection and deleted-cursor parent fallback remain unchanged.

First-open and timing are decided in section 3.12: hidden empty root without a
bookmark, uncapped group playback and account-local pause after animations. See
[prediction/playback design](../04-technical/prediction-and-playback.md).

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
access. D-295 selects **Back to games list** as the dialog's only button, with no
Review downloaded data action; leaving does not cancel uncertain requests. The
dialog does not declare unresolved requests failed. D-216 separately
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
Fresh play has a fresh root/stacks. Keep server outcomes and surviving process
memory scoped to original play, not fresh intent. D-278 may lose client work on
restart; protocol v1 defines IDs/fencing without a crash-durable outgoing journal.

**Session continuity (D-217):** app restarts preserve login while the session is
valid. D-268 forbids age/inactivity expiry; explicit revocation/token loss may
require login. Same-account login returns to the game, recovering surviving-memory
requests; restart uses D-278 server-first synchronization. Invalid authentication
does not prove command rejection. See [session continuity](accounts-decks-and-lobby.md#session-continuity-and-reauthentication--d-217)
and the completed authentication profile.

**Confirmed presentation (D-180):** update local game state immediately to hide
network latency; do not wait for server acceptance before showing the action. If
the server rejects it, animate from the displayed board toward the reconciled
target; an isolated rejection looks like an undo reversal, but D-185/D-277 do not
queue per-request reverse animations or roll back unaffected accepted/pending work.
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

### Prediction rebasing and cascading invalidation — D-277

The local live board changes immediately for successive actions; its accepted base
and outstanding predictions remain distinct. An older expected acknowledgement
advances that base and retires only the incorporated prediction. It does not set
the displayed board back to that older accepted boundary.

For card moves `M1: A→B`, `M2: B→C`, `M3: C→D` (with applicable original guards):

| Information/action | Accepted base | Remaining displayed predictions | Visible position |
| --- | --- | --- | --- |
| All three local actions made; results delayed | A | M1, M2, M3 | D, immediately |
| M1's expected committed effect incorporated | B | M2, M3 | D; no acknowledgement-only animation |
| Opponent's independent note edit incorporated after M1 | B plus note | M2, M3 still applicable | D with the note; no positional rewind |
| Alternatively opponent moves the card from B to X before M2/M3 | X | M2 fails at original source B; omit it, then M3 fails at original source C and is omitted too | Correct once from displayed D toward X |

Insert accepted remote changes into the accepted base **before** rebuilding the
remaining prediction overlay in original local order with the same shared processor
and conflict/applicability rules. If a prediction fails, omit its provisional effect
and continue checking later active predictions against that resulting state. This
can cascade through dependent moves, but is not whole-suffix/dependency-graph cancellation:
later independent actions whose original checks pass stay predicted. Even a later
same-card move can remain valid if accepted remote work supplies its original source.
Never repair a failure by substituting source/target IDs, changing guards or sampling
new randomness. Compute the reconciled target promptly without waiting for an
animation, acknowledgement or group completion, then animate only its net difference.

Here **cancelled means the local prediction is removed**, not that an already-sent/
admitted request is recalled or known to be rejected. Preserve its original identity,
intent, random input and exposure/result recovery until the server establishes the
actual outcome. Later actual acceptance/rejection is reconciled normally; a local
forecast cannot manufacture a server result, shared Undo or branch. A withdrawn
prediction stays out of the overlay while awaiting its result; later applicability
alone does not speculatively restore the cancelled sequence.

**Late acceptance clarification:** if the server accepts a move whose prediction
was withdrawn, process its committed change like any remote/opponent/other-own-device
update. Add it once to the accepted base in revision order, re-evaluate still-active
predictions, and present the resulting change under the current live/playback mode.
Do not suppress it because the client cancelled its forecast, or reinstate the old
predicted suffix. Retain the original request ID/author in history and deduplication;
"like a remote change" does not forge authorship, resubmit or duplicate the action.
A final rejection of an already-withdrawn effect needs no invented second rollback.
This does not settle unrelated never-transmitted buffer replay policy.

This is presentation reconciliation, not permission to manufacture server results,
retarget pending requests or change their identity/conditions. Preserve unresolved
request context until its actual result is established. D-177's deliberate history
traversal and interruption-resume playback remain distinct, with D-277's mode and
endpoint-animation clarification;
D-185 removes unnecessary visible rewind/replay from ordinary online reconciliation.

Acknowledgement of a predicted mutation must not apply it twice. Animation does
not gate persistence, publication or server acceptance. D-263 selects ordered
gameplay WebSocket; D-275 selects the durable inbox/recovery mechanism below.
Exact animation scheduling, wire/reference handling, queue sizing/saturation and
connection-cutover details remain technical work; these must preserve D-216's
automatic/no-duplicate/no-manual-resend behavior, concurrent sending,
individual outcomes and D-185 difference-only corrections rather than introduce
a blanket wait-for-response interaction gate. Final timing, reduced-motion/
accessibility presentation and layout remain later design work.

### Client crash loss and server-first restart — D-278

The owner explicitly accepts losing local requests, predictions and unreported
exposures on app process death. On startup, synchronize with the server and resume
from its accepted state; **do not replay an outgoing journal or recreate lost
actions**. No crash-durable client request/exposure journal or capture-before-display
disk write is required. This supersedes the stronger client durability obligations
in D-216/D-241/D-246/D-261/D-273–D-277, not server authority or server durability.

While the process lives, retain original request IDs, payloads, random inputs and
active/withdrawn prediction disposition in memory through normal reconnect and
same-account reauthentication. Retry uncertain requests only with those original
identities. Backgrounding/rotation alone is not process death. Logout clears the
account's volatile work along with its private downloads and token; callbacks and
already-admitted server work cannot become another account's data/actions.

Server-admitted commands still complete durably in order, subject to actual access,
lifecycle and applicability. Their committed effects synchronize normally, even
after the originating process forgot them. Missing local work is not a claim of
server rejection. Startup catches up revisions/full baseline and subscribes for
later commits; it need not enumerate lost IDs or wait for the old inbox to drain.
Never restore cached provisional board state as accepted state.

Explicit connected inspections submit ordinary requests, retained in memory like
other online work. D-286 supersedes separate reports for speculative disclosure:
extra information shown only by a rejected/different prediction is acceptable and
needs no additional event or provenance reconstruction, even while the process
lives. Accepted inspection/reveal records keep their history, latest-own targeting,
ordinary Redo clearing and concerned-field dependency checks, never an information
barrier. Recover the original request normally; process
death/Logout may lose unadmitted work. Server-retained records govern controls,
not inferred player knowledge. No offline inspections or competitive anti-cheat
promise is added, and no accepted server records are discarded.

Saved account data and accepted replica/cursor transactions remain durable. Local
unfinished form drafts and personal presentation progress are best-effort saved UI
state, not a guarantee that every last keystroke or outgoing action survives a crash.

### Request identity, durable admission and recovery — D-275

**Confirmed:** each command has a stable unique request identity and original
semantic payload with account/actor, game/lifecycle context, action data/references/
limited guards, random input and explicit historical intent when applicable.
Retransmission preserves that intent/identity; same identity with different contents
is misuse, not another action or permission to overwrite a recorded result.
Credentials/connection replacement on same-account recovery are transport context,
not a change to immutable gameplay intent. Exact wire fields remain technical work.

Use PostgreSQL-backed durable per-game admission and serial processing in the
server-assigned arrival order. Preserve ordered client sending and cross-player/
device interleaving without dependency-graph reconstruction or client-clock sorting.
Unrelated games may run independently. A **durable receipt means received/pending**,
not successful acceptance, a committed mutation or proof of replica convergence.

D-285 uses the local FIFO and ordered WebSocket/TCP connection, not a contiguous
client admission sequence. A rejected or never-admitted message leaves no missing
number blocking the next valid message. Server admission ordinals still order
durable work, and retained uncertain requests still use original-ID recovery.
Do not reconstruct missing messages on the server or infer rejection from timeout.

Selected lifecycle, with exact schema/locking implementation still to specify:

1. The user completes a direct operation or the necessary inputs for an action.
2. Capture its immutable request and prediction in process memory, and display
   that provisional result without waiting for a network round trip or disk write
   (D-278 replaces the earlier atomic client-persistence prerequisite).
   This is not an accepted server transition or a separate local game (D-259).
3. If connected, begin publication immediately in client action order. Do not
   wait for a group to end, a timer, screen exit, or another user action.
4. Admit the authenticated original command/order durably. Only after this commit
   may the service report a durable receipt; the gameplay result is still pending.
5. In that per-game order, check actual access/lifecycle/applicability/guards and
   atomically commit state/history/control changes, final result, relevant sync
   changes and outbox work for each individual command. Admission does not promise
   success; one rejection does not cancel later independently applicable commands.
6. Resolve local request status from its final committed outcome and reconcile
   accepted changes once, independently from whether replica catch-up is complete.
   A timeout/lost response leaves an unknown outcome to recover, not fabricated
   rejection. A pending duplicate keeps its original inbox slot; a completed one
   returns its original accepted/rejected/no-op result rather than executing again.

Same-process reconnect automatically looks up/retransmits retained uncertain original requests as needed
and restores accepted state through revisions or a coherent baseline. Fence obsolete
connections from new admission after replacement, scoped to that installation/
logical stream: other devices stay usable and already-admitted records are not
discarded. Preserve local original order for not-yet-admitted recovery work without
routine per-command acknowledgement waits. Old-play/preparation/invitation requests
cannot affect fresh lifecycle context; original committed results remain recoverable
without reapplication. Process restart instead uses D-278 server-first sync;
Logout/account fences apply even when callbacks outlive the volatile queue.
See [request ordering/recovery](../04-technical/request-ordering-and-recovery.md)
for the selected mechanism and remaining exact wire/retention/fencing work.

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
  activation remain provisional until a final committed outcome, not merely an
  admission receipt. D-180 specifies animated
  rejection feedback, composed under D-185. Retain recoverable online work;
  exact recovery wire states and animation scheduling remain technical work within
  D-275's selected identity/inbox/reconnect mechanism.
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
one group animate concurrently as the group-start to group-end board difference
(D-277), not queued intermediate moves of the same object; they remain an ordered
sequence of individual nodes. The client pointer is a node, not a group.
Ending suggestions are strict singleton groups under D-257: neither preparatory
events nor later companions can join them. Present the final board before the
suggestion's acceptance dialog; grouping does not delay event synchronization.

D-162–D-175 specify sequence-based presentation groups and timeline group-start
markers. Same non-Stack move tuples group, including interleaved taps; real-card
Stack moves and Stack projection creations remain separately visible. Ordinary
card edits, manual face/cover edits, token removal, player values/counters and
persistent-link creation/removal group together. Token creation and Clone group
together with interleaved customization edits. Combat-area creation and projection
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
needed. Its successful shared-history switch clears both Redo queues atomically
(D-258). This is deliberate, not an accidental consequence of concurrent online
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
state, not the contents of either line. Make current UI and branch archive/restore
are removed by r6b; explicit continuation is the route to resumed historical play.

The preserved suffix is not automatically replayed on top of a reaction. It may
refer to objects, positions, or values that are no longer present. A future reuse
tool must preview and validate every copied action; automatic replay is out of
the proposed first release.

## 3.6 Review progress

### Compact grouped tree presentation — D-299

There is ONE history mode. Its timeline tree button opens a full-screen dense
group tree, with timeline chips/icons and branches in parallel lanes, centered on
the viewed cursor. Each group's LAST event supplies its icon/color and tap target.
Tap moves the review cursor to that event and closes the dialog; retain only the
selected description. No legend, bottom buttons, Make current, Continue from here
or Branch details in the dialog. Review alone never activates a shared branch;
hold-branching uses section 3.12. The older explorer interaction below is
superseded in full; mutation-level timeline access and retained branches remain.

H06 replaces D-298's oversized event-letter graph with a compact pannable group
graph. Show the shared **active path and exact current position**, distinct from
the locally selected group. Each normal vertex represents an effective playback
group, using the representative event's icon under
[the chosen R1 ranking](history-grouping.md#r1-representative-group-icons--d-299).
Chronological/tree order never changes to match that significance ranking.

- Open from the table/history tree button or branch stub. Pan the graph to root,
  other paths and later groups; keep usable targets and accessible graph navigation.
  Root is a separate initial-state marker. One **Make current** action activates
  the selected target; the view-title menu contains framing/Show hidden options,
  not a row of Review/Continue/Follow/Root/filter/archive buttons. Close/outside/
  native Back dismisses without activating or recalling a submitted request.
- Selecting a group reviews **its last effective mutation on that path**, not its
  representative event. Bind that exact event ID when selected. Group growth,
  icon/rank changes or sync must not silently substitute a newer group end for
  the pending activation. Removed targets follow normal local ancestor fallback,
  but an invalid bound activation needs fresh explicit selection, not a guessed
  server target. Make current is a no-op only if that exact target is already
  shared current, not merely because selected/current belong to the same group.
  Opening the graph from an already-selected exact timeline boundary preserves
  that boundary (including inside a group); only a new group selection chooses
  its end. Thus the same Make current action still reaches any retained internal
  event via exact timeline review, without adding another prominent graph button.
- Grouping is only the drawing/navigation scale: retain every exact mutation,
  fork, root/internal target and current pointer. A real fork cuts **rendered
  group ranges** if needed; this does not add a group-start marker or change ping
  scope. Show a fork junction, never collapse two continuations into one tile.
  Each displayed range uses its included events for the representative. An exact
  selection of a split range targets its **last included mutation**, not a later
  range across the fork; the same stable exact-target binding applies. An exact
  current/selected point inside a range is indicated as inside that group, not
  falsely moved to its end. Exact-event timeline/detail navigation still exposes
  every boundary and its explicit Make current/accessibility action, without
  expanding the graph into one button per event.
- Make current is explicit shared activation under the existing guards/ordering;
  a real shared context change clears both Redo queues. Merely selecting/panning
  is local. Historical **Continue here** remains the existing separate top/paused-
  transport/accessibility route: arming alone creates no branch, first successful
  mutation alone does. Normal return-to-live catch-up and D-222/D-223 hidden-path
  filtering remain, with Show hidden in the small view menu. No second-player vote,
  automatic Undo, priority state or activation from a tooltip is introduced.
- **Archive/return is not required for branch switching.** Withdraw H07 from the
  current UI packet and remove that clutter from normal navigation. Choosing an
  old retained group/node and Make current leaves both continuations intact; it
  neither archives nor deletes either. The older reversible archive capability in
  section 3.8 is not retired by this UI-only change; its advanced presentation is
  not selected by the withdrawn mockup. Historical H07/provenance remains in Git.

Whole retained current-game download, sync, exposure/Undo rules and offline read-
only review remain unchanged. This is a UI composition, not an application or a
claim that every advanced filter/archive/detail state has been pictured.

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
that every historical mutation can always be restored: dependent later events
can prevent removal/reinsertion, blocked entries are not skipped, own new play clears
Redo, and deliberate history switches clear both queues (D-224/D-227/D-230/D-258). Submitted
game-wide chat (D-94) and acceptance of an ending suggestion / return to lobby
(D-212) have no gameplay Undo. Excluded authoring/manual-group/recovery-center
features are not recovery obligations. Ordinary unfinished input retains local
editing/cancellation; branch archival/restore is removed.
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
retained branches, Undo, exposure preservation and downloaded read-only
access are unchanged. See the [lifecycle contract](accounts-decks-and-lobby.md).
Non-undoable acceptance and deferring finished-session review do
not authorize deleting prior history.

To repair a value while keeping later play active, perform its normal edit now;
this does not require a separate historical-Undo mode or correction wizard.

After replacement play, only single-player/no-exposure inactive
continuations qualify for default hiding. Mixed-player and exposure-bearing
continuations remain visible in full. Hidden nodes retain
identity, payloads, ancestry and stored random results;
the filter never changes game state. See section 3.9 for exposure classification.

D-176 preserves individual recorded changes, but r2/r6 correct Undo scope:
one multi-action invocation is one move. Display groups never merge independently
invoked actions for Undo. An actual draw plus advancement retains its exact move
and tracking transitions; do not fabricate a draw for an empty Library.
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
| Interruption | Eligible latest-own Undo does not activate the displaced future. Review and explicit continuation are separate; no Delete branch and return. |
| Activate another branch/node | A successful switch to a different shared context clears both Redo queues atomically (D-258), without creating a gameplay mutation or selecting the personal cursor as an Undo target. Apply latest-own targeting to the selected shared timeline and D-245 checks to retained future events. A same-current-target no-op does not clear queues. |
| Delete/archive branch | Superseded by r6b: no such action/state/SQL or restoration path. |
| Group metadata edit | No manual group editing in R1 (D-241); automatic regrouping preserves nodes and personal cursors. |
| Submitted game-wide chat | No game Undo (D-94), Edit or Delete (D-240). Corrections use another message; no expansions are planned. Unsent-delivery handling remains distinct; do not promise generic message Undo. |
| Pending win/loss/draw suggestion | Ordinary eligible latest-own Undo removes the suggestion without ending play; acceptance remains a separate lifecycle transition (D-211/D-212). |
| Accepted ending / return to lobby | Both players return after acceptance. Acceptance is not undoable; Undo cannot reverse this transition or reopen ended play (D-212). Pending suggestions remain undoable under D-211. |

Undo/Redo are synchronized tree-edit controls, not board-action child nodes and
not entries to target with the next Undo. Redo reinserts the last-undone mutation
at its recorded boundary under target fidelity and suffix checks. Explicit tree
activation instead selects an existing node; review alone never changes shared play.

Rapid Undo must work before, during, and after acknowledgement. Pending/in-flight
requests retain original identity through same-process retry without double
application. D-278 permits loss on restart; server-admitted work remains durable.

**Current server request semantics (D-236):** distinct requests are successive
presses against current latest-own candidates; retries recover one original
outcome. The former D-161 captured-parent/stale-collision rule is retired, not a
current acceptance oracle. Blocked/absent candidates cause no removal. Reconcile
predictions to current state without jumping unrelated personal review cursors
or overwriting newer state with an old response snapshot. Non-Undo branch controls
retain their separate referenced targets and applicability checks.

## 3.8 Deleting an active interruption

**Superseded by owner playtest r6b:** branch archiving is removed everywhere—UI,
protocol/actions/state and SQL (including migration). There is no archive/restore
or Delete branch and return operation. Retained alternatives remain reviewable;
branching uses explicit continuation, not Make current. The historical description
below records the retired behavior only.

“Delete this branch and return” is a single atomic control operation:

1. Show the branch to be archived and the exact return continuation and board.
2. Archive the selected branch and select the chosen return branch together,
   clearing both Redo queues if this changes the shared selection (D-258).
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

- Undo/Redo eligibility uses only the authoritative concerned-field test, not
  whether anyone can forget information already seen.
- Delivered push notifications cannot reliably be retracted.
- Exported files or screenshots cannot be recalled.
- An external account deletion or legally required data erasure is not a
  recoverable in-game operation. Its policy must explicitly disclose that.
- Navigation has Back/history behavior rather than shared game undo.
- Submitted game-wide chat is explicitly not undoable (D-94), and is the only
  comment channel; there is no event-comments UI.

The UI must explain these boundaries instead of promising physical reversibility.
No history compaction or retention policy may silently remove the promised
in-game undo capability.

### Information-gaining actions and undo branches

Actual retained alternatives follow D-222/D-223 filtering: only single-player
paths without exposure qualify for default hiding. Undo creates no
alternative copy (D-232). Exposure classification affects filtering/records, not
Undo/Redo eligibility.

Exposure coverage, revised by D-252: explicit Inspect other side, library
View/Reveal, and die/coin results. Own-area hold inspection shows real faces
without an event; opponent-area hold does not show hidden faces. Ordinary
visible inspection and every search keystroke are not exposure records.
This classifies the accepted server action/presentation, not what a player remembers
or every temporary predicted glimpse. D-91 covers accepted draws, moves and face
changes that display hidden information to either participant, not only explicit
inspection. D-252 own-area holds remain exempt. D-286 supersedes the requirement to
separately report information shown only by rejected/different predictions. An
ordinary visible-face gesture/keystroke is not itself an exposure record. Accepted
covered-face inspection logs viewing without requiring a board-state edit or
automatic gameplay continuation; record placement follows the lifecycle below.

### Exposure lifecycle and offline inspection — D-246

- New event-producing inspection requires a connection, even with cached faces.
  D-252 own-area holds create no event and may read downloaded real faces offline;
  opponent-area holds never silently disclose hidden information.
  Offline traversal and reading already-recorded visible content remain available.
  An explicit Inspect other side/View/Reveal requiring a new event cannot bypass
  that requirement through cached content. Explain the connection requirement
  rather than silently dropping logging or queuing offline events.
- Submit explicit inspection/View/Reveal as an ordinary connected request, with
  immediate prediction and no disk-before-display or acknowledgement wait. An
  accepted inspection records actor, play, accepted source/face/content references
  and separate viewed/recording context where applicable. No guessed prior knowledge.
- **Server execution resolves contents (D-286).** Top/bottom/all View/Reveal captures
  source and requested count/scope. Resolve the actual members/order and available-
  card clamp at execution. For Library `[A,B,C,D]`, a client predicts Reveal top 3
  as A/B/C; if Draw A executes first, accepted Reveal is B/C/D. Reconcile to B/C/D.
  The extra glimpse of A is acceptable: no second event, barrier, Redo clearing,
  compensating action or frozen historical reconstruction. Accepted tray membership
  then stays fixed under D-47; subsequent moves update locations, not refill it.
- A specific-card/face inspection retains its exact target/context. If that context
  was removed or became inapplicable before processing, reject and reconcile normally.
  Do not substitute another target, restore the removed step or keep a separate
  provenance record solely for the predicted view. An already-accepted inspection
  keeps its normal recorded references and dependency protections.
- A rejected/different board prediction creates **no separate exposure report**.
  Record accepted revealing actions/results, not all locally shown content. This
  intentionally replaces D-89/D-91/D-246's stronger speculative-disclosure accounting,
  beyond D-278's permitted crash loss. Draw/move requests still bind their original
  card identities and retain movement guards; they do not gain replacement draws.
- Deduplicate retries of the same inspection request, not distinct accepted
  inspections. Same-process recovery keeps its original intent; restart/Logout may
  lose unadmitted requests under D-278. Summaries do not reveal covered names.
  Recorded visible content remains readable offline without a new event.

An unavailable connection still disables new event-producing inspection. No
separate exposure queue or reconstruction of lost/temporary disclosure is required.

### Exposure participation in controls and visibility

S-04 distinguishes a board transition from an effective logged action. “No board
edit” is not “no action.” Accepted logged inspection participates in latest-own
targeting, ordinary own-new-action Redo clearing and V grouping. It can be undone
or redone under the same authoritative concerned-field rule as any other event;
information exposure adds no refusal condition.

Associate accepted exposure with its actual effective recording boundary, keeping
viewed context separate. Its concrete content, presence and concerned references
participate in replay validation and reference/payload work. Own-area holds and
permitted visible-face navigation are local only: no event, candidate, stack,
grouping, filtering or replay effect. Rejected/different predictions add no extra
event or old-context snapshot under D-286; a glimpse alone changes no controls.

An exposure-only event has a stable causal position and effective revision at its
recording boundary, ordered with other accepted actions. It need not add a second
board-transition node or a child branch to an internal state with a retained
future. Personal views can distinguish before/after that effective event without
fabricating a card edit. Latest-own selection includes these effective actions on
the shared timeline, not only nodes that move cards. Later opponent edits use the
same concerned-field test and do not clear the other player's Redo.
Exposure records on retained successor continuations also enter full dependency
validation; their data/reference costs cannot be omitted from N/resource budgets.
Exact side-record/node encoding and same-boundary cursor representation are technical.

For filtering, “contains exposure” means its **recording-context** membership,
not every path merely inspected by that record. Inspecting an inactive alternative
from current play records the action on current play and links to the viewed
alternative without activating it. The referenced alternative does not become
exposure-bearing merely from that incoming reference; its original exposure/
author records still decide filtering. Keep the original-context route
discoverable even if the alternative qualifies for Show hidden branches. A shared
prefix exposure alone still does not qualify an otherwise ordinary suffix.

D-278 supersedes crash/Logout-durable outgoing recovery; D-286 additionally removes
separate speculative-disclosure reports. Keep original inspection requests while
memory survives, until outcome resolution or Logout. Admitted server requests and
accepted records remain durable; unadmitted forgotten requests may be lost. Neither
case permits cross-account publication, reopening ended play or fresh-board overwrite.

Retain actor, operation/source context, exposed references or concrete result,
and a route to the original step in the retained history. Distinguish undone/
inactive-line status. Inspecting that branch does not itself activate it or
reapply board changes. No branch archival feature remains.

Ordinary summaries report that viewing occurred without automatically showing
covered card names/faces to the other participant. Original context remains
inspectable under cooperative full access; rolls retain actual recorded results.
"Permanent" here means retained across normal undo/branch operations, not an
exemption from eventual game/account erasure. Exact triggers/coalescing,
historical/offline views, ordinary inspection-request recovery, durable publication, schemas and retention
remain Q-31. No competitive anti-cheat guarantee is implied.

Confirmed branch-level filtering (D-222/D-223 refine D-160, replacing D-91's cutoff):

1. Retain every node of a genuine alternative displaced by explicit continuation;
   Undo alone creates no alternative copy.
2. If that continuation contains exposure or actions by both
   players, keep all of it visible, including ordinary ancestors/intervening/trailing nodes.
3. A single-player continuation without exposure qualifies
   for hiding behind Show hidden branches.
4. Visibility does not move the active pointer or hide unrelated alternatives.
   An exposure in the shared prefix alone does not make an ordinary abandoned
   suffix an exposure-bearing continuation.

Example: explicitly continue from root beside `A → View B → C → roll D → E`.
Keep that actual alternative A–E visible in full; Undo creates no alternative copy.
Either person can inspect/activate retained states; inspection
alone never activates them. Exact exposure coalescing, pre-fork
presentation and concurrent/non-Undo control details remain open.

## 3.10 Randomness and branching

Library is a list of ordered (known) and unordered (hidden-order) layers, see the
domain model. Edge actions take the real concrete edge card. Placing cards makes
their place known; Shuffle makes all one unordered layer; random-order top/bottom
moves add one unordered layer. EVERY branch start reseeds each unordered layer of
both players deterministically with H(game secret, fork node, forking player,
library, layer ID), preserving ordered layers and layer membership. Repeating
draw/Undo/draw or a fork from the same point yields the same cards. Record reseed
inputs as semantic state; do not rewrite a later event's input during Undo checks.
Own Look/scry/Reveal may be undone under the authoritative concerned-field rule;
information exposure is never an eligibility veto. Library piles still show only
backs, without a known marker, and reveals cease on leaving their panel (r11).

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
Undo/Redo uses the authoritative concerned-field rule; fresh generation and
recorded replay remain distinct regardless of what was seen. Exact generator/source
and online concurrent operation protocols remain open, without reopening seed-reset policy.

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

**Undo granularity supersession — playtests r2/r3/r6:** a multi-action invocation
(quick action or multiselect) counts as one move and is undone together. Separately
invoked events remain individually undoable even when grouped for display. Thus a
manual whose-move pass groups backward but Undo removes only that pass. The older
per-constituent Undo wording below is superseded, not the stored exact transitions
or immediate publication requirement. Current shortcut effects are in
[board controls](board-and-controls.md#108-additional-controls-needed-for-complete-play).

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

**Current presentation — playtests r4/r6/r7/r8/r10.** This replaces older entry,
transport, continuation and timing text below; exact-event scrubbing, centered
cursor, author-colored icons and endpoint animation remain current.

R17: “Continue playing from here” (fork) arms the exact reviewed boundary and
keeps the play strip visible there; “Return to current” starts catch-up without
arming or a continuation chooser. Neither changes shared history. Move-to-you
auto-pause is skipped when the immediately next event on the active path is yours;
an opponent next event or no next event retains the existing pause behavior.
Transport has no helper caption; its hold/accessibility continuation action remains.

- Persist the last node shown live locally per (game, account), independently of
  sync revision. Debug builds retain it across Logout/account switching. Opening
  behind head enters timeline mode and auto-plays EVERY group at normal pacing
  to live, never capped or fast-forwarded. No bookmark starts at a hidden empty
  root. If head is not a descendant, show “Branched” about 1 s, roll back quickly
  group by animated group to the common ancestor, then play forward. Every step
  is O(delta), not root replay. Live opponent updates, resume, inspect and menu
  actions stay live; automatic timeline entry occurs only on game opening behind
  head. Entering history manually remains available.
- Pause AFTER each group's animation; default 1.5 s. Settings slider 100 ms–10 s,
  local per account. Hold inspection pauses existing playback, never turns live
  play into timeline mode. The panel overlays the board without shrinking its
  viewport; clip the battlefield bottom only, never scale/move it. The intact
  bottom HUD (hand, piles, life, counters) sits immediately above the timeline.
   Use gray top/bottom bars, dim the field, show a faint centered “Timeline mode” + icon.
  Transport is white-bordered: green play/orange pause left, large “Auto: off/on”,
   no helper caption. Branch markers use correctly directed graph
   lines. There are no timeline overflow/More/History options menus or manual
   Download history action; sync/recovery is automatic. Actions have direct
   buttons or live in Table actions. Toggles are normal buttons whose color/icon
   shows state, never label-plus-switch rows.
- Holding transport branches immediately from the captured cursor, running or
  paused, without a dialog. Arming shows only a small top-bar fork icon; tap it to
  cancel. No banner or separate top continuation button. The first successful
  gameplay edit creates the branch. Toggling history OFF after cursor movement
  alone shows “Continue from here” / “Back to latest”; outside dismiss stays in
  history. At head no chooser. Back exits review directly without that dialog.

**Confirmed D-277:** return from background with unpresented updates enters
grouped playback; catching up or deliberately continuing gameplay at a chosen
boundary returns to live as defined in
[presentation modes](#live-and-playback-presentation--d-277). Synchronization
continues in either mode. A pause is not a shared gameplay interruption.

**Confirmed D-177:** when returning after the opponent interrupted the player's chain:

1. Begin at the player's last local game state, not by replacing the board with
   an unexplained latest-state snapshot.
2. Traverse the old path backward fairly quickly with animations to the
   interruption/common-ancestor node, deterministically rolling back mutations.
3. Traverse the new active continuation forward with animated automatic playback
   or player-controlled steps. Each group is one playback step; its constituent
   changes animate as the net previous-boundary to group-end board difference
   while remaining ordered mutation nodes (D-277).
4. The player can pause automatic playback. While paused, show a horizontal
    event timeline at the bottom of the screen. Dragging it left/right selects
    an exact mutation boundary, including a boundary inside a displayed group.
    D-291 keeps the path timeline visible during playback too: touching/dragging
    it pauses first, then scrubs with endpoint-based board updates while dragging.
    Previous/Next transport steps groups; scrubbing remains mutation-granular.
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

**Gameplay review UI — D-291:** historical browsing hides gameplay/edit controls
and replaces live chrome with Previous, Play/Pause, Next and the exact path
timeline, including compact off-path branch stubs. This is not permission to drop
retained alternatives or stop sync. Explicit “Continue playing from here” is
separate from playback: pause at the selected exact node and arm personal
continuation intent, then reveal gameplay controls. The first successful new
mutation carrying that intent creates/activates the continuation; Undo/Redo still
edit the existing tree rather than supply that intent. Arming, cancelling,
scrubbing or a failed/no-change action does not create an empty fork. Undo still targets shared current
history under its existing rules, not whichever node the personal cursor displays.
Exact button shapes/spacing remain trial layouts for owner review.

**D-296 transport/continuation presentation:** Play/Pause has visible text and
fills the space between Previous/Next in portrait. Landscape uses a similarly
prominent central button with Previous/Next directly attached, not at viewport
edges. While **paused**, holding Play invokes the same explicit continuation as
the retained top-corner button: capture the selected concrete boundary at pointer
start and, on recognized hold if still valid, expose
ordinary play controls using that state. Recognized hold consumes the tap; never
also start playback on press/release or arm from a moving playback cursor. The
running Pause button has no continuation shortcut. Keep an explicit accessible
action alongside the top button; no tooltip/tutorial text is added for the hold.

Armed play has **no Continuing from… banner** and otherwise looks like live play
at the head, but displays/acts from the captured historical state. This replaces
the earlier banner, not intent or request boundaries. Cancel/Return remain in
normal contextual navigation/accessibility rather than a mandatory banner.
Hold/button/arming alone creates no fork; only the first successful mutation
carrying explicit continuation intent does. Cancel, failed/no-change actions,
offline restrictions, deleted-boundary validation and ordinary Undo target rules
remain unchanged. Shared updates never silently substitute the live head for the
captured continuation boundary.

If sync removes that captured node during the hold, the review cursor's normal
ancestor fallback must not retarget this continuation gesture. Leave it unarmed/
show the normal invalid-context outcome; require a fresh explicit choice at the
new cursor. Changes to playback-group presentation alone do not change a retained
node's identity. Request-time deleted-boundary validation still applies later.

**D-295 timeline density:** use compact event-type icons as the actual path nodes,
not widely spaced generic dots with separate type labels. Different event types
have distinct icons; the same type consistently reuses its icon. Exact selection
must remain evident without obscuring that icon. Scroll/window a long path without
truncating/coalescing its mutations; branch stubs, exact cursor and group transport
retain their existing semantics. Accessible details expose type, author, boundary
and result without requiring precision taps on tiny icons. Final icon artwork/
hit geometry remains UI completion; density is not a new grouping/history limit.

Held-event descriptions use compact subject-first text without the actor (already
shown by event color): `Llanowar Elves tapped`, `Lightning Bolt → Graveyard`,
`Life 20 → 17`, `Bob's life 20 → 17`, `Turn 5 → Bob`. Counters include color and
before/after values; notes include their text when visible. Keep the 180-character
plain-text cap; hidden card statistics and note text remain redacted too.
Card names use only visibility at the event's parent plus that event's disclosures/
destination exposure; hidden identities read `A card`. Later reveals never rename
earlier hidden events, including other players' hand, Library, face-down and Look/
Search cards. Library known-order metadata alone does not reveal card faces.

**D-296 author/groups:** color each event-type icon by the originating participant,
not affected card owner, current viewer or latest editor. Add faint vertical
dividers exactly between adjacent events belonging to different effective
playback groups, not after every event or only at author changes. Grouping stays
derived from retained events; the dividers do not create/edit group boundaries.
Selection, branch stubs and author/type details remain accessible without relying
solely on color; selecting a node must not replace its author color with a generic
selected tint.

**D-297 center anchoring:** the exact selected event remains at the horizontal
center of the timeline viewport. Its selection square is screen-fixed there;
translate/window the path underneath it during exact-event scrubbing, grouped
Previous/Next and playback. Do not clamp the selected node toward an edge near
either endpoint: at the first event the left half is empty, and at the last event
the right half is empty. Draw no invented nodes or continuing path line in that
unused half. An only-event path centers that one event with both sides empty.
Rotation/resizing, group reflow and normal deleted-cursor fallback recenter the
actual resulting selected boundary; they do not alter its identity to fill space.
Retain every event, author/type/group/branch information and accessible navigation.
This is personal presentation, not shared activation, Undo or a history cap.

The owner has specified the playback/timeline behavior, not its final geometry,
animation speed, automatic-versus-manual preference default or accessibility
layout. These details remain for the appropriate later design work. Every
mutation publishes immediately regardless of playback or grouping.

### Endpoint-based board animation — D-277

Animations always bridge the previously displayed board/scene to the next selected
board target; they do not replay a list of individual mutation effects. Apply the
underlying ordered mutations with the shared engine to compute that target first.

- In live mode, three moves of one card received together produce one transition
  from its previous position to the final position, not three queued waypoints.
  Do not deliberately delay synchronization to gather a larger animation batch.
- In playback, advance the displayed state by event group and animate the net
  board difference for **each group**. Multiple moves inside one group collapse to
  their endpoint; moves in separate groups remain separate staged transitions even
  if all groups arrived in one network batch. Open groups may extend under existing
  rules, without a closure tap or forced rewind of an already presented prefix.
- Independent objects/fields may animate concurrently. A card returning to its
  original final position needs no invented movement; all intermediate mutations,
  references, random outcomes and exposure records still exist for exact navigation.
- A new target during animation retargets from currently drawn geometry without
  snapping back to an obsolete source/target first. Interpolated pixels are not an
  authoritative board or extra history node; drawing/hit testing share the visible
  geometry under D-267. Pausing/scrubbing selects a real mutation boundary.

This clarifies D-177/D-180/D-185 animation, not history coalescing, an atomic group,
loss of intermediate state, automatic gesture/command generation or cancellation
of protected exposure recovery. Exact timing/interpolation/reduced-motion design
remains UI/technical work.
