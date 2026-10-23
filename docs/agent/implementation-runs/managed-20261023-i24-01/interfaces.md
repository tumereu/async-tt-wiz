# I24 shared API — pinned I23 corrected tree

`GameplayTree.play(actor, command)` accepts `Continuation(nodeId, explicit=true)` only
on `GameAction.Play`. It validates/reduces on that exact retained boundary (no cursor
fallback), allocating a request-derived branch only on an effective mutation that
displaces current history or an existing future. Failed/no-op attempts create nothing.
Ordinary play uses processing-time current state. A historical chain can name the
preceding request-derived `board-node`; invocation ID is context, not a group/base.

`HistoryContinuation.change(tree, actor, command)` dispatches Play/MakeCurrent with
scope/account checks. `GameAction.MakeCurrent(nodeId)` captures an exact root/internal/
retained node, not a group or representative. Different shared selection and fork clear
both Redo queues; same-current no-op does not. The **existing durable FIFO/inbox** must
recover original results before this pure reducer (including retries after newer Undo).
No second queue, dedup store or authoritative local game is supplied here.

`GameplayBranch(id, forkNodeId, firstNodeId, createdByRequestId, author,
displacedNodeId, displacedBranchId)` lives in `GameplayTree.branches`. These are original
stable references for I25 return/recovery, not copied paths or an archive policy.
`GameplayTree.currentBranchId` keeps execution context even when Undo removes a branch's
first mutation. `PlayState.currentBranchId` feeds `GameplayRules.record`; immutable
initial root is never changed to simulate a branch. Replay restores the recorded branch.
Cold tree reads/reduction are unbounded by selective-Undo eligibility limits and must
run off UI. Corrected I23 remains proposed input awaiting its independent verification.

## Native API

`HistoryExplorerController(worker, post, connected, submit)` uses the application's
worker and UI-post callback. `update(tree, groups, visibleNodeIds, openingNodeId)` and
`select(exactNodeId)` reconstruct on the worker (no Undo/read cap). `state` exposes
geometry, exact local selection and reviewed board. A new scope/close fences callbacks
and clears private state. The accepted tree is never changed by this controller.

`captureMakeCurrent()` captures `HistoryActivation(GameScope, nodeId)`;
`makeCurrent(captured)` submits **only** `GameAction.MakeCurrent(captured.nodeId)` through
the supplied existing coordinator/FIFO, gated by connection/scope. Neither group-end
growth, representative changes nor deleted-cursor fallback rewrites a captured action.
`armContinuation(invocationId)` returns personal `HistoricalPlayIntent`: `lower(command)`
adds the explicit exact boundary; `predicted(command, change)` advances it only for a
successful individual mutation. Cancellation/withdrawal/late acceptance are still owned
by the real coordinator, not a new request queue here.

`HistoryGraphLayout.build(tree, membership, visibleNodeIds)` builds compact rendered
group ranges, split at true forks only, with per-exact-node marker positions and one
neutral root. `membership` is the I22 effective node→group-ID projection; default uses
existing `GameplayHistory.append`. Literal representative ranking covers the currently
implemented tap/move/life action types only. `visibleNodeIds` is I25's projection (default
all retained); ancestors/active path are preserved. `HistoryGraph.pan` clamps two-axis
pan. `HistoryExplorer` is the real Compose dialog: author glyphs, active edges/current
ring, exact-selection square, group-end tap, hold-name tooltip, title framing/filter
menu, single Make current action, outside/native-Back dismissal. It takes existing
review/filter callbacks, not sample data or local activation authority.

## Remaining I24b obligations (not executed/complete here)

Prerequisites: accepted corrected I23/shared-tree integration; real I13 relational/
session/FIFO pipeline; I14–I21 complete manual/inspection/projection/action inputs;
I22 complete grouping, literal icon families, comments and exact timeline/playback.
Then wire these components into the normal table/history routes and existing request
identity/admission/atomic commit/revision/prediction path. Test processing-time scope/
lifecycle/account checks, successful switch/fork + both Redo queues in one transaction,
same-current no-op, original retries after newer stacks/switches, lost outcomes/reconnect,
withdrawn-prediction late acceptance without resurrection, historical movement/Stack/
derived reads on one state, missing prerequisites and individual invocation lowering.
Finish all full action/icon/accessibility/gesture/orientation mappings, stable exact
timeline opening/selection and return-live/cancel/continue UX with real accepted sync.

I25 retains hidden-path/exposure/comment classification and all original archive/
restore/displaced-return semantics; this run invents none and retires none. Integrate
that policy/metadata with I24 controls and I26 coherent full-tree/restart/recovery,
including archived/hidden/internal descendants and deleted-cursor fallback. I26 remains
the final owner for full-download/third-instance/restart recovery; I24b owns control/
prediction integration assertions, I25 owns archive/visibility/return assertions.
Run the normal multi-client/native journeys when those seams/resources are ready.
B6 independent capability review and I34–I36 final suites/compatibility/UI gates remain.
Host component checks do **not** certify these server/native/recovery obligations.
