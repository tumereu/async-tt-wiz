# Server boundary

JVM21 consumer of `:core` and `:protocol`. **I01c1 provides real PostgreSQL17,
forward-only Flyway migrations, an explicit executable migration command and
server-only generated jOOQ bindings. I01c2a adds the real database writer
ownership/epoch-fenced transaction boundary.** This is not a live service: Spring
Boot, runtime configuration, readiness and health are I01c2b; the application
image/lifecycle qualification is I01c3. No account/game/inbox/outbox schema or worker
exists yet. No ready endpoint or worker-recovery behavior is fabricated.

## Build and test

Prerequisites: the root [JDK21/Gradle/Android SDK foundation](../docs/agent/foundation-build.md),
an available local Docker daemon for **Testcontainers-owned** PostgreSQL, and public
Maven/image download access on first use. A developer/production database is never
used for generation or integration tests. These commands need no DB environment
variables and do not create/use the later I01e Compose environment:

```sh
./scripts/gradle.sh --no-daemon --console=plain :server:generateJooq
./scripts/gradle.sh --no-daemon --console=plain :server:verifyJooqGeneration
./scripts/gradle.sh --no-daemon --console=plain :server:clean \
  :core:jvmTest :protocol:jvmTest :server:test verifyModuleBoundaries
python3 scripts/check-server-foundation.py
python3 scripts/check-server-dependencies.py
```

`check-server-foundation.py` also expects the normal Android APK build directory
from the root foundation build (not a device run). The dependency check fetches
read-only public POM/license/image-index metadata; it is not a legal release gate.

The qualified versions are Flyway community **13.11.1**, Apache-licensed jOOQ
**3.20.20** (maintained JVM21 line), PostgreSQL JDBC **42.7.14** and Testcontainers
**2.0.5**, pinned in the root version catalog and ordinary strict server lock.
PostgreSQL **17.11-bookworm** is pinned by immutable multi-platform index digest in
`database-images.properties`; actual execution here is **amd64 only**, not arm64
or Pi qualification. Intentional lock maintenance uses the root documented
`--write-locks resolveAndLockAll` command; ordinary builds never rewrite locks.

`generateJooq` boots a disposable database, executes the same migration implementation
and resources as the real runner, introspects PostgreSQL with jOOQ, and generates
Java21 bindings into ignored `server/build/generated-src/jooq`. Main Java/Kotlin
compilation depends on that task. Bindings are not checked-in hand-written stubs.
Only the two dependency-independent migration support classes bootstrap into the
separate `schemaTools` source set; it is not packaged into the runtime distribution.
Do not add generated-type-dependent repositories to this bootstrap set.
`verifyJooqGeneration` regenerates using two independently owned databases and
compares every generated relative path/SHA-256. The tests use those generated
table/field types against the same versioned migrated schema, including committed
and rolled-back transactions.

No container reuse, shared database, hard-coded/shared host port or global Docker
cleanup is used. Restart tests reserve an OS-allocated port and give Docker an
explicit binding: otherwise Docker can change a random publication on stop/start
while Testcontainers caches the old address. Each generator/test owns its container and anonymous volumes, closes them
and checks their removal. Testcontainers' resource reaper covers interrupted JVMs.
Outputs identify the owned resources; collect evidence before cleanup on failure.
No unrelated container, network or volume may be stopped/removed.

## Explicit migration interface

Build the actual local application distribution:

```sh
./scripts/gradle.sh --no-daemon --console=plain :server:installDist
```

Run **only against the intended separately provisioned database**, with inputs
set in that command's environment:

```sh
DB_URL=jdbc:postgresql://127.0.0.1:5432/your_database \
DB_USER=your_database_user DB_PASSWORD_FILE=/path/to/mounted/password \
  server/build/install/server/bin/server migrate
```

Equivalent development task with the same environment:
`./scripts/gradle.sh --no-daemon --console=plain :server:migrate`.
There are no real credentials, default target database or production paths in Git.
The password file is UTF-8; one final LF/CRLF is removed, other whitespace preserved.
Credentials must not appear as JDBC URL query inputs. Successful commands print only
the applied-migration count; failures exit nonzero with a detail-free explanation.
Raw Flyway logging is disabled explicitly, independently of other logging providers,
because library errors can include connection inputs. Do not turn on raw SQL/JDBC
or environment logging when diagnosing a secret-bearing command.

The only CLI operation is `migrate`; no `clean`, `repair`, automatic baseline,
out-of-order migration or unsupported-future-schema exception is enabled.
An empty database receives `V1__service_writer.sql`; an unchanged supported database
is an idempotent rerun. History/checksums are validated. An unexpected nonempty
unversioned database, altered checksum or newer applied history fails closed without
destructive fallback. PostgreSQL transactional migration failures roll back that
migration's SQL/DDL and preserve previously supported data/history. Future upgrades
must add forward versioned migrations, not rewrite deployed V1.

`service_writer` contains a checked singleton key and nonnegative bigint epoch,
initialized to 0. Merely having this row confers no writer ownership.
The explicit migration command must remain separate from service startup, and I01c3
must package both interfaces in the same image.

## Database ownership boundary — I01c2a

`WriterOwnership` is a server-only closeable component, not a running service or
HTTP readiness implementation. The future Spring lifecycle must periodically call
`refresh()`, handle safe `OwnershipFailure` reasons as unready, and use `token()`
to probe existing ownership before reporting readiness. `refresh()` returns empty
when another instance owns the advisory lock; it does not wait for that owner.
It never calls migrate, creates history, baselines, cleans or repairs a database.
Acquisition validates Flyway history/checksums and requires no pending migrations;
empty, unversioned, future and invalid schemas remain unavailable and unchanged.

The dedicated lock connection holds the stable two-int PostgreSQL session advisory
lock `(0x41545457, 1)`. Acquisition takes `FOR UPDATE` on the singleton, increments
and commits the persisted epoch, then reloads/checks the only implemented durable
foundation state before publishing an opaque instance-bound token. A failed or
ambiguous acquisition releases its connection and publishes no token. No automatic
retry of a mutating transaction is performed.

All future mutating repositories must use `transaction(token, callback)` **before**
game/stream locks. It probes the dedicated session, starts a separate transaction,
takes `FOR SHARE` on the epoch row, checks the token and holds that shared lock
through callback and commit/rollback. Callbacks do local database work only: no
network calls, transaction-control statements, retained connections or autocommit
changes. Concurrent callbacks are not synchronized with one another. The replacement
writer's exclusive epoch update waits for existing shared locks; delayed stale
transactions cannot cross the new epoch even if their earlier session probe succeeded.
An unobserved DB restart with an unchanged stored epoch also cannot admit a transaction
under the destroyed lock session.

Observed DB/lock loss, a failed transaction or `close()` invalidates the token.
Recovery requires fresh ownership/schema validation and singleton reload, never a
cached ready flag. Failure reasons intentionally omit raw JDBC/Flyway errors, causes
and suppressed exceptions. Diagnostic strings contain no connection inputs. Connection
defaults override URL timeouts: connect 3 seconds, socket/statement 5 seconds, lock
2 seconds and idle-in-transaction 10 seconds. Callbacks must remain bounded local
work; these defaults are not a scheduler, a whole-request deadline or a guarantee
that arbitrary callback code is interruptible. Close is terminal/idempotent and
releases the dedicated connection without waiting for application callbacks; a
restart creates a new manager and epoch. Existing transactions still fence a
replacement until commit/rollback or their database timeout. Spring process drain
and actual SIGTERM/30-second qualification remain I01c2b/I01c3.

`WriterOwnershipTest` uses real owned PostgreSQL, test-owned probe tables and
lower-layer transactions, never product test-action endpoints. It observes actual
replacement row-lock waits in `pg_stat_activity`, synchronizes simultaneous shared
transactions with latches, terminates the lock backend, stops/restarts PostgreSQL
with retained storage, pauses/unpauses it to test socket bounds, and proves refusal,
rollback, stale-token fencing, revalidation, recovery and exact owned cleanup.
Run just this scope with:

```sh
./scripts/gradle.sh --no-daemon --console=plain :server:test \
  --tests org.asyncttwiz.server.database.WriterOwnershipTest verifyModuleBoundaries
```

No account/game/inbox/outbox/mail/push worker exists. Each future durable worker
must join recovery/readiness before the real service can accept commands; this
singleton-only foundation does not claim those workers have reloaded.

## Scope, evidence and remaining obligations

Sources: [runtime baseline and migration/container interface](../docs/design/04-technical/runtime-and-verification-profile.md#application-container-interface),
[writer persistence/locking](../docs/design/04-technical/storage-mechanics.md),
and the I01c1 outcome/checks in [implementation state](../docs/agent/implementation-state.json).
The coverage ledger's I01 foundation owns portions of **P-001, T-015, T-551,
T-630 and T-662**. This child supplies the real-PostgreSQL schema/migration/generation
part of T-630/T-662 only; no complete P/T scenario or F01–F88 UI behavior is claimed
executed by these lower-layer tests. T-015 replica recovery, T-551 full E2E reset,
normal-APK journeys and the other I01/I35 assertions remain pending.

The [I01c1 run](../docs/agent/implementation-runs/20261021T185205Z-6533f389.json)
retains exact clean build/test outputs, JUnit XML, generated source hashes,
licenses/index provenance and container/volume cleanup. Tests cover the installed
executable on clean/retained schemas, checksum/future/unversioned refusal,
transactional SQL/DDL rollback, generated queries/constraints and secret-file/error
redaction. The two initial tool-compatibility failures are retained as diagnostics,
not passing checks. An initial final-cleanup snapshot also caught Testcontainers'
Ryuk reaper before its asynchronous shutdown completed; the unchanged handoff check
then passed against a settled snapshot with no owned containers/reapers remaining.
B1 independent review remains after I01–I04 together; this child
does not close I01c, I01, B1, architecture/device qualification or any release gate.

The [I01c2a run](../docs/agent/implementation-runs/20261021T191440Z-b4076baa.json)
adds ownership/fencing evidence toward T-630/T-662, with the original port-restart
diagnostics retained. It does not close I01c2, its ancestors or any independent
review. Spring/HTTP/configuration/process and image checks are explicitly pending.
