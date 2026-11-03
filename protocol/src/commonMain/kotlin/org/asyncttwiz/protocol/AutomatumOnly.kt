package org.asyncttwiz.protocol

/**
 * I-D52: Automatum is always on, so the board changes only through Automatum: an automated batch, which the engine's
 * planner always makes with the planned board's digest on its first member ([MtgTag.board]; the shared reducer refuses
 * it on any other board). Outside them a player may send only what the rules leave to the host or what isn't a change
 * to the game:
 *
 * - result suggestions: suggest (a draw, their win, or conceding: suggesting their own loss), decline, withdraw;
 * - their own Automatum settings: Skip when I have no choice ([MtgAction.SetSkipWhenNoChoice]) and Full control
 *   ([MtgAction.SetFullControl]);
 * - their own early answers ([MtgAction.SetRecord] of [EngineRecordKind.PreAnswer] records only, whose [EngineRecord.player] is
 *   the sender, I-D33);
 * - their own opening (I-D49, CR 103.5) while it lasts ([opening]), as the two batches the rules make of it: a London
 *   mulligan of their own hand (counted up by one with [MtgAction.SetMulligans] while another may be taken), or keeping
 *   (exactly the cards [GameRules.owed] from their own hand to the bottom of their own library, then Ready: clearing
 *   their opening status);
 * - the combat draft in the lanes the engine reads when attackers and blockers are declared (open the area, place,
 *   remove, declare into it, retarget, set blocks; not closing it);
 * - a card's place within its own Battlefield, which no rule reads, and passing the move ([PlayAction.PassAttention]: only
 *   shown, the move otherwise follows whoever must act, I-D34);
 * - "Inspect other side" ([PlayAction.InspectFace]).
 *
 * Anything else is refused with [RuleRefusals.AUTOMATUM_ONLY]. Undo, Redo, branching, chat, conceding by result and
 * accepting a result are separate commands and stay allowed. The server and the devices use this one check (the device
 * before it shows its prediction), so a device never shows what the server refuses.
 */
object AutomatumOnly {
    /** The rules of the opening ([GameRules.STANDARD]: every game is played with them). */
    private val rules = GameRules.STANDARD

    /**
     * The check of a play command (I-D52), before it is carried out: [RuleRefusals.AUTOMATUM_ONLY] when it is neither
     * automated nor made only of [allowed] actions in the shapes the rules give them, on the board it applies to (the
     * continuation's point, else the current one; a batch's members are all checked on that board); null otherwise,
     * also for every other command.
     *
     * Automated means Automatum's form (pivot review #4): a batch whose first member, or a single event that, carries the
     * digest of the board it was planned on ([MtgTag.board], which the shared reducer checks against the board it applies
     * to); a tag made by hand without it isn't Automatum's. (A client made to cheat could still compute the digest: the
     * server can't check the rules without running the engine.)
     */
    fun refusal(tree: GameplayTree, actor: String, command: GameCommand): String? {
        val actions = when (val action = command.action) {
            is GameAction.Play -> when {
                action.tag?.board != null -> return null
                action.tag != null -> return RuleRefusals.AUTOMATUM_ONLY
                else -> listOf(action.action)
            }
            is GameAction.PlayBatch -> when {
                action.tags.firstOrNull()?.board != null -> return null
                PlayBatches.automated(action) -> return RuleRefusals.AUTOMATUM_ONLY
                else -> action.actions
            }
            else -> return null
        }
        // An unknown point is refused by the play itself ("historyUnavailable")
        val board = tree.stateAt(command.continuation?.nodeId ?: tree.currentNodeId)?.board ?: return null
        return if (actions.all { allowed(board, actor, it) } && openingShape(board, actor, actions)) null
            else RuleRefusals.AUTOMATUM_ONLY
    }

    /** A play command carried out as the server carries it out (`GameService`): [refusal], then the shared reducer
     * ([HistoryContinuation.change]). The devices predict with it, and tests serve requests with it. */
    fun change(tree: GameplayTree, actor: String, command: GameCommand): GameplayTreeChange =
        refusal(tree, actor, command)?.let { GameplayTreeChange(reason = it) } ?: HistoryContinuation.change(tree, actor, command)

    /** Whether [actor] may send [action] outside an automated batch on [board] (an opening action only as part of its
     * batch, which [refusal] checks). */
    fun allowed(board: BoardState, actor: String, action: PlayAction): Boolean = when (action) {
        // I-D53: the rules' result is Automatum's alone
        is PlayAction.SuggestResult -> !action.byRules
        is PlayAction.DeclineResult, is PlayAction.WithdrawResult, is PlayAction.InspectFace, PlayAction.PassAttention -> true
        is PlayAction.Mtg -> when (val mtg = action.action) {
            is MtgAction.SetSkipWhenNoChoice, is MtgAction.SetFullControl -> true
            is MtgAction.SetMulligans -> opening(board, actor) && mtg.before == mulligans(board, actor) &&
                mtg.after == mtg.before + 1 && rules.canMulligan(mtg.before)
            // I-D33, I-D56: an early answer is its player's own (the attacker's exert, enlist and band choices, CMB-036–CMB-038)
            is MtgAction.SetRecord -> listOfNotNull(mtg.before, mtg.after).let { records ->
                records.isNotEmpty() && records.all { it.kind == EngineRecordKind.PreAnswer && it.player == actor } }
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

    /** The mulligans [actor] took in this opening. */
    fun mulligans(board: BoardState, actor: String): Int = board.players.firstOrNull { it.accountId == actor }?.mulligans ?: 0

    /** The cards [actor] puts on the bottom of their library on keeping: [GameRules.owed], at most their hand. */
    fun owed(board: BoardState, actor: String): Int = minOf(rules.owed(mulligans(board, actor)),
        board.containers.singleOrNull { it.zone == PlayZone.HAND && it.accountId == actor }?.cards?.size ?: 0)

    /**
     * Pivot review #5: the opening's actions in [actions] form one of its two batches. A mulligan: one [PlayAction.Mulligan],
     * counted with [MtgAction.SetMulligans] exactly when it puts a hand back (a mulligan of an empty hand only draws it).
     * A keep: Ready (the opening status cleared) last, after exactly [owed] different cards to the bottom. Nothing of the
     * opening is sent alone otherwise (the count never goes back, nothing goes to the bottom but on keeping).
     */
    private fun openingShape(board: BoardState, actor: String, actions: List<PlayAction>): Boolean {
        val mulligans = actions.filterIsInstance<PlayAction.Mulligan>()
        val counts = actions.count { it is PlayAction.Mtg && it.action is MtgAction.SetMulligans }
        val bottoms = actions.filterIsInstance<PlayAction.MoveCard>().filter { openingBottom(board, actor, it) }
        val ready = actions.count { it is PlayAction.SetStatus }
        if (mulligans.isEmpty() && counts == 0 && bottoms.isEmpty() && ready == 0) return true
        if (mulligans.isNotEmpty()) return mulligans.size == 1 && bottoms.isEmpty() && ready == 0 &&
            counts == (if (board.containers.singleOrNull { it.id == mulligans.single().handId }?.cards.isNullOrEmpty()) 0 else 1)
        return counts == 0 && ready == 1 && actions.last() is PlayAction.SetStatus &&
            bottoms.map { it.cardId }.distinct().size == bottoms.size && bottoms.size == owed(board, actor)
    }

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
