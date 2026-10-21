# One implementation-loop unit

The outer driver supplies a `THIS RUN` JSON object below. Work only in that
repository/session. Do not start subagents, another loop, a deployment or paid
resources. The model is pinned to `openai/gpt-6.1-sol#high` for every role; do not
switch it. Runner qualification in an explicitly authorized disposable repository
does not authorize application work in the real project.

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
3. This is a casual cooperative simulator. Review usable behavior and evidenced
   harmful failures, not style, hypothetical enterprise/competitive guarantees or
   superseded reset/message-order cases. Follow the loop contract's review policy.
   A verification run checks fixes and affected behavior, not a new broad audit.
   B8's final review must include the plan's complete interface-to-mock comparison
   inventory in both orientations, with discrepancies fixed and independently
   rechecked before closure; a sampled usability pass is not the final UI gate.
4. Run applicable checks and retain their actual output. A diagnostic reproducing
   a review finding may fail; mark it `diagnostic`. Required validation must pass
   for a successful unit. No skipped/red required check may be labeled passing.
5. Update task/batch/finding status and append this run's report path to `runs`.
   Leave `activeRun` null at the boundary. Do not change authorization, its owner
   record, runner configuration, driver, this prompt or permission profile.
   At the last implemented task set its batch `ready-for-review`. A clean grouped
   review sets `reviewed` and `reviewedCommit` to the exact code commit inspected;
    findings set `changes-requested`. A fix sets repaired issues `fixed-awaiting-check`,
    never `verified`, withdrawn or nonblocking polish. Even a diagnosed false finding
    gets an evidence-only handoff to the separate verifier, which can withdraw it
    as `not-a-defect` with rationale. Verification either verifies, reopens, blocks
    or explicitly disposes of each attempted finding; it does not leave it awaiting
    the same check again. Withdrawn/duplicate/blocked findings need `rationale`.
    A batch with any awaiting-check finding is `fixes-awaiting-check`; otherwise
    unresolved actionable/owner-environment findings mean `changes-requested`.
    With no such issues, verification sets the batch `reviewed` only if all tasks/
    children are implemented, anchoring this run's starting code commit; an early
    critical repair instead returns the incomplete batch to `pending`. Preserve
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
If validation/save/push fails, leave evidence and report failure; the driver stops
and preserves the workspace for inspection. No success handoff on a failed save.

Then **exit**. A short final summary is useful but the driver relies on committed
report/state, actual upstream confirmation and session metadata—not your last
sentence. Do not wait for or execute the next unit. The driver records the resulting
commit outside Git, since a report cannot contain its own commit hash.
