package arcanum.engine.cube

import arcanum.api.Ability
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Cube chunk 7, counterspells, played as a player would: cast in response, paid, and resolved through priority. */
class Chunk07CounterspellsScenariosTest {
    private fun Scenario.alternative(name: String): CastWay = CastWay(alternative = view()[card(name)]!!.characteristics!!.abilities.first { inst ->
        (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost } == true
    }.origin)

    /** A vanilla 3/3, too big for Sharp Rebuke. */
    private val brute = Cards.invent("Bramblehide Brute", "{2}{G}", "Creature — Beast", "", "3/3")

    /** Denial: counter target spell. */
    @Test
    fun denial() = scenario {
        alice.battlefield("Forest")
        alice.hand("Elvish Seer")
        bob.battlefield("Island", "Island")
        bob.hand("Denial")

        alice.casts("Elvish Seer")
        alice.passes()
        bob.casts("Denial", target = "Elvish Seer")
        expect {
            stack.names shouldBe listOf("Elvish Seer", "Denial")
            card("Island", 0).tapped shouldBe true
            card("Island", 1).tapped shouldBe true
        }
        allPass()

        expect {
            alice.graveyard has "Elvish Seer"
            bob.graveyard has "Denial"
            stack.isEmpty()
            log mentions "Countered"
        }
    }

    /** Spell Noose: counter target spell with mana value 2; a spell of another mana value isn't a target. */
    @Test
    fun spellNoose() = scenario {
        alice.battlefield("Plains", "Plains", "Mountain")
        alice.hand("Wall of Portents", "Storm Spark")
        alice.library("Forest")
        bob.battlefield("Island")
        bob.hand("Spell Noose")

        alice.casts("Wall of Portents")
        alice.casts("Storm Spark", target = "bob")
        alice.passes()
        bob.casts("Spell Noose")
        // CR 601.2c, 202.3: only the mana value 2 spell can be chosen
        assertEquals(listOf(TargetRef.Object(card("Wall of Portents"))), decision<Decision.Targets>().options)
        answer(Answer.Targets(listOf(target("Wall of Portents"))))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()
        allPass()

        expect {
            bob.life shouldBe 17
            alice.graveyard has "Wall of Portents"
            alice.battlefield lacks "Wall of Portents"
            bob.graveyard has "Spell Noose"
        }
    }

    /** Sharp Rebuke: counter target creature spell with power or toughness 2 or less. */
    @Test
    fun sharpRebuke() = scenario {
        alice.battlefield("Forest")
        alice.hand("Elvish Seer")
        bob.battlefield("Island")
        bob.hand("Sharp Rebuke")

        alice.casts("Elvish Seer")
        alice.passes()
        bob.casts("Sharp Rebuke", target = "Elvish Seer")
        allPass()

        expect {
            alice.graveyard has "Elvish Seer"
            bob.graveyard has "Sharp Rebuke"
        }
    }

    // CR 601.2c: a 3/3 creature spell isn't a legal target, so with only it on the Stack Sharp Rebuke can't be cast
    @Test
    fun sharpRebukeIgnoresABigCreature() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(brute)
        bob.battlefield("Island")
        bob.hand("Sharp Rebuke")

        alice.casts(brute)
        alice.passes()
        assertFailsWith<IllegalArgumentException> { bob.casts("Sharp Rebuke") }
        expect { bob.hand has "Sharp Rebuke" }
    }

    /** Mana Seep: counter target spell unless its controller pays {3}; they can't, so it's countered. */
    @Test
    fun manaSeepWhenTheyCantPay() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Island", "Island")
        bob.hand("Mana Seep")

        alice.casts("Storm Spark", target = "bob")
        alice.passes()
        bob.casts("Mana Seep", target = "Storm Spark")
        allPass()

        // CR 118.3: one untapped Mountain can't pay {3}: nobody is asked
        expect {
            alice.graveyard has "Storm Spark"
            bob.life shouldBe 20
            bob.graveyard has "Mana Seep"
            stack.isEmpty()
        }
    }

    /** Mana Seep: the controller pays {3} with their lands, and the spell stays. */
    @Test
    fun manaSeepPaid() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Island", "Island")
        bob.hand("Mana Seep")

        alice.casts("Storm Spark", target = "bob")
        alice.passes()
        bob.casts("Mana Seep", target = "Storm Spark")
        allPass()
        // CR 118.12a: its controller is asked, then pays
        assertEquals(alice.id, decision<Decision.May>().player)
        answer(Answer.YesNo(true))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        expect {
            (0..3).all { card("Mountain", it).tapped } shouldBe true
            stack.names shouldBe listOf("Storm Spark")
        }
        allPass()

        expect { bob.life shouldBe 17 }
    }

    /** Mana Levy: counter target spell unless its controller pays {1}; they decline. */
    @Test
    fun manaLevyDeclined() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Plains")
        bob.hand("Mana Levy")

        alice.casts("Storm Spark", target = "bob")
        alice.passes()
        bob.casts("Mana Levy", target = "Storm Spark")
        allPass()
        answer(Answer.YesNo(false))

        expect {
            alice.graveyard has "Storm Spark"
            card("Mountain", 1).tapped shouldBe false
            bob.life shouldBe 20
        }
    }

    /** Spell Prick: counter target noncreature spell unless its controller pays {2}. */
    @Test
    fun spellPrick() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Island")
        bob.hand("Spell Prick")

        alice.casts("Storm Spark", target = "bob")
        alice.passes()
        bob.casts("Spell Prick", target = "Storm Spark")
        allPass()

        // One Mountain left can't pay {2}
        expect {
            alice.graveyard has "Storm Spark"
            bob.life shouldBe 20
        }
    }

    // CR 601.2c: a creature spell isn't a target for Spell Prick
    @Test
    fun spellPrickIgnoresCreatureSpells() = scenario {
        alice.battlefield("Forest")
        alice.hand("Elvish Seer")
        bob.battlefield("Island")
        bob.hand("Spell Prick")

        alice.casts("Elvish Seer")
        alice.passes()
        assertFailsWith<IllegalArgumentException> { bob.casts("Spell Prick") }
    }

    /** Lapse of Focus: return an Island instead of paying; counter unless they pay {1}, and they pay. */
    @Test
    fun lapseOfFocusForAnIsland() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand("Elvish Seer")
        bob.battlefield("Island")
        bob.hand("Lapse of Focus")

        alice.casts("Elvish Seer")
        alice.passes()
        // CR 118.9: the alternative cost returns the Island instead of paying {1}{U}
        bob.casts("Lapse of Focus", way = alternative("Lapse of Focus"), target = "Elvish Seer", choosing = listOf("Island"))
        expect {
            bob.hand has "Island"
            bob.battlefield.isEmpty()
        }
        allPass()
        answer(Answer.YesNo(true))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()

        expect {
            alice.battlefield has "Elvish Seer"
            bob.graveyard has "Lapse of Focus"
        }
    }

    /** Blaze of Refusal: sacrifice a nontoken blue creature instead of paying; counter target spell. */
    @Test
    fun blazeOfRefusalBySacrifice() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Myriad-Faced Shade", "Elvish Seer")
        bob.hand("Blaze of Refusal")

        alice.casts("Storm Spark", target = "bob")
        alice.passes()
        // CR 118.9: the only nontoken blue creature is sacrificed
        bob.casts("Blaze of Refusal", way = alternative("Blaze of Refusal"), target = "Storm Spark", choosing = listOf("Myriad-Faced Shade"))
        expect {
            bob.graveyard has "Myriad-Faced Shade"
            bob.battlefield has "Elvish Seer"
        }
        allPass()

        expect {
            alice.graveyard has "Storm Spark"
            bob.life shouldBe 20
        }
    }

    /** Blaze of Refusal for its mana cost. */
    @Test
    fun blazeOfRefusalForMana() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Island", "Island", "Island")
        bob.hand("Blaze of Refusal")

        alice.casts("Storm Spark", target = "bob")
        alice.passes()
        bob.casts("Blaze of Refusal", target = "Storm Spark")
        allPass()

        expect {
            alice.graveyard has "Storm Spark"
            (0..2).all { card("Island", it).tapped } shouldBe true
        }
    }

    /** Force of Resolve: pay 1 life and exile a blue card from hand instead of paying; counter target spell. */
    @Test
    fun forceOfResolveForFree() = scenario {
        alice.battlefield("Forest")
        alice.hand("Elvish Seer")
        bob.hand("Force of Resolve", "Denial")

        alice.casts("Elvish Seer")
        alice.passes()
        bob.casts("Force of Resolve", way = alternative("Force of Resolve"), target = "Elvish Seer", choosing = listOf("Denial"))
        expect {
            bob.life shouldBe 19
            bob.exile has "Denial"
        }
        allPass()

        expect {
            alice.graveyard has "Elvish Seer"
            bob.graveyard has "Force of Resolve"
        }
    }

    /** Force of Rebuttal: on the opponent's turn, exile a blue card instead of paying; the countered noncreature spell is
     * exiled instead of going to the graveyard. */
    @Test
    fun forceOfRebuttal() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.hand("Force of Rebuttal", "Denial")

        alice.casts("Storm Spark", target = "bob")
        alice.passes()
        bob.casts("Force of Rebuttal", way = alternative("Force of Rebuttal"), target = "Storm Spark", choosing = listOf("Denial"))
        allPass()

        // CR 614.15: the counterspell's own replacement exiles the spell
        expect {
            alice.exile has "Storm Spark"
            alice.graveyard lacks "Storm Spark"
            bob.exile has "Denial"
            bob.graveyard has "Force of Rebuttal"
            bob.life shouldBe 20
        }
    }

    /** Recall Lapse: the countered spell goes on top of its owner's library. */
    @Test
    fun recallLapse() = scenario {
        alice.battlefield("Forest")
        alice.hand("Elvish Seer")
        alice.library("Island")
        bob.battlefield("Island", "Island")
        bob.hand("Recall Lapse")

        alice.casts("Elvish Seer")
        alice.passes()
        bob.casts("Recall Lapse", target = "Elvish Seer")
        allPass()

        expect {
            alice.library.names shouldBe listOf("Elvish Seer", "Island")
            alice.graveyard.isEmpty()
            bob.graveyard has "Recall Lapse"
        }
    }

    /** Send Back: the countered spell returns to its owner's hand; its caster draws a card. */
    @Test
    fun sendBack() = scenario {
        alice.battlefield("Forest")
        alice.hand("Elvish Seer")
        bob.battlefield("Island", "Island")
        bob.hand("Send Back")
        bob.library("Swamp")

        alice.casts("Elvish Seer")
        alice.passes()
        bob.casts("Send Back", target = "Elvish Seer")
        allPass()

        expect {
            alice.hand has "Elvish Seer"
            bob.hand.names shouldBe listOf("Swamp")
            bob.graveyard has "Send Back"
        }
    }

    /** Mana Siphon: counter target spell; at the beginning of its caster's next main phase, {C} for each of the
     * countered spell's mana value. */
    @Test
    fun manaSiphon() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand("Canopy Visionary")
        bob.battlefield("Island", "Island")
        bob.hand("Mana Siphon")
        bob.library("Swamp")

        alice.casts("Canopy Visionary")
        alice.passes()
        bob.casts("Mana Siphon", target = "Canopy Visionary")
        allPass()
        expect { alice.graveyard has "Canopy Visionary" }

        // CR 603.7a: the delayed trigger waits for bob's own next main phase
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        expect { stack.names shouldBe listOf("ability of Mana Siphon") }
        allPass()

        assertEquals(listOf(null, null, null), bob.pool.map { it.color })
    }
}
