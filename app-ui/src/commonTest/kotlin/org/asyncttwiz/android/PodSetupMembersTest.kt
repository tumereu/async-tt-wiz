package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.*

/** Pod setup's members, invitations and bots (owner, 2026-11-22): Add bot and the creator's ✕s. */
class PodSetupMembersTest {
    // DRF-296: Add bot greys at the mode's most (its player limit less two) with the reason on hold
    @Test fun addBotSaysWhyItIsGreyed() {
        assertNull(podAddBotBlocked(DraftSettings.booster().copy(bots = 5)))
        assertEquals("Booster takes at most 6 bots", podAddBotBlocked(DraftSettings.booster().copy(bots = 6)))
        assertEquals("Winston takes at most 2 bots", podAddBotBlocked(DraftSettings.winston().copy(bots = 2)))
    }

    // DRF-307, DRF-308: a refused cancel of an invitation or removal of a member says why in plain words
    @Test fun refusedCancelsAndRemovalsSayWhy() {
        assertEquals("The draft has already started.", podLeavingError(PodFailure("not_setup")))
        assertEquals("That invitation is no longer pending.", podLeavingError(PodFailure("invitation_not_found")))
        assertEquals("That player is no longer a member of this pod.", podLeavingError(PodFailure("member_not_found")))
        assertEquals("The pod's creator can't be removed.", podLeavingError(PodFailure("creator_cannot_be_removed")))
    }

    // DRF-308, DRF-137: the removal's confirmation names the member and the pod; the removed player's notice names the pod
    @Test fun theRemovalNamesThePlayerAndThePod() {
        val alice = AccountIdentity("a", "Alice"); val bob = AccountIdentity("b", "Bob")
        val detail = PodDetail("pod", "Friday cube", PodPhase.SETUP, alice, DraftSettings(), PodSource("Cube", 540), DraftSupply(0, 540),
            listOf(PodMember(alice), PodMember(bob)), emptyList(), updatedAt = "")
        assertEquals("Remove Bob from Friday cube?", podRemovalText(bob, detail))
        assertEquals("Removed from Friday cube", ActivityTexts.podActivity(ActivityKind.POD_MEMBER_REMOVED, "Friday cube"))
    }
}
