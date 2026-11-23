package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.asyncttwiz.android.PodTab
import org.asyncttwiz.android.ShellDestination
import org.asyncttwiz.protocol.GameLifecycle
import org.asyncttwiz.protocol.InvitationState
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-14 (DRF-181): the pod Games tab's Challenge button, while the member has an open challenge to you, accepts
 * that challenge instead of making a new game; "New challenge" in its choices still challenges them again.
 */
class PodChallengeAcceptTest : E2e() {
    // DRF-181, DRF-121, DRF-123: Alice challenges Bob; Bob's Challenge beside her accepts it and opens its lobby, no second
    // game made; back in the pod, New challenge sends her a new challenge
    @Test fun challengeAcceptsTheirOpenChallengeAndNewChallengeQueuesAnother() {
        launchPlayers()
        val name = "E2E challenge back ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftedPod(alice.account, bob.account, name)
        ServerSetup.submitPodDeck(alice.account, pod)
        ServerSetup.submitPodDeck(bob.account, pod)
        val game = ServerSetup.challenge(alice.account, bob.account, pod, "E2E Alice's challenge")

        bob.auth.navigate(ShellDestination.PODS)
        bob.pods.open(pod, PodTab.GAMES)
        bob.lobby.refresh()
        val accept = hasTestTag("pod-challenge-accept-${alice.id}")
        waitUntil("Bob's Challenge beside Alice accepting her challenge") {
            bob.pods.state.tab == PodTab.GAMES && bob.shows(accept and isEnabled()) && bob.shows(hasTestTag("pod-game-${game.id}"))
        }
        bob.node(accept).performScrollTo().performClick()
        waitUntil("Alice's challenge accepted and its lobby open on Bob's phone") {
            bob.lobby.state.selected == game.id &&
                ServerSetup.head(bob.account, game.id).summary.invitationState == InvitationState.ACCEPTED
        }
        val podGames = { E2eStack.query("SELECT count(*) FROM game WHERE pod_id = '$pod'").single().toInt() }
        assertEquals(1, podGames(), "accepting made no new game")
        assertEquals(GameLifecycle.LOBBY, ServerSetup.head(bob.account, game.id).summary.lifecycle)

        // Back to the pod: no open challenge from Alice any more, so the plain Challenge returns; Alice challenges again and
        // Bob chooses New challenge instead of accepting
        bob.lobby.back()
        waitUntil("Bob's lobby closed") { bob.lobby.state.selected == null }
        bob.auth.navigate(ShellDestination.PODS)
        bob.pods.open(pod, PodTab.GAMES)
        waitUntil("the plain Challenge beside Alice") {
            bob.shows(hasContentDescription("Challenge") and isEnabled()) && !bob.shows(hasTestTag("pod-challenge-choices-${alice.id}"))
        }
        val second = ServerSetup.challenge(alice.account, bob.account, pod, "E2E Alice's second challenge")
        bob.lobby.refresh()
        val choices = hasTestTag("pod-challenge-choices-${alice.id}")
        waitUntil("Bob's challenge choices beside Alice") { bob.shows(choices) && bob.shows(accept and isEnabled()) }
        bob.node(choices).performScrollTo().performClick()
        waitUntil("New challenge offered") { bob.shows(hasText("New challenge")) }
        bob.text("New challenge").performClick()
        waitUntil("Bob's new challenge to Alice made and opened") {
            val selected = bob.lobby.state.selected
            selected != null && selected != game.id && bob.lobby.state.detail?.let { it.inviter.accountId == bob.id } == true
        }
        assertEquals(3, podGames(), "New challenge made a third game, the second challenge still open")
        assertEquals(InvitationState.PENDING, ServerSetup.head(bob.account, second.id).summary.invitationState,
            "Alice's second challenge still waits")
    }

    // DRF-181, DRF-122 (owner 2026-11-22: auto-accept didn't work): Alice, her deck submitted, challenges Bob while he builds;
    // Bob submits on his phone and at once presses the Challenge beside Alice: it accepts her challenge and opens its lobby,
    // no second game made (his phone held her challenge as waiting for his deck until its next lobby read)
    @Test fun challengeRightAfterSubmittingAcceptsTheirChallenge() {
        launchPlayers()
        val name = "E2E challenge on submit ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftedPod(alice.account, bob.account, name)
        ServerSetup.submitPodDeck(alice.account, pod)
        val game = ServerSetup.challenge(alice.account, bob.account, pod, "E2E Alice's early challenge")
        assertTrue(game.invitationHeld, "her challenge waits for Bob's deck")

        bob.auth.navigate(ShellDestination.PODS)
        bob.lobby.refresh()
        waitUntil("Alice's challenge on Bob's phone, waiting for his deck") {
            (bob.lobby.state.games + bob.lobby.state.invitations).any { it.id == game.id && it.invitationHeld }
        }
        bob.pods.open(pod)
        val count = { bob.pods.state.detail?.takeIf { it.id == pod }?.build?.let { it.copies.size + it.basics.total } }
        waitUntil("Bob's pod open on Build with Submit enabled") {
            val shown = count() ?: return@waitUntil false
            bob.pods.state.tab == PodTab.BUILD && bob.shows(hasClickAction() and hasAnyDescendant(hasText("Submit deck ($shown)")) and isEnabled())
        }
        bob.node(hasClickAction() and hasAnyDescendant(hasText("Submit deck (${count()})"))).performClick()
        val confirm = hasClickAction() and hasAnyDescendant(hasText("Submit")) and hasAnyAncestor(isDialog())
        waitUntil("the Submit deck? dialog") { bob.shows(confirm) }
        // The full run of 2026-11-23 (release 0.3.2): Bob pressed the Challenge as his submit landed and nothing happened, no
        // lobby and nothing said. The submit's answer is held here, so the press falls in that window every time: it waits
        // for the answer and accepts then
        val submit = "/v1/pods/$pod/submit"
        bob.link.holdAnswers(submit)
        bob.node(confirm).performClick()
        waitUntil("Bob's deck submitted on the server") { ServerSetup.pod(bob.account, pod).submitted }

        // At once, the Challenge beside Alice, whichever it is now, while his phone still waits for the submit's answer. The
        // button itself, enabled: its icon (which holds the description) counts as enabled also in a greyed button, and the
        // full run's press was likely one of a greyed plain Challenge
        val accept = hasTestTag("pod-challenge-accept-${alice.id}") and isEnabled()
        val plain = hasClickAction() and isEnabled() and hasAnyDescendant(hasContentDescription("Challenge"))
        waitUntil("a Challenge beside Alice to press") { bob.pods.state.tab == PodTab.GAMES && (bob.shows(accept) || bob.shows(plain)) }
        assertTrue(bob.pods.state.pending > 0, "the submit's answer still on its way")
        bob.node(if (bob.shows(accept)) accept else plain).performScrollTo().performClick()
        run(skip = java.time.Duration.ofMillis(500))
        assertTrue(bob.pods.state.pending > 0, "the press made before the submit's answer came")
        bob.link.releaseAnswers(submit)
        waitUntil("a game's lobby open on Bob's phone") { bob.lobby.state.selected != null }
        assertEquals(game.id, bob.lobby.state.selected, "Alice's challenge opened, not a new one")
        waitUntil("Alice's challenge accepted") {
            ServerSetup.head(bob.account, game.id).summary.invitationState == InvitationState.ACCEPTED
        }
        assertEquals(1, E2eStack.query("SELECT count(*) FROM game WHERE pod_id = '$pod'").single().toInt(), "no second game")
    }
}
