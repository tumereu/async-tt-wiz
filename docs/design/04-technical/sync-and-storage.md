# 6. Synchronization and storage

Status: protocol mechanics are proposed unless selected explicitly. D-179 replaces
automatic conflict branching with explicit interruption intent. D-187 normally
accepts ordinary actions against current state, with optimistic guards limited to
movement and Stack additions, not general previous-value comparisons. Exact API
and schema are not yet specified. D-263 selects ordered gameplay WebSocket
transport and HTTPS/JSON for accounts/catalog/reads, using Ktor Client and
kotlinx.serialization on clients; OpenAPI describes HTTP and explicit schemas
describe socket messages. D-275 selects stable request identities, PostgreSQL
durable per-game admission, atomic individual execution and fenced reconnect
recovery. D-278 replaces client crash-durable request/exposure obligations with
server-first restart. [Protocol v1](protocol-v1.md), [storage mechanics](storage-mechanics.md)
and [runtime profile](runtime-and-verification-profile.md) complete the initial
schemas/locking/limits/encoding defaults; older open mechanism labels below refer
to those specifications, not fresh product decisions or implementation permission.

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
graph or server-side reconstruction of client order. D-263 selects WebSocket for
ordered gameplay delivery; D-275 selects durable per-game inbox admission and
obsolete-connection fencing, with exact cutover/resume wire details still to design.
Independent requests that can overtake
each other would not satisfy the ordered-send requirement, and socket ordering
alone does not establish per-game ordering across devices or durable acceptance.

<a id="61-authority-and-local-first-behavior"></a>

## 6.1 Server authority, client replicas and optimistic application

Confirmed authority boundary (D-259): native Android clients plus a persistent
shared service that decides shared game results and serializes control operations.
Storage/protocol mechanics remain design work within D-263's selected transport
and SQLite choices. Each client stores downloaded
game history, a materialized board cache, personal review state, unfinished input,
a small local online send queue and records needed to recover in-flight requests.
This is not an offline gameplay queue (D-182/D-183).

D-259 confirms [the state ownership matrix](../03-contracts/domain-model.md#state-ownership-and-request-boundary--d-259):
semantic game state, history/control and lifecycle are server-authoritative.
"Local" must distinguish personal UI state from an accepted server replica,
provisional requested changes, protocol/recovery records and disposable caches.
Device storage does not create a second authority. Zoom/pan, review position,
selection, modes and open menus are UI-only; board edits, shared activation,
Undo/Redo, communication and logged inspection use server requests, with immediate
local prediction where applicable. Fetching data is not itself shared UI presence.

There is one authoritative active game-state pointer/path per game on the service.
Historical activation may select an internal node with retained descendants. Online
clients may display predictions awaiting acknowledgement; disconnected clients
can review stale downloaded state but cannot make game actions. The UI must not
claim current shared agreement for stale or unacknowledged state.

**D-232 constraint:** Undo/Redo edit the existing mutation tree by removal and
reinsertion; they do not publish revised branches beside unchanged original paths.
The earlier immutable-payload/ancestry proposal must support this behavioral tree
edit, not forbid it or expose an unwanted original alternative. Exact record
mutability, revision/log encoding, IDs, cached states and reference/cursor encoding
remain technical work. D-238 parent fallback, D-235 shared player stacks and the
reference lifetime matrix constrain those mechanics. Physical deletion or
permanent retention after Redo-queue clearing is not selected. Clients materialize
the accepted tree revision, not an immutable-parent assumption or timestamps alone.

**D-224 behavioral revision:** selective timeline removal/reinsertion requires
every later mutation's relevant-state `(from, to)` tuple to match its original
transition exactly, with a hard suffix-work cap N. This is stricter than D-187
ordinary live acceptance. Per-mutation state, N/counting, atomic validation versus
concurrent work, node versioning/reference retention and pointer/Redo-queue effects
remain open technical/behavior work, not an approved storage rewrite algorithm.
The earlier pointer control model below is superseded for Undo/Redo. In particular,
its original-node-retention and automatic post-Undo-fork assumptions do not apply
to D-232. Existing genuine branches and D-223 filtering remain distinct.

The shared active pointer is independent from retained continuation tips and
personal review. D-236 Undo requests target the latest own mutation at processing;
each distinct request is another press, while retries recover one original result.
Redo reinserts last-undone-first from the per-player/per-incarnation stack shared
across that account's devices (D-235), not a remembered active head. D-245 checks
all retained successors, including beyond an activated internal node; personal
view heads never truncate validation. A fork anywhere at/after the target blocks
removal; no partial suffix check. Retained genuine alternatives remain recoverable
and D-222/D-223 filtering deletes nothing. D-179 prohibits implicit conflict forks.

### Shared processing and random-input agreement — D-261

Client prediction and actual server execution use the same Kotlin game-state
objects and command-processing implementation, not just common DTOs/replay or
cross-language parity tests. The [client architecture](client-architecture.md#shared-processing-and-locally-generated-random-inputs--d-261)
owns the shared-code/adaptor boundary. Current authoritative inputs can differ
from predicted state; D-187 acceptance and D-185 reconciliation still apply.

Die/coin samples may be generated locally for a connected new invocation. Bind
the sample to the original request in memory before display; D-278 removes the
crash-durable capture requirement. After checking access/lifecycle/applicability and
sample validity, the server uses the same sample, records the concrete outcome
and publishes the accepted event. It does not independently reroll to demonstrate
authority. Retransmission and local recomputation preserve the input; result lookup,
replay and review preserve the accepted result. Unknown outcome/rejection is not
an instruction to create another invocation or discard an actual exposure record.

### Selected Shuffle and setup randomness — D-274

**Confirmed:** for each new connected in-game Shuffle, the client generates a fresh
seed and retains it in memory with the original request before prediction/transmission. Client
prediction and server execution use the same explicitly versioned **Fisher–Yates
shuffle with unbiased sampling**, starting from a stable canonical ordering of the
execution state's card-instance identities. Do not rely on ambient/default Kotlin
randomness or a library shuffle whose algorithm can change without a version.
The exact random generator, entropy acquisition, canonical-ID encoding/comparator,
seed format, bounded-integer sampling and algorithm-version registry remain
technical specification work; use a suitable secure entropy source for fresh inputs.

The server validates normal access/lifecycle/applicability and input/algorithm
compatibility, uses the supplied seed rather than independently rerolling, and
records the concrete accepted Library order. Equal input/state produces identical
prediction/server order, so ordinary uncontended mulligans complete intended draws
without a routine acknowledgement barrier. The seed does not authorize submitting
the client's old membership as authoritative, choosing arbitrary outcome arrays or
making offline Shuffle invocations.

If membership changed, use the actual current members, never the client's old
snapshot or a stale-whole-Library rejection guard. Different accepted order may
invalidate already-bound Draw requests; preserve those original identities and
normal individual partial outcomes without substitution or automatic extra draws.
Retry/local prediction recomputation preserves the original seed/identity. Accepted
result recovery, history/review/Redo and downloaded replay reuse the concrete
recorded order, not a new shuffle against later membership. A genuinely new Shuffle
gets fresh input even after Undo; equal outcomes by chance remain valid. Preserve
all existing exposure/recovery obligations and per-constituent outcomes.

**Game start:** the server acquires independent fresh inputs for both initial
Library shuffles and runs the same shared versioned processing. If the lobby option
is **Random**, resolve the starter once **at the actual game-start transition**,
choosing fairly between the two participants. Record the chosen participant and
both concrete shuffle orders with initialization. Both-player Ready still gates
start, but randomness does not require an additional client connection, confirmation
or request after readiness has been accepted. Retry/reconnect never initializes
again or resamples the same start. A new play incarnation has fresh initial inputs;
if its selected starter option is Random, it gets its own fresh resolution. Exact
lobby-option retention/defaults across games remain separate from resolution timing.

This is a cooperative-tabletop design, not commit–reveal, a cryptographic fairness
proof or competitive anti-cheat. D-273 compatibility must account for random-input/
algorithm versions: never silently change a pending seed's interpretation across
an app upgrade. No generator/schema implementation is authorized by this selection.

## 6.2 Publication and concurrency

Game actions may be made only with a connection in initial delivery. They begin
publication immediately in client action order; no offline gameplay queue is created.
There is no Send step or waiting for group closure. Only a final committed outcome
establishes the request's result. D-275's durable receipt means admitted/pending,
not accepted gameplay or a caught-up replica; neither animation nor push is proof.

D-180 confirms immediate optimistic local state updates for latency hiding.
D-185/D-277 animate the net difference to the reconciled target after rejection;
an isolated rejected mutation looks like an Undo-like reversal, not queued reverse
operations over newer valid work. This local
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
actual outcome. D-277 explicitly separates withdrawn prediction from request
outcome: keep withdrawn effects out of the speculative overlay; later accepted
changes arrive through the same committed-update path as remote/other-device work,
with original author/identity, not revived prediction sequences. Insert accepted
remote state before replaying remaining active predictions, preserving independent
valid work and cascading only actual guard failures.

D-277 live mode immediately presents coherent incoming updates, rebased under
pending predictions. Foreground with unpresented updates enters D-177 grouped
playback; both modes keep receiving accepted state. Catch-up or deliberate gameplay
continuation returns to live, while pause/scrub remains personal playback. Animations
use previous-display → next-target net differences per live presentation update or
per playback group, not per-mutation waypoints. Exact storage/correlation/scheduling
remain technical design within [prediction/playback rules](prediction-and-playback.md).

D-176 requires one node per individual mutation and immediate publication of
each node, including ordered operations from bulk/combined controls. Transport
batching must not collapse nodes or wait for invocation/group/animation completion.
D-231 makes the latest still-applied own mutation the Undo target; D-232 edits
the existing tree. Retain invocation identity as context,
not as an atomic multi-mutation payload or command to skip targets. Reinsertion
Redo and D-177 playback reuse recorded outcomes. Playback rollback/scrubbing are
personal traversal, not new shared Undo or publication. Both shared and local
displayed state reference mutation nodes, never groups. Exact protocol/schema
mechanics must support this behavior without priority/legality inference.

A publication must distinguish ordinary current-head edits from an explicitly
requested historical continuation. D-179 requires an explicit interruption flag
and chosen boundary for the latter. D-187 applies ordinary operations to current
state unless impossible, retaining limited movement/Stack guards rather than
previous-field-value or whole-game-head checks. D-232 Undo/Redo do not supply
historical-continuation intent. D-275 selects request identity, authenticated
account/actor and game/lifecycle context, action data/captured references/limited
guards, random input and explicit historical intent where applicable; exact field
names and encoding remain technical design. D-254 separates requests from server-created events:
group membership is deterministic derived output, not a client/menu assignment or
group guard. A historical group reference does not assign a new event's group.
D-184 uses ordered
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
WebSocket admission/resume details, new-object/reference handling, queue size/
saturation and animation scheduling remain technical work within D-275's selected
durable-inbox/fencing/recovery mechanism; they must support the online pipeline, not
restore stop-and-wait as a blanket policy. Preserve each operation's identity when
resolving delivery uncertainty; do not resubmit as a different action/random draw.

Fence every gameplay/control/annotation/exposure request and result reference to
its originating play incarnation, distinct from persistent lobby reuse. Recovery
may return an ended incarnation's original committed result but never apply it
or its snapshot to fresh play. Reject an uncommitted old command without mutating
the new incarnation, including object-free End turn. Preserve protected old
   server outcomes across restart and surviving in-memory recovery on reauthentication; exact fencing/ID generation is
technical. Invitation actions similarly retain their original attempt/outcome:
a retry of an old Accept/Decline must not become a response to a later reinvitation.
This does not select a new duplicate-invitation policy.

Fence lobby intent to its originating **preparation round** too. Ready/withdrawal,
deck/mainboard/sideboard/starter edits and cancellation from a previous round must
not first apply after start → accepted ending → return to the same lobby. Recover
committed old outcomes without reapplication; reject uncommitted expired-round
intent without affecting the fresh preparation. Same persistent lobby/deck IDs
and object-free Ready do not bypass this fence. Generation/ID representation is
technical; unchanged-whole-lobby revisions are not required for compatible edits.

Before all reads/guards, resolve the [shared operation context](../03-contracts/operation-composition.md).
Use one execution state: authoritative current head for ordinary actions, chosen
retained boundary/revision for explicit historical continuation. Stack comparison,
movement source/destination and live Reset/Clone/Shuffle reads all use that state.
Keep captured identities/explicit choices, provisional outputs and accepted
outcomes separate. Do not transmit an obsolete whole-Library permutation as an
authoritative live Shuffle command. Retries use the accepted concrete result.

Proposed outcomes:

| Situation | Outcome |
| --- | --- |
| Ordinary edit remains possible at current state | Apply in arrival order and append the actual mutation, preserving unrelated fields. Do not compare old orientation/text/Set values or reject for an unrelated revision change (D-187). |
| One of several concurrently sent ordinary actions fails | Reject only that action; another action whose own checks pass may still apply. Do not cancel/roll back the whole send queue or require a sequence-level retry (D-183). |
| Requested operation genuinely impossible, or exceptional movement/Stack guard fails | Reject only that request without shared mutation or branch. Animate the actual correction under D-180/D-185 while preserving accepted work and valid predictions. Changed old values alone are not a rejection condition for ordinary edits (D-187). |
| Stack-entry request's supplied Stack state differs from Stack in its selected execution state | Reject the addition (real-card move or new projection); compare-and-apply atomically in arrival order. An explicit historical request compares at that boundary, not the displaced live head. No separate opponent/seen detection, priority inference or whole-game-head check (D-187). |
| Deliberate historical continuation, with explicit flag and boundary | Validate at the selected boundary, then create and activate the interruption branch atomically. Mere review/staleness is not intent. |
| Successful deliberate activation/interruption/return changes selected shared history | Clear both players' Redo queues atomically with the switch (D-258). Ordinary events and Undo/Redo retain normal semantics. Same-current-target no-op, failed switch or metadata-only archival does not trigger this reset. Retry recovers the original result without reactivation or clearing newer entries. |
| Further online-pending work after a separate explicit interruption | Ordinary requests use current state with original operands/limited guards; explicit historical requests validate their exact retained boundary. Missing predicted prerequisite rejects that reference, not independent later work. No implicit conflict fork, dependency waiting or substituted intent (protocol v1). |
| Same request identity and semantic payload retried | Pending admission gets no second position; completed command returns its original outcome, including rejection/no-op. Never apply twice (D-275). |
| Same identity with different intent/scope | Reject misuse without overwriting the original pending/final record. Transport credentials/connection replacement are not changed action intent (D-275). |
| D-232 Undo/Redo tree edit is eligible at the authoritative revision | Apply removal/reinsertion once without creating a branch, subject to D-224 replay/N and D-227–D-231 target/stack rules. Exact atomic revision guard and reference reconciliation remain open. |
| Undo/Redo tree changes while validation/request is in progress | Serialize target selection, complete suffix validation and commit under one game transaction; each press uses then-current server target. No unchecked suffix, captured-parent CAS or partially validated edit (storage mechanics). |
| Previously successful Undo request retried | Return its recorded idempotent result; no second move or false stale failure. Reconcile against current control state, not by overwriting newer state with an old success snapshot. |
| Stale non-Undo branch control command | Resolve the exact retained target at execution; missing target rejects without substitution, otherwise perform the explicit control meaning. Same-current selection/no metadata change is no-op. Apply D-258 only on an actual shared switch; no blanket stale-head rejection or silent nearest-parent activation. |
| Invalid object/reference or structural constraint | Reject the affected publication with actionable structural error; explicit interruption cannot repair malformed actions. |
| Explicit historical boundary or new-object reference not yet authoritative | Check the original reference against state at arrival-order processing; do not substitute another base or wait on a sequencing dependency graph (D-184). A failed prerequisite does not cancel later actions whose own checks pass. Exact provisional-reference mapping remains protocol design, not an acknowledgement-before-send policy. |
| Connection drops during an already-started online request | Preserve identity/context and automatically look up/retransmit the original as needed under D-275, without duplicate execution, new randomness or lifecycle retargeting. Receipt/timeout is not an accepted/rejected outcome. Exact wire/result categories remain technical design; no offline action creation (D-182). |

**Confirmed D-187:** ordinary actions are attempted against current state and
normally succeed unless genuinely impossible. Validate/apply each operation in
one server transaction and preserve unrelated current fields. Previous-value
comparisons are not default validation. Card moves retain source/destination
guards; Stack-entry requests carry the client's current Stack state, compared
against authoritative Stack state in the selected execution context at processing. A mismatch rejects; a match
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
are ordinary nodes on the current path; no special comment-only branch mechanism
is needed. D-232 Undo/Redo do not preserve an untouched original path for them;
affected references/removal behavior remains open. Exact comment/
group association, revisions, edit/delete, unsent chat delivery, and idempotent
publication remain open. Do not apply a blanket message-undo policy to both.

Pings are generic communication records, not rule-target declarations. Recording
and referenced contexts remain separate under the annotation contract, including
effective revision, path and play incarnation; encoding cannot reassociate them.
Live activity/cursor presence is excluded from R1 (D-241), not a required channel.

## 6.4 Online request durability

D-182 supersedes the durable offline gameplay queue and its replay/dependency
state machine. D-183 separately requires a small online send queue with concurrent
transmission and per-action outcomes. Do not conflate the two or remove the online
queue to avoid supporting offline play.

Keep recoverable unfinished input separate from accepted game history. Retain
context for an online request in memory through network loss; D-278 restart drops
that context and syncs the server. Do not report server acceptance before a
verified final recorded result. A durable admission receipt is not gameplay
acceptance (D-275). Preserve idempotency, the requested operation and any limited guards,
explicit interruption intent and stored random outcomes when resolving that
request. Missing acknowledgement alone does not prove rejection or justify an
Undo-like rollback as a known server rejection.

### Selected durable admission and outcome recovery — D-275

Use a PostgreSQL-backed durable per-game command inbox with server-assigned admission
order, atomic individual execution/results/relevant revisions/outbox, stable request
payload identities and obsolete-connection fencing scoped to the replaced stream.
Keep receipt, final outcome and accepted-replica catch-up separate. Duplicate pending
requests keep their original slot; completed duplicates recover original outcomes.
Timeouts never prove rejection, and neither inbox restart nor connection replacement
discards already-admitted work. Old-incarnation/preparation/invitation intent never
mutates a later lifecycle. Other same-account devices remain concurrently usable.
See [request ordering/recovery](request-ordering-and-recovery.md) for selected
mechanics, derived race/retry constraints and remaining exact schemas/limits.

Exact connection readiness/resume wire states, retry/backoff, admission/worker
locking, queue sizing/saturation and animation scheduling remain technical design.
D-185 fixes
difference-only corrections with accepted work and valid predictions preserved.
D-183 rules out a blanket wait-for-acknowledgement interaction gate; D-182 still
excludes extended offline gameplay/replay. Do not interpret ordinary network latency as permission
to freeze otherwise available local controls.

D-278 replaces protected crash/Logout-durable exposure capture with best-effort
in-memory reporting. Capture actual shown content/context online and submit promptly,
retry while memory survives, never publish it as another account. Process death/
Logout may lose unreported exposure; server-retained records decide Undo barriers.
Server-admitted exposures/results remain durable and cannot reopen ended play.
No offline event creation or inferred post-crash exposure record is authorized.

Exposure-only records have the control/visibility participation defined in
[history](../03-contracts/history-and-undo.md#exposure-participation-in-controls-and-visibility),
even if encoded outside board mutation nodes. Include their effective ordering,
latest-own revealing barrier, player-specific Redo clearing and reference checks;
do not let storage layout silently exempt them. Own-area hold is unlogged (D-252).
Effective record encoding/coalescing and per-boundary revision cursors remain
technical choices constrained by those outcomes.

D-105 requires no priority state or Pass record to queue, publish, or interrupt
actions. Do not add priority-based publication ordering or legality rejection.
Participants judge timing; preserve actual structural/applicability validation,
explicit interruption and D-187's limited guards without interpreting queued work as
priority passes.

## 6.5 Incremental synchronization

Clients request control changes since a known service revision plus missing
action nodes and referenced definitions. Apply an update batch and its cursor
transactionally so interruption cannot leave a partially applied local tree.
The cursor describes the accepted replica revision, not the camera/review cursor,
active gameplay node alone or speculative local head (D-259). A control/tree/stack
update can change accepted shared state while the selected gameplay node ID stays
the same. The D-258 switch and both-stack reset become visible as one coherent
authoritative update; recovery must not reapply an old reset to newer stacks.

Push notifications remain hints to fetch changes. D-263's selected WebSocket can
carry committed revision updates, but transient delivery is not the durable record:
application-level resume/retry can duplicate updates, and hints may be delayed,
reordered or absent. Reconnecting and ordinary refresh must recover committed
records/outcomes without depending on a notification or original socket session.
Exact update framing and catch-up/pagination must preserve a coherent revision
continuation across concurrent publications.

D-269 makes FCM optional; background notification polling is an acceptable fallback,
not another source of game authority. Neither path's availability/timing gates
foreground synchronization or recovery. Poll lightweight authorized activity data,
not the whole game tree; see [background-work design](notifications-and-background-work.md).

D-276 targets a complete retained current-game tree at initial download/full
recovery, not lazy historical ancestry by default. Once complete, branch review
uses downloaded records; any interrupted/incomplete data must be identified rather
than fabricated or treated as a deleted branch. Exact storage/resource budgets
remain technical design within this complete-download requirement.

Confirmed D-44/D-45: explicit View and Reveal operations enter the durable shared
log and synchronize; neither is merely a local browser or best-effort presence
event. View's initiating participant sees faces and the other sees backs by
default; Reveal shows faces to both. Top-N display order must remain the recorded
source order. This is presentation under the existing full-access trust model,
not a new participant data-security boundary. Exact records, offline historical
context, branch association, and temporary-display lifecycle remain Q-28.

### Bounded revision window and baseline recovery — D-265

**Accepted requirement:** when the revisions needed by a client are unavailable,
perform a fresh baseline/full sync. D-266 confirms the bounded revision-retention
direction on the basis that checkpoints are rebuildable caches; exact count/age/
byte budgets and protocol fields below remain design work. This window is distinct
from retained gameplay history and request/outcome recovery data, as defined in
[storage architecture](storage-architecture.md).

### Complete current-game transfer — D-276

**Selected scope:** initial download and fresh-baseline/full recovery include the
entire retained current-game tree, with all retained branches and required
definitions, alongside current materialized board/control/Undo/Redo/lifecycle
state. This supersedes the earlier minimal-baseline-plus-lazy-history proposal;
the no-routine-full-tree requirement still applies to incremental play/reconnect.
The [history contract](../03-contracts/history-and-undo.md#complete-current-game-downloads--d-276)
owns exact observable scope, including hidden alternatives and retained futures.

Transfer compact topology/mutation/group/reference data, root/initial state and
required per-game definitions/control payloads, not artwork, a full public catalog,
or redundant full-board snapshots for every node. Shared ancestors/definitions
can be encoded once. Initial full transfer is allowed to scale with complete tree
size; routine deltas scale with actual changes, not that same full size.

Use compression and bounded chunks/pages as internal transfer mechanisms; fetch
all automatically rather than leave offshoots unfetched until opened. Stream/stage
to local storage without requiring the whole decoded tree or a recursive JSON graph
in memory. A completed baseline has complete tree coverage at its declared revision;
partial staging is not a completed full sync. Resume or restart interrupted transfers
coherently while preserving the previously accepted replica and independent recovery
records. Exact format, compression, chunk limits, staging, transfer identity and
lease/pinning mechanics are completed by protocol v1/storage mechanics; this is
not an implemented wire schema.

The expected long main line/short branches motivate this default, not a guaranteed
`<2x` bound or permission to cap/drop a larger branch. Benchmark long paths, sparse
offshoots and heavily branched outliers. Complete download does not mean a snapshot
per mutation, app-wide unlimited storage, an archive of ended games or offline play.

Protocol completion within D-265/D-266/D-276 (exact fields/leases remain proposed):

1. Advertise `currentRevision` and `minimumResumeRevision` for the scope. The latter
   means the oldest **client base revision** from which the full delta suffix is
   available, avoiding ambiguity about the first retained delta's base. Revisions
   do not restart or reuse identifiers when old delta records are removed.
2. For the same scope, a client between the floor and current revision receives
   the needed suffix. If its base is too old or another required gap cannot be
   served, return an explicit baseline-required result rather than silently
   skipping deltas or pretending it is current.
3. Obtain a coherent baseline at revision R: current accepted materialized state,
   active pointer, control/Undo/Redo/lifecycle state, retained root/initial data,
   required definitions and **all retained tree/branch/mutation data**, including
   dependencies needed for review/replay and retained control/reference obligations.
   D-276 full recovery targets a complete local current-game tree. Revision-pinned
   paging/chunking is transport, not a default lazy-history product boundary.
4. Stage and verify the baseline, then atomically install its accepted state/cursor.
   Preserve original request/random/exposure records and account fences. Resolve
   their actual outcomes independently, re-evaluate remaining predictions and use
   D-185 difference-only corrections, not a whole-app wipe or blind queue replay.
   Preserve applicable UI and D-238 cursor fallback. Complete tree data enables
   required path playback; loading a current-state checkpoint does not skip it or
   replace the history transfer. New updates beyond R are caught up separately.
5. Catch up from R to the newer head. A paged baseline/catch-up token must either
   protect a coherent transfer for a bounded lifetime or explicitly expire/restart
   from a newer baseline if pruning invalidates it. Never combine incompatible
   pages or report synced after losing the needed suffix mid-transfer. Pinning,
   temporary retention allowance and resource limits remain protocol design.

Pruning revision patches must not delete retained tree/mutation/branch records,
Redo payloads, recorded randomness/exposures/references or idempotency outcomes.
An old committed request still recovers its original result; absence from the
rolling revision window is not evidence that it never ran. Outcome/deduplication
retention needs an independent safe policy; never execute an uncertain old request
as fresh merely because its result was expired. No limit on gameplay history or
finished-game archive/retention promise is selected here.

### Revision recovery and checkpoint verification — D-264

**Selected:** revision-based recovery provides eventual convergence across
connection failures; checkpoint/snapshot state digests provide an independent
integrity check. Do not require checksums on every gameplay node, a Merkle tree or
a chained event checksum for the initial protocol. The older chain option below
is not the selected baseline.

- Each update identifies its base/result revisions. Apply only to the matching
  accepted base; recover gaps, ignore already-applied duplicates and atomically
  save changes with the accepted cursor. Never include pending predictions/UI in
  that cursor or treat an acknowledgement as a second execution.
- Keep committed revisions available for catch-up or provide a coherent checkpoint
  replacement. Reconnect automatically recovers missing data and request outcomes.
  Periodically compare the durable server head even when no update arrives: a
  dropped final notification/update must not leave an undetected stale replica.
- Under eventual connectivity, valid access, successful recovery and a correct
  implementation, these rules converge the accepted replica. WebSocket/TCP already
  preserves ordered reliable delivery within a functioning connection; application
  recovery handles interruptions/crashes/session changes. Digests are not the
  mechanism that retries missing messages or establishes processing order.
- Give snapshot payloads integrity digests and compare canonical materialized-state
  digests at selected checkpoints. Identify scope, game/incarnation, boundary,
  accepted tree/control revision and schema/reducer/encoding version explicitly.
  Compare the same covered state on both sides, excluding predictions and personal
  presentation. A digest of downloaded scope is not proof of unfetched history.
- A correct saved revision can coexist with a corrupt cache or incorrect reducer
  output; a state digest can expose that mismatch. A log-prefix hash alone cannot.
  Invalidate affected derived data and rebuild/refetch from trusted records while
  preserving surviving in-memory requests/exposure reports and applicable personal state.
  Do not clear all app storage, invent a rejection or resample random inputs.
- "Synced" means caught up to the last verified server head for the covered scope,
  not permanently current: another player can publish immediately afterward.
  This is a protocol meaning, not a finalized UI label or a new gameplay gate.
  Hashes also cannot detect a semantic bug shared by client and server.

Canonical encoding/hash algorithm, checkpoint/check frequency, coverage and repair
mechanics remain technical design. A digest check does not itself require another
full-tree round-trip: D-276 initial/full recovery downloads the complete tree, while
normal revisions/checks remain incremental. Typed tree patches and derived-subtree
cache invalidation are technical proposals, not selected exact wire schemas or
permission to discard offshoot history and fetch it lazily by default. A current-
board digest is still not proof of every downloaded historical record.

### Proposed revision checksums and correction points — D-259

The owner suggests blockchain-style checksums in events as a synchronization
mechanism. **D-264 does not select this chain for the baseline protocol.** The
following remains an alternative technical proposal, not a selected hash algorithm,
wire/schema format, consensus protocol or permission to freeze gameplay ancestry.
Do not require it to satisfy the selected revision/checkpoint design above or
treat its absence as an unresolved owner decision.

- Keep an ordered authoritative revision/publication stream separate from the
  editable gameplay mutation tree. A record may describe a gameplay event, tree
  edit, shared activation/archival, stack change, exposure or communication update;
  every shared domain claimed current must be covered by its revision/checksum
  scheme. Scope identities distinguish game incarnations, lobby rounds and account
  data; one game's checksum is not a service-global state or a UI fingerprint.
- A proposed stream record carries its authoritative revision, previous accepted
  checksum and a checksum derived from the previous checksum plus a canonical
  representation of this accepted record, including its scope and concrete result.
  Canonical encoding, hash choice, schema versioning and domain partitioning remain
  technical design. Retries recover the existing record/result, not another link.
- Compare the server update's base against the client's **accepted replica**.
  Personal zoom/pan/menu/selection state and pending predictions are not part of
  that checksum. A matching verified latest revision/checksum establishes the
  agreed prefix for the covered domain; a mismatch or missing predecessor means
  the client cannot apply that delta as though its base were already correct.
  Prefix identity alone does not prove that a client reducer or materialized board
  is correct: separately verify snapshot/state integrity and deterministic replay.
  A corrupt board cache cannot be trusted merely because its stored event hash
  matches the server's history hash.
- A latest hash alone cannot locate the divergence distance. Retain/index agreed
  revision/checksum checkpoints and retrieve intervening records, or a verified
  snapshot plus suffix, to find the most recent matching base and repair from it.
  Verify the recovered prefix/result and convergence under gaps, duplicate delivery,
  out-of-order hints, corrupted caches and server restart. Exact lookup/pagination/
  checkpoint and snapshot protocol remains technical work, not a new UI workflow.
- Undo/Redo can reparent/remove/reinsert logical gameplay nodes. Represent that as
  a new authoritative revision with deterministic effects; do not mutate an already
  published hash-chain link in place or treat the chain's previous record as a
  gameplay parent. An append-only synchronization log must not expose an unwanted
  original gameplay branch or prohibit D-232 tree editing. Selecting the same
  gameplay node ID does not imply its materialized state/tree/control revision is
  unchanged; checksums must detect those changes too.
- After repairing the accepted replica, re-evaluate outstanding predictions and
  animate only the actual correction under D-185. Preserve request identities,
  unfinished input and UI review/camera context subject to their existing rules.
  A checksum mismatch is not proof that an unknown-outcome request was rejected,
  nor authorization to resubmit it as fresh play or duplicate its random result.
- A checksum/base revision used for synchronization is **not a new whole-game
  optimistic guard**. Ordinary requests still follow D-187; a changed head/checksum
  alone does not reject them. No routine acknowledgement barrier is introduced.
  This is server-authoritative consistency checking, not mining, distributed
  consensus, a cryptocurrency feature or a competitive anti-cheat guarantee.

## 6.6 Snapshots and replay

Snapshots accelerate loading but are disposable caches. Their key identifies an
action boundary **and its accepted tree/control revision**, reducer/schema version,
and integrity digest. Reusing a surviving node ID after a tree edit must not reuse
an obsolete snapshot. Replay from root
or a verified snapshot must produce the same logical board.

D-266 explicitly confirms that these checkpoints are a cache/optimization, not a
replacement durable history. They can be evicted and regenerated from retained
initial/root state, essential definitions and recorded tree/mutation/control data.
Missing checkpoints affect reconstruction cost, not the promised reviewable
history or correctness. A fresh baseline can be generated from that durable basis;
bounded sync-delta retention must not make its only reconstruction source vanish.

Versioned accepted mutation-tree records and stored outcomes are the durable
gameplay record. An immutable physical audit/event log may encode tree revisions,
but logical ancestry is edited by Undo/Redo; immutable parents are not a contract.
Schema migrations must preserve accepted histories and recorded redo results. An
upgrade cannot reinterpret an old literal action using newly inferred semantics.

If local corruption is detected, retain/export drafts, invalidate derived data,
and rebuild from trusted records. Never clear all app storage as an automatic
“repair.”

## 6.7 Access and information

Cooperative access applies **within a game**, not across the service. Only the
two admitted participants can read or mutate the game unless an explicit sharing
feature is later approved. Both have equal tabletop rights, including hidden
zone inspection.

D-89/D-91 require reporting actual exposure, including rejected online predictions;
D-224 excludes revealing selective-Undo targets. Follow the
[exposure lifecycle](../03-contracts/history-and-undo.md#exposure-lifecycle-and-offline-inspection--d-246):
capture in memory when displaying new hidden content, keep viewed versus recording
contexts and request identity, and retry surviving records on same-process reconnect.
D-278 allows loss before server admission across process death/Logout, superseding
the earlier pre-display durable-capture rule. D-246 permits no offline event creation/deferred inspection
queue. Downloaded recorded visible content remains readable; new event-producing
inspection needs a connection. D-252 permits unlogged own-area holds to read
downloaded real faces; opponent-area holds conceal hidden information. Deduplicate
retries, not distinct logged exposures. No
automatic gameplay branch, board marker or covered-identity summary is required.

Transport authentication, invite authorization, secure credential storage,
request limits, and validation remain necessary. Clients are untrusted inputs;
they cannot introduce malformed trees, mutate another game's IDs, or execute
arbitrary content.

## 6.8 Data retention

D-265/D-266 separate the selected rolling synchronization-delta direction from
gameplay history, checkpoints and idempotency/exposure recovery. A client below the served
revision floor obtains a fresh baseline; that is not authority to expire retained
current-game history or delete matching request outcomes. See
[baseline recovery](#bounded-revision-window-and-baseline-recovery--d-265).

Proposed default: retain complete history and archived branches for a retained
game. Cache eviction removes only refetchable local data, never unsynchronized
drafts. In-game branch deletion is archival, not garbage collection.

D-283 retains required current-game records and dedup outcomes without automatic
age expiry; production erasure/backup obligations are explicit operator work in
the separate operations repository, not an R1 account-deletion UI. Storage mechanics
defines safe dependency boundaries and rolling-cache retention; runtime profile
defines cache/resource limits. There must be no surprise expiry of Undo history.
