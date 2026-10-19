# 6. Synchronization and storage

Status: proposed protocol mechanics supporting confirmed behavior. D-179 replaces
automatic conflict branching with explicit interruption intent. D-187 normally
accepts ordinary actions against current state, with optimistic guards limited to
movement and Stack additions, not general previous-value comparisons. Exact API
and schema are not yet specified.

**D-182 release scope:** game actions and shared controls require a connection in
initial delivery. Downloaded state/history remains readable offline. There is no
offline gameplay creation or queue replay on reconnect. Online optimistic updates
remain; uncertain delivery of an already-started online request still needs
recovery, not an offline-play protocol.

**D-183 clarification:** a small local send queue of individual online actions is
required. Send actions concurrently and apply/fail each independently. Online-only
does not authorize stop-and-wait transport or a blanket acknowledgement barrier
on ordinary local interaction. The queue hides normal network delays, not enables
extended disconnected gameplay.

**D-184 ordering:** clients send actions in their local action order; the server
validates/applies them in arrival order. Multiple actions may be in flight without
acknowledgement waits. Preserve ordering in delivery, not a per-action dependency
graph or server-side reconstruction of client order. The exact ordered transport
is not yet selected; independent requests that can overtake each other would not
satisfy the ordered-send requirement.

## 6.1 Authority and local-first behavior

Proposed topology: native Android clients plus a persistent shared service. The
service serializes shared game control operations. Each client stores downloaded
game history, a materialized board cache, personal review state, unfinished input,
a small local online send queue and records needed to recover in-flight requests.
This is not an offline gameplay queue (D-182/D-183).

There is one authoritative active game-state pointer/path per game on the service.
It may point to an internal node with retained descendants after Undo. Online
clients may display predictions awaiting acknowledgement; disconnected clients
can review stale downloaded state but cannot make game actions. The UI must not
claim current shared agreement for stale or unacknowledged state.

Game action payloads are immutable once published. Control operations update
branch/head references, archive status, and metadata through a separately
ordered revision stream. Clients materialize state by action ancestry, not by
wall-clock timestamps.

**D-224 behavioral revision:** selective timeline removal/reinsertion requires
every later mutation's relevant-state `(from, to)` tuple to match its original
transition exactly, with a hard suffix-work cap N. This is stricter than D-187
ordinary live acceptance. Per-mutation state, N/counting, atomic validation versus
concurrent work, node versioning/reference retention and pointer/Redo-queue effects
remain open technical/behavior work, not an approved storage rewrite algorithm.
The earlier pointer control model below no longer defines all Undo/Redo behavior.

D-159/D-160 require an explicit active node pointer independent from retained
continuation tips. Undo moves it one parent back, regardless of actor; retained
nodes are never physically deleted by Undo or replacement play. The pointer move
is a control operation, not another board-action child. D-222 adds a remembered
last active head for Redo and a graphical explorer for explicit any-node activation.
Only single-player/no-exposure inactive continuations qualify for hiding behind
Show hidden branches, except event-comment paths also stay visible (D-223).
Mixed-player/exposure/event-comment paths stay visible in full. Sync must converge
on the shared tree/control state without confusing presentation filtering with
deletion. D-161 binds Undo to a specific node-to-parent transition and silently
discards it if no longer valid, reconciling client state without notifications.
This applies to concurrent online requests too. Offline Undo creation is excluded
by D-182. Exact remembered-head user/device scope, memory changes on activation,
concurrent/stale Redo/activation, schema and pre-fork/nested presentation remain
open. Retained existing
alternatives remain recoverable; D-179 prohibits new implicit conflict branches.

## 6.2 Publication and concurrency

Game actions may be made only with a connection in initial delivery. They begin
publication immediately in client action order; no offline gameplay queue is created.
There is no Send step or waiting for group closure. Service acknowledgement establishes the
shared result, not a notification or animation.

D-180 confirms immediate optimistic local state updates for latency hiding. A
rejected mutation is visually reversed with an Undo-like animation; this local
correction does not emit an Undo/control operation, acquire historical-continuation
intent or create a branch. Incoming opponent mutations are also applied through
animations. Keep authoritative state, pending predictions and presentation
distinct: animations neither delay synchronization nor establish acceptance, and
acknowledgement must not apply an already-predicted mutation twice. Preserve
personal historical cursors.

D-185 confirms the reconciliation target: accepted history plus remaining valid
local predictions. Animate only the difference from the displayed board to that
target, not a visible full rollback/replay of unaffected mutations. Retain accepted
local/opponent changes and valid pending predictions; correct genuinely invalid
later predictions as well. Example: a rejected move followed by an accepted tap
returns the card to its authoritative location without untapping it. An
acknowledgement alone neither repeats the mutation nor adds an animation.

Internal recomputation is not visible history traversal or a new shared mutation.
Do not turn a locally invalid prediction into a fabricated server rejection or
rewrite/cancel the original in-flight request; preserve identity and resolve its
actual outcome. D-177 interruption-resume/history playback remains unchanged.
Exact reducer/prediction bookkeeping and animation scheduling remain technical
design, constrained by these presentation invariants.

D-176 requires one node per individual mutation and immediate publication of
each node, including ordered operations from bulk/combined controls. Transport
batching must not collapse nodes or wait for invocation/group/animation completion.
D-159/D-160 make each node one Undo step. Retain invocation identity as context,
not as an atomic multi-mutation payload or command to skip parents. Tree-based
Redo and D-177 playback reuse recorded outcomes. Playback rollback/scrubbing are
personal traversal, not new shared Undo or publication. Both shared and local
displayed state reference mutation nodes, never groups. Exact protocol/schema
mechanics must support this behavior without priority/legality inference.

A publication must distinguish ordinary current-head edits from an explicitly
requested historical continuation. D-179 requires an explicit interruption flag
and chosen boundary for the latter. D-187 applies ordinary operations to current
state unless impossible, retaining limited movement/Stack guards rather than
previous-field-value or whole-game-head checks. Undo supplies
continuation intent automatically. Proposed additional fields include an
idempotency key, game ID, action data and group references. D-184 uses ordered
delivery rather than dependency metadata for sequencing. Object IDs and explicit
historical-boundary references remain necessary semantic data, not a sequencing
graph. Exact field names, revision scopes and schema remain technical design.

D-183 requires individually queued actions and concurrent transmission. A slow
acknowledgement must not routinely stall local interaction or prevent sending
other actions. Each request has its own result: a failed action does not make
another valid action fail as part of a rejected queue/sequence. Validate actual
possibility/structure and any exceptional movement/Stack guard individually at
current state. Concurrent transport does not make an atomic group or remove
server-side validation/application ordering.

D-184 fixes that ordering: process received actions in arrival order, including
interleaved arrivals from both players. Do not reorder by client timestamps or
wait for dependency declarations to reconstruct a sequence. A rejected arrival
leaves shared state unchanged and does not prevent checking the next arrival.
Ordered sending preserves a client's sequence on its connection, not an atomic
uninterrupted block against the other player's actions.

Authoritative parent mapping must support accepted current-head actions without
changing the requested operation, limited guards or interruption intent. Exact
ordered transport, new-object/reference handling, queue size/saturation and animation scheduling
remain open; they must support the confirmed concurrent online pipeline, not
restore stop-and-wait as a blanket policy. Preserve each operation's identity when
resolving delivery uncertainty; do not resubmit as a different action/random draw.

Proposed outcomes:

| Situation | Outcome |
| --- | --- |
| Ordinary edit remains possible at current state | Apply in arrival order and append the actual mutation, preserving unrelated fields. Do not compare old orientation/text/Set values or reject for an unrelated revision change (D-187). |
| One of several concurrently sent ordinary actions fails | Reject only that action; another action whose own checks pass may still apply. Do not cancel/roll back the whole send queue or require a sequence-level retry (D-183). |
| Requested operation genuinely impossible, or exceptional movement/Stack guard fails | Reject only that request without shared mutation or branch. Animate the actual correction under D-180/D-185 while preserving accepted work and valid predictions. Changed old values alone are not a rejection condition for ordinary edits (D-187). |
| Stack-entry request's supplied current Stack state differs from authoritative Stack state | Reject the addition (real-card move or new projection); compare-and-apply atomically in arrival order. No separate opponent/seen detection, priority inference or whole-game-head check (D-187). |
| Deliberate historical continuation, with explicit flag and boundary | Validate at the selected boundary, then create and activate the interruption branch atomically. Mere review/staleness is not intent. |
| Further online-pending work after a separate explicit interruption | Detailed dependency/continuation behavior remains open; do not resurrect the superseded automatic-conflict or offline-queue protocol. |
| Same idempotency key and identical payload retried | Return the original result; never apply twice. |
| Same key with a different payload | Reject as idempotency-key misuse. |
| Undo's specific node-to-parent transition is still valid | Apply that transition once; retain the original nodes. Do not retarget to another source/parent. |
| Online Undo transition is no longer valid | Silently discard the request and reconcile local state (D-161). No warning/notification/confirmation/retry task; preserve retained nodes and recoverable online work. D-182 excludes offline Undo creation. |
| Previously successful Undo request retried | Return its recorded idempotent result; no second move or false stale failure. Reconcile against current control state, not by overwriting newer state with an old success snapshot. |
| Stale non-Undo branch control command | Outcome remains a product decision. D-161 does not approve generic silent discard for branch activation/archive/restore. Preserve nodes and recoverability. |
| Invalid object/reference or structural constraint | Reject the affected publication with actionable structural error; explicit interruption cannot repair malformed actions. |
| Explicit historical boundary or new-object reference not yet authoritative | Check the original reference against state at arrival-order processing; do not substitute another base or wait on a sequencing dependency graph (D-184). A failed prerequisite does not cancel later actions whose own checks pass. Exact provisional-reference mapping remains protocol design, not an acknowledgement-before-send policy. |
| Connection drops during an already-started online request | Outcome may be unknown, not necessarily rejected. Preserve operation identity and recoverable context; exact resolution/retry behavior remains open. No new offline gameplay queue or action creation (D-182). |

**Confirmed D-187:** ordinary actions are attempted against current state and
normally succeed unless genuinely impossible. Validate/apply each operation in
one server transaction and preserve unrelated current fields. Previous-value
comparisons are not default validation. Card moves retain source/destination
guards; Stack-entry requests carry the client's current Stack state, compared
against authoritative Stack state at processing. A mismatch rejects; a match
allows a structurally valid addition. Compare-and-apply is atomic, so concurrent
requests based on the same Stack state do not both add entries. No separate
opponent/seen detector or whole-game-head check. Later queued additions carry
their predicted pre-action Stack state, including preceding local additions,
without acknowledgement waits. Snapshot encoding/equality representation and
provisional-ID mapping remain technical design, not the comparator itself.

Example: both clients see B. A moves card P from p to an empty q. B moves another
card R from r to an empty s. If both moves' conditions remain valid, record
`B → move P → move R` in server commit order, not sibling conflict branches. If
the second move instead expects P at p, or expects the now-occupied q to be empty,
reject it. Never silently choose a new source or destination.

This destination example applies to an empty-position move, not all move actions.
Approved pile joins/ordered insertion retain structural/capacity rules; precise
remaining movement guards are open. Ordinary card/occurrence edits follow D-187's
permissive baseline rather than a proposed table of previous-field comparisons.
Preserve unrelated current fields, actual existence/applicability and structural
invariants. D-185 fixes visible reconciliation; exact bookkeeping remains open.

D-186 confirms existing-counter arithmetic as an explicit relative operation:
validate the current counter is numeric, apply the requested delta to that current
number, and record actual accepted before/after values. Do not reject merely
because the old number differs, or apply a stale predicted replacement snapshot.
Counter Set now writes the requested value without comparing the old number/text
(D-187 supersedes that part of D-186). If 5 became 6 before Set 10 applies, record
6 → 10 and accept rather than reject for staleness. Retries return the original
result; replay/Undo uses recorded values, not renewed arithmetic. The
[limited-locking policy](../03-contracts/optimistic-locking.md) supersedes the old
25-rule proposal; its other category details were not approved. Exact wire
representation and unsettled action edge cases remain specification work.

An explicit interruption instead names the historical boundary and intent to
continue there. Validate the mutation in that selected state and make the new
branch current, retaining the displaced continuation. Ordinary rejected work is
not automatically retried as an interruption. Simultaneous explicit interruptions
and online-pending work after another interruption still need detailed behavior.

An idempotent retry returns the original result, not a second application or
renewed branch activation. Receiving control updates never generates a new local
game edit. Missing ancestry, corrupt input, and lost access remain explicit
failures, with local work retained for recovery.

## 6.3 Compatible edits versus historical replay

D-179/D-187 permit ordinary actions to apply at current state despite intervening
work unless impossible or an exceptional movement/Stack guard fails. This is
sequential application, not automatic merging of retained alternative futures.
Ordinary absolute edits may replace a value changed meanwhile. Replaying a
displaced historical suffix onto an interruption remains out of scope; merely viewing that suffix never activates it.
Personal camera/review settings remain separate from game-state edits.

Metadata/message revision streams must not silently reinstate broad stale-value
rejection under D-187. Exact feature semantics and revision domains remain API/
behavior design; the old batch's per-message CAS proposal was not approved.

D-94 game-wide chat must not be retracted by gameplay Undo. D-95 event comments
are ordinary nodes on the current path and remain in that retained history
after undo; no special comment-only branch mechanism is needed. Exact comment/
group association, revisions, edit/delete, unsent chat delivery, and idempotent
publication remain open. Do not apply a blanket message-undo policy to both.

Pings are generic, action-linked communication records, not game-rule target
declarations. They synchronize durably with source-action/group and effective
context references; they cannot mutate card state as a side effect. Their exact
history/revision placement is still a protocol decision. Live activity/cursor
presence is a separate best-effort channel: it expires, does not enter the
durable game history, and never replays stale offline presence as current activity.

## 6.4 Online request durability

D-182 supersedes the durable offline gameplay queue and its replay/dependency
state machine. D-183 separately requires a small online send queue with concurrent
transmission and per-action outcomes. Do not conflate the two or remove the online
queue to avoid supporting offline play.

Keep recoverable unfinished input separate from accepted game history. Persist
enough context for an online request whose outcome may be unknown after network
loss or restart; do not report server acceptance before acknowledgement or a
verified recorded result. Preserve idempotency, the requested operation and any limited guards,
explicit interruption intent and stored random outcomes when resolving that
request. Missing acknowledgement alone does not prove rejection or justify an
Undo-like rollback as a known server rejection.

Exact connection readiness, uncertain-request resolution, whether/how to retry,
queue sizing/saturation and animation scheduling remain open. D-185 fixes
difference-only corrections with accepted work and valid predictions preserved.
D-183 rules out a blanket wait-for-acknowledgement interaction gate; D-182 still
excludes extended offline gameplay/replay. Do not interpret ordinary network latency as permission
to freeze otherwise available local controls.

D-105 requires no priority state or Pass record to queue, publish, or interrupt
actions. Do not add priority-based publication ordering or legality rejection.
Participants judge timing; preserve actual structural/applicability validation,
explicit interruption and D-187's limited guards without interpreting queued work as
priority passes.

## 6.5 Incremental synchronization

Clients request control changes since a known service revision plus missing
action nodes and referenced definitions. Apply an update batch and its cursor
transactionally so interruption cannot leave a partially applied local tree.

Push/realtime messages are hints to fetch changes. They may be duplicated,
delayed, reordered, or absent. Reconnecting and ordinary refresh must converge
without them. Pagination must provide a coherent continuation token across
concurrent publications.

An app opening a historical branch fetches required ancestry before rendering.
If it is unavailable offline, show that limitation instead of fabricating state.
The exact minimum offline-download set and cache budget need approval.

Confirmed D-44/D-45: explicit View and Reveal operations enter the durable shared
log and synchronize; neither is merely a local browser or best-effort presence
event. View's initiating participant sees faces and the other sees backs by
default; Reveal shows faces to both. Top-N display order must remain the recorded
source order. This is presentation under the existing full-access trust model,
not a new participant data-security boundary. Exact records, offline historical
context, branch association, and temporary-display lifecycle remain Q-28.

## 6.6 Snapshots and replay

Snapshots accelerate loading but are disposable caches. Their key identifies an
action boundary, reducer/schema version, and integrity digest. Replay from root
or a verified snapshot must produce the same logical board.

Immutable action nodes and stored random outcomes are the durable gameplay
record. Schema migrations must preserve old histories and redo results. An
upgrade cannot reinterpret an old literal action using newly inferred semantics.

If local corruption is detected, retain/export drafts, invalidate derived data,
and rebuild from trusted records. Never clear all app storage as an automatic
“repair.”

## 6.7 Access and information

Cooperative access applies **within a game**, not across the service. Only the
two admitted participants can read or mutate the game unless an explicit sharing
feature is later approved. Both have equal tabletop rights, including hidden
zone inspection.

D-89/D-90 retain information-gaining actions as shared history branches after
undo, including covered-face inspection and die/coin results. They are distinct
from cancelable ordinary board effects/ephemeral presence, not a new opponent
access-control layer. Specify durable local capture, reconnect publication/
deduplication, branch/offline availability, and covered-summary disclosure in
Q-31. Already-visible-face holds and every search keystroke are not in the
confirmed scope merely as gestures; D-91 covers any actual resulting exposure of
otherwise-hidden information, including draw/move/face changes. Exact unfinished-
input/exposure boundaries remain open.

Transport authentication, invite authorization, secure credential storage,
request limits, and validation remain necessary. Clients are untrusted inputs;
they cannot introduce malformed trees, mutate another game's IDs, or execute
arbitrary content.

## 6.8 Data retention

Proposed default: retain complete history and archived branches for a retained
game. Cache eviction removes only refetchable local data, never unsynchronized
drafts. In-game branch deletion is archival, not garbage collection.

Actual game deletion, account erasure, backup expiry, storage quotas, image
cache limits, and export/import retention must be separately specified. Any hard
deletion is outside ordinary reversible gameplay and needs a clear confirmation
and recovery policy. There must be no surprise expiry of undo history.
