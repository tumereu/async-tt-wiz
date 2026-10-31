package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.activity.ActivityService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlayerStatusRuntimeTest {
    @Test fun `status changes are ordinary own events that notify the opponent and never pass the move`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime); val activity = ActivityService(runtime)
                assertTrue(f.root.board.players.all { it.status == PlayerStatus.OPENING })
                val holder = f.root.board.effectiveAttentionHolder
                val before = activity.page(f.a, null).nextCursor
                val ready = f.recipient.execute(f.recipient.command(PlayAction.SetStatus("")))
                assertEquals("accepted", ready.outcome)
                val wait = f.recipient.execute(f.recipient.command(PlayAction.SetStatus("Wait")))
                assertEquals("accepted", wait.outcome)
                val head = f.games.head(f.a, f.game.id).play!!
                assertEquals(listOf(PlayerStatus.OPENING, "Wait"), listOf(f.a, f.b).map { actor ->
                    head.board.players.single { it.accountId == actor.account.accountId }.status })
                assertEquals(holder, head.board.effectiveAttentionHolder)
                assertEquals(f.root.board.turn, head.board.turn)
                // The opponent gets ordinary game activity for the status author's events.
                val entries = activity.page(f.a, before).entries
                assertTrue(entries.isNotEmpty())
                assertTrue(entries.all { it.authorId == f.b.account.accountId })
                assertEquals(head.currentGroup!!.id, entries.last().identity)
                assertTrue(activity.page(f.b, null).entries.none { it.authorId == f.b.account.accountId })
                // Invalid text is rejected by the shared rules.
                assertEquals("invalidInput", assertThrows(GameProtocolFailure::class.java) {
                    f.recipient.execute(f.recipient.command(PlayAction.SetStatus("x".repeat(PlayerStatus.MAX_LENGTH + 1)))) }.code)
            }
        }
    }
}
