# I10b2 — native preparation transfers

Scope: P-044/P-045/P-052/P-053/P-059; T-400–T-405, T-440–T-445,
T-608/T-680/T-682–T-684; F20/F21/F30–F32/F34–F36. Completes L04/L04b
on I10b1's existing ordered per-card transfer/session path. Ready/start/play-sideboard
exclusion and complete two-player lifecycle remain I11/I12; gameplay prediction I14,
rematch I28/I31 and final qualification/UI comparison I34–I36/B8 remain required.

## Delivered and checked

- Own **Prepare deck** opens stacked portrait / side-by-side landscape lists. Two
  disjoint 24dp-wide **1 / ∞** origins capture at pointer-down; valid opposite-panel
  release submits once, outside/cancel submits nothing. No pending-ack gate, bulk
  mutation or Save/Send. Quantity then thumbnail/name follow the controls; inspection
  is personal and uses retained definitions with the existing artwork/text fallback.
- Native OnClick and full-row custom one/all accessibility actions use the same
  capture/transfer interface. Only list membership/counts come from preparation;
  editor/inspection/feedback are personal, rotation-retained and cleared on Logout.
- Latest feedback derives **actual displayed membership differences**, not attempted
  quantities or receipts. Reconciliation reports its real reverse/partial delta.
  The 40dp slot never collapses; new output restarts a 1.5s standard Compose fade.
  Removed rows use 180ms Compose item fade/placement, with no actionable semantics
  during visual removal. Standard motion-duration scaling is retained; explicit
  reduced-motion/large-text matrix remains I32/I34, not a newly executed pass.
- `affected-host.json` / `host-junit.json`: **20** affected production controller,
  gesture/feedback and transfer-session host cases pass, incremental normal APK,
  lint and boundaries pass. No shared/server source changed; I10b1's actual ordered
  server/semantic tests remain reusable. The real backend image is incrementally
  rebuilt only to exercise the new native interface against those transfers.
- `native-retained-observer-cwd.json` is the passing retained native tail. Actual
  UI-created accounts/catalog/deck/contact/game and portrait one/all taps succeeded
  in `native-journey-configured.json` before its missing scroll-to-control assertion.
  Portrait drags succeeded in `native-retained-handle-target.json` before the observer
  cwd failure. The passing tail resumes **without reset**, checks outside drop,
  completes landscape taps/drags/outside drop, inspection, normal same-account
  other-device conflict, delayed acknowledgement, Back/reopen/restart. It does not
  claim to have newly repeated the earlier UI setup/portrait assertions.
- `native-interaction-navigation.json`: actual normal-app Android instrumentation
  checks one/all native OnClick/custom accessibility, immediate counts, no inspector,
  unchanged list/feedback bounds visible versus faded (32/1632ms), non-actionable
  departing row at 32ms and completed reflow after another 220ms, and actual touch
  cancellation. PNGs and semantics dumps retain the visible/faded/removal endpoints.
- `native-rapid-interaction.json`: a separate missing-assertion native check exercises
  rapid taps before animation/feedback completion, then holds another pointer while
  the captured row disappears through a normal accessibility action. Release cannot
  trigger the ghost or the different row sliding underneath. Counts remain immediate;
  reverse restores original preparation. No unchanged green journey was rerun.
- `retained-results-outbox-corrected.json` pins exact original ordered commands and
  outcomes: **48** IDs, **one** rejected conflicting original copy, **47** accepted
  revisions and **48** result publications (including rejection). Final game-local
  copy and saved source retain exact original 12/3 membership and immutable identities.
  Input hashes include the tested APK/instrumentation, source/build/locks and images.
  The final E2E source removes an unused non-exercising `done` shortcut; executed
  stages and runtime inputs are unchanged. This test-only deletion is not a new run.
- `cleanup-first.json`, `cleanup-peer.json`, `cleanup-environment.json`: exact two
  owned devices/Appium/ADB/routes and synthetic Compose resources removed. No unrelated
  resources removed; redundant owned APK readbacks preserved with hashes in ignored
  build storage to avoid exhausting the shared `/tmp/opencode` tmpfs.

## Retained diagnostics and obligations

The peer provision initially refused auto-discovered first-device transport. Exact
live-peer ownership was recorded through the existing guard, without broadening it.
One APK readback exhausted tmpfs; only this run's redundant readbacks were preserved
off-tmpfs, then the same prepare operation passed. The initial native APK omitted
the synthetic `/prism/` catalog path; ordinary update/download failed honestly until
explicit debug configuration and guarded changed-APK reattachment corrected it.
Native automation first lacked scoped list scrolling, then mistakenly used the
scroll expression's returned container as a drag origin; explicit handle lookup
fixed the check without changing product code. Observer cwd and SQL table/outbox
assertions were corrected to the actual schema: rejected requests publish results
but do not create state revisions. Initial instrumentation attempted a game row
before navigating to Games; normal navigation/wait fixed that test. Every red output
is diagnostic, never relabeled green. No new product defect/finding, driver repair,
authorization/model/permission change, UI-document edit/publication or deployment.

Only I10b2 is implemented here. Selected parent I10b/I10 closures still audit exact
child evidence and missing seams; independent B3 review follows I09–I12. Actual
Ready/starter/start/randomness/table/cancel/play-sideboard exclusion and full two-player
lifecycle remain I11/I12. I14 extends gameplay prediction, I28/I31 rematch/integrated
recovery. I34 retains final full suite, uncommon assertions, older migration fixtures
and I09 recovered HTTP-error correction; I32/I34 retain explicit native reduced-motion
and large-text matrix. I33 measurements, I35 compatibility/upgrade/architecture,
I36/B8 complete final interface comparison/fixes/rechecks and external release gates
remain required and unverified. No full-suite/review/release claim is made.
