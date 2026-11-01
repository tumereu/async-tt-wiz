package arcanum.engine.query

import arcanum.api.CardType
import arcanum.api.Condition
import arcanum.api.EventPattern
import arcanum.api.Measure
import arcanum.api.ObjectFilter
import arcanum.api.PlayerFilter
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.change.Change
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.Attacker
import arcanum.engine.state.CombatState
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "This turn" questions over the turn's tagged events (D33): revolt, counts, "if you attacked this turn". */
class HistoryTest {
    private fun Scenario.evaluator(you: String = "alice"): Evaluator =
        Evaluator(ViewBoard(state, view()), Context(you = PlayerId(you)))

    private val creatureDied = EventPattern.ZoneChange(ObjectFilter.Type(CardType.Creature), from = Zone.Battlefield, to = Zone.Graveyard)

    // CR 700.4, D33: one board wipe is one event and two objects
    @Test
    fun eventsAndObjects() = scenario {
        bob.battlefield("Umbral Elves", "Elvish Seer")
        alice.hand("Blazing Confluence")

        alice.castsWithoutCosts("Blazing Confluence", modes = listOf(0, 1, 1))
        resolve()

        val e = evaluator()
        assertEquals(1, e.value(Value.EventsThisTurn(creatureDied, Measure.Events)))
        assertEquals(2, e.value(Value.EventsThisTurn(creatureDied, Measure.Objects)))
        expect { bob.life shouldBe 16 }
    }

    // CR 119.3: life gained this turn, by amount
    @Test
    fun lifeGainedThisTurn() = scenario {
        alice.hand("Tendrils of Torment")

        alice.castsWithoutCosts("Tendrils of Torment", target = "bob")
        resolveStack()

        val e = evaluator()
        assertTrue(e.holds(Condition.HappenedThisTurn(EventPattern.GainLife(PlayerFilter.You), 2, Measure.Amount)))
        assertFalse(e.holds(Condition.HappenedThisTurn(EventPattern.GainLife(PlayerFilter.You), 3, Measure.Amount)))
    }

    // Revolt: a permanent left the battlefield under your control this turn: the bigger threshold applies
    @Test
    fun revoltOn() = scenario {
        alice.battlefield("Elvish Seer")
        bob.battlefield("Elvish Hierophant")
        alice.hand("Deadly Shove")
        move("Elvish Seer", to = Zone.Hand)

        alice.castsWithoutCosts("Deadly Shove", target = "Elvish Hierophant")
        resolve()

        expect { bob.graveyard has "Elvish Hierophant" }
    }

    // Without revolt only mana value 2 or less
    @Test
    fun revoltOff() = scenario {
        bob.battlefield("Elvish Hierophant")
        alice.hand("Deadly Shove")

        alice.castsWithoutCosts("Deadly Shove", target = "Elvish Hierophant")
        resolve()

        expect { bob.battlefield has "Elvish Hierophant" }
    }

    // D33: last turn's events don't count this turn
    @Test
    fun lastTurnDoesntCount() = scenario {
        alice.battlefield("Elvish Seer")
        bob.battlefield("Elvish Hierophant")
        alice.hand("Deadly Shove")
        move("Elvish Seer", to = Zone.Hand)
        beginStep(arcanum.api.TurnStep.PrecombatMain, active = bob)
        beginStep(arcanum.api.TurnStep.PrecombatMain, active = alice)

        alice.castsWithoutCosts("Deadly Shove", target = "Elvish Hierophant")
        resolve()

        expect { bob.battlefield has "Elvish Hierophant" }
    }

    // "Draw X cards, where X is the number of creatures that died under your control this turn"
    @Test
    fun countCreaturesThatDied() = scenario {
        alice.battlefield("Elvish Seer", "Umbral Elves")
        alice.hand("Blazing Confluence", "Gravecaller Banneret")
        alice.library("Forest", "Island", "Swamp")

        alice.castsWithoutCosts("Blazing Confluence", modes = listOf(0, 1, 1))
        resolve()
        alice.castsWithoutCosts("Gravecaller Banneret")
        resolve()
        resolve()

        expect { alice.hand.size shouldBe 2 }
    }

    // "Then discard a card unless you attacked this turn": an attack this turn (tagged by hand, D42)
    @Test
    fun attackedThisTurn() = scenario {
        alice.battlefield("Elvish Seer")
        alice.hand("Plot the Voyage")
        alice.library("Forest", "Island")
        change(Change.Combat(null, CombatState(listOf(Attacker(card("Elvish Seer"), TargetRef.Player(bob.id)))),
            listOf(arcanum.engine.change.Meaning.AttackersDeclared)))

        alice.castsWithoutCosts("Plot the Voyage")
        resolve()

        expect { alice.hand.size shouldBe 2 }
    }

    // Without an attack, a discard
    @Test
    fun notAttackedThisTurn() = scenario {
        alice.hand("Plot the Voyage")
        alice.library("Forest", "Island")

        alice.castsWithoutCosts("Plot the Voyage")
        resolve()
        alice.chooses("Island")

        expect {
            alice.hand.names shouldBe listOf("Forest")
            alice.graveyard has "Island"
        }
    }

    // CR 702.40a: spells cast this turn before a spell
    @Test
    fun spellsCastBefore() = scenario {
        alice.hand("Storm Spark", "Magma Needle", "Glance Ahead")
        alice.library("Forest")

        alice.castsWithoutCosts("Storm Spark", target = "bob")
        alice.castsWithoutCosts("Magma Needle", target = "bob")
        alice.castsWithoutCosts("Glance Ahead")

        val e = evaluator()
        assertEquals(2, e.value(Value.SpellsCastBefore(arcanum.api.Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Named("Glance Ahead"),
            ObjectFilter.InZone(Zone.Stack)))))))
    }
}
