package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Final adversarial review of the Automatum switch, history and sync on the device ("finalreview-sync", report
 * `arcanum-final-sync-review.md` outside the repo). Ignored tests pin problems found and fail while they aren't fixed.
 */
class AutomatumSyncReviewTest {
    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun disconnect() = Unit
        override fun close() = Unit
    }

    private val data = GameplayFixture()
    private val wire = Wire()
    private val posts = ArrayDeque<() -> Unit>()
    private val session = GameSession(wire, { "A".repeat(43) }, { posts.add(it) }, {}, {}, { _, _ -> }, {},
        accountId = { data.actor }, now = { 0L })
    private fun drain() { while (posts.isNotEmpty()) posts.removeFirst()() }
    private fun message(message: GameServerMessage) { wire.listener.message(message); drain() }
    private fun connect(head: GameHead) {
        session.watch(listOf(data.game)); wire.listener.opened(); drain(); message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
        val stream = wire.frames.mapNotNull { when (it) { is OpenGameStream -> it.streamId; is ResumeGameStream -> it.streamId; else -> null } }.last()
        message(GameStreamReady(data.game.id, stream, "1", emptyList())); message(head)
    }
    private fun commands() = wire.frames.filterIsInstance<GameCommand>()

    // finalreview-sync #6 — I-D19, I-D24, SYS-055 ("switching Automatum off is always allowed and is the way out of
    // anything stuck", spec §2; I-D42 "bug → report → fix by hand → continue"): the switch first sends RevertFlow, then
    // SetAutomatum(false) (`TableToolkitController.setAutomatum`). When the device can't predict the revert (here the
    // flow's opening event isn't in the history it holds; also with an uncertain earlier request, or a revert the server
    // will refuse), the revert is queued as an unpredicted control, and `GameSession.gameplayCommand` refuses every play
    // behind it ("controlBarrier"): the switch-off is never sent. The server then refuses the revert, the device says
    // "it was closed as it is" (`historyControlFailure`), and Automatum stays on with the flow open. Expected: the
    // switch-off is queued behind the revert and goes to the server whatever the revert's outcome
    @Ignore("finalreview-sync #6: switching off is dropped when the flow's revert can't be predicted")
    @Test fun switchingOffIsSentEvenWhenTheRevertCantBePredicted() {
        val open = EngineRecord(Automatum.CONTINUATION_ID, EngineRecordKind.Continuation, data = """{"pending":null}""")
        val initial = data.initial.copy(board = data.initial.board.copy(automatum = true, engineRecords = listOf(open)))
        connect(data.head(initial))
        // As the switch does it: revert, then off
        assertTrue(session.historyControl(data.game, data.actor, GameAction.RevertFlow), "the revert is queued")
        val off = session.gameplay(data.game, data.actor, PlayAction.Mtg(MtgAction.SetAutomatum(false)))
        assertTrue(off, "switching off is queued too: ${session.error}")
        assertEquals(listOf<GameAction>(GameAction.RevertFlow, GameAction.Play(PlayAction.Mtg(MtgAction.SetAutomatum(false)))),
            commands().map { it.action })
        assertFalse(session.error.orEmpty().contains("controlBarrier"))
        session.close()
    }
}
