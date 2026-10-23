# Delivery-first execution — D-307/D-309

Optimize **real elapsed time to the whole agreed R1**, not check/report counts.
Approved owner direction; supersedes repeated upfront/ancestor qualification, not
scope, security, reviews, safe saves or release gates. The [loop contract](implementation-loop.md)
owns transitions/budgets; [managed orchestration](implementation-orchestrator.md) owns
parallel dispatch. Historical setup is not required reading for implementation units.

## During implementation: affected checks, then continue

1. Deliver the next usable journey in dependency order: entry → import → invitation/
   preparation → shared table → complete manual toolkit/history/lifecycle. These
   are milestones, not a smaller MVP replacing R1. Build only supporting machinery.
2. Run affected builds/tests and ordinary journeys, including directly affected
   recovery/security/data integrity. Fix observed failures; expand only where a
   changed relevant input or evidenced failure identifies a wider affected area.
3. Once green, retain output, save/push and move on. Default incremental builds;
   no second clean/cache-disabled build, `--rerun-tasks`, device cold boot or passing
   journey/full-suite repeat just for confidence, fresh context or ancestor closure.
4. Summarize delivered scope/check choice briefly in existing report fields. No
   test-selection-only session, mandatory 100-line bespoke audit or new tracking
   framework. Assigned IDs are not executed coverage. Retain pending required
   assertions with source IDs and final owner (usually I34/I36), never false passes.

Prioritize broad functional delivery over minor-detail loops. Noncritical evidenced
findings retain an explicit final/owner-review destination. Do not relabel required
defects optional. Repair serious security/privacy/accepted-state failures, unsafe
handoffs and failures preventing ordinary use; do not search endlessly for imagined risks.

## Ancestor closure is evidence reconciliation, not recertification

Avoid recursive qualification-only splits. Reconcile exact child reports/commits and
relevant input identities with the current parent's scoped outcome. A small real
run-owned required audit plus missing affected seam checks suffices. An already
exercised unchanged integrated path is not rerun. Matching statuses alone are not
proof; copied old output is not a new execution. Qualified D-309 explicit ancestor
closures may share a unit, but each parent needs its own current required evidence
and all children done; unrelated completion remains forbidden.

## Findings and tooling: evidence, not imagined risks

Blockers need a current requirement, reproduction/credible harmful code path and
meaningful player consequence. No speculative enterprise/competitive hardening.
Implement required uncommon behavior; defer its broad matrix, not the behavior.
Fix tools obstructing affected checks without turning them into platform-certification
projects. Resource cleanup uses exact ownership/provenance and real interference,
not machine-wide inventory equality. Never stop unrelated resources to satisfy an oracle.

## Final qualification, not perpetual full-suite repetition

Keep independent capability reviews and fresh targeted fix verification. In B8:

- I34: full executable suite, complete coverage, integrated normal-app journeys and
  deferred infrastructure/recovery/multi-device assertions, including I01f3.
- I35: remaining Android/version/tablet/upgrade/image-architecture qualification.
- I36/B8: delivery evidence and complete D-303 interface-to-mock comparison/fixes.

Preserve the full run, repair actual failures and rerun affected checks. A fix alone
does not require another full run; broaden only if the repair invalidates that
baseline. Do not relabel an initial red run green or claim a full pass on a binary
tested only by affected rechecks. External gates remain unverified without authority.

## Paused application and foundation scheduling

I01f3's move to final B8 is already recorded in the
[application record](delivery-first-application-2026-10-22.md); do not replay that migration.
Apply new scheduling/oracle/controller changes only after the owning workers drain
and the controller pauses at a validated handoff. Reconcile the latest state; never
copy a stale preparation snapshot or recover from a sibling checkout. Preserve all
completed work, requirements, findings and unmet final obligations. Validate affected
dependency/selection/role guards, save/push, then resume only under current authority.

## Preparation validation — not live activation

[Original D-307 preparation/91-test evidence and application steps](https://github.com/tumereu/async-tt-wiz/blob/b9a092dc212deca204e039e1791f7391e51f392a/docs/agent/delivery-first-execution.md)
remain historical. D-309 preparation likewise does not launch workers or migrate live state.
