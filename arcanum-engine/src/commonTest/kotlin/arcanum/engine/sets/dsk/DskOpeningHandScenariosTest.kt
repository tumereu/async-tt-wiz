package arcanum.engine.sets.dsk

import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.setup.StartOptions
import arcanum.engine.setup.cards
import arcanum.engine.setup.inOrder
import arcanum.engine.setup.keep
import arcanum.engine.setup.startGame
import arcanum.engine.setup.table
import kotlin.test.Test
import kotlin.test.assertEquals

/** Duskmourn's Leylines: an opening-hand action (CR 103.6a). */
class DskOpeningHandScenariosTest {
    private val leyline = Cards.invent("Leyline of the Hollow Halls", "{2}{B}{B}", "Enchantment",
        "If this card is in your opening hand, you may begin the game with it on the battlefield.")
    private val alices = cards(listOf(leyline), "Swamp", "Elvish Seer")
    private val bobs = cards(emptyList(), "Mountain", "Elvish Seer")

    // CR 103.6a: after mulligans, starting with the starting player, the card may be put onto the battlefield
    @Test
    fun beginsTheGameOnTheBattlefield() = scenario(start = table(alices, bobs)) {
        random(inOrder, inOrder)
        startGame(StartOptions(startingPlayer = alice.id))
        keep(alice)
        keep(bob)
        val offer = decision<Decision.ChooseObjects>()
        assertEquals(alice.id, offer.player)
        answer(Answer.Objects(offer.options))
        expect { card(leyline).zone shouldBe Zone.Battlefield }
    }

    // CR 103.6a: "you may": declined, it stays in the hand
    @Test
    fun mayStayInTheHand() = scenario(start = table(alices, bobs)) {
        random(inOrder, inOrder)
        startGame(StartOptions(startingPlayer = alice.id))
        keep(alice)
        keep(bob)
        answer(Answer.Objects(emptyList()))
        expect { card(leyline).zone shouldBe Zone.Hand }
    }
}
