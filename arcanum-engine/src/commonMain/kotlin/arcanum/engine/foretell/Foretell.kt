package arcanum.engine.foretell

import arcanum.api.Ability
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.ObjectMark
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Timing
import arcanum.api.Zone
import arcanum.engine.action.Act
import arcanum.engine.action.Actions
import arcanum.engine.action.Cause
import arcanum.engine.action.PlannedMove
import arcanum.engine.change.Meaning
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.view.AbilityInstance
import arcanum.engine.view.AbilityOrigin
import arcanum.engine.view.AppliedMechanic
import arcanum.engine.view.View

/**
 * Foretell (CR 702.143), engine code rather than abilities so the card stays hidden: any time its owner has priority
 * during their turn they may pay {2} and exile a card with foretell from their hand face down (a special action, 702.143b);
 * they may look at it while it stays in exile, and after that turn has ended cast it for its foretell cost rather than its
 * mana cost (702.143a). The special action is [AbilityOrigin.Rule] [RULE], so a viewer's state that doesn't know the card
 * (D60) takes it without the card being revealed (the host forwards it from the owner's own state); the foretold card is
 * [ObjectMark.Foretold] with the turn in [arcanum.engine.state.ObjectFacts.foretoldTurn].
 */
internal object Foretell {
    /** The special action's origin. */
    val RULE: AbilityOrigin.Rule = AbilityOrigin.Rule("foretell")
    /** The origin of the permission to cast a foretold card. */
    val CAST_RULE: AbilityOrigin.Rule = AbilityOrigin.Rule("foretold")

    private val ACTION = Ability.SpecialAction(Cost.mana(ManaCost.parse("{2}")!!), Instructions(steps = emptyList()), timing = Timing.Any,
        functionsIn = listOf(Zone.Hand), condition = Condition.YourTurn, text = "Foretell: pay {2} and exile this card face down")

    /** Whether [origin] is the foretell special action. */
    fun isForetell(origin: AbilityOrigin?): Boolean = origin == RULE

    /** The foretell costs of [o]'s card (its printed foretell abilities, read through its definition as the card is hidden). */
    fun costs(o: GameObject): List<Cost> = o.definition?.faces?.firstOrNull()?.abilities.orEmpty().filterIsInstance<Keyword.Foretell>().map { it.cost }

    /**
     * The foretell special action of [id] in a hand: offered when the card has foretell where it is ([view]); for a card the
     * state doesn't know (another player's, D60) it is accepted as the host forwards it ([forwarded]).
     */
    fun actions(state: GameState, view: View, id: ObjectId, forwarded: Boolean = false): List<AbilityInstance> {
        val o = state.obj(id) ?: return emptyList()
        if (o.zone != Zone.Hand) return emptyList()
        val has = if (o.unknown) forwarded else view[id]?.characteristics?.abilities.orEmpty().any { it.ability is Keyword.Foretell }
        return if (has) listOf(AbilityInstance(ACTION, RULE)) else emptyList()
    }

    /** CR 702.143a: [player] exiles [id] from their hand face down; it becomes foretold this turn. Returns the exiled card. */
    fun exile(act: Act, cause: Cause, id: ObjectId): ObjectId? {
        val moved = Actions.move(act, cause, listOf(PlannedMove(id, Zone.Exile, faceDown = true, meanings = listOf(Meaning.Exiled),
            marks = listOf(ObjectMark.Foretold))))
        return moved[id]
    }

    /** Whether [o] is a foretold card in exile, cast-able by its owner once the turn it was foretold has ended. */
    fun castable(state: GameState, o: GameObject): Boolean =
        o.zone == Zone.Exile && ObjectMark.Foretold in o.marks && (o.facts.foretoldTurn ?: Int.MAX_VALUE) < state.turn.number

    /** Whether [player] may look at [o]: its owner, while it is a foretold card in exile (CR 702.143a). */
    fun mayLook(o: GameObject, player: PlayerId): Boolean = o.zone == Zone.Exile && ObjectMark.Foretold in o.marks && o.owner == player

    /** The permissions to cast foretold cards for their foretell costs (CR 702.143a), their owners'. */
    fun permissions(state: GameState): Map<ObjectId, List<AppliedMechanic>> = state.exile.mapNotNull { id ->
        val o = state.obj(id) ?: return@mapNotNull null
        if (!castable(state, o)) return@mapNotNull null
        val costs = costs(o).ifEmpty { return@mapNotNull null }
        id to costs.map { cost -> AppliedMechanic(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, cost = cost, holder = PlayerFilter.You), o.owner,
            o.timestamp, source = id, origin = CAST_RULE) }
    }.toMap()
}
