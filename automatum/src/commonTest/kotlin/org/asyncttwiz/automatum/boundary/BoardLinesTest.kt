package org.asyncttwiz.automatum.boundary

import arcanum.api.Zone
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import org.asyncttwiz.automatum.CardPlays
import org.asyncttwiz.automatum.EngineTable
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.EventBuilder
import org.asyncttwiz.protocol.game.GameDefinitions
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.GameModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Web client step 4c: a board goes to the engine's worker as the events from the board it holds ([BoardLines],
 * [BoardTransfer.since]): what changed between two boards along their lines, those events moving the worker's copy to
 * exactly the newer board, and a snapshot when the worker lacks the board the events start from.
 */
class BoardLinesTest {
    private val table = EngineTable()
    private val alice get() = table.alice
    private val game = "game-1"

    /** Alice's first main phase: two Mountains on the battlefield, a Mountain, an Elvish Seer and a Storm Spark in hand. */
    private val root: GameModel = run {
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain")
        table.put(alice, Zone.Hand, "Mountain", "Elvish Seer", "Storm Spark")
        table.put(table.bob, Zone.Battlefield, "Elvish Seer")
        GameModel(table.state())
    }
    private val codec = EngineJson(GameDefinitions.of(root.engine))

    private fun event(model: GameModel, id: String, parent: String, input: (GameModel) -> Input): GameEvent =
        EventBuilder(model, id, parent, alice).run { perform(input(model)); build() }

    private fun land(model: GameModel): Input = Input.PlayLand(PlayerId(alice),
        model.engine.player(PlayerId(alice)).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == "Mountain" })
    private fun pass(@Suppress("UNUSED_PARAMETER") model: GameModel): Input = Input.PassPriority(PlayerId(alice))

    // The line: root → e1 (Alice plays her Mountain) → e2 (she passes); a branch at the root: e3 (she passes at once)
    private val atE1 = root.copy()
    private val e1 = event(atE1, "e1", "root", ::land)
    private val atE2 = atE1.copy()
    private val e2 = event(atE2, "e2", "e1", ::pass)
    private val atE3 = root.copy()
    private val e3 = event(atE3, "e3", "root", ::pass)

    private fun lines() = BoardLines().apply {
        record(1, "root", emptyList())
        record(2, "root", listOf(e1))
        record(3, "root", listOf(e1, e2))
        record(4, "root", listOf(e3))
    }

    private fun snapshot(model: GameModel) = BoardTransfer.Snapshot(codec.encodeDefinitions(), codec.encodeModel(model))

    /** [from]'s board as the worker decodes it, moved on by [delta]'s events as the worker applies them. */
    private fun moved(from: GameModel, delta: BoardTransfer.Delta): GameModel {
        val worker = EngineJson(EngineJson.decodeDefinitions(codec.encodeDefinitions()))
        val model = worker.decodeModel(codec.encodeModel(from))
        delta.revert.forEach { model.revert(worker.decodeEvent(it)) }
        delta.apply.forEach { model.apply(worker.decodeEvent(it)) }
        return model
    }

    private fun ids(events: List<GameEvent>) = events.map { it.id }

    @Test fun aLaterBoardOnTheSameLineIsItsNewEvents() {
        val between = assertNotNull(lines().between(1, 3))
        assertEquals(emptyList(), ids(between.revert))
        assertEquals(listOf("e1", "e2"), ids(between.apply))
        val back = assertNotNull(lines().between(3, 2))
        assertEquals(listOf("e2"), ids(back.revert))
        assertEquals(emptyList(), ids(back.apply))
    }

    @Test fun aBranchRevertsBackToWhereTheLinesPartThenApplies() {
        val between = assertNotNull(lines().between(3, 4))
        assertEquals(listOf("e2", "e1"), ids(between.revert), "the latest first")
        assertEquals(listOf("e3"), ids(between.apply))
    }

    @Test fun theEventsMoveTheWorkersBoardToExactlyTheNewerOne() {
        val lines = lines()
        assertTrue(moved(atE2, assertNotNull(lines.since(3, 4, codec::encodeEvent))).sameAs(atE3), "branch")
        assertTrue(moved(root, assertNotNull(lines.since(1, 3, codec::encodeEvent))).sameAs(atE2), "forward")
        assertTrue(moved(atE2, assertNotNull(lines.since(3, 2, codec::encodeEvent))).sameAs(atE1), "back")
    }

    @Test fun linesTrimmedAtDifferentPointsStillMeet() {
        val lines = lines()
        // The device trimmed its line's start past e1 by board 5
        lines.record(5, "e1", listOf(e2))
        val forward = assertNotNull(lines.between(2, 5))
        assertEquals(emptyList(), ids(forward.revert))
        assertEquals(listOf("e2"), ids(forward.apply))
        val backward = assertNotNull(lines.between(5, 2))
        assertEquals(listOf("e2"), ids(backward.revert))
        assertEquals(emptyList(), ids(backward.apply))
    }

    @Test fun unknownOrUnrelatedBoardsOrTooManyEventsGoAsASnapshot() {
        val lines = lines()
        assertNull(lines.between(9, 3), "an unknown board")
        assertNull(lines.since(3, 9, codec::encodeEvent))
        lines.record(6, "elsewhere", listOf(e3))
        assertNull(lines.between(2, 6), "no shared point: the game loaded again")
        val small = BoardLines(most = 1).apply { record(1, "root", emptyList()); record(3, "root", listOf(e1, e2)) }
        assertNull(small.between(1, 3))
    }

    @Test fun onlyTheNewestBoardsAreKeptAndARecordedBoardKeepsItsLine() {
        val lines = BoardLines(keep = 2)
        lines.record(1, "root", emptyList())
        lines.record(2, "root", listOf(e1))
        lines.record(2, "root", listOf(e3))
        lines.record(3, "root", listOf(e1, e2))
        assertFalse(lines.knows(1))
        assertEquals(listOf("e2"), ids(assertNotNull(lines.between(2, 3)).apply), "board 2 kept its first line")
        lines.clear()
        assertFalse(lines.knows(3))
    }

    // ---- Through the message channel, a planner standing in for the worker

    private class Loopback {
        var planner = EnginePlanner()
        val posted = ArrayDeque<String>()
        val logged = mutableListOf<String>()
        val channel = MessageEngineChannel({ posted += it }, { _, text -> logged += text })
        fun deliver() { while (posted.isNotEmpty()) channel.received(EngineMessages.encode(planner.handle(EngineMessages.decodeRequest(posted.removeFirst())))) }
        fun kinds(): List<String> = posted.map { EngineMessages.decodeRequest(it)::class.simpleName.orEmpty() }
    }

    /** Board [number] standing on [model], transferred by [lines]. */
    private inner class Board(val number: Long, val model: GameModel, val lines: BoardLines) : BoardTransfer {
        var snapshots = 0
        override fun snapshot() = snapshot(model).also { snapshots++ }
        override fun since(from: Long) = lines.since(from, number, codec::encodeEvent)
    }

    private fun cardPlays(model: GameModel) = CardPlays.of(model.engine, alice, model.view(), model.visible(PlayerId(alice))).byCard

    @Test fun theChannelSendsTheNextBoardsAsTheirEvents() {
        val lines = lines()
        // Back on the line, as after a branch was taken back (a board's number never goes back)
        lines.record(5, "root", listOf(e1))
        val loop = Loopback()
        val answers = mutableListOf<EngineAnswer>()
        val boards = listOf(Board(1, root, lines), Board(3, atE2, lines), Board(4, atE3, lines), Board(5, atE1, lines))
        for ((i, board) in boards.withIndex()) {
            loop.channel.ask(EngineRequest.CardPlaysOf(loop.channel.next(), game, board.number, alice), null, board) { answers += it }
            assertEquals(listOf(if (i == 0) "Load" else "Advance", "CardPlaysOf"), loop.kinds(), "board ${board.number}")
            loop.deliver()
            assertEquals(cardPlays(board.model), assertIs<EngineAnswer.CardPlaysAnswer>(answers.last()).plays.byCard, "board ${board.number}")
        }
        assertEquals(listOf(1, 0, 0, 0), boards.map { it.snapshots })
        assertEquals(0, loop.channel.requests.awaited)
    }

    @Test fun aWorkerWithoutTheBaseBoardGetsASnapshot() {
        val lines = lines()
        val loop = Loopback()
        val answers = mutableListOf<EngineAnswer>()
        val first = Board(3, atE2, lines)
        loop.channel.ask(EngineRequest.CardPlaysOf(loop.channel.next(), game, 3, alice), null, first) { answers += it }
        loop.deliver()
        // The worker started again: it holds nothing, so the events from board 3 don't fit
        loop.planner = EnginePlanner()
        val next = Board(4, atE3, lines)
        loop.channel.ask(EngineRequest.CardPlaysOf(loop.channel.next(), game, 4, alice), null, next) { answers += it }
        assertEquals(listOf("Advance", "CardPlaysOf"), loop.kinds())
        loop.deliver()
        assertEquals(1, next.snapshots, "sent whole after its events didn't fit")
        assertEquals(cardPlays(atE3), assertIs<EngineAnswer.CardPlaysAnswer>(answers.last()).plays.byCard)
        assertTrue(loop.logged.any { "asked again" in it }, loop.logged.toString())
        assertEquals(0, loop.channel.requests.awaited)
    }

    // Staleness and order across boards sent as events: an answer about a board older than the one shown is dropped, the
    // newer board's answers arrive in the order asked
    @Test fun answersForAnOlderBoardAreDroppedAndTheNewersArriveInOrder() {
        val lines = lines()
        val loop = Loopback()
        val answers = mutableListOf<Pair<Long, Long>>()
        loop.channel.ask(EngineRequest.CardPlaysOf(loop.channel.next(), game, 1, alice), null, Board(1, root, lines)) { answers += it.board to it.seq }
        val newer = Board(3, atE2, lines)
        val hand = EngineRequest.HandPlaysOf(loop.channel.next(), game, 3, alice)
        loop.channel.ask(hand, null, newer) { answers += it.board to it.seq }
        val cards = EngineRequest.CardPlaysOf(loop.channel.next(), game, 3, alice)
        loop.channel.ask(cards, null, newer) { answers += it.board to it.seq }
        assertEquals(listOf("Load", "CardPlaysOf", "Advance", "HandPlaysOf", "CardPlaysOf"), loop.kinds())
        loop.deliver()
        assertEquals(listOf(3L to hand.seq, 3L to cards.seq), answers)
        assertTrue(loop.logged.any { "dropped" in it && "board 3" in it }, loop.logged.toString())
    }
}
