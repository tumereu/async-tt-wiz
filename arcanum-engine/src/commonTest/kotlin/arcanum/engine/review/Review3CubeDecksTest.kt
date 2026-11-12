package arcanum.engine.review

import arcanum.engine.cards.Cards
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Review 3: whole games between the themed cube decks ([Review3Decks]) with every invariant of [LongGame] checked after
 * every input. The normal suite plays two pairs; the long run (every pair, many seeds) is `Review3CubeDecksJvmTest`,
 * behind `-Parcanum.review3.games=N`.
 */
class Review3CubeDecksTest {
    /** The themed decks are 40 cards of cube cards that parse. */
    @Test
    fun theThemedDecksAreFortyCubeCards() {
        Review3Decks.all.forEach { (name, deck) ->
            assertEquals(40, deck.size, name)
            deck.forEach { Cards.definition(it) }
        }
    }

    /** Artifacts against graveyard and spells against creatures, seeded, to their end. */
    // CR 103, 104, D56, D58, D59
    @Test
    fun themedDecksPlayWholeGames() {
        LongGame(301, jsonEvery = 25, decks = Review3Decks.match("artifacts", "graveyard")).play()
        LongGame(302, jsonEvery = 25, decks = Review3Decks.match("spells", "creatures")).play()
    }
}
