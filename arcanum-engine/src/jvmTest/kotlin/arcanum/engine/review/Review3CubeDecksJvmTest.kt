package arcanum.engine.review

import arcanum.engine.state.PlayerId
import kotlin.test.Test

/**
 * Review 3: many whole games between every pair of themed cube decks ([Review3Decks]): N seeds per pair from
 * `arcanum.review3.games` (`./scripts/gradle.sh :arcanum-engine:jvmTest -Parcanum.review3.games=5
 * --tests '*Review3CubeDecksJvmTest*'`), each on full information, on alice's view and on bob's view ([LongGame]), and
 * the same games through the public-API host keeping both views ([FakeHost]). 0 by default: the normal run skips it.
 * Every failure is collected and reported at the end with its seed; the host's findings are printed.
 */
class Review3CubeDecksJvmTest {
    @Test
    fun everyPairOfThemedDecks() {
        val games = System.getProperty("arcanum.review3.games")?.toIntOrNull() ?: 0
        if (games <= 0) return
        val failures = mutableListOf<String>()
        Review3Decks.pairs.forEachIndexed { p, (a, b) ->
            (0 until games).forEach { g ->
                val seed = 1000L + p * 100 + g
                listOf<PlayerId?>(null, PlayerId("alice"), PlayerId("bob")).forEach { viewer ->
                    try {
                        val t0 = System.nanoTime()
                        val game = LongGame(seed, jsonEvery = 5, viewer = viewer, decks = Review3Decks.match(a, b))
                        val turns = game.play()
                        println("REVIEW3 $a vs $b seed $seed view ${viewer ?: "full"}: $turns turns, ${game.inputs} inputs, " +
                            "${(System.nanoTime() - t0) / 1_000_000} ms")
                    } catch (e: LongGameFailure) {
                        failures += "$a vs $b, view ${viewer ?: "full"}: " + e.message +
                            (e.cause?.let { "\n  caused by ${it::class.simpleName}: ${it.message}" } ?: "")
                    }
                }
                val host = FakeHost(seed, Review3Decks.match(a, b), optionsEvery = 50)
                try {
                    host.play()
                } catch (e: Throwable) {
                    failures += "$a vs $b host seed $seed: ${e::class.simpleName}: ${e.message}"
                }
                println("REVIEW3 host " + host.report())
            }
        }
        if (failures.isNotEmpty()) throw AssertionError("${failures.size} games failed:\n\n" + failures.joinToString("\n\n"))
    }
}
