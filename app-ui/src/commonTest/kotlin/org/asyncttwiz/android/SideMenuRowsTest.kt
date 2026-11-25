package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals

/** The side menu's one list of games and pods (owner, 2026-11-25). */
class SideMenuRowsTest {
    private val me = AccountIdentity("me", "alex")
    private val them = AccountIdentity("them", "riley")
    private fun at(hour: Int) = "2026-11-25T${hour.toString().padStart(2, '0')}:00:00Z"
    private fun game(id: String, hour: Int, lifecycle: GameLifecycle = GameLifecycle.ACTIVE, holder: String? = them.accountId,
        inviter: AccountIdentity = me, state: InvitationState = InvitationState.ACCEPTED, held: Boolean = false, pod: String? = null) =
        GameSummary(id, "Game $id", at(0), inviter, if (inviter == me) them else me, "round", "inv-$id", state, lifecycle,
            attentionHolder = holder, updatedAt = at(hour), podId = pod, podName = pod?.let { "Pod $it" }, invitationHeld = held)
    private fun incoming(id: String, hour: Int, held: Boolean = false, pod: String? = null) =
        game(id, hour, GameLifecycle.LOBBY, null, them, InvitationState.PENDING, held, pod)
    private fun pod(id: String, hour: Int, attention: PodAttention = PodAttention.NONE) =
        PodSummary(id, "Pod $id", PodPhase.DRAFTING, them, listOf(me, them), attention, 1, at(hour))
    private fun podInvite(id: String, hour: Int) = PodInvitation(id, "Pod $id", them, at(hour))
    private fun rows(games: List<GameSummary> = emptyList(), invitations: List<GameSummary> = emptyList(), pods: List<PodSummary> = emptyList(),
        podInvitations: List<PodInvitation> = emptyList()) = sideMenuRows(games, invitations, pods, podInvitations, me.accountId).map { it.id }

    // LOB-018, LOB-267, LOB-269: red first (your move, a game invitation you can answer, a pod challenge you can accept), then
    // blue (your pick, a pod invitation), then green (your deck to build), each longest waiting first with games and pods
    // mixed; then the rest most recent first; a challenge held for your deck last
    @Test fun theListGoesByDotColourThenActivityAndHeldInvitationsLast() {
        val games = listOf(game("move-new", 9, holder = me.accountId), game("theirs-old", 1), game("theirs-new", 12),
            game("pod-move", 2, holder = me.accountId, pod = "build"))
        val invitations = listOf(incoming("invite", 5), incoming("held", 23, held = true, pod = "build"), incoming("challenge", 7, pod = "pick"))
        val pods = listOf(pod("pick", 8, PodAttention.YOUR_PICK), pod("build", 3, PodAttention.BUILD), pod("other", 11))
        val podInvitations = listOf(podInvite("invited", 4))
        val all = sideMenuRows(games, invitations, pods, podInvitations, me.accountId)
        assertEquals(SideMenuListSize, all.size)
        assertEquals(listOf("pod-move", "invite", "challenge", "move-new", "invited"), all.map { it.id })
        assertEquals(listOf(WaitingDot.Move, WaitingDot.Move, WaitingDot.Move, WaitingDot.Move, WaitingDot.Pick),
            all.map { sideMenuDot(it, me.accountId) })
        // With fewer of those, the list carries on: blue (the pod invitation before the later pick), green, the rest, held last
        assertEquals(listOf("invited", "pick", "build", "theirs-new", "other"),
            rows(games.filter { it.attentionHolder != me.accountId }, invitations.filter { it.invitationHeld }, pods, podInvitations))
        assertEquals(listOf("theirs-new", "other", "theirs-old", "held"),
            rows(games.filter { it.attentionHolder != me.accountId }, invitations.filter { it.invitationHeld }, pods.filter { it.id == "other" }))
    }

    // LOB-018: always five rows, fewer only when there are fewer; nothing at all lists nothing
    @Test fun theListHoldsFiveRowsOrFewer() {
        val many = (1..8).map { game("g$it", it) }
        assertEquals(listOf("g8", "g7", "g6", "g5", "g4"), rows(many))
        assertEquals(listOf("g2", "g1"), rows(many.take(2)))
        assertEquals(emptyList(), rows())
    }

    // LOB-018: an invitation listed among both the games and the invitations is one row; an answered one is no longer an
    // invitation; an invitation to a pod you are already in is the pod's row
    @Test fun eachGameAndPodIsOneRow() {
        val invite = incoming("invite", 3)
        assertEquals(listOf("invite"), rows(listOf(invite), listOf(invite)))
        val answered = game("answered", 3, GameLifecycle.LOBBY, null)
        assertEquals(listOf("answered"), rows(listOf(answered), listOf(answered)))
        assertEquals(listOf<SideMenuRow>(SideMenuRow.Pod(pod("p", 1))),
            sideMenuRows(emptyList(), emptyList(), listOf(pod("p", 1)), listOf(podInvite("p", 2)), me.accountId))
    }

    // LOB-268 (owner, 2026-11-25): an invitation to a pod lights the hamburger blue, unless it is the pod open or a pod you are in
    @Test fun aPodInvitationLightsTheHamburgerBlue() {
        assertEquals(WaitingDot.Pick, navigationDot(emptyList(), emptyList(), listOf(podInvite("invited", 1)), me.accountId)?.dot)
        assertEquals("pod invited \"Pod invited\" (invitation from riley)",
            navigationDot(emptyList(), listOf(pod("build", 1, PodAttention.BUILD)), listOf(podInvite("invited", 1)), me.accountId)?.why)
        assertEquals(WaitingDot.Move,
            navigationDot(listOf(game("move", 1, holder = me.accountId)), emptyList(), listOf(podInvite("invited", 1)), me.accountId)?.dot)
        assertEquals(null, navigationDot(emptyList(), emptyList(), listOf(podInvite("invited", 1)), me.accountId, openPod = "invited"))
        assertEquals(null, navigationDot(emptyList(), listOf(pod("in", 1)), listOf(podInvite("in", 1)), me.accountId))
    }
}
