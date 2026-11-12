package arcanum.engine.review

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Review 2, hidden information: the state has full information, and what a player looks at or everyone sees is said by
 * a [arcanum.engine.change.Change.Shown] (exactly the cards an effect looks at or reveals), so a host shows each player
 * only what they may see; options never name cards a player can't see (P7). Decisions wait as data and survive JSON
 * (D58).
 */
class Review2HiddenInfoTest {
    private val plusOne = CounterKind.PowerToughness(1, 1)

    /** Cards milled from a library go to a public zone, so "whenever a land card is put into your graveyard" sees the
     * lands among them. */
    // CR 701.17a, 400.2, 603.2
    @Test
    fun milledCardsAreSeenByTriggers() = scenario {
        alice.battlefield("Mirebloat, the Undermuck", "Island", "Island")
        alice.hand("Mind Chill")
        alice.library("Forest", "Storm Spark", "Island", "Swamp")

        alice.casts("Mind Chill", target = "alice")
        // Storm's trigger, then the spell, then Mirebloat's two triggers
        repeat(4) { allPass() }

        expect { alice.graveyard has "Forest"; alice.graveyard has "Storm Spark" }
        // The Swamp stays in the library, unseen
        assertTrue(card("Swamp") !in shownIds())
        expect { "Mirebloat, the Undermuck".counters(plusOne) shouldBe 2 }
    }

    /** "Discards three cards at random": the cards left in hand aren't shown to anyone. */
    // CR 701.9b, 400.2
    @Test
    fun randomDiscardShowsNoCardLeftInHand() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Searing Question")
        alice.library("Island", "Swamp", "Plains")
        bob.hand("Forest", "Storm Spark")
        bob.library("Denial", "Elvish Seer", "Mountain")

        alice.casts("Searing Question")
        allPass()

        expect {
            alice.graveyard.size shouldBe 4
            bob.graveyard.size shouldBe 3
            bob.hand.size shouldBe 2
        }
        assertTrue(state.player(bob.id).hand.none { it in shownIds() }, "cards left in hand aren't shown")
    }

    /** A card discarded in the cleanup step goes to the graveyard, and the other players aren't offered answers naming
     * the discarding player's hidden cards (P7). */
    // CR 514.1, 400.2
    @Test
    fun cleanupDiscardOfAHiddenCard() = scenario {
        turn(number = 2, active = bob, step = TurnStep.End)
        bob.hand("Forest", "Forest", "Forest", "Forest", "Island", "Island", "Island", "Storm Spark")
        allPass()
        val d = decision<Decision.ChooseObjects>()
        assertEquals(bob.id, d.player)
        assertTrue(Engine.options(state, alice.id).none { it is Input.Answer && (it.answer as Answer.Objects).chosen.isNotEmpty() })
        assertTrue(Engine.options(state, bob.id).any { it is Input.Answer })
        bob.answers(Answer.Objects(listOf(card("Storm Spark"))))
        expect { bob.graveyard has "Storm Spark" }
    }

    /** A face-down creature is a 2/2 colourless nameless creature in the view. */
    // CR 708.2a
    @Test
    fun aFaceDownCreatureIsATwoTwo() = scenario {
        bob.battlefield("Barkwood Pathfinders", faceDown = true)
        val v = view()[card("Barkwood Pathfinders")]?.characteristics ?: kotlin.test.fail("no characteristics")
        assertEquals(2, v.power)
        assertEquals(2, v.toughness)
        assertEquals(listOf(CardType.Creature), v.types)
        assertTrue(v.colors.isEmpty())
    }

    /** A face-down creature can be targeted and dealt lethal damage like any 2/2. */
    // CR 708.2a, 704.5g
    @Test
    fun aFaceDownCreatureDies() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Barkwood Pathfinders", faceDown = true)
        alice.casts("Storm Spark", target = "Barkwood Pathfinders")
        allPass()
        expect { bob.graveyard has "Barkwood Pathfinders" }
    }

    /** A face-down permanent that leaves the battlefield is revealed (CR 708.9): face up in the graveyard. */
    // CR 708.9
    @Test
    fun aFaceDownPermanentLeavingIsRevealed() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Barkwood Pathfinders", faceDown = true)
        alice.casts("Storm Spark", target = "Barkwood Pathfinders")
        allPass()
        assertFalse(state.objOrThrow(card("Barkwood Pathfinders", zone = Zone.Graveyard)).faceDown)
    }

    /** Cascade reveals the cards it exiles, not the whole library. */
    // CR 702.85a
    @Test
    fun cascadeRevealsOnlyTheCardsItExiles() = scenario {
        alice.battlefield("Forest", "Island", "Island")
        alice.hand("Splintered Envoy")
        alice.library("Forest", "Tinkered Mite", "Island", "Swamp")

        alice.casts("Splintered Envoy")
        allPass()
        val shown = shownIds().toSet()
        assertTrue(card("Island", zone = Zone.Library) !in shown, "the Island below the hit wasn't revealed: $shown")
        assertTrue(card("Swamp") !in shown)
    }

    /** Twining Augur reveals only the top card. */
    // CR 701.20a
    @Test
    fun revealingTheTopCardShowsThatCardOnly() = scenario {
        alice.battlefield("Forest", "Island")
        alice.hand("Twining Augur")
        alice.library("Island", "Storm Spark")

        alice.casts("Twining Augur")
        allPass()
        allPass()

        assertEquals(1, shownIds().size, "only the top card is revealed")
        expect { alice.battlefield has "Island" }
        assertTrue(card("Storm Spark") !in shownIds())
    }

    /** Scry 2 shows its player the top two cards only. */
    // CR 701.22a
    @Test
    fun scryShowsTheTopCardsOnly() = scenario {
        alice.battlefield("Island")
        alice.hand("Faerie Augur")
        alice.library("Forest", "Island", "Swamp")

        alice.casts("Faerie Augur")
        allPass()
        allPass()
        // The scry's choices: the cards are shown as the step is done
        val top = setOf(card("Forest"), card("Island", zone = Zone.Library))
        var guard = 5
        while (pending != null && guard-- > 0) perform(Engine.options(state, alice.id).first { it is Input.Answer })
        assertEquals(top, shownIds().toSet())
        assertEquals(listOf(alice.id), shownSoFar().map { it.to }.distinct())
    }

    /** Spy's Trinket looks at one card at random: the random pick comes first, then only that card is shown. */
    // CR 701.20c, D25
    @Test
    fun lookingAtARandomCardShowsThatCardOnly() = scenario {
        alice.battlefield("Spy's Trinket")
        bob.hand("Storm Spark", "Forest", "Denial")

        alice.activates("Spy's Trinket", target = "bob")
        allPass()
        assertEquals(1, shownIds().size)
        assertTrue(shownIds().single() in state.player(bob.id).hand)
    }

    /** Seer's Trinket looks at the top card of bob's library: only that card is shown, and only to alice. */
    // CR 701.20c
    @Test
    fun lookingAtTheTopCardShowsThatCardOnly() = scenario {
        alice.battlefield("Seer's Trinket")
        bob.library("Storm Spark", "Forest")

        alice.activates("Seer's Trinket", target = "bob")
        allPass()
        assertEquals(listOf(card("Storm Spark")), shownIds())
        assertEquals(alice.id, shownSoFar().single().to)
    }

    /** A search's choice waits as data: the state with its continuation through JSON gives the same outcome. */
    // D58
    @Test
    fun aSearchSurvivesJson() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Fiendish Mentor")
        alice.library("Forest", "Storm Spark")
        alice.casts("Fiendish Mentor")
        val (game, search) = copyUntil<Decision.ChooseObjects>()
        Review1.sameAfterJson(game, Input.Answer(search.id, Answer.Objects(search.options.take(1))))
    }

    /** Petty Coercion's choice lists only the matching cards of the revealed hand (alice chooses, bob's hand is
     * revealed to her), and the cards left in bob's hand stay where they are. */
    // CR 701.20a, 608.2d
    @Test
    fun aRevealedHandChoiceListsOnlyMatchingCards() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Petty Coercion")
        bob.hand("Elvish Seer", "Forest", "Storm Spark", "Denial")
        alice.casts("Petty Coercion", target = "bob")
        allPass()
        val d = decision<Decision.ChooseObjects>()
        assertEquals(setOf(card("Storm Spark"), card("Denial")), d.options.toSet())
        assertEquals(alice.id, d.player)
    }

    /** While alice searches her library, the decision lists which of her hidden cards match ("a creature card"): bob
     * isn't offered answers that name them, which would tell him what those cards are. */
    // P7, CR 701.23a
    @Test
    fun theOpponentIsntShownTheSearchChoices() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand("Verdant Moon's Apex")
        alice.library("Canopy Visionary", "Island", "Elvish Seer")
        alice.casts("Verdant Moon's Apex", x = 3)
        allPass()
        val d = decision<Decision.ChooseObjects>()
        assertEquals(alice.id, d.player)
        val bobs = Engine.options(state, bob.id)
        assertTrue(bobs.none { it is Input.Answer && (it.answer as? Answer.Objects)?.chosen?.isNotEmpty() == true },
            "bob is offered answers naming alice's hidden cards: $bobs")
    }

    /** Seer of Harvestide: with a known land on top, playing it from the library is offered. */
    // CR 305.1, 401.5
    @Test
    fun aKnownTopLandCanBePlayedFromTheLibrary() = scenario {
        alice.battlefield("Seer of Harvestide")
        alice.library("Forest", "Island")
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.PlayLand>().any { it.card == card("Forest") })
    }

    /** Seer of Harvestide lets alice look at her top card any time: alice may see it, bob may not, and neither may see
     * the card below it. */
    // CR 401.5
    @Test
    fun aCardItsControllerMayLookAtIsVisibleOnlyToThem() = scenario {
        alice.battlefield("Seer of Harvestide")
        alice.library("Forest", "Island")
        settle()
        assertTrue(card("Forest") in Host.visible(state, alice.id))
        assertFalse(card("Forest") in Host.visible(state, bob.id))
        assertFalse(card("Island") in Host.visible(state, alice.id), "only the top card")
    }

    /** Gossamer Coterie looks at bob's hand: alice is shown bob's hand (and only that); the card she picks goes to the
     * bottom of bob's library and bob draws a card only he sees. */
    // CR 701.20c, 121.1
    @Test
    fun lookingAtAHandShowsThatHandOnly() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand("Gossamer Coterie")
        bob.hand("Storm Spark", "Forest", "Denial")
        bob.library("Island", "Swamp")
        val hand = state.player(bob.id).hand.toSet()
        alice.casts("Gossamer Coterie")
        allPass()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("bob"))))
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) alice.chooses("Storm Spark")
        allPass()
        val shown = shownSoFar()
        assertTrue(shown.isNotEmpty())
        assertEquals(alice.id, shown.first().to)
        assertEquals(hand, shown.flatMap { it.ids }.toSet())
        expect { bob.hand.size shouldBe 3 }
        assertTrue(card("Island", owner = bob) !in shownIds(), "the drawn card isn't shown to alice")
    }
}
