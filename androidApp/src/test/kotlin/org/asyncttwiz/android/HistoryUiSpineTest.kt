package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.*
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.asyncttwiz.storage.*

class HistoryUiSpineTest {
    private val f = DownloadedTreeFixture
    private fun command(n: Int, action: PlayAction, invocation: String? = null) = GameCommand(f.id(n),
        GameScope(f.id(1), playId = f.id(2)), GameAction.Play(action), f.id(14), "1", invocationId = invocation)
    private inner class Harness : AutoCloseable {
        val worker = Executors.newSingleThreadExecutor()
        val posts = LinkedBlockingQueue<() -> Unit>()
        var tree = f.tap(f.root())
        var display = tree.stateAt(tree.currentNodeId)!!
        val sent = mutableListOf<GameCommand>()
        var online = true
        var next = 900
        val controls = mutableListOf<GameAction>()
        val history = TableHistoryController(worker, { posts.add(it) }, { node, complete ->
            val value = tree
            worker.execute {
                val cursor = node?.let(value::resolveCursor) ?: value.currentNodeId
                val state = value.stateAt(cursor)
                posts.add { complete(DownloadedHistoryState(1L, cursor, node != null, state)) }
            }; true
        }, { action, invocation, continuation ->
            if (!online) false else {
                val command = command(next++, action, invocation).copy(continuation = continuation)
                val result = HistoryContinuation.change(tree, f.a, command)
                if (result.tree == null) false else {
                    sent.add(command); display = result.tree!!.stateAt(result.tree!!.currentNodeId)!!; true
                }
            }
        }, { controls.add(it); online })
        fun drain() { repeat(3) { worker.submit {}.get(5, TimeUnit.SECONDS); while (true) (posts.poll() ?: break).invoke() } }
        fun update() { history.update(tree, next.toString()); drain(); history.displayPosition(); drain() }
        override fun close() { history.close(); worker.shutdown(); assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS)) }
    }

    @Test fun pausedIncomingSyncAndDeletedCursorFallbackNeverSelectSharedHeadOrSend() {
        Harness().use { h ->
            h.update(); val first = h.tree.currentNodeId
            h.history.select(first); h.history.displayPosition(); h.drain()
            h.tree = f.tap(h.tree, 101, f.b, 12); h.update()
            assertEquals(first, h.history.playback!!.position.cursorId)
            assertEquals(first, h.history.view!!.cursor)
            h.tree = f.undo(h.tree); h.update()
            assertEquals(h.tree.initial.root.id, h.history.playback!!.position.cursorId)
            assertEquals(HistoryMode.PLAYBACK, h.history.playback!!.position.mode)
            assertTrue(h.sent.isEmpty()); assertTrue(h.controls.isEmpty())
        }
    }

    @Test fun explicitArmCancelFailureAndNoopDoNotForkFirstEffectiveOrdinaryToolkitPathCarriesExactBoundary() {
        Harness().use { h ->
            h.update(); val root = h.tree.initial.root.id
            h.history.select(root); h.history.displayPosition(); h.drain()
            assertTrue(h.history.arm(root)); assertTrue(h.sent.isEmpty())
            h.online = false
            assertFalse(h.history.gameplay(PlayAction.AdjustLife(f.a, "1"), null) { h.display })
            assertEquals(root, h.history.armed)
            h.online = true
            assertFalse(h.history.gameplay(PlayAction.SetTapped(f.id(9), false), null) { h.display })
            assertEquals(root, h.history.armed)
            h.history.cancel(); assertNull(h.history.armed); assertTrue(h.sent.isEmpty())
            assertTrue(h.history.arm(root))
            val toolkit = TableToolkitController(f.a, { h.history.view?.state }, { true }, { action, invocation ->
                h.history.gameplay(action, invocation) { h.display }
            })
            assertTrue(toolkit.submit(PlayAction.AdjustLife(f.a, "1")))
            val original = h.sent.single()
            assertEquals(Continuation(root, true), original.continuation)
            assertTrue(h.history.provisional)
            h.history.captured(original)
            h.history.settled(original, GameResult(original.requestId, original.scope, "rejected", ingressOrdinal = "1"))
            assertEquals(root, h.history.armed); assertFalse(h.history.provisional)
            h.history.current(); assertNull(h.history.armed)
            assertEquals(root, h.history.playback!!.position.cursorId)
            assertTrue(h.history.playback!!.position.playing)
            h.history.playback!!.next(automatic = true)
            assertEquals(HistoryMode.LIVE, h.history.playback!!.position.mode)
        }
    }

    @Test fun returnPreviewIsWorkerBuiltAndKeepsExactTargetWhileTreeGrows() {
        Harness().use { h ->
            h.tree = HistoryContinuation.change(h.tree, f.b, command(300, PlayAction.AdjustLife(f.b, "1"))
                .copy(continuation = Continuation(h.tree.initial.root.id, true))).tree!!; h.update()
            val branch = h.tree.currentBranchId
            h.history.prepareReturn(branch); h.drain()
            val confirmation = assertNotNull(h.history.confirmation)
            val action = assertNotNull(confirmation.action)
            val intent = action.intent as BranchArchiveIntent.ArchiveAndReturn
            assertNotNull(confirmation.preview.board)
            assertEquals(confirmation.preview.target, intent.target)
            assertEquals(confirmation.preview.returnHead, intent.expectedReturnHead)
            h.tree = f.tap(h.tree, 400, f.b, 12); h.update()
            assertSame(confirmation, h.history.confirmation)
            assertTrue(h.history.request(action))
            assertEquals(GameAction.BranchArchive(intent), h.controls.single())
        }
    }

    @Test fun locationPingCapturesLogicalTransformAndOriginalReferenceForOneExplicitUnping() {
        Harness().use { h ->
            val scene = TableScene(emptyList(), listOf(
                TableField(TableBounds(30f, 10f, 400f, 400f), f.b),
                TableField(TableBounds(30f, 450f, 400f, 400f), f.a)),
                footprint = 126f, camera = TableCamera(42f, 21f, 1.5f))
            val geometry = TableAnnotationGeometry(scene, f.a)
            val reference = EventReference(h.display.id, h.display.currentNodeId)
            val point = TablePoint(156f, 692.5f)
            var captured = assertNotNull(geometry.capture(h.display, reference, point))
            val target = PingTarget(PingTargetKind.LOCATION, position = BoardPosition(1, 2))
            assertEquals(listOf(PlayAction.Ping(reference, target)), captured.actions())
            assertEquals(point, geometry.endpoint(target))
            assertNotEquals(target, geometry.location(TablePoint(156f, 167.5f))) // Other battlefield, same local cell.
            val moved = scene.copy(camera = TableCamera(84f, 42f, .75f), footprint = 63f)
            val movedGeometry = TableAnnotationGeometry(moved, f.a)
            val coordinator = TableGestureCoordinator({ intent ->
                if (intent is TableIntent.Ping) captured.actions().forEach {
                    assertTrue(h.history.gameplay(it, null) { h.display })
                }
            })
            val selection = TableSelection(mode = TableMode.PING)
            coordinator.update(scene, selection, true)
            coordinator.down(point, 0)
            coordinator.update(moved, selection, true) // Reflow/camera must not reinterpret the pressed pixels.
            coordinator.up(point, 10); coordinator.tick(1000)
            assertEquals(PlayAction.Ping(reference, target), (h.sent.single().action as GameAction.Play).action)
            h.tree = HistoryContinuation.change(h.tree, f.a, h.sent.single()).tree!!
            h.display = h.tree.stateAt(h.tree.currentNodeId)!!
            val marker = assertNotNull(movedGeometry.endpoint(target))
            assertEquals(target, movedGeometry.location(marker))
            captured = assertNotNull(movedGeometry.capture(h.display, EventReference(h.display.id, h.display.currentNodeId), marker))
            assertEquals(listOf(PlayAction.Unping(reference, target)), captured.actions())
            coordinator.down(marker, 1100); coordinator.up(marker, 1110); coordinator.tick(2000)
            assertEquals(2, h.sent.size)
            assertEquals(PlayAction.Unping(reference, target), (h.sent.last().action as GameAction.Play).action)
            assertTrue(h.display.currentGroup!!.id.let { EventAnnotations.activePings(h.display.annotations, it) }.isEmpty())
        }
    }

    @Test fun currentResultRequiresActuallyPresentedFinalBoardAndHistoricalSuggestionHasNoGate() {
        Harness().use { h ->
            h.update()
            val parent = h.tree.stateAt(h.tree.currentNodeId)!!
            val command = command(800, PlayAction.SuggestResult(SuggestedResult.DRAW))
            h.tree = HistoryContinuation.change(h.tree, f.b, command).tree!!
            val accepted = h.tree.stateAt(h.tree.currentNodeId)!!
            h.update()
            assertEquals(parent.currentNodeId, h.history.playback!!.position.cursorId)
            h.history.presented(parent, accepted, false)
            assertNull(h.history.gate)
            h.history.playback!!.next()
            h.history.presented(accepted, accepted, true)
            assertEquals(accepted.board.resultSuggestion!!.id, h.history.gate!!.suggestionId)
            h.history.presented(accepted, accepted, false)
            assertNull(h.history.gate)
        }
    }

    @Test fun actualAccountStorageFactoryColdOfflineSessionPrivateClearAndAccountSwitchShareOneDriver() {
        val file = java.nio.file.Files.createTempFile(java.nio.file.Path.of("/tmp/opencode"), "history-account-", ".db")
        val uiThread = Thread.currentThread()
        val worker = Executors.newSingleThreadExecutor()
        val posts = LinkedBlockingQueue<() -> Unit>()
        var opens = 0
        val handle = AccountStorageHandle { opens++; ClientStorage.open { JdbcSqliteDriver("jdbc:sqlite:$file",
            java.util.Properties().apply { setProperty("foreign_keys", "true") }) } }
        var actor: String? = f.a
        var commands = 0
        val wire = object : GameConnection {
            override fun connect(token: String, listener: GameConnection.Listener) { error("Offline factory must not connect") }
            override fun send(message: GameClientMessage): Boolean { commands++; return false }
            override fun disconnect() = Unit
            override fun close() = Unit
        }
        val root = f.root()
        val game = GameSummary(root.initial.gameId, "Cached", "2026-10-23T00:00:00Z", AccountIdentity(f.a, "A"),
            AccountIdentity(f.b, "B"), root.initial.preparationRound, f.id(80), InvitationState.ACCEPTED,
            lifecycle = GameLifecycle.ACTIVE, revision = "1", playId = root.initial.id)
        lateinit var old: GameplayTreeStore
        val session = GameSession(wire, { null }, { posts.add(it) }, {}, {}, { _, _ -> }, {},
            accountId = { actor }, treeCache = handle::treeCache, treeWorker = worker)
        fun drain() { repeat(3) { worker.submit {}.get(5, TimeUnit.SECONDS); while (true) (posts.poll() ?: break).invoke() } }
        try {
            worker.submit {
                handle.storage { it.activateAccount(f.a) }
                old = handle.treeCache(f.a, game.id, root.initial.id)
                val transfer = f.transfer(TreeSync.generation(root))
                val download = TreeDownload(old, uiThread)
                val staged = download.stage(transfer.plan) { transfer.compressed[it].inputStream() }
                assertTrue(download.publish(staged, emptyList(), 10) is TreePublication.Published)
            }.get(10, TimeUnit.SECONDS)
            session.watch(listOf(game)); drain()
            assertEquals(root.initial.board, session.acceptedPlay(game)!!.board)
            var view: DownloadedHistoryState? = null
            assertTrue(session.review(game.id, root.initial.root.id) { view = it }); drain()
            assertEquals(root.initial.root.id, view!!.cursor)
            assertEquals(root.contextVersion(root.initial.root.id), view!!.contextVersion)
            assertEquals(0, commands); assertEquals(1, opens)
            session.clear() // immediate UI/process fence precedes disk cleanup.
            assertNull(session.acceptedPlay(game))
            worker.submit { handle.storage { it.clearPrivateData(); it.activateAccount(f.b) } }.get(5, TimeUnit.SECONDS)
            assertFailsWith<IllegalStateException> { old.accepted() }
            actor = f.b
            worker.submit { assertNull(handle.treeCache(f.b, game.id, root.initial.id).accepted()) }.get(5, TimeUnit.SECONDS)
            assertEquals(1, opens)
        } finally {
            session.close(); assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS)); handle.close()
            java.nio.file.Files.deleteIfExists(file)
        }
    }
}
