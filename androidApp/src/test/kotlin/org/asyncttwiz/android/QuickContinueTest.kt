package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.*

class QuickContinueTest {
    private val self = AccountIdentity("a", "Alice")
    private val other = AccountIdentity("b", "Bob")
    private fun game(id: String, hour: Int, holder: String = other.accountId,
        lifecycle: GameLifecycle = GameLifecycle.ACTIVE) = GameSummary(id, "Table $id", "2026-10-20T00:00:00Z",
        self, other, "round", "invite", InvitationState.ACCEPTED, lifecycle,
        attentionHolder = holder, updatedAt = "2026-10-24T${hour.toString().padStart(2, '0')}:00:00Z")

    @Test fun ownActivityComesFirstOldestFirstThenOtherActivityNewestFirstCappedAtThree() {
        val games = listOf(game("other-old", 1), game("own-new", 10, "a"),
            game("other-new", 20), game("own-old", 5, "a"))
        assertEquals(listOf("own-old", "own-new", "other-new"), quickContinueGames(games, "a").map { it.id })
        assertEquals(listOf("other-old", "other-new", "own-new"), quickContinueGames(games, "b").map { it.id })
        assertEquals("Your move", quickContinueMove(games[1], "a"))
        assertEquals("Bob's move", quickContinueMove(games[0], "a"))
    }

    @Test fun endedGamesAndDeclinedInvitationsNeverAppearAndEmptyListHasNoEntries() {
        val excluded = listOf(GameLifecycle.CANCELLED, GameLifecycle.DELETED)
            .map { game(it.name, 1, "a", it) }
        val declined = game("declined", 1, lifecycle = GameLifecycle.LOBBY).copy(invitationState = InvitationState.DECLINED)
        assertTrue(quickContinueGames(excluded + declined, "a").isEmpty())
        assertTrue(quickContinueGames(emptyList(), "a").isEmpty())
        assertEquals(listOf("active"), quickContinueGames(excluded + game("active", 2), "a").map { it.id })
    }

    @Test fun timestampsCompareAsInstantsWithStableIdentityTieBreak() {
        val early = game("early", 1, "a").copy(updatedAt = "2026-10-24T04:00:00+04:00")
        val late = game("late", 1, "a")
        assertEquals(listOf("early", "late"), quickContinueGames(listOf(late, early), "a").map { it.id })
        val tied = listOf(game("z", 1), game("b", 1), game("a", 1), game("c", 1))
        assertEquals(listOf("a", "b", "c"), quickContinueGames(tied, "a").map { it.id })
    }

    @Test fun sharedRowHighlightsOnlyTheViewerWhoseMoveFlag() {
        val own = game("own", 1, "a")
        assertEquals(GameRowMove("Your move", true), gameRowMove(own, "a"))
        assertEquals(GameRowMove("Alice's move", false), gameRowMove(own, "b"))
        assertEquals(GameRowMove("Bob's move", false), gameRowMove(game("other", 1), "a"))
        assertEquals(GameRowMove("Move not available", false), gameRowMove(own.copy(attentionHolder = null), "a"))
    }

    @Test fun lastActivityUsesUpdatedAtAndLocalDayAndYear() {
        val clock = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneId.of("Europe/Helsinki"))
        fun format(timestamp: String) = gameLastActivity(game("date", 1).copy(updatedAt = timestamp), clock)
        assertEquals("14:05", format("2026-10-24T11:05:00Z"))
        assertEquals("00:05", format("2026-10-23T21:05:00Z"))
        assertEquals("23 Oct", format("2026-10-23T20:59:00Z"))
        assertEquals("24 Sep", format("2026-09-24T11:05:00Z"))
        assertEquals("24 Oct 2025", format("2025-10-24T11:05:00Z"))
        assertEquals("", format("invalid"))
    }

    @Test fun localYearBoundaryDeterminesWhetherYearIsShown() {
        val clock = Clock.fixed(Instant.parse("2026-12-31T23:00:00Z"), ZoneId.of("Europe/Helsinki"))
        fun format(timestamp: String) = gameLastActivity(game("date", 1).copy(updatedAt = timestamp), clock)
        assertEquals("00:05", format("2026-12-31T22:05:00Z"))
        assertEquals("31 Dec 2026", format("2026-12-31T21:05:00Z"))
    }

    @Test fun gameInvitationsAlwaysComeAfterActiveGamesAndAcceptedSetupAndOnlyFillFreeSlots() {
        val invitation = game("invitation", 0, "a", GameLifecycle.LOBBY).copy(invitationState = InvitationState.PENDING)
        val setup = game("setup", 2, lifecycle = GameLifecycle.LOBBY)
        val active = game("active", 1)
        assertEquals(listOf("setup", "active", "invitation"),
            quickContinueGames(listOf(invitation, active, setup), "a").map { it.id })
        assertEquals(listOf("own", "setup", "active"),
            quickContinueGames(listOf(invitation, active, setup, game("own", 5, "a")), "a").map { it.id })
        val newer = invitation.copy(id = "newer-invitation", updatedAt = game("date", 20).updatedAt)
        assertEquals(listOf("newer-invitation", "invitation"), quickContinueGames(listOf(invitation, newer), "a").map { it.id })
        assertEquals(listOf("active", "invitation"), quickContinueGames(listOf(invitation, active, invitation), "a").map { it.id })
    }

    private fun pod(id: String, hour: Int, attention: PodAttention = PodAttention.NONE,
        phase: PodPhase = PodPhase.DRAFTING) = PodSummary(id, id, phase, self, listOf(self, other), attention, 2,
        "2026-10-24T${hour.toString().padStart(2, '0')}:00:00Z")
    private fun invitation(id: String, hour: Int) = PodInvitation(id, id, other, pod(id, hour).updatedAt)

    @Test fun podPicksAndBuildActionsComeFirstOldestFirstThenOtherPodsNewestFirstCappedAtThree() {
        val pods = listOf(pod("other-old", 1), pod("pick-new", 10, PodAttention.YOUR_PICK),
            pod("other-new", 20, phase = PodPhase.SETUP), pod("build-old", 5, PodAttention.BUILD, PodPhase.DRAFTED))
        assertEquals(listOf("build-old", "pick-new", "other-new"), quickContinuePods(pods, emptyList()).map { it.id })
        assertEquals(listOf("other-new", "other-old"),
            quickContinuePods(pods.filter { it.attention == PodAttention.NONE }, emptyList()).map { it.id })
    }

    @Test fun podInvitationsRankLastOnlyFillFreeSlotsAndEmptyPanelHasNoEntries() {
        val invitations = listOf(invitation("invite-old", 0), invitation("invite-new", 23))
        val pods = listOf(pod("active", 10), pod("pick", 20, PodAttention.YOUR_PICK))
        assertEquals(listOf("pick", "active", "invite-new"), quickContinuePods(pods, invitations).map { it.id })
        assertEquals(listOf("pick", "active", "other"),
            quickContinuePods(pods + pod("other", 1), invitations).map { it.id })
        assertTrue(quickContinuePods(emptyList(), emptyList()).isEmpty())
        assertEquals(listOf("active"), quickContinuePods(listOf(pods[0]), listOf(invitation("active", 23))).map { it.id })
        assertNotNull(quickContinuePods(emptyList(), invitations).first().invitation)
    }

    @Test fun podTimestampsUseInstantsAndStableIdentityTies() {
        val early = pod("early", 1, PodAttention.YOUR_PICK).copy(updatedAt = "2026-10-24T04:00:00+04:00")
        val late = pod("late", 1, PodAttention.YOUR_PICK)
        assertEquals(listOf("early", "late"), quickContinuePods(listOf(late, early), emptyList()).map { it.id })
        val tied = listOf("z", "b", "a", "c").map { pod(it, 1) }
        assertEquals(listOf("a", "b", "c"), quickContinuePods(tied, emptyList()).map { it.id })
    }
}
