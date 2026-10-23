# Compile-qualified merged kernel checkpoint

This checkpoint normally merges exact `addc748f34e8f4d0df797066e24ddbc6493af9aa`
onto exact `934e559b5fea8f893a0cbbd07bf2ec22fa4434f6`. Pin the saved commit
containing these notes, not the evolving branch. This is not independent verification,
application acceptance, a native journey or final UI qualification.

`kernel-compile.json` passes production server, normal Android consumer and JVM
protocol compilation. `combined-affected-01.json` remains red: protocol/wire and
seven real PostgreSQL methods pass; Android production compilation initially stopped
on two Kotlin callback conversions and two cross-module nullable smart casts. Those
four compile errors are corrected. Android host tests, instrumentation and APK did not
execute in that invocation. Authored native E2E source compiled; no device was used.

## Callable production seams

- The published runtime APIs in the original
  `managed-20261023-history-runtime-01/interfaces.md` remain present. One
  `GameService` inbox/FIFO/result/outbox and `GameplayStore` retained tree; no alternate
  Apply writer. `GameRevision` now carries both named `chatMessage` and `treeDelta`;
  `GameHead` carries both named `chat` and `retainedTreeRequired`. Chat increments
  the accepted cursor without a full-board replacement or synthetic gameplay group.
- `GameSession.gameplay` adds optional `captured: (GameCommand) -> Unit = {}`.
  `inspectFace` adds optional explicit `continuation` and `captured` arguments,
  after the existing accepted-event observer parameters. Existing calls retain their
  defaults. Receipt/result alone still cannot produce accepted inspection.
- `LobbyController.history` exposes the personal `TableHistoryController` used by
  the normal table. All toolkit/basic/Library/manual/quick/combat ordinary requests
  pass through `LobbyController.gameplay`, preserving explicit continuation intent.
  `annotate(PlayAction)` accepts only EventComment/Ping/Unping and appends ordinary
  current history with the captured viewed-event reference, never an implicit fork.
  `historyControl` remains typed Undo/Redo/MakeCurrent/BranchArchive, server-selected
  Undo/Redo target, not a client cursor target.
- `TableToolkitController` adds an optional typed `acceptedInspection` callback;
  production supplies `LobbyController.inspectFace`. Only Accepted calls
  `ManualActions.inspected`; closed/replaced inspector lifetime and changed source
  region are fenced. Existing two-argument gameplay callbacks remain unchanged.
- `AndroidAccountLocalData.treeCache(account, game, play)` binds
  `GameplayTreeCache` on the session's serial tree worker to the existing activated
  account database/driver. It never activates an account or creates a second DB or
  outgoing journal. AuthViewModel supplies this factory and closes the retained
  handle off UI after immediate private-work invalidation.
- V16's immutable SQL is added to the exact jOOQ/reset inventories; all original
  migrations/checksum guards and legacy table matcher are preserved. The combined
  SQL test exercises real chat/edit/Undo/Redo/group target/full retained export/result
  return and private original dedup. A separate removal SQL/socket/HTTP method passes.

## Fresh-verifier scope and remaining work

The supervisor can independently inspect the merged FIFO, tree export, head/version
fences, chat/lifecycle/result/accepted-disclosure/activity consumer mappings now.
Original runtime and lifecycle failure groups still require that fresh verification.
Later source identity changes must invalidate only materially affected assertions.

Normal UI mounting is present but still being checked/refined in this session:
history playback/exact timeline, explicit continuation, group explorer/advanced
archive, final-board/result presentation, accepted inspector and authorized activity
boundary. Do not certify these from compilation. Android host/instrumentation/APK
checks blocked above remain pending. Projection-occurrence links and indexed token
search remain with the supervisor's separate exact-pin owners.

**Privacy review reconciliation:** AccountLocalData/AuthViewModel changed from
per-operation open/close to a retained synchronized account storage handle and factory.
Immediate Logout/account private-clear and late disk/UI callback fences need materially
affected earlier review reconciliation; no blanket B1/B2 recertification is claimed.

Canonical state/controls and every imported original report/evidence remain unchanged.
No application state/proposal/acceptance validators or transitions are used.
