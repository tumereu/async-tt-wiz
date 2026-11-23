package arcanum.engine.sets.snc

import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.resolveAll
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import kotlin.test.Test
import kotlin.test.assertEquals

/** Streets of New Capenna chunk 02 in play: a spell that reads whether its target was dealt noncombat damage this turn. */
class SncChunk02CasualtyScenariosTest {
    private val sigil = Cards.invent("Snc02 Gutter Mark", "{B}", "Sorcery", """
        Casualty 1 (As you cast this spell, you may sacrifice a creature with power 1 or greater. When you do, copy this spell and you may choose a new target for the copy.)
        Choose target creature or planeswalker. If it was dealt noncombat damage this turn, Snc02 Gutter Mark deals 3 damage to it and you gain 3 life. Otherwise, Snc02 Gutter Mark deals 1 damage to it and you gain 1 life.
    """.trimIndent())
    private val zap = Cards.invent("Snc02 Gutter Zap", "{R}", "Instant", "Snc02 Gutter Zap deals 1 damage to any target.")
    private val cub = Cards.invent("Snc02 Alley Cub", "{1}{B}", "Creature — Rogue", "", "2/2")
    private val brute = Cards.invent("Snc02 Alley Brute", "{3}{G}", "Creature — Ogre", "", "3/3")

    // CR 120.2b (damage from a spell is noncombat), 608.2h
    @Test
    fun undamagedTargetTakesOneAndYouGainOne() = scenario {
        alice.battlefield("Swamp")
        alice.hand(sigil)
        bob.battlefield(brute)
        alice.casts(sigil, target = "Snc02 Alley Brute", kicked = 0)
        resolveAll()
        expect { bob.battlefield has "Snc02 Alley Brute" }
        assertEquals(21, alice.life)
    }

    // CR 120.2b, 608.2h, 704.5g
    @Test
    fun targetDealtNoncombatDamageTakesThreeAndYouGainThree() = scenario {
        alice.battlefield("Swamp", "Mountain")
        alice.hand(sigil, zap)
        bob.battlefield(brute)
        alice.casts(zap, target = "Snc02 Alley Brute")
        resolveAll()
        alice.casts(sigil, target = "Snc02 Alley Brute", kicked = 0)
        resolveAll()
        expect { bob.graveyard has "Snc02 Alley Brute" }
        assertEquals(23, alice.life)
    }

    // CR 702.153a (the casualty copy), 707.10 (the copy is cast with the same choices), 608.2h: the copy resolves first and
    // deals the target's first damage, so the original finds it already dealt noncombat damage
    @Test
    fun casualtyCopyResolvesFirstSoTheOriginalDealsThree() = scenario {
        alice.battlefield("Swamp", cub)
        alice.hand(sigil)
        bob.battlefield(brute)
        alice.casts(sigil, target = "Snc02 Alley Brute", kicked = 1, choosing = listOf("Snc02 Alley Cub"))
        expect { alice.graveyard has "Snc02 Alley Cub" }
        allPass()
        if (pending is Decision.May) answer(Answer.YesNo(false))
        resolveAll()
        expect { bob.graveyard has "Snc02 Alley Brute" }
        assertEquals(24, alice.life)
    }
}
