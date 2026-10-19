# 6. Synchronization and storage

Status: proposed protocol mechanics supporting confirmed behavior. D-179 replaces
automatic conflict branching with explicit interruption intent and current-head
action-specific optimistic locking. Exact API and schema are not yet specified.

## 6.1 Authority and local-first behavior

Proposed topology: native Android clients plus a persistent shared service. The
service serializes shared game control operations. Each client stores downloaded
game history, a materialized board cache, personal review state, drafts, and a
durable outbound queue.

There is one authoritative active game-state pointer/path per game on the service.
It may point to an internal node with retained descendants after Undo. Offline
clients can each have different provisional views; the UI must not claim global
agreement until synchronization. This is unavoidable when disconnected edits
are allowed, not a second authoritative branch model.

Game action payloads are immutable once published. Control operations update
branch/head references, archive status, and metadata through a separately
ordered revision stream. Clients materialize state by action ancestry, not by
wall-clock timestamps.

D-159/D-160 require an explicit active node pointer independent from retained
continuation tips. Undo moves it one parent back, regardless of actor; retained
nodes are never physically deleted by Undo or replacement play. The pointer move
is a control operation, not another board-action child. After replacement play,
ordinary abandoned continuations hide behind Show hidden branches; whole exposure/
comment continuations remain visible, including ordinary tails. Sync must converge
on the shared tree/control state without confusing presentation filtering with
deletion. D-161 binds Undo to a specific node-to-parent transition and silently
discards it if no longer valid, reconciling client state without notifications.
This applies to concurrent/offline requests too. Exact schema, pre-fork/forward
presentation and non-Undo branch-control outcomes remain open. Retained existing
alternatives remain recoverable; D-179 prohibits new implicit conflict branches.

## 6.2 Publication and concurrency

Completed game actions automatically enter the outbound queue and begin
synchronizing immediately when online and dependency-ready. There is no Send
step or waiting for group closure. Service acknowledgement establishes the
shared result, not a notification or animation.

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
and chosen boundary for the latter, and action-specific from/to conditions rather
than a blanket unchanged-game-head check for ordinary edits. Undo supplies
continuation intent automatically. Proposed additional fields include an
idempotency key, game ID, action data, dependency and group references. Exact
field names, revision scopes and schema remain technical design.

Proposed per-game queue ordering: while one action awaits acknowledgement, the
user can make more local actions without a network wait. Dependent operations
refer to their known local predecessor. Authoritative parent/dependency mapping
must allow valid ordinary actions to append after intervening server mutations,
without substituting a different requested from/to state or inferring interruption
intent. Exact mapping and queue recovery after rejection remain open. Freeze
payload and idempotency identity once transmission starts; retries do not rewrite
intent or lock conditions under the same key.

Proposed outcomes:

| Situation | Outcome |
| --- | --- |
| Ordinary edit; from/to conditions hold at current head | Append there and advance the shared pointer atomically, even if other actions committed meanwhile. |
| Ordinary edit; either from/to condition fails at current head | Reject without changing game state or creating a branch. Retain recoverable local work; detailed feedback/dependent-queue behavior remains open. |
| Deliberate historical continuation, with explicit flag and boundary | Validate at the selected boundary, then create and activate the interruption branch atomically. Mere review/staleness is not intent. |
| Further queued work after a separate explicit interruption | Detailed dependency/continuation behavior remains open; do not resurrect the superseded automatic-conflict protocol. |
| Same idempotency key and identical payload retried | Return the original result; never apply twice. |
| Same key with a different payload | Reject as idempotency-key misuse. |
| Undo's specific node-to-parent transition is still valid | Apply that transition once; retain the original nodes. Do not retarget to another source/parent. |
| Undo transition is no longer valid, including after offline queuing | Silently discard the request and reconcile local state (D-161). No warning/notification/confirmation/retry task; preserve nodes and other queued work, including continuation intent supplied by Undo (D-179). |
| Previously successful Undo request retried | Return its recorded idempotent result; no second move or false stale failure. Reconcile against current control state, not by overwriting newer state with an old success snapshot. |
| Stale non-Undo branch control command | Outcome remains a product decision. D-161 does not approve generic silent discard for branch activation/archive/restore. Preserve nodes and recoverability. |
| Invalid object/reference or structural constraint | Reject the affected publication with actionable structural error; explicit interruption cannot repair malformed actions. |
| Base not yet synchronized | Resolve/upload the known parent chain first; do not substitute a different base. |
| Temporary transport/server failure | Retain queued operation for retry with the same identity. |

**Confirmed D-179:** ordinary actions are attempted against the current head using
action-specific optimistic locks. Validate both from/to conditions and commit the
mutation as one server transaction; a concurrent commit cannot slip between the
check and application. Do not reject solely because the game-head revision changed.

Example: both clients see B. A moves card P from p to an empty q. B moves another
card R from r to an empty s. If both moves' conditions remain valid, record
`B → move P → move R` in server commit order, not sibling conflict branches. If
the second move instead expects P at p, or expects the now-occupied q to be empty,
reject it. Never silently choose a new source or destination.

This destination example applies to an empty-position move, not all move actions.
Approved pile joins, ordered-pile insertion, card edits and occurrence operations
need action-specific condition contracts. Their exact lock granularity, including
which unrelated fields may change, remains open; do not invent whole-card or
whole-game locks as a blanket default.

An explicit interruption instead names the historical boundary and intent to
continue there. Validate the mutation in that selected state and make the new
branch current, retaining the displaced continuation. Ordinary rejected work is
not automatically retried as an interruption. Simultaneous explicit interruptions
and queued work after another interruption still need detailed behavior.

An idempotent retry returns the original result, not a second application or
renewed branch activation. Receiving control updates never generates a new local
game edit. Missing ancestry, corrupt input, and lost access remain explicit
failures, with local work retained for recovery.

## 6.3 Compatible edits versus historical replay

D-179 permits ordinary actions with valid from/to conditions to append at the
current head despite unrelated intervening work. This is sequential validation
and application, not automatic merging of retained alternative futures. Failed
conditions reject the action. Replaying a displaced historical suffix onto an
interruption remains out of scope; merely viewing that suffix never activates it.
Personal camera/review settings remain separate from game-state edits.

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
- Record dependencies between queued operations. A rejected/missing parent must
  not be treated as successful or silently bypassed by its descendants. Retain
  dependent work recoverably; detailed recovery and independent-queue progress
  remain open. Ordinary edits still use current-head conditions, while explicit
  historical continuations retain their selected boundary and intent (D-179).
- Undo of a pending node retains that node/outcome and queues the specific
  node-to-parent control intent; exact unsent-node/control representation remains
  technical design. If delivery may already have occurred, establish its outcome
  and synchronize that intent without requiring another ordinary Undo press.
  If no longer valid, discard silently and reconcile under D-161. Never replace
  the intent with a different Undo/archive target or lose other queued actions.
- Permanent errors leave a recoverable draft with explanation and export path.

Queue state machine: completed action + local commit → queued → sending →
synchronized (appended or explicitly branched), or sending → retryable
failure / permanent rejection. Retryable failures return to queued automatically
when eligible. Rejected dependencies block their suffix for recovery. An app
restart from sending must resolve the original operation identity before creating
a replacement. Incomplete input is outside this queue state machine.

D-105 requires no priority state or Pass record to queue, publish, or interrupt
actions. Do not add priority-based publication ordering or legality rejection.
Participants judge timing; preserve normal dependency/structural validation and
explicit interruption and optimistic locking without interpreting queued work as
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
