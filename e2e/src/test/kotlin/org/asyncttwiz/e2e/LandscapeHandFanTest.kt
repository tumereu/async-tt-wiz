package org.asyncttwiz.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performTouchInput
import org.asyncttwiz.android.AppLog
import org.asyncttwiz.android.CardAppearancePreferences
import org.asyncttwiz.android.CardArea
import org.asyncttwiz.appui.AppPlatform
import org.junit.Test
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-25: in landscape your hand is a fan like the desktop's; with more cards than fit easy to tap, a sideways drag
 * turns it like a wheel, cards turned away sinking below the screen, and a card turned into view plays as any hand card.
 */
class LandscapeHandFanTest : E2e() {
    // ZON-138, ZON-139, ZON-140, ZON-141, ZON-079, ZON-102 (owner 2026-11-25): at 3x Alice's fan holds six of her seven cards; her
    // seventh, a Mountain, is turned away at the fan's right end. A sideways drag on the fan turns it (the log says what it turned
    // and what is turned away), the Mountain comes into view, and a tap raises it and a second plays it
    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    @Test fun aCardTurnedIntoViewPlaysFromTheFan() {
        val sizes = CardAppearancePreferences(AppPlatform.current)
        sizes.customizeAreas(true)
        sizes.setSize(3f, CardArea.HAND)
        try {
            startGame(deal = { a, _ -> hand(a).size == 7 && hand(a)[6] == "Mountain" && "Mountain" !in hand(a).take(6) })
            bothOpen()
            alice.tap("Keep")
            bob.tap("Keep")
            waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
            val bar = 412f - 56f
            // Her hand cards in view (a card turned away has no place on the screen)
            fun inView(name: String = ""): List<Rect> = alice.nodes(hasContentDescription("$name, owner ${alice.name}, ", substring = true))
                .fetchSemanticsNodes().map { it.boundsInRoot }.filter { it.width > 0f && it.bottom > bar && it.height > 80f }.sortedBy { it.left }
            fun mountain(): Rect? = inView("Mountain").firstOrNull()
            waitUntil("her 3x fan laid out, its seventh card (the Mountain) turned away") {
                alice.settled(this) && inView().size == 6 && mountain() == null
            }
            // A slow drag leftwards in the bar, across two cards' parts of the fan, turns it on
            val field = alice.battlefieldBounds()
            val from = inView()[4].let { Offset(it.left + 10f, bar + 24f) }
            alice.table().performTouchInput {
                down(Offset(from.x - field.left, from.y - field.top))
                repeat(10) { moveBy(Offset(-5f * density, 0f)) }
                advanceEventTime(300)
                moveBy(Offset(-.5f * density, 0f))
                up()
            }
            waitUntil("the Mountain turned into view") { mountain() != null && alice.settled(this) }
            assertTrue(AppLog.text().lines().any { "Hand fan" in it && "turned away left" in it }, "the turn is logged")
            // A tap on its own part of the fan raises it; a tap on it raised plays it
            val card = mountain()!!
            fun tap(at: Offset) { val f = alice.battlefieldBounds(); alice.table().performTouchInput { click(Offset(at.x - f.left, at.y - f.top)) } }
            tap(Offset(card.left + 10f, bar + 24f))
            waitUntil("the Mountain raised") { mountain()?.let { it.top < card.top - 10f } == true && alice.settled(this) }
            mountain()!!.let { raised -> tap(Offset(raised.left + 12f, raised.top + 8f)) }
            waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() }
            assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
        } finally { sizes.setSize(1f, CardArea.HAND); sizes.customizeAreas(false) }
    }

    // ZON-126, ZON-001, WEB-076 (owner-reported 2026-11-25): in landscape the other player's hand is the small fan of card backs every
    // other layout shows, after their piles in their top bar with its count, not a row of full-size backs across the bar
    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    @Test fun theOtherPlayersHandIsASmallFanInTheTopBar() {
        startGame()
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
        fun backs(): List<Rect> = alice.nodes(hasContentDescription("Covered card, owner ${bob.name}, ", substring = true))
            .fetchSemanticsNodes().map { it.boundsInRoot }
        waitUntil("Bob's seven cards on Alice's table") { backs().size == 7 && alice.settled(this) }
        val cards = backs()
        // (before, a row of full-size backs across the top bar: only the first few were in view, the rest scrolled away)
        assertTrue(cards.all { it.width > 0f }, "all seven in view: $cards")
        val exile = alice.described("${bob.name} exile,", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue(cards.all { it.bottom <= 48f && it.height <= 32f }, "small cards inside the top bar: $cards")
        assertTrue(cards.all { it.left > exile.right }, "after his piles: $cards, exile $exile")
        assertTrue(cards.maxOf { it.right } - cards.minOf { it.left } < 80f, "fanned together, not spread across the bar: $cards")
        assertTrue(AppLog.text().lines().any { "Table laid out landscape" in it && "other player's hand fan" in it }, "the layout is logged")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }
}
