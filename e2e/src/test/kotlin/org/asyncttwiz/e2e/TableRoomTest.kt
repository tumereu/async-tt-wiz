package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.performClick
import org.asyncttwiz.android.EdgeToEdgePreferences
import org.asyncttwiz.appui.AppPlatform
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-11 ("More vertical room on the phone table"): in portrait the other player's hand is a small fan of card
 * backs with its count in their life and pile row, the battlefields taking its old row; their hand's cards revealed still
 * open in the reveal panel. Settings → Table → "Edge to edge" (off by default) draws the table into the camera's strip.
 */
class TableRoomTest : E2e() {
    private val inPanel = hasAnyAncestor(hasContentDescription("Reveal"))
    private fun deck() = TestDeck("Exposures", listOf(TestCards.mountain to 20, TestCards.exposure to 20))

    /** The other player's hand cards on [player]'s table: card backs. */
    private fun backs(player: E2ePlayer, owner: E2ePlayer) =
        player.nodes(hasContentDescription("Covered card, owner ${owner.name}, ", substring = true)).fetchSemanticsNodes().map { it.boundsInRoot }

    // ZON-001, TRN-034, HIS-110: on Alice's table Bob's hand is a fan of card backs in his row, after his piles and level with
    // his life square, his battlefield starting under that row; Alice casts Ember Exposure ("Target player reveals their
    // hand") at Bob: his hand's cards open in her reveal panel, the fan stays in his row
    @Test fun theOtherPlayersHandIsAFanInTheirRowAndItsRevealOpensInAPanel() {
        startGame(deck(), deck(), deal = { a, _ -> "Ember Exposure" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        val life = alice.described("${bob.name}, life 20", substring = true).fetchSemanticsNode().boundsInRoot
        val exile = alice.described("${bob.name} exile,", substring = true).fetchSemanticsNode().boundsInRoot
        val handSize = bob.state.player(bob.me).hand.size
        val cards = backs(alice, bob)
        assertEquals(handSize, cards.size, "his whole hand as card backs: ${alice.screen()}")
        assertTrue(cards.all { it.left > exile.right && it.center.y > life.top && it.center.y < life.bottom }, "in his row after his piles: $cards")
        assertTrue(alice.battlefieldBounds(bob).top >= life.bottom, "his battlefield under his row: ${alice.battlefieldBounds(bob)}")

        val names = bob.state.player(bob.me).hand.mapNotNull { cardName(bob.state, it) }
        alice.playFromHand("Ember Exposure", this)
        waitUntil("Alice's target bar") { alice.shows(hasText("Choose target player") and isHeading()) }
        alice.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
        waitUntil("Bob's hand in Alice's reveal panel", along = listOf(bob)) {
            alice.showsText("revealed · Ember Exposure", substring = true) && alice.shown.reveal != null
        }
        for (name in names.distinct()) assertTrue(alice.shows(hasContentDescription(name, substring = true) and inPanel),
            "$name in the panel: ${alice.screen()}")
        assertEquals(handSize, backs(alice, bob).size, "the fan stays in his row")
        alice.revealOk()
        waitUntil("Alice's panel closed") { !alice.showsRevealOk() }
    }

    // SYS-085: "Edge to edge" is off by default; Alice turns it on in Settings → Table (Settings itself stays clear of the
    // camera's strip), and once she opens the game her screen asks for the table edge to edge
    @Test fun edgeToEdgeTakesTheCameraStripOnTheTable() {
        EdgeToEdgePreferences(AppPlatform.current).enabled = false
        startGame()
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-SETTINGS")) }
        alice.tagged("menu-SETTINGS").performClick()
        waitUntil("Alice's settings") { alice.shows(hasTestTag("edge-to-edge-setting")) }
        assertTrue(alice.shows(hasTestTag("edge-to-edge-setting") and isOff()), "off by default")
        alice.tagged("edge-to-edge-setting").performClick()
        waitUntil("the switch on") { alice.shows(hasTestTag("edge-to-edge-setting") and isOn()) }
        assertTrue(EdgeToEdgePreferences(AppPlatform.current).enabled, "kept on the device")
        assertFalse(alice.edgeToEdge, "not outside the table")
        alice.activity.onBackPressedDispatcher.onBackPressed()
        bothOpen()
        waitUntil("Alice's table edge to edge") { alice.edgeToEdge }
        EdgeToEdgePreferences(AppPlatform.current).enabled = false
    }
}
