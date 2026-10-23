# I18a handoff — shared Library tools and native component inputs

Start/pin: `7641ac350cbadd277652be668680ba3049817393`, branch
`managed/managed-20261023-i18-01`, session `ses_e98cb4fb2ffecKF9bsdZNNZ6MM`.
The supplied reservation grants exactly the ten source/test paths in context.json
and this run's report/evidence namespace. Resource bundle: isolated-host-tests.
No devices, services, database/schema, live state or sibling checkout were changed.
Model metadata supplied by dispatch is `openai/gpt-6.1-sol#high`; the supervisor
must independently confirm actual session/model/lease and latest prerequisites.

## Delivered seam

- Extend the same PlayAction / GameplayRules.change / GameplayHistory.replay.
  Shuffle binds source/seed/version, reads current whole membership through
  RandomV1, and records exact from/to orders, including equal random outcomes.
- ShowContents binds source/top/bottom/all/count, resolves/clamps at processing,
  and records immutable accepted reference IDs. Dismiss changes only that display.
  Full browsing has canonical orderless IDs; native category/name/ID sorting and
  search never change source order. Covered rows expose no name/type/search match.
- Individual MoveCard lowering preserves captured order, bottom-slice frontiers,
  normalized untouched gaps and pending-member exclusion. Missing prefix anchors
  reject dependents without rollback/substitution. Already-placed anchors retain
  identity without fake nodes. Tokens never become destination-prefix anchors.
- The early-I13 dispatch now supports ordinary Hand/Library/Graveyard/Exile
  movement. Entry resets use the retained currently selected face default (blank
  fallback); counters reset, unrelated fields persist, and same-Library reorder
  does not reset. Draws capture top IDs with individual top guards.
- Mulligan captures the whole displayed Hand, pre-return size, source guards and
  return order once. Return requests precede one Shuffle; draws bind the resulting
  shared prediction's top IDs. Minus-one is captured size minus one, clamped at
  zero; no acknowledgement, atomicity or keep/bottoming gate.
- LibraryController injects the existing session state/connection and synchronous
  `(PlayAction) -> Boolean` admission callback. It owns no replica/queue/retries.
  The callback must synchronously run the existing shared provisional processing
  before returning, so following mulligan draws use that prediction, not an ack.
  A fresh shuffle invocation samples one SecureRandom seed. Retry retains the
  original action in I14's queue, never calls shuffle() again.
- Production LibraryActions and ContentsScreen are reusable Compose controls,
  not a mock screen. Rendering never sends inspection. Inspection input preserves
  viewed play/node/card/face/source; the required I17 adapter distinguishes logged
  explicit inspection from unlogged holds. Explicit inspection is online-only;
  own-area covered holds and already-visible downloaded holds remain local.

## Integration and retained obligations — I18b

I13/I14/I16 are not accepted complete dependencies at this pin. These controls
are packaged but **not embedded in the live table** and have no socket adapter in
this unit. I18b depends on I18a, I13, I14, I16 and I17. It must wire the real bound
table/contents/inspection/request/prediction/recovery path, surface actual partial
outcomes, reconcile provisional View/Reveal to accepted members, persist/download
accepted displays and preserve request seeds and references across recovery.
I13 must union the MoveSource.bottomSlice / MoveDestination.pendingIds guards and
the special-container seam with its board/Stack moves, not overwrite either lane.
I17/I19/I20 must union their PlayAction/transition extensions. Token execution and
mandatory link/source cleanup are I19's processor: this helper rejects token moves;
only token-aware **lowering** is tested here. Actual mixed-token/reset/Stack/board
journeys remain integrated I19/I18b/I34 assertions, not passes from this component.
I20 retains turn-1 Untap quick access to both same controller mulligan paths.

Original I18 obligations are retained in the proposal, not narrowed to components.
Real two-client/native both-orientation/response-loss/restart/offline/history/Redo
and compatibility journeys remain I18b, I22-I26, I34-I36. Independent B5 review and
all final gates remain required. No full suite, E2E, device or release pass is claimed.

## Actual evidence

- `affected-host.json`: overall **failed** on missing org.junit.Test imports in new
  Android host tests, after 9 Library, 2 scalar and 3 RandomV1 shared cases passed.
- `native-host-apk.json`: corrected Android imports; 8 host cases and normal APK
  assembly pass. `initial-junit.json` retains exact XML from these two executions.
- `captured-context.json`: after binding return guards at capture (rather than
  reconstructing them) and distinguishing visible holds from explicit inspection,
  affected 10 shared Library and 4 controller cases pass, with APK and lint green.
  `final-junit.json` retains their exact XML. Unchanged green scalar/random/contents
  cases were not rerun for confidence. These are component assertions, not executed
  coverage of every assigned T/P/F ID.
