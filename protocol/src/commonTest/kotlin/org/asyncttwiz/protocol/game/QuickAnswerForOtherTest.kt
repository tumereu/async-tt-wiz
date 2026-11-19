package org.asyncttwiz.protocol.game

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.AssumeReveals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * SYS-063, SYS-206 (owner 2026-11-19, item a819b9ffdd): a one-answer question of a player on Quick that depends on their hidden
 * cards may be answered by the other player's device (it holds those cards, never showing them), so the other player doesn't
 * wait for them; the server takes it only while that player is on Quick and only as the question's one answer. The
 * information rule still holds on a tentative line.
 */
class QuickAnswerForOtherTest {
    private val prod = arcanum.engine.cards.Cards.invent("Mind Prod", "{0}", "Sorcery", "Each opponent discards two cards unless they discard a nonland card.")

    /**
     * Alice starts; Bob's opening hand is one card. Both keep, Bob leaves (his device closed), Alice's device plays on to her
     * main phase. Bob's response mode is [bobMode]; the game's settings [reveals]. Alice casts a Mind Prod: Bob holds only
     * a Forest, so "unless they discard a nonland card" has one answer, no (asked as his cards decide it, I-D18), then he discards it.
     */
    private fun aliceProdsBob(bobMode: ResponseMode, reveals: AssumeReveals): DeviceSim {
        val sim = DeviceSim(EngineGames(3, List(40) { prod } to List(40) { "Forest" }))
        val alice = sim.alice; val bob = sim.bob
        sim.start(alice, handSizes = mapOf(bob to 1))
        sim.reveals = reveals
        sim.keepWhileAway(alice)
        if (bobMode != ResponseMode.AutoSkip) {
            sim.device(bob).settings(PlaySettings(mode = bobMode))
            sim.flush(bob)
            sim.deliver(alice)
            sim.settle(only = listOf(alice))
        }
        assertEquals(bobMode, sim.device(alice).opponentMode)
        val alices = sim.device(alice)
        alices.model.engine.let { assertTrue(it.turn.active == alice && it.turn.step == TurnStep.PrecombatMain && it.priority.holder == alice, "Alice's main") }
        val card = alices.model.engine.player(alice).hand.first()
        assertIs<GameDevice.Acted.Sent>(alices.act(Input.Cast(alice, card)) { m ->
            val d = m.engine.pending?.decision
            if (d !is Decision.Targets || d.player != alice) null
            else Engine.options(m.engine, alice, EnginePreferences.of(m.engine)).filterIsInstance<Input.Answer>()
                .first { (it.answer as Answer.Targets).chosen == listOf(TargetRef.Player(bob)) }
        })
        sim.settle(only = listOf(alice))
        return sim
    }

    private fun discarded(sim: DeviceSim): Boolean = sim.server.state().engine.let { s -> s.player(sim.bob).hand.isEmpty() && s.player(sim.bob).graveyard.isNotEmpty() }

    // SYS-063, SYS-206, LOB-135 (owner 2026-11-19): Bob is on Quick and the game allows assumes that reveal hands: Alice's device
    // answers his one-answer discard for him (an answer of hers for his decision, not assumed), the server takes it, and Alice
    // plays on without waiting for him
    @Test
    fun theOtherPlayersDeviceAnswersAOneAnswerQuestionOfAPlayerOnQuick() {
        val sim = aliceProdsBob(ResponseMode.AutoSkip, AssumeReveals(library = true, hands = true))
        assertTrue(discarded(sim), "Bob's only card discarded: ${sim.server.state().engine.player(sim.bob).hand}")
        val answered = sim.server.line().last { e -> e.engine?.steps.orEmpty().any { s -> (s.input as? Input.Answer)?.answer is Answer.YesNo } }
        assertEquals(sim.alice.value, answered.author, "made on Alice's device")
        assertTrue(answered.engine!!.steps.none { it.assumedBy != null && (it.input as? Input.Answer)?.answer is Answer.YesNo }, "not assumed")
        assertTrue(sim.device(sim.alice).status !is GameDevice.Status.Waiting, "Alice doesn't wait for Bob: ${sim.device(sim.alice).status}")
        assertTrue(sim.leaks.isEmpty(), "${sim.leaks}")
    }

    // SYS-206, SYS-063: with Bob on Normal his discard is his own to make: Alice's device waits for his choice
    @Test
    fun aPlayerNotOnQuickAnswersTheirOwnQuestion() {
        val sim = aliceProdsBob(ResponseMode.Normal, AssumeReveals(library = true, hands = true))
        assertTrue(!discarded(sim), "nothing discarded for Bob")
        val wait = assertIs<GameDevice.Status.Waiting>(sim.device(sim.alice).status)
        assertIs<GameDevice.Wait.Choosing>(wait.reason)
    }

    // SYS-206, TRN-067, LOB-135: on Quick but with the game's default settings (hands not allowed), Alice's line rests on her
    // assumption of Bob's pass, and his discard would show her his card: her device leaves it to his
    @Test
    fun theInformationRuleStillHoldsOnATentativeLine() {
        val sim = aliceProdsBob(ResponseMode.AutoSkip, AssumeReveals())
        assertTrue(!discarded(sim), "nothing discarded for Bob")
        assertIs<GameDevice.Status.Waiting>(sim.device(sim.alice).status)
    }

    // SYS-206 (the server's own rule): the answer for Bob's decision by Alice's device is taken only while Bob is on Quick
    @Test
    fun theServerTakesItOnlyWhileThePlayerIsOnQuick() {
        val server = AsyncServer(EngineGames(3, List(40) { prod } to List(40) { "Forest" }))
        val alice = server.alice; val bob = server.bob
        server.reveals = AssumeReveals(library = true, hands = true)
        server.start(alice, handSizes = mapOf(bob to 1))
        server.keepAndAssumeKeep(alice)
        repeat(40) {
            val s = server.state().engine
            if (s.turn.active == alice && s.turn.step == TurnStep.PrecombatMain && s.priority.holder == alice && s.stack.isEmpty()) return@repeat
            val holder = s.priority.holder!!
            server.act(alice, Input.PassPriority(holder), assumed = holder != alice).getOrThrow()
        }
        val card = server.state().engine.player(alice).hand.first()
        server.act(alice, Input.Cast(alice, card), follow = { m ->
            val d = m.engine.pending?.decision
            if (d !is Decision.Targets || d.player != alice) null
            else Engine.options(m.engine, alice, EnginePreferences.of(m.engine)).filterIsInstance<Input.Answer>()
                .first { (it.answer as Answer.Targets).chosen == listOf(TargetRef.Player(bob)) }
        }).getOrThrow()
        if (server.state().engine.priority.holder == alice) server.act(alice, Input.PassPriority(alice)).getOrThrow()
        server.act(bob, Input.PassPriority(bob)).getOrThrow()
        val discard = assertNotNull(server.state().engine.pending?.decision, "Bob's discard")
        assertEquals(bob, discard.player)
        val only = assertNotNull(WhoActs.quickAnswer(server.state(), discard), "one answer: he can't discard a nonland card")
        fun answer(id: String) = Submission(id, server.head, listOf(Act.Perform(only)))
        server.seen(alice, server.head)
        assertTrue(server.submit(answer("off"), alice).isFailure, "Bob not on Quick: only he decides")
        server.quick = setOf(bob)
        assertTrue(server.submit(answer("on"), alice).isSuccess, "Bob on Quick: his one answer, by Alice's device")
        assertTrue(server.state().engine.player(bob).hand.isEmpty())
    }
}
