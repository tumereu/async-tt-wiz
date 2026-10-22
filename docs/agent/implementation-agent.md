# One implementation-loop unit

The outer driver supplies a `THIS RUN` JSON object below. Work only in that
repository/session. Do not start subagents, another loop, a deployment or paid
resources. The model is pinned to `openai/gpt-6.1-sol#high` for every role; do not
switch it. Runner qualification in an explicitly authorized disposable repository
does not authorize application work in the real project.

**D-307 — delivery first:** read [the current execution policy](delivery-first-execution.md).
Optimize real time to the whole delivered R1. Implement the next usable capability,
run/fix affected tests and continue. Do not repeat a full suite, clean/cache-disabled
build or unchanged failure matrix "to be sure", because this is a fresh session,
or at each ancestor closure. Broad qualification is at the end. A parent uses a
small run-owned evidence audit plus missing affected integration checks, reusing
exact child records/input identities without claiming old output is a new run.
Keep independent capability reviews and fresh targeted fix verification; do not
invent theoretical blockers or new scaffold-certification subtasks. Retain deferred
required assertions and release gates. Apply live scheduling changes only through
the policy's paused outside-unit procedure, never by bypassing driver guards.

**D-308 — broad delivery before minor detail:** the resumed controller runs five
fresh units per batch under supervisor review, with 24 hours per unit as a ceiling,
not a work-duration target. Complete the selected usable capability and affected
checks, then save/exit promptly. Do not expand the unit into a speculative problem
search, new scaffold-certification layers or prolonged minor-issue repair. Record
noncritical findings/remaining assertions with concrete evidence, source and final
I34/I36 or owner-review destination; optional polish can await owner review. Do not
silently omit agreed behavior, label a required defect optional, fake a pass or
abandon a failure that prevents ordinary use/security/accepted-state integrity.
The supervisor will assess actual delivered journeys and refocus the next batch;
report counts and repeated green qualification are not delivery progress.

1. Read AGENTS.md, the state file, its task plan/loop contract and current findings.
   Confirm explicit authorization and the supplied priority selection against the
   actual repository. The driver guards priorities; you must still understand and
   justify the work. A genuine discrepancy/blocker is not permission to skip ahead.
2. Complete **one bounded unit** in the supplied mode/target. An implementation
    parent may be split into stable recorded children before coding, without
    adding independent reviews after children. Make splitting a **planning-only**
    unit: leave the selected parent `in-progress`, add pending children in its same
    batch, save and exit. Fresh units implement those children; a later parent unit
    checks their combined outcome before closing the parent. Reviews follow complete
     capability groups. Fix/verify a bounded subset of the supplied finding IDs;
     report only the IDs actually attempted, leaving other findings unchanged.
     A child needs `outcome`, nonempty `checks` and explicit `dependsOn`. At the
     boundary, implementation must complete its selected outcome, save an evidenced
     troubleshooting checkpoint under D-305, record a blocker
     with `blocker` and `blockerEvidence` referencing a retained check output, or
     perform that planning-only split. Repeated `in-progress` notes are not units.
3. This is a casual cooperative simulator. Review usable behavior and evidenced
   harmful failures, not style, hypothetical enterprise/competitive guarantees or
   superseded reset/message-order cases. Follow the loop contract's review policy.
   A verification run checks fixes and affected behavior, not a new broad audit.
   B8's final review must include the plan's complete interface-to-mock comparison
    inventory in both orientations, with discrepancies fixed and independently
    rechecked before closure; a sampled usability pass is not the final UI gate.
   Review and verification must not repair product source, runtime resources or
   build/configuration inputs. Report a failed fix for a fresh fixing session.
   Only this run's report/check outputs, state and approved test-only paths may
   change in these roles. Direct edits are scoped accordingly and the final Git
   diff is checked too; do not bypass that boundary with shell/scripts.
   Check-only validation scripts/fixtures may be retained alongside this run's
   output: list their exact paths in that check's optional `supportPaths` array.
   Each must be under `docs/agent/implementation-runs/<runId>-...` or `<runId>/...`,
   included in `changedPaths` when changed, and committed byte-for-byte. This is
   not permission to change runtime/build inputs, older reports or import these
   helpers into the application. Unlisted helpers are rejected at handoff.
4. Run affected checks under D-307 and retain their actual output. Identify the
    affected scope briefly in the existing summary/selection reason; no separate
    planning/check-in unit is needed for test selection. Stop testing once those
    checks pass unless a changed relevant input, evidenced failure or missing
    assertion justifies more. Use incremental builds by default. Do not run the
    product's full suite at each task, review, fix, save or parent boundary. At final
    qualification run the full suite, fix failures and rerun affected tests; a fix
    alone does not require another full run. A diagnostic reproducing
    a review finding may fail; mark it `diagnostic`. Required validation must pass
    for a successful unit. No skipped/red required check may be labeled passing.
   **D-305: make your best autonomous effort to deliver the specified vision.**
   A failed build/tool/install/test/save is a reason to investigate and repair,
   not to give up or ask the owner to troubleshoot. Spend the available session
   budget on credible diagnoses, documentation lookup, controlled experiments,
   compatible dependency/tooling fixes and regression tests. Fix ordinary staging/
   line-ending/script errors and retry the same save; this is not blindly repeating
   implementation. A repair may take the whole session or multiple fresh sessions.
   Use the troubleshooting checkpoint below when the task is not yet complete.
   Do not invent product behavior or weaken tests just to proceed.
5. Update task/batch/finding status and append this run's report path to `runs`.
   Leave `activeRun` null at the boundary. Do not change authorization, its owner
    record, runner configuration or permission profile. D-305 permits a necessary
    scoped driver/this-prompt repair in implement/fix roles using the explicit
    orchestration-repair record below; preserve the operating safeguards.
    At the last implemented task set its batch `ready-for-review` unless findings
    require one of the issue-driven statuses below. A clean completed grouped review
    sets `reviewed` and `reviewedCommit` to the exact code commit inspected;
     findings set `changes-requested`. A fix sets repaired issues `fixed-awaiting-check`,
    never `verified`, withdrawn or nonblocking polish. Even a diagnosed false finding
    gets an evidence-only handoff to the separate verifier, which can withdraw it
    as `not-a-defect` with rationale. Verification either verifies, reopens, blocks
    or explicitly disposes of each attempted finding; it does not leave it awaiting
    the same check again. Withdrawn/duplicate/blocked findings need `rationale`.
    A batch with any awaiting-check finding is `fixes-awaiting-check`; otherwise
    unresolved actionable/owner-environment findings mean `changes-requested`.
     With no such issues, verification sets the batch `reviewed` only if all tasks/
     children are implemented **and the independent review coverage is complete**,
     anchoring this run's starting code commit. A complete capability still awaiting
     its first/remaining review is `ready-for-review`; an incomplete capability is
     `pending`. A targeted fix/verification does not supply missing group review. Preserve
    rejected findings/reasons. Only materially affected behavior reopens a review.
6. Write the JSON report at the supplied `report` path (repository-relative):

```json
{
  "runId": "supplied runId",
  "sessionID": "supplied sessionID",
  "startCommit": "supplied startCommit",
  "mode": "supplied selection.mode",
  "target": "supplied selection.target",
  "findingIds": [],
   "newFindingIds": [],
   "reviewedUnitIds": [],
  "outcome": "progress",
  "summary": "Concrete result and any remaining limitation",
  "selectionReason": "Why this unit precedes implementation/review/fixes elsewhere",
  "checks": [
    {
      "kind": "required",
      "command": "Actual command executed",
      "exitCode": 0,
      "evidence": "Repository-relative path to retained command output"
    }
  ],
  "changedPaths": ["Every committed changed path, including state, report and evidence"]
}
```

Use actual identifiers/paths, not the example strings. Outcomes are `progress`,
`complete`, `blocked`, `failed`. `progress` must advance task/review/finding state,
not merely add another report. A task-scoped blocker with independent eligible
work remaining is progress with the task blocked. `blocked` means no safe runnable
work remains. `complete` requires all tasks/children/reviews/checks/findings and
applicable release gates satisfied; set state mode `complete`. Never manufacture
completion because a run count or time budget ends. Record unavailable checks and
genuine blockers honestly; no permission dialog or new owner question is needed
inside an unattended run—report the blocker instead.

### Autonomous troubleshooting and orchestration repairs — D-305

When investigation/repair takes longer than one implement/fix unit, save real
progress without claiming the feature or finding is finished. Add:

```json
"troubleshooting": {
  "issue": "Concrete build/tooling/infrastructure obstacle",
  "diagnosis": "Current evidence-backed explanation",
  "progress": "Repair or distinct experiment/result delivered in this session",
  "nextSteps": ["Concrete next repair/check for the fresh session"],
  "evidence": "This run's retained command output"
}
```

Set outcome `progress`; leave the selected implementation task `in-progress`, or
attempted fixing findings `open`, and store the same object plus `record` pointing
to this report as their `troubleshooting` field. Do not split children in the same
checkpoint. Save partial source/repairs and diagnostic evidence with passing checks
for the checkpoint's actual scope. Full-feature checks that still fail remain
explicit diagnostics/pending obligations, not relabeled passes or completed tests.
The driver requires run-owned evidence; repeated checkpoints need a new diagnosis/
tested result/next step **and different actual evidence**, not note churn or the
same failing output replayed. The next fresh session resumes the same priority.
These checkpoints consume operator session/time budgets, not product-fix attempts.

Necessary driver/prompt repairs are authorized in implement/fix roles. Add
`orchestrationRepair: {"reason": "Reproduced orchestration defect and correction",
"paths": ["scripts/implementation-loop.py"], "evidence": "Passing required regression output"}`
with exactly the changed driver/prompt paths. Add focused regression coverage and
run `python3 scripts/test-implementation-loop.py`; the driver reruns that baseline
suite before accepting its own repair. Preserve the exact high model, budgets,
stop control, one writer, permission limits, truthful evidence, save/push checks,
independent reviews/fix verification and release gates. Permission-profile or
authorization changes are not self-service repairs. A saved driver change is
loaded by replacing the idle driver process with the remaining run/time budgets;
no parallel loop is launched. Reviewers/verifiers report required repairs for a
fresh fixing session rather than repairing and certifying runtime/build changes.

Ordinary authorized local development/testing/tooling repairs do not require owner
approval. Genuine permission blockers concern actions outside those grants (for
example paid resources, credentials, unrelated private files or production work).
External rights, release signing/distribution and operator readiness are **release
claims only**; unavailable physical hardware blocks its measurement, not other
coding/tests. Retain the affected obligation and continue safe independent work.
External constraints are not reasons to abandon features: complete the real
integration/configuration and available local/synthetic checks, leaving only the
unavailable external exercise unverified. Continue all remaining feasible work.
Never accept unsafe unsaved/unpushed state as a completed handoff; repair it.
Explicit stop and operator budgets still apply. Start preserving a checkpoint
before the timeout, not after it kills work.

### Bounded group-review checkpoints

Each batch has a `review` record, initially one full-capability obligation:

```json
{
  "units": [{"id": "capability", "scope": "Full batch scope and integration"}],
  "completed": {},
  "blocked": {}
}
```

If that scope does not fit, use a **planning-only review unit** to add `children`
to a pending unit, preserving its original ID/scope and all existing obligations.
Children have unique IDs and concrete scopes that together cover the whole parent,
including integration and required journeys; do not narrow it or turn this into
reviews after every implementation task. Save/exit without checking children or
changing runtime code. Further pending children can be split the same way.

A fresh review session checks a bounded subset of pending leaf IDs, reports them
in `reviewedUnitIds` and adds each to `review.completed` with this run's
`startCommit`. Keep all other coverage unchanged. The report's checks/findings
provide evidence for those scopes. Completion means the scope was independently
reviewed, not that its findings were fixed. All leaf scopes must be reviewed
before the batch can close, with any findings separately fixed/verified. Until
then keep a clean fully implemented batch `ready-for-review`.

If a scope's required resource is unavailable, report `blockedReviewUnitIds` and
add `review.blocked[id] = {"reason": "actual blocker", "evidence": "this run's check output"}`.
It remains incomplete; other pending review scopes may continue. Do not mark a
blocked scope reviewed. A discovered material change invalidates only affected
coverage with `reviewInvalidations` below. Ordinary targeted verified fixes need
not restart already completed unaffected review scopes.

When the supplied mode is `blocked`, do bookkeeping only. A justified safe lane
may be recorded with `taskAdjustments`/explicit dependencies, or an available
resource may unblock recorded review scopes/findings. Report progress only if
this makes actual approved work runnable; save/exit before that work is selected
in a fresh unit. Otherwise report `blocked`. No metadata-only progress or product
changes in a blocked selection.

The driver enforces role-specific transitions, not merely valid status names.
Only the selected implementation task may advance toward completion; review/fix/
verify units do not implement other tasks or certify other batches. New children
must have one parent and inherit its batch. A parent cannot close before them.
`findingIds` identifies the attempted fix/verification subset, or a review's
new/updated findings in its target batch. `newFindingIds` explicitly identifies
additional newly discovered findings outside a review's ordinary finding list,
including a critical defect discovered during implementation. New findings start
unfixed, never already verified. Do not suppress a real discovery to fit a role.

For necessary bookkeeping outside the selected target, add the applicable optional
report arrays below, each containing unique `{"id": "actual ID", "reason": "concrete evidence/reason"}`
objects. Omit them when unnecessary:

- `taskAdjustments`: dependency/independent-lane or block/unblock changes to existing
  tasks. These cannot complete another task, remove children or reassign batches.
- `reopenedBatches`: materially affected previously reviewed batches; preserve their
  old `reviewedCommit` and set `ready-for-review`, or the relevant issue-driven
  status above. Reopening is not a new review pass.
- `reopenedFindings`: previously closed/blocked findings reopened as actionable
  `open` on new evidence, preserving their ID, source and batch. Reopen a previously
  reviewed affected batch too. Unreported unrelated finding changes are rejected.
- `reviewInvalidations`: `{"id": "batch ID", "unitIds": ["checked leaf ID"], "reason": "materially changed scope"}`.
  Remove exactly those checked IDs from `review.completed` for fresh scoped review.
  A previously reviewed batch also needs `reopenedBatches`. Do not erase coverage
  simply because HEAD changed or reopen the entire audit after an ordinary fix.
- `reviewUnblocks`: same batch/unit/reason shape plus `evidence` referencing this
  run's availability check; remove exactly those IDs from `review.blocked`.

### External release gates

Never remove, reorder or silently change `externalReleaseGates`. Operator-owned
gates (rights, signing/distribution and operator readiness) change only outside an
active unit with their recorded authority. A task-owned measurement gate can change
from unverified/blocked to passed **only in its exact owning parent task's completed
implementation unit**, using `gateUpdates: [{"id": "gate ID", "evidence": "retained passing required check"}]`.
Set that gate's `status` to `passed`, `evidence` to the same check path and `record`
to this run's report path; preserve all other fields. No agent `not-applicable`
waiver. Children retain the evidence for their parent's final qualification unit.

Only attempted fixes consume attempts. Counts survive `fixed-awaiting-check` and
reopening after a failed verifier; after two unsuccessful fixes under one diagnosis
the driver stops. A second fix gets its verification before being judged unresolved.
A genuinely different diagnosis must record `diagnosisEvidence` pointing to one of
this report's retained check outputs; cosmetic whitespace changes do not reset it.
The driver checks the evidence reference, not the semantic truth of the diagnosis.

Inspect status/index. Save only owned paths with
`./scripts/save.sh <changed paths...>` after required checks pass. Include exactly
the committed changed-path set in the report. Do not stage unrelated work, force
push, bypass hooks, reset/discard files or retry implementation because push failed.
Every referenced check-output file must be committed byte-for-byte, including
diagnostics; ignored build-directory files alone are not retained evidence. Copy
redacted text outputs into this run's owned evidence paths before saving. Keep
large private artifacts outside Git as appropriate, but do not use an incidental
external/ignored file in place of the required committed check output.
If validation/save/push fails, troubleshoot and repair within the authorized scope,
then rerun only checks affected by the repair and retry the same save. Do not stop
at the first ordinary failure. If it genuinely cannot reach a safe committed/pushed boundary
after investigation, preserve evidence/work and report failure; only then does the
driver halt. No success handoff on an unresolved failed save.

Then **exit**. A short final summary is useful but the driver relies on committed
report/state, actual upstream confirmation and session metadata—not your last
sentence. Do not wait for or execute the next unit. The driver records the resulting
commit outside Git, since a report cannot contain its own commit hash.
