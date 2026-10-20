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
| Request recovery | Original request identity/payload identity and pending/final outcome, independent from the sync replay window. Exact admission/inbox mechanisms remain proposals. |
| Notifications | Transactional outbox records delivered after commit. |
| Checkpoints | Materialized state at an explicit boundary/revision with D-264 integrity metadata. Derived checkpoints do not replace retained essential initial/game definitions or history. |

Use relational columns for identities, relationships, ordering and queried fields;
versioned JSONB payloads for structured object/mutation data where suitable. Do not
let foreign-key cascades or generic JSON updates define tree edits, cleanup or
game semantics. Transaction boundaries retain the [server obligations](server-architecture.md#authority-and-transaction-obligations).

## Client storage responsibilities

SQLDelight provides SQL-first type-safe query APIs over SQLite with platform driver
adapters. Repository logic can be shared without leaking SQLDelight-generated
types into the game engine. Room Multiplatform was considered, not selected.

Keep four distinct logical categories; this does not require four database files:

1. **Accepted replica:** downloaded records/control state and an atomic accepted
   revision/coverage cursor. A partial history cache must distinguish unfetched from
   nonexistent records and carry coherent revision-qualified pages.
2. **Durable recovery:** original requests/random inputs and protected exposure
   obligations. Baseline replacement, cache eviction and Logout never silently
   discard these; preserve account isolation and original lifecycle fences.
3. **Disposable caches:** derived snapshots, refetchable catalog data and images.
   Retained essential per-game definitions/defaults are not disposable server data.
4. **Personal state:** drafts, review positions and appropriate UI restoration data,
   separate from authority and subject to account-private Logout rules.

## Migration boundaries

- Server: Flyway SQL against PostgreSQL; jOOQ generation checked against the migrated
  schema and actual query tests.
- Client: SQLDelight migrations checked against retained older SQLite schemas.
- Preserve request/outcome identities, recorded results and essential definitions;
  neither path may silently wipe pending obligations or reinterpret history.
- Database schemas may differ; semantic state and processing remain shared.

## Synchronization retention is not gameplay-history retention

The owner suggests a maximum retained revision count per game and requires fresh
baseline/full sync when the client's needed revisions are unavailable. A rolling
revision window is a **proposed mechanism**, not a selected count/age/byte budget or
permission to expire gameplay history. See the [baseline recovery proposal](sync-and-storage.md#bounded-revision-window-and-baseline-recovery--d-265).

Do not prune retained nodes/mutations, branches, Redo/exposure/reference obligations
or request outcomes merely because their synchronization deltas leave that window.
Checkpoints reduce transfer/replay cost, not the promised current-game review scope.
Finished-game archives remain outside R1; permanent retention/account erasure and
removed-record disposal remain separate service/privacy policy.

References: [SQLDelight multiplatform SQLite](https://sqldelight.github.io/sqldelight/latest/multiplatform_sqlite/),
[Room Multiplatform](https://developer.android.com/kotlin/multiplatform/room).
