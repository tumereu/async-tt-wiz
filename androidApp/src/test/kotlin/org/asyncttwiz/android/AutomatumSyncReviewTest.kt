package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Final adversarial review of the Automatum switch, history and sync on the device ("finalreview-sync", report
 * `arcanum-final-sync-review.md` outside the repo), and the owner's decision for #6/#7 (2026-11-03): switching Automatum
 * off, or clearing its data, during an open flow is one server step that always goes through.
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
    private val open = EngineRecord(Automatum.CONTINUATION_ID, EngineRecordKind.Continuation, data = """{"pending":null}""")
    private val flowOpen = data.initial.copy(board = data.initial.board.copy(automatum = true, engineRecords = listOf(open)))

    // finalreview-sync #6 — I-D19, I-D24, SYS-055, SYS-060 ("switching Automatum off is always allowed and is the way out
    // of anything stuck", spec §2; I-D42 "bug → report → fix by hand → continue"): the switch sent RevertFlow, then
    // SetAutomatum(false); when the device couldn't predict the revert (here the flow's opening event isn't in the history
    // it holds), the switch-off was refused behind it ("controlBarrier") and Automatum stayed on. Now switching off is one
    // command, `ExitFlow`, which the server carries out whatever the revert's outcome: it is sent as it is
    @Test fun switchingOffIsSentEvenWhenTheRevertCantBePredicted() {
        connect(data.head(flowOpen))
        val off = GameAction.ExitFlow(MtgAction.SetAutomatum(false))
        assertTrue(session.historyControl(data.game, data.actor, off), "switching off is queued: ${session.error}")
        assertEquals(listOf<GameAction>(off), commands().map { it.action })
        assertFalse(session.error.orEmpty().contains("controlBarrier"))
        session.close()
    }

    // finalreview-sync #6, #7 — SYS-060, TRN-054, TRN-055: during an open flow the switch and "Clear Automatum data" go as
    // one ExitFlow each (no separate revert, no play behind a control); with no flow open they are ordinary events
    @Test fun theSwitchAndClearingDuringAFlowGoAsOneStep() {
        val exits = mutableListOf<MtgAction>()
        val plays = mutableListOf<PlayAction>()
        var play = flowOpen
        val toolkit = TableToolkitController(data.actor, { play }, { true }, { action, _ -> plays += action; true },
            exitFlow = { exits += it; true })
        assertTrue(toolkit.setAutomatum(false), "switching off: ${toolkit.error}")
        assertTrue(toolkit.submit(PlayAction.Mtg(MtgAction.ClearRecords)), "clearing: ${toolkit.error}")
        assertEquals(listOf(MtgAction.SetAutomatum(false), MtgAction.ClearRecords), exits)
        assertTrue(plays.isEmpty())
        play = flowOpen.copy(board = flowOpen.board.copy(engineRecords = emptyList()))
        assertTrue(toolkit.setAutomatum(false))
        assertEquals(listOf<PlayAction>(PlayAction.Mtg(MtgAction.SetAutomatum(false))), plays)
    }
}
