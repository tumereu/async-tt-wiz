# Fresh-session implementation loop and behavioral review contract

Owner direction D-300; prepared under D-301. **Inactive planning contract.** Read
the [task plan](implementation-plan.md), [state record](implementation-state.json)
and [delivery authorization gates](delivery-workflow.md) before any execution.

## Operating brief for every session

> A casual, cooperative game simulator—not a mission-critical service or a
> competitive game. Optimize for usable, responsive and reliable actual play.
> Implement the agreed behavior simply. Do not invent machinery for speculative
> guarantees, inferred card rules, competitive secrecy or arbitrary event mixtures.

Preserve baseline account security/privacy, server authority and accepted-state
integrity. The casual framing is **not** permission to ignore agreed outcomes,
silently drop work, weaken tests, hide failures or omit difficult in-scope tools.
Apply existing simplifications: server-first restart can lose unadmitted local
work; ordinary FIFO/arrival order; revealing predictions reconcile normally;
authorized in-flight work can finish after reset/Logout; complete definition
metadata uses ordinary HTTP. Do not resurrect superseded guarantees as findings.

The UI packet is an initial practical baseline, not perfect/frozen art. Fix clear
reachability/readability/gesture problems within agreed constraints. Unpictured
states use the established patterns and actual contracts. Product-policy changes
or genuine contradictory outcomes still need the owner; ordinary technical/layout
completion does not need a new approval checkpoint. UI-document edits continue to
require cumulative feedback and full same-URL publication under AGENTS.md.

## One fresh agent per bounded unit

The outer runner is a small process, not an AI manager or a parallel-agent swarm.
It starts one fresh session, waits for its outcome, verifies the handoff/save
boundary and starts the next. Each session independently reads Git, the plan,
authorization, task/evidence state and current findings, then selects **one** mode:

| Priority | Selection |
| --- | --- |
| 0 | No explicit implementation authorization, stop requested, unrelated dirty/index state or unsafe workspace? Do not code. Reconcile safe known interrupted owned work only from its recorded provenance; never reset/discard it. |
| 1 | A due fix-verification or actionable review issue has its dependencies available? Verify/fix a bounded related set before new scope. A fix must be verified against the actual finding and relevant regression checks. |
| 2 | A batch's tasks/children and relevant tests are complete and its scoped review is due (first review or an explicitly reopened affected capability)? Review that integrated batch now. |
| 3 | Otherwise select the earliest ready pending implementation task/child. Complete it and its checks; no independent review after every task. |
| 4 | If blocked, record evidence and select safe independent approved work where it exists. No runnable work but unmet criteria means blocked, **not complete**. Completion requires the plan's actual completion criteria. |

Every selection records why it outranks the alternatives. Initially follow the
listed batch/task order; do not repeatedly implement new features to avoid a due
review or fix. A new genuine critical defect may interrupt a batch immediately;
this is not a routine per-task review policy.

`reviewedCommit` anchors what was checked; **do not compare it with every new HEAD
and automatically rereview**. A report-only commit or an unrelated later task does
not invalidate a completed batch review. Relevant behavior/integration changes
record the affected scope and targeted rechecks; only a materially changed
capability reopens that scoped review. Verified fixes can close the findings/batch
without another full audit.

Before mutating anything, identify the exact starting commit, owned paths, task/
batch/mode, scope/tests and any child split. Fresh sessions inherit repository
records, not assumptions from a previous context window. There is one writer;
do not start another implementation/reviewer session until the previous one exits.

### Implement

Read current contracts and the task's exact P/T/F IDs, complete necessary technical
details, implement and write/run relevant executable tests in that unit. Record
code/test traceability, limitations and the next state. The full regression suite
need not run on every small task; focused tests/builds are mandatory, and batch/
cross-cutting changes run the applicable integration/E2E regression set. Do not
defer all verification to reviewers or mark unexecuted checks passing.

### Review a group

Use a fresh review session after a coherent B1–B8 group, not after each file/task/
commit or arbitrary count of commits. The reviewer may run tests, inspect code,
exercise UI and add focused regression evidence. Do not let the same session
implement the batch and then declare its own independent review complete.

Review the **delivered scope and its integration**, acknowledging explicitly
pending later features. Inspect normal workflows first, then credible risks that
would disrupt them: crashes, accepted-state loss/duplication, broken recovery,
responsiveness/performance, confusing/unreachable UI and harmful concurrent edits.
Source inspection supports behavioral evidence, not a style scorecard.

Every actionable finding needs:

1. Stable ID, batch and exact reviewed commit/range.
2. Current requirement/expected behavior, not an obsolete proposal.
3. Reproduction or precise code/test evidence and a credible trigger.
4. Observable consequence for a player and why it merits fixing now.
5. A checkable correction outcome and relevant regression test.

Clearly label evidence-backed, not-yet-reproduced risks as such. An unlikely crash
or destructive race can matter; frequency alone does not dismiss severity. But
mere possibility of a different ordering is not a bug without a harmful outcome
contrary to current requirements. Do not require reproduction of every theoretical
interleaving or add protocol guarantees merely because they could be imagined.

**Do not file blockers for:** arbitrary style/naming preferences, speculative
abstractions, decorative mockup differences, enterprise availability guarantees,
competitive anti-cheat/secrecy, or contrived message/reset combinations already
allowed by D-289. Example: an authorized message finishing after successful reset
is allowed; a new request using a revoked credential, a cross-account disclosure
or a duplicated accepted move is a real defect.

Disposition is `actionable`, `nonblocking-polish`, `not-a-defect`, `duplicate`, or
`blocked-owner/environment`, with rationale. Nonblocking polish must still have a
real user benefit; it is not a compulsory new backlog or permission to leave an
approved functional requirement broken. No review quota/minimum finding count.
A clean review may close the batch without inventing another round of questions.

### Fix and verify

The fixing session reproduces/understands a finding, makes the smallest coherent
repair, adds a regression and runs relevant checks. Record `fixed-awaiting-check`,
not silently `verified`. A subsequent fresh verification session checks that
bounded finding set and affected behavior, then closes it or records remaining
evidence. It does **not** restart a full unconstrained audit after every fix.

A review session normally commits its report/findings, not product fixes mixed
into the same review. A fix can be combined with closely related fixes in one run.
Preserve rejected/withdrawn/duplicate findings and their sources so later sessions
do not resurrect them. Reopen only on new evidence or a relevant behavior change.
No arbitrary number of rounds can waive a real defect; repeated no-progress
rounds stop/escalate instead of creating an endless review/fix carousel.

## Durable records

`implementation-state.json` is the small status/selection record; the task plan
owns task scope, and product contracts own behavior. It starts in `draft` with
`authorization: not-authorized`. No application task is complete or running.

When execution is authorized, each run adds a durable report under the future
`docs/agent/implementation-runs/` directory and references it from state. Record:

- Run ID/session ID, mode, task/child or batch, starting commit and owned paths.
- Source requirement/test IDs and concrete expected outcome.
- Commands run, actual exit/results and retained evidence locations; skipped/
  unavailable checks with reasons are distinct from passes.
- New/updated findings and their dispositions, or an explicit no-findings result.
- Changed paths, next status and remaining dependencies/blockers.

Tasks use `pending → in-progress → implemented` (or `blocked`); `implemented`
means task outcomes/tests passed, not independently reviewed. A batch uses
`pending → ready-for-review → changes-requested/fixes-awaiting-check → reviewed`.
Issue records retain `open → fixed-awaiting-check → verified`, plus explicit
withdrawal/duplicate/blocked dispositions. The state may add child IDs before
execution; the same approved parent/batch/coverage obligations remain.

The report/state changes are saved with the unit. A commit cannot truthfully
contain its own hash: the runner records the resulting commit and push confirmation
in its external run journal after save, and the next session can reconcile them
with Git/report paths. Before the next launch verify that report/state is in the
resulting commit, the working tree/index is safe and the configured upstream
contains that commit. A final assistant sentence/zero process exit alone is not
evidence of completion or a successful push.

## Runner lifecycle and stop rules

The runner is **not implemented by these documents**. OpenCode V2 supports
headless `opencode run`; qualify fresh-session behavior and final-outcome handling
against the installed CLI/API before building the wrapper. Do not guess flags or
assume a session's conversational ending is the required durable outcome.

Required behavior for that wrapper:

1. Hold one repository-writer lock and require a safe starting tree/index.
2. Check explicit authorization and a user stop/pause control **between** runs.
   Requesting a stop should let the active unit reach a safe boundary by default;
   interruption is not permission to discard its files.
3. Start a fresh session with the operating brief and record/report paths, scoped
   unattended permissions and a declared run time/cost budget. Inherit the selected
   configured model unless the owner selects another; no silent paid-resource,
   model-panel or parallel-agent expansion.
4. Require exactly one bounded unit and a structured `progress`, `complete`,
   `blocked`, or `failed` result referencing durable evidence. `progress` records
    its selected implement/review/fix/verify mode. Validate it against state/Git.
   A task-scoped blocker with safe independent approved work remaining is recorded
   as progress with that task blocked; reserve `blocked` for no runnable work.
5. Each completed unit runs applicable checks, then uses
   `./scripts/save.sh <only owned paths...>` to commit/push, and exits. Reviews
   save reports; no empty commits are needed for a no-change verification, but
   its meaningful report normally changes. A failed check/save cannot produce a
   success handoff. Preserve diagnostic work; no force push/hook bypass/reset.
6. Confirm save/push before another agent. A failed push may be retried as the
   **same save operation** without repeating implementation; unresolved failure
   stops the runner. Do not launch another agent to blindly redo failed work.
7. Continue after validated progress. Stop on completion, no runnable independent
   work (including an owner/environment decision blocking all eligible work),
   unsafe workspace, unresolved required-check/tooling/save failure at the unit
   boundary, a permission prompt requiring the owner, or repeated no progress. Bounded transient tool
   retries are acceptable with evidence; no automatic semantic retry/reroll.

Initial anti-spin defaults for wrapper qualification: one active agent; at most
one automatic rerun of a failed infrastructure operation; stop if a supposedly
successful run advances no task/finding/evidence; stop if the same finding remains
unresolved after two fix attempts without a new diagnosis. These are proposed
engineering safeguards, not a defect waiver or extra per-slice owner checkpoints.
Exact per-run budget must be configured before unattended activation, not invented
as unlimited spending. Keep logs local and redact credentials/private data.
Normal red/green development and repairing a failed check inside the selected
unit are expected; the stop rule applies when the agent exits with the failure
unresolved, not to the first failing test while developing its fix.

## Autonomy and boundaries

The runner continuing across stopped agents implements D-256's **project-level**
autonomy; it does not require one giant session or routine owner “continue?” after
each task. Derive technical details and repair ambiguous documentation within
approved constraints. Escalate only demonstrated product choices, unavailable
required resources or genuine blockers, and continue independent approved work.

Before activation, qualify wrapper dispatch/one-unit exit, grouped-review priority,
fix verification, save/push failure, stop control and interrupted-work recovery in
an isolated non-product fixture. Do not start coding merely because state files
exist or because a headless session can be launched. Current readiness still needs
explicit implementation authorization and this runner qualification.
The safe default after interruption is to stop and preserve dirty work. Any
explicit resume must reconcile the recorded unit/owned paths first; qualification
must prove unrelated work is never discarded or blindly staged.
