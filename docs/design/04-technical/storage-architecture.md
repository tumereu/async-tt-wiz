# Storage architecture

## Selected direction — D-265

The owner accepts the storage proposal: PostgreSQL/jOOQ with explicit relational
structure and versioned payloads on the server; SQLite through SQLDelight on the
client. Both map to the same Kotlin semantic objects/processor, not shared database
schemas or generated persistence types. Exact schema, indexes, versions, encryption
and retention policies remain design work. No implementation is authorized.

## Server storage responsibilities

| Data | Selected representation direction |
| --- | --- |
| Games, participants, lifecycle | Relational records with explicit identities and constraints. |
| Gameplay tree | Node/parent relationships and branch metadata, indexed for paginated traversal, separate from mutation payloads. Not one recursive JSONB tree document. |
| Recorded mutations | Versioned actual transitions and recorded random outcomes. Removing a logical tree node does not automatically delete data needed by Redo, references or exposure/recovery rules. |
| Current materialized state | Object/container/control records updated incrementally, not a full history document rewritten on each action. |
| Synchronization | Ordered committed revision/change records, separate from retained gameplay history. |
| Request admission/recovery | D-275: PostgreSQL-backed durable per-game inbox with server-assigned order, immutable request/semantic payload identity and pending/final outcomes, independent from the sync replay window. Exact tables/locking/fencing/retention remain design work, not the overall mechanism. |
| Notifications | Transactional outbox records delivered after commit. |
| Checkpoints | Materialized state at an explicit boundary/revision with D-264 integrity metadata. Derived checkpoints do not replace retained essential initial/game definitions or history. |

Use relational columns for identities, relationships, ordering and queried fields;
versioned JSONB payloads for structured object/mutation data where suitable. Do not
let foreign-key cascades or generic JSON updates define tree edits, cleanup or
game semantics. Transaction boundaries retain the [server obligations](server-architecture.md#authority-and-transaction-obligations).
D-275 distinguishes inbox-admission commit/receipt from final atomic command
execution: accepted state/history/control, result, applicable sync changes and
outbox work commit together. Pending duplicates have one queue position; final
duplicates recover original outcomes without another mutation. Receipt alone
cannot advance a client accepted-state cursor or mark a command successful. See
[request ordering/recovery](request-ordering-and-recovery.md).

**Checkpoint meaning — D-266:** checkpoints are rebuildable materialized-state
caches/optimizations for loading, replay and baseline transfer. Evicting one may
make reconstruction slower, not remove an inspectable history boundary or change
its result. Retained initial/root state, essential game definitions and recorded
mutations/tree/control data remain the durable basis; those are not disposable
merely because a checkpoint exists. A baseline may be generated again from that
basis. Checkpoint eviction and sync-delta pruning do not delete recovery outcomes.

## Client storage responsibilities

SQLDelight provides SQL-first type-safe query APIs over SQLite with platform driver
adapters. Repository logic can be shared without leaking SQLDelight-generated
types into the game engine. Room Multiplatform was considered, not selected.

Keep four distinct logical categories; this does not require four database files:

1. **Accepted replica:** downloaded records/control state and an atomic accepted
   revision/coverage cursor. D-276 initial/full recovery downloads the complete
   retained current-game tree and required definitions, not default lazy branches.
   Partial staging still distinguishes unfetched from nonexistent records and
   carries coherent revision-qualified chunks/pages; only complete coverage may
   be labelled a completed full download.
2. **Volatile recovery (D-278):** original requests/random inputs, active/withdrawn
   flags and explicit inspection requests live in process memory. Baseline repair
   preserves surviving memory, but process death/Logout drops it. No crash-durable
   outgoing table or exposure journal. D-286 removes separate speculative-disclosure
   reports; server-admitted work and accepted inspection records remain durable.
3. **Disposable caches:** derived snapshots, refetchable catalog data and images.
   Retained essential per-game definitions/defaults are not disposable server data.
4. **Personal state:** drafts, review positions and appropriate UI restoration data,
   separate from authority and subject to account-private Logout rules.

D-277 keeps active-versus-withdrawn prediction disposition separate from immutable
request intent and final server outcome. A withdrawn already-sent prediction must
not be re-added during same-process recovery; restart drops the overlay/journal.
Late accepted deltas are
ordinary authoritative changes, not a request to replay the old overlay. Personal
live/playback mode and last presented boundary are distinct from accepted sync
revision, since background delivery can advance data before it was watched.

D-270 requires local storage/reuse of client-downloaded public card records and
artwork, not a mandatory backend catalog/image proxy. The refetchable public
catalog/art cache remains distinct from essential accepted per-game definitions
and account-private recovery/downloads. Exact cache retention/update policy remains
design work; see [card data and artwork](card-data-and-art.md).
D-271 permits arbitrary client-supplied definitions after structural/security
validation, not official-provider verification. Retain accepted snapshots as game
data and transfer required definitions independently of a recipient's public
catalog. Differing supplied content under the same name/external ID needs distinct
definition identity; catalog updates/deduplication cannot overwrite an accepted
snapshot. Image bytes load/cache lazily and remain separate from required records.

## Migration boundaries

- Server: Flyway SQL against PostgreSQL; jOOQ generation checked against the migrated
  schema and actual query tests.
- Client: SQLDelight migrations checked against retained older SQLite schemas.
- Preserve request/outcome identities, recorded results and essential definitions;
  neither path may silently wipe pending obligations or reinterpret history.
- Database schemas may differ; semantic state and processing remain shared.

## Synchronization retention is not gameplay-history retention

D-265 proposes a maximum retained revision count and requires fresh baseline/full
sync when needed revisions are unavailable. D-266 confirms that retention design
with checkpoints understood as rebuildable caches: a rolling sync-revision window
is selected in direction, not an exact count/age/byte budget or permission to
expire gameplay history. See the [baseline recovery design](sync-and-storage.md#bounded-revision-window-and-baseline-recovery--d-265).

Do not prune retained nodes/mutations, branches, Redo/exposure/reference obligations
or request outcomes merely because their synchronization deltas leave that window.
Checkpoints reduce transfer/replay cost, not the promised current-game review scope.
D-276 makes initial/full recovery include every retained current-game continuation;
normal play/reconnect remains incremental. Compression/chunking does not change that
complete-download target or require storing a full board snapshot for every node.
Finished-game archives remain outside R1; permanent retention/account erasure and
removed-record disposal use D-283's no-age-expiry required-current-game boundary
and external operator erasure responsibility, not a new finished-game feature.

The [storage mechanics](storage-mechanics.md) now supplies concrete logical tables,
indexes/locks, replay/N budgets, cache/lease/retention defaults and crash matrix.
[Protocol v1](protocol-v1.md) supplies encoding/transfer/result correlation;
[runtime profile](runtime-and-verification-profile.md) supplies migrations/build
qualification and resource/verification parameters. Older "exact ... design work"
labels here are completed by those contracts, not additional product decisions.

References: [SQLDelight multiplatform SQLite](https://sqldelight.github.io/sqldelight/latest/multiplatform_sqlite/),
[Room Multiplatform](https://developer.android.com/kotlin/multiplatform/room).
