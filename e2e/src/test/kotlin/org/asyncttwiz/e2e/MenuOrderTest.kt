package org.asyncttwiz.e2e

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import org.junit.Test
import kotlin.test.assertEquals

/** Owner, 2026-11-09: the menus' order. */
class MenuOrderTest : E2e() {
    // LOB-015, LOB-018: the main menu reads Games, Pods, Manage decks, Contacts, Stats, Settings; in the side menu Games and
    // Pods come above the menu section, in that same order
    @Test fun mainAndSideMenusKeepTheirOrder() {
        launchPlayers()
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-PODS")) }
        val main = listOf("GAMES", "PODS", "DECKS", "CONTACTS", "STATS", "SETTINGS")
            .map { alice.tagged("menu-$it").getUnclippedBoundsInRoot().top.value }
        assertEquals(main.sorted(), main)

        alice.described("Open navigation").performClick()
        waitUntil("Alice's side menu") { alice.shows(hasTestTag("navigation-Games")) }
        val side = listOf("Games", "Pods", "Menu", "Manage decks", "Contacts", "Stats", "Settings")
            .map { alice.tagged("navigation-$it").getUnclippedBoundsInRoot().top.value }
        assertEquals(side.sorted(), side)
    }
}
