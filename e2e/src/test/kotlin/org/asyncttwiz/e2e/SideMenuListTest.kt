package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.asyncttwiz.android.ShellDestination
import org.asyncttwiz.android.WAITING_DOT_SLOT_TAG
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        // LOB-269 (owner gallery review 2026-11-25): every row's dot slot at one x before its name, the red and blue dots inside theirs
        val slots = expected.map { bob.node(dotIn(WAITING_DOT_SLOT_TAG, it)).fetchSemanticsNode().boundsInRoot }
        assertTrue(slots.all { kotlin.math.abs(it.left - slots[0].left) < 0.5f }, "the dot slots line up: $slots")
        val name = bob.node(hasText("E2E invite $tag") and hasAnyAncestor(hasTestTag(expected[0]))).fetchSemanticsNode().boundsInRoot
        assertTrue(slots[0].right <= name.left, "the dot's slot before the name: ${slots[0]} vs $name")
        for ((row, dot) in listOf(expected[0] to "waiting-dot-move", expected[1] to "waiting-dot-pick")) {
            val mark = bob.node(dotIn(dot, row)).fetchSemanticsNode().boundsInRoot
            val slot = slots[expected.indexOf(row)]
            assertTrue(mark.left >= slot.left - 0.5f && mark.right <= slot.right + 0.5f, "$row's dot in its slot: $mark vs $slot")
        }
        // LOB-018 (owner 2026-11-26): each row's game or pod badge: the pod invitation's a pod's, the games' a game's
        for (row in expected) {
            val pod = row.startsWith("navigation-pod-")
            assertTrue(bob.shows(dotIn(if (pod) "navigation-badge-pod" else "navigation-badge-game", row)) &&
                !bob.shows(dotIn(if (pod) "navigation-badge-game" else "navigation-badge-pod", row)), "$row's badge: ${bob.screen()}")
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
