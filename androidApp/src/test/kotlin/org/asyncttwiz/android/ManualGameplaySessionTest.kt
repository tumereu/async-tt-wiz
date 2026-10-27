package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

/** Missing I17 manual-tool / I14 production-session seam; accepted revisions use the shared processor. */
class ManualGameplaySessionTest {
    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun disconnect() = Unit
        override fun close() = Unit
    }
    private class Fixture {
        val data = GameplayFixture()
        val wire = Wire()
        val posts = ArrayDeque<() -> Unit>()
        val results = mutableListOf<GameResult>()
        val session = GameSession(wire, { "A".repeat(43) }, { posts.add(it) }, {}, {},
            { _, result -> results.add(result) }, {}, accountId = { data.actor })
        fun drain() { while (posts.isNotEmpty()) posts.removeFirst()() }
        fun message(message: GameServerMessage) { wire.listener.message(message); drain() }
        fun connect(generation: String = "1", statuses: List<GameRequestStatus> = emptyList(), head: GameHead = data.head()) {
            session.watch(listOf(data.game)); wire.listener.opened(); drain(); message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            val stream = wire.frames.mapNotNull { when (it) {
                is OpenGameStream -> it.streamId; is ResumeGameStream -> it.streamId; else -> null
            } }.last()
            message(GameStreamReady(data.game.id, stream, generation, statuses)); message(head)
            assertTrue(session.ready(data.game.id))
        }
        fun submit(action: PlayAction) = session.gameplay(data.game, data.actor, action)
        fun commands() = wire.frames.filterIsInstance<GameCommand>()
        fun display() = session.play(data.game)!!
        fun accepted() = session.acceptedPlay(data.game)!!
        fun counter(state: PlayState = display()) = state.board.cards.single().counters[CounterColor.BLUE]
        fun note() = display().board.cards.single().note
        fun editNote(text: String) {
            val ui = ManualActions()
            ui.cards(display(), data.id(9), emptyList())
            ui.edit(ManualValue(ManualValueKind.NOTE), display(), data.actor)
            ui.editor!!.text = text
            assertTrue(ui.submitValue(display(), data.actor, ::submit))
            assertNull(ui.editor)
            assertEquals(listOf(data.id(9)), ui.scope!!.targetIds)
        }
    }

    @Test fun mixedManualAndScalarInflightRebaseOnActualInputAndResultBeforeRevisionNeverDoubleApplies() {
        val f = Fixture(); val d = f.data; f.connect()
        assertTrue(f.submit(PlayAction.AdjustLife(d.actor, "2")))
        assertTrue(f.submit(PlayAction.AdjustCounter(CounterTarget.CARD, d.id(9), CounterColor.BLUE, "1")))
        assertTrue(f.submit(PlayAction.AdjustCounter(CounterTarget.CARD, d.id(9), CounterColor.BLUE, "2")))
        f.editNote("  captured note  ")
        val originals = f.commands()
        assertEquals(4, originals.size)
        assertEquals(4, originals.map { it.requestId }.toSet().size)
        assertTrue(originals.all(GameWire::valid))
        assertEquals("22", d.life(f.display())); assertEquals("3", f.counter()); assertEquals("captured note", f.note())
        assertEquals(d.initial, f.accepted())

        // A receipt and even a final result do not install a board or remove the awaiting-revision overlay.
        f.message(GameReceipt(originals[1].requestId, originals[1].scope, "1"))
        f.message(d.result(originals[1], "4"))
        assertEquals(d.initial, f.accepted()); assertEquals("3", f.counter())
        val remote = d.update(d.initial, d.command(PlayAction.SetCounter(CounterTarget.CARD,
            d.id(9), CounterColor.BLUE, "10")), "1", "2", d.other)
        f.message(remote)
        assertEquals("10", f.counter(f.accepted())); assertEquals("13", f.counter())
        assertEquals("22", d.life(f.display())); assertEquals("captured note", f.note())
        val endpoint = f.display().board

        var accepted = remote.play!!
        originals.forEachIndexed { index, command ->
            val revision = d.update(accepted, command, (index + 2).toString(), (index + 3).toString())
            f.message(revision); f.message(revision)
            f.message(d.result(command, revision.resultRevision)); f.message(d.result(command, revision.resultRevision))
            assertEquals(endpoint, f.display().board)
            assertTrue(f.session.ready(d.game.id))
            accepted = revision.play!!
        }
        assertEquals(accepted, f.accepted()); assertEquals(accepted, f.display())
        assertFalse(f.session.pending(d.game.id)); assertEquals(4, f.results.size)
        assertEquals(originals, f.commands())
        assertEquals("13", f.counter()); assertEquals("22", d.life(f.accepted()))
    }

    @Test fun inapplicableManualPredictionStaysWithdrawnButUncertainOriginalResumesAndCanBeAcceptedLater() {
        val f = Fixture(); val d = f.data; f.connect()
        assertTrue(f.submit(PlayAction.AdjustCounter(CounterTarget.CARD, d.id(9), CounterColor.BLUE, "1")))
        assertTrue(f.submit(PlayAction.AdjustLife(d.actor, "1")))
        f.editNote("retained note")
        val originals = f.commands()
        val blocked = d.update(d.initial, d.command(PlayAction.SetCounter(CounterTarget.CARD,
            d.id(9), CounterColor.BLUE, "sealed")), "1", "2", d.other)
        f.message(blocked)
        assertEquals("sealed", f.counter()); assertEquals("21", d.life(f.display())); assertEquals("retained note", f.note())
        val restored = d.update(blocked.play!!, d.command(PlayAction.SetCounter(CounterTarget.CARD,
            d.id(9), CounterColor.BLUE, "10")), "2", "3", d.other)
        f.message(restored)
        assertEquals("10", f.counter()) // Restoration must not resurrect the withdrawn overlay.
        assertTrue(f.session.pending(d.game.id)); assertTrue(f.results.isEmpty())

        f.wire.listener.disconnected(); f.drain()
        assertFalse(f.submit(PlayAction.SetNote(d.id(9), "offline replacement")))
        f.connect("2", originals.map { GameRequestStatus(it.requestId, "notAdmitted") }, d.head(restored.play!!, "3"))
        assertEquals(originals.map { it.requestId }, f.wire.frames.filterIsInstance<ResumeGameStream>().single().requestIds)
        assertEquals(originals.map { it.copy(generation = "2") }, f.commands().drop(originals.size))
        assertEquals("10", f.counter()); assertEquals("retained note", f.note())

        val late = d.update(restored.play!!, originals[0], "3", "4")
        f.message(d.result(originals[0], "4"))
        assertEquals("10", f.counter())
        f.message(late); f.message(late)
        assertEquals("11", f.counter()); assertEquals("21", d.life(f.display())); assertEquals("retained note", f.note())
        var accepted = late.play!!
        originals.drop(1).forEachIndexed { index, command ->
            val revision = d.update(accepted, command, (index + 4).toString(), (index + 5).toString())
            f.message(d.result(command, revision.resultRevision)); f.message(revision)
            accepted = revision.play!!
        }
        assertEquals(accepted, f.accepted()); assertEquals(accepted, f.display())
        assertEquals("11", f.counter()); assertFalse(f.session.pending(d.game.id))
        assertEquals(3, f.results.size); assertEquals(6, f.commands().size)
        assertTrue(f.session.ready(d.game.id))
    }
}
