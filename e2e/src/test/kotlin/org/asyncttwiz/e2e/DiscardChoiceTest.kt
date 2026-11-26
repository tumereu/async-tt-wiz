package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import arcanum.engine.decision.Decision
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Prod problem report DH9MW5 (0.3.4, 2026-11-26): Thirst for Knowledge's "Then discard two cards unless you discard an artifact
 * card" (CR 118.12a) answered itself. After "no", the two cards to discard were picked by the phone ~30 ms after the panel showed
 * (Quick took the engine's sample answer, the first two cards of the hand, for the question's only answer). Alice, on Quick, casts
 * a Cog Reverie (the same text, free) with two different artifacts among the other cards of her hand: whichever way she answers,
 * the cards discarded are the ones she picks, and nothing goes before her OK.
 */
class DiscardChoiceTest : E2e() {
    private val inChoice = hasAnyAncestor(hasContentDescription("Automatum choice"))
    private val inHandArea = hasAnyAncestor(hasContentDescription("Hand area"))

    private fun hand() = alice.state.player(alice.me).hand
    private fun graveyard() = alice.state.player(alice.me).graveyard.map { cardName(alice.state, it).toString() }

    /** Alice casts her Cog Reverie and it resolves as far as its question "Discard an artifact card?". */
    private fun toTheQuestion() {
        startGame(TestDeck("Reveries", listOf(TestCards.mountain to 20, TestCards.reverie to 10, TestCards.geode to 5, TestCards.engineRock to 5)),
            aliceSettings = PlaySettings(mode = ResponseMode.AutoSkip),
            deal = { a, _ -> "Cog Reverie" in hand(a) && "Ember Geode" in hand(a) && "Ember Engine" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        val before = hand().size
        alice.playFromHand("Cog Reverie", this)
        waitUntil("the Reverie's question", along = listOf(bob)) {
            if (alice.stepArrow() == "Resolve" || alice.stepArrow() == "Pass") alice.once { alice.tap(alice.stepArrow()!!) }
            alice.state.pending?.decision is Decision.May && alice.showsText("Yes") && alice.showsText("No")
        }
        assertEquals(before - 1 + 3, hand().size, "three cards drawn")
    }

    /** The panel's choice stays open for Alice for a while: nothing discarded by itself. */
    private fun staysOpen(what: String) {
        repeat(4) { run(skip = Duration.ofMillis(500)) }
        assertIs<Decision.ChooseObjects>(alice.state.pending?.decision, "$what still asked: ${alice.screen()}")
        assertEquals(1, alice.state.stack.size, "the Reverie still resolving")
        assertTrue(graveyard().isEmpty(), "nothing discarded by itself: ${graveyard()}")
        // Problem report DH9MW5: the app log says why Quick left the choice to her
        assertTrue("Quick leaves the player's own ChooseObjects" in E2ePlayer.log(), "the reason logged")
    }

    // SYS-208, SYS-063, STK-043, STK-054, CR 118.12a (problem report DH9MW5): "no" asks her two cards of her nine; the phone waits for her picks,
    // and the two she moves to Discard are the ones discarded
    @Test fun afterNoThePlayerPicksTheTwoCardsToDiscard() {
        toTheQuestion()
        alice.text("No").performClick()
        waitUntil("the discard panel") { alice.showsText("Hand · ${hand().size}") && alice.showsText("Discard · 0") }
        staysOpen("the discard of two")
        val cards = hand().size
        // An artifact and a land: any two of her cards may go after "no"
        alice.nodes(hasContentDescription("Ember Geode", substring = true) and inHandArea)[0].performClick()
        waitUntil("one card in Discard") { alice.showsText("Discard · 1") }
        alice.nodes(hasContentDescription("Mountain", substring = true) and inHandArea)[0].performClick()
        waitUntil("two cards in Discard") { alice.showsText("Discard · 2") }
        waitUntil("OK enabled") { alice.showsControl("OK") }
        alice.tap("OK")
        waitUntil("her two cards discarded, on the server", along = listOf(bob)) {
            hand().size == cards - 2 && alice.state.stack.isEmpty() && alice.engine { device.session.pending.isEmpty() }
        }
        assertEquals(listOf("Cog Reverie", "Ember Geode", "Mountain"), graveyard().sorted(), "the cards she picked")
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }

    // SYS-208, SYS-063, CR 118.12a (problem report DH9MW5): "yes" asks which of her two artifact cards to discard; the phone waits for her pick
    @Test fun afterYesThePlayerPicksTheArtifactToDiscard() {
        toTheQuestion()
        alice.text("Yes").performClick()
        val engine = hasContentDescription("Ember Engine", substring = true) and inChoice
        waitUntil("the choice of the artifact") { alice.state.pending?.decision is Decision.ChooseObjects && alice.shows(engine) }
        staysOpen("the artifact's choice")
        val cards = hand().size
        alice.nodes(engine)[0].performClick()
        waitUntil("OK enabled") { alice.showsControl("OK") }
        alice.tap("OK")
        waitUntil("her artifact discarded, on the server", along = listOf(bob)) {
            hand().size == cards - 1 && alice.state.stack.isEmpty() && alice.engine { device.session.pending.isEmpty() }
        }
        assertEquals(listOf("Cog Reverie", "Ember Engine"), graveyard().sorted(), "the artifact she picked")
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}
