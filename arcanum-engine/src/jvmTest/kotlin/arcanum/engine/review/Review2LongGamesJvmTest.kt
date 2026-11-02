package arcanum.engine.review

import kotlin.test.Test

/**
 * Review 2: many whole games ([LongGame]), seeds 1..N, with N from the system property `arcanum.longGames`
 * (`./scripts/gradle.sh :arcanum-engine:jvmTest -Parcanum.longGames=50 --tests '*Review2LongGamesJvmTest*'`); 0 by
 * default, so the normal run skips it. `arcanum.longGames.players` sets the number of players (2 by default),
 * `arcanum.longGames.from` the first seed, `arcanum.longGames.viewer` a player whose view the games are played on
 * (D60: "alice"), and `arcanum.longGames.malformed=true` tries odd answers at every decision. Every failure is
 * collected and reported at the end with its seed.
 */
class Review2LongGamesJvmTest {
    @Test
    fun manySeededGamesKeepEveryInvariant() {
        val games = System.getProperty("arcanum.longGames")?.toIntOrNull() ?: 0
        if (games <= 0) return
        val from = System.getProperty("arcanum.longGames.from")?.toLongOrNull() ?: 1L
        val players = System.getProperty("arcanum.longGames.players")?.toIntOrNull() ?: 2
        val malformed = System.getProperty("arcanum.longGames.malformed") == "true"
        val viewer = System.getProperty("arcanum.longGames.viewer")?.takeIf { it.isNotBlank() }?.let { arcanum.engine.state.PlayerId(it) }
        val failures = (from until from + games).mapNotNull { seed ->
            try {
                LongGame(seed, players = players, jsonEvery = 5, skip = Review2LongGamesTest.KNOWN, viewer = viewer, malformed = malformed).play()
                null
            } catch (e: LongGameFailure) {
                e.message + (e.cause?.let { "\n  caused by ${it::class.simpleName}: ${it.message}" } ?: "")
            }
        }
        if (failures.isNotEmpty()) throw AssertionError("${failures.size} of $games games failed:\n\n" + failures.joinToString("\n\n"))
    }
}
