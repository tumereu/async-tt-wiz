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
        val host = FakeHost(102, Review3Decks.match("artifacts", "creatures"), optionsEvery = 40, maxInputs = 500, fillBeforePlay = true)
        host.play()
        assertTrue(host.views.all { it.diverged == null }, host.report())
        val wrong = host.findings.keys.filter { it.startsWith("public card") || it.startsWith("another player's hand") ||
            it.startsWith("an option only") || it.startsWith("a card put into another") }
        assertTrue(wrong.isEmpty(), host.report())
    }

    /** Without that workaround (#4): the other player's plays, casts and activations of cards a view doesn't know are
     * filled in publicly by the engine's own host fill, and both views follow the game, with the same checks. */
    // D60, CR 305.1, 601.2a, 602.2a
    @Test
    fun eachViewFollowsTheGameByTheEngineAlone() {
        val host = FakeHost(102, Review3Decks.match("artifacts", "creatures"), optionsEvery = 40, maxInputs = 500)
        host.play()
        assertTrue(host.views.all { it.diverged == null }, host.report())
        assertTrue(host.findings.keys.none { it.startsWith("public card") || it.startsWith("another player's hand") ||
            it.startsWith("an option only") || it.startsWith("a card put into another") }, host.report())
        assertTrue((host.findings["a play, cast or activation of a card unknown to the view"] ?: 0) > 0, host.report())
    }

    /** No view is asked to fill cards only another player may see (a scry, a search): answering it would put those cards
     * into the view (#5). The steps are worked out as in that player's view instead ([Decision.OtherView], D31), and the
     * views stay in step with the truth, never knowing the other player's hand. */
    // D60, D31
    @Test
    fun noViewIsAskedForAnotherPlayersPrivateFill() {
        val host = FakeHost(101, Review3Decks.match("artifacts", "spells"), optionsEvery = 40, maxInputs = 600)
        host.play()
        assertEquals(0, host.privateFillsInOtherViews, host.report())
        assertTrue(host.otherViewAnswers > 0, "no look by the other player happened: ${host.report()}")
        assertTrue(host.views.all { it.diverged == null }, host.report())
        assertTrue(host.findings.keys.none { it.startsWith("public card") || it.startsWith("another player's hand") ||
            it.startsWith("an option only") || it.startsWith("a card put into another") }, host.report())
    }
}
