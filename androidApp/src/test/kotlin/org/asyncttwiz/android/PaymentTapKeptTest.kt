package org.asyncttwiz.android

import arcanum.engine.decision.Decision
import org.asyncttwiz.automatum.boundary.CastAnswer
import org.asyncttwiz.automatum.boundary.SharedAnswer
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * STK-049, STK-024, TBL-118 (review 147a9c5af6): a tap on a mana source while paying on the table is never lost. Each tap is worked
 * out where the flow runs (off the main thread in the app); before, a tap made meanwhile did nothing, and a tap whose work ended
 * after a new board arrived (the other player's event, the server's answer) was thrown away. Here the work is held until the test
 * lets it through, as a slow worker thread would.
 */
class PaymentTapKeptTest {
    /** The work the controller asked for, waiting to be let through in order. */
    private val waiting = ArrayDeque<() -> Unit>()
    private val shaken = mutableListOf<String>()

    private fun letThrough() { while (waiting.isNotEmpty()) waiting.removeFirst()() }

    /** Alice's table at her Ember Tollkeeper's "you may pay {1}" (STK-168), Autotap's Mountain paying it, her flows' work held. */
    private fun paying(): Triple<EngineGame, SharedFlowController, String> {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(EngineGameFixture.Choices.TOLLKEEPER, "Mountain"))))
        kit.opening()
        kit.playLand()
        val keeper = kit.atw(kit.hand(EngineGameFixture.Choices.TOLLKEEPER).first())
        assertTrue(kit.toolkit.cast.start(keeper), "the Tollkeeper is cast: ${kit.toolkit.error}")
        kit.playOn("the Tollkeeper's question", rounds = 40) {
            val s = kit.state()
            if (s.stack.isNotEmpty() && s.priority.holder == kit.me && s.pending == null)
                (kit.alice.device.status as? GameDevice.Status.Stopped)?.let { kit.alice.act(it.assumed) } ?: kit.alice.pass()
            kit.state().pending?.decision is Decision.May
        }
        kit.settle()
        val f = kit.scenario.f
        val game = EngineGame(f.game.id, f.a, f.base, kit.scenario.sim.snapshot(f.a), worker = HostWorker)
        assertIs<Decision.May>(game.shown?.engine?.pending?.decision, "the Tollkeeper asks")
        val flows = SharedFlowController(game.me, { game.play() }, { }, engineSend = { null }, shake = { shaken += it },
            port = LocalSharedPort(game.me) { work: () -> SharedAnswer, done: (SharedAnswer) -> Unit -> waiting.addLast { done(work()) } })
        flows.load(game.play())
        letThrough()
        val mountain = game.shown!!.engine.battlefield.first { game.shown!!.engine.obj(it)?.definition?.faces?.first()?.name == "Mountain" }
            .let { (it as arcanum.engine.state.ObjectId.Card).card.value }
        assertTrue(flows.paying && flows.paid, "the slim bar with Autotap's Mountain")
        assertEquals(setOf(mountain), flows.paySources())
        return Triple(game, flows, mountain)
    }

    // STK-049, STK-024: the Mountain tapped out, and tapped again while that is still worked out: the second tap waits for the
    // first and is taken then, so it pays again
    @Test fun aTapWhileTheLastIsWorkedOutIsTakenAfterIt() {
        val (_, flows, mountain) = paying()
        assertTrue(flows.tapSource(mountain))
        assertTrue(flows.busy, "the first tap is being worked out")
        assertTrue(flows.tapSource(mountain), "the second tap is taken")
        letThrough()
        assertTrue(!flows.busy)
        assertEquals(setOf(mountain), flows.paySources(), "out and back in")
        assertTrue(flows.paid)
        assertTrue(shaken.isEmpty())
    }

    // STK-049, STK-024: a new board asking the same payment arrives while the tap is worked out: the tap is worked out again there
    // and applies (before, its result was thrown away and the tap did nothing)
    @Test fun aTapOutlivesANewBoardAskingTheSamePayment() {
        val (game, flows, mountain) = paying()
        val before = flows.view
        assertTrue(flows.tapSource(mountain))
        flows.load(game.play())
        assertTrue(flows.view !== before, "the panel shows the new board's flow")
        letThrough()
        assertTrue(flows.paySources().isEmpty(), "the Mountain tapped out: ${flows.paySources()}")
        assertTrue(!flows.paid)
        assertTrue(AppLog.text().lines().any { "worked out again there" in it }, "logged")
        assertTrue(shaken.isEmpty())
    }

    // TBL-118, STK-049: the payment is gone by the time the tap is worked out: the tap can't change it any more, so its card shakes
    // instead of nothing happening
    @Test fun aTapWhosePaymentIsGoneShakesItsCard() {
        val (_, flows, mountain) = paying()
        assertTrue(flows.tapSource(mountain))
        flows.show(null)
        letThrough()
        assertEquals(listOf(mountain), shaken)
        assertTrue(AppLog.text().lines().any { "source $mountain not taken" in it }, "logged")
    }

    // STK-024: the same in a cast's payment (Autotap off, nothing chosen first): a second tap on the Mountain while the first is
    // worked out waits for it and is taken then
    @Test fun aCastPaymentTapWhileTheLastIsWorkedOutIsTakenAfterIt() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(EngineGameFixture.Choices.BURST, "Mountain"))))
        kit.opening()
        kit.playLand()
        val f = kit.scenario.f
        val game = EngineGame(f.game.id, f.a, f.base, kit.scenario.sim.snapshot(f.a), worker = HostWorker)
        val cast = CastController(game.me, { game.play() }, { }, shake = { shaken += it }, engineSend = { null },
            port = LocalCastPort(game.me) { work: () -> CastAnswer, done: (CastAnswer) -> Unit -> waiting.addLast { done(work()) } })
        cast.autotap = false
        val s = game.shown!!.engine
        fun named(ids: List<arcanum.engine.state.ObjectId>, name: String) = ids.first { s.obj(it)?.definition?.faces?.first()?.name == name }
            .let { (it as arcanum.engine.state.ObjectId.Card).card.value }
        assertTrue(cast.start(named(s.player(arcanum.engine.state.PlayerId(game.me)).hand, EngineGameFixture.Choices.BURST)))
        letThrough()
        assertTrue(cast.answer(arcanum.engine.decision.Answer.Number(1)))
        letThrough()
        val bob = arcanum.engine.state.TargetRef.Player(arcanum.engine.state.PlayerId(f.b))
        assertTrue(cast.answer(kotlin.test.assertNotNull(cast.pick, "the target next").answer(listOf(bob))))
        letThrough()
        assertTrue(cast.paying && cast.paySources().isEmpty(), "at the payment with nothing chosen")
        val mountain = named(s.battlefield, "Mountain")
        assertTrue(cast.tapSource(mountain))
        assertTrue(cast.busy, "the first tap is being worked out")
        assertTrue(cast.tapSource(mountain), "the second tap is taken")
        letThrough()
        assertTrue(cast.paySources().isEmpty(), "in and back out: ${cast.paySources()}")
        assertTrue(cast.tapSource(mountain))
        letThrough()
        assertEquals(setOf(mountain), cast.paySources())
        assertTrue(shaken.isEmpty())
    }
}
