package arcanum.engine.sets.mom

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals

/** MOM chunk 14: transforming cards whose cost has a Phyrexian symbol, a trigger on transforming that looks at the top card, a dies trigger of a
 * Hydra and other nontoken Hydras, and an opponent-by-opponent sacrifice with three players. */
class MomChunk14TransformScenariosTest {
    private val aerialist = Cards.inventFaces("Transform",
        face("Cliffhold Aerialist", "{1}{U}", "Creature — Merfolk Scout", """
            Flying
            {4}{G/P}: Transform this creature. Activate only as a sorcery. ({G/P} can be paid with either {G} or 2 life.)
        """.trimIndent(), "2/1", null, null),
        face("Cliffhold Invader", null, "Creature — Phyrexian Merfolk Scout", """
            Flying
            When this creature transforms into Cliffhold Invader, look at the top card of your library. If it's a land card, you may put it onto the battlefield. If you don't put the card onto the battlefield, put it into your hand.
        """.trimIndent(), "2/4", null, "U"))
    private val gorgonax = Cards.invent("Gorgonax, Engine of Doom", "{3}{G}{W}", "Legendary Creature — Phyrexian Hydra", """
        Reach, lifelink
        Whenever Gorgonax or another nontoken Hydra you control dies, create a 3/3 green and white Phyrexian Hydra creature token with reach and a 3/3 green and white Phyrexian Hydra creature token with lifelink.
    """.trimIndent(), "6/6")
    private val calf = Cards.invent("Moss Hydra Calf", "{1}{G}", "Creature — Hydra", "", "1/1")
    private val lash = Cards.invent("Cinder Lash Tongue", "{R}", "Instant", "Cinder Lash Tongue deals 9 damage to target creature.")
    private val gorehound = Cards.invent("Gleaming Gorehound", "{1}{R}", "Creature — Phyrexian Devil", """
        When this creature dies, each opponent sacrifices an artifact or creature of their choice.
    """.trimIndent(), "3/2")
    private val scarab = Cards.invent("Tin Scarab", "{1}", "Artifact Creature — Insect", "", "1/1")
    private val mite = Cards.invent("Dust Mite", "{1}", "Creature — Insect", "", "1/1")
    private val bolt = Cards.invent("Bolt Wisp", "{R}", "Instant", "Bolt Wisp deals 1 damage to any target.")
    private val wisp = Cards.invent("Quill Wisp", "{1}", "Creature — Spirit", "", "1/1")

    private fun Scenario.libraries() = players.forEach { it.library("Island", "Island", "Island", "Island", "Island", "Island") }
    private fun Scenario.faceName(name: String): String? = view()[card(name, zone = Zone.Battlefield)]?.characteristics?.name

    // CR 107.4f: {G/P} is paid with 2 life without green mana; CR 701.27a; CR 603.10 (transform trigger on the new face): a land on top is put
    // onto the battlefield when the player agrees
    @Test
    fun aerialistTransformsPayingLifeAndPutsALandTopCard() = scenario {
        alice.battlefield(aerialist, "Island", "Island", "Island", "Island")
        alice.library("Forest", "Island")
        bob.library("Island", "Island")
        alice.activates(aerialist)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        expect {
            alice.life shouldBe 18
            alice.battlefield has "Forest"
            alice.hand.size shouldBe 0
        }
        assertEquals("Cliffhold Invader", faceName(aerialist))
    }

    // CR 608.2c: declining the land (or no land on top) puts the card into the hand
    @Test
    fun aerialistPutsANonlandTopCardIntoHand() = scenario {
        alice.battlefield(aerialist, "Island", "Island", "Island", "Island")
        alice.library(bolt, "Island")
        bob.library("Island", "Island")
        alice.activates(aerialist)
        resolveAll()
        expect {
            alice.hand has bolt
        }
    }

    // CR 700.4, 603.10a: the dies trigger sees the creature as it was; another nontoken Hydra of the controller also triggers it
    @Test
    fun gorgonaxMakesTwoTokensWhenAnotherNontokenHydraDies() = scenario {
        alice.battlefield(gorgonax, calf)
        alice.hand(lash)
        alice.battlefield("Mountain")
        libraries()
        alice.casts(lash, targets = listOf(calf))
        resolveAll()
        assertEquals(2, cards("Phyrexian Hydra Token", zone = Zone.Battlefield).size)
        expect { alice.graveyard has calf }
    }

    // CR 101.4: each opponent, in turn order, chooses their own sacrifice; the controller sacrifices nothing
    @Test
    fun gorehoundMakesEachOpponentSacrifice() = scenario(players = 3) {
        alice.battlefield(gorehound, "Mountain")
        alice.hand(lash)
        bob.battlefield(scarab, mite)
        carol.battlefield(wisp)
        libraries()
        alice.casts(lash, targets = listOf(gorehound))
        resolveAll()
        bob.chooses("Dust Mite")
        resolveAll()
        expect {
            bob.battlefield has "Tin Scarab"
            bob.graveyard has "Dust Mite"
            carol.graveyard has "Quill Wisp"
        }
    }
}
