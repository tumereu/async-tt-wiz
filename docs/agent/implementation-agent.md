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
   adding independent reviews after children. Reviews follow complete capability
   groups. Fix/verify a bounded subset of the supplied finding IDs; record which.
3. This is a casual cooperative simulator. Review usable behavior and evidenced
   harmful failures, not style, hypothetical enterprise/competitive guarantees or
   superseded reset/message-order cases. Follow the loop contract's review policy.
   A verification run checks fixes and affected behavior, not a new broad audit.
4. Run applicable checks and retain their actual output. A diagnostic reproducing
   a review finding may fail; mark it `diagnostic`. Required validation must pass
   for a successful unit. No skipped/red required check may be labeled passing.
5. Update task/batch/finding status and append this run's report path to `runs`.
   Leave `activeRun` null at the boundary. Do not change authorization, its owner
   record, runner configuration, driver, this prompt or permission profile.
   At the last implemented task set its batch `ready-for-review`. A clean grouped
   review sets `reviewed` and `reviewedCommit` to the exact code commit inspected;
   findings set `changes-requested`. A fix sets issues `fixed-awaiting-check` and
   batch `fixes-awaiting-check`; a separate verification sets issues `verified`
   and the batch `reviewed` if no actionable issue remains. Preserve rejected
   findings/reasons. Only materially affected behavior reopens an earlier review.
6. Write the JSON report at the supplied `report` path (repository-relative):

```json
{
  "runId": "supplied runId",
  "sessionID": "supplied sessionID",
  "startCommit": "supplied startCommit",
  "mode": "supplied selection.mode",
  "target": "supplied selection.target",
  "findingIds": [],
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

Inspect status/index. Save only owned paths with
`./scripts/save.sh <changed paths...>` after required checks pass. Include exactly
the committed changed-path set in the report. Do not stage unrelated work, force
push, bypass hooks, reset/discard files or retry implementation because push failed.
If validation/save/push fails, leave evidence and report failure; the driver stops
and preserves the workspace for inspection. No success handoff on a failed save.

Then **exit**. A short final summary is useful but the driver relies on committed
report/state, actual upstream confirmation and session metadata—not your last
sentence. Do not wait for or execute the next unit. The driver records the resulting
commit outside Git, since a report cannot contain its own commit hash.
