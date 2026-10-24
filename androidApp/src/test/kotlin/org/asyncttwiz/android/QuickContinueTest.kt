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

    @Test fun lobbiesAndEndedGamesNeverAppearAndEmptyListHasNoEntries() {
        val excluded = listOf(GameLifecycle.LOBBY, GameLifecycle.CANCELLED, GameLifecycle.DELETED)
            .map { game(it.name, 1, "a", it) }
        assertTrue(quickContinueGames(excluded, "a").isEmpty())
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
}
