package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

class PlayBatchTest {
    @Test fun `history revision cursors never split a hundred member batch`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime)
                val before = f.games.head(f.a, f.game.id).revision
                val action = PlayAction.AdjustLife(f.a.account.accountId, "1")
                val prior = f.owner.command(action)
                f.owner.execute(prior)
                val batch = f.owner.command(action).copy(action = GameAction.PlayBatch(List(100) { action }))
                val result = f.owner.execute(batch)
                assertEquals("accepted", result.outcome, result.reason)
                val opponent = f.recipient.command(PlayAction.AdjustLife(f.b.account.accountId, "1"))
                val later = f.recipient.execute(opponent)
                val page = f.games.history(f.a, f.game.id, f.root.id, before)
                assertEquals(101, page.mutations.size)
                assertEquals(prior.requestId, page.mutations.first().requestId)
                assertEquals(result.createdIds, page.mutations.drop(1).map { it.id })
                assertEquals(result.commitRevision, page.next)
                val tail = f.games.history(f.a, f.game.id, f.root.id, page.next!!)
                assertEquals(listOf(later.mutation), tail.mutations)
                assertNull(tail.next)
                val undo = f.owner.execute(batch.copy(requestId = gameplayId(), action = GameAction.Undo))
                assertEquals("accepted", undo.outcome, undo.reason)
                assertEquals("21", f.games.head(f.a, f.game.id).play!!.board.players.single { it.accountId == f.a.account.accountId }.life)
            }
        }
    }

    // SYS-004
    @Test fun `batch commits consecutive nodes one ordered publication and one deduplicated result`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime)
                val before = f.games.head(f.a, f.game.id)
                val actor = f.a.account.accountId
                val actions = List(4) { PlayAction.AdjustLife(actor, "1") }
                val command = f.owner.command(actions.first()).copy(action = GameAction.PlayBatch(actions))
                val opponent = f.recipient.command(PlayAction.AdjustLife(f.b.account.accountId, "10"))
                f.owner.admit(command); f.recipient.admit(opponent)
                val pool = Executors.newFixedThreadPool(2)
                try {
                    val executions = pool.invokeAll(List(2) { java.util.concurrent.Callable { f.games.executeOne() } })
                    executions.forEach { it.get() }
                    // A worker may skip the locked game; finish any remaining original.
                    while (f.games.executeOne()) Unit
                } finally { pool.shutdownNow() }
                val result = f.owner.result(command)
                assertEquals("accepted", result.outcome)
                assertEquals(result, f.owner.admit(command))
                assertFalse(f.games.executeOne())
                val revisions = f.games.revisions(f.a, f.game.id, before.revision).revisions
                assertEquals(listOf(command.requestId, opponent.requestId), revisions.map { it.requestId })
                val delta = revisions.first().treeDelta!!
                val nodes = delta.upserts.filter { it.kind == "node" }.map {
                    GameWire.json.decodeFromJsonElement(GameplayMutation.serializer(), it.payload)
                }
                assertEquals(4, nodes.size)
                assertEquals(result.createdIds, nodes.map { it.id })
                assertEquals(listOf(f.root.root.id) + nodes.dropLast(1).map { it.id }, nodes.map { it.parentId })
                assertEquals(actions.indices.map { CommandIds.uuidV5(command.requestId, "member/$it") }, nodes.map { it.requestId })
                assertTrue(nodes.all { it.invocationId == command.requestId })
                assertEquals(nodes.last().id, revisions.last().mutation!!.parentId)
                assertEquals(nodes.map { it.id } + revisions.last().mutation!!.id,
                    f.games.history(f.a, f.game.id, f.root.id, before.revision).mutations.map { it.id })
                val publications = f.games.publications().filter { it.gameId == f.game.id }.map {
                    GameWire.json.decodeFromString<GameServerMessage>(it.result) as GameResult
                }.filter { it.requestId == command.requestId }
                assertEquals(listOf(result), publications)
                val undone = command.copy(requestId = gameplayId(), action = GameAction.Undo)
                f.owner.admit(undone)
                assertTrue(GameService(runtime).executeOne()) // Exercise the persisted endpoint, not the accepting service's cache.
                val undoResult = f.owner.result(undone)
                assertEquals("accepted", undoResult.outcome, undoResult.reason)
                assertEquals("20", f.games.head(f.a, f.game.id).play!!.board.players.single { it.accountId == actor }.life)
                assertEquals("30", f.games.head(f.a, f.game.id).play!!.board.players.single { it.accountId == f.b.account.accountId }.life)
                assertEquals("accepted", f.owner.execute(undone.copy(requestId = gameplayId(), action = GameAction.Redo)).outcome)
                assertEquals("24", f.games.head(f.a, f.game.id).play!!.board.players.single { it.accountId == actor }.life)
            }
        }
    }

    // SYS-010
    @Test fun `member refusal persists no prefix or publication and retry recovers same rejection`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime)
                val before = f.games.head(f.a, f.game.id)
                val command = f.owner.command(PlayAction.AdjustLife(f.a.account.accountId, "1")).copy(action = GameAction.PlayBatch(listOf(
                    PlayAction.AdjustLife(f.a.account.accountId, "1"), PlayAction.SetTapped(gameplayId(), true))))
                val result = f.owner.execute(command)
                assertEquals("rejected", result.outcome)
                assertEquals("missingReference", result.reason)
                assertNull(result.commitRevision)
                assertEquals(result, f.owner.admit(command))
                assertEquals(before.play, f.games.head(f.a, f.game.id).play)
                assertEquals(before.revision, f.games.head(f.a, f.game.id).revision)
                assertTrue(f.games.revisions(f.a, f.game.id, before.revision).revisions.isEmpty())
                assertNull(f.games.publications().single { (GameWire.json.decodeFromString<GameServerMessage>(it.result) as GameResult).requestId == command.requestId }.revision)
            }
        }
    }
}
