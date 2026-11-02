package arcanum.engine.review

import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.Players
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Review 1: what costs leave behind (last known information of paid objects, CR 608.2h), "this turn" questions, and
 * cost changes of abilities and players' restrictions. */
class Review1HistoryTest {
    private fun Scenario.settleStack(vararg names: String) {
        var guard = 30
        while ((state.stack.isNotEmpty() || pending != null) && guard-- > 0) {
            when (val d = pending) {
                is Decision.Targets -> answer(Answer.Targets(names.map { target(it) }.filter { it in d.options }.take(d.max ?: 1)
                    .ifEmpty { d.options.take(d.min.coerceAtLeast(1)) }))
                is Decision.May -> answer(Answer.YesNo(true))
                is Decision.OrderTriggers -> answer(Answer.Order(d.triggers.indices.toList()))
                is Decision.ChooseObjects -> answer(Answer.Objects(d.options.take(d.min.coerceAtLeast(1))))
                null -> allPass()
                else -> return
            }
        }
    }

    /** "Mills cards equal to the sacrificed creature's power": its power as it was sacrificed, with a pump. */
    // CR 608.2h, 118.3
    @Test
    fun sacrificedCreaturesPumpedPowerCounts() = scenario {
        alice.battlefield("Shrine of Oblivion", "Thicketwood Elves")
        effect(alice, Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(0)), on = listOf("Thicketwood Elves"))
        bob.library("Island", "Island", "Island", "Island", "Island")
        alice.activates("Shrine of Oblivion", target = "bob", choosing = listOf("Thicketwood Elves"))
        settleStack("bob")
        expect { bob.graveyard.size shouldBe 3 }
    }

    /** "With mana value equal to the number of charge counters on this artifact", read after sacrificing it as a cost. */
    // CR 608.2h
    @Test
    fun countersOfASacrificedSource() = scenario {
        alice.battlefield("Clockwork Charge", counters = listOf(CounterKind.Named("charge") to 1))
        bob.battlefield("Elvish Seer", "Sun Band", "Canopy Visionary")
        alice.activates("Clockwork Charge", ability = 1)
        settleStack()
        expect {
            bob.graveyard has "Elvish Seer"
            bob.graveyard has "Sun Band"
            bob.battlefield has "Canopy Visionary"
        }
    }

    /** "Creatures that died under your control this turn" counts a creature alice controlled but didn't own. */
    // CR 700.4, 603.10a
    @Test
    fun diedUnderYourControlCountsAStolenCreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain")
        alice.hand("Storm Spark", "Gravecaller Banneret")
        alice.library("Island", "Island", "Island")
        bob.battlefield("Thicketwood Elves")
        effect(alice, Mechanic.ControlledBy(Players.You), on = listOf("Thicketwood Elves"))
        alice.casts("Storm Spark", target = "Thicketwood Elves", paying = listOf("Mountain"))
        settleStack()
        alice.casts("Gravecaller Banneret")
        settleStack()
        expect {
            bob.graveyard has "Thicketwood Elves"
            alice.hand.size shouldBe 1
        }
    }

    /** A player who already cast a nonartifact spell this turn can't cast another once the restriction arrives. */
    // CR 101.2, 601.3
    @Test
    fun castBeforeTheRestrictionCounts() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Storm Spark", "Magma Needle")
        bob.hand("Aetherbound Lawkeeper")
        alice.casts("Storm Spark", target = "bob", paying = listOf("Mountain"))
        settleStack()
        move("Aetherbound Lawkeeper", arcanum.api.Zone.Battlefield)
        kotlin.test.assertFailsWith<IllegalArgumentException> { perform(Input.Cast(alice.id, card("Magma Needle"))) }
        val casts = Engine.options(state, alice.id).filterIsInstance<Input.Cast>()
        assertTrue(casts.none { it.card == card("Magma Needle") }, "Offered: $casts")
    }

    /** An activated ability's own cost reduction: Okurai's channel costs {1} less for each legendary creature. */
    // CR 602.2b, 601.2f
    @Test
    fun abilityCostReduction() = scenario {
        alice.battlefield("Velo, Master of Ceremonies", "Forest")
        alice.hand("Okurai, Who Abides")
        bob.battlefield("Sun Band")
        perform(Input.Activate(alice.id, card("Okurai, Who Abides"),
            view()[card("Okurai, Who Abides")]!!.characteristics!!.abilities.first { it.ability is arcanum.api.Ability.Activated &&
                (it.ability as arcanum.api.Ability.Activated).cost.parts.any { p -> p is arcanum.api.CostPart.Discard } }.origin))
        var guard = 5
        while (pending !is Decision.ManaPayment && pending != null && guard-- > 0) when (val d = pending) {
            is Decision.Targets -> answer(Answer.Targets(listOf(target("Sun Band"))))
            else -> kotlin.test.fail("Unexpected $d")
        }
        assertEquals(arcanum.api.ManaCost.parse("{G}"), decision<Decision.ManaPayment>().cost)
    }

    /** The opponent's Lawkeeper stops a second nonartifact spell cast the same turn. */
    // CR 101.2, 601.3
    @Test
    fun lawkeeperStopsTheSecondSpell() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Storm Spark", "Magma Needle")
        bob.battlefield("Aetherbound Lawkeeper")
        alice.casts("Storm Spark", target = "bob", paying = listOf("Mountain"))
        allPass()
        kotlin.test.assertFailsWith<IllegalArgumentException> { perform(Input.Cast(alice.id, card("Magma Needle"))) }
    }

    /** "Unless you attacked this turn": attacking first skips the discard. */
    // CR 508.1
    @Test
    fun attackedThisTurn() = scenario {
        alice.battlefield("Thicketwood Elves", "Island", "Island")
        alice.hand("Plot the Voyage", "Mountain")
        alice.library("Forest", "Forest", "Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Thicketwood Elves")
        advanceTo(TurnStep.PostcombatMain)
        alice.casts("Plot the Voyage")
        settleStack()
        expect { alice.hand.size shouldBe 3 }
    }

    /** Revolt: a permanent left the battlefield under your control this turn (a token counts). */
    // CR 702.? (revolt is an ability word), 603.10a
    @Test
    fun revoltSeesATokenLeave() = scenario {
        alice.battlefield("Swamp", "Plains", "Plains", "Plains")
        alice.hand("Lingering Shades", "Deadly Shove")
        bob.battlefield("Canopy Visionary", "Mountain")
        bob.hand("Magma Needle")
        alice.casts("Lingering Shades", paying = listOf("Plains", "Plains#1", "Plains#2"))
        settleStack()
        alice.passes()
        bob.casts("Magma Needle", target = "Spirit Token")
        settleStack()
        alice.casts("Deadly Shove", target = "Canopy Visionary")
        settleStack()
        expect { bob.graveyard has "Canopy Visionary" }
    }

    /** Ilsa's landfall counts its own resolutions this turn: the second one reveals for an Elf. */
    // CR 603.4
    @Test
    fun secondResolutionThisTurn() = scenario {
        alice.battlefield("Ilsa, Verdant Seeker", "Frontier Wandering")
        alice.hand("Forest", "Forest")
        alice.library("Island", "Elvish Seer", "Island")
        alice.plays("Forest")
        var first = 10
        while ((state.stack.isNotEmpty() || pending != null) && first-- > 0) when (pending) {
            is Decision.ChooseColor -> answer(Answer.ColorChoice(arcanum.api.Color.Green))
            else -> settleStack()
        }
        perform(Input.PlayLand(alice.id, card("Forest", 1)))
        var guard = 10
        while ((state.stack.isNotEmpty() || pending != null) && guard-- > 0) when (val d = pending) {
            is Decision.ChooseColor -> answer(Answer.ColorChoice(arcanum.api.Color.Green))
            else -> settleStack()
        }
        expect { alice.hand has "Elvish Seer" }
    }
}
