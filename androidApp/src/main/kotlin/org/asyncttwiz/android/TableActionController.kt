package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import java.security.SecureRandom

data class TableActionScope(val gameId: String, val playId: String)
data class TurnPlayerTargets(val accountId: String, val battlefieldId: String, val libraryId: String, val handId: String)
enum class LibraryTool { CONTENTS, DRAW_N, VIEW, REVEAL, SHUFFLE, MULLIGAN_N, MULLIGAN_MINUS_ONE }
enum class CombatTool { CREATE, CLOSE, DECLARE, DECLARE_AND_TAP, ADVANCE_AND_CLOSE, NO_ATTACK }
enum class EndingTool { SUGGEST_WIN, SUGGEST_LOSS, SUGGEST_DRAW, ACCEPT }
enum class StackTargetKind { INSTANT_OR_SORCERY, PERMANENT, EFFECT }
data class ExplicitStackTarget(val id: String, val kind: StackTargetKind)

/** Required semantic routes into the corresponding capability, never dummy menu buttons.
 * The table host supplies the real I18/I19/I21/I27 action components and ordinary sender.
 * These routes do not implement combat/Stack or introduce a second request/prediction queue. */
interface TableActionRoutes {
    fun library(intent: TableActionIntent.Library)
    fun combat(intent: TableActionIntent.Combat)
    fun token(intent: TableActionIntent.Token)
    fun ending(intent: TableActionIntent.Ending)
    fun stack(intent: TableActionIntent.Stack)
    fun player(intent: TableActionIntent.Player)
    fun selectAttackers(intent: TableActionIntent.SelectAttackers) {}
}

sealed interface TableActionIntent {
    val scope: TableActionScope
    data class Next(override val scope: TableActionScope) : TableActionIntent
    data class End(override val scope: TableActionScope) : TableActionIntent
    data class Choose(override val scope: TableActionScope, val position: TurnPosition) : TableActionIntent
    data class Bundle(override val scope: TableActionScope, val actions: List<TableActionIntent>) : TableActionIntent
    data class SelectAttackers(override val scope: TableActionScope, val advance: Boolean) : TableActionIntent
    data class Orient(override val scope: TableActionScope, val cardIds: List<String>, val tapped: Boolean) : TableActionIntent
    data class Untap(override val scope: TableActionScope, val targets: TurnPlayerTargets,
        val cardIds: List<String>, val advance: Boolean) : TableActionIntent
    data class Draw(override val scope: TableActionScope, val targets: TurnPlayerTargets,
        val cardId: String?, val advance: Boolean) : TableActionIntent
    data class Library(override val scope: TableActionScope, val targets: TurnPlayerTargets,
        val tool: LibraryTool, val handIds: List<String>, val defaultCount: Int = 7) : TableActionIntent
    data class Combat(override val scope: TableActionScope, val tool: CombatTool,
        val areaId: String?, val sourceIds: List<String> = emptyList()) : TableActionIntent
    data class Token(override val scope: TableActionScope) : TableActionIntent
    data class Ending(override val scope: TableActionScope, val tool: EndingTool,
        val suggestionId: String? = null) : TableActionIntent
    data class Stack(override val scope: TableActionScope, val target: ExplicitStackTarget) : TableActionIntent
    data class Player(override val scope: TableActionScope, val accountId: String) : TableActionIntent
}

data class PlayerValueTarget(val scope: TableActionScope, val accountId: String, val color: CounterColor? = null)

/** Main-thread adapter. The supplied ordinary sender owns stable request IDs, retries and prediction.
 * All fresh input is connection-gated; a failed send never causes an automatic resample/resubmit. */
private fun secureTableSample(): (Int) -> Int {
    val random = SecureRandom()
    return { bound -> RandomV1.sample(bound) { random.nextInt().toLong() and 0xffffffffL } }
}

class TableActionController(private val state: () -> PlayState?, private val canAct: () -> Boolean,
    private val submit: (PlayAction) -> Boolean, private val routes: TableActionRoutes,
    private val sample: (Int) -> Int = secureTableSample()) {

    private fun current(scope: TableActionScope): PlayState? = state()?.takeIf {
        canAct() && it.gameId == scope.gameId && it.id == scope.playId
    }
    fun scope(): TableActionScope? = state()?.let { TableActionScope(it.gameId, it.id) }
    fun enabled(scope: TableActionScope) = current(scope) != null

    fun targets(play: PlayState): TurnPlayerTargets? {
        val account = play.board.turn.accountId
        fun container(zone: PlayZone) = play.board.containers.singleOrNull { it.accountId == account && it.zone == zone }?.id
        return TurnPlayerTargets(account, container(PlayZone.BATTLEFIELD) ?: return null,
            container(PlayZone.LIBRARY) ?: return null, container(PlayZone.HAND) ?: return null)
    }
    fun untap(play: PlayState, advance: Boolean): TableActionIntent.Untap? {
        val targets = targets(play) ?: return null
        val ids = play.board.cards.filter { it.containerId == targets.battlefieldId }
            .sortedWith(compareBy<PlayCard> { it.placement?.position?.y ?: 0 }
                .thenBy { it.placement?.position?.x ?: 0 }.thenBy { it.id }).map { it.id }
        return TableActionIntent.Untap(TableActionScope(play.gameId, play.id), targets, ids, advance)
    }
    fun draw(play: PlayState, advance: Boolean): TableActionIntent.Draw? {
        val targets = targets(play) ?: return null
        return TableActionIntent.Draw(TableActionScope(play.gameId, play.id), targets,
            play.board.containers.single { it.id == targets.libraryId }.cards.firstOrNull(), advance)
    }
    fun library(play: PlayState, tool: LibraryTool, targets: TurnPlayerTargets? = targets(play)): TableActionIntent.Library? {
        targets ?: return null
        val hand = play.board.containers.singleOrNull { it.id == targets.handId } ?: return null
        return TableActionIntent.Library(TableActionScope(play.gameId, play.id), targets, tool, hand.cards.toList(),
            if (tool == LibraryTool.MULLIGAN_MINUS_ONE) (hand.cards.size - 1).coerceAtLeast(0) else 7)
    }

    fun perform(intent: TableActionIntent): Boolean {
        val play = current(intent.scope) ?: return false
        fun send(actions: List<PlayAction>): Boolean {
            var sent = false
            // These are ordered ordinary constituents, not an atomic batch. Do not short circuit
            // after a no-op/rejection: the final relative Next step still has its own applicability.
            for (action in actions) if (current(intent.scope) != null) sent = submit(action) || sent
            return sent
        }
        when (intent) {
            is TableActionIntent.Bundle -> {
                var sent = false
                for (action in intent.actions) if (action.scope == intent.scope) sent = perform(action) || sent
                return sent
            }
            is TableActionIntent.SelectAttackers -> routes.selectAttackers(intent)
            is TableActionIntent.Next -> return submit(PlayAction.NextStep)
            is TableActionIntent.End -> return submit(PlayAction.EndTurn)
            is TableActionIntent.Choose -> return submit(PlayAction.ChooseStep(intent.position))
            is TableActionIntent.Orient -> return send(intent.cardIds.distinct().filter { id ->
                play.board.cards.any { card -> card.id == id && play.board.containers.none {
                    it.id == card.containerId && it.zone == PlayZone.STACK } }
            }.map { PlayAction.SetTapped(it, intent.tapped) })
            is TableActionIntent.Untap -> {
                if (play.board.containers.none { it.id == intent.targets.battlefieldId && it.zone == PlayZone.BATTLEFIELD &&
                        it.accountId == intent.targets.accountId }) return false
                return send(intent.cardIds.distinct().map { PlayAction.SetTapped(it, false) } +
                    if (intent.advance) listOf(PlayAction.NextStep) else emptyList())
            }
            is TableActionIntent.Draw -> {
                if (play.board.containers.none { it.id == intent.targets.libraryId && it.zone == PlayZone.LIBRARY &&
                        it.accountId == intent.targets.accountId } ||
                    play.board.containers.none { it.id == intent.targets.handId && it.zone == PlayZone.HAND &&
                        it.accountId == intent.targets.accountId }) return false
                return send(listOfNotNull(intent.cardId?.let { PlayAction.MoveCard(it,
                    MoveSource(intent.targets.libraryId, edge = MoveSourceEdge.TOP),
                    MoveDestination(intent.targets.handId, MoveInsertion.UNORDERED)) }) +
                    if (intent.advance) listOf(PlayAction.NextStep) else emptyList())
            }
            is TableActionIntent.Library -> routes.library(intent)
            is TableActionIntent.Combat -> routes.combat(intent)
            is TableActionIntent.Token -> routes.token(intent)
            is TableActionIntent.Ending -> routes.ending(intent)
            is TableActionIntent.Stack -> routes.stack(intent)
            is TableActionIntent.Player -> routes.player(intent)
        }
        return true
    }

    fun rollDie(scope: TableActionScope, sides: Int = 6): Boolean {
        if (current(scope) == null || sides <= 0) return false
        val value = sample(sides) // exactly once, only for a fresh connected click
        require(value in 0 until sides)
        return submit(PlayAction.RollDie(sides, value + 1))
    }
    fun flipCoin(scope: TableActionScope): Boolean {
        if (current(scope) == null) return false
        val value = sample(2)
        require(value in 0..1)
        return submit(PlayAction.FlipCoin(CoinFace.entries[value]))
    }
    fun adjust(target: PlayerValueTarget, delta: Long): Boolean {
        val player = current(target.scope)?.board?.players?.singleOrNull { it.accountId == target.accountId } ?: return false
        if (delta == 0L) return false
        val text = if (target.color == null) player.life else player.counters[target.color] ?: "0"
        if (!TableActions.numeric(text)) return false
        return submit(if (target.color == null) PlayAction.AdjustLife(target.accountId, delta.toString())
            else PlayAction.AdjustPlayerCounter(target.accountId, target.color, delta.toString()))
    }
}
