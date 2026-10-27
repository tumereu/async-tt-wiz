package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Captured menu identity, not a freshly resolved selection on click. */
data class CombatStackTarget(val viewId: String, val sourceId: String?)
data class CombatStackPlan(val commands: List<GameCommand>, val prediction: PlayState) {
    /** Exactly one original is dispatched for a nonempty plan. */
    fun dispatch(send: (GameCommand) -> Unit) { commands.forEach(send) }
}

/** Stateless invocation lowering; prediction is an output, never a local authoritative table.
 * The caller supplies the normal session envelope/IDs and enqueues these exact commands.
 */
object CombatStackController {
    fun single(state: PlayState, actor: String, action: PlayAction,
        command: (PlayAction) -> GameCommand): CombatStackPlan = Builder(state, actor, command).apply { emit(action) }.plan()
    fun bind(board: BoardState, viewId: String): CombatStackTarget {
        require(board.cards.any { it.id == viewId } || board.stackEffects.any { it.id == viewId } ||
            CombatStackRules.occurrences(board.combat).any { it.id == viewId }) { "Unknown target" }
        return CombatStackTarget(viewId, CombatStackRules.liveSource(board, viewId)?.id)
    }
    fun sources(targets: List<CombatStackTarget>) = targets.mapNotNull { it.sourceId }.distinct()

    fun attackerPairs(board: BoardState, viewIds: List<String>): List<CombatPair> =
        board.combat?.pairs.orEmpty().filter { pair -> pair.attackers.any { it.id in viewIds } }

    /** Cross-lane attacker-row drops retarget whole pairs; blocker-row drops still change roles. */
    fun retargetDrop(board: BoardState, viewIds: List<String>, destination: CombatDestination): List<CombatPair> {
        if (destination.role != CombatRole.ATTACKER) return emptyList()
        val pairs = attackerPairs(board, viewIds)
        if (pairs.flatMap { it.attackers }.none { it.id in viewIds } || viewIds.any { id ->
                pairs.none { pair -> pair.attackers.any { it.id == id } } }) return emptyList()
        val target = destination.pairId?.let { id -> board.combat?.pairs?.find { it.id == id }?.attackTarget }
            ?: destination.attackTarget
        return pairs.filter { it.attackTarget != target }
    }

    fun attackTarget(state: PlayState, actor: String, viewIds: List<String>, cardId: String?,
        command: (PlayAction) -> GameCommand): CombatStackPlan = Builder(state, actor, command).apply {
        for (pair in attackerPairs(state.board, viewIds)) emit(PlayAction.Combat(
            CombatAction.SetAttackTarget(state.board.combat!!.id, pair.id, cardId)))
    }.plan()

    /** Move the live source, not the projection, using its owner's bin and normal move defaults. */
    fun graveyard(board: BoardState, occurrenceId: String): PlayAction.MoveCard {
        require(CombatStackRules.occurrences(board.combat).any { it.id == occurrenceId })
        val card = requireNotNull(CombatStackRules.liveSource(board, occurrenceId))
        val graveyard = board.containers.single { it.accountId == card.owner && it.zone == PlayZone.GRAVEYARD }
        return PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement),
            MoveDestination(graveyard.id, MoveInsertion.UNORDERED))
    }

    private class Builder(initial: PlayState, val actor: String, val command: (PlayAction) -> GameCommand) {
        var state = initial
        val commands = mutableListOf<GameCommand>()
        var envelope: GameCommand? = null
        fun emit(action: PlayAction) {
            val base = envelope ?: command(action).also { envelope = it }
            val request = PlayBatches.member(base, commands.size, action)
            val change = GameplayRules.change(state, actor, request)
            require(change.reason == null) { change.reason.orEmpty() }
            if (change.mutation != null) commands += request
            state = requireNotNull(change.state)
        }
        fun plan() = CombatStackPlan(if (commands.isEmpty()) emptyList() else listOf(requireNotNull(envelope).copy(
            action = GameAction.PlayBatch(commands.map { (it.action as GameAction.Play).action }),
            invocationId = envelope!!.requestId)), state)
    }

    fun sourceActions(state: PlayState, actor: String, targets: List<CombatStackTarget>,
        command: (PlayAction) -> GameCommand, action: (String) -> PlayAction): CombatStackPlan {
        val builder = Builder(state, actor, command)
        sources(targets).forEach { source ->
            // Frozen/missing sources are individually unusable; never substitute a replacement ID.
            if (state.board.cards.any { it.id == source }) builder.emit(action(source))
        }
        return builder.plan()
    }

    fun declare(state: PlayState, actor: String, targets: List<CombatStackTarget>, tap: Boolean,
        command: (PlayAction) -> GameCommand): CombatStackPlan {
        val builder = Builder(state, actor, command)
        val eligible = sources(targets).filter { id -> state.board.cards.find { it.id == id }?.let { card ->
            state.board.containers.find { it.id == card.containerId }?.zone == PlayZone.BATTLEFIELD
        } == true }
        if (eligible.isEmpty()) return builder.plan()
        builder.emit(PlayAction.Combat(CombatAction.Open))
        val areaId = builder.state.board.combat!!.id
        for (source in eligible) builder.emit(PlayAction.Combat(CombatAction.Declare(areaId, source)))
        if (tap) for (source in eligible) builder.emit(PlayAction.SetTapped(source, true))
        return builder.plan()
    }

    /** Pre-action visual ordering. Mixed views preserve the supplied scene order, never projections-first. */
    fun orderedTargets(board: BoardState, selected: List<String>): List<CombatStackTarget> {
        val bound = selected.map { bind(board, it) }
        val combatIds = board.combat?.let { area -> area.pairs.flatMap { it.blockers } + area.pairs.flatMap { it.attackers } }
            .orEmpty().map { it.id }
        val ordered = when {
            selected.all { it in combatIds } -> combatIds.filter { it in selected }
            selected.all { id -> board.cards.any { it.id == id } } -> board.cards.filter { it.id in selected }
                .sortedWith(compareBy<PlayCard> { it.placement?.position?.y ?: 0 }
                    .thenBy { it.placement?.position?.x ?: 0 }
                    .thenBy { card -> board.containers.find { it.id == card.containerId }?.cards?.indexOf(card.id) ?: 0 })
                .map { it.id }
            else -> selected
        }
        return ordered.map { id -> bound.first { it.viewId == id } }.distinctBy { it.sourceId ?: it.viewId }
    }

    /** Whole-drop preflight via shared prediction; nothing is dispatched until this succeeds.
     * Each emitted placement rechecks its own source and untouched gap, excluding pending members.
     */
    fun place(state: PlayState, actor: String, selected: List<String>, destination: CombatDestination,
        command: (PlayAction) -> GameCommand): CombatStackPlan {
        val retarget = retargetDrop(state.board, selected, destination)
        if (retarget.isNotEmpty()) return attackTarget(state, actor, selected,
            state.board.combat?.pairs?.find { it.id == destination.pairId }?.attackTarget ?: destination.attackTarget, command)
        val targets = orderedTargets(state.board, selected)
        val sourceIds = sources(targets)
        require(sourceIds.size == targets.size && sourceIds.isNotEmpty()) { "No live source" }
        val builder = Builder(state, actor, command)
        var target = destination
        var prefix: String? = null
        var rightAnchor = destination.cardGap.beforeId
        for ((index, source) in sourceIds.withIndex()) {
            val area = requireNotNull(builder.state.board.combat)
            val occurrence = CombatStackRules.occurrences(area).find { it.sourceId == source }
            val pending = sourceIds.drop(index + 1)
            if (prefix != null) target = target.copy(insertion = CombatInsertion.GAP,
                cardGap = IdentityGap(prefix, rightAnchor), pendingSourceIds = pending)
            else target = target.copy(pendingSourceIds = pending)
            builder.emit(PlayAction.Combat(CombatAction.Place(source, target,
                occurrence?.let { CombatStackRules.location(area, it.id) })))
            val placedArea = builder.state.board.combat!!
            val placed = CombatStackRules.occurrences(placedArea).single { it.sourceId == source }
            val location = CombatStackRules.location(placedArea, placed.id)!!
            // APPEND becomes a bound gap after the emitted prefix, not repeated free appends.
            if (index == 0) {
                val side = placedArea.pairs.single { it.id == location.pairId }.side(location.role)
                val right = side.dropWhile { it.id != placed.id }.drop(1).firstOrNull { it.sourceId !in pending }?.id
                rightAnchor = right
                target = target.copy(pairId = location.pairId, insertion = CombatInsertion.GAP,
                    cardGap = IdentityGap(placed.id, right))
            }
            prefix = placed.id
        }
        return builder.plan()
    }

    fun addEffects(state: PlayState, actor: String, targets: List<CombatStackTarget>,
        command: (PlayAction) -> GameCommand): CombatStackPlan {
        val builder = Builder(state, actor, command)
        for (source in sources(targets)) {
            val card = state.board.cards.find { it.id == source } ?: continue
            if (!CombatStackRules.canProject(state.board, card)) continue
            builder.emit(PlayAction.Stack(StackAction.AddEffect(source, CombatStackRules.stackDigest(builder.state))))
        }
        return builder.plan()
    }

    fun remove(state: PlayState, actor: String, targets: List<CombatStackTarget>,
        command: (PlayAction) -> GameCommand): CombatStackPlan {
        val builder = Builder(state, actor, command)
        for (target in targets.distinctBy { it.viewId }) {
            val board = builder.state.board
            when {
                board.stackEffects.any { it.id == target.viewId } ->
                    builder.emit(PlayAction.Stack(StackAction.RemoveEffect(target.viewId)))
                CombatStackRules.occurrences(board.combat).any { it.id == target.viewId } ->
                    builder.emit(PlayAction.Combat(CombatAction.Remove(board.combat!!.id, target.viewId)))
                else -> error("Target is not a live occurrence")
            }
        }
        return builder.plan()
    }
}
