package org.asyncttwiz.android

import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test

/**
 * Commander readiness (owner, 2026-11-11): what a board wipe killing 30 creatures costs the device's engine path when
 * both players have "whenever a creature dies" enchantments (60 triggers, all shown, HIS-112): the pass that resolves the
 * wipe (the engine, the device's own steps, building the board), each play shown's end (the device's dismissal and the
 * board published again; the last one lets the device resolve the triggers by itself), and what the table works out for
 * each play shown and for the scene at the Stack's peak. Prints medians, never asserts on time (owner rule).
 *
 * Runs only with `-Patw.bench=true`:
 * `./scripts/gradle.sh :androidApp:testDebugUnitTest -Patw.bench=true --tests '*MassTriggerBenchmark*' -i`
 * (`-Patw.bench.profile=true` adds a sampled profile of the last play's end).
 */
class MassTriggerBenchmark {
    private val enabled get() = System.getProperty("atw.bench") == "true"
    private val runs get() = System.getProperty("atw.bench.runs")?.toIntOrNull() ?: 5

    private val ledger = BenchCard("Ledger of Debts", "{1}{B}{B}", "Enchantment",
        "Whenever a creature dies, each opponent loses 1 life and you gain 1 life.")
    private val wipe = BenchCard("Last Reckoning", "{2}{W}{W}", "Sorcery", "Destroy all creatures.")
    private val plains = BenchCard("Plains", null, "Basic Land — Plains")
    private val pup = BenchCard("Cinder Pup", "{R}", "Creature — Elemental Dog", "", "1", "1")

    private val board = BenchBoard("mass", listOf(plains to 4, ledger to 1, pup to 15),
        listOf(BenchCard.MOUNTAIN to 20, BenchCard.SPARK to 12, BenchCard.CUB to 7), hand = listOf(wipe to 1), life = 200)

    private class Run(val cast: Double, val wipe: Double, val shows: Int, val describe: Double, val describeMax: Double,
        val scene: Double, val dismiss: Double, val dismissMax: Double, val last: Double, val stackAfter: Int,
        val resolve: Double, val passes: Int)

    @Test fun boardWipeWithSixtyDeathTriggers() {
        if (!enabled) return
        println("BENCH mass: ${board.permanents} permanents, ${board.parsedWarnings}")
        repeat(2) { once(false) }
        val measured = List(runs) { once(it == runs - 1) }
        fun med(f: (Run) -> Double) = measured.map(f).sorted()[measured.size / 2]
        val r = measured.first()
        println(("BENCH mass: cast %.1f ms; the pass resolving the wipe %.1f ms; %d plays shown: described %.2f ms each (max %.2f), " +
            "scene at the peak %.1f ms, each one's end but the last %.2f ms (max %.1f), the last one's end (the device resolving by " +
            "itself, %d left on the Stack) %.1f ms; then %d passes resolving %.1f ms [median of %d]").format(
            med { it.cast }, med { it.wipe }, r.shows, med { it.describe }, med { it.describeMax }, med { it.scene }, med { it.dismiss },
            med { it.dismissMax }, r.stackAfter, med { it.last }, r.passes, med { it.resolve }, measured.size))
        profile()
    }

    private fun once(sample: Boolean): Run {
        val game = board.game()
        val a = PlayerId(board.a)
        game.showPlays(true)
        fun ms(block: () -> Unit): Double { val t0 = System.nanoTime(); block(); return (System.nanoTime() - t0) / 1e6 }
        val wipeId = game.read { d -> d.model.engine.player(a).hand.first { d.model.engine.obj(it)?.definition == wipe.definition } }
        val cast = ms {
            game.act(Input.Cast(a, wipeId))
            while (true) {
                val d = game.read { it.model.engine.pending?.decision } ?: break
                check(d is Decision.ManaPayment) { "unexpected $d" }
                game.act(Input.Answer(d.id, Answer.ManaPayment(d.suggestion)))
            }
        }
        val wipe = ms { game.pass() }
        val shows = game.read { it.openShows }
        check(shows.size == 60) { "${shows.size} plays shown, stack ${game.read { it.model.engine.stack.size }}" }
        val play = game.play()
        val names = mapOf(board.a to "Alice", board.b to "Bob")
        val described = shows.map { show -> ms { PlayShows.of(play, show, board.a, names) } }
        val scene = ms { TablePresentation.scene(TableStatesFixture.game, play, board.a, 412f, 892f, false) }
        val dismissed = ArrayList<Double>()
        while (true) {
            val show = game.shown?.show ?: break
            val last = game.read { it.openShows.size } == 1
            dismissed += ms { if (last && sample) sampled { game.dismissShow(show) } else game.dismissShow(show) }
        }
        check(dismissed.size == 60) { "${dismissed.size} ended" }
        // The last one's end lets the device make its own steps: Alice's passes and Bob's assumed ones resolve the triggers
        val last = dismissed.removeAt(dismissed.size - 1)
        val stackAfter = game.read { it.model.engine.stack.size }
        val resolved = ArrayList<Double>()
        while (game.read { it.model.engine.stack.isNotEmpty() } && resolved.size < 400) {
            check(game.shown?.status == GameDevice.Status.YourMove) { "status ${game.shown?.status}" }
            resolved += ms { game.pass() }
        }
        return Run(cast, wipe, shows.size, described.average(), described.max(), scene, dismissed.average(), dismissed.max(),
            last, stackAfter, resolved.sum(), resolved.size)
    }

    /** Stack samples of this thread every half millisecond while [sampled] blocks run (with `-Patw.bench.profile=true`). */
    private val samples = java.util.concurrent.ConcurrentLinkedQueue<Array<StackTraceElement>>()

    private fun sampled(block: () -> Unit) {
        if (System.getProperty("atw.bench.profile") != "true") return block()
        val target = Thread.currentThread()
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val sampler = Thread { while (!stop.get()) { samples += target.stackTrace; Thread.sleep(0, 500_000) } }.apply { isDaemon = true; start() }
        try { block() } finally { stop.set(true); sampler.join() }
    }

    private fun profile() {
        if (samples.isEmpty()) return
        val own = { e: StackTraceElement -> e.className.startsWith("arcanum.") || e.className.startsWith("org.asyncttwiz.") }
        fun key(e: StackTraceElement) = e.className.substringAfterLast('.').substringBefore('$') + "." + e.methodName.substringBefore('$')
        val all = samples.filter { s -> s.any(own) }
        val inclusive = HashMap<String, Int>()
        val self = HashMap<String, Int>()
        for (s in all) {
            s.filter(own).map(::key).toSet().forEach { inclusive.merge(it, 1, Int::plus) }
            s.firstOrNull(own)?.let { self.merge(key(it), 1, Int::plus) }
        }
        val n = all.size.coerceAtLeast(1)
        println("BENCH profile of the last play's end: ${all.size} samples")
        inclusive.entries.sortedByDescending { it.value }.take(60).forEach { (k, v) -> println("BENCH   incl %5.1f%% %s".format(100.0 * v / n, k)) }
        self.entries.sortedByDescending { it.value }.take(25).forEach { (k, v) -> println("BENCH   self %5.1f%% %s".format(100.0 * v / n, k)) }
    }
}
