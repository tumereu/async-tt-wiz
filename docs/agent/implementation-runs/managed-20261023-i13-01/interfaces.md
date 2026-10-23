# I13 proposed concrete shared interface (not acceptance)

Pinned base: a4b444e192776f5d165fb9188fc6e526f697620e. No state/journal edits.
Public Kotlin package: `org.asyncttwiz.protocol`.

- Extend the existing `GameAction` with `GameAction.Play(action: PlayAction)`;
  wire discriminator `play`, nested discriminators `setTapped`, `setLife`,
  `adjustLife`, `moveCard`. Same GameCommand and GameService FIFO, not a new queue.
- `GameplayRules.change(state: PlayState, actor: String, command: GameCommand): GameplayChange`.
  `GameplayChange(state?, mutation?, reason?)`, derived `outcome` accepted/noOp/rejected.
  Predictions call exactly this entry point; server owns actual execution and parent.
  Scope is gameId + playId, no preparationId/invitationId. Historical continuation
  remains I24 (currently refused, never silently treated as ordinary).
- `PlayAction.SetTapped(cardId, tapped)`, `SetLife(accountId, value: String)`,
  `AdjustLife(accountId, delta: String)`; no previous-value operand/lock.
- `PlayAction.MoveCard(cardId, source: MoveSource, destination: MoveDestination,
  expectedStackDigest: String? = null)`.
  `MoveSource(containerId, placement: PlayPlacement? = null, edge = MEMBER)`;
  edges MEMBER/TOP/BOTTOM. Specific-card moves never substitute targets.
  `MoveDestination(containerId, insertion, position?, pileId?, afterId?, beforeId?)`.
  Insertions TOP/BOTTOM/GAP (two exact neighbors), UNORDERED (Graveyard/Exile),
  EMPTY (Battlefield coordinates), PILE_TOP/PILE_BOTTOM (existing pile identity).
  `BoardPosition(x: Int,y: Int)` logical subunits, `PlayPlacement(position,pileId)`;
  `PlayCard.placement` optional, default null; existing constructor calls stay valid.
  Battlefield region remains an existing PlayContainer. Same pile ID + position
  binds members, container card sequence gives top-first within-pile order, cap 3.
  Empty placement creates UUIDv5(requestId, `pile/0`); no pixel/camera state.
- Individual `GameplayMutation(id,parentId,branchId,requestId,author,invocationId,
  action,transition,schemaVersion=1)`. Node UUIDv5(requestId, `board-node`).
  `GameplayTransition(fields, movement?)` stores actual field tuples and locations,
  never a replacement board/card. `GameplayFieldChange(targetId,field,from,to,color?)`;
  TAPPED/LIFE initially; CORNER/COUNTER reserved for move-entry cleanup composition.
  `GameplayMoveChange(cardId,from:CardLocation,to:CardLocation)`;
  ordered locations retain actual neighbor identities, unordered bins do not.
- `GameplayHistory.replay(state, mutation): GameplayChange` uses the same processor
  and requires exact accepted relevant transition fidelity; unrelated fields persist.
- `GameplayGroup` compact server-derived prefix context on `PlayState.currentGroup`
  (default null). `GameplayHistory.append(previous,mutation)` groups this I13 subset;
  I22 extends the same processor/context for remaining families and icons.
- Planned server payload additions in this unit: nullable `mutation` on GameResult
  and GameRevision; coherent materialized PlayState retained on revisions for current
  compatibility, separate paged history read for actual mutation records. V11 owns
  gameplay relational mutation/group tables. Later I14 incremental/baseline work may
  extend the existing envelopes; do not build a parallel reducer or history model.

First pushed implementation covers scalar reduction/codec/replay with tests. Moves,
durable records, grouping refinement and PostgreSQL/socket coverage follow in this
same I13 session. No B3 acceptance, I14 UI, I17–I26 completion or review is asserted.

## Delivered refinements (same interface, additive details)

- The concrete `GameResult.mutation`, `GameRevision.mutation` and
  `GameRevision.group` are nullable/defaulted. Current-compatible revisions still
  carry PlayState; results never carry replacement board snapshots.
- `GameplayRules.moveActions(state, move): List<PlayAction>` lowers entry convenience
  to move, each captured `ClearCardCounter(cardId,color)`, then `ResetCorner(cardId)`.
  Each constituent needs its own ordinary GameCommand/request ID. No hidden atomic
  multi-field reset; the move itself preserves counters/corner/owner/note/orientation.
  Reset reads actual retained face/default at its own execution turn. These narrow
  cleanup primitives do not claim I17's full manual-tool UI or remaining edits.
- `GameplayRules.stackDigest(state)` implements the exact protocol-v1 canonical
  real-card entry projection. I21 extends this same projection for effect entries;
  there is no effect engine yet. The empty digest includes the play ID.
- `GameplayTransition.reads: List<GameplayRead>` captures actual Reset face/default.
  Move tuples also retain destination pile size and semantic Stack before/after
  when applicable; unordered bins never capture unrelated contents/indices.
- `GameplayHistory.reverse(state,mutation): BoardState?` provides exact recorded
  inverse traversal without a shared Undo selection/control implementation (I23).
  Battlefield inventory is canonically grouped by pile ID; order *within* each
  pile is top-first. Between-pile inventory order has no visual/semantic meaning.
- `GET /v1/games/{gameId}/plays/{playId}/mutations?after=REVISION` returns
  `GameplayHistoryPage(playId,mutations,groups,throughRevision,next?)`, 100 records
  per page, `next` an opaque-to-callers decimal effective-ordinal cursor. This is a
  current-play retained mutation read, not I26's complete branched-tree baseline.
- V11 introduces relational `gameplay_mutation` and `gameplay_group` with versioned
  JSONB payloads. The existing game lock/transaction commits these, materialized
  play, revision, original outcome and outbox together. No-op/rejected requests
  retain individual outcomes/outbox entries but invent no mutation/revision/group.
- All field tuples are actual accepted values. Tapped tuples exclude location;
  life tuples include actual numeric input/output. Every absent effect is unchanged:
  no stale card/player/board replacement, no previous-value guard for online edits.
  Life arithmetic is decimal text, not capped at machine Long.
- Token creation/disappearance, effects/combat, full grouping families, Undo/Redo,
  historical switching and full-tree baselines remain their later assigned tasks.
