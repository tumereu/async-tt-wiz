# Implementation-flow repair qualification — 2026-10-23

Owner requested the analyzed throughput repairs, orchestrator parallelization
instructions and a net reduction in agent instructions. Preparation began at
`b9a092d` in isolated disk-backed worktrees; the old controller received a graceful
STOP request. Its dirty I12 planning unit was not edited, migrated or discarded.

## Runner and evidence tooling

- Cycle validation previously enumerated shared dependency paths: 86 tasks/130
  edges produced 720,216,341 visits per call. An iterative active/done traversal
  now visits each vertex/edge once, rejects cycles and handles deep valid graphs
  without a Python recursion limit. On the same 86-task snapshot, 100 validations
  had median **0.320 ms**, maximum **0.755 ms**. No model/device work was launched
  for this measurement.
- `implementationUnits` permits a decomposition and completed new leaf children
  in the selected implementation unit. Each entry is
  `{"id":"child","sourceIds":["actual source ID"],"evidence":"passing current-run check"}`.
  Only newly introduced descendants can advance; their order must replay the
  ready-task selection, each completion names this run's `implementationRecord`,
  and undeclared children remain pending. Planning-only units remain supported.
- `ancestorClosures` entries name a true ancestor, a concrete `reason` and a
  passing `evidence` check. The selected child must finish; every ancestor child
  and explicit prerequisite must be implemented. Original ancestor scope stays
  unchanged, with this run's `implementationRecord`. This grants no independent
  review coverage or external gate authority.
- `scripts/implementation-evidence.py` provides `report CONTEXT DETAILS`,
  `validate REPORT [--pending-evidence PATH]`, `retain-junit OUTPUT XML...` and
  `reconcile OUTPUT [TASK=]REPORT@COMMIT...`. The controller saves `contextPath` in its
  private journal for report generation. Details contain the concise result and
  actual checks; the helper generates immutable identity and exact changed paths.
  It never runs tests, stages files or replaces controller acceptance.
- For the validator's own evidence, use the foreground command
  `python3 scripts/capture-check.py RUN/check.json -- python3 scripts/implementation-evidence.py validate REPORT --pending-evidence RUN/check.json`.
  Substitute the actual current-run paths. Declare that required check before
  generating the report; `capture-check.py` writes its real exit/output after the
  validator exits. No other missing evidence is accepted. The controller still
  requires the final file's committed bytes.
- Retention preserves failed JUnit/output bytes. Reconciliation checks supplied
  child commits, their implemented task records and exact report/check/support
  bytes, retaining diagnostic exits; it reports later changed inputs for seam
  assessment instead of rerunning product tests or declaring ancestors complete.

## Executed checks

- `python3 scripts/test-implementation-loop.py`: **107 passing**, 24.772 seconds;
  disposable Git/fake-model fixtures, including split+implementation+ancestor
  closure+independent review+actual fixture push. That delivery takes five fresh
  units instead of seven; no live model qualification is implied.
- `python3 -m unittest discover -s tests -p test_implementation_evidence.py -v`:
  **10 passing**, 0.681 seconds. Covers identity, attempted-finding subsets,
  run-owned pending capture, unreported changes, corrupted output, red diagnostics,
  exact red JUnit, committed child/split-unit evidence, dirty/committed input changes
  and binary/nonzero command capture.

Native-tooling and instruction-reduction evidence is added at integration. App
full-suite/device/release qualification is not claimed by these tooling checks.
