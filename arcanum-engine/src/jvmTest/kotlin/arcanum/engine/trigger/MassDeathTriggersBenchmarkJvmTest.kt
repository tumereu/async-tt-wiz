package arcanum.engine.trigger

import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import kotlin.test.Test

/**
 * Commander readiness (owner, 2026-11-11): what a board wipe killing about 30 creatures with death triggers costs the
 * engine ([MassDeathTriggersScenariosTest]'s boards): the pass that resolves the wipe and puts the triggers on the Stack,
 * each target and order answer, the view and options at the Stack's peak, and resolving every trigger. Printed (medians
 * of warm runs), never asserted: the owner's rule is no timing tests. Runs only with `-Parcanum.massTriggers.perf=true`:
 * `./scripts/gradle.sh :arcanum-engine:jvmTest -Parcanum.massTriggers.perf=true --tests '*MassDeathTriggersBenchmarkJvmTest*' -i`.
 */
class MassDeathTriggersBenchmarkJvmTest {
    private val enabled get() = System.getProperty("arcanum.massTriggers.perf") == "true"

    private class Case(val name: String, val players: Int, val setup: Scenario.() -> Unit)

    private val cases = listOf(
        Case("2p, 30 deaths, each opponent loses 1", 2) {
            alice.battlefield("Plains", "Plains", "Plains", "Plains", MassTriggerCards.ledger)
            repeat(15) { alice.battlefield(MassTriggerCards.pup); bob.battlefield(MassTriggerCards.pup) }
        },
        Case("2p, 30 deaths, target player loses 1", 2) {
            alice.battlefield("Plains", "Plains", "Plains", "Plains", MassTriggerCards.bloodLedger)
            repeat(15) { alice.battlefield(MassTriggerCards.pup); bob.battlefield(MassTriggerCards.pup) }
        },
        Case("2p, 30 deaths, two sources ordered (60)", 2) {
            alice.battlefield("Plains", "Plains", "Plains", "Plains", MassTriggerCards.ledger, MassTriggerCards.bloodLedger)
            repeat(15) { alice.battlefield(MassTriggerCards.pup); bob.battlefield(MassTriggerCards.pup) }
        },
        Case("2p, 30 deaths, doubled (60)", 2) {
            alice.battlefield("Plains", "Plains", "Plains", "Plains", MassTriggerCards.ledger, MassTriggerCards.echo)
            repeat(15) { alice.battlefield(MassTriggerCards.pup); bob.battlefield(MassTriggerCards.pup) }
        },
        Case("4p, 32 deaths, a Ledger each (128)", 4) {
            alice.battlefield("Plains", "Plains", "Plains", "Plains")
            for (p in players) { p.battlefield(MassTriggerCards.ledger); repeat(8) { p.battlefield(MassTriggerCards.pup) } }
        },
        Case("4p, 32 deaths, a Ledger each, Bob's doubled (160)", 4) {
            alice.battlefield("Plains", "Plains", "Plains", "Plains")
            bob.battlefield(MassTriggerCards.echo)
            for (p in players) { p.battlefield(MassTriggerCards.ledger); repeat(8) { p.battlefield(MassTriggerCards.pup) } }
        },
    )

    private class Run(val wipe: Double, val answers: Double, val answerMax: Double, val answered: Int, val view: Double,
        val options: Double, val resolve: Double, val resolveMax: Double, val passes: Int, val stack: Int)

    @Test
    fun massDeathTriggers() {
        if (!enabled) return
        val runs = System.getProperty("arcanum.massTriggers.runs")?.toIntOrNull() ?: 5
        for (case in cases) {
            scenario(players = case.players) {
                case.setup(this)
                alice.hand(MassTriggerCards.wipe)
                for (p in players) p.life = 1_000
                repeat(2) { once(this) }
                val measured = List(runs) { once(this) }
                fun med(f: (Run) -> Double) = measured.map(f).sorted()[measured.size / 2]
                val r = measured.first()
                println("MASS ${case.name}: stack peak ${r.stack}; wipe pass %.1f ms; %d answers %.1f ms (max %.1f); view %.1f ms; options %.1f ms; %d passes resolving %.1f ms (max %.1f) [median of %d]".format(
                    med { it.wipe }, r.answered, med { it.answers }, med { it.answerMax }, med { it.view }, med { it.options },
                    r.passes, med { it.resolve }, med { it.resolveMax }, measured.size))
            }
        }
    }

    /** One run on a copy of the scenario's state: the wipe cast, passed, answered and resolved. */
    private fun once(s: Scenario): Run {
        val st = s.state.deepCopy()
        fun go(i: Input): Double {
            val t0 = System.nanoTime()
            Engine.perform(st, i).changes.forEach { st.apply(it) }
            return (System.nanoTime() - t0) / 1e6
        }
        go(Input.Cast(s.alice.id, s.card(MassTriggerCards.wipe)))
        while (true) when (val d = st.pending?.decision) {
            is Decision.ManaPayment -> go(Input.Answer(d.id, Answer.ManaPayment(d.suggestion)))
            null -> break
            else -> error("unexpected $d")
        }
        // Everyone passes: the last pass resolves the wipe
        var wipe = 0.0
        while (st.stack.isNotEmpty() && st.pending == null && st.stack.any { (it as? ObjectId.Card) != null }) {
            wipe = go(Input.PassPriority(holder(st)))
        }
        var answers = 0.0
        var answerMax = 0.0
        var answered = 0
        while (true) {
            val d = st.pending?.decision ?: break
            val answer = when (d) {
                is Decision.Targets -> Answer.Targets(listOf(d.options.first { it is TargetRef.Player && it.id != s.alice.id }))
                is Decision.OrderTriggers -> Answer.Order(d.triggers.indices.toList())
                else -> error("unexpected $d")
            }
            val t = go(Input.Answer(d.id, answer))
            answers += t
            answerMax = maxOf(answerMax, t)
            answered++
        }
        val stack = st.stack.size
        val v0 = System.nanoTime()
        Engine.view(st)
        val view = (System.nanoTime() - v0) / 1e6
        val o0 = System.nanoTime()
        Engine.options(st, holder(st))
        val options = (System.nanoTime() - o0) / 1e6
        var resolve = 0.0
        var resolveMax = 0.0
        var passes = 0
        while (st.stack.isNotEmpty()) {
            check(st.pending == null) { "unexpected ${st.pending}" }
            val t = go(Input.PassPriority(holder(st)))
            resolve += t
            resolveMax = maxOf(resolveMax, t)
            passes++
        }
        return Run(wipe, answers, answerMax, answered, view, options, resolve, resolveMax, passes, stack)
    }

    private fun holder(st: GameState): PlayerId = st.priority.holder ?: error("nobody has priority")
}
