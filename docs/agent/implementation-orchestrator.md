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

Allow at most **two isolated implement/fix workers plus one independent review/verify
lane**. Every bounded role is a fresh session, exact `openai/gpt-6.1-sol#high`, no
fallback. Count all roles/retries/troubleshooting against the original remaining
3,000-session ceiling. Reserve budget before launch; at most five fresh launches
before an idle assessment, each capped at 86,400 seconds. Never replenish on restart.

For each unit retain a lease in the orchestrator's durable dispatch record:

- run/session ID, role/task/finding/review scope, immutable base commit;
- branch/worktree, unique report/evidence namespace and proposed outcome;
- exact writable files/contract ownership, read-only inputs and dependency commits;
- required affected checks/integration seams, resource reservations and budget debit;
- lifecycle (reserved/running/saved/accepted or rejected/blocked), stop/recovery owner.

Workers use separate branches/worktrees/upstreams and must not edit authoritative
state, each other's paths or shared contract/build files without an exclusive lease.
Reports propose transitions; no worker-state file is copied into integration.
Shared DTOs/protocol/schema/build catalog changes have **one** owner. Dependent workers
wait for accepted contracts or consume a pinned committed version explicitly;
uncommitted promises and status labels are not readiness. If useful work cannot be
isolated, serialize it. A readiness graph must include inherited prerequisites and
due fix/review obligations, not just the next numerically convenient task.

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

Units cannot spawn further agents. Launch independent ready work concurrently only
after leases are durable; wait for actual completed/idle metadata and saved outcomes.
Review/verify receives the exact accepted code anchor in an isolated checkout, never
an evolving worker tree. A reviewer cannot implement its reviewed scope; a verifier
cannot certify its own repair. New failures return to fresh leased fixing sessions.

Use the [resource matrix and enforced leases](test-resource-plan.md): one native
device/routing lifecycle at a time, isolated host/build/service work where proven.
Hold `integration-writer` around the orchestrator process; it also excludes the old
serial controller. Reserve bundles before commands, retain failed-owner fences and
recover only with exact cleanup evidence. Keep other ready host/source work moving.

## Accept and integrate (one writer)

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
   state, validates selection/role/dependency/gate/report/changed-path guards, retains
   integration evidence and saves/pushes owned paths. Journal worker/base/resulting
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

After at most five launches, drain and assess actual delivered journeys, diffs/checks,
integration drift, resource conflicts and budget—not report counts. Refocus before
the next group; continue through overnight/context loss under the existing assignment.
Persist leases, remaining budget and next readiness decisions in the handoff/dispatch
record. Explicit owner pause, exhausted budget/quota, real completion or unavoidable
boundary stops execution; recoverable failures trigger scoped repair and continuation.
Final full-suite/fixes, all capability reviews, complete mock comparison, security/
data integrity and external release gates remain unchanged. No parallel runner is
claimed active or qualified by this document alone.
