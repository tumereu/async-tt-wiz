package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.performClick
import org.asyncttwiz.protocol.AssumeReveals
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals

/**
 * Owner, 2026-11-07 (LOB-135): the game's assume reveal settings. With the defaults (the other player's library allowed,
 * hands not), Alice's phone assumes Bob's pass over her spell that reveals the top of his library; over one that reveals
 * his hand the wait's Assume stays greyed until Alice, the game's host, turns "Allow assumes that reveal hands" on from
 * Table actions → Game settings: then her phone goes on, and the server takes it.
 */
class AssumeRevealSettingsTest : E2e() {
    private fun deck(spell: TestCard) = TestDeck("Reveals", listOf(TestCards.mountain to 20, spell to 20))

    /** Alice casts [name] at Bob, who is away. */
    private fun castAtBob(name: String) {
        alice.playFromHand(name, this)
        waitUntil("Alice's target bar") { alice.shows(hasText("Choose target player") and isHeading()) }
        alice.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
    }

    private fun resolved(name: String) = alice.state.stack.isEmpty() &&
        alice.state.player(alice.me).graveyard.any { cardName(alice.state, it) == name }

    private val assume = hasTestTag("engine-wait-action") and hasContentDescription("Assume resolves")

    // LOB-135, TRN-067, HIS-096: with the game's defaults Bob's pass over the Augury (revealing his library's top two
    // cards) is assumed, at once or from the wait's Assume, and the Augury resolves with Bob away (on master the wait's
    // Assume was greyed: going on would show hidden cards)
    @Test fun anAssumptionOverTheOtherPlayersLibraryRevealGoesOnByDefault() {
        startGame(deck(TestCards.augury), deck(TestCards.augury), deal = { a, _ -> "Ember Augury" in hand(a) })
        assertEquals(AssumeReveals(), game.assumeReveals)
        bothOpen()
        bothKeep(bobAway = true)
        castAtBob("Ember Augury")
        waitUntil("the Augury resolved, or Bob's pass offered to assume") { resolved("Ember Augury") || alice.shows(assume and isEnabled()) }
        if (!resolved("Ember Augury")) alice.node(assume).performClick()
        waitUntil("the Augury resolved on Bob's assumed pass") {
            resolved("Ember Augury") && alice.engine { device.session.pending.isEmpty() }
        }
        waitUntil("the server's game past the Augury") { serverGame()?.let { snapshot -> snapshot.headId == alice.engine { device.session.head } } == true }
    }

    // LOB-135, TRN-067, TRN-071, TRN-010: with "Allow assumes that reveal hands" off (the default) the wait over Alice's
    // Exposure keeps its Assume greyed; Alice (the host) turns it on from Table actions → Game settings, and her phone
    // assumes Bob's pass: the Exposure resolves and the server takes it
    @Test fun anAssumptionOverAHandRevealWaitsUntilTheHostAllowsIt() {
        startGame(deck(TestCards.exposure), deck(TestCards.exposure), deal = { a, _ -> "Ember Exposure" in hand(a) })
        bothOpen()
        bothKeep(bobAway = true)
        castAtBob("Ember Exposure")
        waitUntil("Alice waiting for Bob: going on would show hidden cards") {
            (alice.status as? GameDevice.Status.Waiting)?.reason == GameDevice.Wait.HiddenCards
        }
        waitUntil("the greyed Assume on the main button") { alice.shows(assume and isNotEnabled()) }
        run(skip = Duration.ofSeconds(2))
        assertEquals(1, alice.state.stack.size, "nothing assumed: the Exposure waits on the Stack")
        alice.tap("Table actions")
        waitUntil("Alice's table actions") { alice.showsText("Game settings") }
        alice.text("Game settings").performClick()
        waitUntil("the game settings") { alice.shows(hasTestTag("assume-hands")) }
        alice.tagged("assume-hands").performClick()
        waitUntil("the Exposure resolved on Bob's assumed pass") {
            resolved("Ember Exposure") && alice.engine { device.session.pending.isEmpty() }
        }
        waitUntil("the server's game past the Exposure") {
            serverGame()?.let { snapshot -> snapshot.headId == alice.engine { device.session.head } } == true
        }
    }
}
