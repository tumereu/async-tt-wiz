@file:OptIn(arcanum.engine.state.ArcanumTestSetup::class)

package org.asyncttwiz.automatum

import arcanum.api.Affected
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.state.Bindings
import arcanum.engine.state.CounterEntry
import arcanum.engine.state.EffectId
import arcanum.engine.state.EffectObject
import arcanum.engine.state.GameState
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.setup
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TBL-104 (owner, 2026-11-07): Inspect says when each continuous effect ends in concrete words read from the effect's
 * own data (the player, step, source, card or condition it names), never "until a later step" or "for as long as its
 * condition holds".
 */
class DurationTextTest {
    init {
        Cards.invent("Ember Herald", "{1}{R}", "Creature — Human", "", "2/2")
        Cards.invent("Hill Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    }
    private val table = EngineTable()
    private val herald = table.put(table.alice, Zone.Battlefield, "Ember Herald").single()
    private val ogre = table.put(table.bob, Zone.Battlefield, "Hill Ogre").single()
    private val names = mapOf(table.alice to "Alice", table.bob to "Bob")

    /** The Inspect line of an effect giving [card] haste for [duration], made by Alice's Ember Herald, as [viewer] reads it. */
    private fun line(duration: Duration, card: String = ogre, viewer: String = table.alice, controller: String = table.alice,
        bound: Map<String, NamedValue> = emptyMap(), change: (GameState) -> Unit = {}): String {
        val state = table.state()
        change(state)
        val id = table.obj(card)
        state.setup.effects[EffectId("e1")] = EffectObject(EffectId("e1"), listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))),
            Affected.Objects(Objects.Bound("affected")), locked = listOf(id), source = table.obj(herald), controller = PlayerId(controller),
            timestamp = 100, createdTurn = state.turn.number, duration = duration, names = Bindings(bound))
        val view = Engine.view(state)
        val board = ComputedBoard.of(table.board(state, viewer), table.base().definitions, table.definitions, viewer, state, view)
        return board.effects(listOf(card), viewer, names).getValue(card).single().text
    }

    @Test fun untilEndOfTurnAndCombat() {
        assertEquals("Haste until end of turn", line(Duration.EndOfTurn))
        assertEquals("Haste until end of combat", line(Duration.EndOfCombat))
    }

    // CR 611.2b: "until your next turn" names the effect's controller
    @Test fun untilAPlayersNextTurnNamesThePlayer() {
        assertEquals("Haste until your next turn", line(Duration.UntilYourNextTurn))
        assertEquals("Haste until Alice's next turn", line(Duration.UntilYourNextTurn, viewer = table.bob))
        assertEquals("Haste until the end of your next turn", line(Duration.UntilEndOfYourNextTurn))
        assertEquals("Haste until the end of Bob's next turn", line(Duration.UntilEndOfYourNextTurn, controller = table.bob))
    }

    // CR 611.2c: "until the end of their next turn", for the player named as the effect was created
    @Test fun untilTheEndOfANamedPlayersNextTurn() {
        assertEquals("Haste until the end of Bob's next turn",
            line(Duration.UntilEndOfNextTurnOf(Players.OwnerOf(Objects.Bound("card"))), bound = mapOf("card" to NamedValue.Objects(listOf(table.obj(ogre))))))
        assertEquals("Haste until the end of your next turn", line(Duration.UntilEndOfNextTurnOf(Players.You)))
    }

    // CR 500.4: an "until" a step names the step and whose it is
    @Test fun untilAStepNamesTheStep() {
        assertEquals("Haste until your next end step", line(Duration.UntilStep(TurnStep.End, PlayerFilter.You)))
        assertEquals("Haste until Alice's next end step", line(Duration.UntilStep(TurnStep.End, PlayerFilter.You), viewer = table.bob))
        assertEquals("Haste until the next postcombat main phase", line(Duration.UntilStep(TurnStep.PostcombatMain, PlayerFilter.Any)))
        assertEquals("Haste until Bob's next upkeep", line(Duration.UntilStep(TurnStep.Upkeep, PlayerFilter.Opponent)))
        assertEquals("Haste until after Bob's next untap step",
            line(Duration.ThroughNextStep(TurnStep.Untap, Players.ControllerOf(Objects.Bound("tapped"))),
                bound = mapOf("tapped" to NamedValue.Objects(listOf(table.obj(ogre))))))
    }

    // CR 702.62a: a creature cast with suspend has haste until you lose control of it
    @Test fun asLongAsYouControlIt() {
        val played = mapOf("played" to NamedValue.Objects(listOf(table.obj(herald))))
        val suspend = Duration.AsLongAs(Condition.ObjectMatches(Objects.Bound("played"), ObjectFilter.ControlledBy(PlayerFilter.You)))
        assertEquals("Haste as long as you control it", line(suspend, card = herald, bound = played))
        assertEquals("Haste as long as Alice controls it", line(suspend, card = herald, bound = played, viewer = table.bob))
    }

    // CR 611.2b: "for as long as you control this Vehicle" names the source; counters and zones read as conditions
    @Test fun asLongAsNamesTheConditionsObject() {
        assertEquals("Haste as long as you control Ember Herald",
            line(Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, ObjectFilter.ControlledBy(PlayerFilter.You)))))
        assertEquals("Haste as long as it has a stun counter on it", line(
            Duration.AsLongAs(Condition.ObjectMatches(Objects.Bound("t"), ObjectFilter.HasCounter(CounterKind.Stun, atLeast = 1))),
            bound = mapOf("t" to NamedValue.Objects(listOf(table.obj(ogre))))) { s ->
            s.setup.objects[table.obj(ogre)] = s.objects.getValue(table.obj(ogre)).copy(counters = listOf(CounterEntry(CounterKind.Stun, 1, 1)))
        })
    }

    // a condition these words don't cover names whose condition it is
    @Test fun anUnknownConditionNamesItsSource() {
        assertEquals("Haste as long as Ember Herald's condition holds", line(Duration.AsLongAs(Condition.Not(Condition.IsNight))))
    }

    // CR 610.3: "until ~ leaves the battlefield" names the card
    @Test fun untilItsSourceLeavesNamesTheSource() {
        assertEquals("Haste until Ember Herald leaves the battlefield", line(Duration.UntilLeaves(Objects.Self)))
        assertEquals("Haste until it leaves the battlefield", line(Duration.UntilLeaves(Objects.Self), card = herald))
    }

    // CR 601.2i: "until this card is cast from exile" names the card
    @Test fun untilTheExiledCardIsCastNamesTheCard() {
        val exiled = table.put(table.alice, Zone.Exile, "Hill Ogre").single()
        assertEquals("Haste until Hill Ogre is cast", line(Duration.UntilCast(Objects.Bound("exiled")),
            bound = mapOf("exiled" to NamedValue.Objects(listOf(table.obj(exiled))))))
    }
}
