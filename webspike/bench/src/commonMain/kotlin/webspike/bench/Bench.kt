package webspike.bench

import arcanum.engine.Engine
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import org.asyncttwiz.automatum.CastFlow
import org.asyncttwiz.automatum.EngineProjection
import org.asyncttwiz.protocol.EngineSnapshot
import org.asyncttwiz.protocol.game.GameDevice
import kotlin.time.TimeSource

/**
 * Web spike: the device's engine path on the heavy board, in the steps a player's actions take on a device (no threads,
 * no UI): load the game from the server's snapshot, project the table's board, begin a cast of a one-drop from hand
 * (CastFlow.start), pay it with the suggested payment, send it (the device performs the inputs), the device's own steps
 * after it (the other player's pass assumed, the spell resolving, the Sentinel's trigger and its passes), a single pass,
 * and End turn. Prints medians; the first run of each operation is reported apart (cold: JIT / wasm tier-up).
 */
object Bench {
    val OPS = listOf("load", "board", "castStart", "pay", "send", "afterCast", "pass", "endTurn")

    class Result(val first: Map<String, Double>, val medians: Map<String, Double>, val mins: Map<String, Double>, val runs: Int, val setupMs: Double) {
        fun report(label: String): String = buildString {
            appendLine("BENCH $label: board setup ${fmt(setupMs)} ms, $runs measured runs per op; End turn stops at $endTurnStop")
            appendLine("BENCH $label: op          first     median    min   (ms)")
            for (op in OPS) appendLine("BENCH $label: ${op.padEnd(11)} ${fmt(first[op] ?: 0.0).padStart(8)} ${fmt(medians[op] ?: 0.0).padStart(8)} ${fmt(mins[op] ?: 0.0).padStart(8)}")
        }
        fun json(): String = "{" + OPS.joinToString(",") { op -> "\"$op\":{\"first\":${fmt(first[op] ?: 0.0)},\"median\":${fmt(medians[op] ?: 0.0)},\"min\":${fmt(mins[op] ?: 0.0)}}" } +
            ",\"runs\":$runs,\"setupMs\":${fmt(setupMs)}}"
    }

    fun fmt(v: Double): String {
        val r = kotlin.math.round(v * 100) / 100
        val s = r.toString()
        return if ('.' in s) s else "$s.0"
    }

    private val clock = TimeSource.Monotonic
    private inline fun <T> timed(into: MutableMap<String, MutableList<Double>>, op: String, f: () -> T): T {
        val mark = clock.markNow()
        val r = f()
        into.getOrPut(op) { mutableListOf() } += mark.elapsedNow().inWholeNanoseconds / 1e6
        return r
    }

    /** Where the last End turn stopped (turn/step, status, the device's pending events). */
    var endTurnStop = ""
    private var board: BenchBoard? = null
    private var setupMs = 0.0

    /** The heavy board, built once (parsing the cards and making the snapshot). */
    fun board(): BenchBoard {
        board?.let { return it }
        val mark = clock.markNow()
        val made = BenchBoard()
        board = made
        setupMs = mark.elapsedNow().inWholeNanoseconds / 1e6
        return made
    }

    /** One run of every operation, each on a game loaded afresh. */
    fun once(times: MutableMap<String, MutableList<Double>>, snapshot: EngineSnapshot = board().snapshot) {
        val b = board()
        val me = PlayerId(b.a)
        // Cast a one-drop from hand, its passes after
        var device = timed(times, "load") { b.device(snapshot) }
        val tip = device.session.tip
        val shown = timed(times, "board") { EngineProjection.project(b.base, device.model, b.a, tip).board }
        val state = device.model.engine
        val begin = Engine.options(state, me).filterIsInstance<Input.Cast>()
            .sortedBy { c -> if (state.obj(c.card)?.definition == BenchCard.CUB.definition) 0 else 1 }
            .firstOrNull { c -> state.obj(c.card)?.definition == BenchCard.CUB.definition } ?: error("no Ember Cub to cast in hand")
        var seed = 0
        val flow = timed(times, "castStart") {
            CastFlow.start(CastFlow.Source.engine(state, shown, b.base.gameId), b.a, begin,
                CastFlow.Seeds { org.asyncttwiz.protocol.RandomV1.encodeSeed(ByteArray(32) { (seed++).toByte() }) })
        }
        val payment = flow.pending as? Decision.ManaPayment ?: error("no payment open: ${flow.pending}")
        val sending = timed(times, "pay") { flow.sending(payment.suggestion) }
        val inputs = sending.cast.inputs.toList()
        val acted = timed(times, "send") {
            val rest = inputs.drop(1).iterator()
            device.act(inputs.first()) { if (rest.hasNext()) rest.next() else null }
        }
        check(acted is GameDevice.Acted.Sent) { "cast not sent: $acted" }
        val before = device.session.tip
        timed(times, "afterCast") { device.advance(2_000) }
        check(device.session.tip != before) { "nothing happened after the cast" }
        // A single pass
        device = b.device(snapshot)
        timed(times, "pass") { device.act(Input.PassPriority(me)); device.advance(2_000) }
        // End turn
        device = b.device(snapshot)
        val endBefore = device.session.tip
        timed(times, "endTurn") { device.endTurn(2_000) }
        check(device.session.tip != endBefore) { "End turn didn't move on" }
        endTurnStop = "${device.model.engine.turn.number}/${device.model.engine.turn.step}, ${device.status}, events ${device.session.pending.size}"
    }

    /** [warmups] unmeasured runs after the first (reported apart), then [runs] measured ones. */
    fun run(runs: Int = 15, warmups: Int = 5, progress: (String) -> Unit = {}): Result {
        board()
        val first = HashMap<String, MutableList<Double>>()
        once(first)
        progress("first run done")
        repeat(warmups) { once(HashMap()) }
        progress("warm-up done")
        val times = HashMap<String, MutableList<Double>>()
        repeat(runs) { once(times); progress("run ${it + 1}/$runs") }
        fun median(l: List<Double>) = l.sorted()[l.size / 2]
        return Result(first.mapValues { it.value.first() }, times.mapValues { median(it.value) }, times.mapValues { it.value.min() }, runs, setupMs)
    }
}
