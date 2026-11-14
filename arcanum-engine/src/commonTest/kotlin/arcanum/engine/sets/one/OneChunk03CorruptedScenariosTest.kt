package arcanum.engine.sets.one

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.aliceUpkeep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Phyrexia: All Will Be One chunk 03: Corrupted, "an opponent has three or more poison counters" (CR 122.1f) with three players: only an
 * opponent's poison counts, and only three or more; "its controller has …" in an "instead" paragraph (CR 608.2c); "each opponent who …";
 * the chosen creature's total toxic value (CR 702.164c); flashback granted while a condition holds (CR 702.34a).
 */
class OneChunk03CorruptedScenariosTest {
    private val herald = Cards.invent("Herald of the Ichor Host", "{4}{W}{W}", "Creature — Phyrexian Angel", """
        Flying
        Corrupted — As long as an opponent has three or more poison counters, this creature has double strike.
    """.trimIndent(), "4/4")
    private val blight = Cards.invent("Blight of Judgment", "{1}{B}", "Instant", """
        Exile target creature if it has mana value 3 or less.
        Corrupted — Exile that creature instead if its controller has three or more poison counters.
    """.trimIndent())
    private val hasten = Cards.invent("Hasten the Silence", "{1}{U}", "Instant", """
        Counter target spell unless its controller pays {2}.
        Corrupted — Counter that spell instead if its controller has three or more poison counters.
    """.trimIndent())
    private val nourish = Cards.invent("Nourish the Blight", "{3}{B}", "Sorcery", """
        You draw three cards and you lose 3 life.
        Corrupted — Each opponent who has three or more poison counters loses 3 life.
    """.trimIndent())
    private val pit = Cards.invent("Colossus Brood Pit", "{4}{G}{G}", "Enchantment", """
        When this enchantment enters, create two 3/3 green Phyrexian Beast creature tokens with toxic 1. (Players dealt combat damage by them also get a poison counter.)
        Corrupted — At the beginning of your upkeep, if an opponent has three or more poison counters, choose a creature you control, then draw cards equal to its total toxic value.
    """.trimIndent())
    private val stinger = Cards.invent("Ichor Stinger", "{1}{G}", "Creature — Phyrexian Insect",
        "Toxic 2 (Players dealt combat damage by this creature also get two poison counters.)", "1/1")
    private val brood = Cards.invent("Contagion Brood", "{2}{G}", "Sorcery", """
        Create a 3/3 green Phyrexian Beast creature token with toxic 1. (Players dealt combat damage by it also get a poison counter.)
        Corrupted — As long as an opponent has three or more poison counters and this card is in your graveyard, it has flashback {2}{G}. (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """.trimIndent())

    // CR 122.1f, 613.1f: one opponent with three poison counters is enough, the other opponent's two don't matter
    @Test
    fun heraldHasDoubleStrikeWhileOneOpponentHasThreePoison() = scenario(players = 3) {
        alice.battlefield(herald)
        bob.counters(CounterKind.Poison, 2)
        carol.counters(CounterKind.Poison, 3)
        expect { herald hasKeyword Keyword.DoubleStrike.kind }
    }

    // CR 102.2: your own poison counters don't count, nor opponents' under three
    @Test
    fun heraldLacksDoubleStrikeWithoutACorruptedOpponent() = scenario(players = 3) {
        alice.battlefield(herald)
        alice.counters(CounterKind.Poison, 5)
        bob.counters(CounterKind.Poison, 2)
        carol.counters(CounterKind.Poison, 2)
        expect { herald lacksKeyword Keyword.DoubleStrike.kind }
    }

    // CR 608.2c: the target's controller has three poison counters: a creature of mana value 6 is exiled
    @Test
    fun blightExilesAnyCreatureOfACorruptedController() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(blight)
        carol.battlefield(herald)
        carol.counters(CounterKind.Poison, 3)
        alice.casts(blight, target = herald)
        resolveAll()
        expect { herald.zone shouldBe Zone.Exile }
    }

    // CR 608.2c: another opponent's poison counters don't matter: the target's controller has two, mana value 6 stays
    @Test
    fun blightLeavesABigCreatureWhenItsControllerIsNotCorrupted() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(blight)
        carol.battlefield(herald)
        carol.counters(CounterKind.Poison, 2)
        bob.counters(CounterKind.Poison, 3)
        alice.casts(blight, target = herald)
        resolveAll()
        expect { herald.zone shouldBe Zone.Battlefield }
    }

    // CR 608.2c, 118.12a: the spell's controller has three poison counters: countered, and no payment is offered
    @Test
    fun hastenCountersACorruptedPlayersSpellWithoutOfferingPayment() = scenario {
        turn(number = 2, active = bob)
        alice.battlefield("Island", "Island")
        alice.hand(hasten)
        bob.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Island", "Island")
        bob.hand(nourish)
        bob.counters(CounterKind.Poison, 3)
        bob.casts(nourish, paying = listOf("Swamp", "Swamp#1", "Swamp#2", "Swamp#3"))
        bob.passes()
        alice.casts(hasten, target = nourish)
        resolveAll()
        assertEquals(null, pending, "no payment is offered")
        expect { nourish.zone shouldBe Zone.Graveyard }
        assertTrue(cards("Island", owner = bob).none { it.tapped }, "bob paid nothing")
    }

    // CR 118.12a: without three poison counters its controller may pay {2}; declining, the spell is countered
    @Test
    fun hastenOffersPaymentToAPlayerWithFewerPoison() = scenario {
        turn(number = 2, active = bob)
        alice.battlefield("Island", "Island")
        alice.hand(hasten)
        bob.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Island", "Island")
        bob.hand(nourish)
        bob.counters(CounterKind.Poison, 2)
        bob.casts(nourish, paying = listOf("Swamp", "Swamp#1", "Swamp#2", "Swamp#3"))
        bob.passes()
        alice.casts(hasten, target = nourish)
        resolveAll()
        assertTrue(pending != null, "bob is asked whether to pay {2}")
        answer(Answer.YesNo(false))
        resolveAll()
        expect { nourish.zone shouldBe Zone.Graveyard }
    }

    // CR 102.3, 122.1f: of two opponents only the one with three poison counters loses 3 life
    @Test
    fun nourishDrainsOnlyCorruptedOpponents() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(nourish)
        alice.library("Plains", "Plains", "Plains")
        bob.counters(CounterKind.Poison, 3)
        carol.counters(CounterKind.Poison, 2)
        alice.casts(nourish)
        resolveAll()
        expect {
            alice.hand.size shouldBe 3
            alice.life shouldBe 17
            bob.life shouldBe 17
            carol.life shouldBe 20
        }
    }

    // CR 603.4, 702.164b, c: the chosen creature's toxic 2 and a granted toxic 1 total 3: three cards
    @Test
    fun broodPitDrawsTheChosenCreaturesTotalToxicValue() = scenario {
        alice.battlefield(pit, stinger, herald)
        bob.counters(CounterKind.Poison, 3)
        effect(alice, Mechanic.GainAbilities(listOf(Keyword.Toxic(1))), on = listOf(stinger))
        aliceUpkeep("Plains", "Plains", "Plains")
        resolveAll()
        alice.chooses(stinger)
        resolveAll()
        expect { alice.hand.size shouldBe 3 }
    }

    // CR 603.4: no opponent with three poison counters: the upkeep trigger doesn't trigger
    @Test
    fun broodPitDoesNothingWithoutACorruptedOpponent() = scenario {
        alice.battlefield(pit, stinger, herald)
        bob.counters(CounterKind.Poison, 2)
        aliceUpkeep("Plains", "Plains", "Plains")
        resolveAll()
        assertEquals(null, pending)
        expect {
            stack.isEmpty()
            alice.hand.size shouldBe 0
        }
    }

    // CR 702.34a: flashback from the graveyard while an opponent has three poison counters; the card is exiled
    @Test
    fun broodHasFlashbackWhileAnOpponentIsCorrupted() = scenario(players = 3) {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.graveyard(brood)
        carol.counters(CounterKind.Poison, 3)
        alice.casts(brood)
        resolveAll()
        expect {
            brood.zone shouldBe Zone.Exile
            alice.battlefield has "Phyrexian Beast Token"
        }
    }

    // CR 702.34a: no corrupted opponent, no flashback
    @Test
    fun broodHasNoFlashbackOtherwise() = scenario(players = 3) {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.graveyard(brood)
        bob.counters(CounterKind.Poison, 2)
        carol.counters(CounterKind.Poison, 2)
        assertFailsWith<IllegalArgumentException> { alice.casts(brood) }
    }
}
