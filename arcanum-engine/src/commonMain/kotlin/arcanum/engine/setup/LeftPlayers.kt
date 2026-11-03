package arcanum.engine.setup

import arcanum.engine.decision.Decision
import arcanum.engine.decision.DecisionId
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId

/**
 * Decisions of a player who left the game (CR 800.4f–h), as a flow asks them ([arcanum.engine.action.Act.ask]):
 * - a cost they would pay or choose whether to pay ([Decision.May] with a cost) isn't paid (800.4f); a payment they were making
 *   ([Decision.ManaPayment]) is never handed to another player (an assisting player's part becomes 0, the caster paying the rest);
 * - another choice an object asks of them is made by a player the object's controller chooses: another opponent of the
 *   controller when the one who left was one, if there is one, else any other player in the game (800.4g);
 * - a choice the rules ask of them (no source, or its controller left too) is made by the next player in turn order (800.4h).
 * Random results, host fills and other views stay the host's.
 */
internal object LeftPlayers {
    sealed interface Redirect {
        /** CR 800.4f: the cost isn't paid. */
        data object NotPaid : Redirect
        /** CR 800.4h: [player] makes the choice. */
        data class To(val player: PlayerId) : Redirect
        /** CR 800.4g: [chooser] chooses among [options] who makes it. */
        data class ChosenBy(val chooser: PlayerId, val options: List<PlayerId>) : Redirect
    }

    /** How [d] is decided when its player has left the game; null when they haven't (or the host answers it). */
    fun redirect(s: GameState, d: Decision): Redirect? {
        if (!s.player(d.player).lost) return null
        if (d is Decision.Random || d is Decision.HostFill || d is Decision.OtherView) return null
        // CR 800.4f: nor a payment they were making: it is never handed to another player (paid with the left player's objects)
        if (d is Decision.May && d.cost != null || d is Decision.ManaPayment) return Redirect.NotPaid
        val left = d.player
        val controller = d.source?.let { id -> s.obj(id)?.let { it.controller ?: it.owner } ?: s.stackItems[id]?.controller }
            ?.takeIf { !s.player(it).lost }
            ?: return Redirect.To(arcanum.engine.turn.Priority.nextPlayer(s, left))
        val inGame = s.players.filter { !it.lost }.map { it.id }
        val opponents = s.opponents(controller)
        val options = if (left != controller) inGame.filter { it in opponents }.ifEmpty { inGame } else inGame
        return if (options.size == 1) Redirect.To(options.single()) else Redirect.ChosenBy(controller, options)
    }

    /** [d] asked of [player] under [id]. */
    fun reassigned(d: Decision, id: DecisionId, player: PlayerId): Decision = when (d) {
        is Decision.Targets -> d.copy(id = id, player = player)
        is Decision.Modes -> d.copy(id = id, player = player)
        is Decision.Announce -> d.copy(id = id, player = player)
        is Decision.ManaPayment -> d.copy(id = id, player = player)
        is Decision.May -> d.copy(id = id, player = player)
        is Decision.ChooseObjects -> d.copy(id = id, player = player)
        is Decision.ChooseOption -> d.copy(id = id, player = player)
        is Decision.ChooseColor -> d.copy(id = id, player = player)
        is Decision.ChooseSubtype -> d.copy(id = id, player = player)
        is Decision.ChooseCardType -> d.copy(id = id, player = player)
        is Decision.ChooseCardName -> d.copy(id = id, player = player)
        is Decision.ChooseNumber -> d.copy(id = id, player = player)
        is Decision.ChoosePlayer -> d.copy(id = id, player = player)
        is Decision.Divide -> d.copy(id = id, player = player)
        is Decision.OrderTriggers -> d.copy(id = id, player = player)
        is Decision.ChooseReplacement -> d.copy(id = id, player = player)
        is Decision.LegendRule -> d.copy(id = id, player = player)
        is Decision.Random -> d.copy(id = id, player = player)
        is Decision.HostFill -> d.copy(id = id, player = player)
        is Decision.OtherView -> d.copy(id = id, player = player)
        is Decision.Manual -> d.copy(id = id, player = player)
    }
}
