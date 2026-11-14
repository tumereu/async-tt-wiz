package arcanum.engine.sets.mh3

import arcanum.api.CounterKind
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.mh2.playOut
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFails

/**
 * The MH3 cards of chunk 14, played: flashback with an extra payment, vanishing's proliferate trigger counted per caster,
 * threshold and ferocious (invented names carry the oracle text of Deep Analysis, Eviscerator's Insight, Dreamtide Whale,
 * Barbarian Ring, Fledgling Dragon and Fanatic of Rhonas).
 */
class Mh3Chunk14CascadeFlashbackProliferateScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val reflection = Cards.invent("Deep Reflection", "{3}{U}", "Sorcery", """
        Target player draws two cards.
        Flashback—{1}{U}, Pay 3 life. (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val insight = Cards.invent("Gruesome Insight", "{1}{B}", "Instant", """
        As an additional cost to cast this spell, sacrifice an artifact or creature.
        Draw two cards.
        Flashback {4}{B} (You may cast this card from your graveyard for its flashback cost and any additional costs. Then exile it.)
    """)
    private val orca = Cards.invent("Dreamtide Orca", "{2}{U}", "Creature — Whale", """
        Vanishing 2 (This creature enters with two time counters on it. At the beginning of your upkeep, remove a time counter from it. When the last is removed, sacrifice it.)
        Whenever a player casts their second spell each turn, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
    """, "7/5")
    private val ring = Cards.invent("Barbarian Fen", null, "Land", """
        {T}: Add {R}. This land deals 1 damage to you.
        Threshold — {R}, {T}, Sacrifice this land: It deals 2 damage to any target. Activate only if there are seven or more cards in your graveyard.
    """)
    private val wyrm = Cards.invent("Fledgling Wyrm", "{2}{R}{R}", "Creature — Dragon", """
        Flying
        Threshold — As long as there are seven or more cards in your graveyard, this creature gets +3/+3 and has "{R}: This creature gets +1/+0 until end of turn."
    """, "2/2")
    private val fanatic = Cards.invent("Fanatic of Ammit", "{1}{G}", "Creature — Snake Druid", """
        {T}: Add {G}.
        Ferocious — {T}: Add {G}{G}{G}{G}. Activate only if you control a creature with power 4 or greater.
        Eternalize {2}{G}{G} ({2}{G}{G}, Exile this card from your graveyard: Create a token that's a copy of it, except it's a 4/4 black Zombie Snake Druid with no mana cost. Eternalize only as a sorcery.)
    """, "1/4")
    private val boon = Cards.invent("Minor Boon", "{W}", "Instant", "You gain 1 life.")
    private val brute = Cards.invent("Tallgrass Brute", "{3}{G}", "Creature — Beast", "", "4/4")
    private val bear = Cards.invent("Tallgrass Bear", "{1}{G}", "Creature — Bear", "", "2/2")

    // CR 702.34a: the flashback cost, with its extra life payment, then it is exiled
    @Test
    fun deepReflectionFlashbackPaysLife() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(reflection)
        alice.library("Island", "Island", "Island", "Island")
        alice.casts(reflection, target = "alice")
        resolveAll()
        expect { alice.hand.size shouldBe 2; alice.graveyard has reflection }
        alice.casts(reflection, target = "alice")
        resolveAll()
        expect {
            alice.hand.size shouldBe 4
            alice.life shouldBe 17
            alice.exile has reflection
        }
    }

    // CR 702.34a, 601.2b: flashback keeps the additional cost, and it can't be paid without something to sacrifice
    @Test
    fun gruesomeInsightFlashbackNeedsAnotherSacrifice() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", bear, brute)
        alice.hand(insight)
        alice.library("Swamp", "Swamp", "Swamp", "Swamp")
        alice.casts(insight, choosing = listOf(bear))
        resolveAll()
        expect { alice.graveyard has bear; alice.graveyard has insight }
        alice.casts(insight, choosing = listOf(brute))
        resolveAll()
        expect {
            alice.graveyard has brute
            alice.exile has insight
            alice.hand.size shouldBe 4
        }
    }

    // CR 601.2b, 702.34a
    @Test
    fun gruesomeInsightFlashbackFailsWithNothingToSacrifice() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(insight)
        assertFailsWith<IllegalArgumentException> { alice.casts(insight) }
    }

    // CR 603.2, 701.34a: "their second spell" counts each caster's own spells (three players)
    @Test
    fun orcaProliferatesOnlyOnAPlayersOwnSecondSpell() = scenario(players = 3) {
        alice.battlefield(orca, bear, "Plains", "Plains", counters = listOf(p1p1 to 1))
        alice.hand(boon, boon)
        bob.battlefield("Plains")
        bob.hand(boon)
        alice.casts(boon)
        alice.passes()
        bob.casts(boon)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
        alice.casts(boon)
        playOut(bear)
        expect { bear.counters(p1p1) shouldBe 2 }
    }

    // CR 207.2c, 602.5b: threshold lets the land be sacrificed only with seven cards in the graveyard
    @Test
    fun barbarianFenNeedsSevenCards() = scenario {
        alice.battlefield(ring, "Mountain")
        alice.graveyard("Island", "Island", "Island", "Island", "Island", "Island")
        assertFailsWith<IllegalArgumentException> { alice.activates(ring, ability = 1, target = "bob") }
    }

    @Test
    fun barbarianFenDealsTwoWithSevenCards() = scenario {
        alice.battlefield(ring, "Mountain")
        alice.graveyard("Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.activates(ring, ability = 1, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 18; alice.graveyard has ring }
    }

    // CR 605.1a, 120.3: the mana ability's damage to its controller
    @Test
    fun barbarianFenHurtsItsController() = scenario {
        alice.battlefield(ring)
        alice.activates(ring)
        expect { alice.life shouldBe 19; alice.pool.size shouldBe 1 }
    }

    // CR 207.2c, 611.3a: threshold gives +3/+3 and a firebreathing ability only while it holds
    @Test
    fun fledglingWyrmNeedsThresholdForBonusAndAbility() = scenario {
        alice.battlefield(wyrm, "Mountain")
        alice.graveyard("Island", "Island", "Island", "Island", "Island", "Island")
        expect { wyrm.power shouldBe 2 }
        assertFails { alice.activates(wyrm) }
    }

    @Test
    fun fledglingWyrmWithThresholdPumpsItself() = scenario {
        alice.battlefield(wyrm, "Mountain")
        alice.graveyard("Island", "Island", "Island", "Island", "Island", "Island", "Island")
        expect { wyrm.power shouldBe 5 }
        alice.activates(wyrm)
        resolveAll()
        expect { wyrm.power shouldBe 6; wyrm.toughness shouldBe 5 }
    }

    // CR 207.2c, 602.5b: ferocious needs a creature with power 4 or greater
    @Test
    fun fanaticAddsFourWithAPowerfulCreature() = scenario {
        alice.battlefield(fanatic, brute)
        alice.activates(fanatic, ability = 1)
        expect { alice.pool.size shouldBe 4 }
    }

    @Test
    fun fanaticCantAddFourWithoutAPowerfulCreature() = scenario {
        alice.battlefield(fanatic, bear)
        assertFailsWith<IllegalArgumentException> { alice.activates(fanatic, ability = 1) }
    }
}
