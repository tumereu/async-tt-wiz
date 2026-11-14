package arcanum.engine.sets.dmu

import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dft.hits
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The DMU domain cards of chunk 02 that give the engine something new, played: the invented names carry the oracle text of Bortuk
 * Bonerattle, Nael, Avizoa Aeronaut and Sphinx of Clear Skies.
 */
class DmuChunk02DomainScenariosTest {
    private val sprout = Cards.invent("Domain Test Sprout", "{1}{G}", "Creature — Plant", "", "2/2")
    private val ogre = Cards.invent("Domain Test Ogre", "{2}{B}", "Creature — Ogre", "", "3/3")
    private val pages = (1..6).map { Cards.invent("Domain Test Page $it", "{U}", "Instant", "Draw a card.") }

    private val thornback = Cards.invent("Gravecaller Thornback", "{4}{B}{G}", "Legendary Creature — Troll Shaman",
        "Domain — When Gravecaller Thornback enters, if you cast it, choose target creature card in your graveyard. Return that card to the battlefield if its mana value is less than or equal to the number of basic land types among lands you control. Otherwise, put it into your hand.",
        "4/4")
    private val ilsa = Cards.invent("Ilsa, Cloudsail Scout", "{2}{G}{U}", "Legendary Creature — Elf Scout", """
        Flying
        Domain — Whenever Ilsa deals combat damage to a player, look at the top X cards of your library, where X is the number of basic land types among lands you control. Put up to one of them on top of your library and the rest on the bottom in a random order. Then if there are five basic land types among lands you control, draw a card.
    """, "2/4")
    private val sphinx = Cards.invent("Sphinx of Parted Fates", "{3}{U}{U}", "Creature — Sphinx", """
        Flying, ward {2}
        Domain — Whenever this creature deals combat damage to a player, reveal the top X cards of your library, where X is the number of basic land types among lands you control. An opponent separates those cards into two piles. Put one pile into your hand and the other into your graveyard. (Piles can be empty.)
    """, "5/5")

    /** Two basic land types (Swamp, Forest): a creature card of mana value 2 returns to the battlefield, one of 3 goes to the hand. */
    private fun Scenario.castThornback(chosen: String) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Forest", "Forest", "Forest")
        alice.hand(thornback)
        alice.graveyard(sprout, ogre)
        alice.casts(thornback)
        resolveAll()
        answer(Answer.Targets(listOf(target(chosen))))
        resolveAll()
    }

    // CR 305.6, 608.2h (the mana value compared with the domain count as the trigger resolves)
    @Test
    fun thornbackReturnsACheapCardToTheBattlefield() = scenario {
        castThornback(sprout)
        expect {
            alice.battlefield has thornback
            alice.battlefield has sprout
            alice.graveyard has ogre
        }
    }

    // CR 305.6: "Otherwise, put it into your hand."
    @Test
    fun thornbackPutsACostlierCardIntoTheHand() = scenario {
        castThornback(ogre)
        expect {
            alice.hand has ogre
            alice.graveyard has sprout
            alice.battlefield lacks ogre
        }
    }

    /** Ilsa hits; the top X cards are looked at, one kept on top, the rest go to the bottom; with all five basic land types, a card is drawn. */
    private fun Scenario.ilsaHits(vararg lands: String) {
        alice.battlefield(ilsa, *lands)
        alice.library(*pages.toTypedArray())
        hits(ilsa)
    }

    // CR 401.4, 305.6 (five basic land types: draw the card kept on top)
    @Test
    fun ilsaWithFiveTypesDrawsTheKeptCard() = scenario {
        ilsaHits("Plains", "Island", "Swamp", "Mountain", "Forest")
        val look = pending as Decision.ChooseObjects
        assertTrue(look.options.size == 5, "looks at the top five cards: ${look.options}")
        alice.chooses(pages[3])
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(pages[3])
            alice.library.names.first() shouldBe pages[5]
            alice.library.size shouldBe 5
        }
    }

    // CR 305.6: four basic land types are not five
    @Test
    fun ilsaWithFourTypesKeepsTheCardOnTop() = scenario {
        ilsaHits("Plains", "Island", "Swamp", "Forest")
        val look = pending as Decision.ChooseObjects
        assertTrue(look.options.size == 4, "looks at the top four cards: ${look.options}")
        alice.chooses(pages[2])
        resolveAll()
        expect {
            alice.hand.isEmpty()
            alice.library.names.first() shouldBe pages[2]
            alice.library.size shouldBe 6
        }
    }

    /** With three players, Alice picks which opponent separates the revealed cards (Carol, not the player dealt damage); Alice picks a pile. */
    // CR 701.20 (reveal), 800.4 (a choice of opponent made by the controller)
    @Test
    fun sphinxLetsAChosenOpponentSeparateThePiles() = scenario(players = 3) {
        alice.battlefield(sphinx, "Island", "Plains", "Swamp")
        alice.library(pages[0], pages[1], pages[2], pages[3])
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(sphinx, defender = "bob")
        advanceTo(TurnStep.DeclareBlockers)
        if (state.priority.holder == null) bob.blocks()
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { bob.life shouldBe 15 }
        val who = pending as Decision.ChoosePlayer
        assertTrue(who.player == alice.id, "Alice chooses the opponent: $who")
        answer(Answer.Player(carol.id))
        resolveAll()
        val split = pending as Decision.ChooseObjects
        assertTrue(split.player == carol.id, "Carol separates the piles: $split")
        assertTrue(split.options.size == 3, "three cards revealed: ${split.options}")
        carol.chooses(pages[0])
        alice.answers(Answer.Option("second"))
        resolveAll()
        expect {
            alice.hand.names.toSet() shouldBe setOf(pages[1], pages[2])
            alice.graveyard.names shouldBe listOf(pages[0])
            alice.library.names shouldBe listOf(pages[3])
        }
    }
}
