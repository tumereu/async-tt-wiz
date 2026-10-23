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
  After replacing the prompt with the compact integrated instructions, its required
  affected runner repeat also passed **107 tests**, 20.093 seconds (new prompt input,
  not an unchanged-candidate confidence run).
- `python3 -m unittest discover -s tests -p test_implementation_evidence.py -v`:
  **10 passing**, 0.681 seconds. Covers identity, attempted-finding subsets,
  run-owned pending capture, unreported changes, corrupted output, red diagnostics,
  exact red JUnit, committed child/split-unit evidence, dirty/committed input changes
  and binary/nonzero command capture.

## Native tooling, resources and instructions

- Native worker `ses_e991d0898ffeU9RZZeb36a8L7D`, commit `6cb1756`, ran **91 focused
  Python regressions**, **four Kotlin host cases**, and compiled all E2E test sources.
  Owned new APK readbacks use private disk-backed `e2e/build/device-artifacts`, not
  bounded tmpfs; exact fresh installed-readback/hash provenance remains. Shared exact
  selectors, scrolling, dialog-safe keyboard/reopen helpers and named phase checkpoints
  replace repeated per-journey fixes. Phase evidence distinguishes full from partial
  journeys. Native gesture/keyboard execution is **not yet device-validated**.
- `python3 -m unittest discover -s tests -p test_implementation_resources.py -v`:
  **9 passing**, 2.045 seconds. Real disposable process/worktree checks prove mutual
  exclusion, independent-resource overlap, canonical bundle acquisition, legacy
  writer exclusion, wrong-owner/running recovery refusal, exact cleanup recovery,
  interrupted-command fencing and inherited locks surviving wrapper death.
- [Resource matrix](test-resource-plan.md) and `implementation-resources.py` enforce
  complete device/routing/backend lifecycles. Exit 75 is scheduling contention;
  nonzero/interrupted commands retain fences. These cooperative locks do not claim
  exclusive ownership over unrelated repositories or host applications.
- Documentation worker `ses_e991d44acffeoOI7E0XbHrBl6T`, commit `19e62db`, compacted
  the active guides and retained immutable historical links/anchors. The initial
  compaction reduced core instructions by 65%; final sizes after the reviewed managed
  proposal protocol are recorded below. History is lookup-only, not default unit reading.
- Changed Markdown relative links/fences pass; all 36 original task-outcome table
  rows remain byte-identical. No UI document or cumulative feedback was edited.

App full-suite/device/release qualification is not claimed by these tooling checks.
The owner stopped the old supervisor and explicitly requests a fresh exact-high
parallel supervisor after safe integration, with original budget/stop/review/release
obligations retained. The [handoff](session-handoff.md) records actual paused progress
and conservative budget reservations; no old serial restart is intended.

## Independent review and final repair acceptance

[Review](flow-review-2026-10-23.md) found three evidenced P2 seams; a fresh
[fixing session](flow-fixes-2026-10-23.md) corrected them and a separate fresh
[verifier](flow-verification-2026-10-23.md) verified all three at `bca5fc6`:
retained managed fences block legacy run/recovery, completion arrays require fresh
run-owned output, and read-only worker proposals have separate latest-state acceptance.
**109 runner, 29 evidence and 10 resource tests pass independently**, including overlap
acceptance and tampered-evidence rejection. This is tooling qualification, not a live
parallel-product execution claim. Per-session model metadata was checked as exact high.
Final core guide/prompt/contract: **7,555 → 2,824 words (62.6% shorter)**. Full active
policy bundle including orchestrator/resource plan: **39,698 → 12,496 words (68.5%
shorter)**. All 36 original task rows and the saved `4ffaf58` application-state bytes
are preserved. Later lifecycle/merge-only prose clarifies the existing protocol; it
does not change qualified code or claim another test execution.
