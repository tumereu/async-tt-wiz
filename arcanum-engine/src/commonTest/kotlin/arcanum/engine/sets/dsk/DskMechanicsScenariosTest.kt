package arcanum.engine.sets.dsk

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Duskmourn's keyword actions and ability words in play: manifest dread, impending, survival and delirium. */
class DskMechanicsScenariosTest {
    private val dread = Cards.invent("Creeping Dread", "{1}{G}", "Sorcery", "Manifest dread.")
    private val overlord = Cards.invent("Overlord of the Dim Halls", "{4}{G}{G}", "Enchantment Creature — Avatar Horror", """
        Impending 4—{1}{G}{G} (If you cast this spell for its impending cost, it enters with four time counters and isn't a creature until the last is removed. At the beginning of your end step, remove a time counter from it.)
        Whenever this permanent enters or attacks, you gain 2 life.
    """, "6/6")
    private val survivor = Cards.invent("Hallway Survivor", "{1}{W}", "Creature — Human Survivor",
        "Survival — At the beginning of your second main phase, if this creature is tapped, you gain 2 life.", "2/2")
    private val delirious = Cards.invent("Frenzied Lurker", "{1}{B}", "Creature — Horror",
        "Delirium — This creature gets +2/+1 as long as there are four or more card types among cards in your graveyard.", "1/1")

    private fun Scenario.alternative(name: String): CastWay = CastWay(alternative = view()[card(name)]!!.characteristics!!.abilities.first { inst ->
        (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost } == true
    }.origin)

    // CR 701.62a: look at the top two cards, manifest one, put the rest into the graveyard; CR 701.40a: a 2/2 face-down
    // creature; CR 701.40b: a manifested creature card turns face up for its mana cost
    @Test
    fun manifestDread() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.library("Elvish Seer", "Swamp")
        alice.hand(dread)
        alice.casts(dread, paying = listOf("Forest", "Forest"))
        allPass()
        alice.chooses("Elvish Seer")
        expect {
            alice.graveyard has "Swamp"
            card("Elvish Seer").zone shouldBe Zone.Battlefield
            card("Elvish Seer").power shouldBe 2
            card("Elvish Seer").types shouldBe listOf(CardType.Creature)
        }
        alice.takesSpecialAction("Elvish Seer", "manifested", paying = listOf("Forest#2"))
        expect { card("Elvish Seer").power shouldBe 1 }
    }

    // CR 702.176a: cast for its impending cost it enters with time counters and isn't a creature while it has one; at
    // the beginning of your end step one is removed
    @Test
    fun impending() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(overlord)
        alice.casts(overlord, way = alternative(overlord))
        allPass()
        allPass()
        expect {
            overlord.counters(CounterKind.Time) shouldBe 4
            assertTrue(CardType.Creature !in overlord.types)
            alice.life shouldBe 22
        }
        advanceTo(TurnStep.End)
        allPass()
        expect { overlord.counters(CounterKind.Time) shouldBe 3 }
    }

    // CR 702.176a: with its last time counter removed it is a creature again
    @Test
    fun impendingEndsWithTheLastCounter() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(overlord)
        alice.library("Swamp", "Swamp", "Swamp")
        bob.library("Swamp", "Swamp", "Swamp")
        alice.casts(overlord, way = alternative(overlord))
        allPass()
        allPass()
        advanceTo(TurnStep.End, turn = 7) // alice's fourth end step
        allPass()
        expect {
            overlord.counters(CounterKind.Time) shouldBe 0
            assertTrue(CardType.Creature in overlord.types)
            overlord.power shouldBe 6
        }
    }

    // CR 505.1a: the second main phase is the postcombat main phase; survival checks the tapped creature as it begins
    @Test
    fun survival() = scenario {
        alice.battlefield(survivor, tapped = true)
        advanceTo(TurnStep.PostcombatMain)
        allPass()
        expect { alice.life shouldBe 22 }
    }

    // Delirium: four or more card types among cards in your graveyard
    @Test
    fun delirium() = scenario {
        alice.battlefield(delirious)
        alice.graveyard("Swamp", "Elvish Seer")
        expect { delirious.power shouldBe 1 }
        alice.graveyard(dread, overlord)
        expect {
            delirious.power shouldBe 3
            assertEquals(2, delirious.toughness)
        }
    }
}
