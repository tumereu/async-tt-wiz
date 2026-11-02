package arcanum.engine.review

import arcanum.api.TurnStep
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test

/** Review 1: delayed effects that remember what happened (CR 603.7, 608.2h) and keyword returns. */
class Review1DelayedTest {
    private fun Scenario.settleStack(vararg names: String) {
        var guard = 30
        while ((state.stack.isNotEmpty() || pending != null) && guard-- > 0) {
            when (val d = pending) {
                is Decision.Targets -> answer(Answer.Targets(names.map { target(it) }.filter { it in d.options }.take(d.max ?: 1)
                    .ifEmpty { d.options.take(d.min.coerceAtLeast(1)) }))
                is Decision.May -> answer(Answer.YesNo(true))
                is Decision.OrderTriggers -> answer(Answer.Order(d.triggers.indices.toList()))
                is Decision.ChooseObjects -> answer(Answer.Objects(d.options.take(d.min.coerceAtLeast(1))))
                is Decision.ManaPayment -> answer(Answer.ManaPayment(d.suggestion))
                null -> allPass()
                else -> return
            }
        }
    }

    /** Mana Siphon remembers the countered spell's mana value on the Stack, X included. */
    // CR 202.3e, 608.2h, 603.7
    @Test
    fun manaSiphonUsesTheManaValueOnTheStack() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Doomfire")
        alice.library("Island", "Island")
        bob.battlefield("Island", "Island")
        bob.hand("Mana Siphon")
        bob.library("Island", "Island")
        alice.casts("Doomfire", target = "bob", x = 3)
        alice.passes()
        bob.casts("Mana Siphon", target = "Doomfire")
        settleStack()
        expect { alice.graveyard has "Doomfire" }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        settleStack()
        expect { bob.pool.size shouldBe 4 }
    }

    /** Discarding a hand with madness cards: each is exiled and may be cast. */
    // CR 702.35a
    @Test
    fun madnessFromAWholeHandDiscard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand("Spinning Fate", "Scorching Mudlizard", "Sunning Mudlizard")
        alice.library(*Array(8) { "Island" })
        bob.library(*Array(8) { "Island" })
        alice.casts("Spinning Fate")
        settleStack()
        expect {
            alice.battlefield has "Scorching Mudlizard"
            alice.battlefield has "Sunning Mudlizard"
            alice.hand.size shouldBe 7
        }
    }

    /** Unearth: the creature returns with haste, and is exiled at the end step. */
    // CR 702.84a
    @Test
    fun unearthReturnsAndIsExiled() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.graveyard("Junkyard Cur")
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        alice.activates("Junkyard Cur")
        settleStack()
        expect { alice.battlefield has "Junkyard Cur" }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Junkyard Cur")
        advanceTo(TurnStep.End)
        settleStack()
        expect {
            bob.life shouldBe 18
            alice.exile has "Junkyard Cur"
        }
    }

    /** Harbor Gate Tempestcaller kicked: the next small instant this turn is copied twice. */
    // CR 707.10, 603.7c
    @Test
    fun nextSpellCopiedTwice() = scenario {
        alice.battlefield(*Array(7) { "Island" }, "Mountain")
        alice.hand("Harbor Gate Tempestcaller", "Storm Spark")
        perform(arcanum.engine.input.Input.Cast(alice.id, card("Harbor Gate Tempestcaller")))
        var guard = 5
        while (pending != null && guard-- > 0) when (val d = pending) {
            is Decision.May -> answer(Answer.YesNo(true))
            is Decision.ChooseNumber -> answer(Answer.Number(1))
            is Decision.ManaPayment -> answer(Answer.ManaPayment(d.suggestion))
            else -> kotlin.test.fail("Unexpected $d")
        }
        settleStack()
        alice.casts("Storm Spark", target = "bob", paying = listOf("Mountain"))
        settleStack("bob")
        expect { bob.life shouldBe 11 }
    }

    /** Escape: the creature escapes with a +1/+1 counter, exiling eight other cards. */
    // CR 702.138a, 702.138c
    @Test
    fun escapeWithACounter() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.graveyard("Bull of the Pyre", *Array(8) { "Island" })
        alice.library("Forest", "Forest", "Forest")
        perform(arcanum.engine.input.Input.Cast(alice.id, card("Bull of the Pyre")))
        var guard = 5
        while (pending != null && guard-- > 0) when (val d = pending) {
            is Decision.ChooseObjects -> answer(Answer.Objects(d.options.filter { it != card("Bull of the Pyre") }.take(8)))
            is Decision.ManaPayment -> answer(Answer.ManaPayment(d.suggestion))
            else -> kotlin.test.fail("Unexpected $d")
        }
        settleStack()
        expect {
            "Bull of the Pyre".power shouldBe 5
            alice.exile.size shouldBe 8
        }
    }
}
