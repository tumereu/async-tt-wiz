package org.asyncttwiz.automatum.boundary

import arcanum.api.Zone
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import org.asyncttwiz.automatum.CardPlay
import org.asyncttwiz.automatum.CardPlays
import org.asyncttwiz.automatum.EngineTable
import org.asyncttwiz.automatum.HandPlays
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.EventBuilder
import org.asyncttwiz.protocol.game.GameDefinitions
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.GameModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The engine boundary (docs/specs/web-client.md §1 item 3): its requests and answers as JSON strings, the engine side's
 * boards (a snapshot, then the events that changed it), the page's ledger dropping stale answers, and the message channel
 * end to end through JSON on a planner standing in for the Web Worker.
 */
class EngineBoundaryTest {
    private val table = EngineTable()
    private val alice get() = table.alice
    private val game = "game-1"

    /** A board of Alice's: two Mountains on the battlefield, a Mountain, a Cinder Pup and a Storm Spark in her hand. */
    private fun model(): GameModel {
        table.put(alice, Zone.Battlefield, "Mountain", "Mountain")
        table.put(alice, Zone.Hand, "Mountain", "Elvish Seer", "Storm Spark")
        table.put(table.bob, Zone.Battlefield, "Elvish Seer")
        return GameModel(table.state())
    }

    private fun codec(model: GameModel) = EngineJson(GameDefinitions.of(model.engine))

    private fun snapshot(model: GameModel): BoardTransfer.Snapshot =
        codec(model).let { BoardTransfer.Snapshot(it.encodeDefinitions(), it.encodeModel(model)) }

    private fun handPlays(model: GameModel, viewer: String = alice) = HandPlays.of(model.engine, viewer, model.view())
    private fun cardPlays(model: GameModel, viewer: String = alice) =
        CardPlays.of(model.engine, viewer, model.view(), model.visible(PlayerId(viewer)))

    /** Alice plays her Mountain from her hand: the event, with [model] moved past it. */
    private fun playLand(model: GameModel, id: String = "e1"): GameEvent {
        val land = model.engine.player(PlayerId(alice)).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == "Mountain" }
        return EventBuilder(model, id, "root", alice).run { perform(Input.PlayLand(PlayerId(alice), land)); build() }
    }

    private fun assertSame(expected: HandPlays, actual: HandPlays) {
        assertEquals(expected.byCard, actual.byCard)
        assertEquals(expected.offZone, actual.offZone)
    }

    // ---- The messages as JSON

    @Test fun requestsRoundTripAsJson() {
        val requests = listOf(
            EngineRequest.Load(1, game, 3, "{\"defs\":1}", "{\"model\":2}"),
            EngineRequest.Advance(2, game, 4, 3, listOf("{\"e\":1}"), listOf("{\"e\":0}")),
            EngineRequest.Close(3, game),
            EngineRequest.HandPlaysOf(4, game, 4, alice),
            EngineRequest.CardPlaysOf(5, game, 4, alice),
        )
        for (request in requests) assertEquals(request, EngineMessages.decodeRequest(EngineMessages.encode(request)))
    }

    @Test fun answersRoundTripAsJson() {
        val model = model()
        val hand = handPlays(model)
        val cards = cardPlays(model)
        assertTrue(hand.byCard.isNotEmpty(), "Alice has plays")
        assertTrue(cards.byCard.values.flatten().any { it.refusal != null }, "some plays are refused")
        val plain = listOf(
            EngineAnswer.Held(1, game, 3, 120),
            EngineAnswer.Missing(2, game, 4, 5, held = 3),
            EngineAnswer.Missing(3, game, 4, 5),
            EngineAnswer.Failed(4, game, 4, 9, "IllegalStateException", "no such object"),
        )
        for (answer in plain) assertEquals(answer, EngineMessages.decodeAnswer(EngineMessages.encode(answer)))
        val handBack = assertIs<EngineAnswer.HandPlaysAnswer>(EngineMessages.decodeAnswer(
            EngineMessages.encode(EngineAnswer.HandPlaysAnswer(5, game, 4, 300, hand))))
        assertSame(hand, handBack.plays)
        assertEquals(300, handBack.micros)
        val cardsBack = assertIs<EngineAnswer.CardPlaysAnswer>(EngineMessages.decodeAnswer(
            EngineMessages.encode(EngineAnswer.CardPlaysAnswer(6, game, 4, 300, cards))))
        assertEquals(cards.byCard, cardsBack.plays.byCard)
    }

    @Test fun everyRefusalAndKindRoundTrips() {
        val plays = listOf(CardPlay.Kind.Cast, CardPlay.Kind.Play, CardPlay.Kind.Ability, CardPlay.Kind.Mana, CardPlay.Kind.Special)
            .map { CardPlay(it, "text", null) } + listOf(CardPlay.Refusal.Priority("bob"), CardPlay.Refusal.Priority(null),
            CardPlay.Refusal.Timing, CardPlay.Refusal.Loyalty, CardPlay.Refusal.Used, CardPlay.Refusal.NotYours, CardPlay.Refusal.Other)
            .map { CardPlay(CardPlay.Kind.Ability, "{T}: Draw a card.", null, it) }
        val text = EngineMessages.json.encodeToString(CardPlaysSerializer, CardPlays.restored(mapOf("c1" to plays)))
        assertEquals(plays, EngineMessages.json.decodeFromString(CardPlaysSerializer, text).of("c1"))
    }

    // ---- The engine side's boards

    @Test fun plansOnABoardSentAsASnapshot() {
        val model = model()
        val planner = EnginePlanner()
        val snap = snapshot(model)
        assertIs<EngineAnswer.Held>(planner.handle(EngineRequest.Load(1, game, 7, snap.definitions, snap.model)))
        assertEquals(7, planner.held(game))
        val hand = assertIs<EngineAnswer.HandPlaysAnswer>(planner.handle(EngineRequest.HandPlaysOf(2, game, 7, alice)))
        assertSame(handPlays(model), hand.plays)
        val cards = assertIs<EngineAnswer.CardPlaysAnswer>(planner.handle(EngineRequest.CardPlaysOf(3, game, 7, alice)))
        assertEquals(cardPlays(model).byCard, cards.plays.byCard)
        assertEquals(cardPlays(model, table.bob).byCard,
            assertIs<EngineAnswer.CardPlaysAnswer>(planner.handle(EngineRequest.CardPlaysOf(4, game, 7, table.bob))).plays.byCard)
    }

    @Test fun aBoardNotHeldIsMissing() {
        val model = model()
        val planner = EnginePlanner()
        assertEquals(EngineAnswer.Missing(1, game, 7, 0), planner.handle(EngineRequest.HandPlaysOf(1, game, 7, alice)).zeroed())
        val snap = snapshot(model)
        planner.handle(EngineRequest.Load(2, game, 7, snap.definitions, snap.model))
        assertEquals(EngineAnswer.Missing(3, game, 8, 0, held = 7), planner.handle(EngineRequest.CardPlaysOf(3, game, 8, alice)).zeroed())
        assertEquals(EngineAnswer.Missing(4, game, 9, 0, held = 7), planner.handle(EngineRequest.Advance(4, game, 9, 8, emptyList())).zeroed())
        planner.handle(EngineRequest.Close(5, game))
        assertNull(planner.held(game))
    }

    @Test fun eventsMoveTheHeldBoardOnAndBack() {
        val model = model()
        val codec = codec(model)
        val snap = snapshot(model)
        val before = handPlays(model)
        val event = playLand(model)
        val planner = EnginePlanner()
        planner.handle(EngineRequest.Load(1, game, 1, snap.definitions, snap.model))
        assertIs<EngineAnswer.Held>(planner.handle(EngineRequest.Advance(2, game, 2, 1, listOf(codec.encodeEvent(event)))))
        val after = assertIs<EngineAnswer.HandPlaysAnswer>(planner.handle(EngineRequest.HandPlaysOf(3, game, 2, alice))).plays
        assertSame(handPlays(model), after)
        assertTrue(after.byCard.values.flatten().none { it is Input.PlayLand }, "the land play is used")
        assertIs<EngineAnswer.Held>(planner.handle(EngineRequest.Advance(4, game, 3, 2, emptyList(), listOf(codec.encodeEvent(event)))))
        assertSame(before, assertIs<EngineAnswer.HandPlaysAnswer>(planner.handle(EngineRequest.HandPlaysOf(5, game, 3, alice))).plays)
    }

    @Test fun anEventThatDoesNotFitLeavesNoBoard() {
        val model = model()
        val codec = codec(model)
        val snap = snapshot(model)
        val event = playLand(model)
        val planner = EnginePlanner()
        planner.handle(EngineRequest.Load(1, game, 1, snap.definitions, snap.model))
        planner.handle(EngineRequest.Advance(2, game, 2, 1, listOf(codec.encodeEvent(event))))
        // The same event again: made on another timestamp counter
        assertIs<EngineAnswer.Failed>(planner.handle(EngineRequest.Advance(3, game, 3, 2, listOf(codec.encodeEvent(event)))))
        assertNull(planner.held(game))
    }

    @Test fun theInProcessPathPlansOnTheBoardItIsGiven() {
        val model = model()
        val lines = mutableListOf<String>()
        val channel = InProcessEngineChannel(EngineLog { _, text -> lines += text })
        val board = PlanningBoard(model.engine, model.view(), model.visible(PlayerId(alice)))
        val answer = channel.call(EngineRequest.HandPlaysOf(channel.next(), game, 1, alice), board)
        assertSame(handPlays(model), assertIs<EngineAnswer.HandPlaysAnswer>(answer).plays)
        var got: EngineAnswer? = null
        channel.ask(EngineRequest.CardPlaysOf(channel.next(), game, 1, alice), board, null) { got = it }
        assertEquals(cardPlays(model).byCard, assertIs<EngineAnswer.CardPlaysAnswer>(got).plays.byCard)
    }

    // ---- The message channel through JSON, a planner standing in for the worker

    /** A worker on a queue: what the page posts waits until [deliver]. */
    private class Loopback {
        var planner = EnginePlanner()
        val posted = ArrayDeque<String>()
        val lines = mutableListOf<String>()
        val channel = MessageEngineChannel({ posted += it }, { _, text -> lines += text })
        fun deliver() { while (posted.isNotEmpty()) channel.received(EngineMessages.encode(planner.handle(EngineMessages.decodeRequest(posted.removeFirst())))) }
        fun kinds(): List<String> = posted.map { EngineMessages.decodeRequest(it)::class.simpleName.orEmpty() }
    }

    private class Transfer(val snapshot: BoardTransfer.Snapshot, val deltas: Map<Long, BoardTransfer.Delta> = emptyMap()) : BoardTransfer {
        var snapshots = 0
        override fun snapshot() = snapshot.also { snapshots++ }
        override fun since(from: Long) = deltas[from]
    }

    @Test fun theChannelSendsTheBoardOnceThenItsEvents() {
        val model = model()
        val codec = codec(model)
        val first = Transfer(snapshot(model))
        val event = playLand(model)
        val second = Transfer(snapshot(model), mapOf(1L to BoardTransfer.Delta(listOf(codec.encodeEvent(event)))))
        val loop = Loopback()
        val answers = mutableListOf<EngineAnswer>()
        loop.channel.ask(EngineRequest.HandPlaysOf(loop.channel.next(), game, 1, alice), null, first) { answers += it }
        loop.channel.ask(EngineRequest.CardPlaysOf(loop.channel.next(), game, 1, alice), null, first) { answers += it }
        assertEquals(listOf("Load", "HandPlaysOf", "CardPlaysOf"), loop.kinds(), "the board goes once, before the requests")
        loop.deliver()
        assertEquals(2, answers.size)
        loop.channel.ask(EngineRequest.HandPlaysOf(loop.channel.next(), game, 2, alice), null, second) { answers += it }
        assertEquals(listOf("Advance", "HandPlaysOf"), loop.kinds(), "the next board goes as its events")
        loop.deliver()
        assertEquals(0, second.snapshots)
        assertSame(handPlays(model), assertIs<EngineAnswer.HandPlaysAnswer>(answers.last()).plays)
        assertEquals(0, loop.channel.requests.awaited)
    }

    @Test fun aMissingBoardIsSentWholeAndAskedAgainOnce() {
        val model = model()
        val transfer = Transfer(snapshot(model))
        val loop = Loopback()
        val answers = mutableListOf<EngineAnswer>()
        loop.channel.ask(EngineRequest.HandPlaysOf(loop.channel.next(), game, 1, alice), null, transfer) { answers += it }
        loop.deliver()
        // The worker starts again and holds nothing
        loop.planner = EnginePlanner()
        loop.channel.ask(EngineRequest.CardPlaysOf(loop.channel.next(), game, 1, alice), null, transfer) { answers += it }
        assertEquals(listOf("CardPlaysOf"), loop.kinds(), "the page thinks the worker holds the board")
        loop.deliver()
        assertEquals(2, answers.size)
        assertEquals(cardPlays(model).byCard, assertIs<EngineAnswer.CardPlaysAnswer>(answers.last()).plays.byCard)
        assertEquals(2, transfer.snapshots)
        assertTrue(loop.lines.any { "asked again" in it }, loop.lines.toString())
    }

    // Answers for an older board than the page shows now are dropped, as the engine thread's stale results are
    @Test fun anAnswerForAnOlderBoardIsDropped() {
        val model = model()
        val transfer = Transfer(snapshot(model))
        val loop = Loopback()
        val answers = mutableListOf<EngineAnswer>()
        loop.channel.ask(EngineRequest.HandPlaysOf(loop.channel.next(), game, 1, alice), null, transfer) { answers += it }
        loop.channel.moved(game, 2)
        loop.deliver()
        assertTrue(answers.isEmpty(), "dropped")
        assertTrue(loop.lines.any { "dropped" in it && "board 2" in it }, loop.lines.toString())
        assertEquals(0, loop.channel.requests.awaited)
    }

    // A request replaced by a newer one of its kind on the same game: only the newer one's answer is taken
    @Test fun aReplacedRequestsAnswerIsDropped() {
        val model = model()
        val transfer = Transfer(snapshot(model))
        val loop = Loopback()
        val answers = mutableListOf<Long>()
        val first = EngineRequest.HandPlaysOf(loop.channel.next(), game, 1, alice)
        loop.channel.ask(first, null, transfer) { answers += it.seq }
        val second = EngineRequest.HandPlaysOf(loop.channel.next(), game, 1, alice)
        loop.channel.ask(second, null, transfer) { answers += it.seq }
        val other = EngineRequest.CardPlaysOf(loop.channel.next(), game, 1, alice)
        loop.channel.ask(other, null, transfer) { answers += it.seq }
        loop.deliver()
        assertEquals(listOf(second.seq, other.seq), answers)
        assertTrue(loop.lines.any { "replaced by #${second.seq}" in it }, loop.lines.toString())
    }

    @Test fun closingDropsWhatWaitsAndForgetsTheBoard() {
        val model = model()
        val loop = Loopback()
        val answers = mutableListOf<EngineAnswer>()
        loop.channel.ask(EngineRequest.HandPlaysOf(loop.channel.next(), game, 1, alice), null, Transfer(snapshot(model))) { answers += it }
        loop.channel.close(game)
        loop.deliver()
        assertTrue(answers.isEmpty())
        assertNull(loop.planner.held(game))
        assertTrue(loop.lines.any { "(closed)" in it }, loop.lines.toString())
    }

    private fun EngineAnswer.zeroed(): EngineAnswer = when (this) {
        is EngineAnswer.Missing -> copy(micros = 0)
        is EngineAnswer.Held -> copy(micros = 0)
        is EngineAnswer.Failed -> copy(micros = 0)
        is EngineAnswer.HandPlaysAnswer -> copy(micros = 0)
        is EngineAnswer.CardPlaysAnswer -> copy(micros = 0)
    }

}
