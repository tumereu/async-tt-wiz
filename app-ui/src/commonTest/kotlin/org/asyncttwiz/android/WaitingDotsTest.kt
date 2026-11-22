package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The "Waiting for you" dots (owner, 2026-11-22). */
class WaitingDotsTest {
    private val me = AccountIdentity("me", "alex")
    private val them = AccountIdentity("them", "riley")
    private fun game(id: String, lifecycle: GameLifecycle = GameLifecycle.ACTIVE, holder: String? = null,
        inviter: AccountIdentity = them, state: InvitationState = InvitationState.ACCEPTED, held: Boolean = false, pod: String? = null) =
        GameSummary(id, "Game $id", "2026-11-22T10:00:00Z", inviter, if (inviter == me) them else me, "round", "inv-$id",
            state, lifecycle, attentionHolder = holder, podId = pod, podName = pod?.let { "Pod $it" }, invitationHeld = held)
    private fun invitation(id: String, inviter: AccountIdentity = them, held: Boolean = false, pod: String? = null) =
        game(id, GameLifecycle.LOBBY, inviter = inviter, state = InvitationState.PENDING, held = held, pod = pod)
    private fun pod(id: String, attention: PodAttention) = PodSummary(id, "Pod $id", PodPhase.DRAFTING, them, listOf(me, them),
        attention, 1, "2026-11-22T10:00:00Z")

    // LOB-267: red for your move, an invitation to you or a pod challenge you can accept now; nothing for a challenge held
    // until you submit your deck, an invitation you sent, or a game waiting for the other player
    @Test fun gamesLightRedWhenTheyWaitForYou() {
        assertEquals(WaitingDot.Move, gameWaitingDot(game("move", holder = me.accountId), me.accountId))
        assertNull(gameWaitingDot(game("theirs", holder = them.accountId), me.accountId))
        assertEquals(WaitingDot.Move, gameWaitingDot(invitation("invite"), me.accountId))
        assertEquals(WaitingDot.Move, gameWaitingDot(invitation("challenge", pod = "p"), me.accountId))
        assertNull(gameWaitingDot(invitation("held", held = true, pod = "p"), me.accountId))
        assertNull(gameWaitingDot(invitation("sent", inviter = me), me.accountId))
        assertNull(gameWaitingDot(game("setup", GameLifecycle.LOBBY), me.accountId))
    }

    // LOB-267: blue for your pick, green for your deck to build (DRF-013)
    @Test fun podsLightBlueForAPickAndGreenForABuild() {
        assertEquals(WaitingDot.Pick, podWaitingDot(pod("pick", PodAttention.YOUR_PICK)))
        assertEquals(WaitingDot.Build, podWaitingDot(pod("build", PodAttention.BUILD)))
        assertNull(podWaitingDot(pod("none", PodAttention.NONE)))
    }

    // LOB-267, LOB-268: the hamburger shows the highest colour (move over pick over build) of everything but the game or pod open
    @Test fun theHamburgerShowsTheHighestColourOfEverythingButWhatIsOpen() {
        val move = game("move", holder = me.accountId)
        val pick = pod("pick", PodAttention.YOUR_PICK)
        val build = pod("build", PodAttention.BUILD)
        assertEquals(WaitingDot.Move, navigationDot(listOf(move), listOf(build, pick), me.accountId)?.dot)
        assertEquals(WaitingDot.Pick, navigationDot(listOf(move), listOf(build, pick), me.accountId, openGame = move.id)?.dot)
        assertEquals(WaitingDot.Build, navigationDot(listOf(move), listOf(build, pick), me.accountId, openGame = move.id, openPod = pick.id)?.dot)
        assertNull(navigationDot(listOf(move), listOf(build), me.accountId, openGame = move.id, openPod = build.id))
        assertNull(navigationDot(emptyList(), emptyList(), me.accountId))
        // A pod's challenge is a game: it lights the hamburger in its pod too
        assertEquals(WaitingDot.Move, navigationDot(listOf(invitation("challenge", pod = pick.id)), listOf(pick), me.accountId,
            openPod = pick.id)?.dot)
        // The log names what lights it
        assertEquals("game move \"Game move\" (your move)", navigationDot(listOf(move), listOf(pick), me.accountId)?.why)
        assertEquals("pod pick \"Pod pick\" (Your pick · 1 pack)", navigationDot(emptyList(), listOf(pick), me.accountId)?.why)
    }
}
