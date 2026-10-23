# Fresh independent F1–F3 verification — 2026-10-23

Verified **`bca5fc6d0c3a2e29e470f8ce94f183f804ea4e88`**, branch `perf/flow-verify`,
including repair **`c002b9401cadde5d787c96e05d46174e22f0df30`**. Bounded follow-up to
[F1–F3](flow-review-2026-10-23.md) and the [fix report](flow-fixes-2026-10-23.md),
not another full audit. Only this report was edited.

**Outcome: F1, F2 and F3 verified within the stated helper/fixture scope. No remaining
blocking reproduction found.** This does not certify operational parallel delivery,
product behavior, whole-R1 completion or release acceptance.

## F1 — verified: retained writer fence covers legacy run and recovery

Code anchors: [loop](../../scripts/implementation-loop.py), `Runner.managed_writer_fence`
and both callers, lines 949–995; [resource helper](../../scripts/implementation-resources.py),
lines 28–30, 45–85, 120–125.

- Both legacy `run` and `recover` check the Git-common-directory retained
  `resources/integration-writer.json` **under the shared writer OS lock**, before
  starting/recovering serial work. Absence of serial `active.json` does not bypass it.
- Independently executed
  `test_failed_managed_writer_fences_legacy_run_and_recover_until_owned_cleanup`
  ([resource tests](../../tests/test_implementation_resources.py):98–118): managed
  command exit 3 retains the fence; both operations reject after its lock closes;
  wrong-owner and red cleanup reject; matching successful cleanup records recovery.
  Legacy recovery still needs saved/pushed clean work; STOP still pauses run without
  a fake-agent call. Other passing fixtures cover sibling-worktree exclusion,
  live-owner exclusion, interrupted/killed wrappers and inherited child locks.
- Limit: cleanup output/ownership/hash are checked, not the semantic truth of arbitrary
  cleanup commands. The owning supervisor must inspect actual cleanup. No live fence,
  retained run or STOP was changed here.

## F2 — verified: fresh completion output, reusable old child inputs

Code anchors: [loop](../../scripts/implementation-loop.py), lines 100–117, 324–390,
909–924; [evidence helper](../../scripts/implementation-evidence.py), lines 119–135,
244–259, 323–338, 357–420.

- Both `implementationUnits` and `ancestorClosures` require a passing **required**
  check in the report's current run namespace. Artifact validation and serial saved
  acceptance also require that output in the actual changed-path set, not merely a
  matching old reference. Child readiness replay and ancestor scope/record guards remain.
- Independently executed runner fixtures
  `test_both_completion_arrays_reject_old_run_check_references` and
  `test_completion_output_must_actually_change_in_this_run`, plus evidence regression
  `test_old_child_output_is_input_not_current_ancestor_closure`: old-child-only closure
  rejects; retaining that child as input to fresh scoped reconciliation succeeds.
  Managed split/acceptance regression also rejects substituting old worker output.
- Exact committed child reuse still passes, including split-child selection and
  accepted managed source/test inputs; later changed inputs are reported for seam
  reconciliation rather than automatically rerunning unchanged product suites.
- Limit: fresh changed bytes/namespacing and captured exit/hash consistency are not
  proof that an arbitrary command honestly exercises the claimed product outcome.

## F3 — verified: usable read-only proposal and separate latest-state acceptance

Code anchors: [evidence helper](../../scripts/implementation-evidence.py),
`proposed_state`/`state_boundary`/`validate_proposal`, lines 146–264;
`validate_acceptance`, lines 267–341; CLI dispatch, lines 424–441.

- Worker proposals compare complete `before` objects and bounded `after` objects in
  memory. Only the four documented collections are mutable; no deletion/control
  replacement. Authoritative state must remain **byte-identical** to the worker base,
  even if allowlisted. Exact changed paths, source/test inputs, fresh output, role
  restrictions and real saved upstream are checked. Serial `validate` explicitly
  refuses managed reports. Actual CLI fixtures confirm no state writes or staging.
- Acceptance reads the exact worker commit/report/check bytes, requires a distinct
  acceptance report and latest-HEAD before/current-state after boundary, and restores
  normal priority/readiness and split scheduling checks. Original inherited
  prerequisites cannot be dropped; changed selected tasks require a fresh proposal.
  Independent review/verify anchors retain the worker's actual inspected base, not
  the later integration HEAD. Scope/outcomes, review obligations, operator/task gate
  authority, state inventory/history and immutable runner/authorization controls remain
  guarded. Fresh acceptance output cannot be replaced by unchanged worker output.
- Independently executed all 29 evidence regressions, including speculative dependent
  development followed by latest-state acceptance, newly due capability review,
  selected-task drift, stale snapshots, unrelated objects, runtime/build repair denial
  for review/verify, control/gate bypass, review-anchor invention and unpushed workers.
- Additional disposable-fixture seam: after saving a dependent worker, committed an
  overlapping `probe.txt` candidate result, advanced the prerequisite and validated
  with fresh reconciliation. Acceptance passed without relabeling the original worker
  report/base or copying its state. Changing original worker evidence then rejected
  with `Worker evidence bytes changed during integration`. This tests handoff mechanics,
  not real conflict repair or application integration correctness.
- The [compact schema](implementation-agent.md#managed-proposal-additional-report-field),
  [command sequence](implementation-runner.md#managed-handoff-example) and
  [sole-writer procedure](implementation-orchestrator.md#accept-and-integrate-one-writer)
  agree with the implementation: managed workers never edit state to satisfy serial
  validation; saved proposals are checked in their owning checkout; candidates save
  before acceptance; review anchors and old evidence retain their original meanings.
- Source-only API inspection: the new helper exposes the two documented CLI boundaries
  without session/merge/state-writing machinery. No separate new session API helper
  exists in this snapshot. The existing [OpenCode adapter](../../scripts/implementation-loop.py)
  at lines 629–695 still supplies fresh-session creation, exact model/directory checks,
  metadata/active inspection, permission monitoring and owned interruption. No API
  execution or model session was used to qualify it here.
- Limits are explicit, not inferred automation: the supervisor checks actual leased
  identity/paths, succeeded/inactive exact-high session metadata, grants, STOP/budget,
  changed source/review validity, integrated seam adequacy and post-save committed bytes,
  clean branch and actual upstream. Helper success alone is not live acceptance.

## Executed checks and preserved contracts

Each required suite was independently run **once**, exit 0, no skipped tests:

| Command | Result | Retained output under `/tmp/opencode/` |
| --- | --- | --- |
| `python3 -B scripts/test-implementation-loop.py` | **109 passed** | `flow-verify-2026-10-23-runner.log` |
| `python3 -B tests/test_implementation_evidence.py` | **29 passed** | `flow-verify-2026-10-23-evidence.log` |
| `python3 -B tests/test_implementation_resources.py` | **10 passed** | `flow-verify-2026-10-23-resources.log` |

Output SHA-256, respectively:

```text
a02214fb63c6c4f66604881690ad820de00c90f5225f58e7d0b1190fb21fd1bb
a8ae3f6041cc687bbced2f1bc80d7bfc0a471f59065606b0debd99c2c7887685
88054f3d694db2d6af48e3781a6c5996f00675c5a29a4f1d6c6505cc0305eb0a
```

The extra seam output is `flow-verify-2026-10-23-focused.log`. Runner fixtures also
recheck model/grant, stop/recovery, saved-byte, actual-upstream and scoped repair guards;
no protected source, prompt, permission profile or save script was edited by verification.
Report local links and whitespace were checked. All incoming headings/anchors in the
seven changed current contract/handoff documents remain present relative to `cf3e6d9`;
all **36 product task rows remain byte-identical to `b9a092d`**.

Current instructions have **no lane cap or five-unit checkpoints**; prefer useful
parallel development even with modest overlap, delegate candidate merges/conflict
repairs/checks to fresh integration subagents, retain one supervisor live acceptance/
state writer, and serialize genuinely exclusive resources only. The old review's
scheduling wording is historical, not a current finding. Original budget, exact-high/
no-fallback, scoped grants, stop drain, independent review/fresh verification and final
full-suite/fix/release obligations remain.

All executable checks used disposable Git/fake-agent/local-command fixtures. No
application full suite, native/device/emulator qualification, live loop, service,
model session, supervisor or worker was launched; no main-state/journal/STOP mutation.
The owner's requested **new fresh-context exact-high supervisor**, continuing through
all feasible R1 final tests/fixes, remains a separate parent handoff action—not something
launched or certified by this verification.
