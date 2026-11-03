package arcanum.engine.change

import arcanum.api.Affected
import arcanum.api.CounterKind
import arcanum.api.Designation
import arcanum.api.Duration
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Mechanic
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.Pending
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.decision.DecisionId
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.Attacker
import arcanum.engine.state.CombatState
import arcanum.engine.state.ControlSince
import arcanum.engine.state.DelayedTrigger
import arcanum.engine.state.EffectId
import arcanum.engine.state.EffectObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ManaUnit
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PriorityState
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.StatusFlag
import arcanum.engine.state.TargetRef
import arcanum.engine.state.TokenIds

/**
 * One sample of every [Change] kind, each made for the state it applies to. Built on a small game: alice controls
 * Lineage Druid and a Forest, has Storm Spark in hand and two cards in library; bob controls Umbral Elves.
 */
internal object ChangeSamples {
    /** A base game and, by change kind, the state it applies to and the change. */
    fun all(): Pair<GameState, List<Pair<GameState, Change>>> {
        lateinit var base: GameState
        lateinit var s: Scenario
        scenario {
            alice.battlefield("Lineage Druid", "Forest")
            alice.hand("Storm Spark")
            alice.library("Elvish Seer", "Thicketwood Elves")
            bob.battlefield("Umbral Elves")
            alice.counters(CounterKind.Poison, 1)
            base = state.deepCopy()
            s = this
        }
        val druid = s.card("Lineage Druid")
        val forest = s.card("Forest")
        val spark = s.card("Storm Spark")
        val elves = s.card("Umbral Elves")
        val alice = s.alice.id
        val bob = s.bob.id
        val next = base.nextTimestamp()

        val ability = StackItem(
            id = ObjectId.StackItem("S$next"), kind = StackItemKind.Triggered, controller = alice, source = druid,
            ability = null, timestamp = next,
        )
        val effect = EffectObject(
            id = EffectId("E$next"), mechanics = listOf(Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(1))),
            affects = Affected.Objects(Objects.Target("creature")), locked = listOf(elves), source = druid, controller = alice,
            timestamp = next, createdTurn = 1, duration = Duration.EndOfTurn,
        )
        val delayed = DelayedTrigger(
            id = EffectId("D$next"), trigger = Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
            instructions = Instructions(), source = druid, controller = alice, timestamp = next, createdTurn = 1,
        )
        val token = arcanum.engine.state.GameObject(
            id = ObjectId.Card(TokenIds.token("S1", 0), 0), owner = alice, controller = alice, zone = Zone.Battlefield,
            definition = Cards.definition("Forest"), token = true, timestamp = next,
        )
        val pending = Pending(
            Decision.Manual(DecisionId("d1"), alice, druid, "Do it by hand"),
            Continuation.Resolving(item = ability.id, step = 0),
        )
        fun on(state: GameState, change: Change) = state to change
        fun after(vararg changes: Change): GameState = base.deepCopy().also { s2 -> changes.forEach { s2.apply(it) } }

        val withAbility = after(Change.StackItemAdded(ability, 0))
        val withEffect = after(Change.EffectAdded(effect))
        val withDelayed = after(Change.DelayedTriggerAdded(delayed))
        val druidObject = base.objOrThrow(druid)
        val library = base.player(alice).library.map { base.objOrThrow(it) }
        val unknown = base.objOrThrow(spark).copy(definition = null)
        val withUnknown = base.deepCopy().also { it.objects[spark] = unknown }

        val samples = listOf(
            on(base, Changes.move(base, listOf(druid, forest), Zone.Graveyard)),
            on(base, Change.Created(listOf(Placed(token, base.battlefield.size)))),
            on(base, Change.Removed(listOf(Placed(druidObject, base.battlefield.indexOf(druid))))),
            on(base, Changes.tap(base, forest)),
            on(base, Change.Status(elves, StatusFlag.Marked(ObjectMark.Monstrous), false, true)),
            on(base, Change.Face(druid, 0, 1, listOf(Meaning.Transformed))),
            on(base, Changes.counters(base, TargetRef.Object(druid), CounterKind.PowerToughness(1, 1), 2)),
            on(base, Changes.counters(base, TargetRef.Player(alice), CounterKind.Poison, 2)),
            on(base, Change.Damage(listOf(DamageDealt(druid, TargetRef.Object(elves), 2)))),
            on(base, Changes.damageMarked(base, elves, 2, deathtouch = true)),
            on(base, Changes.life(base, bob, -3, listOf(Meaning.LifeLost))),
            on(base, Changes.manaPool(base, alice, listOf(ManaUnit(arcanum.api.Color.Green, source = forest)), listOf(Meaning.ManaAdded))),
            on(base, Change.StackItemAdded(ability, 0)),
            on(withAbility, Change.StackItemRemoved(ability, 0, listOf(Meaning.Resolved))),
            on(withAbility, Change.StackItemUpdated(ability, ability.copy(targets = mapOf("t" to listOf(TargetRef.Player(bob)))))),
            on(base, Change.EffectAdded(effect)),
            on(withEffect, Change.EffectRemoved(effect)),
            on(withEffect, Change.EffectUpdated(effect, effect.copy(usesLeft = 1))),
            on(base, Change.DelayedTriggerAdded(delayed)),
            on(withDelayed, Change.DelayedTriggerRemoved(delayed)),
            on(base, Change.Attached(forest, null, TargetRef.Object(druid), base.objOrThrow(forest).timestamp, next)),
            on(base, Change.Restamped(druid, druidObject.timestamp, next)),
            on(base, Change.Memory(druid, "chosen", null, NamedValue.Colors(listOf(arcanum.api.Color.Green)))),
            on(base, Change.Facts(druid, druidObject.facts, druidObject.facts.copy(x = 3))),
            on(base, Change.ControlStart(druid, druidObject.controlSince, ControlSince(bob, 1))),
            on(base, Change.ObjectUpdated(druidObject, druidObject.copy(goadedBy = listOf(bob)))),
            on(base, Changes.step(base, base.turn.copy(step = TurnStep.BeginningOfCombat), listOf(Meaning.StepBegan))),
            on(base, Changes.priority(base, PriorityState(bob, 1), listOf(Meaning.PriorityPassed))),
            on(base, Change.Combat(null, CombatState(listOf(Attacker(druid, TargetRef.Player(bob)))), listOf(Meaning.AttackersDeclared))),
            on(base, Change.Ledger(null, "storm", 0, 2)),
            on(base, Change.Ledger(alice, "expend", 0, 4)),
            on(base, Change.Lost(bob, false, true)),
            on(base, Change.PlayerDesignation(alice, Designation.Monarch, false, true)),
            on(base, Change.SkipWhenNoChoice(bob, true, false)),
            on(base, Change.Shuffle(alice, library, library.reversed().map { it.copy(id = it.id.next()) })),
            on(base, Change.Reorder(Zone.Library, alice, base.player(alice).library, base.player(alice).library.reversed())),
            on(base, Change.LibraryPlaces(alice, emptyMap(), mapOf(base.player(alice).library.first() to 0))),
            on(base, Change.Shown(alice, base.player(alice).library)),
            on(withUnknown, Change.Filled(unknown, base.objOrThrow(spark))),
            on(base, Change.Forgotten(base.objOrThrow(spark), unknown)),
            on(base, Change.Happened(GameEvent.CoinFlipped(alice, won = true))),
            on(base, Changes.pending(base, pending)),
            on(base, Change.TriggersWaiting(emptyList(), listOf(arcanum.engine.decision.WaitingTrigger(alice,
                arcanum.api.Ability.Unparsed("When this enters, draw a card."), origin = null, source = druid)))),
            on(after(Changes.tap(base, forest)), Change.Reverted(Changes.tap(base, forest))),
        )
        return base to samples
    }
}
