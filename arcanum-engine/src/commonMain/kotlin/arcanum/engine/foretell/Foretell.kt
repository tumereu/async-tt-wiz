package arcanum.engine.foretell

import arcanum.api.Ability
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.KeywordActionKind
import arcanum.api.ManaCost
import arcanum.api.ObjectMark
import arcanum.api.Step
import arcanum.api.Timing
import arcanum.api.Zone
import arcanum.engine.action.Act
import arcanum.engine.action.Actions
import arcanum.engine.action.Cause
import arcanum.engine.action.PlannedMove
import arcanum.engine.casting.Legality
import arcanum.engine.casting.Snapshot
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.change.Meaning
import arcanum.engine.state.GameObject
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.view.AbilityInstance
import arcanum.engine.view.AbilityOrigin

/**
 * Foretell (CR 702.143), engine code rather than abilities so the card stays hidden: any time its owner has priority
 * during their turn they may pay {2} and exile a card with foretell from their hand face down (a special action, 702.143b);
 * they may look at it while it stays in exile, and after that turn has ended cast it for any foretell cost it has rather than
 * its mana cost (702.143a). The special action is [AbilityOrigin.Rule] [RULE], so it is taken without the card being revealed;
 * the foretold card is
 * [ObjectMark.Foretold] with the turn and the costs it had in [arcanum.engine.state.ObjectStatus.MarksGained] (CR 702.143e), and
 * [MarkCasts] gives the permission.
 *
 * {2} and "during their turn" are the rule; effects change them the way they change activations, naming foretelling with
 * `ObjectFilter.AbilityOf(keyword = Keyword.Foretell.Kind)`: `Mechanic.CostChange` ("costs {1} less to foretell"), a free
 * `Mechanic.AbilityAlternativeCost` ("the first card you foretell each turn costs {0}"), and `Mechanic.ActivateAtInstantSpeed`
 * ("any time you have priority").
 */
internal object Foretell {
    /** The special action's origin. */
    val RULE: AbilityOrigin.Rule = AbilityOrigin.Rule("foretell")

    private val ACTION = Ability.SpecialAction(Cost.mana(ManaCost.parse("{2}")!!),
        Instructions(steps = listOf(Step.Do(Effect.Action(KeywordAction.Foretell)))), timing = Timing.Any,
        functionsIn = listOf(Zone.Hand), condition = Condition.YourTurn, text = "Foretell: pay {2} and exile this card face down")

    /** Whether [origin] is the foretell special action. */
    fun isForetell(origin: AbilityOrigin?): Boolean = origin == RULE

    /**
     * The foretell special action of [id] in a hand: offered when the card has foretell where it is. Not held to its owner's turn
     * when an effect lets them foretell any time.
     */
    fun actions(snap: Snapshot, id: ObjectId): List<AbilityInstance> {
        val o = snap.state.obj(id) ?: return emptyList()
        if (o.zone != Zone.Hand) return emptyList()
        val has = snap.view[id]?.characteristics?.abilities.orEmpty().any { it.ability is Keyword.Foretell }
        if (!has) return emptyList()
        val anyTime = Legality.activatesAtInstantSpeed(snap, o.owner, id, AbilityInstance(ACTION, RULE))
        return listOf(AbilityInstance(if (anyTime) ACTION.copy(condition = null) else ACTION, RULE))
    }

    /** CR 702.143a: [player] exiles [id] from their hand face down; it becomes foretold this turn. Returns the exiled card. */
    fun exile(act: Act, cause: Cause, id: ObjectId): ObjectId? {
        val moved = Actions.move(act, cause, listOf(PlannedMove(id, Zone.Exile, faceDown = true, meanings = listOf(Meaning.Exiled),
            marks = listOf(ObjectMark.Foretold))))
        // CR 702.143c: foretelling a card is this special action
        moved[id]?.let { act.record(Change.Happened(GameEvent.KeywordActionDone(KeywordActionKind.Foretell, cause.controller, listOf(it)))) }
        return moved[id]
    }

    /** Whether [player] may look at [o]: its owner, while it is a foretold card in exile (CR 702.143a). */
    fun mayLook(o: GameObject, player: PlayerId): Boolean = o.zone == Zone.Exile && ObjectMark.Foretold in o.marks && o.owner == player
}
