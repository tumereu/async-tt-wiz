# Production history API — runtime lane

Pin the saved commit containing this file; do not pin the evolving worktree. This is an
implementation API handoff, not acceptance, independent verification or native coverage.
The final run report will retain combined runtime evidence and remaining limitations.

## Normal controller/session calls

`LobbyController` exposes these calls for the currently selected game/account:

```kotlin
fun historyControl(action: GameAction): Boolean
fun gameplay(action: PlayAction, invocationId: String? = null,
    continuation: Continuation? = null): Boolean
fun retainedTree(): GameplayTree?
fun reviewHistory(nodeId: String?, complete: (DownloadedHistoryState) -> Unit): Boolean
fun downloadHistory(): Boolean
```

`GameSession` exposes the corresponding explicit game/account APIs:

```kotlin
fun historyControl(game: GameSummary, actor: String, action: GameAction): Boolean
fun gameplay(game: GameSummary, actor: String, action: PlayAction,
    invocationId: String? = null, continuation: Continuation? = null): Boolean
fun retainedTree(gameId: String): GameplayTree?
fun review(gameId: String, nodeId: String?, complete: (DownloadedHistoryState) -> Unit): Boolean
fun downloadHistory(game: GameSummary): Boolean
```

Controls are exactly `GameAction.Undo`, `GameAction.Redo`,
`GameAction.MakeCurrent(exactNodeId)` and `GameAction.BranchArchive(intent)`.
Use `BranchArchiveIntent.Archive`, `Restore`, `ArchiveAndReturn` or `Recover`.
For archive-and-return, obtain the real preview from
`BranchArchives.previewReturn(tree, tree.archiveCatalog(), branchId)`; preserve
its exact `target` and `returnHead` as `expectedReturnHead`. Never guess heads
by filtering nodes by branch ID. `tree.archiveCatalog()` also resolves internal
shared-prefix boundaries and original displaced selections/materialization versions.

Historical mutation requires `Continuation(exactNodeId, explicit = true)` on the
ordinary `gameplay` call. Opening/scrubbing/review is **not** that flag. No flag is
added to Undo/Redo. Those controls carry no client-selected target.

`true` means captured/sent online, not server acceptance. Controls share the existing
original request map, stream fence and recovery result callbacks; multiple controls/play
can be inflight. No pending-result or animation gate is introduced. The session's existing
`settled(GameCommand, GameResult)` callback receives the exact original outcome;
`GameResult.targetNodeId` identifies the server-selected target where applicable.
Offline new shared actions return false. Offline personal review is read-only.

`retainedTree` is accepted immutable data, not prediction or authority for shared edits.
It contains all nodes/offshoots/futures, definitions, branch heads/lineage, archive recovery,
selection control identity, removal parents and current account Redo. Use the worker
`review` callback for materialized boards; do not call cold `stateAt` on the UI thread.
Null review node means follow current; a deleted cursor resolves to its retained parent,
not the shared head. Callbacks are account/incarnation/clear-fenced. Review itself submits
no event, exposure or control and does not clear Redo.

## Bind existing account storage on the storage worker

Both constructors accept:

```kotlin
treeCache: ((accountId: String, gameId: String, playId: String) -> GameplayTreeStore)?
```

The factory is called on the session's serial tree worker, never a Composable/UI thread.
Bind `GameplayTreeCache.bind(existingClientStorage, accountId, gameId, playId)` using
the **existing** account-activated SQLDelight database. Do not create a second DB,
outgoing journal or authoritative snapshot. Account lifecycle owns activation/Logout
cleanup and handle lifetime. Session clear immediately invalidates bound caches.
Native account-storage mounting remains with the later UI/lifecycle owner: the current
AuthViewModel/AccountLocalData constructor site is not leased to this runtime lane.
Without a cache factory the existing accepted-memory baseline compatibility path remains;
that mode does not claim durable offline downloads.

Production `SocketGameConnection.treeTransfer` obtains the SAME `/baselines` manifest
and chunk routes, now with `recordCount`/`head` and flat NDJSON accepted records.
`GameSession` uses I26 `TreeDownload.stage/publish/apply` and the supplied SQLDelight
store, serial worker replay, contiguous `GameRevision.treeDelta` catch-up or full-generation
restart, then one accepted publication. No new mutation endpoint, lazy branch request,
or transport exists. Old final results are recovered before resending originals.

Final runtime additions preserve the API above: `watch` can restore a complete accepted
generation from the bound cache before connecting, including a fresh offline process with
no recreated commands. Production `GameHead.retainedTreeRequired` prevents treating a
root current pointer as proof that no retained future/offshoot exists. Ordinary gameplay
revisions omit the per-node board snapshot and carry `treeDelta`; non-tree revisions with
neither `play` nor `treeDelta` advance the same accepted cursor without losing history.
Later owners that change the board must supply the corresponding tree records, not only
a new full-board endpoint. The store's session `guard` is evaluated inside its existing
SQLDelight transaction before stale account/play callbacks can write accepted data.

## Consolidation overlap

The runtime edits `GameWire`, `GameplaySync`, `GameplayTree`, `HistoryContinuation`,
`TreeSync`, `GameService`, `GameplayStore`, `PlayStore`, `GameSession`, `GameplayReplica`,
`GameConnection` and `LobbyController`. Preserve later lifecycle/chat/result/activity
cases in these shared dispatch/publication seams. V16 is this lane's only migration;
V10–V15 and the exact reset registry belong to their existing owners. This branch does
not duplicate older core fixes or I16 PILE_GAP prefix insertion. Rendering, mounting,
native journeys, separate fresh runtime verification and full B6/final reviews remain later.

## Accepted InspectFace consumer — FG-TK-03

The normal controller now exposes (session equivalent adds explicit game/actor):

```kotlin
fun inspectFace(action: PlayAction.InspectFace, lifetimeId: String,
    isCurrent: () -> Boolean, observed: (InspectionEvent) -> Unit): InspectionSubscription?
```

The returned subscription contains `request: InspectionRequest` with original request ID,
game/play scope, account, UI lifetime ID and the **exact** card/face/container action.
Null means not enqueued. A non-null handle is **not** an accepted disclosure.
Keep it for the inspector lifetime; `close()` cancels presentation only, not the
original server request/result/exposure. `isCurrent` is mandatory: compare the captured
UI lifetime/selection, not merely whether some inspector is open. The controller adds
its own account/selected-game/play/route fences.

Only `InspectionEvent.Accepted` may invoke
`manualActions.inspected(event.state, viewer, event.disclosure)`. It contains the exact
bound request, accepted mutation, accepted through-revision and a still-applicable
accepted endpoint. It is emitted once from accepted retained-tree proof, never a
prediction, receipt or accepted result alone. Result-before-state waits; revision-before-
result is sufficient. Duplicates do not reopen the inspector. Withdrawing prediction
does not invent acceptance or cancel a still-interested observer's actual late outcome.

`InspectionEvent.NotAccepted` carries the original final rejection/no-op `result`, or
a definitive non-retryable `admissionError`. It must not call `ManualActions.inspected`.
Closed/replaced lifetime, changed account/play/region, or unavailable exact face suppresses
presentation rather than retargeting or reopening a stale overlay. The original durable
outcome/exposure and normal `settled` flow remain intact. Own-area hold stays local.
No extra request queue, disclosure journal, policy or ManualActions/UI mounting is added.

## Authorized activity boundary consumer — activity pin 919fd511

This branch does not merge/copy Activity.kt, ActivityNavigation.kt or notification logic.
The exact authorized pin's `ActivityTarget` has `gameId`, `destination`, `playId`,
`groupId`, `boundaryNodeId`; later consolidation maps a **GROUP** target to:

```kotlin
CapturedHistoryTarget(currentAccountId, target.gameId, target.playId!!,
    target.groupId, target.boundaryNodeId!!)
```

Call `LobbyController.reviewHistoryTarget(captured, isCurrent, complete)`, or
`GameSession.reviewTarget(captured, isCurrent, complete)` for explicit session clients.
`complete` receives `CapturedHistoryReview(target, view: DownloadedHistoryState)`.
The callback's `view.state`/`cursor` is the actual accepted-tree boundary, not the current
board relabelled as history. A deleted cursor follows retained removal parents to its
nearest surviving ancestor. Group metadata is retained for correlation; it does not
replace the exact boundary with a current group end.

Pass only an already authorized target, **never raw push/hint data**. Keep `isCurrent`
bound to that authorization/route lifetime; revocation or replacement makes it false.
The session also checks current account, participant access, active game/play and the
downloaded incarnation, before worker/cursor work and before UI delivery. Stale scope
returns false or drops the obsolete callback. A missing retained boundary returns an
explicit unavailable view, never the shared/current board as replacement. No gameplay,
MakeCurrent, presence, read receipt, or outgoing command is created. If the complete
tree is not downloaded yet, use the existing full-download API and retry the same captured
target after readiness; there is no lazy branch route or automatic target substitution.
