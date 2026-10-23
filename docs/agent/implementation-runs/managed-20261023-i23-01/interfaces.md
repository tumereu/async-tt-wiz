# I23 proposed public interface (pinned base 7641ac3)

`GameplayTree(initial: PlayState, nodes: Map<String, GameplayMutation>,
currentNodeId, redoByAccount: Map<String,List<GameplayRedoEntry>>, removedParents)`
is the one shared retained tree. `initial` is the immutable root state, not today's
board. Surviving node IDs, request IDs and branch labels remain stable. Parent links
are rewired in place; removed nodes live only in account Redo, not another branch.
No per-node board snapshots. `stateAt(id, limits): PlayState?` derives state using
the existing `GameplayHistory.replay`/`GameplayRules` exact tuple semantics.
`resolveCursor(id)` follows removal-parent metadata to a surviving ancestor.

`SelectiveUndo.change(tree, actor, command, limits): GameplayTreeChange` accepts
`GameAction.Undo`/`Redo`, scoped by gameId/playId only, never a selected target.
The caller processes distinct commands in durable server FIFO and recovers duplicate
request outcomes WITHOUT invoking the engine again. Returned immutable candidate
tree and final result must be committed atomically by that caller. Null tree with
reason means unavailable, with no mutation; no reason/null tree means no-op.
`targetId` is output only. No local account stack is authoritative.

Undo selects the last own current-path node, never skips a revealing/blocked node,
and validates the entire retained linear future, even beyond an internal shared
pointer. Redo is last-undone-first; exact target fidelity precedes suffix replay.
Anchors are original parent and immediate successor (if any); a missing one blocks,
never guesses a new object/boundary. Original sibling continuations are excluded
from reinsertion, preserving the ability to Undo/Redo strictly after a fork.
Both directions reject forks within the affected continuation.

`tree.play(actor, command)` is ordinary shared-reducer play, clearing only an
effective author's queue. Rejection/no-op does not clear. `tree.activate(nodeId)`
is the I24/I25 successful deliberate-selection seam: different shared selection
clears BOTH queues; same-current is no-op. Continuation/archive+return callers must
also clear both queues atomically on their actual shared switch. Review/metadata
alone must not call this seam. Restore does not reload historical stack snapshots.

Hard technical ceilings: N=128 individual successor records (not groups/invocations),
2048 ancestor loads, 2 MiB cumulative conservatively counted payload work,
32768 transition-effect units, 250000 structural serializer visits and 16384
metadata entries. The streaming visitor counts without allocating encoded JSON;
snapshot/reducer loading, references, target and suffix all consume work. Reduced
limits are allowed, never increased beyond hard ceilings. Exhaustion is atomic
unavailability, NOT a history/game-object cap, truncated validation or expiry.

`GameplayMutation.revealsInformation` is authoritative accepted metadata;
`references: List<GameplayNodeReference(nodeId, transition)>` protects exact event
dependencies. These are seams, NOT a claim that future action mappings exist.
Current base has scalar replay and a MoveCard record API with an inapplicable
movement reducer placeholder. No alternate movement/board engine is introduced.

Native `UndoCallbacks(undo, redo)`, `UndoAvailability(connected, canUndo, canRedo)`,
`UndoController(scope, availability, submit)` and `UndoIntent.action` connect to a
real session adapter, with no fake connected/default handler. `UndoQuickAction`
uses Compose tap/hold arbitration; `UndoActionsMenu` provides explicit alternatives.
These components are not yet mounted in the normal gameplay route.

## Required integration child I23b (not waived)

Integrate accepted I13 and I17–I22 (plus native shared session/replica) mappings:
ALL scalar/text/counter/reset/Clone/face/owner/note, movement/draw/reorder/shuffle,
tokens/disappearance, combat/Stack/occurrence/link/cleanup, tracking/result,
annotation/ping/comment, random and exposure-only records/references. Include
accepted revealing barriers even without a board edit, exact viewed/recording
context, mandatory-effect fidelity and inverse traversal. Charge all side records,
reference fan-out and reconstruction work, including large zero-suffix targets.
Do not treat a default metadata flag or assigned ID as executed coverage.

Implement actual relational inbox FIFO, atomic topology/stacks/removal metadata/
result/deltas, same-account cross-device shared queues and original-ID lost-response
dedup/recovery; full baseline/deltas must retain all required cursor fallback data.
Wire provisional controls and accepted rebase into the REAL native session/route,
connection gate, reauthentication/rotation/restart/account/play-incarnation fences;
mount explicit menu and quick slot, then run affected host/APK and leased native
journeys. I24–I26 consume this tree rather than introducing competing history state.
Independent B6 review and I34/B8 final integrated qualification remain required.
