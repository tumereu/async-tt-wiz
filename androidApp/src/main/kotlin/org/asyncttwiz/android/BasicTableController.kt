package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import java.util.UUID

/** Invocation lowering, not a board store. The toolkit collects constituents against its batch draft. */
class BasicTableController(private val play: () -> PlayState?, private val connected: () -> Boolean,
    private val submit: (PlayAction, String?) -> Boolean) {
    fun handle(intent: TableIntent): Boolean {
        if (!connected()) return false
        val state = play() ?: return false
        val invocation = (intent as? TableIntent.Move)?.invocationId ?: UUID.randomUUID().toString()
        return when (intent) {
            is TableIntent.Draw -> {
                val library = state.board.containers.singleOrNull { it.id == intent.libraryId && it.zone == PlayZone.LIBRARY }
                val hand = state.board.containers.singleOrNull { it.id == intent.handId && it.zone == PlayZone.HAND }
                if (library == null || hand == null || library.accountId != hand.accountId || library.cards.firstOrNull() != intent.topId) false
                else LibraryActions.draw(state.board, library.id, hand.id, 1).any {
                    submit(it.copy(destination = ownerMoveDestination(state.board, it.cardId, it.destination)), invocation)
                }
            }
            is TableIntent.Rotate -> {
                // The animated hit item can lag prediction/remote updates. Resolve once from the
                // session's current board at emission; all selected cards follow the context card.
                val context = state.board.cards.singleOrNull { it.id == intent.scope.contextId } ?: return false
                if (state.board.containers.none { it.id == context.containerId && it.zone == PlayZone.BATTLEFIELD }) return false
                val tapped = if (intent.toggle) !context.tapped else intent.tapped
                var sent = false
                for (id in intent.scope.cardIds.distinct()) if (state.board.cards.any { card -> card.id == id &&
                    state.board.containers.any { it.id == card.containerId && it.zone == PlayZone.BATTLEFIELD } })
                    sent = submit(PlayAction.SetTapped(id, tapped), invocation) || sent
                sent
            }
            is TableIntent.PlayerDelta -> if (intent.delta == 0) false else submit(
                intent.color?.let { PlayAction.AdjustCounter(CounterTarget.PLAYER, intent.accountId, it, intent.delta.toString()) }
                    ?: PlayAction.AdjustLife(intent.accountId, intent.delta.toString()), invocation)
            is TableIntent.Move -> move(state, intent, invocation)
            is TableIntent.Submit -> {
                val action = intent.action
                val lowered = if (action is PlayAction.MoveCard && action.removeStackEntryId == null && state.board.containers.any {
                        it.id == action.destination.containerId && it.zone == PlayZone.STACK })
                    projection(state, action) ?: return false else action
                submit(lowered, intent.invocationId ?: invocation)
            }
            else -> false // Personal inspection/selection/navigation never become logged requests.
        }
    }

    private fun move(state: PlayState, intent: TableIntent.Move, invocation: String, stackEntryId: String? = null): Boolean {
        val destinations = intent.scope.cardIds.groupBy { ownerMoveDestination(state.board, it, intent.destination) }
        if (destinations.size > 1 || destinations.keys.singleOrNull()?.let { it != intent.destination } == true) {
            // Splitting by owner must not repair stale captures or bypass the single Stack-entry rule.
            if (intent.scope.cardIds.size > 1 && intent.scope.cardIds.any { it in CombatStackRules.stack(state.board).cards }) return false
            for (id in intent.scope.cardIds) {
                val card = CombatStackRules.liveSource(state.board, id) ?: return false
                val captured = intent.sources[id] ?: return false
                val source = captured.source() ?: return false
                if (captured.sourceId != null && captured.sourceId != card.id || source.containerId != card.containerId || source.placement != card.placement) return false
            }
            var sent = false
            for ((destination, ids) in destinations) {
                val predecessor = play()?.takeIf { connected() && it.id == state.id } ?: break
                sent = move(predecessor, intent.copy(scope = TableScope(intent.scope.contextId.takeIf { it in ids } ?: ids.first(), ids),
                    destination = destination, sources = intent.sources.filterKeys { it in ids }), invocation, stackEntryId) || sent
            }
            return sent
        }
        val stack = CombatStackRules.stack(state.board)
        if (intent.scope.cardIds.any { it in stack.cards }) {
            val entry = intent.scope.cardIds.singleOrNull() ?: return false
            if (entry != stack.cards.firstOrNull()) return false
            val effect = state.board.stackEffects.singleOrNull { it.id == entry }
            val destination = when (val d = intent.destination) {
                is TableDestination.Container -> state.board.containers.singleOrNull { it.id == d.id && it.zone == d.zone }
                is TableDestination.Grid -> state.board.containers.singleOrNull { it.id == d.containerId && it.zone == PlayZone.BATTLEFIELD }
                is TableDestination.CardPile -> state.board.containers.singleOrNull { it.id == d.source?.containerId && it.zone == PlayZone.BATTLEFIELD }
                is TableDestination.Combat -> null
            } ?: return false
            if (destination.zone == PlayZone.STACK) return false
            if (effect != null) {
                val card = CombatStackRules.liveSource(state.board, entry) ?: return false
                val captured = intent.sources[entry] ?: return false
                if (captured.sourceId != null && captured.sourceId != card.id) return false
                val source = captured.source() ?: return false
                if (source.containerId != card.containerId || source.placement != card.placement) return false
                // A zone drop onto the source's current zone dismisses only this projection.
                if (state.board.containers.single { it.id == card.containerId }.zone == destination.zone)
                    return submit(StackMenuActions.remove(entry), invocation)
                return move(state, intent.copy(scope = TableScope(card.id, listOf(card.id)),
                    sources = mapOf(card.id to captured)), invocation, entry)
            }
        }
        val ids = ordered(state, intent.scope.cardIds)
        if (ids.isEmpty() || ids.size != intent.scope.cardIds.distinct().size) return false
        // Compare semantic locations, not animated pixels, and never repair a stale source by recapture.
        val sources = ids.associateWith { id -> intent.sources[id]?.source() ?: return false }
        if (ids.any { id -> state.board.cards.single { it.id == id }.let {
                it.containerId != sources.getValue(id).containerId || it.placement != sources.getValue(id).placement } }) return false
        val target = when (val d = intent.destination) {
            is TableDestination.Container -> state.board.containers.singleOrNull { it.id == d.id && it.zone == d.zone }
            is TableDestination.Grid -> state.board.containers.singleOrNull { it.id == d.containerId && it.zone == PlayZone.BATTLEFIELD }
            is TableDestination.CardPile -> state.board.containers.singleOrNull { it.id == d.source?.containerId && it.zone == PlayZone.BATTLEFIELD }
            is TableDestination.Combat -> return false // Toolkit owns occurrence placement, not a real-card move.
        } ?: return false
        if (target.zone == PlayZone.STACK) {
            if (ids.any { !CombatStackRules.canProject(state.board, state.board.cards.single { card -> card.id == it }) }) return false
            var sent = false
            for (id in ids) {
                val predecessor = play()?.takeIf { connected() && it.id == state.id } ?: break
                val before = CombatStackRules.stack(predecessor.board).cards.toSet()
                val added = submit(PlayAction.Stack(StackAction.AddEffect(id, GameplayRules.stackDigest(predecessor))), invocation)
                sent = added || sent
                val card = predecessor.board.cards.single { it.id == id }
                if (added && intent.handDoubleTap && state.board.containers.any { it.id == card.containerId && it.zone == PlayZone.HAND } &&
                    StackContexts.permanentOnly(predecessor.definitions.find { it.digest == card.definition }?.definition)) {
                    val next = play()?.takeIf { connected() && it.id == state.id } ?: break
                    val entry = CombatStackRules.stack(next.board).cards.firstOrNull { entry -> entry !in before &&
                        CombatStackRules.liveSource(next.board, entry)?.id == id } ?: continue
                    // Exactly the manual resolver: separate history group, same atomic invocation.
                    val resolveInvocation = invocation
                    for (action in StackMenuActions.destination(next, entry, PlayZone.BATTLEFIELD))
                        if (!submit(action, resolveInvocation)) break
                }
            }
            return sent
        }
        val pileTarget = (intent.destination as? TableDestination.CardPile)?.let { d ->
            val captured = d.source?.source()?.placement ?: return false
            val card = state.board.cards.singleOrNull { it.id == d.cardId } ?: return false
            if (card.id in ids || card.containerId != target.id || card.placement != captured) return false
            if ((target.cards.filter { id -> state.board.cards.single { it.id == id }.placement == captured } + ids).distinct().size > 3) return false
            captured
        }
        val pileRight = pileTarget?.let { p -> target.cards.firstOrNull { id ->
            id !in ids && state.board.cards.single { it.id == id }.placement == p } }
        val grid = intent.destination as? TableDestination.Grid
        val positions = if (grid != null) {
            val context = sources[intent.scope.contextId]?.placement?.position
            ids.mapIndexed { index, id ->
                val old = sources.getValue(id).placement?.position
                id to if (context != null && old != null) BoardPosition(grid.column + old.x - context.x, grid.row + old.y - context.y)
                    else BoardPosition(grid.column + index, grid.row)
            }.toMap()
        } else emptyMap()
        if (positions.isNotEmpty()) {
            for ((position, members) in ids.groupBy { positions.getValue(it) }) {
                if (members.size > 3 || state.board.cards.any { it.id !in ids && it.containerId == target.id && it.placement?.position == position }) return false
            }
        }
        var prefix: String? = null
        val placed = mutableMapOf<BoardPosition, String>()
        var any = false
        for ((index, id) in ids.withIndex()) {
            if (!connected()) break
            val predecessor = play()?.takeIf { it.id == state.id && it.gameId == state.gameId } ?: break
            val currentTarget = predecessor.board.containers.singleOrNull { it.id == target.id } ?: break
            val destination = when {
                target.zone == PlayZone.HAND && (intent.destination as? TableDestination.Container)?.handGap != null -> {
                    val gap = (intent.destination as TableDestination.Container).handGap!!
                    MoveDestination(target.id, MoveInsertion.GAP, afterId = prefix ?: gap.afterId, beforeId = gap.beforeId,
                        pendingIds = ids.drop(index + 1))
                }
                pileTarget != null -> MoveDestination(target.id, MoveInsertion.PILE_GAP, pileTarget.position, pileTarget.pileId,
                    afterId = prefix, beforeId = pileRight,
                    targetCardId = (intent.destination as TableDestination.CardPile).cardId, pendingIds = ids.drop(index + 1))
                grid != null -> {
                    val position = positions.getValue(id)
                    val anchor = placed[position]
                    if (anchor == null) MoveDestination(target.id, MoveInsertion.EMPTY, position)
                    else {
                        val p = predecessor.board.cards.singleOrNull { it.id == anchor }?.placement ?: break
                        MoveDestination(target.id, MoveInsertion.PILE_BOTTOM, p.position, p.pileId)
                    }
                }
                target.zone == PlayZone.LIBRARY -> {
                    val previous = prefix
                    if (previous == null) MoveDestination(target.id,
                        if ((intent.destination as TableDestination.Container).bottom) MoveInsertion.BOTTOM else MoveInsertion.TOP)
                    else {
                        val reduced = currentTarget.cards.filter { it != id }
                        val index = reduced.indexOf(previous)
                        if (index < 0) break // A lost prefix is not permission to choose a fresh top.
                        MoveDestination(target.id, MoveInsertion.GAP, afterId = previous, beforeId = reduced.getOrNull(index + 1),
                            libraryInsertion = if ((intent.destination as TableDestination.Container).bottom) MoveInsertion.BOTTOM else MoveInsertion.TOP)
                    }
                }
                else -> MoveDestination(target.id, MoveInsertion.UNORDERED)
            }
            val source = sources.getValue(id)
            val action = PlayAction.MoveCard(id, source, destination, removeStackEntryId = stackEntryId)
            if (target.zone == PlayZone.HAND && source.containerId == target.id && destination.insertion == MoveInsertion.GAP) {
                val members = currentTarget.cards
                val reduced = members.filter { it != id }
                val projected = reduced.filterNot { it in destination.pendingIds }
                val left = destination.afterId?.let { projected.indexOf(it) } ?: -1
                val index = destination.afterId?.let { reduced.indexOf(it) + 1 } ?: 0
                val card = predecessor.board.cards.single { it.id == id }
                if ((destination.afterId == null || left >= 0) && projected.getOrNull(left + 1) == destination.beforeId &&
                    members.indexOf(id) == index && card.covered && !card.explicitlyCovered) {
                    prefix = id // A no-op Hand member must still anchor the next selected card.
                    continue
                }
            }
            if (target.zone in setOf(PlayZone.LIBRARY, PlayZone.STACK) && source.containerId == target.id) {
                val card = predecessor.board.cards.singleOrNull { it.id == id }
                val members = currentTarget.cards
                val sourceValid = card != null && card.containerId == source.containerId && card.placement == source.placement &&
                    (source.edge == MoveSourceEdge.MEMBER || target.zone == PlayZone.LIBRARY &&
                        if (source.edge == MoveSourceEdge.TOP) members.firstOrNull() == id else members.lastOrNull() == id) &&
                    (source.bottomSlice.isEmpty() || target.zone == PlayZone.LIBRARY &&
                        source.bottomSlice.first() == id && members.takeLast(source.bottomSlice.size) == source.bottomSlice)
                val index = when (destination.insertion) {
                    MoveInsertion.TOP -> 0
                    MoveInsertion.BOTTOM -> members.lastIndex
                    else -> members.filter { it != id }.indexOf(destination.afterId) + 1
                }
                if (sourceValid && !(card?.token == true && target.zone == PlayZone.LIBRARY) && members.indexOf(id) == index &&
                    (target.zone != PlayZone.LIBRARY || id in currentTarget.knownTop || id in currentTarget.knownBottom)) {
                    prefix = id // An already-placed ordered member is still the next member's anchor.
                    continue
                }
            }
            if (pileTarget != null && source.containerId == target.id && source.placement == pileTarget) {
                val members = currentTarget.cards.filter { member -> predecessor.board.cards.single { it.id == member }.placement == pileTarget }
                val reduced = members.filter { it != id && it !in destination.pendingIds }
                val left = destination.afterId?.let { reduced.indexOf(it) } ?: -1
                val addressed = predecessor.board.cards.singleOrNull { it.id == destination.targetCardId }
                if ((destination.afterId == null || left >= 0) && reduced.getOrNull(left + 1) == destination.beforeId &&
                    addressed?.containerId == target.id && addressed.placement == pileTarget &&
                    members.indexOf(id) == (destination.afterId?.let { members.filter { m -> m != id }.indexOf(it) + 1 } ?: 0)) {
                    prefix = id // Already placed: keep the identity, but fabricate neither request nor node.
                    continue
                }
            }
            val constituents = GameplayRules.moveActions(predecessor, action)
            // A failed first enqueue must not run its reset conveniences on an unmoved card.
            if (!submit(constituents.first(), invocation)) continue
            any = true
            constituents.drop(1).forEach { submit(it, invocation) }
            prefix = id
            positions[id]?.let { placed[it] = id }
        }
        return any
    }

    companion object {
        fun projection(state: PlayState, action: PlayAction.MoveCard): PlayAction.Stack? {
            if (action.removeStackEntryId != null) return null
            val card = state.board.cards.singleOrNull { it.id == action.cardId } ?: return null
            if (!CombatStackRules.canProject(state.board, card) || card.containerId != action.source.containerId ||
                card.placement != action.source.placement) return null
            return PlayAction.Stack(StackAction.AddEffect(card.id, GameplayRules.stackDigest(state)))
        }

        fun sources(state: PlayState, ids: List<String>, top: Boolean = false): Map<String, TableMoveSource> =
            state.board.cards.filter { it.id in ids }.associate { c -> c.id to TableMoveSource(c.containerId,
                c.placement?.let { TablePlacement(it.position.x, it.position.y, pileId = it.pileId) }, top) }

        private fun TableMoveSource.source(): MoveSource? = MoveSource(containerId, placement?.let {
            PlayPlacement(BoardPosition(it.column.toInt(), it.row.toInt()), it.pileId ?: return null)
        }, if (libraryTop) MoveSourceEdge.TOP else MoveSourceEdge.MEMBER, bottomSlice, takeFromEdge)

        /** Same-source order is semantic; cross-source order is stable spatial order, not card name. */
        private fun ordered(state: PlayState, ids: List<String>): List<String> {
            val cards = state.board.cards.associateBy { it.id }
            return ids.distinct().filter { it in cards }.sortedWith(compareBy<String> {
                state.board.containers.indexOfFirst { c -> c.id == cards.getValue(it).containerId }
            }.thenBy { cards.getValue(it).placement?.position?.y ?: 0 }
                .thenBy { cards.getValue(it).placement?.position?.x ?: 0 }
                .thenBy { id -> state.board.containers.single { it.id == cards.getValue(id).containerId }.cards.indexOf(id) })
        }
    }
}
