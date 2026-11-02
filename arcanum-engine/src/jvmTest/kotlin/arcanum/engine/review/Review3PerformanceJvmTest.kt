package arcanum.engine.review

import arcanum.api.CounterKind
import arcanum.engine.Engine
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.GameState
import kotlin.test.Test

/**
 * Review 3: what `Engine.view`, `Engine.options` and `Engine.perform` cost on large boards (printed, never asserted:
 * the owner's rule is no timing tests). Runs only with `-Parcanum.review3.perf=true`:
 * `./scripts/gradle.sh :arcanum-engine:jvmTest -Parcanum.review3.perf=true --tests '*Review3PerformanceJvmTest*' -i`.
 */
class Review3PerformanceJvmTest {
    private val enabled get() = System.getProperty("arcanum.review3.perf") == "true"

    /** Median milliseconds of [times] runs of [block] (after one warm-up). */
    private fun ms(times: Int = 3, block: () -> Unit): Double {
        block()
        return List(times) {
            val t0 = System.nanoTime()
            block()
            (System.nanoTime() - t0) / 1e6
        }.sorted()[times / 2]
    }

    private fun report(label: String, s: GameState) {
        val view = ms { Engine.view(s) }
        val options = ms(1) { Engine.options(s, s.priority.holder ?: s.turn.active) }
        val pass = ms(1) { Engine.perform(s, Input.PassPriority(s.priority.holder ?: s.turn.active)) }
        println("PERF $label: permanents ${s.battlefield.size}, effects ${s.effects.size}: view ${"%.1f".format(view)} ms, " +
            "options ${"%.1f".format(options)} ms (${Engine.options(s, s.priority.holder ?: s.turn.active).size} listed), " +
            "pass priority ${"%.1f".format(pass)} ms")
    }

    /** Elves with lords (layer 7c from static abilities) and +1/+1 counters (more 7c effects) at growing sizes. */
    @Test
    fun lordsAndCountersAtGrowingSizes() {
        if (!enabled) return
        // Baselines: 40 permanents without static abilities; lords without counters
        scenario {
            repeat(8) { alice.battlefield("Elvish Seer", "Thicketwood Elves", "Tinkered Mite", "Forest"); bob.battlefield("Elvish Seer") }
            alice.battlefield("Mountain"); alice.hand("Storm Spark")
            report("40 permanents without static abilities", state)
        }
        scenario {
            repeat(4) { alice.battlefield("Elvish Hierophant", "Bough-Crowned Oracle", "Elvish Seer", "Thicketwood Elves", "Lord of the Aetherforge")
                bob.battlefield("Elvish Hierophant", "Elvish Seer", "Tinkered Mite", "Iron Foreman", "Forest") }
            alice.battlefield("Mountain"); alice.hand("Storm Spark")
            report("lords ×20 without counters", state)
        }
        listOf(5, 10, 15, 20, 30).forEach { n ->
            scenario {
                repeat(n / 5) {
                    alice.battlefield("Elvish Hierophant", "Bough-Crowned Oracle", "Elvish Seer", "Thicketwood Elves",
                        counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
                    alice.battlefield("Lord of the Aetherforge")
                    bob.battlefield("Elvish Hierophant", "Elvish Seer", "Tinkered Mite", "Iron Foreman", "Forest")
                }
                alice.battlefield("Mountain")
                alice.hand("Storm Spark")
                report("lords ×$n", state)
                val cast = ms(1) { castAndResolve(this) }
                println("PERF lords ×$n: cast Storm Spark at a lord and resolve it (SBA, layers again) ${"%.1f".format(cast)} ms")
            }
        }
    }

    private fun castAndResolve(s: Scenario) {
        var st = s.state.deepCopy()
        fun go(i: Input) { Engine.perform(st, i).changes.forEach { st.apply(it) } }
        go(Input.Cast(s.alice.id, s.card("Storm Spark")))
        val d = st.pending?.decision as? arcanum.engine.decision.Decision.Targets ?: return
        go(Input.Answer(d.id, arcanum.engine.decision.Answer.Targets(listOf(d.options.first { it is arcanum.engine.state.TargetRef.Object }))))
        val p = st.pending?.decision as? arcanum.engine.decision.Decision.ManaPayment ?: return
        go(Input.Answer(p.id, arcanum.engine.decision.Answer.ManaPayment(p.suggestion)))
        go(Input.PassPriority(s.alice.id))
        go(Input.PassPriority(s.bob.id))
    }

    /** Ten death triggers at once: Crimson Mourners (each with a target) and a creature dying. */
    @Test
    fun tenTriggersAtOnce() {
        if (!enabled) return
        scenario {
            alice.battlefield(*Array(10) { "Crimson Mourner" })
            alice.battlefield("Mountain")
            alice.hand("Storm Spark")
            bob.battlefield("Elvish Seer")
            report("ten Mourners", state)
            val t = ms(3) {
                var st = state.deepCopy()
                fun go(i: Input) { Engine.perform(st, i).changes.forEach { st.apply(it) } }
                go(Input.Cast(alice.id, card("Storm Spark")))
                val d = st.pending!!.decision as arcanum.engine.decision.Decision.Targets
                go(Input.Answer(d.id, arcanum.engine.decision.Answer.Targets(listOf(arcanum.engine.state.TargetRef.Object(card("Elvish Seer"))))))
                val p = st.pending!!.decision as arcanum.engine.decision.Decision.ManaPayment
                go(Input.Answer(p.id, arcanum.engine.decision.Answer.ManaPayment(p.suggestion)))
                go(Input.PassPriority(alice.id))
                val t0 = System.nanoTime()
                go(Input.PassPriority(bob.id))
                println("PERF ten Mourners: the pass that resolves Storm Spark and detects 10 triggers ${(System.nanoTime() - t0) / 1_000_000} ms; " +
                    "pending ${st.pending?.decision?.let { it::class.simpleName }}")
                val o0 = System.nanoTime()
                val listed = Engine.options(st, alice.id).size
                println("PERF ten Mourners: options at the trigger order/targets decision ${(System.nanoTime() - o0) / 1_000_000} ms ($listed listed)")
            }
            println("PERF ten Mourners: whole sequence ${"%.1f".format(t)} ms")
        }
    }

    /** A long game's per-input cost (themed decks), split by input kind. */
    @Test
    fun aLongGamesInputs() {
        if (!enabled) return
        val game = LongGame(300, jsonEvery = 1_000_000, decks = Review3Decks.match("artifacts", "creatures"))
        val t0 = System.nanoTime()
        val turns = game.play()
        val total = (System.nanoTime() - t0) / 1e6
        println("PERF long game artifacts vs creatures: $turns turns, ${game.inputs} inputs, ${"%.0f".format(total)} ms in all " +
            "(${"%.1f".format(total / game.inputs)} ms per input, invariant checks and reverts included)")
    }
}
