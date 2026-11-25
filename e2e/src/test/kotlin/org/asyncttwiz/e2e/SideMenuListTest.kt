package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import org.asyncttwiz.android.ShellDestination
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals

/**
 * The side menu's one list (owner, 2026-11-25), the overall flow: Bob has sent Alice four invitations, then Alice invites him
 * to a game and to her pod. His hamburger is blue for the pod invitation alone; his side menu lists five rows,
 * Alice's invitation (red) and the pod invitation (blue) first, then his three newest sent invitations, the oldest left out;
 * tapping the pod invitation opens the Pods screen to answer it.
 */
class SideMenuListTest : E2e() {
    private fun dotIn(dot: String, ancestor: String) = hasTestTag(dot) and hasAnyAncestor(hasTestTag(ancestor))
    private fun rows(player: E2ePlayer): List<String> = player.nodes(SemanticsMatcher("a side-menu row") { node ->
        node.config.getOrElseNullable(SemanticsProperties.TestTag) { null }
            ?.let { it.startsWith("navigation-game-") || it.startsWith("navigation-pod-") } == true
    }).fetchSemanticsNodes().sortedBy { it.boundsInRoot.top }.map { it.config[SemanticsProperties.TestTag] }

    // LOB-018, LOB-021, LOB-267, LOB-268, LOB-269
    @Test fun theSideMenuListsTheFiveMostImportantAndAPodInvitationOpensPods() {
        launchPlayers()
        val tag = UUID.randomUUID().toString().take(6)
        val sent = (1..4).map { ServerSetup.invite(bob.account, alice.account, "E2E sent $it $tag") }
        // LOB-268: a pod invitation alone lights Bob's hamburger blue
        val pod = ServerSetup.setupPod(alice.account, "E2E side pod $tag")
        ServerSetup.invitePod(alice.account, bob.account, pod)
        bob.auth.navigate(ShellDestination.CONTACTS)
        bob.pods.refresh(); bob.lobby.refresh()
        waitUntil("the blue dot on Bob's Contacts hamburger for the pod invitation") { bob.shows(hasTestTag("navigation-dot-pick")) }
        val invitation = ServerSetup.invite(alice.account, bob.account, "E2E invite $tag")
        bob.lobby.refresh()
        waitUntil("the red dot on Bob's hamburger for Alice's invitation") { bob.shows(hasTestTag("navigation-dot-move")) }

        bob.described("Open navigation").performClick()
        val expected = listOf("navigation-game-${invitation.id}", "navigation-pod-invitation-$pod") +
            sent.reversed().take(3).map { "navigation-game-${it.id}" }
        waitUntil("Bob's side menu listing five rows, most important first") { rows(bob) == expected }
        assertEquals(expected, rows(bob))
        waitUntil("the red and blue dots on the first two rows") {
            bob.shows(dotIn("waiting-dot-move", expected[0])) && bob.shows(dotIn("waiting-dot-pick", expected[1]))
        }
        // The buttons below the list, Games and Pods among them
        val buttons = listOf("Menu", "Games", "Pods", "Manage decks", "Contacts", "Stats", "Settings")
            .map { bob.tagged("navigation-$it").fetchSemanticsNode().boundsInRoot.top }
        assertEquals(buttons.sorted(), buttons)

        bob.tagged("navigation-pod-invitation-$pod").performClick()
        waitUntil("Bob's Pods screen with the invitation to answer") {
            bob.auth.state.destination == ShellDestination.PODS && !bob.auth.state.drawerOpen && bob.showsText("E2E side pod $tag")
        }
    }
}
