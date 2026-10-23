# Fresh-session implementation loop and behavioral review contract

Canonical execution contract, D-300–D-309. D-304 authorizes the entire agreed R1;
[authorization](implementation-authorization.md) and [decisions](decisions.md) retain
provenance. Product behavior belongs to the [design contracts](../README.md), task
outcomes to the [plan](implementation-plan.md), current progress to
[state](implementation-state.json). Read only relevant sources per unit, not history.

## Operating brief for every session

Deliver a usable, responsive casual cooperative simulator, not a competitive or
mission-critical service. Preserve agreed outcomes, account security/privacy,
server authority and accepted-state integrity. Do not invent card rules, speculative
hardening, style blockers or superseded reset/message-order guarantees. Ordinary
FIFO, server-first restart losing unadmitted local work, normal prediction rebase,
authorized in-flight work finishing after reset/Logout and ordinary definition HTTP
remain accepted. UI is a practical baseline, not frozen art; genuine product-policy
contradictions require the owner, technical/layout completion does not.

## One fresh agent per bounded unit

Each implement/review/fix/verify unit uses a fresh session, exact
**`openai/gpt-6.1-sol#high`** verified in actual metadata, and saves/exits before its
successor. No fallback or unit-spawned agents. The existing CLI is serial;
[D-309 managed lanes](implementation-orchestrator.md) permit two isolated implement
workers plus an independent review lane, with one integration/state writer. Do not
run the old loop alongside them. No tool/grant/model changes are implied.

Selection: safe ownership/authorization/stop preflight → due verification/actionable
fix → due complete capability review → earliest ready implementation. Honor explicit
dependencies, inherited prerequisites and recorded independent lanes; do not evade
due reviews/fixes by repeatedly starting features. Independent work may proceed
around evidenced blockers. No ready work with unmet criteria means blocked, not done.
Record a brief selection reason and owned paths/start commit before editing.

### Implement

Complete a bounded real outcome with its affected tests and source P/T/F IDs. Record
executed assertions separately from assigned/deferred requirements. Delivery-first
[test policy](delivery-first-execution.md) governs scope: incremental affected checks,
not repeated green broad qualification. Carry deferred assertions to a named final
owner. A boundary needs an implemented outcome, evidenced blocker, concrete split
or evidence-backed troubleshooting advance; note churn is not progress.

New children have stable IDs, one parent, the same batch, concrete `outcome`, nonempty
`checks` and explicit `dependsOn`; preserve existing children/obligations. Planning-only
splits remain supported. `implementationUnits` lists completed **new leaf descendants**
in ready-selection order: `[{"id":"child","sourceIds":["actual ID"],"evidence":"passing check"}]`.
Each names this run's `implementationRecord`; unlisted new children stay pending.
The selected parent remains the report target and can complete only when all children
are done. This permits decomposition plus first-child delivery, not unrelated completion.

`ancestorClosures: [{"id": "parent", "reason": "integration outcome", "evidence": "run-owned check"}]`
may close only true ancestors of implemented work, all children done, with a passing
**current-run required check for each parent's scoped outcome**. Reuse exact child
records/input identities; test missing seams, not full recertification. Status alone
is not proof and old output is not a new run. The [qualified tooling](flow-improvements-2026-10-23.md)
enforces these transitions; original independent review/gate protections remain.

### Review a group

Independently review each complete B1–B8 capability and its integrated journeys, not
each task/file/commit. Prioritize normal play, then credible crashes, data loss or
duplication, broken recovery, responsiveness, unreachable/confusing UI and harmful
races. Each actionable finding needs stable ID/batch/code anchor, current expected
behavior, reproducible or precise code/test evidence, credible trigger/player harm
and a checkable correction/regression. Label un-reproduced evidenced risks honestly.
No quotas, speculative enterprise/anti-cheat guarantees or purely decorative blockers.

Dispositions: `actionable`, `nonblocking-polish`, `not-a-defect`, `duplicate`,
`blocked-owner/environment`; retain sources and rationale. Required behavior is not
optional polish. Noncritical findings may have an explicit final I34/I36 or owner-
review destination; do not hide them or turn unexecuted assertions into passes.

`review.units` retains the complete capability scope; a planning-only review may add
bounded children without narrowing/removing it. Fresh reviewers add checked leaf IDs
to `review.completed` at this run's `startCommit`, reported in `reviewedUnitIds`.
`blockedReviewUnitIds` records unavailable scopes in `review.blocked[id]` with reason
and current check evidence; blocked scopes remain incomplete. Complete independent
coverage plus resolved findings is required for `reviewed`; targeted verification
never substitutes for missing first review. Only material behavior/integration
changes invalidate affected coverage—not unrelated/report commits or HEAD movement.
Final B8 performs the plan's [complete interface-to-mock gate](implementation-plan.md#final-interface-to-mock-review--required-b8-completion-gate),
including all current orientations/unpictured interfaces and fresh discrepancy fixes.

### Fix and verify

Fresh fix sessions reproduce/understand findings, repair coherently and run affected
regressions; set `fixed-awaiting-check`, never self-verify/waive as polish. A separate
fresh verifier checks the attempted subset and affected behavior, then verifies,
reopens or explicitly disposes of each; no unconstrained new full audit.
Review/verify cannot edit product source, runtime resources or build/config inputs.
Only current report/checks, serial state and approved test-only paths may change;
managed review proposes state updates instead. Check-only helpers use exact
`supportPaths` in the run namespace. Shell/scripts must not bypass role boundaries.
Failed fixes return to fresh fix sessions. Even suspected false findings use an
evidence-only fix handoff, then independent verifier disposition. Retain rejected
findings; reopen only on new evidence/material change.

Only attempted fixes consume attempts; counts survive awaiting-check/reopening.
After two unsuccessful fixes under one diagnosis, stop for diagnosis/repair; the
second fix receives fresh verification first. A genuinely new diagnosis needs
`diagnosisEvidence` referencing actual retained check output, not cosmetic edits.
This guard never waives a defect; troubleshooting checkpoints do not spend attempts.

## Durable records and transitions

Use the [compact report](implementation-agent.md#report): supplied identity/selection,
summary/reason, actual commands/exits/evidence and exact `changedPaths`. Retain reports
and diagnostics byte-for-byte in Git under `docs/agent/implementation-runs/<runId>/...`
or `<runId>-...`; no rewriting archived reports, ignored-output substitutes or secrets.
Small truthful reports suffice; no bespoke audit-size requirement. Use the shared
[evidence commands](implementation-runner.md#shared-evidence-commands), not new report/audit scripts.

Tasks: `pending → in-progress → implemented` or evidenced `blocked` (`blocker` and
`blockerEvidence`). Implemented means scoped outcome/affected checks, not global
qualification. Findings: `open → fixed-awaiting-check → verified`, or explicit
withdrawn/duplicate/blocked with rationale. New findings start unfixed. `findingIds`
lists only attempted supplied fix/verify IDs or the review's findings;
`newFindingIds` declares other new discoveries, including critical implementation bugs.

Batch state is derived: awaiting-check issue → `fixes-awaiting-check`; other unresolved
actionable/owner-environment issues → `changes-requested`; otherwise incomplete work
→ `pending`; implemented with incomplete review → `ready-for-review`; only complete
independent coverage/resolved issues → `reviewed`. Anchor `reviewedCommit` to actual
review/verification starting code. A verified early critical fix cannot skip review.

Optional explained arrays contain unique `{id, reason}` unless specified:

- `taskAdjustments`: bounded dependency/independent-lane or block/unblock bookkeeping;
  no unrelated completion, removed children or reassigned batches.
- `reopenedBatches`: material affected review reopening; preserve old code anchor.
- `reopenedFindings`: new evidence reopening retained closed/blocked IDs as `open`.
- `reviewInvalidations`: `{id: batch, unitIds: [checked leaf IDs], reason}` removes
  exactly affected completed coverage; previously reviewed batches also reopen.
- `reviewUnblocks`: same shape plus current availability-check `evidence`; removes
  exactly those blocked IDs, not marking them checked.

Serial units append exactly this report to `runs`, leave `activeRun` null and preserve
authorization/owner record/runner/permission controls. Only selected role/target may
advance, except explicit validated D-309 work/closures and explained bookkeeping.
Blocked mode is planning/unblocking only: save/exit before newly runnable product
work. Managed workers leave authoritative state untouched; the integrator validates
proposals against current state, never replaces it with a worker snapshot.

`progress` needs real state/work/evidence advance; a scoped blocker with independent
work remaining is progress. `blocked` means no safe runnable work; `failed` is an
unresolved unsafe handoff. `complete` requires every task/review/finding/applicable
check/release gate satisfied. Budget exhaustion or a successful CLI exit is not done.

## Runner lifecycle and stop rules

The supervisor retains the original **3,000-session total remaining budget across
all roles**, no reset on restart/worktree/context loss. At most **five fresh launches
before an idle supervision assessment**, each at most **86,400 seconds**; drain active
lanes before that assessment. Use blocking dependent shell calls without tool timeout.
Inspect actual delivered journeys/diffs/checks for drift and refocus before continuing
through overnight/context compaction; no routine owner checkpoint. Stop only on actual
completion, explicit pause, exhausted budget/quota or a genuinely unavoidable boundary.

Stop drains active units to safe saved boundaries and prevents launches; interruption
does not authorize discard. Keep session/branch/worktree/state provenance and resource
leases. One integration writer; no old competing loop or sibling-worktree recovery.
Follow the [exclusive-resource plan](test-resource-plan.md); isolated host checks may overlap.
No claimed hard dollar cap or new paid-resource authority.

Before accepting a unit validate exact model/succeeded-idle session, report identity,
role/target/transitions, changed-path lease, committed report/check bytes, whitespace,
branch/ancestry, clean tree/index and actual remote containing the commit. Required
checks must pass; failed diagnostic reproductions stay explicit. Final prose/CLI exit
alone is insufficient. The resulting commit/push lives in the external journal—a
report cannot contain its own commit hash. Reject unsafe handoffs; preserve for repair.

### Autonomous infrastructure troubleshooting — D-305

Repair recoverable build/dependency/test-tool/save/orchestration failures autonomously,
including further fresh sessions. Use distinct experiments/results, not repeated
notes/green checks. Implement/fix checkpoints add `troubleshooting` with `issue`,
`diagnosis`, `progress`, `nextSteps` and current run-owned `evidence`; store the same
object plus report `record` on the unfinished selected task (`in-progress`) or fixing
finding (`open`). Do not combine checkpoint and split. Passing checkpoint checks do
not satisfy still-pending feature assertions; start preserving before timeout.

Necessary implement/fix driver/prompt repairs add `orchestrationRepair` with reason,
exact changed `paths` and passing required `evidence`; add focused regressions and run
`python3 scripts/test-implementation-loop.py`. The controller independently reruns
that suite before acceptance. No self-expanded permissions/authorization/model or
weakened checks. Replace the idle controller with remaining budgets, never another
parallel old driver. Reviewers/verifiers request fresh fixes, not self-certification.

Repair/retry the **same** save after ordinary Git/script failures. Never force push,
bypass hooks, reset/discard work or accept dirty/unpushed success. Retain genuine
external blockers; complete real configurable adapters and available local/synthetic
checks without crossing rights/credentials/production/paid/hardware boundaries.
Unavailable release exercises block only those claims, not independent feasible work.

## External release gates and completion

Preserve `externalReleaseGates` inventory/order/authority. Operator-owned gates change
only outside active units with recorded authority. A task-owned measurement gate
passes only in its exact completed owning parent unit with
`gateUpdates: [{id, evidence}]`, passing required output and that report as gate
`record`; retain all other fields. Children/evidence reuse do not waive the actual
measurement. No agent `not-applicable` waiver. Required final suite, complete approved
coverage, independent reviews and B8 mock discrepancy fixes remain obligations.
Distinguish implemented, verified and externally releasable software truthfully.

## Autonomy and boundaries

No production/public deployment, paid resources, secrets/unrelated private data or
operations-repository work is implied. Permissions are scoped safeguards, not an OS
sandbox. Respect explicit stop, preserve unrelated work and redact logs. Follow
[serial operations](implementation-runner.md) or the managed orchestrator—not both.
[Pre-D-309 contract/history](https://github.com/tumereu/async-tt-wiz/blob/b9a092dc212deca204e039e1791f7391e51f392a/docs/agent/implementation-loop.md)
is provenance only; current rules above supersede its duplicate execution wording.
