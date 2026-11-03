package arcanum.engine.foretell

import arcanum.api.Mechanic
import arcanum.api.ObjectMark
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Zone
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.view.AbilityOrigin
import arcanum.engine.view.AppliedMechanic

/**
 * Plotted cards (CR 702.170): a card exiled by its plot special action (the keyword's expansion) or made plotted by an effect is
 * [ObjectMark.Plotted], with the turn in [arcanum.engine.state.ObjectFacts.plottedTurn]. Its owner may cast it from exile without
 * paying its mana cost during their main phase while the Stack is empty on any later turn (702.170d), even an instant, and even
 * if it no longer has plot.
 */
internal object Plot {
    /** The origin of the permission to cast a plotted card. */
    val CAST_RULE: AbilityOrigin.Rule = AbilityOrigin.Rule("plotted")

    private val CAST = Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, withoutPaying = true, holder = PlayerFilter.You)

    /** Whether [o] is a plotted card its owner may cast now as far as the turn goes (a turn after it became plotted). */
    fun castable(state: GameState, o: GameObject): Boolean =
        o.zone == Zone.Exile && ObjectMark.Plotted in o.marks && (o.facts.plottedTurn ?: Int.MAX_VALUE) < state.turn.number

    /** Whether [origin] is the permission of a plotted card (cast only as a sorcery, CR 702.170d). */
    fun isPlotted(origin: AbilityOrigin?): Boolean = origin == CAST_RULE

    /** The permissions to cast plotted cards, their owners'. */
    fun permissions(state: GameState): Map<ObjectId, AppliedMechanic> = state.exile.mapNotNull { id ->
        val o = state.obj(id) ?: return@mapNotNull null
        if (!castable(state, o)) return@mapNotNull null
        id to AppliedMechanic(CAST, o.owner, o.timestamp, source = id, origin = CAST_RULE)
    }.toMap()
}
