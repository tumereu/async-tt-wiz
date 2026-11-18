package org.asyncttwiz.e2e

import org.junit.Test
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-17 (catalog review thread on SYS-171): "maybe mana abilities could get a different color glow that's also more
 * faint? if mana and non mana then show non mana glow?" A permanent whose only use now is a mana ability glows fainter in blue.
 */
class ManaGlowTest : E2e() {
    /** How blue the table is at [x], [y] of [picture]: how far its blue rises above its green (the battlefields' tints are
     * greener than blue, so this is below 0 there). */
    private fun blueAt(picture: android.graphics.Bitmap, x: Float, y: Float): Int {
        val pixel = picture.getPixel(x.toInt(), y.toInt())
        return (pixel and 255) - ((pixel shr 8) and 255)
    }

    /** How much bluer than the battlefield beside it [player]'s table is just above the top of [owner]'s Mountain. */
    private fun glowAboveTheMountain(player: E2ePlayer, owner: E2ePlayer): Int {
        val card = player.battlefieldCard("Mountain", owner).fetchSemanticsNode().boundsInWindow
        val picture = player.picture()
        val y = card.top - 2f
        return blueAt(picture, card.center.x, y) - blueAt(picture, card.right + 40f, y)
    }

    // TBL-117, SYS-171: Alice plays her Mountain at her Main 1: on her phone it glows in the fainter blue (its only use now is mana);
    // on Bob's phone her Mountain has no glow (never the other player's cards)
    @Test fun aLandWhoseOnlyUseIsManaGlowsFaintlyInBlue() {
        startGame(deal = { a, _ -> "Mountain" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").size == 1 && alice.inOwnMain() }
        waitUntil("both settled") { alice.settled(this) && bob.settled(this) }
        // The glow fades in
        repeat(3) { run(skip = java.time.Duration.ofMillis(300)) }
        waitUntil("both settled after the glow's fade") { alice.settled(this) && bob.settled(this) }
        val own = glowAboveTheMountain(alice, alice)
        assertTrue(own >= 10, "Alice's Mountain glows blue on her table: $own")
        val theirs = glowAboveTheMountain(bob, alice)
        assertTrue(theirs <= 3, "Alice's Mountain has no glow on Bob's table: $theirs")
    }
}
