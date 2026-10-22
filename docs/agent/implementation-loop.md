# Fresh-session implementation loop and behavioral review contract

Owner direction D-300/D-303/D-304; prepared under D-301 and executable driver work D-302.
**Product implementation is authorized by [D-304](implementation-authorization.md).** Read
the [task plan](implementation-plan.md), [state record](implementation-state.json)
and [delivery authorization gates](delivery-workflow.md) before any execution.

**Delivery-first execution — D-307:** [current testing/scheduling policy](delivery-first-execution.md).
Minimize elapsed time to deliver R1: implement real capabilities, run/fix affected
checks, save and continue. Full-suite qualification is at the end, not an automatic
per-session/parent/review ritual. Reuse valid child evidence; ancestor closure runs
an evidence audit and only missing affected integration checks. This supersedes
older broad repetition wording. Keep independent capability reviews, fresh targeted
fix verification, security/data integrity, truthful evidence and release gates.

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

D-307 prioritizes common delivered user journeys over speculative fault/tooling
work inside those selections. Rare required assertions deferred to final
qualification keep explicit ownership; they do not become false passes. Apply
the policy's scheduling corrections outside an active unit. The
[one-time paused application](delivery-first-application-2026-10-22.md) retains
I01f3 as final B8 work; do not restore it as a gate to account implementation.

Every selection records why it outranks the alternatives. Initially follow the
listed batch/task order; do not repeatedly implement new features to avoid a due
review or fix. A new genuine critical defect may interrupt a batch immediately;
this is not a routine per-task review policy.

Do not split merely to add more qualification layers. For already split tasks,
the selected parent reconciles exact child evidence and the integrated outcome;
it does not automatically rerun the children's clean builds or failure matrices.
Use a real, small run-owned audit as its required check, with affected integration
tests only where evidence or changed relevant inputs show a gap. Do not advance
unselected ancestors or copy old check output as a new execution.

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
is reserved for final qualification, not routine tasks or batch closure. Focused
affected tests/builds are mandatory; a cross-cutting change expands that scope only
where its impact warrants it. Run the delivered journey and affected integration
checks, fix actual failures and continue once green. No extra clean/cache-disabled
build or passing journey repeat just for confidence or because a fresh agent starts.
Do not defer all verification to reviewers or mark unexecuted checks passing.

### Review a group

Use fresh review sessions after a coherent B1–B8 group, not after each file/task/
commit or arbitrary count of commits. A group may have bounded review checkpoints
with retained scope/commit anchors; it need not fit one invocation. The reviewer may run tests, inspect code,
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
abstractions or new decorative preferences (distinct from D-303's explicit final
interface-to-mock discrepancies), enterprise availability guarantees,
competitive anti-cheat/secrecy, or contrived message/reset combinations already
allowed by D-289. Example: an authorized message finishing after successful reset
is allowed; a new request using a revoked credential, a cross-account disclosure
or a duplicated accepted move is a real defect.

Disposition is `actionable`, `nonblocking-polish`, `not-a-defect`, `duplicate`, or
`blocked-owner/environment`, with rationale. Nonblocking polish must still have a
real user benefit; it is not a compulsory new backlog or permission to leave an
approved functional requirement broken. No review quota/minimum finding count.
A clean review may close the batch without inventing another round of questions.

Each batch's `review.units` retains the complete capability obligation. A review
planning unit may split a pending obligation into bounded children without removing
or narrowing its original scope. Subsequent fresh review units add checked leaf IDs
to `review.completed`, anchored individually to the actual starting commit, and
retain the remaining scopes. This is one coherent group review across fresh units,
not independent review after every implementation child. Checks/findings stay in
the corresponding reports. Blocked review scopes retain evidence separately; they
are not completion, and other ready review scopes/independent batches may continue.

Only complete independent coverage can close the group. An implementation-time
critical finding and its targeted fix/verification **never replace the first group
review**, even if all implementation tasks are now done. A material behavior change
records explicit affected-scope invalidation/rechecks; report-only/unrelated commits
do not erase completed coverage. See the [unit record schema](implementation-agent.md#bounded-group-review-checkpoints).

The final B8 review additionally performs the task plan's
[complete interface-to-mock comparison](implementation-plan.md#final-interface-to-mock-review--required-b8-completion-gate):
all current mocked states/orientations and implemented unpictured interfaces, not
just a representative usability sample. Record discrepancies for repair and fresh
targeted visual/interaction verification. The comparison inventory and all required
corrections must be complete before B8 closes; I32's earlier pass does not replace it.

### Fix and verify

The fixing session reproduces/understands a finding, makes the smallest coherent
repair, adds a regression and runs relevant checks. Record `fixed-awaiting-check`,
not silently `verified`. A subsequent fresh verification session checks that
bounded finding set and affected behavior, then closes it or records remaining
evidence. It does **not** restart a full unconstrained audit after every fix.

A review/verification session must not change product source, runtime resources or
build/configuration inputs and certify that change itself. Its edit permissions
are narrowed to state/current report outputs and approved test-only paths, and its
actual committed diff is checked. Failed fixes return to a fixing unit; focused
regression tests may be added without product repairs. Shell/script execution is
not an OS sandbox and must not bypass this boundary.
A fix can be combined with closely related fixes in one run.
Preserve rejected/withdrawn/duplicate findings and their sources so later sessions
do not resurrect them. Reopen only on new evidence or a relevant behavior change.
No arbitrary number of rounds can waive a real defect; repeated no-progress
rounds stop/escalate instead of creating an endless review/fix carousel.

## Durable records

`implementation-state.json` is the small status/selection record; the task plan
owns task scope, and product contracts own behavior. It initially started in `draft`;
D-304 now records `mode: active`, `authorization: authorized` and the owner record.
All application tasks remain pending until actual implementation units run.

During authorized execution, each run adds a durable report under the
`docs/agent/implementation-runs/` directory and references it from state. Record:

- Run ID/session ID, mode, task/child or batch, starting commit and owned paths.
- Source requirement/test IDs and concrete expected outcome.
- Commands run, actual exit/results and retained evidence locations; skipped/
  unavailable checks with reasons are distinct from passes.
- New/updated findings and their dispositions, or an explicit no-findings result.
- Changed paths, next status and remaining dependencies/blockers.

Tasks use `pending → in-progress → implemented` (or `blocked`); `implemented`
means the current scoped task outcome and affected checks passed, not independently
reviewed or globally qualified. D-307 deferrals retain their final owning tasks;
unperformed required assertions are not silently included in an implemented claim.
A batch uses
`pending → ready-for-review → changes-requested/fixes-awaiting-check → reviewed`.
Issue records retain `open → fixed-awaiting-check → verified`, plus explicit
withdrawal/duplicate/blocked dispositions. The state may add child IDs before
execution; the same approved parent/batch/coverage obligations remain.

The driver checks **transitions**, not just status names. Implementation cannot
review its own batch; a fix cannot verify or waive its own finding. Splitting is
a planning-only unit leaving the parent in progress and new same-batch children
pending for fresh units. A completed parent requires all its children implemented.
Only attempted finding subsets may change during fix/verify. Necessary independent-
lane/blocker bookkeeping and material review/finding reopenings use the explicit
explained arrays in [the unit report contract](implementation-agent.md), never
unreported advancement of unrelated tasks or reviews. A critical finding discovered
inside an implementation unit is explicitly reported and prioritized normally.
An early critical fix's clean verification returns an incomplete batch to pending;
a complete batch with missing review coverage becomes ready-for-review, not reviewed.
Each implementation boundary needs a completed outcome, D-305 evidence-backed
troubleshooting checkpoint, evidenced blocker or real
planning-only child split; changing notes on an in-progress task is not progress.
A blocked selection may save explained planning/unblocking that makes independent
approved work runnable, but must exit before that work starts in a fresh unit.
Final clean review/verification anchors the starting code
commit that the fresh reviewer/verifier actually inspected.

The report/state changes are saved with the unit. A commit cannot truthfully
contain its own hash: the runner records the resulting commit and push confirmation
in its external run journal after save, and the next session can reconcile them
with Git/report paths. Before the next launch verify that report/state is in the
resulting commit, the working tree/index is safe and the configured upstream
contains that commit. A final assistant sentence/zero process exit alone is not
evidence of completion or a successful push.
Every report/check-output reference must exist byte-for-byte in that commit;
local ignored build output is not a durable check record.
Review/verification/planning check outputs belong to that run's report prefix;
runtime files or older reports cannot masquerade as newly owned check output.
The required external release-gate inventory is retained: operator gates cannot
be waived by an active agent. Only an explicitly task-owned gate may be passed by
its exact completed owning task with retained passing evidence and a gate-update
report. Every resolved gate retains its authority/evidence record.

## Runner lifecycle and stop rules

**Current supervised run — D-306:** the owner selected 3,000 sessions and a
two-week (1,209,600-second) per-session ceiling, invoked with no shell-tool timeout
as a blocking call. A controller validation failure is a boundary to diagnose and
repair, not the end of the supervising agent's assignment. After each exit,
inspect actual completion, repair/validate/save/push any recoverable problem,
reconcile the retained owned run and restart. Do not bypass validation or blindly
replay implementation. Continue feasible independent work around evidenced external/
forbidden steps with documented plan/dependency adjustments and retained obligations.
See [authorization](implementation-authorization.md#supervised-unattended-execution--d-306).

The [Python driver](../../scripts/implementation-loop.py) implements this contract;
see [operations/qualification](implementation-runner.md). OpenCode V2.1.1 CLI help
and the V2 API were checked: create a fresh top-level session, then attach the
headless invocation only to that newly created ID. Never assume a conversational
ending or zero CLI exit alone is the required durable outcome. D-302 authorizes
this tooling; D-304 separately authorizes the product task plan.

Required behavior for that wrapper:

1. Hold one repository-writer lock and require a safe starting tree/index.
2. Check explicit authorization and a user stop/pause control **between** runs.
   Requesting a stop should let the active unit reach a safe boundary by default;
   interruption is not permission to discard its files.
3. Start a fresh session with the operating brief and record/report paths, explicit
   per-session permissions and operator-selected run-count/wall-time budgets.
   D-302 selects **openai/gpt-6.1-sol#high for every role**; pin and verify its actual
   metadata, with no fallback/model-panel/parallel expansion. These budgets do not
   promise a hard dollar cap or authorize acquiring paid resources.
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
7. Continue after validated progress. D-305 below requires autonomous repair and
   best-effort implementation inside external constraints, not halting at the first
   permission/resource/build/save problem. Stop on completed work, explicit stop or
   operator budgets. A genuinely unsafe/unresolved handoff cannot be accepted as
   progress: preserve it for recovery rather than bypassing checks. Missing external
   qualification must never prevent remaining feasible implementation work.

The driver uses one active agent and no automatic failed-unit reruns; stop if a supposedly
successful run advances no task/finding/evidence; stop if the same finding remains
unresolved after two fix attempts without a new diagnosis. These are engineering
safeguards, not a defect waiver or extra per-slice owner checkpoints. Only reported
attempted findings consume attempts; counts survive awaiting-check and failed
verification. A second proposed repair gets its fresh verification before the
unresolved-cycle guard fires. Verified/explicitly disposed findings clear counts;
a genuinely new diagnosis needs a retained check-evidence reference, not cosmetic
record changes. Counts and the resulting commit/push confirmation are retained even
when the guard stops the run. Positive
session-count and wall-time budgets are required on the run command; there is no
unlimited default or billing-cap claim. Keep logs local and redact credentials/private data.
Normal red/green development and repairing a failed check inside the selected
unit are expected; the stop rule applies when the agent exits with the failure
unresolved, not to the first failing test while developing its fix.

### Autonomous infrastructure troubleshooting — D-305

The owner's unattended-implementation objective takes precedence over historical
"report any save failure and exit" wording. Make a serious autonomous effort to
repair recoverable build, dependency, local test infrastructure, staging/save and
orchestration defects. Spending a whole session or several fresh sessions on a
credible repair is preferable to returning the problem to the owner at the first
red command. Follow the specified product vision; no speculative product expansion.

An implement/fix session may save a bounded `troubleshooting` checkpoint using the
[unit schema](implementation-agent.md#autonomous-troubleshooting-and-orchestration-repairs--d-305).
Keep unfinished tasks/findings unfinished; retain distinct experiments/repairs,
fresh output, current diagnosis and concrete next steps. Passing checkpoint checks
do not satisfy still-failing/pending whole-feature checks. The next fresh session
continues the same work within the operator budgets. Identical replayed evidence
and note-only churn are rejected; infrastructure checkpoints do not consume the
two-attempt product-fix guard. Planning-only splits remain a separate mechanism.

Necessary driver/prompt repairs are allowed in implement/fix roles with explicit
`orchestrationRepair` provenance, passing regression evidence and an independently
executed runner fixture suite. Permission grants, authorization and the model remain
unchanged. A saved driver repair replaces the idle controller process with the
remaining budgets instead of starting a parallel driver. Reviewers/verifiers still
cannot repair runtime/build/controller changes and certify those changes themselves.

Repair and retry the **same save** after ordinary Git/script/line-ending failures;
this does not repeat implementation or bypass a check. Do not erase fences or accept
dirty/unpushed handoffs to simulate progress. Diagnose and repair before escalating;
checkpoint before timeout. Explicit stop requests and operator budgets still apply.

Existing development/testing/tooling grants cover routine work. New paid resources,
credentials, unrelated private data or production operations are not implied grants.
If a feature encounters those boundaries, implement it as fully as possible within
them: real adapters, configuration/secret inputs, synthetic local integration tests
and clear external prerequisites, not a fake implementation or silent omission.
Missing rights, signing keys, production access or reference hardware is not a
reason to abandon that feature or halt other work. Defer only the genuinely external
exercise/qualification, retain it visibly unverified, and continue all available
coding/tests/reviews. The objective is to finish all feasible implementation work,
not to stop merely because release readiness cannot yet be demonstrated. No claim
that an unperformed check passed, or that unavailable prerequisites were satisfied.

## Autonomy and boundaries

The runner continuing across stopped agents implements D-256's **project-level**
autonomy; it does not require one giant session or routine owner “continue?” after
each task. Derive technical details and repair ambiguous documentation within
approved constraints. Escalate only demonstrated product choices, unavailable
required resources or genuine blockers, and continue independent approved work.

Tooling qualification exercises dispatch/one-unit exit, grouped-review priority,
fix verification, save/push failure, stop control and interrupted-work recovery in
isolated non-product fixtures; see the operations record for actual evidence.
Do not start coding merely because state files exist or a headless session can
be launched. Product execution now has explicit D-304 authorization; operator
budgets and actual build/device permission qualification remain required. This
authorization-recording change does not itself start the driver.
The safe default after interruption is to stop and preserve dirty work. Any
explicit resume must reconcile the recorded unit/owned paths first; qualification
must prove unrelated work is never discarded or blindly staged.
The shared active-run fence records the owning worktree, branch and state path.
Recovery must run in that exact ownership context; a clean sibling worktree cannot
acknowledge the original checkout's unfinished work.
