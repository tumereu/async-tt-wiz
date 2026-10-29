package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.*

class OpponentPresenceViewTest {
    private fun id() = UUID.randomUUID().toString()
    private val wall = ZonedDateTime.of(2026, 10, 29, 15, 0, 0, 0, ZoneOffset.UTC)
    private fun received(state: PresenceState, idle: Int? = null, seen: Int? = null) = ReceivedPresence(OpponentPresence(state, idle, seen), 1_000)

    @Test fun dotFollowsServerStateAndOnlineTurnsIdleLocallyAfterTwoMinutes() {
        val online = received(PresenceState.ONLINE, idle = 100, seen = 0)
        assertEquals(PresenceDot.ONLINE, PresenceText.dot(online, 1_000))
        assertEquals(20_000, PresenceText.untilDotChange(online, 1_000))
        assertEquals(PresenceDot.ONLINE, PresenceText.dot(online, 20_999))
        assertEquals(PresenceDot.IDLE, PresenceText.dot(online, 21_000))
        assertNull(PresenceText.untilDotChange(online, 21_000))
        assertEquals(PresenceDot.IDLE, PresenceText.dot(received(PresenceState.IDLE, idle = 300, seen = 0), 1_000))
        assertEquals(PresenceDot.OFFLINE, PresenceText.dot(received(PresenceState.OFFLINE, seen = 5), 999_999))
        assertNull(PresenceText.untilDotChange(received(PresenceState.OFFLINE), 1_000))
        assertEquals(listOf(0xff43c463, 0xfff2c94c, 0xffaab3b6).map { androidx.compose.ui.graphics.Color(it) },
            PresenceDot.entries.map { it.color })
    }

    @Test fun infoLineSaysOnlineIdleMinutesOrWhenLastSeen() {
        fun line(r: ReceivedPresence, elapsed: Long = 0) = PresenceText.line(r, r.receivedAt + elapsed, wall)
        assertEquals("Online", line(received(PresenceState.ONLINE, idle = 0, seen = 0)))
        assertEquals("Idle 2 min", line(received(PresenceState.ONLINE, idle = 100, seen = 0), elapsed = 20_000))
        assertEquals("Idle 7 min", line(received(PresenceState.IDLE, idle = 400, seen = 0), elapsed = 30_000))
        assertEquals("Idle 2 h", line(received(PresenceState.IDLE, idle = 7300, seen = 0)))
        assertEquals("Not seen yet", line(received(PresenceState.OFFLINE)))
        assertEquals("Last seen just now", line(received(PresenceState.OFFLINE, seen = 20)))
        assertEquals("Last seen 12 min ago", line(received(PresenceState.OFFLINE, seen = 700), elapsed = 20_000))
        assertEquals("Last seen 3 h ago", line(received(PresenceState.OFFLINE, seen = 3 * 3600 + 59)))
        assertEquals("Last seen Oct 27", line(received(PresenceState.OFFLINE, seen = 2 * 86400 + 60)))
        assertEquals("Last seen Oct 29, 2025", line(received(PresenceState.OFFLINE, seen = 365 * 86400)))
    }

    @Test fun routineHeadsThatPredictNothingNewAreTheSame() {
        val old = received(PresenceState.OFFLINE, seen = 100)
        assertTrue(PresenceText.same(old, ReceivedPresence(OpponentPresence(PresenceState.OFFLINE, null, 130), 31_000)))
        assertFalse(PresenceText.same(old, ReceivedPresence(OpponentPresence(PresenceState.OFFLINE, null, 5), 31_000)))
        assertFalse(PresenceText.same(old, ReceivedPresence(OpponentPresence(PresenceState.ONLINE, 0, 0), 31_000)))
        val idle = received(PresenceState.ONLINE, idle = 10, seen = 0)
        assertTrue(PresenceText.same(idle, ReceivedPresence(OpponentPresence(PresenceState.ONLINE, 40, 0), 31_000)))
        assertFalse(PresenceText.same(idle, ReceivedPresence(OpponentPresence(PresenceState.ONLINE, 2, 0), 31_000)))
    }

    @Test fun sessionOffersMinorOneKeepsPresenceFromHeadsAndUpdatesAndDropsItOnDisconnect() {
        val a = AccountIdentity(id(), "A"); val b = AccountIdentity(id(), "B")
        val game = GameSummary(id(), "Presence", "2026-10-29T00:00:00Z", a, b, id(), id(), InvitationState.PENDING)
        val frames = mutableListOf<GameClientMessage>()
        lateinit var listener: GameConnection.Listener
        fun connected(value: GameConnection.Listener) { listener = value }
        val wire = object : GameConnection {
            override fun connect(token: String, listener: GameConnection.Listener) { connected(listener) }
            override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
            override fun disconnect() = Unit
            override fun close() = Unit
        }
        val posts = ArrayDeque<() -> Unit>()
        var time = 5_000L
        val session = GameSession(wire, { "token" }, { posts.add(it) }, {}, {}, { _, _ -> }, {}, now = { time })
        fun drain() { while (posts.isNotEmpty()) posts.removeFirst()() }
        fun receive(message: GameServerMessage) { listener.message(message); drain() }
        try {
            session.watch(listOf(game)); listener.opened(); drain()
            assertEquals(GAME_PROTOCOL_MINOR, frames.filterIsInstance<GameHello>().single().maxMinor)
            receive(GameWelcome(minor = GAME_PROTOCOL_MINOR, gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            val stream = frames.filterIsInstance<OpenGameStream>().single()
            receive(GameStreamReady(game.id, stream.streamId, "1"))
            assertNull(session.opponentPresence(game.id))
            receive(GameHead(game.id, "0", game, opponentPresence = OpponentPresence(PresenceState.OFFLINE, lastSeenSeconds = 60)))
            assertEquals(ReceivedPresence(OpponentPresence(PresenceState.OFFLINE, lastSeenSeconds = 60), 5_000), session.opponentPresence(game.id))
            time += 30_000
            receive(GameHead(game.id, "0", game, opponentPresence = OpponentPresence(PresenceState.OFFLINE, lastSeenSeconds = 90)))
            assertEquals(5_000, session.opponentPresence(game.id)!!.receivedAt, "a head that tells nothing new keeps the old value")
            receive(GamePresenceUpdate(game.id, OpponentPresence(PresenceState.ONLINE, 0, 0)))
            assertEquals(ReceivedPresence(OpponentPresence(PresenceState.ONLINE, 0, 0), 35_000), session.opponentPresence(game.id))
            receive(GamePresenceUpdate(id(), OpponentPresence(PresenceState.ONLINE, 0, 0)))
            listener.disconnected(false); drain()
            assertNull(session.opponentPresence(game.id))
        } finally { session.close() }
    }
}
