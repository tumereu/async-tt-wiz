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

    // LOB-016
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

    // LOB-024
    @Test fun sharedRowHighlightsOnlyTheViewerWhoseMoveFlag() {
        val own = game("own", 1, "a")
        assertEquals(GameRowMove("Your move", true), gameRowMove(own, "a"))
        assertEquals(GameRowMove("Alice's move", false), gameRowMove(own, "b"))
        assertEquals(GameRowMove("Bob's move", false), gameRowMove(game("other", 1), "a"))
        assertEquals(GameRowMove("Move not available", false), gameRowMove(own.copy(attentionHolder = null), "a"))
    }

    // LOB-024
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

    private fun incoming(id: String, hour: Int) = game(id, hour, lifecycle = GameLifecycle.LOBBY)
        .copy(inviter = other, recipient = self, invitationState = InvitationState.PENDING)
    private val ordered = listOf(game("their-old", 1, "a"), game("their-new", 9, "a"),
        incoming("in-held", 7).copy(invitationHeld = true), incoming("in-old", 3),
        game("own-new", 8), game("outgoing", 5, lifecycle = GameLifecycle.LOBBY).copy(invitationState = InvitationState.PENDING),
        game("own-old", 2), game("setup", 10, lifecycle = GameLifecycle.LOBBY),
        game("declined", 0, lifecycle = GameLifecycle.LOBBY).copy(invitationState = InvitationState.DECLINED))

    // LOB-022
    @Test fun sharedOrderIsOpponentActedLongestWaitingThenInvitationsThenOwnMovesAndSentInvitationsThenTheRest() {
        assertEquals(ordered.map { it.id }, orderedGames(ordered.reversed() + ordered.shuffled(java.util.Random(4)), "a").map { it.id })
        assertEquals(listOf(GameOrderGroup.THEIR_EVENT, GameOrderGroup.THEIR_EVENT, GameOrderGroup.INVITATION, GameOrderGroup.INVITATION,
            GameOrderGroup.OWN_EVENT, GameOrderGroup.OWN_EVENT, GameOrderGroup.OWN_EVENT, GameOrderGroup.OTHER, GameOrderGroup.OTHER),
            ordered.map { gameOrderGroup(it, "a") })
        // The other seat sees the same games mirrored: Bob's move flag, Alice's sent invitation to answer.
        assertEquals(listOf("own-old", "own-new", "outgoing", "their-new", "in-held", "in-old", "their-old", "setup", "declined"),
            orderedGames(ordered, "b").map { it.id })
        assertEquals(GameOrderGroup.OWN_EVENT, gameOrderGroup(game("no-flag", 1).copy(attentionHolder = null), "a"))
        assertEquals(GameOrderGroup.OTHER, gameOrderGroup(game("cancelled", 1, "a", GameLifecycle.CANCELLED), "a"))
        assertEquals(GameOrderGroup.OTHER, gameOrderGroup(incoming("ended", 1).copy(lifecycle = GameLifecycle.DELETED), "a"))
    }

    // LOB-016
    @Test fun menuShowsTheFirstThreeOfTheSharedOrderAndInvitationsOnlyAfterOpponentMoves() {
        assertEquals(listOf("their-old", "their-new", "in-held"), quickContinueGames(ordered.shuffled(java.util.Random(1)), "a").map { it.id })
        val noTheirMoves = ordered.filter { gameOrderGroup(it, "a") != GameOrderGroup.THEIR_EVENT }
        assertEquals(listOf("in-held", "in-old", "own-new"), quickContinueGames(noTheirMoves, "a").map { it.id })
        assertEquals(listOf("setup"), quickContinueGames(ordered.filter { it.id in setOf("setup", "declined") }, "a").map { it.id })
        assertEquals(listOf("in-old"), quickContinueGames(listOf(incoming("in-old", 3), incoming("in-old", 3)), "a").map { it.id })
    }

    // LOB-022
    @Test fun drawerUsesTheSharedOrderWithInvitationsFromEitherListOnce() {
        val (invitations, games) = ordered.partition { incomingInvitation(it, "a") }
        assertEquals(ordered.map { it.id }, navigationGameRows(games.reversed(), invitations.reversed(), "a").map { it.id })
        // An answered invitation still listed in the old invitation list is never shown as one.
        val stale = game("in-old", 3, lifecycle = GameLifecycle.LOBBY)
        assertEquals(listOf("in-old"), navigationGameRows(listOf(stale), listOf(stale), "a").map { it.id })
    }

    @Test fun unreadableTimestampsSortAsOldestWithoutFailing() {
        val broken = game("broken", 1, "a").copy(updatedAt = "invalid")
        assertEquals(listOf("broken", "fine"), orderedGames(listOf(game("fine", 1, "a"), broken), "a").map { it.id })
    }

    private fun pod(id: String, hour: Int, attention: PodAttention = PodAttention.NONE,
        phase: PodPhase = PodPhase.DRAFTING) = PodSummary(id, id, phase, self, listOf(self, other), attention, 2,
        "2026-10-24T${hour.toString().padStart(2, '0')}:00:00Z")
    private fun invitation(id: String, hour: Int) = PodInvitation(id, id, other, pod(id, hour).updatedAt)

    // DRF-014, LOB-017
    @Test fun podPicksAndBuildActionsComeFirstOldestFirstThenOtherPodsNewestFirstCappedAtThree() {
        val pods = listOf(pod("other-old", 1), pod("pick-new", 10, PodAttention.YOUR_PICK),
            pod("other-new", 20, phase = PodPhase.SETUP), pod("build-old", 5, PodAttention.BUILD, PodPhase.DRAFTED))
        assertEquals(listOf("build-old", "pick-new", "other-new"), quickContinuePods(pods, emptyList()).map { it.id })
        assertEquals(listOf("other-new", "other-old"),
            quickContinuePods(pods.filter { it.attention == PodAttention.NONE }, emptyList()).map { it.id })
    }

    // DRF-014, LOB-017
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
