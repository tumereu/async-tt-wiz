package arcanum.engine.combat

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Trigger
import arcanum.api.Zone
import arcanum.engine.action.Act
import arcanum.engine.change.Change
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import arcanum.engine.view.AbilityInstance
import arcanum.engine.view.AbilityOrigin
import arcanum.engine.view.View

/**
 * Battles (CR 310): each has a protector ([arcanum.engine.state.GameObject.protector]) chosen as it enters by its controller
 * (310.9a: a Siege's among their opponents, 310.12a; one with no battle type is its controller's) and again by the state-based
 * actions when it has none or one who can't be (704.5x, y; none possible: its owner's graveyard). It can be attacked by an
 * attacking player its protector is defending for, never by its protector (310.9b); only its protector blocks creatures
 * attacking it and is their defending player (310.9c, d). A Siege has the intrinsic trigger of 310.12b ([SIEGE_RULE]).
 */
internal object Battles {
    val SIEGE = Subtype("Siege")

    /** The intrinsic ability of a Siege (CR 310.12b): "When the last defense counter is removed from this permanent, exile it,
     * then you may cast it transformed without paying its mana cost." */
    val SIEGE_RULE: AbilityOrigin.Rule = AbilityOrigin.Rule("siege")
    private const val EXILED = "\$siege-exiled"
    val siegeAbility: AbilityInstance = AbilityInstance(
        Ability.Triggered(
            Trigger.On(listOf(EventPattern.CountersRemoved(ObjectFilter.Is(Objects.Self), CounterKind.Defense, last = true))),
            Instructions(steps = listOf(
                Step.Do(Effect.Move(Objects.Self, Destination(Zone.Exile), postBind = EXILED)),
                Step.May(listOf(Step.Do(Effect.Cast(Objects.Bound(EXILED), PlayKind.Cast, withoutPaying = true, caster = Players.You,
                    transformed = true))), Players.You),
            )),
            text = "When the last defense counter is removed from this permanent, exile it, then you may cast it transformed without paying its mana cost.",
        ),
        SIEGE_RULE,
    )

    /** The players who may be the protector of a battle [controller] controls with [subtypes] (CR 310.9a, 310.12a). */
    fun choices(s: GameState, controller: PlayerId, subtypes: List<Subtype>): List<PlayerId> = when {
        SIEGE in subtypes -> s.opponents(controller)
        else -> listOf(controller).filter { !s.player(it).lost }
    }

    /** The player defending against an attack on [target] (CR 506.2, 310.9d): a battle's protector, a planeswalker's controller. */
    fun defender(s: GameState, controllerOf: (ObjectId) -> PlayerId?, target: TargetRef): PlayerId? = when (target) {
        is TargetRef.Player -> target.id
        is TargetRef.Object -> s.obj(target.id)?.protector ?: controllerOf(target.id)
    }

    /** CR 310.9a, 310.12a: as a battle enters under [controller] with [subtypes], its protector: asked when there is a choice;
     * null when nobody can be (the state-based actions then put it into the graveyard, 704.5x). */
    fun protectorAsEnters(act: Act, id: ObjectId, controller: PlayerId, subtypes: List<Subtype>): PlayerId? {
        val options = choices(act.state, controller, subtypes)
        return if (options.size <= 1) options.firstOrNull() else act.choosePlayer(controller, id, options)
    }

    /**
     * CR 704.5x, y: a battle on the battlefield without a protector in the game (not while creatures attack it) or with one who
     * can't be its protector gets one its controller chooses; returns those for which nobody can be chosen (to the graveyard).
     */
    fun checkProtectors(act: Act, view: View, battlefield: List<ObjectId>): List<ObjectId> {
        val s = act.state
        val attacked = s.combat?.attackers.orEmpty().mapNotNull { (it.target as? TargetRef.Object)?.id }.toSet()
        val gone = mutableListOf<ObjectId>()
        battlefield.forEach { id ->
            val c = view[id]?.characteristics ?: return@forEach
            if (!c.isType(CardType.Battle)) return@forEach
            val o = s.objOrThrow(id)
            val controller = view[id]?.controller ?: o.owner
            val options = choices(s, controller, c.subtypes)
            val current = o.protector?.takeIf { !s.player(it).lost }
            val ok = current != null && current in options
            if (ok || (current == null && id in attacked)) return@forEach
            val chosen = if (options.size <= 1) options.firstOrNull() else act.choosePlayer(controller, id, options)
            if (chosen == null) gone += id
            else act.record(Change.ObjectUpdated(o, o.copy(protector = chosen)))
        }
        return gone
    }

    /** CR 704.5v: a Siege with defense 0 waits while an ability it is the source of has triggered and not yet left the Stack. */
    fun siegeWaits(s: GameState, view: View, id: ObjectId): Boolean =
        view[id]?.characteristics?.subtypes?.contains(SIEGE) == true &&
            (s.triggered.any { it.source == id } || s.stackItems.values.any { it.source == id && it.ability is Ability.Triggered })
}
