# F1–F3 flow repairs — 2026-10-23

Base: `c82ccda1ac4114b5aaefc57e88215c9534988396`, branch `perf/flow-fix`.
Scope: [independent findings](flow-review-2026-10-23.md), loop/evidence helpers,
focused qualification and managed handoff instructions only.

| Finding | Disposition | Repair / regression outcome |
| --- | --- | --- |
| F1 | **fixed-awaiting-check** | Legacy `run` **and** `recover` check the retained Git-common-directory `resources/integration-writer.json` under the shared OS lock. A failed managed command still fences both after its OS lock closes; wrong-owner/red cleanup cannot release it. Exact successful owned resource recovery records acknowledgement before legacy recovery can proceed. No manual fence deletion or STOP clearing. |
| F2 | **fixed-awaiting-check** | Both `implementationUnits` and `ancestorClosures` require current-run namespaced passing checks, with actual changed output checked at artifact/save boundaries. Old unchanged child output alone is rejected; old records remain usable inputs to a fresh scoped reconciliation. Pure fixtures now supply run IDs/namespaced evidence rather than weakening this check. |
| F3 | **fixed-awaiting-check** | `validate-proposal` validates bounded object changes in memory, source/test traceability, exact writable paths, real check bytes and roles while authoritative state remains byte-identical. `--saved` additionally checks committed paths/clean tree/actual upstream. Separate `validate-acceptance` checks latest-HEAD before/current-after snapshots, current priority and original inherited prerequisites, proposed scope/outcomes, fresh reconciliation and immutable controls. Independent review/verify retains the original inspected code anchor rather than relabeling latest HEAD. |

Managed acceptance uses a **new** supervisor report linked to exact worker report/commit;
it never copies worker state. Useful speculative implementation, including split units,
may validate before serial readiness; serial replay/selection remains enforced during
acceptance. Unrelated accepted branches may advance without forcing worker
`startCommit == integration HEAD`. Changed selected contracts require reconciliation,
not stale acceptance. Child reuse now tracks accepted managed source/test inputs too.

The [compact schema](implementation-agent.md#managed-proposal-additional-report-field)
and [command sequence](implementation-runner.md#managed-handoff-example) distinguish
worker validation from sole-supervisor acceptance. Lease ownership, actual saved
branch/upstream and succeeded/inactive exact-high model/session metadata remain
explicit supervisor checks, as do material changes since independent review anchors.
Fresh integration subagents prepare candidate code/conflict repairs/checks; only the
supervisor accepts/writes live state. No fixed lane cap, five-unit checkpoints or
fallback to the old serial workflow was introduced. The authorization fallback cited
by the older review was already absent at this base; no out-of-scope edit was needed.

## Executed fixing-session checks (not independent verification)

- `python3 -B scripts/test-implementation-loop.py`: **109 passed**. Final driver
  output retained at `/tmp/opencode/flow-fix-2026-10-23-runner.json`, exit 0,
  output SHA-256 `6af169a0484e8511afe8ce1683f9034194e56d74b92096f14160ee701354c786`.
- `python3 -B tests/test_implementation_evidence.py`: **29 passed**, including
  actual CLI proposal/acceptance calls without state writes/staging; latest selection,
  prerequisite/task drift, fresh closure evidence, independent anchors, role/path,
  unrelated-object, review, release-gate, control and real-upstream rejection cases.
  Output retained at `/tmp/opencode/flow-fix-2026-10-23-evidence.json`, exit 0,
  output SHA-256 `79c72d1a7f9e11696a57253efe0c3a6e214081cb25e124edb041e634f7261e44`.
- `python3 -B tests/test_implementation_resources.py`: **10 passed**. The initial
  new recovery fixture correctly hit the existing dirty-tree guard because its
  cleanup evidence was unsaved; fixed the fixture to save/push evidence, not the guard.
- `implementation-evidence.py --help` and `git diff --check`: passed.

All executable qualification used disposable Git fixtures/fake agents and local
resource commands. No real model/session/worker, live state/resource/STOP mutation,
application edits, device/emulator or application full-suite run. Prior native child
qualification remains prior evidence, not a new feature pass. This is not operational
parallel qualification, whole-R1 completion, independent review or release acceptance.
Parent must obtain separate fresh-context verification before accepting these fixes,
then a new fresh-context managed supervisor continues the authorized R1 delivery.
