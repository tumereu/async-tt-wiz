package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import org.asyncttwiz.android.ShellDestination
import org.junit.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * The "Waiting for you" dots (owner, 2026-11-22), the overall flow: a pod game where it is your move lights your hamburger
 * red while you look at its pod, and its side-menu row red, the open pod's row none; a draft starting with your pick lights
 * its row blue, and at the game's table (the game itself left out) the hamburger blue; the other screens' hamburgers (Contacts,
 * Settings) red again.
 */
class WaitingDotsTest : E2e() {
    private fun dotIn(dot: String, ancestor: String) = hasTestTag(dot) and hasAnyAncestor(hasTestTag(ancestor))

    // LOB-267, LOB-268, LOB-269, LOB-270, DRF-306
    @Test fun yourMoveLightsThePodsHamburgerAndTheGamesRowThenAPickLightsTheTables() {
        launchPlayers()
        val name = "E2E dots ${UUID.randomUUID().toString().take(6)}"
        val game = ServerSetup.readyBoth(alice.account, bob.account, ServerSetup.podGame(alice.account, bob.account, name))
        val pod = assertNotNull(game.podId)
        // Whoever the game waits for first is "you"
        waitUntil("the game waiting for a player") { ServerSetup.head(alice.account, game.id).summary.attentionHolder != null }
        val holder = ServerSetup.head(alice.account, game.id).summary.attentionHolder
        val you = if (holder == bob.id) bob else alice

        // In the game's pod: the hamburger red for your move in its game
        you.auth.navigate(ShellDestination.PODS)
        you.pods.open(pod)
        waitUntil("the red dot on ${you.name}'s pod hamburger") { you.shows(dotIn("navigation-dot-move", "pod-play-header")) }
        you.described("Open navigation").performClick()
        waitUntil("the red dot on the game's side-menu row") { you.shows(dotIn("waiting-dot-move", "navigation-game-${game.id}")) }
        for (dot in listOf("move", "pick", "build"))
            assertFalse(you.shows(dotIn("waiting-dot-$dot", "navigation-pod-$pod")), "no dot for the pod open: ${you.screen()}")

        // A draft starts with both of them: its row blue, once the pod list is read again
        val drafting = ServerSetup.draftingPod(alice.account, bob.account, "E2E dots draft ${UUID.randomUUID().toString().take(6)}")
        waitUntil("the blue dot on the drafting pod's row") { you.shows(dotIn("waiting-dot-pick", "navigation-pod-$drafting")) }
        you.described("Close navigation").performClick()

        // At the game's table its own move gives none: the hamburger blue for the pick
        you.auth.navigate(ShellDestination.MENU)
        you.openGame(game, this)
        waitUntil("the blue dot on ${you.name}'s table hamburger") { you.shows(hasTestTag("navigation-dot-pick")) }
        assertFalse(you.shows(hasTestTag("navigation-dot-move")), "the open game's move lights nothing: ${you.screen()}")

        // LOB-268: every other screen's hamburger too, with nothing open there: red for the game's move on Contacts and Settings
        for (screen in listOf(ShellDestination.CONTACTS, ShellDestination.SETTINGS)) {
            you.auth.navigate(screen)
            waitUntil("the red dot on ${you.name}'s $screen hamburger") { you.shows(hasTestTag("navigation-dot-move")) }
        }
    }
}
