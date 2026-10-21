# I01c combined database, service and image qualification

- Run `20261021T202050Z-3bc861e9`, implement `I01c`, B1; starting clean
  `master` at `8a0ed2e597aab68836e7f0ac65931aa0a458627d`.
- D-304 explicitly authorizes this scope. Findings are empty; no independent
  capability review is due before I01–I04. I01c1, I01c2 (including I01c2a/b)
  and I01c3 are implemented. This parent qualifies their combined outcome before
  the remaining foundation work; no other task advances.
- Owned paths: this run's report/evidence/helpers, implementation state and
  `server/README.md`. No product/build changes are planned, no new session/loop,
  deployment, production orchestration, image publication or paid resources.

## Sources and partial coverage

- [Runtime profile](../../../design/04-technical/runtime-and-verification-profile.md#application-container-interface):
  JVM21, pinned images, explicit same-image migration, separate detail-free health,
  supported schema/database/writer readiness, no admission before readiness and
  shutdown within 30 seconds.
- [Storage mechanics](../../../design/04-technical/storage-mechanics.md): dedicated
  database advisory ownership, persisted epoch, shared transaction fence and
  exclusive replacement wait, loss/reacquisition/revalidation/reload.
- [Plan](../../implementation-plan.md#b1--install-enter-and-resume-the-normal-app)
  and [state](../../implementation-state.json): unchanged I01c outcome and all
  three checks. Preserve Testcontainers ownership separate from image-lifecycle
  isolation and the future Compose environment.
- I01 ledger allocations include **P-001, T-015, T-551, T-630, T-662**. This
  parent qualifies only foundation portions of T-630/T-662; no full P/T scenario,
  F01–F88 UI behavior or normal-APK user journey is declared executed. Replica,
  Compose reset, device, full product and architecture obligations remain pending.

## Combined qualification

1. Record actual local tool/resource inventory. Rebuild the image through the
   documented clean/cache-disabled/strictly locked Gradle path, regenerating jOOQ
   from explicit migrations in disposable PostgreSQL17. Retain immutable base,
   PostgreSQL, application and runtime-JAR/generated-source identities.
2. Run all server/database/shared JVM tests and independent byte-identical jOOQ
   generation. These include clean/retained migration, rollback, schema refusal,
   generated query/transaction round trips, epoch fencing and replacement waits,
   real Spring HTTP/process recovery, secret-file/redaction and SIGTERM checks.
   Confirm the tested installed distribution is byte-identical to the image.
3. Run the complete local amd64 image lifecycle suite twice, using fresh owned
   databases between independent scenarios and retained volumes for in-scenario
   restarts. Exercise the same migration/service interface, invalid schema,
   configuration, HTTP health, two writers, outage/revalidation and PID1 SIGTERM.
4. Retain actual JUnit XML, qualified dependency/license provenance, module/test
   boundaries, coverage accounting and exact owned-resource cleanup. Validate
   report/state/changed paths and Markdown links before scoped save/push.

Only the ownership recovery scheduler/singleton reload is implemented. Future
account/game/inbox/outbox/mail/push workers must join readiness/recovery/shutdown
when introduced; none is fabricated here. I01 integration, SQLDelight, Compose,
Android/device qualification, independent B1 review, full product/arm64 acceptance
and all external release gates remain open. No UI document is changed.
