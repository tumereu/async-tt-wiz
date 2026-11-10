package org.asyncttwiz.server.history

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** The deletion of request rows holding the old model's fields (V76, owner 2026-11-10), run again on the latest schema. */
class OldFormRowsCleanupTest {
    private fun cleanup(w: HistoryWorld) {
        val text = checkNotNull(javaClass.getResourceAsStream("/db/migration/V76__delete_old_form_request_rows.sql")).reader().readText()
        w.runtime.transaction { connection -> connection.createStatement().use { it.execute(text) } }
    }

    private fun counts(w: HistoryWorld, match: HistoryWorld.Match) = listOf("game_revision", "game_inbox", "game_outbox")
        .associateWith { w.number("SELECT count(*) FROM $it WHERE game_id = ?", match.id) }

    // A revision with an old field at its top level or in its play, a request whose result holds one, and that request's
    // outbox row go; the rest of that game, a field nested deeper, a row that isn't JSON and another game all stay
    @Test fun `V76 deletes only the rows holding an old field`() = HistoryWorld.run { w ->
        val a = w.account("OldAlice"); val b = w.account("OldBob")
        val old = w.Match(a, b, "Old forms"); val other = w.Match(a, b, "Current forms")
        val oldBefore = counts(w, old); val otherBefore = counts(w, other)
        assertTrue(oldBefore.values.all { it >= 4 }, "$oldBefore")
        val revisions = w.sql { it.fetch("SELECT revision FROM game_revision WHERE game_id = ? ORDER BY revision", old.id)
            .map { row -> (row.get(0) as Number).toLong() } }
        w.execute("UPDATE game_revision SET payload = jsonb_set(payload::jsonb, '{currentNodeId}', '\"n1\"')::text " +
            "WHERE game_id = ? AND revision = ?", old.id, revisions[0])
        w.execute("UPDATE game_revision SET payload = jsonb_set(payload::jsonb, '{play}', '{\"currentGroup\": null}')::text " +
            "WHERE game_id = ? AND revision = ?", old.id, revisions[1])
        w.execute("UPDATE game_revision SET payload = jsonb_set(payload::jsonb, '{summary,mutation}', '1')::text " +
            "WHERE game_id = ? AND revision = ?", old.id, revisions[2])
        val request = w.sql { it.fetchValue("SELECT request_id FROM game_inbox WHERE game_id = ? AND result IS NOT NULL " +
            "ORDER BY ingress_ordinal LIMIT 1", old.id) }
        w.execute("UPDATE game_inbox SET result = jsonb_set(result::jsonb, '{mutation}', '{}')::text WHERE request_id = ?", request)
        assertEquals(1L, w.number("SELECT count(*) FROM game_outbox WHERE request_id = ?", request))
        w.execute("UPDATE game_outbox SET result = 'not json' WHERE id = (SELECT min(id) FROM game_outbox WHERE game_id = ?)", other.id)

        cleanup(w)

        assertEquals(mapOf("game_revision" to oldBefore["game_revision"]!! - 2, "game_inbox" to oldBefore["game_inbox"]!! - 1,
            "game_outbox" to oldBefore["game_outbox"]!! - 1), counts(w, old))
        assertEquals(0L, w.number("SELECT count(*) FROM game_revision WHERE game_id = ? AND revision IN (?, ?)",
            old.id, revisions[0], revisions[1]))
        assertEquals(1L, w.number("SELECT count(*) FROM game_revision WHERE game_id = ? AND revision = ?", old.id, revisions[2]),
            "a field nested deeper than the play is not the old model's")
        assertEquals(0L, w.number("SELECT count(*) FROM game_inbox WHERE request_id = ?", request))
        assertEquals(0L, w.number("SELECT count(*) FROM game_outbox WHERE request_id = ?", request))
        assertEquals(otherBefore, counts(w, other))
        assertTrue(old.exists()); assertTrue(other.exists())
    }
}
