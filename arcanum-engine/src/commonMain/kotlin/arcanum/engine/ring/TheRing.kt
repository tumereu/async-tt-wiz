package arcanum.engine.ring

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.action.Act
import arcanum.engine.action.Actions
import arcanum.engine.action.Cause
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.change.Placed
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StatusFlag
import arcanum.engine.state.TokenIds
import arcanum.engine.view.View

/**
 * The Ring tempts you (CR 701.54): the player gets the emblem named The Ring if they have none ([EMBLEM], 701.54c), the number
 * of times the Ring tempted them goes up (the player's ledger under [KEY]), they choose a creature they control as their
 * Ring-bearer ([ObjectMark.RingBearer], the chooser kept in the permanent's memory under [BEARER_OF]) and the Ring has tempted
 * them ([GameEvent.RingTempted], 701.54d), even when they control no creature. The emblem's abilities that need two, three or
 * four temptations trigger only once the Ring has tempted its owner that often.
 */
internal object TheRing {
    /** The player-ledger key counting the Ring's temptations (CR 701.54c). */
    const val KEY: String = "ring-tempted"
    /** The memory key of a Ring-bearer naming the player whose Ring-bearer it is. */
    const val BEARER_OF: String = "\$ring-bearer-of"
    const val EMBLEM: String = "The Ring"

    fun temptations(state: GameState, player: PlayerId): Int = state.player(player).ledger[KEY] ?: 0

    /** The player whose Ring-bearer [o] is, or null. */
    fun bearerOf(o: GameObject): PlayerId? =
        if (ObjectMark.RingBearer !in o.marks) null else (o.memory[BEARER_OF] as? NamedValue.Players)?.ids?.firstOrNull()

    /** CR 701.54a, c, d: the Ring tempts [player]. */
    fun tempt(act: Act, cause: Cause?, player: PlayerId) {
        if (act.state.command.none { id -> act.state.obj(id)?.let { it.owner == player && it.definition?.faces?.firstOrNull()?.name == EMBLEM } == true })
            createEmblem(act, player)
        val before = temptations(act.state, player)
        act.record(Change.Ledger(player, KEY, before, before + 1))
        val view = act.view()
        val creatures = act.state.battlefield.filter { act.controllerOf(it) == player && view[it]?.characteristics?.isType(CardType.Creature) == true }
        val chosen = act.chooseObjects(player, cause?.source, creatures, 1, 1, "Choose your Ring-bearer").firstOrNull()
        if (chosen != null) {
            // "until another creature becomes your Ring-bearer"
            act.state.battlefield.filter { it != chosen && act.state.obj(it)?.let(::bearerOf) == player }.forEach { clear(act, it) }
            val o = act.state.objOrThrow(chosen)
            if (bearerOf(o) != player) {
                if (o.memory[BEARER_OF] != null) act.record(Change.Memory(chosen, BEARER_OF, o.memory[BEARER_OF], null))
                act.record(Change.Memory(chosen, BEARER_OF, null, NamedValue.Players(listOf(player))))
                if (ObjectMark.RingBearer !in o.marks) Actions.tapStatus(act, chosen, StatusFlag.Marked(ObjectMark.RingBearer), true)
            }
        }
        act.record(Change.Happened(GameEvent.RingTempted(player, chosen)))
    }

    /** CR 701.54a: a Ring-bearer another player gained control of isn't one any more. Checked with the state-based actions. */
    fun check(act: Act, view: View) {
        act.state.battlefield.forEach { id ->
            val o = act.state.obj(id) ?: return@forEach
            val of = bearerOf(o) ?: return@forEach
            if (view[id]?.controller != of) clear(act, id)
        }
    }

    private fun clear(act: Act, id: ObjectId) {
        val o = act.state.objOrThrow(id)
        if (ObjectMark.RingBearer in o.marks) Actions.tapStatus(act, id, StatusFlag.Marked(ObjectMark.RingBearer), false)
        o.memory[BEARER_OF]?.let { act.record(Change.Memory(id, BEARER_OF, it, null)) }
    }

    private fun createEmblem(act: Act, player: PlayerId) {
        val ts = act.state.nextTimestamp()
        val o = GameObject(
            // D35, D60: named from the timestamp, the same in every view (not the cause's key: a spell's id differs between views)
            id = ObjectId.Card(TokenIds.token(TokenIds.created("the-ring", ts) + "/$player", 0), 0), owner = player, controller = player,
            zone = Zone.Command, definition = CardDefinition(Layout.Normal, listOf(FaceDefinition(EMBLEM, abilities = abilities))),
            token = false, timestamp = ts,
        )
        act.record(Change.Created(listOf(Placed(o, act.state.command.size)), emptyList()))
    }

    /** "Your Ring-bearer" (CR 701.54e): a creature you control that is your Ring-bearer. */
    val yourRingBearer: ObjectFilter = ObjectFilter.And(listOf(ObjectFilter.HasStatus(Status.Marked(ObjectMark.RingBearer)),
        ObjectFilter.ControlledBy(PlayerFilter.You)))

    private fun temptedAtLeast(n: Int) = Condition.Compare(Value.RingTemptations(Players.You), Comparison.AtLeast, Value.Fixed(n))

    /** The Ring's abilities (CR 701.54c). */
    val abilities: List<Ability> = listOf(
        Ability.Static(listOf(Mechanic.SetTypes(addSupertypes = listOf(Supertype.Legendary)),
            Mechanic.CantBeBlockedBy(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Creature),
                ObjectFilter.Power(Comparison.Greater, Value.Of(NumericProperty.Power, Objects.Affected, Combine.Single)))))),
            affects = Affected.Objects(Objects.Each(yourRingBearer)),
            text = "Your Ring-bearer is legendary and can't be blocked by creatures with greater power."),
        Ability.Triggered(Trigger.On(EventPattern.Attack(yourRingBearer)), Instructions(steps = listOf(
            Step.Do(Effect.Draw(Players.You, Value.Fixed(1))),
            Step.Do(Effect.Discard(Players.You, Value.Fixed(1), ObjectFilter.Any)))),
            triggersOnlyIf = temptedAtLeast(2), text = "Whenever your Ring-bearer attacks, draw a card, then discard a card."),
        Ability.Triggered(Trigger.On(EventPattern.BecomesBlocked(yourRingBearer, ObjectFilter.Type(CardType.Creature), perBlocker = true)),
            Instructions(steps = listOf(
                Step.Bind(Objects.EventObject(EventRole.OtherCombatant), "blocker"),
                Step.Do(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.EndOfCombat, PlayerFilter.Any)),
                    Instructions(steps = listOf(Step.Do(Effect.Sacrifice(Objects.Bound("blocker"))))))))),
            triggersOnlyIf = temptedAtLeast(3),
            text = "Whenever your Ring-bearer becomes blocked by a creature, the blocking creature's controller sacrifices it at end of combat."),
        Ability.Triggered(Trigger.On(EventPattern.Damage(yourRingBearer, RecipientFilter(players = PlayerFilter.Any),
            combat = true)), Instructions(steps = listOf(Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), Value.Fixed(3))))),
            triggersOnlyIf = temptedAtLeast(4), text = "Whenever your Ring-bearer deals combat damage to a player, each opponent loses 3 life."),
    )
}
