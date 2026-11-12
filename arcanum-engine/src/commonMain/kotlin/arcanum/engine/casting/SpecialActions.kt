package arcanum.engine.casting

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Timing
import arcanum.api.Zone
import arcanum.engine.foretell.Foretell
import arcanum.engine.room.Rooms
import arcanum.engine.state.GameObject
import arcanum.engine.state.ObjectId
import arcanum.engine.view.AbilityInstance
import arcanum.engine.view.AbilityOrigin
import arcanum.engine.view.KeywordMeanings

/**
 * The special actions the rules give an object rather than an ability it shows (CR 116.2), in one list: [Engine.options][arcanum.engine.Engine.options]
 * offers them beside the object's abilities and [Legality.instance] finds them again to take one. Whatever gives one, it is an
 * [Ability.SpecialAction] like those of a keyword (suspend, plot, companion, hidden agenda): legal by [Legality.cantActivate], paid as a cost, and
 * its steps run through the step runner without the Stack ([Payment]). A new one is a new entry here and nothing else.
 */
internal object SpecialActions {
    /** The special actions the rules give [id]. */
    fun of(snap: Snapshot, id: ObjectId): List<AbilityInstance> {
        val o = snap.state.obj(id) ?: return emptyList()
        return RULES.flatMap { it(snap, o) }
    }

    private val RULES: List<(Snapshot, GameObject) -> List<AbilityInstance>> = listOf(
        // CR 116.2m, 709.5e: unlocking a locked door of a Room
        { snap, o -> if (o.faceDown) emptyList() else Rooms.unlockActions(snap.view[o.id]) },
        // CR 116.2b, 701.40b, 701.58b: a manifested or cloaked creature card turns face up for its mana cost
        { _, o -> manifested(o) },
        // CR 116.2b, 702.37e, 702.168d: a face-down permanent's own card
        { _, o -> ofFaceDownCard(o) },
        // CR 116.2h, 702.143b: foretelling a card in a hand
        { snap, o -> Foretell.actions(snap, o.id) },
    )

    private fun manifested(o: GameObject): List<AbilityInstance> {
        if (!o.faceDown || (ObjectMark.Manifested !in o.marks && ObjectMark.Cloaked !in o.marks)) return emptyList()
        val face = o.definition.faces.firstOrNull() ?: return emptyList()
        val cost = face.manaCost?.takeIf { CardType.Creature in face.types } ?: return emptyList()
        return listOf(AbilityInstance(Ability.SpecialAction(Cost.mana(cost),
            Instructions(steps = listOf(Step.Do(Effect.TurnFaceUp(Objects.Self)))),
            timing = Timing.Any, functionsIn = listOf(Zone.Battlefield),
            condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(Status.FaceDown)),
            text = "Turn it face up for its mana cost"), AbilityOrigin.Rule("manifested")))
    }

    /** Its characteristics show no abilities (CR 708.2a), so they are read from its front face, keyword expansions included. */
    private fun ofFaceDownCard(o: GameObject): List<AbilityInstance> {
        if (!o.faceDown) return emptyList()
        val face = o.definition.faces.firstOrNull() ?: return emptyList()
        return face.abilities.flatMapIndexed { i, ability ->
            val printed = AbilityOrigin.Printed(0, i)
            when (ability) {
                is Ability.SpecialAction -> listOf(AbilityInstance(ability, printed))
                is Keyword -> KeywordMeanings.expand(ability).mapIndexedNotNull { j, a ->
                    (a as? Ability.SpecialAction)?.let { AbilityInstance(it, AbilityOrigin.Expansion(printed, j)) }
                }
                else -> emptyList()
            }
        }
    }
}
