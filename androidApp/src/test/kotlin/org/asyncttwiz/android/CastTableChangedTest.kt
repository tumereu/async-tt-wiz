package org.asyncttwiz.android

import org.asyncttwiz.automatum.boundary.CastAnswer
import org.asyncttwiz.automatum.boundary.CastOp
import org.asyncttwiz.protocol.Fault
import org.asyncttwiz.protocol.Faults
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner, 0.3.2 review (2026-11-23): "The table changed: cast it again." told of your own cast is a fault. Its causes: a shown
 * board taken back without the game going back (the phone's own passes losing a race to the other player's same passes, the
 * game loaded again from a head that overtook its revisions, merge gate 2026-11-24) dropped an open cast at once, as an undo
 * does. Alice opens a Storm Spark's cast; the table is corrected under it, then the game itself goes back.
 */
class CastTableChangedTest {
    private val faults = mutableListOf<Fault>()
    private val sink = Faults.sink.also { Faults.sink = { faults += it } }

    @After fun forget() { Faults.sink = sink; AutomatumBoards.reset() }

    // STK-055, STK-210 (owner 2026-11-23): a board only corrected leaves the open cast to be worked out again, never dropped; an
    // undo, rewind or branch drops it at once (no fault: the game went back)
    @Test fun aCorrectedBoardKeepsTheCastAndOnlyAGameGoingBackDropsIt() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture()))
        kit.opening()
        kit.playLand()
        var takeBacks = 0L
        var rewinds = 0L
        val told = mutableListOf<String?>()
        val cast = CastController(kit.f.a, { kit.alice.play() }, { told += it }, engineSend = kit.routes::send,
            takeBacks = { takeBacks }, rewinds = { rewinds }, lastTakeBack = { "test" })
        assertTrue(cast.start(kit.atw(kit.hand("Storm Spark").first())))
        assertTrue(cast.open, "the cast is open")
        takeBacks++
        cast.follow()
        assertTrue(cast.open, "a corrected board keeps the cast open: $told")
        assertTrue(told.none { it == CastController.TABLE_CHANGED }, "$told")
        rewinds++; takeBacks++
        cast.follow()
        assertTrue(!cast.open, "the game went back: dropped")
        assertTrue(faults.none { it.area == "CastController" }, "an undo's drop is no fault: $faults")
    }

    // STK-055 (owner 2026-11-23): a step of the open cast answered "The table changed: cast it again." is reported as a fault
    @Test fun aTableChangedAnswerIsReported() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture()))
        kit.opening()
        kit.playLand()
        val local = LocalCastPort(kit.f.a) { work: () -> CastAnswer, done: (CastAnswer) -> Unit -> done(work()) }
        var stale = false
        val port = object : CastPort by local {
            override fun ask(request: org.asyncttwiz.automatum.boundary.CastRequest, play: TableBoard?, stacks: () -> List<List<String>>?,
                done: (CastAnswer) -> Unit) {
                if (stale && request.op !is CastOp.Close) done(CastAnswer.Refuse(CastController.TABLE_CHANGED))
                else local.ask(request, play, stacks, done)
            }
        }
        val cast = CastController(kit.f.a, { kit.alice.play() }, {}, engineSend = kit.routes::send, port = port)
        assertTrue(cast.start(kit.atw(kit.hand("Storm Spark").first())))
        stale = true
        cast.answer(cast.pick!!.answer(listOf(arcanum.engine.state.TargetRef.Player(arcanum.engine.state.PlayerId(kit.f.b)))))
        val reported = faults.filter { it.area == "CastController" }
        assertEquals(1, reported.size, "$faults")
        assertEquals("own cast refused: the table changed", reported.single().what)
    }
}
