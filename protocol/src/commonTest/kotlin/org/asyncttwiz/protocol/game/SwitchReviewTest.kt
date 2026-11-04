package org.asyncttwiz.protocol.game

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.setup.GameResult
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Adversarial review of the switch-over to engine-state games (I-D56 step D2, 2026-11-04,
 * `~/projects/atw-cache/switch-review-2026-11-04.md`): each finding's reproduction at protocol level, ignored with its
 * number until fixed.
 */
class SwitchReviewTest {
    private fun hand(model: GameModel, player: PlayerId, name: String): ObjectId =
        model.engine.player(player).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    // switchreview #4, I-D56 §7, §11, TRN-065, LOB-111: Bob burns Alice out while she is away. Her Auto-skip doesn't
    // stop at the assumed pass of a spell she can't answer (Forests only), so the main line reaches the rules' result
    // without any next stop for her: nobody's move, no notification (the game-over notice waits for the result's
    // confirmation, which waits for her local head). Expected: the result past her local head is a stop for her (she
    // must see it before it is final), so the server tells her.
    @Test
    fun aResultReachedPastTheLosersLocalHeadIsAStopForThem() {
        val sim = DeviceSim(EngineGames(3, List(40) { "Forest" } to (List(20) { "Mountain" } + List(20) { "Storm Spark" }), life = 3))
        val alice = sim.alice; val bob = sim.bob
        sim.start(bob)
        assertIs<GameDevice.Acted.Sent>(sim.device(bob).act(sim.keep(bob)))
        sim.settle(only = listOf(bob))
        // Alice comes back once, confirms the assumed keep and catches up to Bob's main phase
        val a = sim.load(alice)
        a.play()
        while (true) {
            a.playToStop()
            val stop = a.status as? GameDevice.Status.Stopped ?: break
            a.act(stop.assumed)
        }
        sim.settle()
        assertNull(sim.server.nextStop(alice), "Alice is caught up and waits for Bob")
        // Bob plays a Mountain and casts the lethal Storm Spark at Alice; she is away again, her pass is assumed
        val model = sim.device(bob).model
        assertTrue(model.engine.turn.active == bob && model.engine.turn.step == TurnStep.PrecombatMain)
        assertIs<GameDevice.Acted.Sent>(sim.device(bob).act(Input.PlayLand(bob, hand(model, bob, "Mountain"))))
        val cast = sim.device(bob).act(Input.Cast(bob, hand(sim.device(bob).model, bob, "Storm Spark"))) { m ->
            val d = m.engine.pending?.decision
            if (d == null || d is Decision.Random || d.player != bob) null
            else Engine.options(m.engine, bob).filterIsInstance<Input.Answer>().let { answers ->
                if (d is Decision.Targets) answers.first { (it.answer as Answer.Targets).chosen == listOf(TargetRef.Player(alice)) } else answers.first()
            }
        }
        assertIs<GameDevice.Acted.Sent>(cast)
        sim.settle(only = listOf(bob))
        val over = assertIs<GameDevice.Status.Over>(sim.device(bob).status)
        assertEquals(GameResult.Won(bob), over.result)
        assertTrue(!over.confirmable, "You won · waiting for Alice to catch up")
        assertNotNull(Engine.result(sim.server.state().engine))
        // The server's view of Alice: nothing waits for her, so she is never told
        assertNotNull(sim.server.nextStop(alice), "the result Alice hasn't seen is something waiting for her")
    }
}
