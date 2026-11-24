package org.asyncttwiz.android

import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.asyncttwiz.automatum.boundary.CastAnswer
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Owner problem report 4DKZET ("wtf is 'table changed, cast again'"): each tap on the X stepper previews the payment where the
 * cast's flow runs (STK-024). Each preview made a new version of the flow and only four were kept, so after a quick burst of
 * taps the version the panel showed (the X question's own) wasn't kept any more: every later preview and Cast was refused
 * with "The table changed: cast it again." although nothing on the table changed, and the cast couldn't be finished. Here
 * the work is held until the test lets it through, as a slow worker thread does while the taps come faster.
 */
class XPreviewStaysCurrentTest {
    /** The work the controller asked for, waiting to be let through in order. */
    private val waiting = ArrayDeque<() -> Unit>()
    private fun letThrough() { while (waiting.isNotEmpty()) waiting.removeFirst()() }

    @After fun forget() = AutomatumBoards.reset()

    // STK-138, STK-024, STK-023 (4DKZET): X set up and down seven times faster than the previews are worked out, then Cast: X is
    // answered and the cast goes on to its target and resolves; no step is refused as stale
    @Test fun anyNumberOfPreviewsLeaveTheXAnswerable() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = listOf(Choices.BURST, "Mountain"))))
        kit.opening()
        kit.playLand()
        val refused = mutableListOf<String?>()
        val cast = CastController(kit.f.a, { kit.alice.play() }, { refused += it }, engineSend = kit.routes::send,
            port = LocalCastPort(kit.f.a) { work: () -> CastAnswer, done: (CastAnswer) -> Unit -> waiting.addLast { done(work()) } })
        // The log is a capped buffer whose start is trimmed: what follows a marker line, not an offset into it
        val marker = "XPreviewStaysCurrentTest from here ${DeviceRandom.uuid()}"
        AppLog.info("test", marker)
        assertTrue(cast.start(kit.atw(kit.hand(Choices.BURST).first())))
        letThrough()
        val x = assertIs<Decision.Announce>(assertNotNull(cast.flow, "the panel").pending, "X is asked")
        val shown = cast.flow!!.version
        for (value in listOf(1, 0, 1, 0, 1, 0, 1)) cast.preview(x, value)
        assertTrue(cast.answer(Answer.Number(1)), "Cast answers X")
        letThrough()
        assertNull(cast.notice, "no refusal in the panel")
        assertTrue(cast.targeting, "X answered, the target is next")
        val log = AppLog.text().substringAfter(marker)
        assertTrue("refused as stale" !in log, log.takeLast(4_000))
        assertTrue("version $shown worked out again" in log, "the previews kept the version shown: ${log.takeLast(4_000)}")
        assertTrue(cast.answer(cast.pick!!.answer(listOf(TargetRef.Player(PlayerId(kit.f.b))))), "Bob targeted")
        letThrough()
        assertTrue(!cast.open, "sent")
        kit.playOn("the Burst resolving") { kit.state().player(PlayerId(kit.f.b)).life == 19 }
        assertEquals(19, kit.state().player(PlayerId(kit.f.b)).life)
        assertTrue(refused.none { it == CastController.TABLE_CHANGED }, "$refused")
    }
}
