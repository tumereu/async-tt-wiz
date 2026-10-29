package org.asyncttwiz.server.activity

import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.game.GameService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OpponentPresenceTest {
    private fun id() = UUID.randomUUID().toString()
    private val start = OffsetDateTime.parse("2026-10-27T12:00:00Z")

    @Test fun `state comes from live rows idle from the freshest input and last seen from the newest heartbeat`() {
        fun row(heartbeat: Long, input: Long, left: Boolean = false) =
            PresenceRow(UUID.randomUUID(), start.plusSeconds(heartbeat), start.plusSeconds(input), left)
        fun at(seconds: Long, vararg rows: PresenceRow) = OpponentPresences.of(rows.toList(), start.plusSeconds(seconds))
        assertEquals(OpponentPresence(PresenceState.OFFLINE), at(0))
        assertEquals(OpponentPresence(PresenceState.ONLINE, 0, 0), at(0, row(0, 0)))
        assertEquals(OpponentPresence(PresenceState.ONLINE, 119, 4), at(119, row(115, 0)))
        assertEquals(OpponentPresence(PresenceState.IDLE, 120, 0), at(120, row(120, 0)))
        // Another open device's input keeps the player online.
        assertEquals(PresenceState.ONLINE, at(130, row(130, 0), row(128, 100)).state)
        assertEquals(OpponentPresence(PresenceState.OFFLINE, lastSeenSeconds = 15), at(15, row(0, 0)))
        assertEquals(OpponentPresence(PresenceState.OFFLINE, lastSeenSeconds = 3), at(13, row(10, 10, left = true)))
        assertEquals(OpponentPresence(PresenceState.OFFLINE, lastSeenSeconds = 2 * 86400), at(2 * 86400L, row(0, 0, left = true)))
        // Buckets: fresh, late (client turns idle by itself after this), idle, offline.
        assertEquals(3, OpponentPresences.bucket(listOf(row(89, 0)), start.plusSeconds(89)))
        assertEquals(2, OpponentPresences.bucket(listOf(row(90, 0)), start.plusSeconds(90)))
        assertEquals(1, OpponentPresences.bucket(listOf(row(120, 0)), start.plusSeconds(120)))
        assertEquals(0, OpponentPresences.bucket(listOf(row(0, 0)), start.plusSeconds(15)))
    }

    @Test fun `heartbeats report only flips and heads carry the opponent's presence`() {
        ActivityFixture().use { f ->
            val game = f.accept(f.create())
            val clock = ActivityClock(Instant.now().truncatedTo(ChronoUnit.MICROS)); val first = clock.time
            val service = ActivityService(f.runtime, clock)
            val games = GameService(f.runtime, clock)
            fun head() = games.subscriptionHead(f.b, game.id, presence = true).opponentPresence
            assertEquals(OpponentPresence(PresenceState.OFFLINE), head())
            assertNull(games.subscriptionHead(f.b, game.id).opponentPresence)
            val phone = GamePresence(game.id, id())
            val opened = service.heartbeat(f.a, phone)!!
            assertEquals(PresenceChange(game.id, f.a.account.accountId, OpponentPresence(PresenceState.ONLINE, 0, 0)), opened)
            fun quiet(seconds: LongProgression) = seconds.forEach {
                clock.time = first.plusSeconds(it); assertNull(service.heartbeat(f.a, phone), "no flip at $it s")
            }
            // Quiet heartbeats: the late bucket starts at 90 s without a push (clients count on their own) ...
            quiet(5L..85L step 5)
            clock.time = first.plusSeconds(85); assertEquals(OpponentPresence(PresenceState.ONLINE, 85, 0), head())
            // ... but input after it is pushed, so a client never shows idle past input it has not heard of.
            clock.time = first.plusSeconds(95)
            assertEquals(OpponentPresence(PresenceState.ONLINE, 0, 0), service.heartbeat(f.a, phone.copy(didSomething = true))!!.presence)
            clock.time = first.plusSeconds(100); assertNull(service.heartbeat(f.a, phone.copy(didSomething = true)))
            quiet(105L..220L step 5)
            assertEquals(OpponentPresence(PresenceState.IDLE, 120, 0), head())
            clock.time = first.plusSeconds(225)
            assertEquals(PresenceState.ONLINE, service.heartbeat(f.a, phone.copy(didSomething = true))!!.presence.state)
            // A second device leaving does not flip; the last one leaving does.
            val tablet = GamePresence(game.id, id())
            assertNull(service.heartbeat(f.a, tablet))
            assertNull(service.heartbeat(f.a, tablet.copy(left = true)))
            clock.time = first.plusSeconds(227)
            assertEquals(OpponentPresence(PresenceState.OFFLINE, lastSeenSeconds = 0), service.heartbeat(f.a, phone.copy(left = true))!!.presence)
            clock.time = first.plusSeconds(227 + 3 * 3600L)
            assertEquals(OpponentPresence(PresenceState.OFFLINE, lastSeenSeconds = 3 * 3600), head())
            // Timing out (app killed) is noticed by the next head, not by a heartbeat.
            assertEquals(PresenceState.ONLINE, service.heartbeat(f.a, phone)!!.presence.state)
            clock.time = clock.time.plusSeconds(15)
            assertEquals(OpponentPresence(PresenceState.OFFLINE, lastSeenSeconds = 15), head())
            // The viewer's own rows never count as the opponent's.
            assertEquals(OpponentPresence(PresenceState.OFFLINE), games.subscriptionHead(f.a, game.id, presence = true).opponentPresence)
        }
    }

    @Test fun `pruning keeps each player's newest row so last seen survives for days`() {
        ActivityFixture().use { f ->
            val game = f.accept(f.create())
            val clock = ActivityClock(Instant.now().truncatedTo(ChronoUnit.MICROS)); val first = clock.time
            val service = ActivityService(f.runtime, clock)
            service.heartbeat(f.a, GamePresence(game.id, id(), left = true))
            clock.time = first.plusSeconds(60); service.heartbeat(f.a, GamePresence(game.id, id(), left = true))
            service.heartbeat(f.b, GamePresence(game.id, id(), left = true))
            clock.time = first.plus(Duration.ofDays(3)); service.pruneExpired()
            assertEquals(2L, f.number("SELECT count(*) FROM activity_presence"))
            assertEquals(OpponentPresence(PresenceState.OFFLINE, lastSeenSeconds = (3 * 86400 - 60)),
                GameService(f.runtime, clock).subscriptionHead(f.b, game.id, presence = true).opponentPresence)
        }
    }
}
