package org.asyncttwiz.protocol

/**
 * I-D52: Automatum is always on, so the board changes only through Automatum: an automated batch (one whose members
 * carry [MtgTag]s, [PlayBatches.automated]; the engine's planner always tags its first member) or an automated single
 * event. Outside them a player may send only what the rules leave to the host or what isn't a change to the game:
 *
 * - result suggestions: suggest (a draw, their win, or conceding: suggesting their own loss), decline, withdraw;
 * - their own Automatum settings: Skip when I have no choice ([MtgAction.SetSkipWhenNoChoice]) and Full control
 *   ([MtgAction.SetFullControl]);
 * - early answers ([MtgAction.SetRecord] of [EngineRecordKind.PreAnswer] records only, I-D33);
 * - their own opening (I-D49, CR 103.5) while it lasts ([opening]): a London mulligan of their own hand, its count
 *   ([MtgAction.SetMulligans]), their own hand cards to the bottom of their own library, and Ready (clearing their
 *   opening status);
 * - the combat draft in the lanes the engine reads when attackers and blockers are declared (open the area, place,
 *   remove, declare into it, retarget, set blocks; not closing it);
 * - a card's place within its own Battlefield, which no rule reads, and passing the move ([PlayAction.PassAttention]: only
 *   shown, the move otherwise follows whoever must act, I-D34);
 * - "Inspect other side" ([PlayAction.InspectFace]).
 *
 * Anything else is refused with [RuleRefusals.AUTOMATUM_ONLY]. Undo, Redo, branching, chat, conceding by result and
 * accepting a result are separate commands and stay allowed.
 */
object AutomatumOnly {
    /**
     * The server's check of a play command (I-D52), before it is carried out: [RuleRefusals.AUTOMATUM_ONLY] when it is
     * neither automated nor made only of [allowed] actions on the board it applies to (the continuation's point, else
     * the current one; a batch's members are all checked on that board); null otherwise, also for every other command.
     */
    fun refusal(tree: GameplayTree, actor: String, command: GameCommand): String? {
        val actions = when (val action = command.action) {
            is GameAction.Play -> if (action.tag != null) return null else listOf(action.action)
            is GameAction.PlayBatch -> if (PlayBatches.automated(action)) return null else action.actions
            else -> return null
        }
        // An unknown point is refused by the play itself ("historyUnavailable")
        val board = tree.stateAt(command.continuation?.nodeId ?: tree.currentNodeId)?.board ?: return null
        return if (actions.all { allowed(board, actor, it) }) null else RuleRefusals.AUTOMATUM_ONLY
    }

    /** Whether [actor] may send [action] outside an automated batch on [board]. */
    fun allowed(board: BoardState, actor: String, action: PlayAction): Boolean = when (action) {
        is PlayAction.SuggestResult, is PlayAction.DeclineResult, is PlayAction.WithdrawResult, is PlayAction.InspectFace,
        PlayAction.PassAttention -> true
        is PlayAction.Mtg -> when (val mtg = action.action) {
            is MtgAction.SetSkipWhenNoChoice, is MtgAction.SetFullControl -> true
            is MtgAction.SetMulligans -> opening(board, actor)
            is MtgAction.SetRecord -> listOfNotNull(mtg.before, mtg.after).let { records ->
                records.isNotEmpty() && records.all { it.kind == EngineRecordKind.PreAnswer } }
            else -> false
        }
        is PlayAction.Mulligan -> opening(board, actor) && own(board, actor, action.libraryId, PlayZone.LIBRARY) &&
            own(board, actor, action.handId, PlayZone.HAND)
        is PlayAction.SetStatus -> action.text.isEmpty() && opening(board, actor)
        is PlayAction.MoveCard -> openingBottom(board, actor, action) || placement(board, action)
        is PlayAction.Combat -> action.action !is CombatAction.Close
        else -> false
    }

    /** Whether [actor] is in their opening: the first turn, their status still the opening's "Mulliganing". */
    fun opening(board: BoardState, actor: String?): Boolean = board.turn.number == "1" && PlayerStatus.opening(board, actor)

    private fun own(board: BoardState, actor: String, containerId: String, zone: PlayZone) =
        board.containers.any { it.id == containerId && it.zone == zone && it.accountId == actor }

    /** One of [actor]'s own hand cards to the bottom of their own library during their opening (CR 103.5). */
    private fun openingBottom(board: BoardState, actor: String, action: PlayAction.MoveCard): Boolean =
        opening(board, actor) && action.destination.insertion == MoveInsertion.BOTTOM &&
            own(board, actor, action.destination.containerId, PlayZone.LIBRARY) &&
            board.cards.singleOrNull { it.id == action.cardId }?.let { own(board, actor, it.containerId, PlayZone.HAND) } == true

    /** A card moved within the Battlefield it is on, face and Stack untouched: only its place changes. */
    private fun placement(board: BoardState, action: PlayAction.MoveCard): Boolean =
        action.faceDown == null && action.removeStackEntryId == null &&
            action.source.containerId == action.destination.containerId &&
            board.containers.any { it.id == action.source.containerId && it.zone == PlayZone.BATTLEFIELD } &&
            board.cards.any { it.id == action.cardId && it.containerId == action.source.containerId }
}
