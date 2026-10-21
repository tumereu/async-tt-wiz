# I01c2a — database ownership/fencing

Selected at clean `4b4ec73df3ec4cb64d3c8c6b8f4ec47949347dad`, under D-304.
No findings or group reviews are due; I01c1 is implemented. This is the earliest
ready child. Only I01c2a may advance; its ancestors and B1 review remain open.

Sources: `storage-mechanics.md` single-writer persistence/locking paragraphs,
`runtime-and-verification-profile.md` application-container schema/ownership/recovery
boundary, and I01c2a's unchanged outcome/checks in implementation state.
The I01 coverage ledger assigns P-001, T-015, T-551, T-630 and T-662; this unit
supplies lower-layer PostgreSQL ownership/concurrency/recovery assertions toward
T-630/T-662 only. It does not complete these full scenarios, the later replica/E2E
assertions, any UI feedback F01–F88, or any external release gate.

Owned runtime/test scope: server-only ownership connection/schema validation,
opaque writer capability, persisted-epoch transaction fence and real PostgreSQL
tests. No migration/schema changes, gameplay/action endpoints or future workers.
Spring HTTP/configuration/process/SIGTERM remains I01c2b; image remains I01c3.
No subagents, driver launches, deployments or paid resources.

Planned checks: Testcontainers migrated/retained/empty/unversioned/future/checksum
schemas; two managers, lock-session termination/takeover, deterministic observed
row-lock waiting through commit/rollback, simultaneous shared locks; lock timeout,
DB stop/start and blackhole recovery; rollback, redacted diagnostics, repeated close
and restart; existing migration/generation regressions, JVM21/module boundaries,
coverage and final changed-path/evidence/whitespace validation.
