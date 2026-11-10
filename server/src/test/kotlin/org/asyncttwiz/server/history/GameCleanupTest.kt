package org.asyncttwiz.server.history

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.activity.ActivityService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Game history §4: the hourly cleanup, run here at clock times the test chooses (no waiting): finished games after 7 days
 * without activity since their match ended, other games and pods after 14 days without activity, the warning at day 11 for
 * games in play and pods only, what counts as activity (§4.4), and that records stay.
 */
class GameCleanupTest {
    private val day: Duration = Duration.ofDays(1)
    private val minute: Duration = Duration.ofMinutes(1)
    private fun HistoryWorld.warnings(kind: ActivityKind, resource: UUID) =
        number("SELECT count(*) FROM account_activity WHERE kind = ? AND game_id = ?", kind.name, resource)
    private fun Instant.micros(): Instant = truncatedTo(ChronoUnit.MICROS)

    // LOB-133 (game history §4.1, 4.5, 4.6): a finished game whose match ended 7 days ago, with no activity since, is deleted (without a
    // warning); its record stays, and a games list read since before learns it is gone
    @Test fun `a finished game goes 7 days after its match ended`() = HistoryWorld.run { w ->
        val a = w.account("FinAlice"); val b = w.account("FinBob")
        val m = w.Match(a, b)
        m.start(); m.concede()
        // TRN-065 (owner, 2026-11-09): the lobby takes nothing before both OKs
        m.alice.accepted(GameAction.AcknowledgeResult)
        val token = GameWire.json.decodeFromString<GameList>(w.lobby.list(a, null, false).body).token!!
        w.clock.advance(Duration.ofDays(7).minus(minute))
        assertEquals(0, w.cleanup.run().deletedGames)
        assertTrue(m.exists())
        w.clock.advance(minute)
        assertEquals(1, w.cleanup.run().deletedGames)
        assertFalse(m.exists())
        assertEquals(0L, w.warnings(ActivityKind.GAME_REMOVAL, m.id), "finished games go without a warning")
        assertEquals(1, w.sql { MatchRecords.forAccount(it, UUID.fromString(a.account.accountId)) }.size, "the record stays")
        val since = GameWire.json.decodeFromString<GameList>(w.lobby.list(a, null, false, token).body)
        assertTrue(m.game.id in since.removed, "the list learns the game is gone")
    }

    // LOB-133 (game history §4.1, 4.2, 4.4): a lobby change after the finished match is activity: the game then goes after 14 days
    // without activity, as any other game
    @Test fun `activity after a finished match keeps the game 14 days from it`() = HistoryWorld.run { w ->
        val a = w.account("LaterAlice"); val b = w.account("LaterBob")
        val m = w.Match(a, b)
        m.start(); m.concede()
        // TRN-065 (owner, 2026-11-09): the lobby takes nothing before both OKs
        m.alice.accepted(GameAction.AcknowledgeResult)
        w.clock.advance(day)
        m.alice.accepted(GameAction.SetStarter(StarterChoice(b.account.accountId)))
        w.clock.advance(Duration.ofDays(7))
        w.cleanup.run()
        assertTrue(m.exists(), "8 days after the match, 7 after the last activity")
        w.clock.advance(Duration.ofDays(7).minus(minute))
        w.cleanup.run()
        assertTrue(m.exists())
        w.clock.advance(minute)
        w.cleanup.run()
        assertFalse(m.exists(), "14 days after the last activity")
    }

    // LOB-133, LOB-134, LOB-127 (game history §4.2, 4.5, 1.3): a game in play gets one warning at day 11 of 14 to both players ("<game> · vs <opponent>"),
    // then is deleted at day 14, leaving an Unfinished record
    @Test fun `a game in play is warned at day 11 and deleted at day 14`() = HistoryWorld.run { w ->
        val a = w.account("PlayAlice"); val b = w.account("PlayBob")
        val m = w.Match(a, b, "Stalled Spark")
        m.start()
        w.clock.advance(Duration.ofDays(11).minus(minute))
        assertEquals(0, w.cleanup.run().warnedGames)
        w.clock.advance(minute)
        assertEquals(1, w.cleanup.run().warnedGames)
        assertEquals(2L, w.warnings(ActivityKind.GAME_REMOVAL, m.id), "both players")
        val hint = ActivityService(w.runtime, w.clock).page(a, null).entries.single { it.kind == ActivityKind.GAME_REMOVAL }
        assertEquals(m.game.id, hint.gameId)
        assertEquals("Stalled Spark", hint.resourceName)
        assertEquals("PlayBob", hint.opponentName)
        assertTrue(ActivityService(w.runtime, w.clock).page(a, null).readyNotices!!.any { it.kind == ActivityKind.GAME_REMOVAL })
        assertEquals("PlayAlice", ActivityService(w.runtime, w.clock).page(b, null).entries.single { it.kind == ActivityKind.GAME_REMOVAL }.opponentName)
        w.clock.advance(day)
        assertEquals(0, w.cleanup.run().warnedGames, "warned once")
        w.clock.advance(Duration.ofDays(2).minus(minute))
        w.cleanup.run()
        assertTrue(m.exists())
        w.clock.advance(minute)
        val run = w.cleanup.run()
        assertEquals(1, run.deletedGames); assertEquals(1, run.unfinished)
        assertFalse(m.exists())
        assertEquals(MatchEnding.Unfinished, w.sql { MatchRecords.forAccount(it, UUID.fromString(b.account.accountId)) }.single().ending)
    }

    // LOB-133, LOB-134 (game history §4.4, 4.5): any change by a player is activity and resets the clock, a later warning included (a move, a
    // chat message); a device reporting what its player has seen, or a player's private settings, are not
    @Test fun `a player's change resets the clock, seeing and private settings do not`() = HistoryWorld.run { w ->
        val a = w.account("ResetAlice"); val b = w.account("ResetBob")
        val m = w.Match(a, b)
        m.start()
        val keep = m.aliceKeeps()
        val started = m.activityAt()
        assertEquals(w.clock.instant.micros(), started)
        w.clock.advance(Duration.ofDays(5))
        // Bob's device reports that he saw Alice's keep
        m.bob.accepted(GameAction.EngineSeen(keep))
        m.bob.accepted(GameAction.EngineSettings(org.asyncttwiz.protocol.game.PlaySettings(mode = org.asyncttwiz.protocol.game.ResponseMode.FullControl)))
        assertEquals(started, m.activityAt(), "seeing and private settings are not activity")
        w.clock.advance(Duration.ofDays(5))
        m.alice.accepted(GameAction.SendChat("still there?"))
        assertEquals(w.clock.instant.micros(), m.activityAt(), "a chat message")
        w.clock.advance(Duration.ofDays(11))
        assertEquals(1, w.cleanup.run().warnedGames)
        w.clock.advance(day)
        m.bobKeeps()
        assertEquals(w.clock.instant.micros(), m.activityAt(), "a move")
        w.clock.advance(Duration.ofDays(11).minus(minute))
        assertEquals(0, w.cleanup.run().warnedGames)
        assertTrue(m.exists())
        w.clock.advance(minute)
        assertEquals(1, w.cleanup.run().warnedGames, "warned again after new activity")
        assertEquals(4L, w.warnings(ActivityKind.GAME_REMOVAL, m.id))
    }

    // LOB-133 (game history §4.4): lobby changes, invitations and their answers are activity; the server's own revisions (a deck
    // revealed in another lobby when a match ends) are not
    @Test fun `lobby changes and invitation answers are activity, the server's own revisions are not`() = HistoryWorld.run { w ->
        val a = w.account("LobbyAlice"); val b = w.account("LobbyBob")
        val aDeck = w.deck(a, "Alice Red", "R"); val bDeck = w.deck(b, "Bob Green", "G")
        val waiting = w.Match(a, b, "Waiting Spark", aDeck, bDeck)
        val at = waiting.activityAt()
        assertEquals(w.clock.instant.micros(), at, "the deck choice and starter of the setup")
        for (action in listOf(GameAction.Ready, GameAction.WithdrawReady, GameAction.ShowDeck(true),
                GameAction.SelectDeck(aDeck.deck.id, aDeck.deck.revision))) {
            w.clock.advance(day)
            waiting.alice.accepted(action)
            assertEquals(w.clock.instant.micros(), waiting.activityAt(), "$action")
        }
        val before = waiting.activityAt()
        // Another game of the pair ends: Show after first game reveals Bob's deck in the waiting lobby (a server revision)
        w.clock.advance(day)
        val played = w.Match(a, b, "Played Spark", aDeck, bDeck)
        played.start(); played.concede()
        assertTrue(w.number("SELECT count(*) FROM player_preparation WHERE game_id = ? AND account_id = ?::uuid AND deck_shown",
            waiting.id, b.account.accountId) == 1L, "revealed by the server")
        assertEquals(before, waiting.activityAt(), "not activity")
        // An invitation and its answer
        val invited = GameWire.json.decodeFromString<GameSummary>(w.lobby.create(a, CreateInvitation(UUID.randomUUID().toString(),
            b.account.accountId, "Invited Spark")).body)
        w.clock.advance(day)
        w.Stream(b, invited).accepted(GameAction.DeclineInvitation)
        assertEquals(w.clock.instant.micros(), w.sql { it.select(org.asyncttwiz.server.generated.tables.Game.GAME.ACTIVITY_AT)
            .from(org.asyncttwiz.server.generated.tables.Game.GAME)
            .where(org.asyncttwiz.server.generated.tables.Game.GAME.ID.eq(UUID.fromString(invited.id))).fetchSingle().value1()!!.toInstant() })
    }

    // LOB-133, LOB-134 (game history §4.2, 4.5): an invitation or a lobby goes after 14 days without activity, without a warning
    @Test fun `invitations and lobbies go after 14 days without a warning`() = HistoryWorld.run { w ->
        val a = w.account("InvAlice"); val b = w.account("InvBob")
        w.lobby.add(a, AddContact(UUID.randomUUID().toString(), b.account.accountId))
        val invitation = GameWire.json.decodeFromString<GameSummary>(w.lobby.create(a, CreateInvitation(UUID.randomUUID().toString(),
            b.account.accountId, "Unanswered")).body)
        val lobby = w.Match(a, b, "Idle lobby")
        val ids = listOf(UUID.fromString(invitation.id), lobby.id)
        w.clock.advance(Duration.ofDays(13))
        assertEquals(0, w.cleanup.run().warnedGames)
        ids.forEach { assertEquals(0L, w.warnings(ActivityKind.GAME_REMOVAL, it)) }
        w.clock.advance(day.plus(minute))
        assertEquals(2, w.cleanup.run().deletedGames)
        ids.forEach { assertEquals(0L, w.number("SELECT count(*) FROM game WHERE id = ?", it)) }
    }

    // LOB-133, LOB-134, DRF-042 (game history §4.3, 4.5): a pod with no activity in it or its games is warned at day 11 (every member) and
    // deleted at day 14; a game of the pod with activity keeps it; deleting a pod leaves its games
    @Test fun `stalled pods are warned at day 11 and deleted at day 14`() = HistoryWorld.run { w ->
        val a = w.account("PodsAlice"); val b = w.account("PodsBob")
        fun pod(name: String): UUID = UUID.randomUUID().also { pod ->
            w.execute("INSERT INTO pod (id, name, creator_id, settings, source_name, source_snapshot, updated_at) " +
                "VALUES (?, ?, ?, '{}'::jsonb, 'Cube', '[]'::jsonb, ?::timestamptz)", pod, name, UUID.fromString(a.account.accountId),
                w.clock.instant.toString())
            for (member in listOf(a, b)) w.execute("INSERT INTO pod_member (pod_id, account_id) VALUES (?, ?)", pod, UUID.fromString(member.account.accountId))
        }
        val stalled = pod("Stalled pod")
        val busy = pod("Busy pod")
        w.clock.advance(Duration.ofDays(5))
        val game = w.Match(a, b, "Pod game")
        w.execute("UPDATE game SET pod_id = ? WHERE id = ?", busy, game.id)
        game.start()
        w.clock.advance(Duration.ofDays(6))
        val warned = w.cleanup.run()
        assertEquals(1, warned.warnedPods)
        assertEquals(2L, w.warnings(ActivityKind.POD_REMOVAL, stalled), "every member")
        assertEquals(0L, w.warnings(ActivityKind.POD_REMOVAL, busy), "its game had activity")
        val hint = ActivityService(w.runtime, w.clock).page(b, null).entries.single { it.kind == ActivityKind.POD_REMOVAL }
        assertEquals("Stalled pod", hint.resourceName)
        w.clock.advance(Duration.ofDays(3))
        assertEquals(1, w.cleanup.run().deletedPods)
        assertEquals(0L, w.number("SELECT count(*) FROM pod WHERE id = ?", stalled))
        assertEquals(0L, w.number("SELECT count(*) FROM pod_member WHERE pod_id = ?", stalled))
        assertEquals(1L, w.number("SELECT count(*) FROM pod WHERE id = ?", busy))
        // Deleting a pod leaves the games started from it
        w.sql { GameRemoval.deletePods(it, listOf(busy)) }
        assertTrue(game.exists())
    }

    // LOB-133 (game history §4): deleting a game removes everything of it (lobby, chat, matches and their event data, notices) but
    // never its records: no row of any table names the game or its plays afterwards but match_record's
    @Test fun `deleting a game removes every row of it but its records`() = HistoryWorld.run { w ->
        val a = w.account("RowsAlice"); val b = w.account("RowsBob")
        val m = w.Match(a, b)
        m.start()
        m.alice.accepted(GameAction.SendChat("hello"))
        m.bobKeeps()
        val play = UUID.fromString(m.summary().playId)
        w.clock.advance(Duration.ofDays(14))
        assertEquals(1, w.cleanup.run().deletedGames)
        val columns = w.sql { it.fetch("SELECT table_name, column_name FROM information_schema.columns " +
            "WHERE table_schema = 'public' AND column_name IN ('game_id', 'play_id')") }
        assertTrue(columns.size > 10)
        for (row in columns) {
            val table = row.get(0, String::class.java); val column = row.get(1, String::class.java)
            val named = if (column == "game_id") m.id else play
            val count = w.number("SELECT count(*) FROM \"$table\" WHERE \"$column\" = ?", named)
            if (table == "match_record" || table == "game_removed") assertEquals(1L, count, "$table.$column")
            else assertEquals(0L, count, "$table.$column")
        }
        assertFalse(m.exists())
    }

}
