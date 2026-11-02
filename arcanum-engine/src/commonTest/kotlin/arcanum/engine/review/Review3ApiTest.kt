package arcanum.engine.review

import arcanum.engine.Engine
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.setup.GameResult
import arcanum.engine.state.GameState
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Review 3: the API as a host uses it: undoing the last batch and choosing differently (D59), cancelling a cast after
 * its costs were paid (D58), conceding while another player's decision is open, and what decisions tell a UI.
 */
class Review3ApiTest {
    private fun GameState.normalized() = deepCopy().also { it.timestampCounter = 0 }

    /** Undo of the last batch (the mana payment) gives back the state before it; another payment is then accepted and
     * gives the same game as choosing it in the first place, timestamps aside (D34). */
    // D59, D58
    @Test
    fun undoTheLastBatchThenChooseDifferently() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Storm Spark")
        perform(Input.Cast(alice.id, card("Storm Spark")))
        alice.answers(Answer.Targets(listOf(target("bob"))))
        val payment = decision<Decision.ManaPayment>()
        val before = state.deepCopy()
        val first = Engine.perform(state, Input.Answer(payment.id, Answer.ManaPayment(listOf(payment.options.filterIsInstance<ManaUse.Ability>()[0]))))
        val game = state.deepCopy()
        first.changes.forEach { game.apply(it) }
        first.changes.asReversed().forEach { game.revert(it) }
        assertEquals(before.normalized(), game.normalized(), "undo restores the state before the batch")
        val other = Input.Answer(payment.id, Answer.ManaPayment(listOf(payment.options.filterIsInstance<ManaUse.Ability>()[1])))
        val second = Engine.perform(game, other)
        second.changes.forEach { game.apply(it) }
        val direct = before.deepCopy()
        Engine.perform(direct, other).changes.forEach { direct.apply(it) }
        // Timestamps of the redone changes differ (the counter never goes back, D34); what the players see doesn't
        fun seen(s: GameState) = s.battlefield.map { s.objOrThrow(it).let { o -> o.id to o.tapped } } + s.stack.map { it to true } +
            s.players.map { it.manaPool.size }.map { arcanum.engine.state.ObjectId.StackItem("pool") to (it == 0) }
        assertEquals(seen(direct), seen(game))
        perform(other)
        expect { stack.names shouldBe listOf("Storm Spark") }
    }

    /** Cancelling Bone Splinters at its mana payment, after the sacrifice was paid and Havoc Imp's trigger waits: the
     * creature is back, nothing waits, the state is as before the cast (D58, CR 601.2). */
    // CR 601.2, D58
    @Test
    fun cancelAfterASacrificeCostLeavesNoTrace() = scenario {
        alice.battlefield("Swamp", "Havoc Imp", "Tinkered Mite")
        alice.hand("Bone Splinters")
        bob.battlefield("Elvish Seer")
        val before = state.deepCopy()
        perform(Input.Cast(alice.id, card("Bone Splinters")))
        var guard = 10
        while (pending !is Decision.ManaPayment && guard-- > 0) {
            when (val d = pending) {
                is Decision.ChooseOption -> answer(Answer.Option(d.options.first { "acrific" in it }))
                is Decision.ChooseObjects -> alice.chooses("Tinkered Mite")
                is Decision.Targets -> alice.answers(Answer.Targets(listOf(target("Elvish Seer"))))
                else -> error("unexpected $d")
            }
        }
        perform(Input.Cancel(alice.id))
        assertEquals(before.normalized(), state.normalized())
        expect { alice.battlefield has "Tinkered Mite" }
    }

    /** bob concedes while alice's search waits for her: the game ends at once and nothing waits (CR 104.3a, 104.2a). */
    // CR 104.3a, 104.2a
    @Test
    fun aConcessionWhileTheOtherPlayersDecisionIsOpen() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Fiendish Mentor")
        alice.library("Forest", "Island")
        alice.casts("Fiendish Mentor")
        allPass()
        assertTrue(pending is Decision.ChooseObjects)
        perform(Input.Concede(bob.id))
        assertEquals(GameResult.Won(alice.id), Engine.result(state))
        assertEquals(null, state.pending)
        assertTrue(Engine.options(state, alice.id).isEmpty())
    }

    /** A "you may" a UI shows reads as text, not as the engine's internal data: Warden's Reclamation's "you may copy this
     * spell" is shown as `You may: Do(effect=CopySpell(objects=Self, …))`. */
    // D58
    @Ignore("review3 #API-1: Decision.May.text of a resolution step is a Kotlin toString of the step")
    @Test
    fun aMaysTextIsReadable() = scenario {
        alice.graveyard("Warden's Reclamation", "Tinkered Mite")
        alice.castsWithoutCosts("Warden's Reclamation", target = "Tinkered Mite")
        resolve()
        val may = decision<Decision.May>()
        assertTrue("Do(effect=" !in may.text && "objects=" !in may.text, "May text: ${may.text}")
    }

    /** The modes of a modal spell carry their text so a UI can show them (Stormforge Charm). */
    // CR 700.2
    @Ignore("review3 #API-2: ModeOption.text is null for the cube's modal spells")
    @Test
    fun modesCarryTheirText() = scenario {
        alice.battlefield("Island", "Mountain")
        alice.hand("Stormforge Charm")
        perform(Input.Cast(alice.id, card("Stormforge Charm")))
        val modes = decision<Decision.Modes>()
        assertTrue(modes.options.all { it.text != null }, "${modes.options}")
    }

    /** Two triggered abilities of one source waiting at once (Vivacity's two "enters" abilities, both conditions met)
     * are told apart by their text in the order decision. */
    // CR 603.3b
    @Ignore("review3 #API-3: TriggerOption.text is null for parsed abilities: two triggers of one source can't be told apart")
    @Test
    fun triggersToOrderCarryTheirText() = scenario {
        alice.battlefield("Mountain", "Mountain", "Forest", "Forest", "Forest")
        alice.hand("Vivacity")
        alice.library("Forest", "Island")
        alice.casts("Vivacity", paying = listOf("Mountain", "Mountain#1", "Forest", "Forest#1", "Forest#2"))
        allPass()
        val order = decision<Decision.OrderTriggers>()
        assertTrue(order.triggers.all { it.text != null } && order.triggers.map { it.text }.distinct().size == 2, "${order.triggers}")
    }

    /** A history line "Storm Spark dealt 3 damage to bob" needs the source as it was: the damage change carries it. */
    // D33
    @Test
    fun damageCarriesItsSourceForHistory() = scenario {
        alice.hand("Storm Spark")
        alice.castsWithoutCosts("Storm Spark", target = "bob")
        resolve()
        val damage = applied.filterIsInstance<Change.Damage>().single().dealt.single()
        assertEquals("Storm Spark", damage.sourceSeen?.characteristics?.name)
        assertEquals(3, damage.amount)
    }

    /** A host answers for the other player (P7, D11): bob answers alice's target choice for her trigger. */
    // D11
    @Test
    fun anotherPlayerAnswersForTheDecider() = scenario {
        alice.battlefield("Crimson Mourner")
        alice.hand("Storm Spark")
        bob.battlefield("Elvish Seer")
        alice.castsWithoutCosts("Storm Spark", target = "Elvish Seer")
        resolve()
        val d = decision<Decision.Targets>()
        assertEquals(alice.id, d.player)
        assertTrue(Engine.options(state, bob.id).any { it is Input.Answer && it.decision == d.id }, "bob is offered answers")
        bob.answers(Answer.Targets(listOf(target("bob"))))
        resolve()
        expect { bob.life shouldBe 19 }
    }

    /** The state between every call goes through JSON: a pending decision's continuation resumes the same (D58). */
    // D58
    @Test
    fun aPendingTriggerOrderSurvivesJson() = scenario {
        alice.battlefield("Crimson Mourner", "Harborside Knifer", "Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Elvish Seer")
        alice.casts("Storm Spark", target = "Elvish Seer")
        allPass()
        assertNotNull(pending)
        Review1.sameAfterJson(state, Engine.options(state, alice.id).first())
    }
}
