# I01c2 combined outcome

Both children are implemented, and their combined parent outcome passes without
changing product source, runtime resources, migrations, dependencies or build inputs.
Only I01c2 advances to `implemented`; I01c and I01 remain `in-progress`, B1 remains
`pending` for I01–I04, and all independent review/release obligations are unchanged.

## Retained checks and outcome map

| Parent obligation | Actual combined evidence |
| --- | --- |
| Installed Spring service, live/ready and schema refusal | Both full-suite outputs and JUnit XML: seven `ServiceProcessTest` tests cover actual HTTP, separate bindings, empty/unversioned/future/checksum-invalid schemas unchanged, explicit migrate then ready, safe unknown routes, liveness independent of DB and retained restart. |
| DB/writer loss and validated recovery | Same process tests run two service JVMs, terminate dedicated lock ownership, stop/start retained PostgreSQL and corrupt/restore test-owned schema metadata around recovery. Exactly one writer recovers, with persisted epoch advance/reload; invalid schema stays unready. |
| Lower-layer fencing and admission | Twelve `WriterOwnershipTest` and two `ServiceRuntimeTest` tests run real PostgreSQL transactions: observed replacement exclusive-lock waits for old shared-lock commit/rollback, delayed stale-token rejection, simultaneous current-token transactions, failed callback rollback, DB/blackhole loss and revalidation, stop-admission-before-drain. No product test-action endpoints. |
| Config/secret inputs and actual process shutdown | Actual installed process tests include 18 invalid/missing/unreadable/malformed cases, sentinel log checks, configured bindings, safe partial-start bind failure, real TERM, released listeners/sessions/advisory locks and retained epoch restart. Observed SIGTERM times in both runs are below 30 seconds, including paused DB (~10 seconds). Settings and startup tests supplement process coverage. |
| Persistence/generation and strict version/boundary regressions | Six `FoundationDatabaseTest` tests plus migration/settings regressions prove explicit clean/retained/idempotent migration, no destructive fallback and transactional rollback. Each build runs independently migrated byte-identical jOOQ generation. Both artifact checks retain all server XML, exact generated hashes and JVM21/runtime-tool exclusion checks. Dependency provenance passes, preserving jOOQ3.20.20 with Spring Boot4.1.1. |

The first invocation cleans/rebuilds the server and shared test outputs. The
second cleans/recompiles all three JVM modules with `--no-build-cache`; all 29
actionable tasks execute. Each suite executes **34 server tests with zero skips,
failures or errors**. Gradle's skipped no-source/configuration tasks are not
skipped required tests. Shared `:core:jvmTest`/`:protocol:jvmTest` run in both.
The final check retains shared XML as well as validating run outputs and exact
owned database/container/volume/reaper/service-process cleanup.

## Scope limits

This is implementation-parent qualification, not self-supplied independent review.
Only the ownership recovery scheduler and persisted singleton reload exist;
future implemented workers must join readiness/recovery/shutdown. Full P/T scenario
execution claims and UI/normal-APK assertions remain pending in the unchanged
coverage ledger. No external service, deployment, paid resource or subagent is used.
Image lifecycle is I01c3, the next eligible child; parent I01c/I01 integration,
Compose/device work, later feature/architecture qualification and B1 independent
review remain open. External release gates are unchanged and unverified.
