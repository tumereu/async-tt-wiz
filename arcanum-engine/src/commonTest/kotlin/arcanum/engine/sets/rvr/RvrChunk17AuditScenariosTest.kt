package arcanum.engine.sets.rvr

import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.gtc.targets
import kotlin.test.Test

/** Ravnica Remastered chunk 17: the one card the audit doubted. */
class RvrChunk17AuditScenariosTest {
    private val spy = Cards.invent("Rvr17 Cellar Spy", "{3}{B}", "Creature — Vampire Rogue", """
        Flying
        When this creature enters, target player reveals cards from the top of their library until they reveal a land card, then puts those cards into their graveyard.
    """, "2/3")
    private val cub = Cards.invent("Rvr17 Idle Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val cow = Cards.invent("Rvr17 Idle Cow", "{1}{G}", "Creature — Ox", "", "2/2")

    // CR 701.20a, 400.7: every revealed card, the land included, goes to the graveyard; the cards below it stay in the library (graveyard order is not significant)
    @Test
    fun revealedCardsAndTheLandGoToTheGraveyardAndTheRestStays() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(spy)
        carol.library(cub, cow, "Forest", cub, cow)
        alice.casts(spy)
        resolveAll()
        targets("carol")
        resolveAll()
        expect {
            carol.graveyard.names.filterNotNull().sorted() shouldBe listOf("Forest", "Rvr17 Idle Cow", "Rvr17 Idle Cub")
            carol.library.names shouldBe listOf("Rvr17 Idle Cub", "Rvr17 Idle Cow")
            bob.graveyard.names shouldBe emptyList()
        }
    }
}
