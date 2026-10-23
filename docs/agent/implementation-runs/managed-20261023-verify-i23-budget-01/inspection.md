# Independent IG-I23-01 disposition: verified in the assigned shared-engine scope

Inspected immutable pushed source/tests/interfaces at
`ebbbae9626296ffd5ec6b3af1987d3a59b5fbb8a`, not another worker's evolving checkout.
Initial HEAD/upstream matched that anchor; branch/index/tree were clean. The supplied
lease/context names this session, exact-high model, this checkout, isolated host
tests, one writable test and this report/evidence namespace. Authorization is active.
No runtime/build/resource changes, application transitions or self-fix were made.

## Source and retained evidence

* `GameplayTree.kt:26-30,48-67`: only a private transient current checkpoint;
  normal read/play do not enter `boundedHistory`. Incremental play uses the sole
  `GameplayRules.change`. Copy/decode construct a fresh instance; root checkpoints
  use the new initial root, never an inherited non-root board.
* `GameplayTree.kt:87-128` and `GameplayHistory.kt:40-48`: cold reads walk the entire
  requested ancestry, reject repeated/missing/mismatched IDs, validate references,
  schema and exact replayed transitions. No prefix truncation or selective ceiling.
* `SelectiveUndo.kt:14-17,38-64,67-97`: still meters metadata, ancestry, full prefix,
  target, references and retained suffix; never reads `currentMaterialization`.
  Redo checks the original target before the suffix. Candidates are returned only
  after complete checks. Forks and N still block without publishing partial edits.
* `GameplayTree.kt:140-179`: unchanged hard maxima are N=128, 2048 loads, 2 MiB
  conservative cumulative payload, 32768 effects, 250000 serializer visits and
  16384 metadata entries; callers cannot increase them. Independent tests exercise
  every lowered meter on warm Undo/Redo and every above-hard constructor rejection;
  real default ancestry, metadata, payload and N overflows are also tested.
  The unchanged retained oversized-target test covers atomic rejection at 32769
  fields (not an isolated measurement of which work meter trips first).
* Retained fix `interfaces.md` accurately documents the synchronous cold API and
  requires adapters to schedule it off UI and fence accepted revision/incarnation.
  This is documentation of a remaining integration obligation, **not** implemented
  asynchronous dispatch or a native responsiveness measurement.
* `reused-evidence-inspection.json` verifies actual archived output hashes/XML:
  four original failures versus 28 passing affected tests, zero skips/errors.
  These are reused records, not 28 fresh executions here. `unchanged-selective-inputs.json`
  proves unchanged selective engine/reducer/replay and original regression inputs.

## Fresh execution

`focused-tests.json` and lossless `focused-junit.json`: **7 passed, 0 failed/skipped**.
The independent test combines 2113 ancestors with 16391 accepted root offshoots;
checks cold/warm append, off-current reads, codec recovery and unchanged retained
nodes; corrupts first/middle/tail ancestry and an off-path recorded reference;
checks same-ID valid rewiring, changed root/scope, activation and Undo/Redo cache
invalidation; checks 128/129 full retained successors for both directions, exact
target/suffix/reference/fork rejection, every lowered budget, all hard maximum
guards and ordinary play on a board above the default selective payload budget.
Rejected selective results preserve serialized board/tree/queues/metadata and the
existing current materialization. No new credible repair bug was identified.

I23b owns relational FIFO/atomic publication, all accepted action/exposure mappings,
connected native routes and off-UI scheduling/revision fencing. I26 owns retained
tree/recovery integration. Independent complete B6 review and I34/B8 qualification
remain mandatory. This disposition neither accepts tasks nor closes those gates.
