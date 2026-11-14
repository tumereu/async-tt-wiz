package arcanum.engine.sets.bro

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.LtrPlain
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Brothers' War chunk 05 in play: an attacker making the player it attacks mill half their library (three players), a planeswalker's
 * emblem of two abilities, an artifact exiling from the bottom of its controller's library as many cards as they put there while scrying
 * and letting them play those cards on their turn, and a creature card scrying from the graveyard.
 */
class BroChunk05MillAndScryScenariosTest {
    private val cogmindDevourer = Cards.invent("Cogmind Devourer", "{7}", "Artifact Creature — Juggernaut", """
        Whenever this creature attacks, defending player mills half their library, rounded up.
        Unearth {1}{U}{U}{U}
    """, "6/4")
    private val veska = Cards.invent("Veska, Gearwright Adept", "{2}{U}{R}", "Legendary Planeswalker — Veska", """
        [+1]: Scry 1. You may tap an untapped artifact you control. If you do, draw a card.
        [−2]: Create two 1/1 colorless Thopter artifact creature tokens with flying. They gain haste until end of turn.
        [−4]: You get an emblem with "Artifact creatures you control get +1/+1" and "Artifact spells you cast cost {1} less to cast."
    """, loyalty = "3")
    private val brasswingSentinel = Cards.invent("Brasswing Sentinel", "{2}", "Artifact Creature — Construct", "", "2/2")
    private val cogworkPup = Cards.invent("Cogwork Pup", "{2}", "Artifact Creature — Dog", "", "1/1")
    private val mossTrooper = Cards.invent("Moss Trooper", "{1}{G}", "Creature — Elf Soldier", "", "2/2")
    private val chronalLodestone = Cards.invent("The Chronal Lodestone", "{3}{U}{U}{U}", "Legendary Artifact", """
        At the beginning of your upkeep, scry 2.
        Whenever you choose to put one or more cards on the bottom of your library while scrying, exile that many cards from the bottom of your library.
        During your turn, you may play cards exiled with The Chronal Lodestone.
    """)
    private val glimmerOfForesight = Cards.invent("Glimmer of Foresight", "{U}", "Instant", "Draw a card.")
    private val lastOfTheGarrison = Cards.invent("Last of the Garrison", "{W}", "Creature — Human Soldier", """
        First strike
        {1}{W}, Exile this card from your graveyard: Scry 2.
    """, "1/1")

    // CR 506.2, 107.1a: the player this creature attacks mills half their library rounded up; another opponent mills nothing
    @Test
    fun devourerMillsHalfOfTheAttackedPlayersLibraryRoundedUp() = scenario(players = 3) {
        alice.battlefield(cogmindDevourer)
        bob.library("Island", "Island", "Island", "Island", "Island")
        carol.library("Swamp", "Swamp", "Swamp", "Swamp")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cogmindDevourer, defender = "bob")
        playOut()
        expect {
            bob.library.size shouldBe 2
            bob.graveyard.size shouldBe 3
            carol.library.size shouldBe 4
        }
    }

    // CR 114.3, 113.6p: both of the emblem's abilities work from the command zone: artifact creatures get +1/+1 (CR 613.4c) and an
    // artifact spell costs {1} less (CR 601.2f); a nonartifact creature is unchanged
    @Test
    fun veskasEmblemBoostsArtifactCreaturesAndCheapensArtifactSpells() = scenario {
        alice.battlefield(veska, counters = listOf(CounterKind.Loyalty to 4))
        alice.battlefield(brasswingSentinel, mossTrooper, "Island")
        alice.hand(cogworkPup)
        alice.activates(veska, ability = 2)
        allPass()
        alice.casts(cogworkPup, paying = listOf("Island"))
        allPass()
        expect {
            brasswingSentinel.power shouldBe 3
            brasswingSentinel.toughness shouldBe 3
            mossTrooper.power shouldBe 2
            cogworkPup.power shouldBe 2
            alice.battlefield has cogworkPup
            alice.battlefield lacks veska
        }
    }

    /** Alice's upkeep of turn 3 with [top] on top of her library and the Lodestone's scry trigger on the Stack. */
    private fun arcanum.engine.scenario.Scenario.lodestoneUpkeep(vararg top: String) {
        alice.battlefield(chronalLodestone, "Island")
        alice.library(*top, LtrPlain.ogre, LtrPlain.ogre, LtrPlain.ogre)
        bob.library(LtrPlain.ogre, LtrPlain.ogre, LtrPlain.ogre)
        advanceTo(TurnStep.Upkeep, turn = 3)
    }

    // CR 701.22a, d: the cards put on the bottom while scrying are counted once the scry is done, and that many are exiled from the bottom;
    // CR 607.2a, 611.3a: they can be played during your turn
    @Test
    fun lodestoneExilesWhatWentToTheBottomAndLetsYouPlayIt() = scenario {
        lodestoneUpkeep("Mountain", "Forest")
        playOut("Mountain", "Forest")
        expect {
            alice.exile has "Mountain"
            alice.exile has "Forest"
            alice.library.size shouldBe 3
        }
        advanceTo(TurnStep.PrecombatMain)
        alice.plays("Mountain")
        expect { alice.battlefield has "Mountain" }
    }

    // CR 701.22a: a scry that keeps every card on top puts none on the bottom, so nothing triggers
    @Test
    fun lodestoneDoesNothingWhenNoCardGoesToTheBottom() = scenario {
        lodestoneUpkeep("Mountain", "Forest")
        playOut()
        expect {
            alice.exile.isEmpty()
            alice.library.size shouldBe 5
            stack.isEmpty()
        }
    }

    // CR 611.3a: on an opponent's turn an exiled instant can't be cast; on your own it can
    @Test
    fun lodestonesCardsArePlayableOnlyOnYourTurn() = scenario {
        lodestoneUpkeep(glimmerOfForesight)
        playOut(glimmerOfForesight)
        expect { alice.exile has glimmerOfForesight }
        val castable = { arcanum.engine.Engine.options(state, alice.id).any { it is arcanum.engine.input.Input.Cast && it.card == card(glimmerOfForesight) } }
        assertTrue(castable())
        advanceTo(TurnStep.Upkeep, turn = 4)
        bob.passes()
        assertEquals(alice.id, state.priority.holder)
        assertFalse(castable())
    }

    // CR 113.6m, 602.1: exiling the card from the graveyard is the cost, so the ability works from there (no stated zone needed) and not
    // from the battlefield
    @Test
    fun garrisonScriesFromTheGraveyardOnly() = scenario {
        alice.graveyard(lastOfTheGarrison)
        alice.battlefield("Plains", "Plains")
        alice.library("Island", "Swamp", "Forest")
        alice.activates(lastOfTheGarrison)
        allPass()
        val scry = pending as Decision.ChooseObjects
        assertEquals(2, scry.options.size)
        playOut("Island")
        expect {
            alice.exile has lastOfTheGarrison
            alice.graveyard lacks lastOfTheGarrison
            alice.library.names shouldBe listOf("Swamp", "Forest", "Island")
        }
    }

    // CR 113.6m: on the battlefield the graveyard cost can't be paid
    @Test
    fun garrisonOnTheBattlefieldCantActivate() = scenario {
        alice.battlefield(lastOfTheGarrison, "Plains", "Plains")
        assertFailsWith<IllegalArgumentException> { alice.activates(lastOfTheGarrison) }
    }
}
