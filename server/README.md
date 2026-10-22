# Server boundary

JVM21 consumer of `:core` and `:protocol`. **I01c1 provides real PostgreSQL17,
forward-only Flyway migrations, an explicit executable migration command and
server-only generated jOOQ bindings. I01c2a adds the real database writer
ownership/epoch-fenced transaction boundary; I01c2b supplies the real Spring Boot
service, separate public/health listeners, runtime configuration and bounded process
shutdown/recovery. I01c3 adds the local application OCI image and isolated image
lifecycle qualification.** No image is published or deployed by these interfaces.
I02a adds the real account/session schema and public account API described below.
I04a adds password-reset tokens, an encrypted durable mail outbox, SMTP worker and
secure browser fallback form. No game/inbox/game-outbox/push worker exists yet.

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
Spring Boot **4.1.1**, Spring Framework **7.0.9**, Tomcat **11.0.24**, SLF4J **2.0.18**
and Logback **1.5.38** are qualified on JVM21. The Spring BOM is a regular platform,
not permission to upgrade the already-qualified jOOQ **3.20.20** runtime: its strict
constraint keeps runtime and generator on the same line. Public POM license evidence
records Apache-2.0 Spring/Tomcat, MIT SLF4J and dual EPL-2.0/LGPL-2.1-only Logback;
this is dependency provenance, not completed redistribution notices or legal clearance.
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
An empty database receives V1 service writer, V2 accounts/sessions and V3 password
reset/mail migrations; an unchanged supported database
is an idempotent rerun. History/checksums are validated. An unexpected nonempty
unversioned database, altered checksum or newer applied history fails closed without
destructive fallback. PostgreSQL transactional migration failures roll back that
migration's SQL/DDL and preserve previously supported data/history. Future upgrades
must add forward versioned migrations, not rewrite deployed V1.

`service_writer` contains a checked singleton key and nonnegative bigint epoch,
initialized to 0. Merely having this row confers no writer ownership.
The explicit migration command must remain separate from service startup, and I01c3
must package both interfaces in the same image.

## Live service interface — I01c2b

The same installed distribution runs either `server migrate` or `server serve`.
There is no implicit default command, automatic service-start migration, or alternate
test application. After the explicit migration above, run against the intended local
database using file-mounted credentials:

```sh
DB_URL=jdbc:postgresql://127.0.0.1:5432/your_database \
DB_USER=your_database_user DB_PASSWORD_FILE=/path/to/mounted/password \
PUBLIC_BASE_URL=http://127.0.0.1:8080 \
SERVER_ADDRESS=127.0.0.1 SERVER_PORT=8080 \
HEALTH_ADDRESS=127.0.0.1 HEALTH_PORT=8081 \
  server/build/install/server/bin/server serve
```

`DB_URL`, `DB_USER`, `DB_PASSWORD_FILE`, `PUBLIC_BASE_URL` and the reset-mail inputs
[below](#password-reset-httpmail-v1--i04a) are required by `serve`. The latter URL
is an absolute HTTP(S) base URL without user info/query/fragment, retained for future
public link generation; this foundation does not fetch it or implement account links.
The UTF-8 secret must be a readable regular file, at most 64 KiB, with a nonempty
password after removing **one** final LF/CRLF; other whitespace/Unicode is preserved.
Malformed/missing inputs fail promptly with exit 1 and a fixed safe message, never
raw values, file paths, JDBC/SQL exceptions or Spring configuration dumps.

Listener defaults are `SERVER_ADDRESS=0.0.0.0`, `SERVER_PORT=8080`,
`HEALTH_ADDRESS=127.0.0.1`, `HEALTH_PORT=8081`. Ports are distinct integers 1–65535;
both addresses are configurable. For later container tests configure health binding
explicitly to the owned container network; loopback is the safe local default.
Each listener is a real, explicitly configured Spring Boot servlet context/Tomcat
server. No database/Actuator/MVC auto-configuration or general Spring configuration
channel is enabled. `SPRING_*`, arbitrary external application property files and
forwarded headers cannot redirect listeners or expose environment/credentials.
TLS/proxy trust remains external; no production deployment configuration is supplied.

Only the **health listener** serves GET/HEAD `/health/live` and `/health/ready`:

- Live: HTTP 200, `OK\n`, independent of database availability/schema/ownership.
- Ready: HTTP 200 `OK\n` only after ownership, supported schema validation and
  singleton reload; otherwise HTTP 503 `Unavailable\n`. Every readiness request
  probes the dedicated session, not a cached successful poll. All responses are
  plain text and `Cache-Control: no-store`, without data/configuration detail.
- Unknown paths, including both health paths on the **public listener**, return
  HTTP 404 `Not found\n`. I02a's account API is public-listener-only; there are no
  test-action endpoints. Known account paths use JSON method/error responses.

`ServiceRuntime` refreshes/retries ownership every 500 ms after the preceding bounded
attempt finishes. A competitor stays live but unready. DB/lock loss invalidates
readiness/admission; reacquisition must revalidate schema, advance and reload the
persisted singleton epoch. Empty/unversioned/future/checksum-invalid schemas stay
live/unready **and unchanged**, never migrated or repaired. External migration is
required before recovery can succeed. Runtime/library logging is deliberately quiet
via fixed `service-logback.xml` and safe fixed command messages; do not enable raw
JDBC/Flyway/Spring debug logging with real secret inputs.

All future mutating handlers must enter `ServiceRuntime.transaction()` and the
existing epoch fence, never use an HTTP-ready boolean as authorization to write.
SIGTERM first stops readiness/admission, allows already-admitted local transactions
up to 10 seconds to drain, releases ownership and closes both contexts (5-second
graceful shutdown phases). Actual healthy and database-blackhole process exits are
tested within the **30-second** contract, along with exact listener rebind/session
cleanup and restart with retained epoch. Future callbacks remain bounded local DB
work; no shutdown guarantee is made for arbitrary uninterruptible application code.

**Implemented worker inventory:** ownership recovery and the I04a durable reset-mail
sender. Recovery validates pending encrypted mail with the configured key before
readiness/admission; a wrong key stays unavailable without deleting records. SMTP
outage does not make the whole service unready: due mail retries durably. There are
no game/inbox/game-outbox/push workers. Each future worker must join readiness,
recovery and bounded shutdown before its routes are admitted. Historical foundation
qualification below predates account/mail implementation; it is not current product
coverage.

Focused real-process/admission checks (Linux signals, Testcontainers-owned PostgreSQL):

```sh
./scripts/gradle.sh --no-daemon --console=plain :server:test \
  --tests org.asyncttwiz.server.ServiceProcessTest \
  --tests org.asyncttwiz.server.ServiceRuntimeTest \
  --tests org.asyncttwiz.server.ServiceSettingsTest \
  --tests org.asyncttwiz.server.SpringStartupTest verifyModuleBoundaries
```

Process tests use temporary secret files, reserve OS-allocated ports on their eventual
loopback bind address, and retain safe subprocess output. This WSL host failed
wildcard-reservation→explicit-loopback rebinding even in minimal JDK NIO; matching
addresses fixes the fixture. Positive HTTP qualification uses `127.0.0.1`; `127.0.0.2`
timed out on this host, not an application-wide guarantee of all host network aliases.
The WSL host relay may accept TCP after the JVM exits: cleanup therefore proves that
both **exact address/ports can be rebound**, and checks process exit and database
sessions/locks, rather than mistaking relay behavior for an owned listener.

## Database ownership boundary — I01c2a

`WriterOwnership` is a server-only closeable component used by `ServiceRuntime`, not
an independent service. The Spring lifecycle periodically calls
`refresh()`, handles safe `OwnershipFailure` reasons as unready, and uses `token()`
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
replacement until commit/rollback or their database timeout. I01c2b qualifies Spring
process drain/SIGTERM; the local image suite separately checks the packaged process.

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

The current reset-mail worker also joins recovery/readiness. Each future durable
game/push worker must do the same; older singleton qualification is not their test.

## Local application image — I01c3

Prerequisites are the same clean-build toolchain and local Docker daemon above,
plus Python3 stdlib. Build from the repository root:

```sh
python3 scripts/build-server-image.py
python3 tests/server/image_lifecycle.py
```

The build uses ordinary incremental Gradle tasks/strict locks, regenerates affected
jOOQ inputs against an owned migrated PostgreSQL17 and installs the normal distribution.
Use `--clean` only for final reproducibility qualification: it cleans `:core`,
`:protocol` and `:server` and disables the build cache. Neither mode skips the
source-to-distribution build. A small allowlisted context
contains only its runtime JARs and the checked Dockerfile/ignore rules: no checkout,
Gradle cache, test/code-generation tooling, credentials or host configuration.
It then executes `docker build --pull --network=none --platform linux/amd64
--iidfile ...` with source/distribution labels. No Docker socket is mounted in a
builder. The output is **local only**, without a registry tag/push/deployment:

- `server/build/application-image.id`: exact local OCI image identity, not a
  mutable tag. BuildKit metadata/attestation may give a different ID on rebuild;
  the recorded per-JAR/generated-schema hashes identify the actual payload.
- `server/build/application-image.json`: platform, immutable base, starting source
  commit, Dockerfile/distribution/runtime-JAR and generated-schema hashes. A build
  during an uncommitted unit records the starting commit, not a claim those new
  inputs already belong to that commit. Preserve this JSON with qualification
  output before cleaning `server/build`.

`server/Dockerfile` pins Eclipse Temurin **21.0.12.1+1 JRE / Ubuntu 24.04** by
multi-platform index digest `sha256:35501d3f93173b420ac9001658eb68157e813316b865087e345c187fca422c4c`.
The source revision/platform manifests are retained in this run's preflight output;
OpenJDK's included legal notices and actual runtime version are inspected in the
image tests. This is dependency provenance, not completed redistribution notices,
provider/art rights or release clearance. Deliberate base updates require
requalification; neither a floating tag nor an assumed patch upgrade is used.

The runtime is numeric **UID/GID 10001**, with root-owned non-writable application
JARs. The direct exec-form Java entrypoint is PID1 and requires exactly `serve` or
`migrate`; there is **no implicit default command or automatic migration**. No
shell/certificate-entrypoint work runs before Java. The unchanged base trust store
is used; this interface does not promise private-CA injection. No service credential
or database target is baked into image configuration/layers. The tests export the
actual filesystem and match every packaged JAR to the locked distribution hashes.

Example **local development** commands against a separately intended local Docker
network/database (replace all named inputs; these are not production orchestration):

```sh
IMAGE_ID=$(cat server/build/application-image.id)
docker run --rm --network "$LOCAL_NETWORK" --read-only --cap-drop=ALL \
  --security-opt=no-new-privileges --tmpfs /tmp:rw,nosuid,nodev,size=64m,mode=1777 \
  --mount "type=bind,src=$LOCAL_PASSWORD_FILE,dst=/run/secrets/db-password,readonly" \
  --env "DB_URL=$LOCAL_JDBC_URL" --env "DB_USER=$LOCAL_DB_USER" \
  --env DB_PASSWORD_FILE=/run/secrets/db-password "$IMAGE_ID" migrate
docker run --name "$LOCAL_SERVICE_NAME" --network "$LOCAL_NETWORK" --read-only \
  --cap-drop=ALL --security-opt=no-new-privileges \
  --tmpfs /tmp:rw,nosuid,nodev,size=64m,mode=1777 \
  --mount "type=bind,src=$LOCAL_PASSWORD_FILE,dst=/run/secrets/db-password,readonly" \
  --env "DB_URL=$LOCAL_JDBC_URL" --env "DB_USER=$LOCAL_DB_USER" \
  --env DB_PASSWORD_FILE=/run/secrets/db-password \
  --env "PUBLIC_BASE_URL=$LOCAL_PUBLIC_BASE_URL" --env HEALTH_ADDRESS=0.0.0.0 \
  --publish 127.0.0.1::8080 --publish 127.0.0.1::8081 "$IMAGE_ID" serve
docker port "$LOCAL_SERVICE_NAME"
docker stop --timeout 30 "$LOCAL_SERVICE_NAME"
docker rm "$LOCAL_SERVICE_NAME"
```

The password path must be absolute, a regular UTF-8 file readable by UID10001
(use appropriate local group/ACL/mode; do not make real credentials world-readable).
The filesystem can be read-only with bounded writable `/tmp`. Listener inputs are
unchanged: health defaults to container **loopback**, so opt into
`HEALTH_ADDRESS=0.0.0.0` only on the intended private network. The development
examples publish dynamically assigned **host-loopback-only** ports; public API and health are
distinct ports. A Docker EXPOSE declaration does not publish a host port. No Docker
healthcheck is baked in: use the actual `/health/live` and `/health/ready` contracts
on the health port, not public-port or process-only guesses.

The image test suite provisions a UUID-labelled **internal** Docker network,
explicitly named private volume and pinned PostgreSQL17. It is separate from both
Testcontainers and the later I01e reusable Compose environment. Every scenario
gets a fresh database; in-scenario service/DB restarts retain its volume/data.
The suite uses actual HTTP from the local Linux/WSL Docker host directly to its
private bridge IP, with **no published host ports** and no network egress. Docker29
suppresses port mappings on an internal network; a Docker Desktop/remote-daemon
host without direct bridge access needs its own qualified test adapter, not a
silent change to external networking. The development examples above are for an
intended local network where explicit loopback publication is available.
It tests real image commands, schema refusal without repair, private file secrets
(including CRLF, unreadable and malformed input), nonroot/read-only execution,
public/health HTTP isolation, writer competition/takeover, DB outage and recovery
schema revalidation, <=30s actual SIGTERM (including DB blackhole), and retained
singleton epoch restart. There are no user-action/test-only product endpoints.
Safe output is captured **before** targeted cleanup, including on assertion failure;
cleanup verifies exact owned containers/network/volume and temporary secrets are
removed, and pre-existing unrelated resources are left intact. Built/base image
caches are deliberately retained for subsequent local work, not pruned globally.

Only **linux/amd64** execution is qualified here. The build can select
`--platform linux/arm64` when that capacity exists, but building/selecting a base
manifest is not arm64 execution qualification. The amd64-only suite explicitly
refuses to certify arm64. Full mixed-version/architecture/runtime/device delivery
qualification remains I35; physical performance remains I33 and external release
gates remain unchanged. Only the ownership recovery scheduler/singleton reload
exists: no account/game/inbox/outbox/mail/push worker recovery is claimed.

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
review. Its Spring/HTTP/configuration/process obligation is now supplied by
[I01c2b](../docs/agent/implementation-runs/20261021T192847Z-0d517a87.json), with clean
and repeated JVM/database/process regression evidence and retained development
diagnostics.

The [I01c2 combined qualification](../docs/agent/implementation-runs/20261021T195803Z-2deb1ecd.json)
closes that service/ownership parent after both children: two complete 34-test
server runs without skipped/failing cases, shared JVM tests and independent
byte-identical jOOQ regeneration. The second run cleans/recompiles `:core`,
`:protocol` and `:server` with the build cache disabled and ordinary strict locks.
Actual installed Spring processes, migration separation, schema refusal,
two-writer takeover, DB outage/blackhole recovery, epoch fencing/row-lock waits,
configuration redaction and bounded SIGTERM/restart pass together without product
changes. Exact command outputs, JUnit XML, dependency provenance and owned cleanup
are retained in that run. This is implementation qualification, **not independent
B1 review** or full T-630/T-662 acceptance. At that checkpoint, image I01c3,
I01c/I01 combined parent qualification, Compose/device work, full product/architecture
acceptance and all external release gates remained open.

The [I01c3 image qualification](../docs/agent/implementation-runs/20261021T200623Z-d58cf56d.json)
now closes the image child only: two clean locked builds with matching runtime and
generated-schema hashes, two complete four-scenario local amd64 OCI lifecycle runs,
and all 34 service/database regressions pass. Exact image/base identities, runtime
legal/version inventory, JUnit XML, original networking/cache diagnostics and owned
cleanup are retained. The repeated image's DB-blackhole SIGTERM exits in 10.374s
(under 30s). No image is published/deployed. I01c/I01 parent qualification, B1 review,
normal app/Compose/device/product/arm64 acceptance and release gates remain open.

The [I01c combined qualification](../docs/agent/implementation-runs/20261021T202050Z-3bc861e9.json)
closes the database/service/image foundation parent after all three children.
The documented clean, cache-disabled, strictly locked image build and complete
34-test server/database suite plus six shared JVM tests pass, including independent
byte-identical jOOQ regeneration. The tested installed runtime JARs match the actual
image payload. Two complete four-scenario native amd64 image suites qualify the
same explicit migrate/service interfaces, schema refusal, configuration/redaction,
health, writer loss/takeover/reacquisition and retained restart together. The
repeated image's DB-blackhole SIGTERM exits in 10.407s; the installed-process maximum
is 10.054s (both below 30s). Exact identities, actual JUnit XML, dependency provenance,
coverage accounting and owned cleanup are retained; pre-existing unrelated resources
are unchanged. No product/build inputs were changed in this parent unit. This is
implementation qualification, not independent B1 review or full acceptance:
I01's SQLDelight/Compose/device/combined qualification, full product/arm64 checks
and external release gates remain open. Future account/game/inbox/outbox/mail/push
workers remain unimplemented and must join readiness/recovery/shutdown when added.

## Account HTTP v1 — I02a

The normal public listener serves these stateless JSON routes through the Spring
Security web adapter. TLS terminates at the external trusted transport/proxy;
plain HTTP below is only for isolated local synthetic development. Never publish
credentials over untrusted plaintext connections. Forwarded headers are not trusted.
No cookie login, HTTP session, guest identity, JWT, refresh token or saved password.

| Method/path | Request | Success |
| --- | --- | --- |
| `POST /accounts` | `{"username":"…","email":"…","password":"…"}` | 201 `{"accountId":"UUID","username":"normalized display"}`; registration does **not** issue a session |
| `POST /sessions` | `{"email":"…","password":"…"}` | 201 `{"token":"opaque bearer","account":{"accountId":"UUID","username":"…"}}` |
| `GET /account` | `Authorization: Bearer …` | 200 current safe `AccountIdentity`; no email or other-account selector |
| `DELETE /session` | `Authorization: Bearer …` | 204; revokes only this token, repeated/unknown well-formed tokens also return 204 |

Shared types/strict JSON live in `protocol/.../Accounts.kt`. Bodies must be
`application/json` (optional UTF-8 charset), at most 8192 bytes and strictly valid
UTF-8/Unicode scalars. Unknown fields/type errors/missing fields are rejected.
Responses are `Cache-Control: no-store`; credentials are body/header-only, never
query parameters or logs. Unsupported known methods return 405 with `Allow`.
Unknown routes keep the foundation's detail-free 404. No email is included in
`AccountIdentity`, which is also the safe projection for future social features.

Errors are `{"code":"…","field":"username|email|password"}`; `field` is optional.
The Android adapter should translate stable codes into useful local text, never
render raw exception/body text as an error. Retain entered values for correction
only in volatile UI memory; do not persist passwords. Codes/statuses:

- 400 `invalid_field` (with field) or `invalid_request`; no submitted value echoed.
  Email supports ASCII dot-atom local parts (max 64 characters), not SMTPUTF8 or
  quoted/comment mailboxes. Explain that restriction on invalid email input.
- 409 `account_unavailable`: username or email cannot be used; no owner disclosed.
- 401 `invalid_credentials`: generic email/password mismatch, including nonexistent
  email; a dummy Argon2id verification follows the same costly path.
- 401 `authentication_required`: missing/malformed/unknown/revoked bearer on private
  reads. Ask for login; do not silently save/fall back to the reusable password.
- 413 `request_too_large`; 415 `json_required`; 405 `method_not_allowed`.
- 429 `try_later` plus bounded `Retry-After`: registration 5/hour/actual peer IP;
  login 10/min/IP and 5/min/normalized email key. Fixed windows are bounded to 10,000
  hashed keys, process-local, recover after their window; service restart resets
  rate counters, **not sessions**. No permanent account lockout or XFF bypass.
- 503 `authentication_busy` plus `Retry-After: 1` if both hash permits are occupied;
  503 `unavailable` on writer/database/schema admission failure. Retry normally,
  not as proof that an earlier response was committed or rejected.
- 500 `internal_error`: fixed safe fallback, no driver/parser/security details.

Credential semantics follow the [auth contract](../docs/design/04-technical/authentication-and-sessions.md):
username trims Unicode boundary whitespace, NFC display and full Unicode default
case-fold key, pinned **ICU4J 74.2 / Unicode 15.1**, 1–64 scalars and no control/bidi
controls. Email trims independently, ASCII-local case-fold plus non-transitional
UTS46/IDNA ASCII lowercase domain, preserves delivery spelling, max 254 UTF-8 bytes
and 254 ASCII transport bytes. No dot/plus alias guesses. Passwords are exact
8–256 scalar / ≤1024-byte UTF-8 strings, including whitespace/newlines/Unicode;
no trim, NFC, case-fold, replacement or truncation.

Spring Security **7.1.1** (Boot BOM), Argon2id v19 **64 MiB / t3 / p1**, random
16-byte salt and 32-byte result store PHC verifiers, using Bouncy Castle **1.84**.
Two immediate hashing permits bound concurrent RAM usage; saturation queues no
additional hash work. Lower-cost verifiers rehash after successful login. Hashing
occurs outside database transactions; reads and writes enter the existing fenced
runtime boundary. V2 adds unique canonical account keys, account auth epoch and
independent session IDs. Tokens are 32 secure random bytes encoded as canonical
43-character base64url without padding; only SHA-256 verifiers persist. No secret
response replay store, token expiry/age check or periodic eviction exists. New
private requests join session/account epochs and revocation state, not a cached
connection login. I04a implements actual password reset/epoch increments below;
ordinary already-authorized work need not be retrospectively fenced (D-289).

Focused development checks (ordinary incremental builds):

```sh
./scripts/gradle.sh --no-daemon --console=plain :protocol:jvmTest :server:test \
  --tests 'org.asyncttwiz.server.accounts.*' \
  --tests org.asyncttwiz.server.database.FoundationDatabaseTest \
  --tests org.asyncttwiz.server.ServiceRuntimeTest \
  --tests org.asyncttwiz.server.SpringStartupTest verifyModuleBoundaries
python3 scripts/build-server-image.py
python3 tests/server/accounts_image.py
```

The image check reuses isolated UUID-owned network/database/cleanup helpers but runs
only the affected real account journey, not the unchanged full lifecycle matrix.
Normal Android A01/A02/Keystore integration is I02b; Menu/Games/Logout private-state
cleanup is I03; email reset/multi-device entry is I04; contacts/game authorization
and privacy remain I09/I12/I13/I14. Full-suite/compatibility/UI comparison remain
I34/I35/I36/B8. B1 is not independently reviewed until I01–I04 together; external
release gates remain open. These API checks are partial evidence for P-043/P-047,
T-396/T-419/T-420/T-422/T-572/T-573/T-621/T-657 and future feature integration, not
full-scenario or UI-feedback passes.

## Password reset HTTP/mail v1 — I04a

The public listener adds these unauthenticated **JSON** routes, using the same
8192-byte strict UTF-8/JSON boundary and safe `AccountError` format as account entry:

| Method/path | Request | Success |
| --- | --- | --- |
| `POST /password-reset-requests` | `{"email":"…"}` / `RequestPasswordReset` | 202, empty body, identical for known/unknown email; no session changes |
| `POST /password-resets` | `{"token":"…","password":"…"}` / `ResetPassword` | 204, empty body; password changed, **no login token issued** |

Use the existing exact password grammar and email canonicalization, including IDNA
domains. Confirmation errors: 400 `invalid_reset_link` for invalid/expired/used
tokens; 400 `invalid_field` with `password` for invalid password input. Existing
safe JSON/media/size/busy/unavailable errors apply. Reset requests are bounded at
20/hour/actual peer and 3/hour/canonical email (also unknown email); confirmation
at 20/minute/peer plus the shared process-wide **two** Argon2 permits. 429 carries
`Retry-After`. Counters are bounded process-local windows, not account lockout.
Do not infer delivery or successful password change from a request's 202.

V3 stores random 32-byte canonical base64url token verifiers (SHA-256), creation,
30-minute expiry and consumption. Multiple links remain usable concurrently.
Only successful confirmation, under an account-row lock in **one writer-fenced
transaction**, changes PHC, increments auth epoch, revokes all current sessions,
consumes every outstanding token and deletes/cancels pending token-bearing mail.
Failed/expired/reused links, request/delivery and GET never revoke sessions. New
requests using old sessions then fail normally; already-authorized work may finish
(D-289). Do not add reset-vs-login/admitted-game serialization machinery.

### Required `serve` configuration

In addition to DB/listener inputs, supply:

- `RESET_BASE_URL`: explicit **HTTPS origin**, no credentials/path/query/fragment.
  Emails contain exactly `<origin>/reset-password?token=<43-char token>`. Android
  I04b must configure and validate this origin/path, extract only a canonical token,
  and open its native A04 form on cold/warm intents; opening is not confirmation.
- `RESET_MAIL_KEY_FILE`: readable regular UTF-8 file, max 64 KiB, containing canonical
  base64 for **32 random bytes**. One terminal LF/CRLF is removed. Keep it external
  and stable across service restarts/upgrades while pending mail exists. Loss/wrong
  key blocks readiness, not destructive recovery. Do not reuse a DB or SMTP password.
- `SMTP_HOST`, `SMTP_PORT`, `SMTP_FROM` (ASCII transport mailbox), `SMTP_TLS`:
  `starttls` (required upgrade, never opportunistic), `tls` (implicit TLS), or
  **`local-plaintext` only for isolated synthetic/local development**, never a public
  mail host. TLS uses normal JDK trust and hostname verification; no trust-all mode.
- Optional paired `SMTP_USERNAME_FILE` and `SMTP_PASSWORD_FILE`, same bounded UTF-8
  file contract; password whitespace is exact except one final line ending. They
  must occur together and are forbidden in `local-plaintext`. No credential values
  in environment variables, logs, URLs or image layers. Missing/invalid required
  configuration fails `serve` promptly with its fixed safe message; `migrate` still
  needs only DB configuration. Embedded foundation tests can omit mail explicitly;
  the real CLI has no implicit disabled-mail fallback.

For local synthetic development add the isolated sink host/port, explicit
`SMTP_TLS=local-plaintext`, synthetic From, HTTPS `.invalid` reset origin and a
generated **external** key-file mount to the example service commands above.
The normal [E2E Compose environment](../e2e/README.md) already supplies these inputs.
It does not deploy an HTTPS frontend or provide production SMTP/operator readiness.

### Durable delivery and browser fallback

The single bounded worker polls every 500 ms, up to eight due messages per poll.
SMTP (Eclipse Angus Jakarta Mail **2.0.5**) uses 3-second connect/read/write limits,
no library debug and no raw delivery errors. Payloads contain only the transport
recipient and token, encrypted using AES-256-GCM, fresh 96-bit nonce and token-row
UUID as associated data. Randomness, token verifier and encrypted mail insert are
atomic. Recipient local spelling comes from the stored delivery address; its domain
is IDNA ASCII for SMTP. No SMTPUTF8 requirement or commercial provider is introduced.

Before sending, persist attempt/backoff: temporary/network failure retries after
**1, 5, then 15 minutes**, repeating the bounded final delay until token expiry.
5xx permanent rejection ends retries. Success, permanent failure, expiry or successful
reset removes the encrypted token-bearing payload. A crash after SMTP acceptance
but before DB acknowledgement may repeat the **same** email/link; it cannot issue
another token or reset a password. No DB transaction holds network I/O. Recovery
validates pending ciphertext before readiness; HTTP routes check readiness and
every DB mutation still enters the epoch fence. Close stops admission/polling and
retains uncertain/pending mail for restart, not an unbounded flush.

`GET /reset-password?token=…` serves a self-contained password form for a usable
link; GET without a query serves Request another link. Invalid/expired/used links
have no actionable password form and link to that request route. The fallback uses
no scripts/third-party assets, `Cache-Control: no-store`, `Referrer-Policy:
no-referrer`, restrictive CSP and `nosniff`. Tomcat access logging is not enabled;
the external HTTPS proxy must also omit/redact reset queries and mail bodies.

Browser confirmation is explicit URL-encoded `POST /reset-password` with token,
password and HMAC CSRF hidden field bound to a fresh Secure/HttpOnly/SameSite=Strict
cookie. The form posts to a query-free same-origin path; foreign Origin or missing/
invalid cookie/CSRF is rejected. Browser `POST /reset-password/request` uses the same
protection for email requests. Success instructs returning to the app to sign in;
it never auto-logs in. JSON API is stateless/header/body-only, with no cookie auth.

Affected checks (incremental, not the full suite):

```sh
./scripts/gradle.sh --no-daemon --console=plain :server:test \
  --tests 'org.asyncttwiz.server.accounts.PasswordReset*'
python3 scripts/build-server-image.py
python3 tests/server/password_reset_compose.py
python3 -m unittest discover -s tests -p 'test_*environment.py' -v
```

These cover real PostgreSQL, actual synthetic SMTP, controlled-clock long backoff/
expiry, HTTP/CSRF/form and local OCI/Compose delivery/restart/new-store reset. They
are partial I04.reset evidence for P-047/P-066/T-421/T-572/T-621/T-656/T-657/T-666,
not full cross-feature scenarios or Android F04–F06 passes. I04b retains native
A03/A04, configured deep links and the two-device journey; B1 review still follows
I01–I04. I34 retains broad/final suite plus positive local TLS/auth transport
qualification; real external HTTPS/SMTP credentials/delivery and proxy logging
readiness remain operator-owned, not claimed by a plaintext synthetic sink.
