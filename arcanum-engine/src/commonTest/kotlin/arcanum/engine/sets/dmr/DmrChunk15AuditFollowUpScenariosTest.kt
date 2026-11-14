package arcanum.engine.sets.dmr

import arcanum.api.Color
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFails

/** Dominaria Remastered chunk 15 in play: the cards whose audit doubted the model. */
class DmrChunk15AuditFollowUpScenariosTest {
    private val sprite = Cards.invent("Sunfeather Sprite Two", "{G}", "Creature — Bird", """
        Flying
        {T}: Add one mana of any color.
    """, "0/1")
    private val scrapper = Cards.invent("Ember Scrapper Two", "{R}", "Creature — Human Wizard",
        "{R}, {T}, Exile two cards from your graveyard: This creature deals 2 damage to any target.", "1/1")
    private val glance = Cards.invent("Quickglance Two", "{1}{U}", "Instant",
        "Look at the top four cards of your library. Put one of them into your hand and the rest on the bottom of your library in any order.")
    private val gravelet = Cards.invent("Gravelet Two", "{2}", "Artifact Creature — Construct",
        "{T}, Mill a card: Add {C}. (Activate only as an instant. To mill a card, put the top card of your library into your graveyard.)", "0/1")

    // CR 106.1b, 605.1a: the mana can be of any one of the five colours
    @Test
    fun anyColorManaOffersAllFiveColors() {
        for (color in listOf(Color.White, Color.Blue, Color.Black, Color.Red, Color.Green)) scenario {
            alice.battlefield(sprite)
            alice.activates(sprite, color = color)
            expect { alice.pool.map { it.color } shouldBe listOf<Color?>(color) }
        }
    }

    // CR 602.2, 118.3, 3 players: "your graveyard" is the activator's; other players' graveyards are never used as the cost
    @Test
    fun exileCostComesOnlyFromYourOwnGraveyardWithThreePlayers() = scenario(players = 3) {
        alice.battlefield(scrapper, "Mountain", "Mountain")
        alice.graveyard("Forest", "Island")
        bob.graveyard("Swamp", "Plains", "Forest")
        carol.graveyard("Swamp", "Plains")
        alice.activates(scrapper, target = "carol", choosing = listOf("Forest", "Island"))
        allPass()
        expect {
            carol.life shouldBe 18
            alice.graveyard.size shouldBe 0
            alice.exile.size shouldBe 2
            bob.graveyard.size shouldBe 3
            carol.graveyard.size shouldBe 2
        }
    }

    // CR 602.2, 118.3: one card in your graveyard cannot pay for two, whatever the opponents' graveyards hold
    @Test
    fun exileCostCannotUseOpponentsGraveyard() = scenario(players = 3) {
        alice.battlefield(scrapper, "Mountain", "Mountain")
        alice.graveyard("Forest")
        bob.graveyard("Swamp", "Plains", "Forest")
        assertFails { alice.activates(scrapper, target = "carol") }
    }

    // CR 701.20a, 401.4: one of the four goes to the hand and the other three go to the bottom in the order alice picks
    @Test
    fun lookFourTakeOneRestOnTheBottomInChosenOrder() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(glance)
        alice.library("Forest", "Island", "Swamp", "Mountain", "Plains")
        alice.casts(glance)
        allPass()
        alice.chooses("Swamp")
        alice.chooses("Mountain", "Forest", "Island")
        expect {
            alice.hand.names shouldBe listOf("Swamp")
            alice.library.names shouldBe listOf("Plains", "Mountain", "Forest", "Island")
        }
    }

    // CR 605.1a, 602.1: a cost that mills makes it no mana ability, so it uses the stack; any activated ability is instant speed, so it works in the opponent's turn
    @Test
    fun millCostManaAbilityInTheOpponentsTurn() = scenario {
        alice.battlefield(gravelet)
        alice.library("Forest", "Island")
        bob.library("Forest")
        turn(number = 2, active = bob, priority = alice)
        alice.activates(gravelet)
        expect { alice.graveyard.names shouldBe listOf("Forest"); alice.pool.size shouldBe 0 }
        resolve()
        expect {
            alice.pool.size shouldBe 1
            alice.graveyard.names shouldBe listOf("Forest")
            alice.library.names shouldBe listOf("Island")
        }
    }
}
