package arcanum.engine.sets.dsk

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Duskmourn's choices in play: beholding as a cost, targets with one controller, and cards with different powers. */
class DskChoicesScenariosTest {
    private val debut = Cards.invent("Monstrous Debut", "{1}{G}", "Sorcery", """
        As an additional cost to cast this spell, choose a creature you control or reveal a creature card from your hand.
        Monstrous Debut deals damage equal to the power of the creature you chose or the card you revealed to target creature.
    """)
    private val brute = Cards.invent("Towering Brute", "{4}{G}", "Creature — Giant", "", "5/5")
    private val bear = Cards.invent("Stone Bear", "{2}{G}", "Creature — Bear", "", "4/4")
    private val ordeal = Cards.invent("Ordeal of Pain", "{R}", "Sorcery",
        "Choose two target creatures controlled by the same opponent. That player chooses one of those creatures. Ordeal of Pain deals 5 damage to that creature, and the other can't block this turn.")
    private val stalker = Cards.invent("Brood Prowler", "{2}{G}{W}", "Legendary Creature — Human Survivor",
        "Survival — At the beginning of your second main phase, if Brood Prowler is tapped, reveal the top X cards of your library, where X is its power. Put any number of creature and/or Vehicle cards with different powers from among them into your hand. Put the rest on the bottom of your library in a random order.",
        "4/4")

    // CR 701.4a: beholding a creature by revealing it from the hand; its power is read for the damage
    @Test
    fun beholdFromTheHand() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(debut, brute)
        bob.battlefield(bear)
        alice.casts(debut, target = bear, choosing = listOf(brute))
        allPass()
        expect {
            bob.graveyard has bear
            alice.hand has brute
        }
    }

    // CR 701.4a: or by choosing a creature you control
    @Test
    fun beholdOnTheBattlefield() = scenario {
        alice.battlefield("Forest", "Forest", brute)
        alice.hand(debut)
        bob.battlefield(bear)
        alice.casts(debut, target = bear, choosing = listOf(brute))
        allPass()
        expect { bob.graveyard has bear }
    }

    // CR 601.2c: two targets controlled by the same opponent; that player chooses which is dealt damage
    @Test
    fun targetsWithOneController() = scenario(players = 3) {
        alice.battlefield("Mountain")
        alice.hand(ordeal)
        bob.battlefield(bear, "Elvish Seer")
        carol.battlefield(brute)
        assertFailsWith<IllegalArgumentException> { alice.casts(ordeal, targets = listOf(bear, brute)) }
    }

    // CR 601.2c: that player chooses one of them; the other can't block
    @Test
    fun thatPlayerChooses() = scenario {
        alice.battlefield("Mountain")
        alice.hand(ordeal)
        bob.battlefield(bear, "Elvish Seer")
        alice.casts(ordeal, targets = listOf(bear, "Elvish Seer"))
        allPass()
        bob.chooses(bear)
        expect {
            bob.graveyard has bear
            card("Elvish Seer").zone shouldBe Zone.Battlefield
        }
    }

    // "with different powers": no two chosen cards share a power
    @Test
    fun differentPowers() = scenario {
        alice.battlefield(stalker, tapped = true)
        alice.library("Elvish Seer", brute, "Elvish Seer", "Swamp")
        advanceTo(TurnStep.PostcombatMain)
        allPass()
        val seers = decision<Decision.ChooseObjects>().options.filter { state.objOrThrow(it).definition?.faces?.first()?.name == "Elvish Seer" }
        assertFailsWith<IllegalArgumentException> { answer(Answer.Objects(seers)) }
        alice.chooses("Elvish Seer", brute)
        expect { alice.hand has brute }
    }
}
