# I06a — normal client deck import

Authorized D-304, supplied clean master `405dcd3e82e703fb3fa47fd4cd95778ea1381db8`.
No open findings or due B2 review precede this child. I05 is implemented; I06a is
the earliest ready unit. Only I06a closes. I06b real save, I06 parent and the first
independent B2 review remain pending.

## Delivered scope and sources

- P-044/P-048, T-423–T-427, T-573 and T-628: **partial import assertions**, not
  whole cross-feature scenario passes. D-198/D-199/D-271/D-289 and D03/D04/D04b
  constrain preserved input, exact resolution, read-only preview and eventual
  atomic save. The unchanged coverage ledger retains the remaining server/deck,
  collection/lobby/copy/recovery assertions.
- [Implementation boundary](../../../../client-storage/IMPORT.md): common text
  dialects, exact printing/face resolution on real installed catalogs, actionable
  errors, duplicate quantity arithmetic, preserved raw defaults, native preview,
  correction/catalog retry, rotation and account callback fencing.
- Save is visibly disabled, with an honest pending-build explanation. No fake
  success, local saved deck, provider-ID special case, licensed provider request,
  authoring/editor UI, deployment, subagent or extra loop.

## Checks and failures

`affected-host.json` is the initial passing incremental parser/controller/account
test/build/lint/boundary command. `hosted-build.json` adds both existing families'
import fixture cases and the AuthController-to-import Logout regression, plus the
real synthetic-origin APK build. `rotation-fix-build.json` tests the targeted
preview-retention repair and rebuilds/lints its affected Android scope. Current
JUnit XML retention contains five parser cases and three importer-controller cases;
the earlier complete Android host command and subsequent Logout case remain actual
command evidence, not newly executed XML from the final narrower run.

Original native failures are retained, never relabeled:

1. `native-journey.json`: label enabled-state oracle read the Text child, not the
   disabled clickable button container. XML/screenshot show correct blocking UI.
2. `native-selector-recheck.json`: element-relative parent XPath is not available
   through this UiAutomator2 scoped query; use a document-level container selector.
3. `native-container-recheck.json` plus `rotation-crash.txt`: preview rotation
   restarted the debounce lookup, clearing the result while preview remained set.
   The controller now refuses lookup in preview and the debounce does not refresh
   retained results; a host regression preserves result/name on recreation lookup.
4. `native-rotation-recheck.json`: the UiScrollable text search stopped above the
   final preview controls. Explicit native W3C swipes reach them; no app change.
5. `native-scroll-recheck.json`: the remaining landscape sign-in helper located a
   password label without its offscreen field. The Logout/relogin tail uses the
   already-established portrait input route; both import orientations are still
   exercised, not skipped.

`native-final.json` and `runner-acaf719979794f55b47ce44ca7839b9f-*` retain the
**complete passing** normal-APK journey on the final tested binary: UI-created
account, hosted catalog consent/download, exact line-3 error and blocked review,
correction/retry, 18 mainboard/2 sideboard quantities, real art/missing-art names,
both orientations, retained name/text/preview on rotation, long-preview reachable
Edit/disabled Save, Edit/Exit/native Back, fresh empty input and Logout/relogin
clearing private work. No gameplay or saved-deck acceptance is claimed.

## Remaining qualification

- I06b real definition validation/private HTTP save/atomic retry/read-back and
  failure/restart journey; I07/I08 collection/inspector/Overwrite/Merge integration.
- I34 broad/full-suite and uncommon fault assertions; I35 device/upgrade matrix;
  I36/B8 complete interface-to-mock comparison and required discrepancy fixes.
  Current missing-art thumbnails repeat clipped name fragments in a narrow box;
  the adjacent full name is readable. Assess thumbnail presentation during I32/I36
  against the existing text-fallback rule, not as an early delivery blocker.
- Existing NativeShellTest still expects the pre-I05 unavailable-decks copy at
  lines 115/117. This older oracle predates this run; I07's collection integration
  should update it to real deck controls, and I34 must not claim it green meanwhile.
- All external rights/signing/operator/hardware gates are unchanged and unverified.
  No full suite, clean build, independent review or release clearance occurred.

Owned device and Compose cleanup outputs are retained. No unrelated resources or
work were changed. Check outputs/captures are synthetic; no credential-bearing
runner properties or private log archives are copied into Git.

The first handoff check rejected absolute report-output/support paths from Python's
absolute `__file__`; report generation now explicitly uses repository-relative
paths. The original failure stays in `handoff-initial.json`. Product tests were
not rerun for this report-only correction. Native Gradle stdout is retained
losslessly in each command JSON rather than redundant whitespace-bearing raw logs.
