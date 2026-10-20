# Protocol v1: requests, results and replica transfer

Status: technical completion under the owner's instruction to finish the remaining
specifications (D-284), implementing D-183–D-187/D-259–D-278. This is a documentation
contract, not application code or a passed delivery gate. Product contracts win over
encoding examples. [Request ordering](request-ordering-and-recovery.md) owns intent;
[storage mechanics](storage-mechanics.md) owns transactions and retention parameters.

## Types and canonical representation

- UTF-8 JSON, `application/json`; socket messages carry a `type` discriminator.
  No polymorphic class names, executable data, NaN/Infinity or duplicate object keys.
- IDs are lowercase hyphenated UUIDs. New request/installation/process IDs use
  cryptographically random UUIDv4. Server identities use UUIDv4 too. Compare IDs by
  their unsigned 16-byte representation, never locale/string-library ordering.
- Revisions, ingress ordinals and stream sequence numbers are nonnegative signed
  64-bit values encoded as decimal **strings**, starting at `"0"`. No floating-point
  clocks/counters for causality. Timestamps are UTC RFC 3339 explanatory metadata.
- `scope` is `{gameId, playId?, preparationId?, invitationId?}`. Required lifecycle
  IDs depend on operation; missing or wrong IDs reject, never default to fresh play.
  Account endpoints use authenticated account scope instead of a game ID.
- A `boundary` is `{playId, nodeId, treeRevision, effectiveOrdinal}`; root has its
  own ID. The ordinal distinguishes exposure-only records at the same board node.
  Current tree validation resolves surviving IDs at current revision; revision
  qualification prevents mistaking a rewritten node's state for its old context.
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
| `openStream` | `gameId`, process-local stream ID; server returns `streamReady` with monotonically increased fence generation for that installation/game and next accepted sequence. Creating/replacing it is serialized with admission. |
| `resumeStream` | Same installation/game/stream identity while process lives; server fences the old connection, returns generation and largest contiguous admitted sequence plus outcomes for retained pending request IDs. |
| `subscribe` | Game ID, accepted revision, requested read formats; server returns `head`, revision suffix, or `baselineRequired`. Subscription is distinct from write readiness. |
| `head` | Game ID, current revision, minimum resumable base revision, current lifecycle IDs, engine/read versions; sent on subscribe and every 30 seconds while connected, even without gameplay. |
| `ping` / `pong` | Correlation ID only. 30-second heartbeat; close/reconnect after 90 seconds without server traffic. Heartbeat does not expire login or acknowledge gameplay. |

On reconnect, stop new gameplay creation until authentication, stream fencing and
accepted-state catch-up succeed; downloaded review stays usable. Retained earlier
unadmitted commands are sent in original sequence before new work, without waiting
for their final execution. Local `streamSequence` is contiguous; duplicates with
the same request are allowed, a gap returns `sequenceGap(expected)` and is not
admitted. No server dependency graph/reordering. Different devices/processes have
different streams; process restart discards the old local queue (D-278), opens a new
stream and synchronizes, not resumes forgotten commands. Old admitted work remains.

If the old process's stream is replaced, the fence generation prevents its late
frames being newly admitted. An admission committed before cutover is retained.
Reconnect retry delays use full jitter over 1, 2, 4, 8, 16, then 30 seconds, reset
after successful readiness. Foreground network restoration attempts immediately;
no endless hot retry on invalid auth/update-required/storage failure.

## Command and result envelopes

`command` fields: `requestId`, `scope`, `commandVersion=1`, `action`, optional
`invocationId`, and `continuation` (`null` or `{boundary, explicit:true}`). Transport
fields are `streamId`, `generation`, `streamSequence`. Actor is derived from auth,
never trusted from a supplied author label. Request identity is globally unique;
the server binds it permanently to that account and canonical semantic payload.
Another account's collision returns inaccessible/conflict, never that outcome.

Optional `inlineDefinitions` carries supported small content-addressed snapshots
needed by this command, within the same command byte bound. It is semantic input
and immutable on retry. Validate/retain them atomically with the accepted action;
they cannot overwrite accepted different content. Larger prepared-deck definitions
use staged upload below; an already-retained digest needs no repeat content. This
allows a normal new token to predict/send without a definition-upload ack barrier.

`action` is a discriminated typed payload, not a materialized client board or a list
of authoritative effects. Every lowered constituent has its own request identity.
Canonical action fields are the concrete operands in the primary action contract:

| Family | Captured data and guards |
| --- | --- |
| Orientation/text/counter/face/cover | Target/occurrence and field/color IDs, requested absolute value or relative delta and selected face; no old-value/card-revision guard. Preserve field-specific trimming/numeric grammar. |
| Move/Draw/placement | Real instance ID, original source container/placement, destination kind/container/gap/anchor and explicit choices. Ordered Draw includes original top identity; grouped moves include captured moved-member IDs for approved gap exclusion, not a sequencing dependency. Movement guard equality is semantic membership/placement/order actually required, not unrelated card text. |
| Stack entry | Source/real-move operands plus ordered pre-action entry IDs (empty array is empty Stack). D-247 equality ignores source fields, camera, layout and frozen status. No whole-game revision guard. |
| Clone/token/area/projection/link | Source/definition/face/slot/endpoint and placement choices; output IDs follow the deterministic rule below. Reset/Clone read actual chosen execution state, not client-predicted copied fields. |
| Shuffle/die/coin | Target plus versioned seed or explicit sample, bounds/count and original random input; no old Library-membership guard. Concrete accepted outcome is server output. |
| Undo/Redo | Action kind and scope only; server selects then-current latest-own/next entry. No captured target/head CAS, dependency chain or skip permission. |
| Activate/archive/restore | Exact retained target ID and explicit control kind; only an actual shared switch clears both Redo queues. Missing/removed target rejects, not nearest-parent activation. Metadata-only archive/restore does not silently activate. |
| Comment/ping/exposure | Actual content/coordinates and immutable viewed-reference context, separately from server-selected current recording context. Exposure includes actual shown face/result and originating request ID if any. No client-assigned group. |
| Tracking/ending/lobby/invitation | Explicit requested step/player/suggestion outcome or exact pending-suggestion/attempt ID and original lifecycle scope, as required by the contract. No blanket old-head guard. |

No family adds implicit legality/priority checks. Arbitrary unrecognized action kinds
reject before admission. Schema generation must enumerate the approved operands
and discriminators from these primary contracts; it cannot add default substitution
or optional guards not authorized here.

An exposure report carries its own immutable request ID plus nullable
`originatingRequestId`, viewed boundary, exposed face/content identities and actual
shown result. Enqueue it immediately after the originating online command, without
waiting for success. At execution, correlate with that earlier final outcome: if
the accepted revealing event already records the **same disclosure/context**, attach
the report to that event rather than create a second effective action/barrier or
second Redo clearing. If the board command rejected or disclosed different content,
retain actual predicted disclosure as its own effective exposure record. Standalone
inspection is an effective record directly. Retry deduplicates the report identity,
not distinct inspections; board rejection cannot cancel it. D-278 permits loss of
an unadmitted report on process death/Logout, not manufactured post-crash reports.

One command produces at most one board mutation node; mandatory cleanup belongs
to that invariant-closed transition. Board node ID is UUIDv5 with request ID as
namespace and UTF-8 name `board-node`. New object/occurrence/link IDs are UUIDv5
with the same namespace and names `<role>/<zero-based-index>`, enumerated in the
captured semantic operand order. Shared processing supplies the role registry;
indices never depend on unordered map iteration or actual accepted placement.
Server validates these output identities/collisions; it does not remap them to
fresh IDs. Thus later requests can reference predicted outputs without ack waits.
Failed/no-op creation leaves no object/node: later original references reject if
actually absent, while independent later work is still evaluated. UUIDv5 is an
identity function here, not a security hash. Initial game objects use a separately
recorded server initialization identity with the same deterministic role scheme.

| Server message | Fields and meaning |
| --- | --- |
| `receipt` | Request ID, scope, ingress ordinal, stream sequence; only after inbox commit. Pending, not accepted. |
| `result` | Request ID, scope, outcome (`accepted`, `rejected`, `noOp`), stable reason code, ingress ordinal, nullable commit revision, created IDs and concrete recorded result. No old success-state snapshot to install over newer state. |
| `resultStatus` | Per authorized requested ID: `pending`, `final` plus result, or `notAdmitted`. Transport/database unavailable is a separate retryable error, not `notAdmitted`. |
| `notAdmitted` | Checked after stream fence under the admission lock; the old connection can no longer admit it. Resubmit only retained original intent under the new fence, ahead of its local suffix. It is not a final gameplay rejection. |
| `admissionError` | Request ID when parseable, code and retryability. Size/rate/sequence/fence failures reserve no queue position. Never send a durable receipt for them. |

Reasons: `invalidInput`, `inapplicable`, `movementConflict`, `stackConflict`,
`missingReference`, `lifecycleEnded`, `accessDenied`, `replayBlocked`,
`validationBudget`, `requestIdentityMismatch`, `streamFenced`, `sequenceGap`,
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
  boundary at their execution turn and can create distinct genuine continuations;
  no stale-head conflict fork. Later ordinary requests run on current state with
  original operands. A historical chain can name preceding deterministic predicted
  node IDs; missing prerequisite rejects that reference, never supplies another base.
- Catch-up cursor is local presentation state, not sync revision. First open without
  a saved valid boundary starts at accepted current state; a saved retained boundary
  with unpresented work uses playback. Serialize playback-complete/head comparison
  with incoming updates: enter live only against the coordinator's latest coherent
  target. Any next arrival is a normal live update; no lost racing tail. Exact
  control layout/autoplay preference and animation timing stay in the UI gate.

## Reads, full transfer and versioning

All routes have `/v1` prefix. Account/contacts/decks/invitations/lobby reads and
writes use HTTPS JSON with stable request IDs for mutating operations. Game writes
use the ordered socket, including game lifecycle/control; do not create competing
unordered HTTP write ingress. HTTP reset/login/logout remain account operations.

Core routes: `POST /accounts`, `POST /sessions`, `DELETE /session`,
`POST /password-reset-requests`, `POST /password-resets`, `GET /me`,
`GET /contacts`, `POST /contacts`, `GET/POST /decks`, `GET/PUT/DELETE /decks/{id}`,
`GET /invitations`, `POST /invitations` (new or explicit reinvitation attempt),
`GET /games`, `GET /games/{id}/head`, `GET /games/{id}/revisions?after=...`,
`POST /games/{id}/results:lookup`, `POST /games/{id}/baselines`,
`GET /baselines/{id}/manifest`, `GET /baselines/{id}/chunks/{index}`,
`GET /games/{id}/definitions/{digest}`, `GET /activity?after=...`,
`PUT/DELETE /notification-registration`. Accept/Decline/cancel/Ready/preparation
and start/end transitions use the persistent game's serialized command domain.
Deck updates are absolute approved import/metadata operations, not an editor API.

Large content staging uses `POST /games/{id}/definition-uploads`,
`PUT /definition-uploads/{id}/chunks/{index}` and
`POST /definition-uploads/{id}/complete` with expected digest/count/lengths. Chunk
bounds match baseline transfer. Upload writes account-private untrusted staging,
not prepared deck/game state; completion validates all schemas/digests/references.
The ordered prepared-deck/game command then atomically attaches the complete
definition manifest and applies existing copy/Ready rules. Incomplete upload cannot
replace the old selected deck or make Ready succeed; no extra user Send step.
Unused staging expires after 24 hours; attached required definitions do not. The
service does not fetch arbitrary definition/image URLs on the uploader's behalf.

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
and coverage, then atomically switch the local accepted-generation pointer. Keep
previous accepted generation on failure; never combine transfer IDs/revisions.
Catch up R→head and confirm head independently. Manifest SHA-256 covers ordered
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
