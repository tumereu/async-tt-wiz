package arcanum.engine.resolution

import arcanum.api.CounterKind
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Copies of permanent spells: as the copy resolves, it becomes a token permanent (CR 707.10f, 608.3f, 111.13). */
class CopiedPermanentSpellsTest {
    private val mirror = Cards.invent("Mirrorfold Echo", "{1}{U}{U}", "Instant", "Copy target creature spell.")
    private val chronicle = Cards.invent("Tokenwright Chronicle", "{2}", "Artifact",
        "Whenever you create one or more creature tokens, put a story counter on this artifact.")

    /** The copy of Elvish Seer resolves first and becomes a token Elvish Seer; then the card enters. */
    // CR 707.10f, 608.3f
    @Test
    fun aCopyOfACreatureSpellBecomesAToken() = scenario {
        alice.hand("Elvish Seer", mirror)
        alice.castsWithoutCosts("Elvish Seer")
        alice.castsWithoutCosts(mirror, target = "Elvish Seer")
        resolve()
        expect { stack.names shouldBe listOf("Elvish Seer", "copy of Elvish Seer") }
        resolve()
        expect {
            stack.names shouldBe listOf("Elvish Seer")
            alice.battlefield has "Elvish Seer"
        }
        val token = state.battlefield.single()
        assertTrue(state.objOrThrow(token).token, "the copy became a token")
        assertEquals(alice.id, state.objOrThrow(token).controller)
        resolve()
        expect { alice.battlefield.names shouldBe listOf("Elvish Seer", "Elvish Seer") }
        assertEquals(1, state.battlefield.count { state.objOrThrow(it).token })
    }

    /** The token isn't "created": Tokenwright Chronicle's trigger doesn't see it. */
    // CR 111.13, 608.3f
    @Test
    fun theTokenIsNotCreated() = scenario {
        alice.battlefield(chronicle)
        alice.hand("Elvish Seer", mirror, "Chitterstorm")
        alice.castsWithoutCosts("Elvish Seer")
        alice.castsWithoutCosts(mirror, target = "Elvish Seer")
        resolve()
        resolve()
        expect { stack.names shouldBe listOf("Elvish Seer") }
        assertEquals(0, state.objOrThrow(card(chronicle)).counters(CounterKind.Named("story")))
        // Tokens effects create are seen: Chitterstorm and its two storm copies (two spells before it) create three
        resolve()
        alice.castsWithoutCosts("Chitterstorm")
        resolveStack()
        assertEquals(3, state.objOrThrow(card(chronicle)).counters(CounterKind.Named("story")))
    }

    /** A copy of a kicked Pilfering Skydiver was kicked too (CR 707.10): as a token it enters, its "if it was kicked"
     * ability triggers, and it takes the artifact. */
    // CR 707.10, 707.10f
    @Test
    fun theTokenKeepsTheCopiedChoices() = scenario {
        alice.hand("Pilfering Skydiver", mirror)
        bob.battlefield("Tinkered Mite")
        alice.castsWithoutCosts("Pilfering Skydiver", x = 1, kicked = 1)
        alice.castsWithoutCosts(mirror, target = "Pilfering Skydiver")
        resolve()
        resolve()
        // Its only legal target is chosen without asking (P5)
        expect { stack.names shouldBe listOf("Pilfering Skydiver", "ability of Pilfering Skydiver") }
        resolve()
        expect { "Tinkered Mite".controller shouldBe alice.id }
    }

    /** A copy of an instant isn't a permanent spell: it just ceases to exist as it leaves the Stack. */
    // CR 707.10a
    @Test
    fun aCopyOfAnInstantStillJustLeaves() = scenario {
        alice.hand("Storm Spark", "Flash of Mirroring")
        alice.castsWithoutCosts("Storm Spark", target = "bob")
        alice.castsWithoutCosts("Flash of Mirroring", target = "Storm Spark")
        resolve()
        if (pending is arcanum.engine.decision.Decision.May) answer(Answer.YesNo(false))
        resolve()
        expect {
            bob.life shouldBe 17
            alice.battlefield.isEmpty()
            stack.names shouldBe listOf("Storm Spark")
        }
    }
}
