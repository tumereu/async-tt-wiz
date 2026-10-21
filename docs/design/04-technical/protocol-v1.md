# Protocol v1: requests, results and replica transfer

Status: technical completion under the owner's instruction to finish the remaining
specifications (D-284), implementing D-183–D-187/D-259–D-278. This is a documentation
contract, not application code or a passed delivery gate. Product contracts win over
encoding examples. [Request ordering](request-ordering-and-recovery.md) owns intent;
[storage mechanics](storage-mechanics.md) owns transactions and retention parameters.

## Types and canonical representation

- UTF-8 JSON, `application/json`; socket messages carry a `type` discriminator.
  No polymorphic class names, executable data, NaN/Infinity or duplicate object keys.
- IDs are lowercase hyphenated UUIDs. New request/installation/process IDs and
  independently assigned server identities use cryptographically random UUIDv4;
  command-derived output identities use the deterministic UUIDv5 rule below. Compare IDs by
  their unsigned 16-byte representation, never locale/string-library ordering.
- Revisions and ingress ordinals are nonnegative signed
  64-bit values encoded as decimal **strings**, starting at `"0"`. No floating-point
  clocks/counters for causality. Timestamps are UTC RFC 3339 explanatory metadata.
- `scope` is `{gameId, playId?, preparationId?, invitationId?}`. Required lifecycle
  IDs depend on operation; missing or wrong IDs reject, never default to fresh play.
  Account endpoints use authenticated account scope instead of a game ID.
- Requests refer to nodes by stable UUID within the original play scope; root has
  its own ID. The locally known node ID is retained by the server on acceptance,
  with no remapping or acknowledgement wait (D-289).
- An accepted read/cache `boundary` is
  `{playId, nodeId, treeRevision, effectiveOrdinal}`. The ordinal distinguishes
  exposure-only records at the same board node; revision qualification prevents
  reusing an obsolete materialization after a tree edit. These are accepted-state
  metadata, not mandatory guessed future revision/ordinal operands for a request
  referencing a predicted node. Historical continuation resolves the named retained
  node in the current authoritative tree; specific inspection/annotation references
  still retain their required exact viewed context, not a substitute node.
- Canonical bytes are RFC 8785 canonical JSON of the **typed semantic record**:
  explicitly expand schema defaults/null optionals, omit no semantic fields, sort
  sets/maps with the schema's canonical key order, retain ordered arrays. Reject
  unknown command fields; do not hash raw JSON key order, auth headers or transport
  metadata. Text is preserved according to its field's contract, not universally
  trimmed/Unicode-normalized. Integers outside JSON's exact range are decimal
  strings. Geometry uses integer logical subunits (1/1024 board unit); rounding is
  half away from zero at gesture-to-command capture, not on replay.
- SHA-256 over domain-tagged canonical bytes provides payload identity, definition
  IDs and checkpoint digests; it is not authentication or anti-cheat. Tags include
  record kind and schema version. Definitions use `sha256:<64 lowercase hex>` IDs;
  a claimed digest is recomputed. Same external printing ID is not same content.

## Connection and stream state

One authenticated socket may subscribe to multiple games, but each game's writes
have an independent ordered stream. The socket HTTP upgrade uses the normal bearer
header over TLS; no credentials in URLs. JSON codec is shared with HTTP.

| Message | Required fields / response |
| --- | --- |
| `hello` | `protocolMajor=1`, supported minor range, engine/read-format versions, random process ID, installation ID; server returns `welcome` with chosen versions, limits, heartbeat and supported formats, or `upgradeRequired`. No gameplay admitted before agreement. |
| `openStream` | `gameId`, process-local stream ID; server returns `streamReady` with monotonically increased fence generation for that installation/game. Creating/replacing it is serialized with admission. |
| `resumeStream` | Same installation/game/stream identity while process lives and retained uncertain request IDs; server fences the old connection, returns generation and authorized pending/final/not-admitted statuses for those IDs. No client sequence watermark. |
| `subscribe` | Game ID, accepted revision, requested read formats; server returns `head`, revision suffix, or `baselineRequired`. Subscription is distinct from write readiness. |
| `head` | Game ID, current revision, minimum resumable base revision, current lifecycle IDs, engine/read versions; sent on subscribe and every 30 seconds while connected, even without gameplay. |
| `ping` / `pong` | Correlation ID only. 30-second heartbeat; close/reconnect after 90 seconds without server traffic. Heartbeat does not expire login or acknowledge gameplay. |

On reconnect, stop new gameplay creation until authentication, stream fencing and
accepted-state catch-up succeed; downloaded review stays usable. Retained earlier
unadmitted commands are sent in original sequence before new work, without waiting
for their final execution. D-285 uses a simple client FIFO and ordered WebSocket/TCP
delivery, not contiguous client admission numbers. A rejected or never-admitted
message does not reserve a missing sequence that blocks later valid messages.
Retry retained uncertain originals in local order with the same request IDs; TCP
handles ordinary loss within a connection. No server gap reconstruction, dependency
graph or reordering. Different devices/processes have
different streams; process restart discards the old local queue (D-278), opens a new
stream and synchronizes, not resumes forgotten commands. Old admitted work remains.

If the old process's stream is replaced, the fence generation prevents its late
frames being newly admitted. An admission committed before cutover is retained.
Reconnect retry delays use full jitter over 1, 2, 4, 8, 16, then 30 seconds, reset
after successful readiness. Foreground network restoration attempts immediately;
no endless hot retry on invalid auth/update-required/storage failure.

## Command and result envelopes

`command` fields: `requestId`, `scope`, `commandVersion=1`, `action`, optional
`invocationId`, and `continuation` (`null` or `{nodeId, explicit:true}`, within the
scope's required `playId`). Transport fields are `streamId` and `generation`.
Actor is derived from auth,
never trusted from a supplied author label. Request identity is globally unique;
the server binds it permanently to that account and canonical semantic payload.
Another account's collision returns inaccessible/conflict, never that outcome.

Optional `inlineDefinitions` carries supported small content-addressed snapshots
needed by this command, within the same command byte bound. It is semantic input
and immutable on retry. Validate/retain them atomically with the accepted action;
they cannot overwrite accepted different content. Prepared-deck definition sets
use the ordinary HTTP POST below; an already-retained digest needs no repeat content.
This allows a normal new token to predict/send without a metadata-submission
acknowledgement barrier.

`action` is a discriminated typed payload, not a materialized client board or a list
of authoritative effects. Every lowered constituent has its own request identity.
Canonical action fields are the concrete operands in the primary action contract:

| Family | Captured data and guards |
| --- | --- |
| Orientation/text/counter/face/cover | Target/occurrence and field/color IDs, requested absolute value or relative delta and selected face; no old-value/card-revision guard. Preserve field-specific trimming/numeric grammar. |
| Move/Draw/placement | Real instance ID, original source container/placement, destination kind/container/gap/anchor and explicit choices. Ordered Draw includes original top identity; grouped moves include captured moved-member IDs for approved gap exclusion, not a sequencing dependency. Movement guard equality is semantic membership/placement/order actually required, not unrelated card text. |
| Stack entry | Source/real-move operands plus `expectedStackDigest`: SHA-256 of the domain-tagged canonical ordered pre-action entry projection (real-card ID or effect-occurrence ID plus source ID and entry kind), including the empty sequence. D-247 equality ignores source fields, camera, layout and frozen status. Compute/compare against the selected execution state atomically; not a whole-game revision guard. |
| Clone/token/area/projection/link | Source/definition/face/slot/endpoint and placement choices; output IDs follow the deterministic rule below. Reset/Clone read actual chosen execution state, not client-predicted copied fields. |
| Shuffle/die/coin | Target plus versioned seed or explicit sample, bounds/count and original random input; no old Library-membership guard. Concrete accepted outcome is server output. |
| Undo/Redo | Action kind and scope only; server selects then-current latest-own/next entry. No captured target/head CAS, dependency chain or skip permission. |
| Activate/archive/restore | Exact retained target ID and explicit control kind; only an actual shared switch clears both Redo queues. Missing/removed target rejects, not nearest-parent activation. Metadata-only archive/restore does not silently activate. |
| Comment/ping | Actual content/coordinates and immutable referenced context, separately from server-selected current recording context. No client-assigned group. |
| Inspect / View / Reveal | Exact target/face/context for specific inspection; named source, top/bottom/all and requested count for a contents operation. Server resolves the latter's members/order/count clamp at execution, not from predicted member IDs. Accepted references then stay fixed under D-47. Historical inspection validates its exact retained context without activating it; no fallback if removed. No separate speculative-disclosure report (D-286). |
| Tracking/ending/lobby/invitation | Explicit requested step/player/suggestion outcome or exact pending-suggestion/attempt ID and original lifecycle scope, as required by the contract. No blanket old-head guard. |

No family adds implicit legality/priority checks. Arbitrary unrecognized action kinds
reject before admission. Schema generation must enumerate the approved operands
and discriminators from these primary contracts; it cannot add default substitution
or optional guards not authorized here.

Stack checksum v1 hashes canonical JSON
`{"kind":"stack-guard","version":1,"playId":"<UUID>","entries":[]}` with each
entry encoded as `{"kind":"realCard|effect","id":"<UUID>","sourceId":"<UUID>"}`.
The entry-kind string is exactly `realCard` or `effect`, not the example's union;
`id` is respectively the real-card or effect-occurrence ID, and `sourceId` is the
real-card/source ID (equal to `id` for a real card). No optional fields. Array order
is Stack order; encode SHA-256 as `sha256:<64 lowercase hex>`. Empty Stack uses the
same descriptor with `entries:[]`. A checksum mismatch has the existing
`stackConflict` meaning, not a new guard family.

D-286 applies ordinary prediction/reconciliation to revealing actions. For Library
`[A,B,C,D]`, a predicted Reveal top 3 shows A/B/C; if the opponent's Draw A executes
first, the accepted Reveal records B/C/D and the client corrects its display. The
extra glimpse of A is acceptable, not a second event, Undo barrier, Redo reset or
historical-provenance obligation. Rejected predictions likewise create no exposure
event merely because content was shown locally. Explicit accepted inspections and
revealing actions retain their ordinary server records; retry their original
request IDs, not an additional report. A removed/inapplicable inspection target
rejects normally without reconstructing its old context. Draw/move requests retain
their original bound-card movement guards; this current-source resolution is for
View/Reveal, not permission to substitute drawn cards.

One command produces at most one board mutation node; mandatory cleanup belongs
to that invariant-closed transition. Board node ID is UUIDv5 with request ID as
namespace and UTF-8 name `board-node`. New object/occurrence/link IDs are UUIDv5
with the same namespace and names `<role>/<zero-based-index>`, enumerated in the
captured semantic operand order. Shared processing supplies the role registry;
indices never depend on unordered map iteration or actual accepted placement.
Server validates these output identities/collisions; it does not remap them to
fresh IDs. Thus later requests can reference predicted outputs without ack waits
or knowledge of their eventual server revision/effective ordinal (D-289).
Failed/no-op creation leaves no object/node: later original references reject if
actually absent, while independent later work is still evaluated. UUIDv5 is an
identity function here, not a security hash. Initial game objects use a separately
recorded server initialization identity with the same deterministic role scheme.

| Server message | Fields and meaning |
| --- | --- |
| `receipt` | Request ID, scope, ingress ordinal; only after inbox commit. Pending, not accepted. |
| `result` | Request ID, scope, outcome (`accepted`, `rejected`, `noOp`), stable reason code, ingress ordinal, nullable commit revision, created IDs and concrete recorded result. No old success-state snapshot to install over newer state. |
| `resultStatus` | Per authorized requested ID: `pending`, `final` plus result, or `notAdmitted`. Transport/database unavailable is a separate retryable error, not `notAdmitted`. |
| `notAdmitted` | Checked after stream fence under the admission lock; the old connection can no longer admit it. Resubmit only retained original intent under the new fence, ahead of its local suffix. It is not a final gameplay rejection. |
| `admissionError` | Request ID when parseable, code and retryability. Size/rate/fence failures reserve no queue position. Retry transient failures with original intent while memory survives; retire a definitively unadmitted terminal failure's prediction and continue later messages. Never send a durable receipt for them or infer non-admission from timeout alone. |

Reasons: `invalidInput`, `inapplicable`, `movementConflict`, `stackConflict`,
`missingReference`, `lifecycleEnded`, `accessDenied`, `replayBlocked`,
`validationBudget`, `requestIdentityMismatch`, `streamFenced`,
`busy`, `storageUnavailable`, `upgradeRequired`. Auth failure is connection/HTTP
`unauthorized`, not a manufactured outcome for an unknown prior request. Human
messages are localized client-side; diagnostic payloads never leak another game.

## Result/update correlation and presentation

`revision` identifies one atomic committed shared change with `baseRevision`,
`resultRevision`, request ID/author, scope, typed record upserts/removals, current
pointer/control/lifecycle changes and deterministic grouping changes. A no-op or
rejection may have no revision. Accepted exposure-only/control/chat changes still
advance the shared revision when shared state changes. Admission order is not a
revision and does not imply a board node.

All client coordination runs on one serialized per-game coordinator. Save an entire
revision's accepted records and cursor in one SQLDelight transaction, then publish
one accepted-base/active-prediction/display target update to observers:

- Result first: keep an accepted predicted effect until its `commitRevision` is
  incorporated (or baseline at/after it is installed). Do not apply result output
  as another board delta. Rejection/no-op retires its effect immediately and rebases.
- Delta first: its request ID marks that prediction incorporated before rebuilding
  the overlay, even if final result has not arrived. Later result resolves only
  bookkeeping. Duplicate delta/result cannot apply twice.
- Full baseline crossing pending outcomes: query all surviving in-memory pending
  IDs and correlate final commit revisions before publishing the rebuilt overlay.
  Pending/notAdmitted originals remain provisional only if still applicable; no
  blind replay over a base that might already contain them. Restart has no such
  overlay or IDs and simply installs accepted state (D-278).
- Incorporate remote work before replaying active predictions in original order.
  Withdrawn effects stay withdrawn for the remaining process lifetime. Late
  acceptance follows the ordinary incoming path (D-277), including after restart.
- Two simultaneously explicit historical requests validate their own retained
  node at their execution turn and can create distinct genuine continuations;
  no stale-head conflict fork. Later ordinary requests run on current state with
  original operands. A historical chain can name preceding deterministic predicted
  node IDs without inventing future revision/ordinal values; missing prerequisite
  rejects that reference, never supplies another base.
- Catch-up cursor is local presentation state, not sync revision. First open without
  a saved valid boundary starts at accepted current state; a saved retained boundary
  with unpresented work uses playback. Serialize playback-complete/head comparison
  with incoming updates: enter live only against the coordinator's latest coherent
  target. Any next arrival is a normal live update; no lost racing tail. Exact
  control layout/autoplay preference and animation timing stay in the UI gate.

For slow consumers, bound queued socket delivery as in the runtime profile; closing
one subscriber never rolls back committed state. A single revision/result too large
for queued delivery is advertised by `revisionAvailable` (`gameId`, `throughRevision`)
or `resultAvailable` (`scope`, `requestId`), then read through ordinary HTTP revision/
result routes. Stream JSON collections to disk staging without a whole-response
heap blob; commit/publish one complete revision at a time. These hints do not count
as incorporation/results or fragment one semantic command into multiple mutations.

## Reads, full transfer and versioning

All routes have `/v1` prefix. Account/contact/deck reads and business-data mutations
use HTTPS JSON; business-data mutations carry stable request IDs. Game writes use
the ordered socket, including existing-game invitations/lifecycle/control; do not
create competing unordered HTTP write ingress. HTTP registration/reset/login/logout
follow the authentication contract, not generic replay of secret-bearing responses.

Core routes: `POST /accounts`, `POST /sessions`, `DELETE /session`,
`POST /password-reset-requests`, `POST /password-resets`, `GET /me`,
`GET /contacts`, `POST /contacts`, `GET/POST /decks`, `GET/PUT/DELETE /decks/{id}`,
`POST /definitions` (complete account-private card-metadata set),
`GET /decks/{id}/definitions/{digest}` (owned-deck accepted definitions),
`GET /invitations`, `POST /invitations` (create a new game and its first invitation),
`GET /games`, `GET /games/{id}/head`, `GET /games/{id}/revisions?after=...`,
`POST /games/{id}/results:lookup`, `GET /games/{id}/results/{requestId}`,
`POST /games/{id}/baselines`,
`GET /baselines/{id}/manifest`, `GET /baselines/{id}/chunks/{index}`,
`GET /games/{id}/definitions/{digest}`, `GET /activity?after=...`,
`PUT/DELETE /notification-registration`. A separate `reinvite` socket command names
the exact existing game and originating preparation/invitation context; it never
creates a game. Accept/Decline/cancel/Ready/preparation and start/end transitions
use that persistent game's serialized command domain. If cancellation wins first,
discard reinvitation as missing/cancelled; if it wins afterward, the game and its
invitation leave current listings together. No revival/replacement (D-287).
Deck updates are approved import/metadata operations, not a card-builder API.
Under D-293, create-import uses `POST /decks`; contextual import through
`PUT /decks/{id}` binds `overwrite` (default) or `merge`, complete resolved entries
and the exact existing target to the original request. Serialize at that saved
deck: overwrite installs the supplied sections; merge adds quantities to the
execution-state entries per section, leaving untouched entries. Atomic effects +
deduplicated outcome prevent retry from adding twice; missing/deleted targets
never become upsert. Metadata/copy-isolation rules remain unchanged.

**D-293 active deletion** is an ordered `deleteGame` socket lifecycle command,
not a competing HTTP write. Bind game ID, active incarnation and stable request ID;
authorize either participant and require that incarnation still be active. Apply
the primary contract's both-player removal/access invalidation atomically with its
outcome and lifecycle publication. Retain a tombstone/outcome so reconnect/result
lookup can establish deletion without exposing inaccessible board data or creating
a replacement. Prior authorized/admitted work may complete in normal order; work
processed after deletion fails applicability. Stale deletion after result acceptance
or a fresh incarnation fails; it never converts to setup cancellation. Baseline/
definition/history access cannot install or reopen a known deleted game. Local
confirmation is UI intent capture, not opponent consent or a server dialog protocol.

HTTP business-data mutations bind request/account/kind/scope/canonical intent and
atomically store the final non-secret outcome with their effects. Concurrent/repeated
IDs recover one creation/update/deletion outcome, including a new game's original
game/invitation IDs, without recreating it after cancellation. Changed intent under
one ID is a conflict; recovery is account-authorized. Saved-deck updates/deletion
serialize on that deck; delete never becomes upsert. Initial game+invitation creation
is one transaction; subsequent existing-game work enters the game dispatcher.
Notification registration is an absolute session+installation-bound upsert/delete.

Card definitions are small metadata submissions, not artwork or a full catalog.
Use one account-scoped `POST /definitions` with the complete JSON definition set
and normal HTTP request handling (D-289). Networking libraries handle transport
streaming/framing; there are no application chunk/index/manifest/completion routes,
resumable-upload sessions or custom upload protocol. Validate the full body's
schemas/digests/references before atomically retaining the account-private set and
returning its content-addressed definition IDs. Repeating identical content reuses
those IDs. An interrupted/invalid request installs no partial set and can be retried
as an ordinary POST. Apply normal HTTP input/resource validation, not a separate
upload-quota subsystem or an invented deck-size rule.

No game must exist to import/save a deck. A saved-deck HTTP mutation atomically
attaches the accepted definitions to its entries, or the ordered prepared-deck/game
command attaches them under existing copy/Ready rules. The metadata POST alone
does neither; ownership checks cover definition reads and attachment. Missing or
incomplete definitions cannot replace a saved/selected deck or make Ready succeed.
There is no extra user Send step, server-side artwork fetch or automatic expiry of
attached required definitions. This replaces the earlier staged/chunked definition
upload design, not the separate D-276 complete-history baseline transfer.

HTTP statuses: 400 invalid structure, 401 invalid login, 404 inaccessible/missing
resource without existence disclosure, 409 identity/lifecycle conflict, 410 expired
transfer, 413 oversized input, 426 incompatible protocol, 429 rate limit with
`Retry-After`, 503 unavailable. A known final gameplay rejection is a normal result,
not a network error. Error body is `{code, retryable, correlationId, details?}`.
List pages have immutable snapshot cursor plus next cursor; default/max 100/500
records. Expired cursor explicitly restarts the read, never silently skips entries.

Full transfer pins revision R and lists every retained tree/control/definition
record, chunk identities/count/byte lengths and SHA-256 of each uncompressed chunk.
Use independent gzip chunks (1 MiB target, 4 MiB hard decoded chunk size); records
larger than the target use bounded fragments reassembled before validation. HTTP
compressed content length is bounded separately (4 MiB per chunk); decompression
stops at declared/hard length. Stage to disk, verify all counts/hashes/references
and coverage. Catch up staged R to a coherent head at least as new as the currently
published accepted replica, then atomically switch the local accepted-generation
pointer and reconcile pending IDs before publishing its display. Serialize this
handoff with incoming updates; do not install older R over already-presented later
accepted work and visibly roll it back. Keep the previous accepted generation on
failure; never combine transfer IDs/revisions. Confirm head independently and apply
subsequent arrivals normally. Manifest SHA-256 covers ordered
chunk descriptors and scope/R/format. This checks transferred bytes; checkpoint
state digest separately verifies materialized semantics (D-264).

Register the R/suffix pin atomically with snapshot selection before pruning can
remove the required continuation. Export failure releases that pin; a transfer
lease bounds its life. No slow client keeps an unbounded MVCC transaction open.

Protocol v1 initially supports semantic engine v1 and read format v1. Backward-
compatible optional response fields may be added within the major; commands reject
unknown semantic fields rather than quietly ignoring new intent. A semantic change
requires a distinct negotiated engine/action version, with all participants able
to read the game's records. Keep v1 decoders/replayers for retained v1 history;
never reinterpret it with v2 defaults. APK versions need not match. Supporting a
new version requires migration/mixed-version tests, not a promise to support every
future version. Dependency patch numbers are build lockfile work, not wire policy.

The implementation's OpenAPI/JSON Schema documents must mechanically encode these
tables and the action operands and be checked for agreement; no executable API
schema, application or tests are claimed by this prose specification.
