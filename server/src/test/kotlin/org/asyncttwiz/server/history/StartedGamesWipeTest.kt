package org.asyncttwiz.server.history

import java.util.UUID
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.accounts.AccountService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** The wipe of the engine generalisation (V74, owner 2026-11-08), run again on a live server's latest schema: what players
 * see of it afterwards. */
class StartedGamesWipeTest {
    private fun list(w: HistoryWorld, actor: AccountService.Principal, since: String? = null) =
        GameWire.json.decodeFromString<GameList>(w.lobby.list(actor, null, false, since).body)

    private fun wipe(w: HistoryWorld) {
        val text = checkNotNull(javaClass.getResourceAsStream("/db/migration/V74__engine_general_forms_wipe_plays.sql")).reader().readText()
        w.runtime.transaction { connection -> connection.createStatement().use { it.execute(text) } }
    }

    // LOB-133: started games go, a list read since before learns it for both players; a game that never started stays, a
    // finished match's record stays, and a match in play leaves none
    @Test fun `V74 removes started games, a changed-since read reports them gone and records stay`() = HistoryWorld.run { w ->
        val a = w.account("WipeAlice"); val b = w.account("WipeBob")
        val finished = w.Match(a, b, "Wipe finished"); finished.start(); finished.concede()
        val inPlay = w.Match(a, b, "Wipe in play"); inPlay.start()
        val waiting = w.Match(a, b, "Wipe lobby")
        val tokens = listOf(a, b).associateWith { list(w, it).token!! }
        val accountRecords = { actor: AccountService.Principal -> w.sql { MatchRecords.forAccount(it, UUID.fromString(actor.account.accountId)) } }
        assertEquals(1, accountRecords(a).size)

        wipe(w)

        assertFalse(finished.exists()); assertFalse(inPlay.exists()); assertTrue(waiting.exists())
        for ((actor, token) in tokens) {
            val since = list(w, actor, token)
            assertEquals(setOf(finished.game.id, inPlay.game.id), since.removed.toSet(), "the list learns both games are gone")
            assertEquals(listOf(waiting.game.id), list(w, actor).games.map { it.id }, "a full read has only the game never started")
        }
        assertEquals(1, accountRecords(a).size, "a finished match's record stays")
        assertEquals(1, accountRecords(b).size)
        assertEquals(0L, w.number("SELECT count(*) FROM match_record WHERE game_id = ?", inPlay.id), "a match in play leaves none")
        assertEquals(0L, w.number("SELECT count(*) FROM engine_play"))
        assertEquals(2L, w.number("SELECT count(*) FROM game_removed"))
        // Rows of the ones gone, in any table but the records' and the tombstones'
        val columns = w.sql { it.fetch("SELECT table_name, column_name FROM information_schema.columns " +
            "WHERE table_schema = 'public' AND column_name = 'game_id'") }
        for (row in columns) {
            val table = row.get(0, String::class.java)
            if (table == "match_record" || table == "game_removed") continue
            assertEquals(0L, w.number("SELECT count(*) FROM \"$table\" WHERE game_id = ? OR game_id = ?", finished.id, inPlay.id), table)
        }
    }

    // Nothing started: nothing goes, and no tombstone is written
    @Test fun `with no started game V74 does nothing`() = HistoryWorld.run { w ->
        val a = w.account("IdleAlice"); val b = w.account("IdleBob")
        val waiting = w.Match(a, b, "Never started")
        wipe(w)
        assertTrue(waiting.exists())
        assertEquals(0L, w.number("SELECT count(*) FROM game_removed"))
    }
}
