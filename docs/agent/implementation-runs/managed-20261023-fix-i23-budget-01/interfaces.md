# Corrected I23 shared API — budget separation

Base: `654f4cf6e8b39c574ff7bcef4d039190c90a7c25`. This fresh implementation repairs
candidate issue IG-I23-01. It does not accept the original worker proposal, close
I23, or constitute independent verification. Original source/report/API at
`docs/agent/implementation-runs/managed-20261023-i23-01.json` and its `interfaces.md`
are immutable reused inputs, not newly executed evidence.

## Public API for I24–I26

`GameplayTree(initial, nodes, currentNodeId, redoByAccount, removedParents)` remains
the single immutable retained tree. Never mutate supplied backing collections.
Root data, surviving logical IDs and recorded transitions are retained; parent
rewiring is not a new branch or compensation. No parallel board/reducer is added.

* `tree.play(actor, command): GameplayTreeChange` is ordinary processing-time play
  using **GameplayRules.change**. It no longer accepts `SelectiveUndoLimits` and
  cannot reject otherwise-valid normal play merely because an Undo work ceiling
  is exceeded. Effective own success clears only that account's queue; opponent
  success, rejection and no-op retain the existing clearing semantics. Scope,
  access and duplicate-node checks still apply.
* `tree.stateAt(nodeId): PlayState?` is ordinary retained state/review access. It
  no longer accepts `SelectiveUndoLimits`. Cold reads validate the **complete**
  requested ancestry, IDs, cycle/missing-parent checks, schema, exact recorded
  transitions and recorded references through **GameplayHistory.replay**. Null
  means reconstruction is invalid/unavailable, not an Undo-budget overflow.
  Off-current histories remain readable without activating them or changing queues.
* Current state alone has a private, disposable, nonserialized materialization.
  It is established by complete replay (or the immutable root), then incrementally
  advanced by the same shared reducer on ordinary success. No caller can supply
  this checkpoint as authority. All `copy`/decode/new instances start with no
  non-root checkpoint, even for surviving same-ID nodes. Activation and Undo/Redo
  create such new instances, preventing stale topology/root/scope reuse. Encoded
  trees still contain one root board, not one snapshot per node.
* Cold reconstruction is synchronous and costs full ancestry/board replay, without
  the selective-Undo eligibility ceilings. Native/relational adapters must schedule
  cold work off the UI thread and fence any reused accepted materialization by the
  exact accepted revision/incarnation. Durable checkpoint storage, async scheduling,
  SQL integration and actual native route execution are **not** delivered here.
  No retained history is truncated or discarded to improve reconstruction cost.
* `resolveCursor(id)` / `parentOf(id)` and `activate(id)` are unchanged. Deliberate
  different-current activation clears both queues; same-current is no-op. Metadata
  changes/personal review are not shared activation. I24/I25 own atomic switching
  and recovered-outcome behavior; I26 owns complete-tree/full-recovery transfer.

## Selective eligibility remains bounded

`SelectiveUndo.change(tree, actor, command, limits)` is unchanged. Only this path
uses `boundedHistory`/`HistoryWork`; it **never** consumes the ordinary current
checkpoint. All prefix reconstruction, target, references, mandatory effects and
the complete retained linear suffix remain charged before accepting a tree edit.

Hard current engine ceilings remain **N=128** individual successors, 2048 ancestor
loads, 2 MiB conservative cumulative payload, 32768 effect units, 250000 serializer
visits and 16384 metadata entries. Limits may only be lowered. Overflow is atomic
unavailability, not permission for unbounded eligibility or prefix-only checking.
A long/large tree can remain playable/reviewable while selective Undo is unavailable.
No cap was increased, disabled or reinterpreted as a gameplay/retention limit.

Target fidelity, no skipping, revealing-target and fork barriers, strict account
Redo ordering, stable logical identities and entire retained-future checks remain
as in the original engine. Server callers still own actual durable FIFO/dedup,
access/incarnation fences and atomic board/topology/stacks/results/deltas.

## Remaining I23b obligations (not waived)

The corrected split retains the original pending integration child's entire outcome
and checks: actual PostgreSQL FIFO/atomic transitions and same-account devices;
all I13/I17–I22 accepted per-kind tuple/read/choice/cleanup/exposure/reference mappings;
large-target/zero-suffix work bounds; exact queue clearing and recovery semantics;
mounted G03 and quick-slot native controls with real connection gating, provisional
prediction/rebase and lifecycle races; same-tree topology/cursor metadata for I26.
The existing native components and their old host/APK output are reused inputs,
not newly executed native coverage. No runtime/build dependencies changed here,
so no APK/device rerun is claimed. Fresh independent repair verification, complete
B6 capability review and I34/B8 final integrated/release gates remain required.

## Fresh affected evidence

`large-play-red.json` and `large-play-red-junit.json` retain four actual failures
before repair: ordinary append/read at 2100 ancestors and 16385 retained branches.
`affected-tests.json` / `affected-junit.json` retain the passing affected execution:
8 HistoryLargePlay tests, 5 existing tree tests, 13 existing SelectiveUndo tests and
2 existing reducer tests. Added assertions include incremental/cold equivalence,
tail/reference corruption, copy/activation/root/scope invalidation and warm-cache
inability to bypass selective Undo/Redo work bounds. Existing N=128/129, full future,
fork, target fidelity, large-effect and atomic budget regressions are unchanged.
