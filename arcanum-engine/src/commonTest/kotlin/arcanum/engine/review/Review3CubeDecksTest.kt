package arcanum.engine.review

import arcanum.engine.state.PlayerId
import kotlin.test.Test

/**
 * Review 3: whole games between the themed cube decks ([Review3Decks]) with every invariant of [LongGame] checked after
 * every input, on full information and on a player's view. The normal suite plays two pairs and one view; the long run
 * (every pair, many seeds) is `Review3CubeDecksJvmTest`, behind `-Parcanum.review3.games=N`.
 */
class Review3CubeDecksTest {
    /** Artifacts against graveyard and spells against creatures, seeded, to their end. */
    // CR 103, 104, D56, D58, D59
    @Test
    fun themedDecksPlayWholeGames() {
        LongGame(301, jsonEvery = 25, decks = Review3Decks.match("artifacts", "graveyard")).play()
        LongGame(302, jsonEvery = 25, decks = Review3Decks.match("spells", "creatures")).play()
    }

    /** A themed game on bob's view (D60): public cards known, hidden ones not. */
    // D60, D25
    @Test
    fun aThemedGameOnAPlayersView() {
        LongGame(303, jsonEvery = 25, viewer = PlayerId("bob"), decks = Review3Decks.match("graveyard", "spells")).play()
    }
}
