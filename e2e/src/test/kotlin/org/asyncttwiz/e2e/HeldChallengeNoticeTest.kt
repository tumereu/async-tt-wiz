package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import org.asyncttwiz.android.PodTab
import org.asyncttwiz.android.ShellDestination
import org.asyncttwiz.android.invitationText
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-08 (invitation notifications): a pod challenge sent while its recipient is still building notifies them
 * at once, naming the pod and the challenger and asking for their deck, and the notification opens the pod's Games tab;
 * submitting the deck then makes the challenge acceptable with no second notification.
 */
class HeldChallengeNoticeTest : E2e() {
    // DRF-137, DRF-122: Alice has submitted her deck and challenges Bob, who is still building: his notice comes at once
    // ("<pod>: Alice challenged you · submit your deck to accept"), opens the pod's Games tab with the challenge (Accept
    // greyed); his submitted deck makes it acceptable, and no other notice comes
    @Test fun aHeldChallengeNotifiesAtOnceAndSubmittingAddsNone() {
        launchPlayers()
        val name = "E2E pod ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftedPod(alice.account, bob.account, name)
        ServerSetup.submitPodDeck(alice.account, pod)
        val game = ServerSetup.challenge(alice.account, bob.account, pod, "E2E challenge")
        assertTrue(game.invitationHeld)

        assertEquals(1, ServerSetup.invitationNotices(bob.account, game.id), "Bob is notified at once")
        val notice = assertNotNull(ServerSetup.invitationNotice(bob.account, game.id))
        assertEquals("$name: ${alice.name} challenged you · submit your deck to accept", invitationText(notice))

        // The notification opens the pod's Games tab (AuthViewModel.openActivity's route for a challenge waiting for his deck)
        assertEquals(pod, notice.podId)
        bob.auth.navigate(ShellDestination.PODS)
        bob.pods.open(pod, PodTab.GAMES)
        val row = hasTestTag("pod-game-${game.id}")
        waitUntil("the challenge in Bob's pod Games tab") {
            bob.pods.state.tab == PodTab.GAMES && bob.shows(hasText("Challenged you · submit your deck to accept")) &&
                bob.shows(hasAnyChild(hasContentDescription("Accept invitation")) and isNotEnabled())
        }
        assertTrue(bob.shows(row))

        ServerSetup.submitPodDeck(bob.account, pod)
        bob.pods.refresh(); bob.lobby.refresh()
        waitUntil("the challenge acceptable on Bob's phone") {
            bob.shows(hasText("Invitation pending")) && bob.shows(hasAnyChild(hasContentDescription("Accept invitation")) and isEnabled())
        }
        assertEquals(1, ServerSetup.invitationNotices(bob.account, game.id), "submitting adds no notice")
        val after = assertNotNull(ServerSetup.invitationNotice(bob.account, game.id))
        assertFalse(after.held)
        assertEquals("$name: ${alice.name} challenged you", invitationText(after))
    }
}
