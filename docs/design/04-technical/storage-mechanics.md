# Storage, replay and bounded recovery mechanics

Status: technical completion (D-284), not application DDL/code. Product rules remain
in the [history contract](../03-contracts/history-and-undo.md). D-278 supersedes
client crash-durable outgoing/exposure recovery. [Protocol v1](protocol-v1.md)
defines IDs, encodings, ordering and transfer messages.

## Relational schema contract

All rows have typed versioned payloads, not Java/Kotlin class serialization. IDs/
foreign keys, scopes, ordering and query predicates are relational columns. JSONB
holds actual action/transition/definition payloads, never a recursive game tree.
No cascade may delete required referenced history. Tables below are logical names;
physical naming is mechanical migration work, not different behavior.

| Table / primary identity | Required structure and indexes |
| --- | --- |
| `account`, `session`, `password_reset` | Canonical email/username unique keys, versioned password verifier, account auth epoch, hashed token lookup, revocation/consumption metadata. No plaintext passwords/reset/session tokens. |
| `contact`, `saved_deck`, `deck_entry` | Account ownership, approved metadata/imported quantities, exact accepted definition digest, deck revision; owner/list indexes. Selected lobby copies are independent, not cascade-linked to saved deck deletion. |
| `request_identity`, `account_request` | Shared unique business-request ID/account/kind/scope/canonical intent binding across HTTP and game ingress; account-side final non-secret response/status and created IDs. Same transaction as account-data effects or initial game+invitation creation. No passwords/session/reset tokens. Independent of sync-window pruning; retain required dedup outcomes, with no initial automatic expiry. |
| `service_writer`, `game` | Singleton writer epoch for transaction fencing; persistent lobby ID, required display name and immutable server UTC creation instant (D-294), two participants, current preparation/play IDs, lifecycle, next ingress ordinal, next executable ordinal, accepted revision and stream-control version. Membership index per account; names are not unique IDs. |
| `invitation`, `preparation`, `play` | Distinct attempt/round/incarnation IDs, retained submitted copies, Ready/starter choices and recorded initialization/root. Unique current lifecycle identity per game. |
| `stream` | Game/account/installation/process-stream ID and fence generation; locked during replace/admit. Other devices have separate rows. No contiguous client-sequence state (D-285). |
| `inbox` | Unique request identity bound to authenticated account/game/lifecycle, canonical semantic bytes/digest, ingress ordinal, stream/generation, pending/final state and result. Unique `(game, ordinal)`; pending-ordinal index. Already-authorized admitted work may finish after session revocation (D-289); no execution-time session-epoch fence is required. |
| `node`, `transition` | Game/play/node IDs, current parent, node version, actor/request, effective order, recorded relevant before/after and invariant-closed forward/inverse effects. Index `(play,parent)` and author/effective order. Root has no parent. Removed-for-Redo nodes are detached, not browsable alternative branches. |
| `effective_record`, `reference` | Exposure/annotation context with board boundary and effective ordinal; immutable viewed identities/revision/content; indexed incoming references and recording-path membership. Exposure-only rows need no fabricated board mutation. |
| `branch`, `control`, `redo_entry` | Genuine branch identity/archive state; current active node and control revision; per-player/per-play ordered Redo entries with recorded insertion anchors. Snapshot activation never reloads an old Redo stack. |
| `definition`, `account_definition`, `game_definition` | Content-addressed accepted snapshot/version, account-private access and deck/game references. An ordinary complete HTTP POST retains the validated submitted definition set atomically; duplicate content reuses IDs, while duplicate names/external IDs never overwrite different contents. No application upload-session/chunk tables (D-289). |
| `current_object`, `current_container`, `current_link` | Materialized accepted state keyed by play/object/container/endpoint; normalized membership/order and uniqueness. D-295 real cards/tokens carry required same-game owner participant metadata, creator/bringer initialized and changed only by explicit owner edit. Include owner in recorded transitions, roots/checkpoints, deltas, canonical semantic digests and complete baselines; live projections derive source owner, frozen effects retain last-live owner. Owner is not a permission/container key. Ordered containers use deterministic dense integer order in transferred state; update local affected ordering, not unrelated tree records. |
| `revision`, `checkpoint` | Revision base/result and typed changed records, scope/request identity; optional rebuildable snapshot keyed by boundary/tree-control revision/engine version and semantic digest. |
| `outbox`, `activity`, `notification_registration` | Atomic committed publication/notification intents, account-scoped monotonically ordered activity hints, per-session installation registrations; unique delivery identities and due/retry indexes. Not gameplay presence/review receipts. |
| `baseline_transfer`, `baseline_chunk` | Frozen export metadata/chunks at R, owner/membership, expiry and pinned suffix range; full coverage manifest. Reads use ordinary current-session authorization. Disk staging, not a long-held transaction or whole-tree heap blob. |

Constraints: acyclic single-parent retained topology, one current pointer per play,
same-play references, unique real containment, valid projected source/fallback and
approved capacity. Service validation plus transaction locks enforce constraints
not expressible as simple foreign keys. Never trust client IDs to cross game scopes.

## Ordered ingress, execution and fencing

Initially one Spring service instance owns an in-process FIFO ingress dispatcher
per persistent game. A decoded/authenticated complete frame enters that dispatcher
before asynchronous database work. Frames from one socket enter in frame order;
simultaneous frames from different sockets are ordered by dispatcher enqueue, not
client clocks. Bounded mailbox pressure pauses reading rather than parallelizing
admission and overtaking older frames. This deliberately selects a single writer
service topology, not an HA or multiple-replica capacity claim.

A socket shared by several games may slow all of that client's games when reads
are paused under overload. This ordinary connection-wide backpressure is acceptable
(D-289); no per-game transport-isolation machinery is required. Other connections
and independent game workers are not deliberately blocked by that socket.

New requests use ordinary current-session authorization. Each admission transaction
locks game then relevant stream rows (same order everywhere), validates fence/
identity and binds immutable intent. Allocate the next ingress ordinal by updating
that locked game row; commit the request-identity binding and inbox row atomically.
Commit before receipt or admitting the next frame.
Malformed/unauthorized/unavailable admission has no receipt/slot. Duplicate identity
returns the bound outcome/slot without consuming a second ordinal. Stream replacement
is another dispatcher task with the same locks; old pending frames after its fence
fail, while earlier committed inbox rows remain executable.

D-285 does not number client admissions: a terminally rejected/unadmitted frame
leaves no sequence hole, and the dispatcher continues with later frames. TCP handles
ordinary in-connection loss/order. Same-process reconnect looks up retained IDs and
retries uncertain originals in local order after fencing, without reconstructing
missing messages or weakening the durability of already-admitted work.

Account HTTP mutations lock their request-identity record and affected account-data
rows in deterministic order, atomically committing effects and original non-secret
outcome. A competing duplicate waits for that transaction, then returns its outcome.
New-game creation and first invitation commit together; initialize that game's
dispatcher domain before serving subsequent commands. Reinvitation names an existing
game and uses its dispatcher/locks, never the creation path. Cancellation makes it
inapplicable or removes its already-created invitation; either way no current game
or pending invitation survives. Retained outcome/control rows are not a browsable
cancelled game or permission to revive it. A complete definition-set HTTP POST is
account-private and can precede any game; attachment, not metadata submission alone,
changes a deck. No chunked/resumable definition upload state is needed (D-289).

A worker claims a candidate game using `FOR UPDATE SKIP LOCKED`, then selects its
lowest pending ordinal under that lock. Lock game before controls/objects/stacks/
references; ingress and stream replacement use game→stream order. The worker does
not skip a slow pending command to process a newer one. Use PostgreSQL READ COMMITTED
with this explicit serialization; no user-visible revision CAS for ordinary edits.
Validate game access, lifecycle and action applicability at execution. Session
revocation prevents newly authorized requests, but already-authorized/admitted work
may finish; do not add account authorization locks spanning gameplay transactions
or mandatory execution-time session/epoch rechecks (D-289). Atomic final
transaction writes all state/controls/outcome/revision/outbox effects. A worker
crash before commit rolls back; after commit the recorded outcome is final. Poll
pending games at startup and every second so lost wake-ups cannot strand the tail.
Publish committed revisions/results in revision order; duplicate outbox delivery is
safe. Admission may continue while an earlier worker transaction completes, under
the same game lock. No database lock survives a network call/email/image fetch.

Hold a database-backed service-writer advisory lock for the initial single-service
deployment. A second writer stays unready rather than creating a competing ingress
order. Read replicas/multi-writer routing/HA are not required. Lost DB connection
means stop admission/execution, reacquire writer ownership and reload durable state
before becoming ready; never rely on stale in-memory ownership.

Acquiring writer ownership also increments a persisted singleton writer epoch.
Each mutating transaction first takes a shared lock on that epoch row and verifies
its service token, before game→stream locks. A replacement acquires the
exclusive epoch-row lock, waiting for old transactions to finish, then increments.
A delayed old service transaction cannot mutate under a stale epoch even if loss
of its advisory-lock connection has not yet reached application callbacks. Shared
epoch locks do not serialize independent games with each other.

## Undo/Redo and reference repair

Select latest-own Undo or next Redo **inside the game transaction**, not from the
client's old candidate. N is initially **1000 effective successor actions**, counting
board mutations and exposure-only records, including the entire retained future
beyond an internal active node. Check for a fork at/after target before replay; any
such fork blocks removal under D-245. A revealing latest-own action cannot be
skipped. N is an eligibility/work bound, not a history size cap or group count.

Construct candidate topology in a transaction-local overlay. Removal reparents the
next surviving successor to the target's surviving parent; reinsertion restores
the recorded node/anchor without an untouched original alternative. Verify target
fidelity for Redo, then every successor's exact relevant `(from,to)` tuple using
the shared processor's recorded-transition replay mode, never fresh random/default/
Clone reads. Relevant fields are those in the primary mutation-domain table, with
all mandatory cleanup/read/reference dependencies. Missing anchor, changed viewed
context, source/order/default, cleanup or effect blocks the edit. Exclude unrelated
location from tap/text comparisons and unrelated unordered-bin members from moves.

Initial additional validation budgets: 250 ms CPU, 100,000 referenced/effect records,
64 MiB decoded temporary payload. A budget failure leaves the entire operation,
stacks and pointer unchanged; never truncate a check. Measure CPU as charged work,
not a client clock guard. Keep deterministic eligibility causes separate from
machine-dependent safety exhaustion. Tests sweep limits and cold caches; these
are starting engineering budgets, not a guarantee on every Pi/device.

On success update topology/node versions/current state/stack and a single shared
revision atomically. Retained descendant transitions remain concrete recorded
effects; invalidate affected descendant materializations lazily by tree-generation
key, not full-subtree snapshot rewrite. Changed reference meaning is blocked by
the tuple test, not repaired by silently attaching a comment to a different event.
Personal removed-node cursor fallback uses D-238's retained parent route. Store
the removed-node→surviving-parent mapping with the edit so offline replicas can
perform that fallback. Retained same-ID boundaries need revision qualification.

The same D-238 ancestor fallback applies after full recovery when old deltas are no
longer available; required retained removal/parent metadata supports it. This
reaffirms existing behavior, not a new recovery service or unresolved owner choice
(D-289). Revision qualification here is for cache/read correctness, not replacement
of stable UUID request references.

Redo stack clearing removes availability, not referenced server history. Initially
retain detached transition payloads/anchors as non-browsable control records for
the retained play; no new trash/explorer path. Dependency-aware collection can
only be added with proof it preserves every retained reference/outcome/exposure.

## Client persistence and crash matrix — D-278

SQLDelight stores account-private accepted records/definitions and accepted cursor
in one transaction, plus best-effort UI progress/drafts. Token ciphertext uses an
Android Keystore key; exclude private DB/token/drafts from Android cloud/adb backup.
Public catalog/art are separate refetchable caches, not restored credentials.
No outgoing-request, predicted-state or unreported-exposure table is required.
App start never treats an old provisional cache as a committed board.

| Failure point | Recovery |
| --- | --- |
| Before local memory capture, before send, or before server admission | Action may be lost. Process restart syncs server, no invented replay/result. |
| After inbox commit, before receipt/result | Server processes once. Surviving process queries/retries original ID; restarted process simply receives its accepted changes. |
| Before/during final server transaction | Rollback and worker resumes original pending command; no half result. |
| After final commit, before update/result delivery | Durable result/revision/outbox survive; revision/head recovery converges. |
| During accepted client revision save | Atomic old or new accepted cursor/state; resume from that base. |
| After a revealing prediction, before its request is accepted | Ordinary request recovery/reconciliation only. D-286 allows extra speculative glimpses; no separate report or frozen provenance. Accepted inspection/reveal records are durable, unadmitted work may be lost on death/Logout. |
| During baseline or catalog staging | Keep prior complete generation, resume same verified transfer or discard staging and restart; never claim partial coverage. |
| Logout/auth reset during callback | Logout discards local queue/private data; account/session-generation fences reject late store/display. Reset requires same-account auth before retained-memory retries, subject to server authorization. |

## Retention, leases and resource boundaries — D-283/D-284

No automatic age expiry of required current-game history, definitions, references,
control data or deduplication/outcomes. Sync patches/activity hints/checkpoints are
separate bounded caches. D-283 itself adds no account-erasure/export/archive UI.
**D-293 separately adds explicit active shared-game deletion:** atomically retain
a deleted lifecycle tombstone and original command outcome while removing both
players' listing/read/write access. This is access/lifecycle removal, not automatic
age expiry or authority to purge dedup records, backups or referenced shared data.
Late baseline installs and callbacks are fenced by the known deleted lifecycle;
on learning deletion, clients invalidate that game's replica/read-only route.
Erasure/legal basis, timing, backup expiry and operator requests are owned by the
separate operations repository. Do not promise legal compliance or indefinite
retention of every finished session. Hard deletion needs a dependency-aware plan:
remove credentials/tokens immediately when access is revoked, assess shared-game
references/other participant rights, and never run a blanket account cascade over
shared history. Application inventory and read/write authorization support that
work; operations cannot secretly reinterpret retained gameplay semantics.

Starting rolling sync window per game: keep the most recent 10,000 revisions;
older revisions may be removed only after 7 days, additionally retaining pinned
suffixes. This is a soft storage target, not permission to drop a needed pinned
range or gameplay history. Advertise the actual resumable-base floor, never infer
it from age. Outcome rows are independent and not expired by this job.

Baseline export materializes at repeatable-read snapshot R to bounded disk chunks,
then releases the DB snapshot. Export creation has a 60-second work budget; failure
is explicit/retryable, no incomplete manifest. Pin the delta suffix from R during
export/catch-up. A lease lasts 30 minutes, renewable during progress to a maximum
2 hours; at most 2 transfers/account. Expiry/resource exhaustion returns explicit
410/retry from a newer baseline and releases pins/chunks. A completed but unfinished
catch-up keeps its lease until caught-up acknowledgement/expiry. Large games must
remain serviceable by streamed export; failure is not authority to omit branches.
Test the budgets against outliers before release, raising capacity or improving
streaming if necessary instead of imposing an unapproved game/history cap.

Checkpoints are generated every 100 accepted revisions or on demand. Digest descriptor
includes `coverageKind`: `currentState` covers scope/boundary/tree-control revision,
required current definitions, materialized board, lifecycle and current controls;
`completeReplica` additionally covers all retained topology/metadata/effective records
and required historic definitions at that revision. Both exclude UI/predictions/
wall-clock delivery fields. Hash large collections as ordered record streams with
protocol domain/version tags. Retained transfer bytes have separate hashes. Never
claim a current-state digest proves every historical record. Compare matching
`currentState` checkpoints on reconnect and every 100 revisions; every 30-second
head probe detects a missing final tail. Mismatch rebuilds derived cache, then full
recovery if mismatch persists; preserve live in-memory request identity and UI where
valid. Never clear all storage or loop-retry a reproducible semantic mismatch.
