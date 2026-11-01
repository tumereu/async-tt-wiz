package arcanum.engine.review

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.decision.Share
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.GameState
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Review 1: decisions as data (D58): the host stores the state with its pending continuation as JSON; answering the
 * deserialized state gives the same changes. And undo (D59): reverting one outcome restores the state before it.
 */
class Review1ContinuationTest {
    /** Answers the open decision with [answer], first checking a JSON round trip gives the same outcome. */
    private fun Scenario.answerThroughJson(answer: Answer) {
        val d = pending ?: fail("Nothing pending")
        Review1.sameAfterJson(state, Input.Answer(d.id, answer))
        performReverting(Input.Answer(d.id, answer))
    }

    /** A target asked while casting survives JSON. */
    // CR 601.2c, D58
    @Test
    fun castingTargetsThroughJson() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        perform(Input.Cast(alice.id, card("Storm Spark")))
        assertIs<Decision.Targets>(pending)
        answerThroughJson(Answer.Targets(listOf(target("bob"))))
        val pay = decision<Decision.ManaPayment>()
        answerThroughJson(Answer.ManaPayment(pay.suggestion))
        allPass()
        expect { bob.life shouldBe 17 }
    }

    /** Modes chosen while casting survive JSON. */
    // CR 700.2, D58
    @Test
    fun modesThroughJson() = scenario {
        alice.battlefield("Island", "Mountain", "Mountain")
        alice.hand("Spellforge Command")
        alice.library("Forest", "Forest")
        perform(Input.Cast(alice.id, card("Spellforge Command")))
        answerThroughJson(Answer.Modes(listOf(0, 2)))
        answerThroughJson(Answer.Targets(listOf(target("bob"))))
        while (pending is Decision.Targets) answerThroughJson(Answer.Targets(listOf(target("alice"))))
        answerThroughJson(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()
        expect {
            bob.life shouldBe 18
            alice.battlefield has "Treasure Token"
        }
    }

    /** A choice during resolution (Mind Pilfer's "you choose a nonland card") survives JSON. */
    // CR 608.2d, D58
    @Test
    fun resolutionChoiceThroughJson() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Mind Pilfer")
        bob.hand("Storm Spark", "Denial", "Forest")
        alice.casts("Mind Pilfer", target = "bob")
        allPass()
        assertIs<Continuation.Resolving>(state.pending!!.continuation)
        answerThroughJson(Answer.Objects(listOf(card("Denial"))))
        expect {
            bob.graveyard has "Denial"
            alice.life shouldBe 18
        }
    }

    /** Ordering triggers survives JSON. */
    // CR 603.3b, D58
    @Test
    fun triggerOrderThroughJson() = scenario {
        Cards.invent("Review Bear", "{1}{G}", "Creature — Bear", "", "2/2")
        alice.battlefield("Review Bear", "Base Exarch", "Highborn Exarch")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Review Bear")
        assertIs<Continuation.PlacingTriggers>(state.pending!!.continuation)
        answerThroughJson(Answer.Order(listOf(1, 0)))
        expect { stack.size shouldBe 2 }
    }

    /** The legend rule's choice survives JSON. */
    // CR 704.5j, D58
    @Test
    fun legendRuleThroughJson() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Velo, Master of Ceremonies")
        alice.battlefield("Velo, Master of Ceremonies", "Swamp")
        alice.casts("Velo, Master of Ceremonies")
        allPass()
        val d = decision<Decision.LegendRule>()
        answerThroughJson(Answer.Keep(d.among.last()))
        expect { alice.graveyard has "Velo, Master of Ceremonies" }
    }

    /** Dividing combat damage survives JSON. */
    // CR 510.1c, D58
    @Test
    fun combatDamageDivisionThroughJson() = scenario {
        Cards.invent("Review Ox", "{2}{G}", "Creature — Ox", "", "3/3")
        alice.battlefield("Review Ox")
        bob.battlefield("Elvish Hierophant", "Wall of Portents")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Review Ox")
        allPass()
        bob.blocks("Elvish Hierophant" to "Review Ox", "Wall of Portents" to "Review Ox")
        allPass()
        assertIs<Continuation.TurnBased>(state.pending!!.continuation)
        answerThroughJson(Answer.Division(listOf(Share(target("Elvish Hierophant"), 2), Share(target("Wall of Portents"), 1))))
        expect { bob.graveyard has "Elvish Hierophant" }
    }

    /** "As this land enters, you may pay 2 life" asked from a land play (an event continuation) survives JSON. */
    // CR 614.12, D58
    @Test
    fun asEntersChoiceThroughJson() = scenario {
        alice.hand("Gore Vault")
        alice.plays("Gore Vault")
        assertIs<Continuation.Event>(state.pending!!.continuation)
        answerThroughJson(Answer.YesNo(true))
        expect {
            alice.life shouldBe 18
            "Gore Vault".tapped shouldBe false
        }
    }

    /** The cleanup discard survives JSON. */
    // CR 514.1, D58
    @Test
    fun cleanupDiscardThroughJson() = scenario {
        turn(number = 3, step = TurnStep.End)
        alice.hand("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Mountain")
        allPass()
        val d = decision<Decision.ChooseObjects>()
        answerThroughJson(Answer.Objects(listOf(card("Mountain"))))
        expect { alice.hand.size shouldBe 7 }
        assertEquals(1, d.min)
    }

    /** A coin flip asked from the host (D25) survives JSON, and the answer decides the result. */
    // CR 705, D25, D58
    @Test
    fun coinFlipThroughJson() = scenario {
        turn(number = 2, active = bob, step = TurnStep.End)
        alice.battlefield("Mana Reliquary")
        alice.library("Island", "Island")
        bob.library("Island")
        // On a copy (the harness would answer the flip itself): pass until the flip is asked
        val game = state.deepCopy()
        var guard = 20
        while (game.pending?.decision !is Decision.Random && guard-- > 0) {
            val holder = game.priority.holder ?: fail("Stuck: ${game.turn} ${game.pending}")
            Engine.perform(game, Input.PassPriority(holder)).changes.forEach { game.apply(it) }
        }
        val flip = game.pending!!.decision as Decision.Random
        val answer = Input.Answer(flip.id, Answer.CoinFlip(false))
        Review1.sameAfterJson(game, answer)
        Engine.perform(game, answer).changes.forEach { game.apply(it) }
        assertEquals(17, game.player(alice.id).life)
    }

    /** Answering a decision that is no longer the open one is refused. */
    // D58
    @Test
    fun anOldDecisionIdIsRefused() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        perform(Input.Cast(alice.id, card("Storm Spark")))
        val old = pending!!.id
        answer(Answer.Targets(listOf(target("bob"))))
        assertFailsWith<IllegalArgumentException> { perform(Input.Answer(old, Answer.Targets(listOf(target("bob"))))) }
    }

    /** A rich mid-game state (effects, delayed triggers, tokens, a spell copy, counters) survives JSON unchanged. */
    // D58
    @Test
    fun richStateThroughJson() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Mountain", "Mountain", "Mountain", "Seer's Trinket")
        alice.battlefield("Hangar Strider", counters = listOf(CounterKind.PowerToughness(1, 1) to 2))
        alice.hand("Lingering Shades", "Storm Spark", "Flash of Mirroring")
        alice.library("Island", "Island")
        alice.casts("Lingering Shades", paying = listOf("Plains", "Plains#1", "Plains#2"))
        allPass()
        alice.activates("Seer's Trinket", target = "bob")
        allPass()
        alice.casts("Storm Spark", target = "bob", paying = listOf("Mountain"))
        assertEquals(state, Review1.throughJson(state))
    }

    /** Reverting each outcome of a combat with first strike, a death and a dies trigger restores the state before it. */
    // D59
    @Test
    fun eachCombatOutcomeReverts() = scenario {
        Cards.invent("Review Ox", "{2}{G}", "Creature — Ox", "", "3/3")
        alice.battlefield("Review Ox", "Seraphine, Keeper of the Gate")
        bob.battlefield("Crimson Mourner", "Elvish Hierophant")
        advanceTo(TurnStep.DeclareAttackers)
        performReverting(Input.DeclareAttackers(alice.id, listOf(
            arcanum.engine.input.Attack(card("Review Ox"), arcanum.engine.state.TargetRef.Player(bob.id)),
            arcanum.engine.input.Attack(card("Seraphine, Keeper of the Gate"), arcanum.engine.state.TargetRef.Player(bob.id)),
        )))
        performReverting(Input.PassPriority(alice.id))
        performReverting(Input.PassPriority(bob.id))
        performReverting(Input.DeclareBlockers(bob.id, listOf(arcanum.engine.input.Block(card("Elvish Hierophant"), card("Seraphine, Keeper of the Gate")))))
        var guard = 10
        while (guard-- > 0 && state.turn.step != TurnStep.EndOfCombat) {
            val d = pending
            when {
                d is Decision.Targets -> performReverting(Input.Answer(d.id, Answer.Targets(listOf(target("alice")))))
                d != null -> fail("Unexpected $d")
                else -> performReverting(Input.PassPriority(state.priority.holder ?: break))
            }
        }
        expect {
            bob.graveyard has "Elvish Hierophant"
            alice.life shouldBe 19
        }
    }

    /** Every decision type the engine leaves open lists at least one answer in the options, so a host driving the game
     * by options never gets stuck: ordering triggers. */
    // D58, CR 603.3b
    @Ignore("review1 #12: options list no answer for some open decisions (trigger order, legend rule, discarding two, …)")
    @Test
    fun optionsListAnAnswerForATriggerOrder() = scenario {
        Cards.invent("Review Bear", "{1}{G}", "Creature — Bear", "", "2/2")
        alice.battlefield("Review Bear", "Base Exarch", "Highborn Exarch")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Review Bear")
        assertIs<Decision.OrderTriggers>(pending)
        assertTrue(Engine.options(state, alice.id).any { it is Input.Answer })
    }

    /** The same for the legend rule. */
    // D58, CR 704.5j
    @Ignore("review1 #12: options list no answer for some open decisions (trigger order, legend rule, discarding two, …)")
    @Test
    fun optionsListAnAnswerForTheLegendRule() = scenario {
        alice.battlefield("Velo, Master of Ceremonies", "Velo, Master of Ceremonies")
        settle()
        assertIs<Decision.LegendRule>(pending)
        assertTrue(Engine.options(state, alice.id).any { it is Input.Answer })
    }

    /** The same for "discard two cards". */
    // D58, CR 701.9
    @Ignore("review1 #12: options list no answer for some open decisions (trigger order, legend rule, discarding two, …)")
    @Test
    fun optionsListAnAnswerForDiscardingTwo() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Faithless Rummage", "Island", "Island")
        alice.library("Forest", "Forest")
        alice.casts("Faithless Rummage")
        allPass()
        assertIs<Decision.ChooseObjects>(pending)
        assertTrue(Engine.options(state, alice.id).any { it is Input.Answer })
    }
}
