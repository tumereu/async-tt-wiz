# I01c combined qualification result

I01c is implemented after I01c1, I01c2 (and both of its children) and I01c3.
No product source, runtime resource or build/configuration input changed. This
parent closes only their unchanged combined foundation outcome, not I01 or B1.

## Executed evidence

- `clean-image-build.json`: the documented image command cleans core/protocol/
  server, disables the build cache, uses ordinary strict locks, regenerates real
  jOOQ against owned migrated PostgreSQL17 and builds the normal distribution.
  Base/PostgreSQL pins, image identity and per-JAR/generated-source hashes are
  retained. Local amd64 image:
  `sha256:e9f6426cf5942b61b8c894239c05a3d45414c4fa328044db60aebeddecb7e107`;
  runtime distribution SHA-256:
  `028abe92dfd0b42ca137efe25c137869674a47b45672a8dd790f40ae3fea3563`.
- `service-regressions.json`: **34 server/database tests** execute without
  skips/errors/failures, plus **3 core and 3 protocol JVM tests**. Independently
  migrated databases produce byte-identical generated sources. Installed migration
  CLI/service processes test explicit clean/retained/idempotent migration, SQL/DDL
  rollback, generated queries/constraints, unsupported/checksum/unversioned schema
  refusal without repair/auto-migration, separate detail-free HTTP health,
  file-secret/redaction/configuration, two writers and DB outage/recovery. Lower
  layers prove stale-token fencing, observed epoch replacement waits through old
  commit/rollback, simultaneous shared transactions, admission stop/drain and
  rollback. Ten actual process SIGTERM exits are below 30s (maximum **10.054s**).
- `image-lifecycle.json` and `repeated-image-lifecycle.json`: **4/4 scenarios
  pass twice** on the same exact built image. Actual image filesystem export matches
  every locked runtime JAR; no source/codegen/test tooling/credential payload.
  UID/GID10001, read-only runtime, Java21 version/legal inventory, explicit fresh/
  retained migration, empty/unversioned/future/checksum/missing-singleton refusal,
  separate public/health ports, detail-free health and safe secret failures pass.
  Two image processes exercise writer takeover, DB stop/start, incompatible-schema
  recovery refusal and revalidation/epoch reload. Actual PID1 SIGTERM and retained
  same-container/DB restart pass; repeated paused-DB shutdown is **10.407s**.
- `server-artifacts.json`: actual server JUnit XML, Java21 classfile/generated
  types, strict pins/locks, approved test paths and runtime/module boundaries.
  `unit-validation.json` additionally retains shared JUnit XML and checks that
  the tested installed runtime and all generated bindings match the image build.
- `dependency-provenance.json`: exact pinned Spring/Flyway/jOOQ/JDBC/Testcontainers
  versions and public license/image-index metadata, not release/legal clearance.
- `coverage-validation.json`: all 875 P/T/F primary definitions retain explicit
  ownership/disposition and pending partial assertions; no full product scenario
  is relabeled as executed. Superseded and conditional outcomes remain intact.
- `preflight.json` / `final-cleanup.json`: exact unrelated resource inventory
  preserved, owned image/PostgreSQL/Testcontainers/reaper containers, networks,
  volumes, temporary secrets and installed service processes cleaned. Image caches
  remain local; no image publication, deployment, Compose or global cleanup.

Required commands and their byte-preserved outputs are listed in the
[run report](../20261021T202050Z-3bc861e9.json). Both suites were also run
concurrently once with distinct database/resource ownership; neither shares reset
state. The second image suite is a separate complete invocation.

## Remaining obligations

Only ownership recovery/singleton reload exists; no account/game/inbox/outbox/mail/
push worker recovery or product API is claimed. Future workers must join readiness
and shutdown when introduced. I01 storage, reusable Compose reset, Android/device
adapter and whole-foundation qualification remain pending; so do independent B1
review, full P/T/F acceptance, I35 arm64/device/upgrade compatibility, I33 physical
performance and every external release gate. No UI document/publication changed.
