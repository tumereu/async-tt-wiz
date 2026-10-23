# One implementation-loop unit

The supplied `THIS RUN` identifies repository, role/target, start commit, session,
report and budget. Use **`openai/gpt-6.1-sol#high`**, no fallback. Follow AGENTS.md
and the canonical [loop contract](implementation-loop.md); read only the selected
task/current findings and relevant product/test sources. History is lookup-only.
No subagents, new loop, deployment or paid resources from a unit.

1. Confirm Git/start commit, authorization, role/selection and owned paths. In a
   managed lane also confirm its lease and pinned dependencies; do not edit the
   integration checkout or authoritative state. Managed dispatch may lease useful
   speculative development before prerequisites are accepted; this is not permission
   to claim them complete. Latest-state priority/prerequisites guard acceptance.
   The [orchestrator](implementation-orchestrator.md) owns dispatch and discrepancies.
2. Deliver one bounded usable outcome. Writers implement UI/app/test code, save/push
   and hand execution to separate executors; do not run builds/tests/apps/inspections.
   Next-feature writers and fix writers proceed concurrently on separate branches
   before validation; pending remains **UNVERIFIED**. Split only when useful.
   Qualified D-309 `implementationUnits` allows decomposition
   plus a ready first child in this session; explicit `ancestorClosures` can reconcile
    completed ancestry. Follow the exact report schemas in the contract; no unrelated
    advancement or extra closure-only session when evidence already covers the parent.
3. Executors run assigned existing checks/apps/inspections, report exact failures
   and return repairs to fresh writers; no source/test fixes or extra scenarios.
   Use incremental affected checks, no confidence full-suite/clean-build reruns or
   bespoke audits. Final qualification/reviews remain required. Run dependent
   commands in the foreground (`timeout: 0` when needed); do not exit while a
   background check still owns the handoff/resources. Writers still perform static
   ownership/whitespace/save checks, not behavioral validation.
4. Retain actual output, including failed diagnostics, under this run's namespace.
   Prefer `python3 scripts/capture-check.py <run-directory>/check.json -- <command> ...`;
    small truthful summaries suffice. Assigned requirement
   IDs are not executed coverage. Record deferred assertions and their final owner.
5. Serial unit: write report, apply permitted state transitions and use `validate`.
   Managed worker: leave state byte-identical; include the bounded `proposal` schema
   below and use `validate-proposal`, **not** serial `validate`. Save/push owned paths,
   then run `validate-proposal REPORT --saved` and exit. Only the supervisor applies
   latest-state transitions with separate `validate-acceptance`. Actual saved bytes,
   upstream and session metadata—not final prose—are the handoff.

## Report

Required fields (actual supplied values):

```json
{
  "runId": "supplied", "sessionID": "supplied", "startCommit": "supplied",
  "mode": "supplied", "target": "supplied", "findingIds": [],
  "newFindingIds": [], "reviewedUnitIds": [], "outcome": "progress",
  "summary": "Delivered result, affected scope and remaining limitation",
  "selectionReason": "Why this ready unit outranks alternatives",
  "checks": [{"kind": "required", "command": "actual command", "exitCode": 0,
              "evidence": "docs/agent/implementation-runs/run-id/check.json"}],
  "changedPaths": ["exact committed changed-path set including report/evidence"]
}
```

`progress` must advance real work, not notes; `blocked` means no safe runnable work
remains, not one blocked task. `complete` requires all completion/release criteria.
Required checks pass for success; red reproductions are `diagnostic`, not passes.
Keep all referenced output committed byte-for-byte; redact secrets/private data.
Helpers/fixtures need exact check `supportPaths` under this run's `-...` or `/...`
namespace, included in `changedPaths`; never runtime/build inputs or older reports.
Optional report/state fields and role transitions live in the contract, not history.
Use the [shared report/validation/JUnit/reuse commands](implementation-runner.md#shared-evidence-commands).
For native work reserve the [complete resource bundle](test-resource-plan.md) first;
a green phase continuation is not a full journey. Do not invent a new audit framework.

### Managed proposal (additional report field)

```json
{"proposal": {
  "sourceIds": ["assigned requirement ID"],
  "sourcePaths": ["src/affected-file"], "testPaths": ["tests/affected-test"],
  "allowedPaths": ["src/affected-file", "tests/affected-test",
    "docs/agent/implementation-runs/run-id.json",
    "docs/agent/implementation-runs/run-id/check.json"],
  "changes": [{"collection": "tasks", "id": "task-id",
    "before": {"id": "task-id", "batch": "batch-id", "status": "pending"},
    "after": {"id": "task-id", "batch": "batch-id", "status": "implemented",
      "implementationRecord": "docs/agent/implementation-runs/run-id.json"}}]
}}
```

Use exact writable lease-owned `allowedPaths` (no globs), real source/test inputs
(which may be read-only, outside `allowedPaths`) and **complete original
objects** in `before`, bounded replacements in `after`; the example's task is simplified.
Collections: `tasks`, `batches`, `findings`, `externalReleaseGates`. New child/finding
objects use `before: null`; no deletions or whole-state copy. Include affected batch
changes and normal role/report fields. All checks/support are fresh run-owned output.
`sourceIds` are traceability, not a test pass. Managed units propose `progress` only;
global blocked/completion decisions remain with the sole supervisor and final gates.
The validator applies this proposal in memory for role checks, never writes state,
and does not certify dispatch readiness, lease ownership or actual model metadata.

### Autonomous troubleshooting and orchestration repairs — D-305

Diagnose/repair ordinary build/tool/save failures, including across fresh sessions.
For unfinished implement/fix work use the contract's evidence-backed
[checkpoint/repair fields](implementation-loop.md#autonomous-infrastructure-troubleshooting--d-305).
Keep the feature unfinished and record fresh evidence/next steps. Reviewers and
verifiers hand source/build repairs to a fresh fixing session; no self-certification.

### Bounded group-review checkpoints

Review coherent capability groups, not each child. Split oversized review coverage
only in a planning-only review unit; retain the whole scope. Fresh reviewers report
checked leaf `reviewedUnitIds` at `startCommit`; blocked scopes stay incomplete.
See [review transitions](implementation-loop.md#review-a-group). Targeted fix
verification never replaces first group review. Final B8 includes every current
mock/state/orientation and implemented unpictured interface, with discrepancies
fixed and independently rechecked before closure.

## Finish

Serial units append the report to `runs` and leave `activeRun` null; never change
authorization, permission profile or runner controls. Managed workers do not touch
those fields at all. Inspect index; run applicable checks; use
`./scripts/save.sh <only owned paths...>`. Troubleshoot/retry the same failed save.
Stop before timeout with a saved checkpoint, not after it kills work. Exit promptly
after a safe handoff; do not select/execute the next unit.

[Pre-compaction prompt/history](https://github.com/tumereu/async-tt-wiz/blob/b9a092dc212deca204e039e1791f7391e51f392a/docs/agent/implementation-agent.md)
is provenance, not additional mandatory reading.
