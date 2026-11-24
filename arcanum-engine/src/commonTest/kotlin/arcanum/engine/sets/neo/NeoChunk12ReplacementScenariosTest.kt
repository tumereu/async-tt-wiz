package arcanum.engine.sets.neo

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/** Chunk 12 of NEO: a creature that enters with a counter only if you control a modified creature, and an enchantment that enters with counters, gives
 * modified creatures haste and moves its counters once each turn at sorcery speed. */
class NeoChunk12ReplacementScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val heir = Cards.invent("Heir of the Old Fang", "{2}{G}", "Creature — Snake Samurai",
        "This creature enters with a +1/+1 counter on it if you control a modified creature. (Equipment, Auras you control, and counters are modifications.)", "2/3")
    private val spring = Cards.invent("Invigorating Cold Spring", "{1}{R}{G}", "Enchantment", """
        This enchantment enters with four +1/+1 counters on it.
        Modified creatures you control have haste. (Equipment, Auras you control, and counters are modifications.)
        Remove a +1/+1 counter from this enchantment: Put a +1/+1 counter on target creature you control. Activate only as a sorcery and only once each turn.
    """.trimIndent())
    private val cub = Cards.invent("Hakkaido Cub", "{G}", "Creature — Bear", "", "1/1")
    private val plain = Cards.invent("Meadow Grazer", "{G}", "Creature — Ox", "", "2/2")

    // CR 614.1c, 700.9: no modified creature, no counter
    @Test
    fun heirEntersWithoutACounterWhenNothingIsModified() = scenario {
        alice.battlefield(plain, "Forest", "Forest", "Forest")
        alice.hand(heir)
        alice.casts(heir)
        resolveAll()
        expect { card(heir).counters(p1p1) shouldBe 0 }
    }

    // CR 614.1c, 700.9 (a creature with a counter is modified), 122.1
    @Test
    fun heirEntersWithACounterWhenYouControlAModifiedCreature() = scenario {
        alice.battlefield(cub, "Forest", "Forest", "Forest", counters = listOf(p1p1 to 1))
        alice.hand(heir)
        alice.casts(heir)
        resolveAll()
        expect { card(heir).counters(p1p1) shouldBe 1 }
    }

    // CR 614.1c, 700.9: a modified creature an opponent controls does not count (3 players)
    @Test
    fun heirIgnoresAnOpponentsModifiedCreature() = scenario(players = 3) {
        bob.battlefield(cub, counters = listOf(p1p1 to 1))
        carol.battlefield(plain)
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(heir)
        alice.casts(heir)
        resolveAll()
        expect { card(heir).counters(p1p1) shouldBe 0 }
    }

    // CR 614.12, 700.9, 702.10, 602.5d, 602.5b, 122.1
    @Test
    fun springEntersWithFourCountersGivesModifiedHasteAndMovesACounterOncePerTurn() = scenario {
        alice.battlefield(cub, plain, "Mountain", "Forest", "Forest", sick = true)
        alice.hand(spring)
        alice.casts(spring)
        resolveAll()
        expect { card(spring).counters(p1p1) shouldBe 4 }
        assertFalse(view()[card(cub)]!!.characteristics!!.hasKeyword(Keyword.Haste.kind))
        alice.activates(spring, target = cub)
        resolveAll()
        expect {
            card(spring).counters(p1p1) shouldBe 3
            card(cub).counters(p1p1) shouldBe 1
        }
        assertTrue(view()[card(cub)]!!.characteristics!!.hasKeyword(Keyword.Haste.kind))
        assertFalse(view()[card(plain)]!!.characteristics!!.hasKeyword(Keyword.Haste.kind))
        assertFailsWith<IllegalArgumentException> { alice.activates(spring, target = cub) }
    }
}
