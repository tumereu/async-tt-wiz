# One implementation-loop unit

The supplied `THIS RUN` identifies repository, role/target, start commit, session,
report and budget. Use **`openai/gpt-6.1-sol#high`**, no fallback. Follow AGENTS.md
and the canonical [loop contract](implementation-loop.md); read only the selected
task/current findings and relevant product/test sources. History is lookup-only.
No subagents, new loop, deployment or paid resources from a unit.

1. Confirm Git/start commit, authorization, role/selection and owned paths. In a
   managed lane also confirm its lease and pinned dependencies; do not edit the
   integration checkout or authoritative state. Resolve discrepancies, do not skip
   priority/role guards. The [orchestrator](implementation-orchestrator.md) owns dispatch.
2. Deliver one bounded usable outcome. Implement affected tests alongside code;
   split only when useful. Qualified D-309 `implementationUnits` allows decomposition
   plus a ready first child in this session; explicit `ancestorClosures` can reconcile
   completed ancestry. Use neither extension until the running validator supports it.
   Otherwise keep the legacy planning-only split. No unrelated advancement.
3. Run/fix affected checks using incremental builds; stop once green unless evidence
   identifies another affected input/assertion. No confidence full-suite/clean-build
   reruns or bespoke 100-line audits. Final qualification/reviews remain required.
   Run dependent commands in the foreground (`timeout: 0` when needed); do not exit
   while a background check still owns the handoff/resources.
4. Retain actual output, including failed diagnostics, under this run's namespace.
   Prefer `python3 scripts/capture-check.py <run-directory>/check.json -- <command> ...`;
   create that directory first. Small truthful summaries suffice. Assigned requirement
   IDs are not executed coverage. Record deferred assertions and their final owner.
5. Write the report, apply only permitted state transitions (serial unit), validate,
   save/push owned paths and exit. A managed worker instead proposes transitions in
   its report; only the integrator applies them after acceptance. Report/state/check
   bytes and actual remote confirmation—not final prose—are the handoff.

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
The planned shared `implementation-evidence.py` helper (`report`, `validate`,
`retain-junit`, `reconcile`) is not available until implemented/qualified; do not
guess its CLI or substitute fabricated evidence.

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
