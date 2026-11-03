package arcanum.engine.epic

import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Epic (CR 702.50): no more spells for the rest of the game, and a copy of the spell at the beginning of each of your upkeeps. */
class EpicTest {
    private val drain = Cards.invent("Epic Endless Drain", "{3}{B}{B}", "Sorcery", """
        Each opponent loses 2 life.
        Epic (For the rest of the game, you can't cast spells. At the beginning of each of your upkeeps, copy this spell except for its epic ability.)
    """.trimIndent())
    private val lash = Cards.invent("Epic Endless Lash", "{3}{R}{R}", "Sorcery", """
        Target player loses 1 life.
        Epic (For the rest of the game, you can't cast spells. At the beginning of each of your upkeeps, copy this spell except for its epic ability. You may choose a new target for the copy.)
    """.trimIndent())

    // CR 702.50a, b: once it resolves its controller can't cast spells; each of their upkeeps a copy, without epic, resolves again
    @Test
    fun copiedEachUpkeepAndNoMoreSpells() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Mountain")
        alice.hand(drain, "Storm Spark")
        alice.library("Swamp", "Swamp", "Swamp")
        bob.library("Island", "Island", "Island")
        alice.casts(drain)
        allPass()
        expect {
            bob.life shouldBe 18
            alice.graveyard has drain
        }
        // CR 702.50b
        assertFailsWith<IllegalArgumentException> { alice.casts("Storm Spark", target = "bob") }
        // The upkeep trigger resolves and puts the copy on the Stack, then the copy resolves
        advanceTo(TurnStep.Upkeep, turn = 3)
        allPass()
        allPass()
        expect { bob.life shouldBe 16 }
        advanceTo(TurnStep.Upkeep, turn = 5)
        allPass()
        allPass()
        expect { bob.life shouldBe 14 }
    }

    // CR 702.50a, 707.10c: the copy may get a new target
    @Test
    fun theCopyMayChooseANewTarget() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(lash)
        alice.library("Mountain", "Mountain")
        bob.library("Island", "Island")
        alice.casts(lash, target = "bob")
        allPass()
        expect { bob.life shouldBe 19 }
        advanceTo(TurnStep.Upkeep, turn = 3)
        allPass()
        var guard = 5
        while (pending != null && guard-- > 0) when (val d = pending) {
            is Decision.May -> answer(Answer.YesNo(true))
            is Decision.Targets -> answer(Answer.Targets(listOf(target("alice"))))
            else -> assertTrue(false, "Unexpected $d")
        }
        allPass()
        expect {
            alice.life shouldBe 19
            bob.life shouldBe 19
        }
    }
}
