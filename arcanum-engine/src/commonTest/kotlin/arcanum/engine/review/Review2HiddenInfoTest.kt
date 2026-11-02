package arcanum.engine.review

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Review 2, the host's view (D60): the state is one caller's view, with the cards that caller can't see unknown. An
 * effect that needs a card's identity asks the host to fill in exactly the cards it needs; a card that goes where
 * everyone sees it (a graveyard, the battlefield face up, exile face up) becomes known; nothing the viewer couldn't know
 * stays known (a shuffle makes a library unknown again). Decisions wait as data and survive JSON (D58).
 */
class Review2HiddenInfoTest {
    private val plusOne = CounterKind.PowerToughness(1, 1)

    /** Cards milled from a hidden library go to a public zone: the host fills them in, so "whenever a land card is put
     * into your graveyard" sees the lands among them. */
    // CR 701.17a, 400.2, 603.2
    @Ignore("review2 #1: milled hidden cards stay unknown in the graveyard; land-to-graveyard triggers miss them")
    @Test
    fun milledHiddenCardsAreFilledIn() = scenario {
        alice.battlefield("Mirebloat, the Undermuck", "Island", "Island")
        alice.hand("Mind Chill")
        alice.library("Forest", "Storm Spark", "Island", "Swamp", hidden = true)

        alice.casts("Mind Chill", target = "alice")
        // Storm's trigger, then the spell
        repeat(2) { allPass() }

        assertFalse(unknownIn("Forest", Zone.Graveyard), "a milled card is public")
        assertFalse(unknownIn("Storm Spark", Zone.Graveyard))
        // The Swamp stays in the library, unasked
        assertTrue(card("Swamp") !in filledIds())
        expect { "Mirebloat, the Undermuck".counters(plusOne) shouldBe 2 }
    }

    /** "Discards three cards at random" from a hand the viewer can't see: the discarded cards become known, and only
     * they are asked for, not the cards left in hand. */
    // CR 701.9b, 400.2
    @Ignore("review2 #1: randomly discarded hidden cards stay unknown in the graveyard")
    @Test
    fun randomDiscardFillsOnlyTheDiscardedCards() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Searing Question")
        alice.library("Island", "Swamp", "Plains", hidden = true)
        bob.hiddenHand("Forest", "Storm Spark")
        bob.library("Denial", "Elvish Seer", "Mountain", hidden = true)

        alice.casts("Searing Question")
        allPass()

        expect {
            alice.graveyard.size shouldBe 4
            bob.graveyard.size shouldBe 3
            bob.hand.size shouldBe 2
        }
        val graveyards = state.player(alice.id).graveyard + state.player(bob.id).graveyard
        assertTrue(graveyards.none { state.objOrThrow(it).unknown }, "discarded cards are public")
        assertTrue(state.player(bob.id).hand.none { it in filledIds() }, "cards left in hand aren't asked for")
    }

    /** A card discarded in the cleanup step from a hand the viewer can't see becomes known in the graveyard. */
    // CR 514.1, 400.2
    @Ignore("review2 #1: a hidden card discarded in cleanup stays unknown in the graveyard")
    @Test
    fun cleanupDiscardOfAHiddenCardIsFilledIn() = scenario {
        turn(number = 2, active = bob, step = TurnStep.End)
        bob.hiddenHand("Forest", "Forest", "Forest", "Forest", "Island", "Island", "Island", "Storm Spark")
        allPass()
        val d = decision<Decision.ChooseObjects>()
        assertEquals(bob.id, d.player)
        bob.answers(Answer.Objects(listOf(card("Storm Spark"))))
        assertFalse(unknownIn("Storm Spark", Zone.Graveyard))
    }

    /** A face-down creature the viewer can't see is a 2/2 colourless nameless creature in the view. */
    // CR 708.2a
    @Ignore("review2 #4: an unknown face-down permanent has no characteristics in the view")
    @Test
    fun anUnknownFaceDownCreatureIsATwoTwo() = scenario {
        hiddenBattlefield(bob, "Barkwood Pathfinders")
        val v = view()[card("Barkwood Pathfinders")]?.characteristics ?: kotlin.test.fail("no characteristics")
        assertEquals(2, v.power)
        assertEquals(2, v.toughness)
        assertEquals(listOf(CardType.Creature), v.types)
        assertTrue(v.colors.isEmpty())
    }

    /** An unknown face-down creature can be targeted and dealt lethal damage like any 2/2. */
    // CR 708.2a, 704.5g
    @Ignore("review2 #4: an unknown face-down creature isn't a legal target (no characteristics)")
    @Test
    fun anUnknownFaceDownCreatureDies() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        hiddenBattlefield(bob, "Barkwood Pathfinders")
        alice.casts("Storm Spark", target = "Barkwood Pathfinders")
        allPass()
        expect { bob.graveyard has "Barkwood Pathfinders" }
    }

    /** A face-down permanent that leaves the battlefield is revealed (CR 708.9): the host fills it in, and the card in
     * the graveyard is known. */
    // CR 708.9
    @Ignore("review2 #1: a face-down permanent that leaves isn't revealed (blocked by #4 first: it can't be targeted)")
    @Test
    fun aFaceDownPermanentLeavingIsRevealed() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        hiddenBattlefield(bob, "Barkwood Pathfinders")
        alice.casts("Storm Spark", target = "Barkwood Pathfinders")
        allPass()
        assertFalse(unknownIn("Barkwood Pathfinders", Zone.Graveyard))
    }

    /** Cascade from a hidden library asks for the cards it exiles, not for the whole library. */
    // CR 702.85a, D60
    @Ignore("review2 #2: reveal-until (cascade) asks the host to fill the whole library")
    @Test
    fun cascadeAsksOnlyForTheCardsItExiles() = scenario {
        alice.battlefield("Forest", "Island", "Island")
        alice.hand("Splintered Envoy")
        alice.library("Forest", "Tinkered Mite", "Island", "Swamp", hidden = true)

        alice.casts("Splintered Envoy")
        allPass()
        val asked = filledIds().toSet()
        assertTrue(card("Island", zone = Zone.Library) !in asked, "the Island below the hit wasn't revealed: $asked")
        assertTrue(card("Swamp") !in asked)
    }

    /** Search, then shuffle: once shuffled, nobody knows the library's order, so its cards are unknown again. */
    // CR 701.24a, 701.23a, D60
    @Ignore("review2 #3: a shuffled library keeps its cards' identities (and card ids): the searcher knows its order")
    @Test
    fun aShuffleForgetsTheLibrary() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Fiendish Mentor")
        alice.library("Forest", "Storm Spark", "Island", hidden = true)

        alice.casts("Fiendish Mentor")
        allPass()
        alice.chooses("Storm Spark")

        val library = state.player(alice.id).library
        assertEquals(2, library.size)
        assertTrue(library.all { state.objOrThrow(it).unknown }, "after a shuffle the order is unknown")
    }

    /** Twining Augur reveals only the top card: the host is asked for that one. */
    // CR 701.20a, D60
    @Test
    fun revealingTheTopCardAsksForThatCardOnly() = scenario {
        alice.battlefield("Forest", "Island")
        alice.hand("Twining Augur")
        alice.library("Island", "Storm Spark", hidden = true)

        alice.casts("Twining Augur")
        allPass()
        allPass()

        assertEquals(1, filledIds().size, "only the top card is asked for")
        expect { alice.battlefield has "Island" }
        assertTrue(unknownIn("Storm Spark", Zone.Library))
    }

    /** Scry 2 asks for the top two cards only. */
    // CR 701.22a, D60
    @Test
    fun scryAsksForTheTopCardsOnly() = scenario {
        alice.battlefield("Island")
        alice.hand("Faerie Augur")
        alice.library("Forest", "Island", "Swamp", hidden = true)

        alice.casts("Faerie Augur")
        allPass()
        allPass()
        assertEquals(setOf(card("Forest"), card("Island", zone = Zone.Library)), filledIds().toSet())
    }

    /** Spy's Trinket looks at one card at random: the random pick comes first, then only that card is asked for. */
    // CR 701.20c, D25, D60
    @Ignore("review2 #5: Objects.Random evaluates to no object: Spy's Trinket looks at nothing")
    @Test
    fun lookingAtARandomCardAsksForThatCardOnly() = scenario {
        alice.battlefield("Spy's Trinket")
        bob.hiddenHand("Storm Spark", "Forest", "Denial")

        alice.activates("Spy's Trinket", target = "bob")
        allPass()
        assertEquals(1, filledIds().size)
        assertTrue(filledIds().single() in state.player(bob.id).hand)
    }

    /** Seer's Trinket looks at the top card of bob's library: only that card is asked for, and only by alice. */
    // CR 701.20c, D60
    @Test
    fun lookingAtTheTopCardAsksForThatCardOnly() = scenario {
        alice.battlefield("Seer's Trinket")
        bob.library("Storm Spark", "Forest", hidden = true)

        alice.activates("Seer's Trinket", target = "bob")
        allPass()
        assertEquals(listOf(card("Storm Spark")), filledIds())
        assertEquals(alice.id, hostFillsAsked().single().player)
    }

    /** The host fill of a search waits as data: the state with its continuation through JSON gives the same outcome. */
    // D58, D60
    @Test
    fun aHostFillSurvivesJson() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Fiendish Mentor")
        alice.library("Forest", "Storm Spark", hidden = true)
        alice.casts("Fiendish Mentor")
        val (game, fill) = copyUntil<Decision.HostFill>()
        assertEquals(2, fill.objects.size)
        Review1.sameAfterJson(game, Input.Answer(fill.id, Answer.Filled(fill.objects.map {
            arcanum.engine.decision.FilledObject(it, arcanum.engine.cards.Cards.definition(if (it == card("Forest")) "Forest" else "Storm Spark"))
        })))
    }

    /** An unknown card in a player's own hand is not offered to cast or play, and options don't fail on it. */
    // D60
    @Test
    fun unknownCardsInHandAreNotOffered() = scenario {
        alice.battlefield("Mountain")
        alice.hiddenHand("Storm Spark", "Forest")
        val options = Engine.options(state, alice.id)
        assertTrue(options.none { it is Input.Cast || it is Input.PlayLand }, "$options")
    }

    /** Petty Coercion's choice lists only the matching cards of the revealed hand (alice chooses, bob's hand is
     * revealed to her), and the cards left in bob's hand stay where they are. */
    // CR 701.20a, 608.2d
    @Test
    fun aRevealedHandChoiceListsOnlyMatchingCards() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Petty Coercion")
        bob.hiddenHand("Elvish Seer", "Forest", "Storm Spark", "Denial")
        alice.casts("Petty Coercion", target = "bob")
        allPass()
        val d = decision<Decision.ChooseObjects>()
        assertEquals(setOf(card("Storm Spark"), card("Denial")), d.options.toSet())
        assertEquals(alice.id, d.player)
    }

    /** While alice searches her library, the decision lists which of her hidden cards match ("a creature card"): bob
     * isn't offered answers that name them, which would tell him what those cards are. */
    // D60, CR 701.23a
    @Ignore("review2 #7: options offer the opponent answers to a search over hidden cards, naming the matching ones")
    @Test
    fun theOpponentIsntShownTheSearchChoices() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand("Verdant Moon's Apex")
        alice.library("Canopy Visionary", "Island", "Elvish Seer", hidden = true)
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

    /** Seer of Harvestide: alice may look at her top card any time, so in her view a hidden top card should be filled
     * in; the engine never asks, so the land on top isn't offered. */
    // CR 401.5, D60
    @Ignore("review2 #8: nothing asks the host to fill cards a player may look at any time (top card under Seer of Harvestide)")
    @Test
    fun aHiddenTopLandUnderAlwaysLookIsOffered() = scenario {
        alice.battlefield("Seer of Harvestide")
        alice.library("Forest", "Island", hidden = true)
        alice.passes()
        bob.passes()
        assertTrue(hostFillsAsked().isNotEmpty() || !state.objOrThrow(card("Forest")).unknown, "the top card was never filled")
    }

    /** Gossamer Coterie looks at bob's hand: alice is asked for bob's hand (and only that); the card she picks goes to
     * the bottom of bob's library and bob draws a card he can see — not asked for in alice's view. */
    // CR 701.20c, 121.1, D60
    @Test
    fun lookingAtAHandAsksForThatHandOnly() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand("Gossamer Coterie")
        bob.hiddenHand("Storm Spark", "Forest", "Denial")
        bob.library("Island", "Swamp", hidden = true)
        val hand = state.player(bob.id).hand.toSet()
        alice.casts("Gossamer Coterie")
        allPass()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("bob"))))
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) alice.chooses("Storm Spark")
        allPass()
        val fills = hostFillsAsked()
        assertTrue(fills.isNotEmpty())
        assertEquals(alice.id, fills.first().player)
        assertEquals(hand, fills.flatMap { it.objects }.toSet())
        expect { bob.hand.size shouldBe 3 }
        assertTrue(card("Island", owner = bob) !in filledIds(), "the drawn card isn't asked in alice's view")
    }
}
