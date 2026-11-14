package arcanum.engine.sets.mkm

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.abilityIndex
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.unblocked
import arcanum.engine.sets.tdm.Plain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** MKM chunk 04: surveil (CR 701.25) on a land that enters tapped, a batch attack trigger of suspected creatures (CR 701.60, 508.3d), and a
 * planeswalker whose exile trigger copies a creature card onto a token (CR 707.2) and whose minus ability targets once per other player. */
class MkmChunk04SurveilScenariosTest {
    private val alcove = Cards.invent("Hushed Alcove", null, "Land — Mountain Forest", """
        ({T}: Add {R} or {G}.)
        This land enters tapped.
        When this land enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
    """)
    private val fixer = Cards.invent("Furtive Fixer", "{2}{B}", "Creature — Vampire Rogue", """
        When this creature enters, suspect up to one other target creature you control. (A suspected creature has menace and can't block.)
        Whenever one or more suspected creatures you control attack, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
    """, "3/2")
    private val sigrid = Cards.invent("Sigrid, Verdict of Spirits", "{2}{W}{B}", "Legendary Planeswalker — Sigrid", """
        Whenever one or more creatures you control and/or creature cards in your graveyard are put into exile, you may choose a creature card from among them. Until end of turn, target token you control becomes a copy of it, except it has flying.
        [+2]: Surveil 2, then exile a card from a graveyard.
        [+1]: Create a 1/1 white and black Spirit creature token with flying.
        [−2]: Exile target creature you control. For each other player, exile up to one target creature that player controls.
    """, loyalty = "3")
    private val maker = Cards.invent("Tinker's Whistle", "{1}", "Sorcery", "Create a 1/1 colorless Thopter artifact creature token with flying.")

    // CR 614.1c: it enters tapped; CR 701.25a: look at the top card, put it into the graveyard
    @Test
    fun surveilLandEntersTappedAndPutsTheCardInTheGraveyard() = scenario {
        alice.hand(alcove)
        alice.library(Plain.ogre, Plain.elf)
        alice.plays(alcove)
        playOut(Plain.ogre)
        expect {
            alcove.tapped shouldBe true
            alice.graveyard has Plain.ogre
            alice.library has Plain.elf
        }
    }

    // CR 701.25a: "you may": it can stay on top
    @Test
    fun surveilLandMayKeepTheCardOnTop() = scenario {
        alice.hand(alcove)
        alice.library(Plain.ogre, Plain.elf)
        alice.plays(alcove)
        playOut()
        expect {
            alice.library has Plain.ogre
            alice.graveyard.isEmpty()
        }
    }

    // CR 701.60a, c: suspected creatures have menace; CR 508.3d: attacking with two of them (or with one) triggers once
    @Test
    fun suspectedAttackersSurveilOnceForTheBatch() = scenario {
        alice.battlefield(Plain.bear, Plain.elf, "Swamp", "Swamp", "Swamp")
        alice.hand(fixer)
        alice.library(Plain.ogre, Plain.giant, Plain.idol)
        alice.casts(fixer)
        playOut(Plain.bear)
        expect { Plain.bear hasKeyword Keyword.Menace }
        // the suspect effect only reached the targeted creature
        expect { Plain.elf lacksKeyword Keyword.Menace }
    }

    @Test
    fun suspectedCreatureAttackingSurveilsOnce() = scenario {
        alice.battlefield(Plain.bear, Plain.elf, "Swamp", "Swamp", "Swamp")
        alice.hand(fixer)
        alice.library(Plain.ogre, Plain.giant)
        alice.casts(fixer)
        playOut(Plain.bear)
        unblocked(Plain.bear, Plain.elf, picks = listOf(Plain.ogre))
        expect {
            alice.graveyard has Plain.ogre
            alice.library has Plain.giant
        }
    }

    // CR 603.2: a creature that isn't suspected attacking doesn't trigger it
    @Test
    fun anUnsuspectedAttackerDoesNotSurveil() = scenario {
        alice.battlefield(Plain.bear, Plain.elf, "Swamp", "Swamp", "Swamp")
        alice.hand(fixer)
        alice.library(Plain.ogre, Plain.giant)
        alice.casts(fixer)
        playOut(Plain.bear)
        unblocked(Plain.elf, picks = listOf(Plain.ogre))
        expect {
            alice.graveyard.isEmpty()
            alice.library has Plain.ogre
        }
    }

    // CR 701.25a, 701.13a: surveil 2, then exile a card from any graveyard (not targeted)
    @Test
    fun plusTwoSurveilsAndExilesACardFromAnotherPlayersGraveyard() = scenario {
        alice.battlefield(sigrid, counters = listOf(CounterKind.Loyalty to 3))
        alice.library(Plain.bear, Plain.elf)
        bob.graveyard(Plain.giant)
        alice.activates(sigrid, ability = abilityIndex(sigrid, "Surveil"))
        playOut(Plain.giant)
        expect {
            bob.exile has Plain.giant
            sigrid.loyalty shouldBe 5
        }
    }

    // CR 603.2c, 707.2, 707.9b: exiling a creature card from your graveyard lets a token you control become a copy of it with flying until end of turn
    @Test
    fun exilingACreatureCardFromYourGraveyardMakesATokenCopyIt() = scenario {
        alice.battlefield(sigrid, "Island", counters = listOf(CounterKind.Loyalty to 3))
        alice.hand(maker)
        alice.graveyard(Plain.giant)
        alice.library(Plain.bear, Plain.elf)
        alice.casts(maker)
        playOut()
        val token = card("Thopter Token")
        alice.activates(sigrid, ability = abilityIndex(sigrid, "Surveil"))
        playOut(Plain.giant, "Thopter Token")
        val copy = view()[token]!!.characteristics!!
        assertEquals("Steppe Giant", copy.name)
        assertEquals(5, copy.power)
        assertTrue(copy.abilities.any { it.ability == Keyword.Flying })
        expect { alice.exile has Plain.giant }
        advanceTo(arcanum.api.TurnStep.Upkeep, turn = 2)
        assertEquals("Thopter Token", view()[token]!!.characteristics!!.name)
    }

    // CR 601.2c, 115.1: one target for each other player; with 3 players each opponent's creature is its own target
    @Test
    fun minusTwoExilesOneOfYoursAndOneOfEachOpponents() = scenario(players = 3) {
        alice.battlefield(sigrid, Plain.bear, counters = listOf(CounterKind.Loyalty to 3))
        bob.battlefield(Plain.ogre, Plain.elf)
        carol.battlefield(Plain.giant, Plain.dragon)
        alice.activates(sigrid, ability = abilityIndex(sigrid, "Exile target creature you control"))
        playOut(Plain.bear, Plain.ogre, Plain.giant)
        expect {
            alice.exile has Plain.bear
            bob.exile has Plain.ogre
            carol.exile has Plain.giant
            bob.battlefield has Plain.elf
            carol.battlefield has Plain.dragon
            sigrid.loyalty shouldBe 1
        }
    }

    // CR 114: up to one: a player's creature can be left alone
    @Test
    fun minusTwoMayLeaveAnOpponentsCreatureAlone() = scenario(players = 3) {
        alice.battlefield(sigrid, Plain.bear, counters = listOf(CounterKind.Loyalty to 3))
        bob.battlefield(Plain.ogre)
        carol.battlefield(Plain.giant)
        alice.activates(sigrid, ability = abilityIndex(sigrid, "Exile target creature you control"))
        playOut(Plain.bear, Plain.giant)
        expect {
            carol.exile has Plain.giant
            bob.battlefield has Plain.ogre
        }
    }
}
