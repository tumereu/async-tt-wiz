package org.asyncttwiz.android

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.*

class StorageSyncRegressionTest {
    private val f = DownloadedTreeFixture

    @Test fun endedPlayRetiresOutstandingRecoveryAndIgnoresItsLateManifest() = Fixture().use { x ->
        x.connect()
        val old = x.transfer()
        val lobby = x.game.copy(lifecycle = GameLifecycle.LOBBY, playId = null, revision = "2",
            preparationRound = f.id(20000))
        val head = GameHead(x.game.id, "2", lobby)
        x.message(head)
        x.wire.reads.single()(old)
        x.drain()
        repeat(6) { x.time += 30_000; x.session.tick(); x.message(head) }
        assertTrue(x.session.ready(x.game.id), "An obsolete play download must not block the lobby")
        assertTrue(x.session.submit(lobby, GameAction.CancelSetup))
        assertNull(x.session.retainedTree(x.game.id))
    }

    @Test fun unavailableAndDeletedGamesEvictWorkerOwnedMaterializations() {
        for (deleted in listOf(false, true)) Fixture().use { x ->
            x.connect(); x.publish()
            assertEquals(1, x.downloads().size)
            if (deleted) x.message(GameHead(x.game.id, "2", x.game.copy(
                lifecycle = GameLifecycle.DELETED, revision = "2", playId = null)))
            else x.session.unavailable(x.game.id)
            x.drain()
            assertTrue(x.downloads().isEmpty(), "Clearing a game must release its downloaded tree")
        }
    }

    @Test fun reconnectAtSameHeadKeepsRealSqliteCacheUsableForNextDelta() = Fixture().use { x ->
        x.connect(); x.publish()
        x.messageDisconnect()
        x.connect("2")
        assertTrue(x.session.ready(x.game.id))
        val update = x.next()
        x.message(update)
        assertEquals(1, x.wire.reads.size, "A resumed cache must not force another baseline")
        assertEquals(x.tree.stateAt(x.tree.currentNodeId), x.session.acceptedPlay(x.game))
        assertEquals(2L, x.worker.submit<CachedGameplayTree?> { x.cache!!.accepted() }.get(10, TimeUnit.SECONDS)?.version?.revision)
    }

    @Test fun resubscribeUsesAcceptedReplicaNotLeadingHttpSummary() = Fixture().use { x ->
        x.connect(); x.publish()
        x.messageDisconnect()
        val update = x.next()
        x.session.watch(listOf(update.summary)) // HTTP list refresh has no gameplay delta.
        x.authorize("2")
        assertEquals("1", x.wire.frames.filterIsInstance<SubscribeGame>().last().revision)
        x.message(x.head())
        x.message(update)
        x.time += 3_000; x.session.tick(); x.drain()
        assertEquals(1, x.wire.reads.size)
        assertEquals(x.tree.currentNodeId, x.session.acceptedPlay(x.game)?.currentNodeId)
        assertTrue(x.session.ready(x.game.id))
    }

    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        val reads = mutableListOf<(GameplayTreeTransfer?) -> Unit>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames += message; return true }
        override fun treeTransfer(gameId: String, requestIds: List<String>, rejected: (String) -> Unit,
            complete: (GameplayTreeTransfer?) -> Unit): Boolean {
            reads += complete; return true
        }
        override fun disconnect() = Unit
        override fun close() = Unit
    }

    private inner class Fixture : AutoCloseable {
        val wire = Wire()
        val posts = LinkedBlockingQueue<() -> Unit>()
        val worker = Executors.newSingleThreadExecutor()
        val storage = (ClientStorage.open { JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY,
            java.util.Properties().apply { setProperty("foreign_keys", "true") }) } as StorageOpenResult.Opened).storage.also { it.activateAccount(f.a) }
        var cache: GameplayTreeStore? = null
        var tree = f.root()
        var revision = 1L
        var time = 0L
        val game = GameSummary(tree.initial.gameId, "Sync", "date", AccountIdentity(f.a, "A"), AccountIdentity(f.b, "B"),
            tree.initial.preparationRound, f.id(90), InvitationState.ACCEPTED, lifecycle = GameLifecycle.ACTIVE,
            revision = "1", playId = tree.initial.id)
        val session = GameSession(wire, { "A".repeat(43) }, { posts += it }, {}, {}, { _, _ -> }, {},
            accountId = { f.a }, treeCache = { account, game, play -> GameplayTreeCache.bind(storage, account, game, play).also { cache = it } },
            treeWorker = worker, reviewWorker = worker, now = { time })
        fun head() = GameHead(game.id, revision.toString(), game.copy(revision = revision.toString()), play = tree.stateAt(tree.currentNodeId))
        fun drain() { repeat(4) { worker.submit {}.get(10, TimeUnit.SECONDS); while (true) (posts.poll() ?: break).invoke() } }
        fun message(value: GameServerMessage) { wire.listener.message(value); drain() }
        fun messageDisconnect() { wire.listener.disconnected(); drain(); session.tick() }
        fun connect(generation: String = "1") { session.watch(listOf(game)); drain(); authorize(generation); message(head()) }
        fun authorize(generation: String) {
            wire.listener.opened(); drain(); message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            val stream = wire.frames.mapNotNull { when (it) { is OpenGameStream -> it.streamId; is ResumeGameStream -> it.streamId; else -> null } }.last()
            message(GameStreamReady(game.id, stream, generation, emptyList()))
        }
        fun transfer(): GameplayTreeTransfer {
            val value = f.transfer(TreeSync.generation(tree), revision, chunkSize = 65536)
            val manifest = GameplayManifest(value.plan.id, game.id, tree.initial.id, revision.toString(),
                value.plan.chunks.map { GameplayChunk(it.index, it.bytes, it.digest) }, emptyList(), recordCount = value.plan.recordCount,
                head = head()).let { it.copy(digest = GameplaySync.manifestDigest(it)) }
            return GameplayTreeTransfer(manifest, value.plan.copy(transportManifest = StoredPayload(1, GameWire.json.encodeToString(manifest)))) {
                value.compressed[it].inputStream()
            }
        }
        fun publish() { wire.reads.last()(transfer()); drain() }
        fun next(): GameRevision {
            val before = tree
            tree = f.tap(tree)
            val base = revision++
            return GameRevision(game.id, base.toString(), revision.toString(), f.id(100), f.a, GameScope(game.id, playId = tree.initial.id),
                game.copy(revision = revision.toString()), treeDelta = TreeSync.delta(before, tree))
        }
        @Suppress("UNCHECKED_CAST")
        fun downloads(): Map<String, *> = worker.submit<Map<String, *>> {
            (GameSession::class.java.getDeclaredField("downloads").also { it.isAccessible = true }.get(session) as Map<String, *>).toMap()
        }.get(10, TimeUnit.SECONDS)
        override fun close() { session.close(); assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS)); storage.close() }
    }
}
