# I01a save recovery — 2026-10-21

The owner requested repair of the save operation and completion of the preserved
I01a handoff before restarting the implementation driver. This is manual recovery
of run `20261021T170500Z-fc15d7bd`, not a new implementation/review unit or a loop
launch. The original high-model session finished; no loop writer was running.

During recovery, the owner additionally clarified the autonomous execution goal
(D-305): spend development quota troubleshooting even across whole/further fresh
sessions; repair infrastructure/orchestration; do not halt remaining work or abandon
a feature merely because external credentials, rights/signing/hardware or production
access are unavailable. Implement the feature as fully as possible within the
grants, retain only unavailable external qualification as unverified and continue.
This clarification is recorded in AGENTS.md, authorization, decisions, plan,
handoff and the runner/unit contract, rather than left as conversational context.

## Failure and correction

The official generated `gradlew.bat` contains CRLF line endings. Without an
explicit text rule, Git treated its carriage returns as trailing whitespace.
`scripts/save.sh` stopped before committing or pushing. The original failure
output, exit code and `saveFailure` record remain in the
[run report](implementation-runs/20261021T170500Z-fc15d7bd.json).

- `.gitattributes` now declares `gradlew.bat text eol=crlf`: Git stores normalized
  LF content and Windows checkouts retain CRLF. No global Git setting changes.
- `scripts/save.sh` still uses the unmodified mandatory staged-whitespace check,
  fixed commit message and normal push. Failure now explains how to repair and
  re-stage owned paths without bypassing checks or discarding work.
  Explicit paths are treated literally and re-normalized using their declared
  attributes, including wrappers staged before the EOL rule was added; no-path
  saves still use the existing index without pulling in unstaged work.
- `scripts/test-save.py` qualifies CRLF normalization and ordinary commit/push,
  rejection of actual trailing spaces even in CRLF files, preservation of
  unrelated/un-staged changes, and a failed-push retry without a second commit.
- The original pre-save validator remains historical evidence. Its `splitlines()`
  whitespace scan hid carriage returns. Recovery instead checks the exact
  prospective staged content with Git in a temporary index.
- The driver accepts evidence-backed unfinished troubleshooting checkpoints, keeps
  those separate from actual product-fix attempts, and permits explicitly reported
  driver/prompt repairs with an independently executed baseline regression suite.
  A saved controller repair replaces its idle process with the remaining budgets;
  permission grants/model/authorization, review boundaries and truthful save/test
  evidence remain protected. Two edit denies for driver/prompt were removed by
  this owner-authorized preparation; active agents still cannot expand grants.

## Handoff checks and scope

Recovery reruns the offline clean JVM tests, normal/instrumentation APK assembly,
Android lint and module-boundary check, then checks actual artifacts/signatures,
dependency licenses, save regressions, runner regressions, local documentation
links and source/record syntax. Exact outputs are retained under this run's
`-evidence/recovery-*.json` paths; no old output is relabeled as a fresh execution.
There are 85 passing deterministic runner regressions and six passing save tests.
No new live model/driver qualification cycle was launched; actual new-path live
execution remains distinct from these deterministic tests and prior live evidence.

Only I01a becomes `implemented`. I01 remains in progress; I01b is the next ready
child. Device execution, later storage/backend work, B1 independent review and
all external release gates remain open. Existing implementation source is not
replaced or expanded by this repair.

The report's current outcome becomes `progress` with explicit recovery provenance;
the original failed save remains a red **diagnostic**, followed by fresh passing
required checks. After the repaired files/report/state are committed and pushed
with `scripts/save.sh`, use the driver's normal `recover` command to acknowledge
the retained halt. It verifies the original session is idle, a clean/pushed tree,
and resolved required-check/report status before archiving the halt. No fence is
deleted manually, and no agent or driver run is launched here.
