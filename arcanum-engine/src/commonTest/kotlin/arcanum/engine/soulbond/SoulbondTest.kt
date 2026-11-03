package arcanum.engine.soulbond

import arcanum.api.Keyword
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Soulbond (CR 702.95): pairing as either creature enters, abilities of a pair, and unpairing. */
class SoulbondTest {
    private val skystalker = Cards.invent("Bond Skystalker", "{2}{W}", "Creature — Human Soldier", """
        Soulbond
        As long as this creature is paired with another creature, both creatures have flying.
    """.trimIndent(), "2/2")
    private val grovebrute = Cards.invent("Bond Grovebrute", "{1}{G}", "Creature — Beast", """
        Soulbond
        As long as this creature is paired with another creature, each of those creatures gets +2/+2.
    """.trimIndent(), "3/3")
    private val bear = Cards.invent("Bond Plain Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Bond Plain Ox", "{2}{G}", "Creature — Ox", "", "3/3")

    private fun Scenario.paired(name: String): String? = Soulbond.partner(state, card(name))?.let { state.objOrThrow(it).definition?.faces?.first()?.name }

    private fun Scenario.yes() { if (pending is Decision.May) answer(Answer.YesNo(true)) }

    // CR 702.95a: as the soulbond creature enters, you may pair it with another unpaired creature you control
    @Test
    fun pairsAsItEnters() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear)
        alice.hand(skystalker)
        alice.casts(skystalker)
        allPass()
        allPass()
        yes()
        assertEquals(bear, paired(skystalker))
        assertEquals(skystalker, paired(bear))
        expect {
            bear hasKeyword Keyword.Flying
            skystalker hasKeyword Keyword.Flying
        }
    }

    // CR 702.95a: "may": declining leaves both unpaired
    @Test
    fun declining() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear)
        alice.hand(skystalker)
        alice.casts(skystalker)
        allPass()
        allPass()
        answer(Answer.YesNo(false))
        assertEquals(null, paired(skystalker))
        expect { bear lacksKeyword Keyword.Flying }
    }

    // CR 702.95a: whenever another creature you control enters, the unpaired soulbond creature may pair with it
    @Test
    fun pairsWithACreatureEnteringLater() = scenario {
        alice.battlefield("Forest", "Forest", grovebrute)
        alice.hand(bear)
        alice.casts(bear)
        allPass()
        allPass()
        yes()
        assertEquals(bear, paired(grovebrute))
        expect {
            bear.power shouldBe 4
            grovebrute.power shouldBe 5
        }
    }

    // CR 702.95d: a creature is paired with one creature only; a third entering finds both paired
    @Test
    fun onlyOnePartner() = scenario {
        val wolf = Cards.invent("Bond Plain Wolf", "{1}{G}", "Creature — Wolf", "", "2/2")
        alice.battlefield("Forest", "Forest", "Forest", grovebrute, bear)
        alice.hand(ox, wolf)
        alice.casts(ox)
        allPass()
        allPass()
        yes()
        assertEquals(ox, paired(grovebrute))
        assertEquals(null, paired(bear))
        // A creature entering now doesn't trigger: the soulbond creature is paired (CR 702.95a's "both are unpaired")
        move(wolf, Zone.Battlefield)
        settle()
        assertTrue(state.stack.isEmpty() && pending == null)
    }

    // CR 702.95e: the pair ends as either leaves the battlefield
    @Test
    fun leavingUnpairs() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear)
        alice.hand(skystalker)
        alice.casts(skystalker)
        allPass()
        allPass()
        yes()
        move(bear, Zone.Graveyard)
        settle()
        assertEquals(null, paired(skystalker))
        expect { skystalker lacksKeyword Keyword.Flying }
    }
}
