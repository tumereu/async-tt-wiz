# Managed implementation orchestration — D-309

Instructions for the supervising orchestrator, **not a new autonomous daemon**.
The [existing CLI](implementation-runner.md) remains serial. Do not run multiple
copies or alter its shared lock/state to simulate parallelism. The
[canonical contract](implementation-loop.md) supplies roles, transitions and evidence.

## Adopt safely

1. Drain/pause the old controller and workers, reconcile any retained owned run in
   its exact checkout and confirm clean/pushed handoff. Preparation branches never
   migrate live state. Check explicit owner STOP before dispatch.
2. Integrate qualified driver/prompt changes; retain product/review/release obligations.
   Enforce dispatch/report/state gates below. If a lane cannot meet them, hold and
   repair it; **do not silently restart the old serial workflow**. No lock bypass.
3. Designate one integration branch/worktree and **sole integration/state writer**.
   Record the remaining original session budget and ownership/stop controls. Only
   this writer selects accepted transitions in `implementation-state.json`.

## Dispatch leases and readiness

Feature writing, execution and fixing run concurrently, not in serial batches.
Implementation writes UI/app/test code only; executors run builds/tests/apps/UI
inspections and return failures to separate fix writers. Keep maximum useful
feature-writing capacity filled while validation lags, including during fixes;
use separate branches/pinned proposed interfaces before prerequisites pass.
Acceptance still requires real evidence and original prerequisites. Retain this
owner direction and the execution/fix queues in every compaction handoff.

**No fixed lane cap or five-unit checkpoint rule.** Keep as many useful implement/fix/
review/verify units running as model quota, memory/CPU and truly exclusive resources
permit. Prefer parallel development plus merge repair even for modest file/task
overlap. Do not manufacture completed dependencies or launch workers that only wait
on one locked device. Split useful outcomes, not qualification bureaucracy.
Each bounded role is fresh, exact `openai/gpt-6.1-sol#high`, no fallback, at most
86,400 seconds. Debit every role/retry from the original remaining 3,000-session
ceiling before launch; never replenish on restart. Orchestrator-only delegation and
assigned-worktree access are per-session; worker/global grants stay scoped.

For each unit retain a lease in the orchestrator's durable dispatch record:

- run/session ID, role/task/finding/review scope, immutable base commit;
- branch/worktree, unique report/evidence namespace and proposed outcome;
- exact writable files/contract ownership, read-only inputs and dependency commits;
- required affected checks/integration seams, resource reservations and budget debit;
- lifecycle (reserved/running/saved/accepted or rejected/blocked), stop/recovery owner.

Workers use separate branches/worktrees/upstreams and must not edit authoritative
state or another checkout. Path ownership is **per branch**, not a global file mutex;
overlapping files are allowed with explicit overlap/merge responsibility in the leases.
Reports propose transitions; no worker-state file is copied into integration.
Use the [bounded proposal schema](implementation-agent.md#managed-proposal-additional-report-field)
and `validate-proposal` in each worker checkout. The helper permits useful speculative
development without asserting serial readiness; only latest-state acceptance may
advance accepted dependencies. A worker never edits state to appease serial `validate`.
Shared DTO/protocol/schema decisions have one integration owner. Dependent branches
may develop against a pinned existing/proposed interface with assumptions recorded;
merge/reconcile and test the actual accepted interface before completion. Do not wait
merely to avoid a modest merge conflict. Acceptance still honors inherited prerequisites
and due fix/review obligations, not just a numerically convenient task.

## Start and supervise sessions

Use a qualified session-spawning facility when exposed by the installed harness;
otherwise use the installed V2 API/headless adapter already exercised by the serial
driver. Do not invent an unavailable spawn tool. The V2
[API](https://opencode.ai/v2/docs/api) exposes fresh session creation, prompt, metadata,
active-session inspection and interrupt; inspect installed schemas/help for payloads.
Create in the worker's actual directory, pin model/variant and scoped permissions,
record the new session ID, then dispatch the compact prompt plus `THIS RUN` and lease.
Do not fork/resume prior implementation reasoning. If using `opencode run`, attach
only to that newly created ID and explicitly pin the model as the qualified adapter
does; CLI exit/final prose alone is never acceptance. No global config changes.

Units cannot spawn further agents. Launch useful leased work concurrently after leases
are durable; wait for actual completed/idle metadata and saved outcomes.
Review/verify receives the exact accepted code anchor in an isolated checkout, never
an evolving worker tree. A reviewer cannot implement its reviewed scope; a verifier
cannot certify its own repair. New failures return to fresh leased fixing sessions.

Create API-backed workers in their actual leased directory; a harness spawn without
directory control must move/verify that session before edits. Keep the headless
supervisor active: foreground-wait for completions (V2 provides
`POST /api/experimental/session/{sessionID}/wait`), integrate/refill and continue.
Never end the supervising turn merely to wait for a background callback or batch.

Use the [resource matrix and enforced leases](test-resource-plan.md): one native
device/routing lifecycle at a time, isolated host/build/service work where proven.
Hold `integration-writer` around the orchestrator process; it also excludes the old
serial controller. Reserve bundles before commands, retain failed-owner fences and
recover only with exact cleanup evidence. Keep other ready host/source work moving.

## Accept and integrate (one writer)

Delegate merge mechanics, conflict repair and affected integration tests to fresh
exact-high **integration subagents**, rather than consuming supervisor context.
They use separate candidate worktrees/branches, a pinned accepted base and listed
worker commits. Several candidates may be prepared concurrently; none edits live
state. Only the supervisor accepts/promotes one candidate at a time, rechecking its
base against latest HEAD. Stale candidates get rebased/reconciled and affected seam
checks before promotion. Integration subagents cannot independently certify their
own runtime repairs or waive review; charge their sessions to the same total budget.
Merge-only agents return candidate base/HEAD, worker commits, exact changed paths,
conflict repairs and real affected-check evidence—not application-state transitions.
The supervisor accepts original worker proposals separately against the candidate;
do not force a merge-only report through serial or application-proposal validation.

1. Confirm actual exact-high succeeded/idle session and budget/lease identity; inspect
   every diff, path ownership, source/test traceability, diagnostics and actual check
   output. Worker branch must be committed/pushed, clean and contain its exact report
   and evidence bytes. Missing/failed required checks are rejected, not relabeled.
2. Reconcile against **current** integration dependencies, findings and accepted
   contracts. Stale/conflicting work is retained for rebase/fresh repair, never blindly
   accepted because its old base passed. Integrate only leased code/tests/report
   commits; no wholesale state replacement or unrelated worker history.
3. Run missing **integrated seam tests** on the combined candidate when changed inputs
   invalidate worker evidence. Reuse valid child results with exact identities; no
   automatic full-suite/clean-build repeat. Integration conflicts are real changes
   needing affected checks, not mere bookkeeping. Preserve worker evidence as worker
   evidence, not a fabricated run on the integration commit.
4. The sole writer applies proposed task/finding/review/coverage transitions to latest
   state, using a **new acceptance report**, latest-HEAD before/current-after snapshots
   and `validate-acceptance` as shown in the [usable command sequence](implementation-runner.md#managed-handoff-example).
   The worker start commit remains its immutable source anchor, not latest state HEAD;
   independent review coverage retains the actual inspected commit. Validate
   selection/role/original inherited prerequisites/gates/proposed scope/changed paths,
   retain fresh integration reconciliation evidence and save/push owned paths.
   Changed selected task contracts require fresh reconciliation/proposal, not stale
   state replacement. Journal worker/base/resulting
   commits and actual upstream confirmation. Do not claim accepted progress before
   this boundary or mutate archived reports to conceal reconciliation.
5. Schedule independent coherent capability review on the accepted candidate. Fixes
   get fresh verification; parallel review of one capability never waives a due review
   or missing integrated seam in another. Only materially affected coverage reopens.

Use the [qualified evidence commands](implementation-runner.md#shared-evidence-commands)
for reports, captures, pre-save validation, JUnit and child reuse.
Small truthful reports suffice. Assigned requirements are not executed assertions.

## Drain, assess and resume

Stop/pause blocks **all** new launches and drains active lanes to validated saved
boundaries, not discarded branches. If interrupted/timeout, retain lease/session/
worktree provenance, partial work and diagnostics; reconcile in the owning checkout.
Never remove the old serial fence from a sibling or start a second controller.

After each completion refill useful capacity. Continuously inspect delivered journeys,
diffs/checks, integration drift, conflicts and budget—**no fixed-count checkpoints**.
Refocus affected work while unrelated lanes continue through overnight/context loss.
Persist leases, remaining budget and next readiness decisions in the handoff/dispatch
record. Explicit owner pause, exhausted budget/quota, real completion or unavoidable
boundary stops execution; recoverable failures trigger scoped repair and continuation.
Final full-suite/fixes, all capability reviews, complete mock comparison, security/
data integrity and external release gates remain unchanged. No parallel runner is
claimed active or qualified by this document alone.
