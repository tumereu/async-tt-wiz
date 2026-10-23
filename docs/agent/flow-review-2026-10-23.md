# Independent bounded flow review — 2026-10-23

Reviewed **`cf3e6d9c51cc049ded803f3c4c767eb5d745bc33`**, diff from `b9a092d`,
on `perf/flow-review`. Report only; no implementation/runtime repairs.

**Outcome: three P2 findings need correction before relying on the advertised managed
handoff/fencing guarantees.** No product release or operational parallel qualification
is claimed. P2 means a concrete normal recovery/evidence/workflow gap, not a speculative
hostile-owner attack or a requirement for a new daemon.

## F1 — Retained integration-writer fence does not fence the legacy controller

Anchors: [resource lease](../../scripts/implementation-resources.py), lines 52–85,
120–126; [legacy preflight](../../scripts/implementation-loop.py), lines 933–952.

The new wrapper shares `writer.lock` with the serial controller while running, but
on nonzero exit only retains `resources/integration-writer.json`. The controller
checks the OS lock and serial `active.json`, not that retained resource fence.
A managed supervisor failure can therefore leave remote workers/reconciliation
unfinished while an ordinary mistaken legacy restart passes its ownership preflight.
This defeats the durable fence precisely when recovery ownership matters.

**Narrow reproduction executed:** in a disposable Git fixture, call `lease` with
owner `review-owner`, resource `integration-writer` and a Python command exiting 3.
Afterward, acquire the exact legacy `writer.lock` with nonblocking exclusive `flock`
and inspect `active.json`. Result:

```text
exit=3; retainedFence=True; legacyLockAcquired=True; serialActiveExists=False
```

No controller/session was launched. The remaining preflight has no check for this
fence. **Correction:** make legacy launch/recovery respect the retained managed-writer
fence until explicit owned cleanup/recovery; add this failed-wrapper seam regression,
not just the existing live-lock exclusion test.

## F2 — D-309 completion checks accept old-run output as current closure evidence

Anchors: [transition checks](../../scripts/implementation-loop.py), lines 100–102,
309–315, 352–375; [current-run requirement](implementation-loop.md#implement).

`implementationUnits` and `ancestorClosures` only require a matching reported passing
check. Neither requires its evidence to belong to this run. Implement-role path
validation returns without an evidence-namespace restriction. Consequently a last
child can close its parent using an unchanged old child's output, with no current
parent reconciliation output, despite the explicit current-run scoped-check contract.

**Narrow reproduction executed:** use the existing `GitFixture`; commit an
in-progress F1 parent/pending F1a child and `implementation-runs/older/check.txt`.
Implement F1a and close F1 in a new report whose sole required check references that
old file. `implementation-evidence.validate` succeeds; its entire changed-path set is:

```text
docs/agent/implementation-runs/fresh-closure.json
docs/agent/implementation-state.json
```

The pure transition fixture also accepts the stale reference. No fresh check output
exists. **Correction:** enforce run-owned current evidence for both new completion
arrays, preserving explicit reuse of old child records as inputs to a fresh scoped
reconciliation. This does not require rerunning unchanged product tests or pretending
the validator can prove test honesty.

## F3 — Managed workers are directed into a serial-only validation protocol

Anchors: [compact prompt](implementation-agent.md), steps 1/5;
[shared commands](implementation-runner.md#shared-evidence-commands);
[validator](../../scripts/implementation-evidence.py), lines 85–126;
[managed acceptance](implementation-orchestrator.md#accept-and-integrate-one-writer).

Managed workers must leave authoritative state untouched, yet the shared compact
handoff directs them to validate using a helper that requires completed serial task
transitions, appended serial run history and a changed authoritative state path.
There is no documented managed proposal schema/validation alternative. Following
the advertised path therefore either fails every compliant managed handoff or
encourages worker-state edits/the guard bypass the instructions forbid.

**Narrow reproduction executed:** generate a valid managed F1 report/check/source
change with `make_report`, leave state unchanged, then call `validate`. It rejects:
`Implementation unit needs a completed outcome, evidenced blocker, planning-only
split or troubleshooting`. Later history/changed-path checks also require state edits.

**Correction:** distinguish worker report/path/evidence validation from the sole
writer's current-state transition validation; document the minimal proposed-transition
shape and acceptance path without copying worker state or weakening role/selection
guards. No parallel CLI is requested. Also remove the conflicting current D-309
[authorization](implementation-authorization.md#managed-flow-improvements-and-shorter-instructions--d-309)
fallback “otherwise use the existing serial CLI”, which contradicts the explicit
orchestrator/handoff prohibition on silently restarting that workflow.

## Scope, evidence and limits

- Subsequent owner steering supersedes the reviewed two-lane cap/global drain:
  use as many ready nonconflicting lanes as actual dependencies/quota/host/exclusive
  leases permit, with rolling assessment after five completed units. Final instruction
  consistency and the newly requested fresh-context supervisor are parent follow-ups,
  not defects attributed to this immutable snapshot. I launched no supervisor.
- Inspected graph traversal, split/new-child replay, ancestor closure, role/review/gate
  seams, evidence reuse, cross-worktree resources, native artifact/UI/phase helpers,
  three modified native journeys and compact orchestration/resource instructions.
- Confirmed all **36 original product task table rows remain byte-identical**.
  Found no additional concrete cycle/review/gate or native source defect in this scope.
- Reused the supplied 107 runner, 10 evidence, nine resource and native 91 Python/
  four Kotlin/source-compilation results; did not rerun green suites for confidence.
  Added only the disposable/pure missing-seam reproductions above.
- No devices, services, implementation loop, model sessions or workers launched;
  no live state/journal changes. Native gestures/keyboard behavior remains explicitly
  device-unvalidated. Parallel operation, full R1, full-suite and release acceptance
  remain outside this bounded review.
