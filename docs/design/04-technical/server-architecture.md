# Server architecture

## Status

**Recommendation under discussion:** Kotlin/JVM Spring Boot, PostgreSQL and Flyway
with versioned SQL migrations. The owner nominates these technologies and asks
whether better choices exist; do not yet treat them as final selections. Kotlin
Multiplatform for the client/shared core is selected (D-260). This document is an
initial architecture proposal, not a complete API/schema or implementation approval.

**Selected — D-261:** jOOQ for server database access. Client and server must use
the same Kotlin game-state objects and processing code; die/coin inputs may be
sampled on the client and accepted without an independent server reroll. Spring
Boot/PostgreSQL/Flyway remain the recommended candidates from the preceding
discussion; choosing jOOQ does not silently finalize those other choices.

## Technology assessment

| Area | Recommendation | Alternatives and tradeoff |
| --- | --- | --- |
| Backend | Kotlin/JVM Spring Boot modular monolith. Established integration for HTTP, security, transactions, observability and testing; must use the shared domain's JVM target under D-261. | Ktor is a credible lighter Kotlin-oriented alternative, but requires more explicit selection/integration of equivalent application infrastructure. No demonstrated requirement makes switching preferable here. |
| Database | PostgreSQL for transactional shared state, operation outcomes, constraints and notification outbox. | A document database adds no clear benefit for the cross-record atomicity required here. SQLite is suitable locally, not the proposed shared multi-user service database. |
| Migrations | Flyway versioned SQL, reviewed in Git and applied in version order. | Liquibase supports richer changeset metadata, preconditions and explicit rollback definitions, including SQL changelogs. Prefer it only if those capabilities justify the additional migration conventions; rollback definitions do not recover lost data automatically. |
| Data access | jOOQ selected (D-261), with explicit queries and transaction boundaries. | Spring JDBC/JPA/Hibernate are not the selected persistence abstraction. Do not introduce an ORM-owned game-state graph. |
| Request execution | Start with conventional Spring MVC and JDBC transactions. | WebFlux/R2DBC is not needed simply for realtime notifications; evaluate only against a concrete concurrency/resource requirement. |

Exact supported versions, compatibility, licenses/editions and runtime requirements
must be checked before pinning dependencies. No package installation is authorized.

## Proposed service boundaries

One deployable service, with internal modules for accounts/sessions, contacts and
decks, lobby/lifecycle, authoritative game commands/history, synchronization/result
lookup, catalog access and notification delivery. Do not introduce Kafka, Redis,
microservices or a separate event-store product without a demonstrated need.

Keep Spring controllers, repositories and jOOQ-generated records outside the shared
domain. Map database records to/from the same domain objects used on clients.
Use the same command processor, not just shared replay utilities or equivalent
fixtures, for actual server execution and client prediction. Only the service
authorizes requests, selects authoritative execution state and records committed
events/outcomes. Local samples are inputs subject to validation, not independently
authoritative client events; do not reroll accepted die/coin input. Recorded
randomness is an input to replay, never sampled again by replay logic. See
[the shared-processing boundary](client-architecture.md#shared-processing-and-locally-generated-random-inputs--d-261).

## Authority and transaction obligations

- Serialize applicable game/control/lifecycle processing per game, while allowing
  unrelated games to proceed independently. Define the arrival/order admission
  mechanism explicitly: row locks alone do not guarantee network arrival order.
- In one database transaction, persist each command's authoritative state/history
  changes, operation outcome, relevant control-stack changes, sync revision and
  required outbox work. No acknowledgement before durable commit under the defined
  service recovery model.
- Each individual bulk constituent remains its own operation/outcome. A database
  transaction must not turn an entire gesture's sequence into an atomic bulk node.
- Delivery retries return the original outcome, including recorded random results;
  they do not execute another Undo, clear newer Redo entries or mutate fresh play.
- Database isolation/locking protects current-state processing; it is not an extra
  stale-whole-head rejection guard on ordinary actions.
- Publish realtime updates only from committed data. Reconnect recovers durable
  revisions/outcomes; a socket message is not the only record of an accepted action.
  Notification failures do not roll back gameplay.

The [contracts](../03-contracts/README.md) and
[sync/storage design](sync-and-storage.md) remain primary for exact outcomes.
The editable gameplay tree and accepted publication revisions are distinct;
PostgreSQL storage must not impose an immutable-gameplay-tree interpretation.

## Migration and deployment proposal

Use one migration history for production and real-database tests. Never silently
edit an applied versioned migration or let an ORM auto-update production schema.
Test both empty-database installation and supported upgrade paths. Prefer forward
repairs and expand/contract changes compatible with supported app/server versions;
backups and restore procedures remain necessary. Large backfills, lock budgets,
nontransactional PostgreSQL DDL and migration-runner/deployment ownership need
explicit procedures rather than assuming every migration is atomically reversible.

Generate jOOQ schema bindings from the migrated schema in a disposable build/test
database, not a developer's mutable production connection. Keep generated schema
types server-only and verify regeneration/queries against the migration history.
Exact code-generation integration and versions remain technical design.

## Still to design

Ordering/admission and locking, relational schema and payload representation,
idempotency retention, API/realtime protocol, authentication/session mechanisms,
backup/restore, deployment topology, supported versions and operational budgets.
See [cross-cutting architecture work](architecture-and-operations.md).

References: [Spring Kotlin support](https://docs.spring.io/spring-boot/reference/features/kotlin.html),
[PostgreSQL locking](https://www.postgresql.org/docs/current/explicit-locking.html),
[Flyway versioned migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/versioned-migrations),
[Liquibase SQL changelogs](https://docs.liquibase.com/concepts/changelogs/sql-format.html),
[Ktor](https://ktor.io/docs/server-create-a-new-project.html).
