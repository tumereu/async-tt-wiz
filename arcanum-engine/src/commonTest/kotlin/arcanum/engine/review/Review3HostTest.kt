package arcanum.engine.review

import arcanum.engine.cards.Cards
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Review 3: a host written against the public API only ([FakeHost]) plays themed cube decks ([Review3Decks]) on the
 * full-information state and keeps each player's view (D60) in step with it, through JSON between every call.
 */
class Review3HostTest {
    /** The themed decks are 40 cards of cube cards that parse. */
    @Test
    fun theThemedDecksAreFortyCubeCards() {
        Review3Decks.all.forEach { (name, deck) ->
            assertEquals(40, deck.size, name)
            deck.forEach { Cards.definition(it) }
        }
    }

    /** With the host filling the other player's played cards by hand first (a hand-made Change.Filled, see #4), both
     * views follow the whole game: everything public is the same as on the truth after every input, public cards are
     * known in each view, the other player's hand isn't, and each player's options in their view are the truth's. */
    // D56, D58, D60, D11
    @Test
    fun eachViewFollowsTheGameWithTheHostsFill() {
        val host = FakeHost(102, Review3Decks.match("artifacts", "creatures"), optionsEvery = 40, maxInputs = 500)
        host.play()
        assertTrue(host.views.all { it.diverged == null }, host.report())
        val wrong = host.findings.keys.filter { it.startsWith("public card") || it.startsWith("another player's hand") ||
            it.startsWith("an option only") || it.startsWith("a card put into another") }
        assertTrue(wrong.isEmpty(), host.report())
    }

    /** Without that workaround the views can't follow the first land the other player plays (#4). */
    // D60, CR 305.1
    @Ignore("review3 #4: a viewer's state refuses the other player's play from a hidden hand (\"unknown card\")")
    @Test
    fun eachViewFollowsTheGameByTheEngineAlone() {
        val host = FakeHost(102, Review3Decks.match("artifacts", "creatures"), maxInputs = 200, fillBeforePlay = false)
        host.play()
        assertTrue(host.views.all { it.diverged == null }, host.report())
    }

    /** No view is asked to fill cards only another player may see (a scry, a search): answering it puts those cards
     * into the view (#5, D31). */
    // D60, D31
    @Ignore("review3 #5: a view asks to fill cards only another player may see; the host has no other way on")
    @Test
    fun noViewIsAskedForAnotherPlayersPrivateFill() {
        val host = FakeHost(101, Review3Decks.match("artifacts", "spells"), maxInputs = 300)
        host.play()
        assertEquals(0, host.privateFillsInOtherViews, host.report())
    }
}
