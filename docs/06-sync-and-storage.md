# 6. Synchronization and storage

Status: proposed protocol semantics. Exact API and schema are not yet specified.

## 6.1 Authority and local-first behavior

Proposed topology: native Android clients plus a persistent shared service. The
service serializes shared game control operations. Each client stores downloaded
game history, a materialized board cache, personal review state, drafts, and a
durable outbound queue.

There is one authoritative active branch per game on the service. Offline
clients can each have different provisional views; the UI must not claim global
agreement until synchronization. This is unavoidable when disconnected edits
are allowed, not a second authoritative branch model.

Game action payloads are immutable once published. Control operations update
branch/head references, archive status, and metadata through a separately
ordered revision stream. Clients materialize state by action ancestry, not by
wall-clock timestamps.

D-90 separates internal retained records from the visible event tree: ordinary
undo removes the action from that tree, whereas information-gaining undo retains
a history branch. Sync must converge on that distinction without treating a
visible-tree removal as irreversible deletion of redo/recovery data. Exact
visibility/control schema, cutoff/group display, stale cursors, and redo/branch
reconciliation remain to be specified. Conflict/interruption preservation is
unchanged; do not apply ordinary-undo pruning to unrelated branches.
D-91 fixes the visible retained path's endpoint at the last information-gaining
action in the undone sequence, excluding trailing ordinary steps. Preserve
internal recovery for the entire sequence and converge on the requested rollback
board state. Exact concurrent/dependent-branch impact still needs a protocol.

## 6.2 Publication and concurrency

Completed game actions automatically enter the outbound queue and begin
synchronizing immediately when online and dependency-ready. There is no Send
step or waiting for group closure. Service acknowledgement establishes the
shared result, not a notification or animation.

D-131 requires user-action/group undo identity to survive publication, offline
storage, retries, and branch recovery even when that action contains multiple
reaction-point events. Retain the distinct effective boundaries needed for review/
interruption; batching must not collapse them into one indivisible event. One
Undo still targets the whole user action, subject to normal dependent-work and
exposure/comment safeguards. This does not add a Send step, priority protocol, or
wait for presentation-group closure. Exact publication/schema mechanics remain
technical design, not a reason to change the behavior.

A publication includes an idempotency key, game ID, intended base action ID,
continuation/branch context, revision preconditions, desired operation, actions,
and group references. Exact revision scopes remain schema/API design work.

Proposed per-game queue ordering: while one action awaits acknowledgement, the
user can make more local actions without a network wait. Dependent operations
refer to their known local predecessor; bind authoritative continuation/revision
fields from its successful result before first transmission. Freeze payload and
idempotency identity once transmission starts. A retry never changes the base
or expected revision under the same key.

Proposed outcomes:

| Situation | Outcome |
| --- | --- |
| Continuation unchanged; base is current active head | Append and advance its head atomically. |
| Deliberate edit from an earlier retained boundary | Create and activate interruption branch atomically. |
| Concurrent game edit invalidates the expected continuation | Apply the action at its intended base on a new conflict branch and activate that branch atomically. |
| Descendant of an acknowledged conflict/interruption branch | Continue on the assigned branch; do not repeatedly reactivate it if another line is now active. |
| Same idempotency key and identical payload retried | Return the original result; never apply twice. |
| Same key with a different payload | Reject as idempotency-key misuse. |
| Stale control command such as historical undo/archive | Re-evaluate its declared impact; return an explicit stale-control result if it would affect additional work. Never guess or discard that work. |
| Invalid object/reference/precondition even at the intended base | Reject whole publication with actionable structural error; branching cannot repair malformed actions. |
| Base not yet synchronized | Resolve/upload the known parent chain first; do not substitute a different base. |
| Temporary transport/server failure | Retain queued operation for retry with the same identity. |

**Confirmed:** game-edit conflicts branch automatically, without a blocking
resolution choice. Proposed activation rule: treat each newly created conflict
branch like an interruption and select it on commit. Service commit order
serializes those selections; device clocks and event delivery order do not.
The previously active line is preserved, never overwritten or automatically
merged. The response identifies both continuations and the reason for the fork.

For example, both participants act at B. A's action X commits first. B's action
Y then commits on `B → Y`, retaining `B → X`; Y's branch is now active. If A
already queued X2 after X, it appends on `B → X → X2` without taking active
selection back from Y. There is no perpetual retry/rebranch/activate cycle.

Forking must validate the incoming action against its original base, not try to
apply its payload to the competing board. A card moved by the other player can
therefore be moved differently on the alternative line without a false error.

Automatic branching preserves/synchronizes a valid pending suffix. It does not
require the user to choose accept, rebase, preserve, or discard. Optional follow-up
actions are inspect, activate a retained line, undo/return, annotate, or create a
new correction. Automatic replay/merging between paths remains out of scope.

An idempotent retry returns the same branch ID and activation record; it cannot
fork or activate again. Receiving control updates never generates a new local
game edit. Missing ancestry, corrupt input, and lost access remain explicit
failures, with the local work retained for recovery.

## 6.3 Why not automatically merge game edits?

Independent-looking edits may matter together to players: two participants may
move the same card or base their next edit on the same named value. For first
release, shared game edits create alternative branches automatically rather than
using a general CRDT merge. Choosing a different retained line later is optional,
not a prerequisite to sync. Personal camera/review settings need not share that
rule.

Metadata and messages may use simpler per-entity revision checks, but editing
them must not cause false game-action conflicts. The exact division of revision
domains is a required API design decision.

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
durable game queue, and never replays stale offline presence as current activity.

## 6.4 Durable outbound queue

- Persist each completed action and its queued operation transactionally before
  reporting it saved locally; then start synchronization automatically.
- Persist incomplete form input separately; it is not a queued game action.
- Retry after reconnect, process restart, and authentication renewal.
- Acknowledge only after a committed server result or a verified idempotent retry.
- Record dependencies between queued operations. An automatically branched parent
  carries its descendants onto the returned branch. A rejected/missing parent
  blocks dependent publications with recoverable state; never silently rebase.
- Cancellation of an unsent operation is local and recoverable. If delivery may
  already have occurred, first establish its outcome, then issue a normal undo
  or archive operation automatically for the recorded undo intent. Do not pretend
  an in-flight request can be unsent or require a second ordinary undo tap.
- Permanent errors leave a recoverable draft with explanation and export path.

Queue state machine: completed action + local commit → queued → sending →
synchronized (appended or automatically branched), or sending → retryable
failure / permanent rejection. Retryable failures return to queued automatically
when eligible. Rejected dependencies block their suffix for recovery. An app
restart from sending must resolve the original operation identity before creating
a replacement. Incomplete input is outside this queue state machine.

D-105 requires no priority state or Pass record to queue, publish, or interrupt
actions. Do not add priority-based publication ordering or legality rejection.
Participants judge timing; preserve normal dependency/structural validation and
automatic conflict branches without interpreting queued work as priority passes.

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
