@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.FaceDefinition
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.PrintedNumber
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.setup.StartOptions
import arcanum.engine.state.CardId
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import org.asyncttwiz.automatum.boundary.BoardTransfer
import org.asyncttwiz.automatum.boundary.EngineAnswer
import org.asyncttwiz.automatum.boundary.EngineRequest
import org.asyncttwiz.automatum.boundary.MessageEngineChannel
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.EventBuilder
import org.asyncttwiz.protocol.game.GameDefinitions
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.LineRandom
import org.asyncttwiz.protocol.game.TableState

/**
 * Dev only (a test build opened at `#engine-bench`, in place of the app): measures the engine boundary's round trips to
 * the Web Worker ([startEngineWorker]) on a game made here (two 60-card decks of lands and vanilla creatures, both hands
 * kept, Alice's first main phase): the first request with the board's snapshot, then hand plays and card plays on the
 * same board, then the next board sent as its event (Alice plays a land), while the page's frames are timed. The results
 * go on the page, to the console and, when done, into the title ("engine-bench: done").
 */
fun runEngineBench(base: String) {
    val out = StringBuilder()
    fun line(text: String) { out.append(text).append('\n'); jsShowBench(out.toString()); println("engine-bench: $text") }
    val frames = Frames().also { it.start() }
    val channel = startEngineWorker(base)
    val bench = try { BenchGame() } catch (problem: Exception) {
        line("The bench game couldn't be made: ${problem.stackTraceToString()}"); jsBenchTitle("engine-bench: failed"); return
    }
    line("Board: ${bench.objects} objects; snapshot ${bench.snapshotChars} chars (made in ${bench.encodeMs.f()} ms on the page); " +
        "delta ${bench.deltaChars} chars")
    val game = "bench"
    val runs = 20

    fun timed(request: EngineRequest, transfer: BoardTransfer, done: (ms: Double, answer: EngineAnswer) -> Unit) {
        val at = jsNow()
        channel.ask(request, null, transfer) { answer -> done(jsNow() - at, answer) }
    }
    fun series(kind: String, board: Long, transfer: BoardTransfer, n: Int, make: (Long) -> EngineRequest, then: () -> Unit) {
        val totals = mutableListOf<Double>(); val works = mutableListOf<Double>()
        fun next(left: Int) {
            if (left == 0) {
                line("$kind on the same board, $n round trips: median ${totals.median().f()} ms (worker ${works.median().f()} ms), " +
                    "max ${totals.max().f()} ms")
                then(); return
            }
            timed(make(channel.next()), transfer) { ms, answer -> totals += ms; works += answer.micros / 1000.0; next(left - 1) }
        }
        next(n)
    }

    frames.reset()
    timed(EngineRequest.HandPlaysOf(channel.next(), game, 1, bench.alice), bench.first) { ms, answer ->
        line("First hand plays with the snapshot: ${ms.f()} ms round trip (plays worked out in ${(answer.micros / 1000.0).f()} ms; " +
            "${(answer as? EngineAnswer.HandPlaysAnswer)?.plays?.byCard?.size ?: "no"} cards with plays, ${answer::class.simpleName})")
        series("Hand plays", 1, bench.first, runs, { EngineRequest.HandPlaysOf(it, game, 1, bench.alice) }) {
            series("Card plays", 1, bench.first, runs, { EngineRequest.CardPlaysOf(it, game, 1, bench.alice) }) {
                timed(EngineRequest.HandPlaysOf(channel.next(), game, 2, bench.alice), bench.second) { ms, after ->
                    line("Next board as its event, then hand plays: ${ms.f()} ms round trip (${after::class.simpleName}, " +
                        "${bench.second.snapshots} snapshots of it made)")
                    line("Page frames meanwhile: ${frames.count} frames, worst ${frames.worst.f()} ms, mean ${frames.mean().f()} ms")
                    line(channel.requests.let { "Answers awaited at the end: ${it.awaited}" })
                    frames.stop()
                    jsBenchTitle("engine-bench: done")
                }
            }
        }
    }
}

private fun List<Double>.median(): Double = sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }
private fun Double.f(): String = ((this * 10).toLong() / 10.0).toString()

/** The page's frame times while the bench runs (requestAnimationFrame). */
private class Frames {
    private var last = 0.0
    private var running = false
    var worst = 0.0; private set
    var count = 0; private set
    private var sum = 0.0
    fun start() { running = true; frame() }
    fun stop() { running = false }
    fun reset() { worst = 0.0; count = 0; sum = 0.0; last = 0.0 }
    fun mean() = if (count == 0) 0.0 else sum / count
    private fun frame() {
        jsFrame {
            val now = jsNow()
            if (last > 0) { val gap = now - last; count++; sum += gap; if (gap > worst) worst = gap }
            last = now
            if (running) frame()
        }
    }
}

/** The bench's game: its board after both keeps ([first]) and after Alice plays a land ([second], also as its event). */
private class BenchGame {
    val alice = "alice"
    private val bob = "bob"
    val objects: Int
    val snapshotChars: Int
    val deltaChars: Int
    val encodeMs: Double
    val first: Transfer
    val second: Transfer

    class Transfer(private val snapshot: BoardTransfer.Snapshot, private val deltas: Map<Long, BoardTransfer.Delta> = emptyMap()) : BoardTransfer {
        var snapshots = 0
        override fun snapshot() = snapshot.also { snapshots++ }
        override fun since(from: Long) = deltas[from]
    }

    init {
        fun land(name: String) = CardDefinition(Layout.Normal, listOf(FaceDefinition(name, supertypes = listOf(Supertype.Basic),
            types = listOf(CardType.Land), subtypes = listOf(Subtype(name)))))
        fun creature(name: String, cost: String, kind: String, size: Int) = CardDefinition(Layout.Normal, listOf(FaceDefinition(name,
            ManaCost.parse(cost), types = listOf(CardType.Creature), subtypes = listOf(Subtype(kind)),
            power = PrintedNumber.Fixed(size), toughness = PrintedNumber.Fixed(size))))
        val deck = List(12) { land("Mountain") } + List(12) { land("Forest") } +
            List(18) { creature("Bench Bear", "{1}{G}", "Bear", 2) } + List(18) { creature("Bench Brute", "{2}{R}", "Ogre", 3) }
        val seats = listOf(alice, bob).map { p -> Seat(PlayerId(p), deck.mapIndexed { i, card -> DeckCard(CardId("$p-$i"), card) }) }
        val model = GameModel(Engine.newGame(seats), TableState(secret = LineRandom.secret(ByteArray(32) { 5 })))
        fun event(id: String, parent: String, author: String, input: (GameState) -> Input) =
            EventBuilder(model, id, parent, author).run { perform(input(model.engine)); answerRandoms(); build() }
        // As GameDevice.openingInput: the open decision when it is the player's, else a declaration ahead of it
        fun keep(p: String): (GameState) -> Input = { s ->
            val d = s.pending?.decision
            if (d != null && d.player == PlayerId(p)) Input.Answer(d.id, Answer.Option("Keep")) else Input.SetupAnswer(PlayerId(p), Answer.Option("Keep"))
        }
        event("start", "root", "server") {
            Input.StartGame(StartOptions(startingPlayer = PlayerId(alice), simultaneousMulligans = true, freeFirstMulligan = true))
        }
        event("keep-b", "start", bob, keep(bob))
        event("keep-a", "keep-b", alice, keep(alice))
        // On to Alice's first main phase, where she may play a land
        var parent = "keep-a"
        var passes = 0
        while (Engine.options(model.engine, PlayerId(alice)).none { it is Input.PlayLand }) {
            check(passes < 20) { "no land play after $passes passes, step ${model.engine.turn}" }
            val holder = requireNotNull(model.engine.priority.holder) { "nobody holds priority, step ${model.engine.turn}" }
            event("pass-$passes", parent, holder.value) { Input.PassPriority(holder) }
            parent = "pass-${passes++}"
        }
        val codec = EngineJson(GameDefinitions.of(model.engine))
        val at = jsNow()
        val snapshot = BoardTransfer.Snapshot(codec.encodeDefinitions(), codec.encodeModel(model))
        encodeMs = jsNow() - at
        objects = model.engine.objects.size
        snapshotChars = snapshot.definitions.length + snapshot.model.length
        first = Transfer(snapshot)
        val land = event("land", parent, alice) { s ->
            Engine.options(s, PlayerId(alice)).first { it is Input.PlayLand }
        }
        val delta = BoardTransfer.Delta(listOf(codec.encodeEvent(land)))
        deltaChars = delta.apply.sumOf { it.length }
        second = Transfer(BoardTransfer.Snapshot(codec.encodeDefinitions(), codec.encodeModel(model)), mapOf(1L to delta))
    }
}

private fun jsNow(): Double = js("performance.now()")
private fun jsFrame(action: () -> Unit): Unit = js("requestAnimationFrame(() => action())")
private fun jsBenchTitle(text: String): Unit = js("document.title = text")
private fun jsShowBench(text: String): Unit = js("""{
    let pre = document.getElementById('engine-bench');
    if (!pre) {
        const loading = document.getElementById('atw-loading');
        if (loading) loading.remove();
        pre = document.createElement('pre');
        pre.id = 'engine-bench';
        pre.style.cssText = 'margin: 16px; font: 13px monospace; white-space: pre-wrap;';
        document.body.appendChild(pre);
    }
    pre.textContent = text;
}""")
