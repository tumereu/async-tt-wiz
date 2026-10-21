# I01c2 combined service/ownership qualification

- Run: `20261021T195803Z-2deb1ecd`; implement `I01c2`, B1.
- Starting code: `bb25687200efe0c454548f19a35f81c2c7203be2` on `master`;
  initial working tree/index clean. D-304 authorizes the real application scope.
- Both recorded children, I01c2a and I01c2b, are implemented. There are no findings
  or due group reviews. This parent must qualify their combined outcome before
  I01c3 image packaging; no other task advances in this unit.
- Owned edits: this run's report/check outputs/helpers, implementation state and
  the server's qualification notes. Product/build changes are unnecessary unless
  combined checks identify an actual defect.

## Current sources and traceability

- [Runtime profile](../../../design/04-technical/runtime-and-verification-profile.md#application-container-interface):
  JVM21, separate health binding, explicit migration, supported schema/DB/writer
  readiness, no command admission before readiness and bounded SIGTERM.
- [Storage mechanics](../../../design/04-technical/storage-mechanics.md):
  dedicated advisory ownership, persisted epoch, shared transaction fence,
  exclusive replacement wait, loss/revalidation/reload before readiness.
- [Plan](../../implementation-plan.md#b1--install-enter-and-resume-the-normal-app)
  and [state](../../implementation-state.json): unchanged I01c2 outcome and four
  checks, including both children's migration, ownership and process regressions.
- I01's ledger allocations include **P-001, T-015, T-551, T-630, T-662**.
  This parent qualifies only JVM/server foundation portions of T-630/T-662;
  no full P/T scenario, normal-APK user journey, UI behavior or F01–F88 is marked
  executed by lower-layer tests. Replica recovery, complete reset, real image,
  device/architecture compatibility and later feature assertions remain pending.

## Combined checks

1. Clean and rebuild shared JVM and server outputs with ordinary strict locks;
   run the entire server suite, installed executable and byte-reproducible jOOQ
   generation against independently owned migrated PostgreSQL17 databases.
2. Rerun the full server suite in a fresh test invocation/database set. Existing
   tests jointly exercise actual Spring processes/HTTP/SIGTERM, explicit clean/
   retained migration without service auto-migration, schema refusal, two writers,
   DB outage/recovery and blackhole, lock termination/takeover, persisted epoch,
   stale-token fencing, observed replacement row-lock waits through old commit/
   rollback and simultaneous shared transactions. Test-only data stays below the
   runtime boundary; there are no product test-action endpoints.
3. Retain actual JUnit reports through the existing artifact checker, pinned
   dependency/license provenance, approved test-path/module boundary checks,
   coverage accounting and exact owned-container/volume/process cleanup.
4. Validate report/state transitions, exact changed paths, local Markdown links
   and whitespace before the normal scoped save/push.

Only the ownership recovery scheduler/singleton reload exists. Account/game/
inbox/outbox/mail/push workers must join readiness/recovery/shutdown when added;
none is fabricated or certified here. I01c3 image lifecycle, I01c and I01 parent
integration, Compose/device work, independent B1 review and external release
gates remain open. No UI document or served review edition changes.
