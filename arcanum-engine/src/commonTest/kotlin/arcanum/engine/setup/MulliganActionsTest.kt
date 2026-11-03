package arcanum.engine.setup

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardDefinition
import arcanum.api.Mechanic
import arcanum.api.Players
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Actions "any time you could mulligan" (CR 103.5b) and effects that change the starting hand size (CR 103.5, 103.5a). */
class MulliganActionsTest {
    /** "Any time you could mulligan and this card is in your hand, you may draw a card." (written by hand from the parsed "draw
     * a card"; `casting/ReadLaneWordingsScenariosTest` plays the parsed wording) */
    private val augury = run {
        val base = Cards.definition(Cards.invent("Powdered Augury Base", "{3}", "Artifact", "When this artifact enters, draw a card."))
        val face = base.faces.single()
        val draw = (face.abilities.single() as Ability.Triggered).instructions
        Cards.define("Powdered Augury", CardDefinition(base.layout, listOf(face.copy(name = "Powdered Augury",
            abilities = listOf(Ability.Static(listOf(Mechanic.WhenYouCouldMulligan(draw)), Affected.Objects(arcanum.api.Objects.Self),
                functionsIn = listOf(Zone.Hand)))))))
    }

    /** A conspiracy: "Your starting hand size is increased by one." (written by hand) */
    private val largesse = run {
        val base = Cards.definition(Cards.invent("Opening Largesse Base", null, "Conspiracy", ""))
        val face = base.faces.single()
        Cards.define("Opening Largesse", CardDefinition(base.layout, listOf(face.copy(name = "Opening Largesse",
            abilities = listOf(Ability.Static(listOf(Mechanic.StartingHandSize(1)), Affected.Players(Players.You),
                functionsIn = listOf(Zone.Command)))))))
    }

    private val deck = cards(listOf(augury, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp"), "Swamp", "Undead Soothsayer")
    private val mountains = cards(listOf("Mountain"), "Mountain", "Glint-Tusk Corsair")

    // CR 103.5b: where the player would declare, they may take the action; then they declare
    @Test
    fun actionWhenYouCouldMulligan() = scenario(start = table(deck, mountains)) {
        random(inOrder, inOrder)
        startGame(StartOptions(startingPlayer = alice.id))
        val options = decision<Decision.ChooseOption>().options
        assertEquals(listOf(Setup.KEEP, Setup.MULLIGAN, "${Setup.USE} 1: $augury"), options)
        answer(Answer.Option(options.last()))
        expect { alice.hand.size shouldBe 8 }
        // still in the hand: offered again before the declaration
        assertTrue(decision<Decision.ChooseOption>().options.size == 3)
        keep(alice)
        keep(bob)
        expect { alice.hand.size shouldBe 8 }
    }

    // CR 103.5b: not only in the first round: after a mulligan, the new hand's card offers it again
    @Test
    fun alsoAfterAMulligan() = scenario(start = table(deck, mountains)) {
        random(inOrder, inOrder, inOrder)
        startGame(StartOptions(startingPlayer = alice.id))
        mulligan(alice)
        keep(bob)
        bottom(alice, "Swamp")
        val options = decision<Decision.ChooseOption>().options
        assertTrue(options.any { it.startsWith(Setup.USE) })
    }

    // CR 103.5a: a player's own starting hand size (a variant's modifier)
    @Test
    fun ownStartingHandSize() = scenario(start = table(deck, mountains)) {
        random(inOrder, inOrder)
        startGame(StartOptions(startingPlayer = alice.id, handSizes = mapOf(bob.id to 5)))
        keep(alice)
        keep(bob)
        expect {
            alice.hand.size shouldBe 7
            bob.hand.size shouldBe 5
        }
    }

    // CR 103.5: an effect in the command zone increases the starting hand size, for mulligans too
    @Test
    fun effectChangesStartingHandSize() = scenario(start = table(deck, mountains)) {
        put(bob, listOf(largesse), Zone.Command)
        random(inOrder, inOrder, inOrder)
        startGame(StartOptions(startingPlayer = alice.id))
        keep(alice)
        expect { bob.hand.size shouldBe 8 }
        mulligan(bob)
        expect { bob.hand.size shouldBe 8 }
    }
}
