package org.asyncttwiz.android

import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.StoredPayload
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Actual production GameSession/replica/I26 coordinator. The socket/store scheduling boundaries
 * are controlled doubles here; real PostgreSQL and file-backed SQLDelight execute in the combined pass. */
class HistoryRuntimeIntegrationTest {
    @Test fun stalePreviousPlayRevisionCannotInvalidateNewerAcceptedCacheOrStartObsoleteRecovery() {
        Fixture().use { x ->
            x.connect(); x.publish()
            assertTrue(x.session.gameplay(x.game, f.a, PlayAction.AdjustLife(f.a, "1")))
            val update = x.execute(x.commands().last())
            x.message(update)
            val reads = x.wire.reads.size
            val accepted = x.session.acceptedPlay(x.game)
            x.message(update)
            assertEquals(reads, x.wire.reads.size, "Duplicate current-play delivery cannot restart storage recovery")
            val oldPlay = f.id(998)
            x.message(GameRevision(x.game.id, "0", "1", f.id(997), f.b,
                GameScope(x.game.id, playId = oldPlay), x.game.copy(playId = oldPlay),
                treeDelta = GameplayTreeDelta(emptyList())))
            assertEquals(reads, x.wire.reads.size, "An old-incarnation delivery must be fenced before observing/invalidation")
            assertEquals(accepted, x.session.acceptedPlay(x.game))
            assertTrue(x.cache.current)
            assertTrue(x.session.historyControl(x.game, f.a, GameAction.Undo))
        }
    }
    private val f = DownloadedTreeFixture
    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        val reads = mutableListOf<Pair<List<String>, (GameplayTreeTransfer?) -> Unit>>()
        val baselines = mutableListOf<(GameplayBaseline?) -> Unit>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun treeTransfer(gameId: String, requestIds: List<String>, complete: (GameplayTreeTransfer?) -> Unit): Boolean {
            reads.add(requestIds to complete); return true
        }
        override fun baseline(gameId: String, requestIds: List<String>, complete: (GameplayBaseline?) -> Unit): Boolean {
            baselines.add(complete); return true
        }
        override fun disconnect() = Unit
        override fun close() = Unit
    }
    private inner class Fixture(persistent: Boolean = true, initial: GameplayTree = f.root()) : AutoCloseable {
        val wire = Wire()
        val posts = LinkedBlockingQueue<() -> Unit>()
        val worker = Executors.newSingleThreadExecutor()
        val cache = TreeStoreFixture()
        var actor: String? = f.a
        var token: String? = "A".repeat(43)
        var tree = initial
        var revision = 1L
        val game = GameSummary(tree.initial.gameId, "Runtime", "2026-10-23T00:00:00Z", AccountIdentity(f.a, "A"),
            AccountIdentity(f.b, "B"), tree.initial.preparationRound, f.id(90), InvitationState.ACCEPTED,
            lifecycle = GameLifecycle.ACTIVE, revision = "1", playId = tree.initial.id)
        val results = mutableListOf<GameResult>()
        val session = GameSession(wire, { token }, { posts.add(it) }, {}, {}, { _, result -> results.add(result) }, {},
            accountId = { actor }, treeCache = if (!persistent) null else { account, game, play ->
                assertEquals(cache.accountId, account); assertEquals(cache.gameId, game); assertEquals(cache.playId, play); cache
            }, treeWorker = worker)
        fun head() = GameHead(game.id, revision.toString(), game.copy(revision = revision.toString()),
            play = tree.stateAt(tree.currentNodeId))
        fun drain() {
            repeat(3) {
                worker.submit {}.get(10, TimeUnit.SECONDS)
                while (true) (posts.poll() ?: break).invoke()
            }
        }
        fun message(message: GameServerMessage) { wire.listener.message(message); drain() }
        fun connect(generation: String = "1", statuses: List<GameRequestStatus> = emptyList(), required: Boolean = false) {
            session.watch(listOf(game)); wire.listener.opened(); drain(); message(GameWelcome())
            val stream = wire.frames.mapNotNull { when (it) { is OpenGameStream -> it.streamId; is ResumeGameStream -> it.streamId; else -> null } }.last()
            message(GameStreamReady(game.id, stream, generation, statuses)); message(head().copy(retainedTreeRequired = required))
        }
        fun transfer(statuses: List<GameRequestStatus> = emptyList(), snapshotTree: GameplayTree = tree,
            snapshotRevision: Long = revision): GameplayTreeTransfer {
            val transfer = f.transfer(TreeSync.generation(snapshotTree), snapshotRevision, n = 600 + snapshotRevision.toInt())
            val head = GameHead(game.id, snapshotRevision.toString(), game.copy(revision = snapshotRevision.toString()),
                play = snapshotTree.stateAt(snapshotTree.currentNodeId))
            val manifest = GameplayManifest(transfer.plan.id, game.id, tree.initial.id, snapshotRevision.toString(),
                transfer.plan.chunks.map { GameplayChunk(it.index, it.bytes, it.digest) }, statuses,
                recordCount = transfer.plan.recordCount, head = head).let { it.copy(digest = GameplaySync.manifestDigest(it)) }
            val plan = transfer.plan.copy(transportManifest = StoredPayload(1, GameWire.json.encodeToString(manifest)))
            return GameplayTreeTransfer(manifest, plan) { transfer.compressed[it].inputStream() }
        }
        fun publish(statuses: List<GameRequestStatus> = emptyList()) { wire.reads.last().second(transfer(statuses)); drain() }
        fun execute(command: GameCommand, author: String = f.a): GameRevision {
            val before = tree
            val change = HistoryContinuation.change(before, author, command)
            assertEquals("accepted", change.outcome, change.reason)
            tree = assertNotNull(change.tree)
            val base = revision++
            return GameRevision(game.id, base.toString(), revision.toString(), command.requestId, author, command.scope,
                game.copy(revision = revision.toString()), treeDelta = TreeSync.delta(before, tree))
        }
        fun commands() = wire.frames.filterIsInstance<GameCommand>()
        override fun close() { session.close(); assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS)) }
    }

    private fun untapRoot(): GameplayTree {
        val initial = f.root().initial
        return GameplayTree(initial.copy(board = initial.board.copy(
            cards = initial.board.cards.map { if (it.owner == f.a) it.copy(tapped = true, covered = false,
                placement = PlayPlacement(BoardPosition(0, 0), f.id(30))) else it },
            containers = initial.board.containers.map { if (it.accountId == f.a) it.copy(zone = PlayZone.BATTLEFIELD) else it } +
                listOf(PlayContainer(f.id(20), PlayZone.LIBRARY, f.a), PlayContainer(f.id(21), PlayZone.HAND, f.a)))))
    }

    @Test fun untapUndoUntapUndoRedoPredictImmediatelyAndReconcileWithoutAcknowledgementWaypoints() {
        for (resultFirst in listOf(false, true)) Fixture(initial = untapRoot()).use { x ->
            x.connect(); x.publish()
            val initial = x.tree
            val actions = TableActionController({ x.session.play(x.game) }, { true },
                { x.session.gameplay(x.game, f.a, it) }, RecordingRoutes())
            fun untap() {
                val play = x.session.play(x.game)!!
                val quick = QuickActions.contextual(play, QuickContext(), actions).single { it.name == "Untap all" }
                assertEquals(listOf(f.id(9)), (quick.intent as TableActionIntent.Untap).cardIds)
                assertTrue(actions.perform(quick.intent))
                assertFalse(x.session.play(x.game)!!.board.cards.first().tapped)
            }
            fun control(action: GameAction, tapped: Boolean) {
                assertTrue(x.session.historyControl(x.game, f.a, action))
                assertEquals(tapped, x.session.play(x.game)!!.board.cards.first().tapped)
                assertEquals(x.session.play(x.game), x.session.displayTree(x.game.id)!!.let { it.stateAt(it.currentNodeId) })
            }
            untap(); control(GameAction.Undo, true)
            assertEquals(initial.currentNodeId, x.session.play(x.game)!!.currentNodeId)
            untap(); control(GameAction.Undo, true); control(GameAction.Redo, false)
            val originals = x.commands()
            assertEquals(5, originals.size, "Every original is sent before any acknowledgement")
            assertEquals(initial.initial, x.session.acceptedPlay(x.game))
            val predicted = x.session.play(x.game)
            val predictedTree = x.session.displayTree(x.game.id)
            for (command in originals) {
                val update = x.execute(command)
                val result = GameResult(command.requestId, command.scope, "accepted", ingressOrdinal = "1",
                    commitRevision = update.resultRevision)
                if (resultFirst) x.message(result)
                assertEquals(predicted, x.session.play(x.game))
                x.message(update)
                if (!resultFirst) x.message(result)
                x.message(update); x.message(result)
                assertEquals(predicted, x.session.play(x.game), "No accepted-prefix flash")
            }
            assertEquals(predictedTree, x.session.retainedTree(x.game.id))
            assertEquals(predicted, x.session.acceptedPlay(x.game))
            assertFalse(x.session.pending(x.game.id))
        }
    }

    @Test fun rejectedAndAdmissionRefusedControlsRollBackAndKeepExistingExplanations() {
        for (admission in listOf(false, true)) Fixture(initial = untapRoot()).use { x ->
            x.connect(); x.publish()
            assertTrue(x.session.gameplay(x.game, f.a, PlayAction.SetTapped(f.id(9), false)))
            val untap = x.commands().last()
            x.message(x.execute(untap))
            x.message(GameResult(untap.requestId, untap.scope, "accepted", ingressOrdinal = "1", commitRevision = "2"))
            val accepted = x.session.acceptedPlay(x.game)
            assertTrue(x.session.historyControl(x.game, f.a, GameAction.Undo))
            val undo = x.commands().last()
            assertTrue(x.session.play(x.game)!!.board.cards.first().tapped)
            if (admission) x.message(GameAdmissionError("invalidInput", false, undo.requestId, x.game.id))
            else x.message(GameResult(undo.requestId, undo.scope, "rejected", "referenceBlocked", ingressOrdinal = "2"))
            assertEquals(accepted, x.session.play(x.game))
            assertEquals(accepted, x.session.acceptedPlay(x.game))
            assertTrue(x.session.error!!.startsWith("Can't undo:"))
            if (!admission) assertTrue("later event refers" in x.session.error!!)
            assertFalse(x.session.pending(x.game.id))
            assertTrue(x.session.gameplay(x.game, f.a, PlayAction.SetTapped(f.id(9), true)))
        }
    }

    @Test fun recoveryCorrelatesAnAcceptedPrefixBeforeReplayingPendingControlsRegardlessOfStatusOrder() {
        Fixture(initial = untapRoot()).use { x ->
            x.connect(); x.publish()
            fun untap() = assertTrue(x.session.gameplay(x.game, f.a, PlayAction.SetTapped(f.id(9), false)))
            fun control(action: GameAction) = assertTrue(x.session.historyControl(x.game, f.a, action))
            untap(); control(GameAction.Undo); untap(); control(GameAction.Undo); control(GameAction.Redo)
            val commands = x.commands()
            val predicted = x.session.play(x.game)
            val updates = commands.take(2).map { x.execute(it) } // Lose both accepted envelopes/results.
            x.message(x.head())
            val statuses = commands.mapIndexed { index, command ->
                if (index >= 2) GameRequestStatus(command.requestId, "pending")
                else GameRequestStatus(command.requestId, "final", GameResult(command.requestId, command.scope,
                    "accepted", ingressOrdinal = "1", commitRevision = updates[index].resultRevision))
            }.reversed()
            x.publish(statuses)
            assertTrue(x.session.ready(x.game.id))
            assertEquals(predicted, x.session.play(x.game))
            commands.drop(2).forEach { x.message(x.execute(it)) }
            assertEquals(predicted, x.session.acceptedPlay(x.game))
            assertEquals(predicted, x.session.play(x.game))
        }
    }

    @Test fun rejectedRedoRollsBackToUndoneBoard() {
        Fixture(initial = untapRoot()).use { x ->
            x.connect(); x.publish()
            assertTrue(x.session.gameplay(x.game, f.a, PlayAction.SetTapped(f.id(9), false)))
            x.message(x.execute(x.commands().last()))
            assertTrue(x.session.historyControl(x.game, f.a, GameAction.Undo))
            x.message(x.execute(x.commands().last()))
            val accepted = x.session.acceptedPlay(x.game)
            assertTrue(x.session.historyControl(x.game, f.a, GameAction.Redo))
            val redo = x.commands().last()
            assertFalse(x.session.play(x.game)!!.board.cards.first().tapped)
            x.message(GameResult(redo.requestId, redo.scope, "rejected", "missingAnchor", ingressOrdinal = "3"))
            assertEquals(accepted, x.session.play(x.game))
            assertTrue("original place" in x.session.error!!)
        }
    }

    @Test fun interveningRemotePlayWithdrawsTerminalPredictionAndFencesFollowingOverlaysUntilReconciliation() {
        Fixture(initial = untapRoot()).use { x ->
            x.connect(); x.publish()
            assertTrue(x.session.gameplay(x.game, f.a, PlayAction.SetTapped(f.id(9), false)))
            x.message(x.execute(x.commands().last()))
            assertTrue(x.session.historyControl(x.game, f.a, GameAction.Undo))
            val undo = x.commands().last()
            assertTrue(x.session.gameplay(x.game, f.a, PlayAction.AdjustLife(f.a, "3")))
            assertTrue(x.session.play(x.game)!!.board.cards.first().tapped)
            val remote = undo.copy(requestId = f.id(981), action = GameAction.Play(PlayAction.AdjustLife(f.b, "1")))
            x.message(x.execute(remote, f.b))
            assertFalse(x.session.play(x.game)!!.board.cards.first().tapped)
            assertEquals("20", x.session.play(x.game)!!.board.players.first().life)
            assertFalse(x.session.gameplay(x.game, f.a, PlayAction.AdjustLife(f.a, "1")))
            x.message(x.execute(undo)) // Server correctly performs selective Undo with the remote suffix.
            assertTrue(x.session.play(x.game)!!.board.cards.first().tapped)
            assertEquals("23", x.session.play(x.game)!!.board.players.first().life)
            x.message(x.execute(x.commands().last()))
            assertEquals(x.session.acceptedPlay(x.game), x.session.play(x.game))
        }
    }

    @Test fun withdrawingAnEarlierOriginalNeverRetargetsPredictedUndoToAnOlderAcceptedEvent() {
        Fixture().use { x ->
            x.connect(); x.publish()
            assertTrue(x.session.gameplay(x.game, f.a, PlayAction.AdjustLife(f.a, "1")))
            x.message(x.execute(x.commands().last()))
            val accepted = x.session.acceptedPlay(x.game)
            assertTrue(x.session.gameplay(x.game, f.a, PlayAction.AdjustLife(f.a, "2")))
            val earlier = x.commands().last()
            assertTrue(x.session.historyControl(x.game, f.a, GameAction.Undo))
            assertEquals(accepted!!.board, x.session.play(x.game)!!.board)
            x.session.withdrawGameplayPrediction(x.game.id, earlier.requestId)
            assertEquals(accepted, x.session.play(x.game), "Do not undo the older +1 when +2 is still uncertain")
            assertFalse(x.session.gameplay(x.game, f.a, PlayAction.AdjustLife(f.a, "1")))
            x.message(x.execute(earlier))
            x.message(x.execute(x.commands().last()))
            assertEquals(accepted.board, x.session.play(x.game)!!.board)
        }
    }

    @Test fun predictedStepUndoRestoresContextualQuickActionsBeforeTheServerResponds() {
        Fixture(initial = untapRoot()).use { x ->
            x.connect(); x.publish()
            val actions = TableActionController({ x.session.play(x.game) }, { true },
                { x.session.gameplay(x.game, f.a, it) }, RecordingRoutes())
            fun quickNames() = QuickActions.contextual(x.session.play(x.game)!!, QuickContext(), actions).map { it.name }
            val initial = quickNames()
            assertTrue("Untap all" in initial)
            assertTrue(x.session.gameplay(x.game, f.a, PlayAction.NextStep))
            assertTrue(quickNames().isEmpty())
            assertTrue(x.session.historyControl(x.game, f.a, GameAction.Undo))
            assertEquals(initial, quickNames())
            assertTrue(x.session.historyControl(x.game, f.a, GameAction.Redo))
            assertTrue(quickNames().isEmpty())
            assertEquals(3, x.commands().size)
        }
    }

    @Test fun selectiveAndRevealBlockedControlsRemainServerOnlyAndKeepCaptureBarrier() {
        for (revealed in listOf(false, true)) Fixture().use { x ->
            x.connect(); x.publish()
            assertTrue(x.session.gameplay(x.game, f.a, if (revealed)
                PlayAction.ShowContents(f.id(10), ContentsSelection.ALL, reveal = true) else PlayAction.AdjustLife(f.a, "1")))
            x.message(x.execute(x.commands().last()))
            if (!revealed) {
                val remote = GameCommand(f.id(980), GameScope(x.game.id, playId = x.tree.initial.id),
                    GameAction.Play(PlayAction.AdjustLife(f.b, "1")), f.id(14), "1")
                x.message(x.execute(remote, f.b))
            }
            val before = x.session.play(x.game)
            assertTrue(x.session.historyControl(x.game, f.a, GameAction.Undo))
            assertEquals(before, x.session.play(x.game))
            assertEquals(GameAction.Undo, x.commands().last().action)
            assertFalse(x.session.gameplay(x.game, f.a, PlayAction.AdjustLife(f.a, "1")))
            if (revealed) {
                val undo = x.commands().last()
                x.message(GameResult(undo.requestId, undo.scope, "rejected", "revealingTarget", ingressOrdinal = "2"))
                assertTrue("revealed hidden information" in x.session.error!!)
            } else {
                x.message(x.execute(x.commands().last()))
                assertEquals(x.tree.stateAt(x.tree.currentNodeId), x.session.play(x.game))
            }
        }
    }

    @Test fun referenceBearingLatestEventIsNotPredictedAndAnExplorerOpenedDuringPredictionUsesAcceptedBoundary() {
        Fixture().use { x ->
            x.connect(); x.publish()
            assertTrue(x.session.gameplay(x.game, f.a, PlayAction.AdjustLife(f.a, "1")))
            val accepted = x.session.retainedTree(x.game.id)!!
            val predictedCursor = x.session.play(x.game)!!.currentNodeId
            val explorer = HistoryExplorerController(java.util.concurrent.Executor { it.run() }, { it() }, { true }, { _, _ -> false })
            try {
                explorer.update(accepted, openingNodeId = accepted.resolveCursor(predictedCursor))
                assertNull(explorer.state.error)
                assertEquals(accepted.currentNodeId, explorer.state.selectedNodeId)
            } finally { explorer.close() }
            x.message(x.execute(x.commands().last()))
            val reference = EventReference(x.tree.initial.id, x.tree.currentNodeId)
            assertTrue(x.session.gameplay(x.game, f.a, PlayAction.EventComment(reference, "An exact event reference")))
            x.message(x.execute(x.commands().last()))
            val before = x.session.play(x.game)
            assertTrue(x.session.historyControl(x.game, f.a, GameAction.Undo))
            assertEquals(before, x.session.play(x.game))
            assertFalse(x.session.gameplay(x.game, f.a, PlayAction.AdjustLife(f.a, "1")))
            x.message(x.execute(x.commands().last()))
            assertEquals(x.tree.stateAt(x.tree.currentNodeId), x.session.play(x.game))
        }
    }

    @Test fun actualSessionUsesI26WorkerPublicationTypedInflightControlsAndOriginalRecovery() {
        Fixture().use { x ->
            x.connect(); assertFalse(x.session.ready(x.game.id)); x.publish(); assertTrue(x.session.ready(x.game.id))
            assertEquals(x.tree, x.session.retainedTree(x.game.id))
            assertTrue(x.session.gameplay(x.game, f.a, PlayAction.AdjustLife(f.a, "1")))
            assertTrue(x.session.gameplay(x.game, f.a, PlayAction.SetTapped(f.id(9), true)))
            val play = x.commands().takeLast(2)
            play.forEach { x.message(x.execute(it)) }
            assertTrue(x.session.historyControl(x.game, f.a, GameAction.Undo))
            assertTrue(x.session.historyControl(x.game, f.a, GameAction.Undo))
            val controls = x.commands().takeLast(2)
            val original = controls.first()
            val update = x.execute(original)
            val outcome = GameResult(original.requestId, original.scope, "accepted", ingressOrdinal = "3",
                commitRevision = update.resultRevision, targetNodeId = CommandIds.uuidV5(play.last().requestId, "board-node"))
            // Lose the accepted update/result, then recover original outcome through the same baseline.
            x.wire.listener.disconnected(); x.drain()
            assertFalse(x.session.historyControl(x.game, f.a, GameAction.Redo))
            val sentCount = x.commands().size
            val statuses = x.commands().distinctBy { it.requestId }.map {
                when (it.requestId) {
                    original.requestId -> GameRequestStatus(it.requestId, "final", outcome)
                    controls.last().requestId -> GameRequestStatus(it.requestId, "pending")
                    else -> GameRequestStatus(it.requestId, "final", GameResult(it.requestId, it.scope, "accepted", ingressOrdinal = "1", commitRevision = "2"))
                }
            }
            x.connect("2", statuses)
            x.publish(x.wire.reads.last().first.map { id -> statuses.single { it.requestId == id } })
            assertEquals(sentCount, x.commands().size)
            assertEquals(x.tree, x.session.retainedTree(x.game.id))
            assertEquals(1, x.results.count { it.requestId == original.requestId })
            assertTrue(x.cache.threads.all { it !== Thread.currentThread() })
        }
    }

    @Test fun interleavedTreeDeltaCatchupPrunedRestartAndOfflineReviewWithGraceRequestWaitingForRecovery() {
        Fixture().use { x ->
            x.connect()
            val baseline = x.transfer()
            val command = GameCommand(f.id(800), GameScope(x.game.id, playId = x.tree.initial.id),
                GameAction.Play(PlayAction.AdjustLife(f.a, "1")), f.id(14), "1")
            val update = x.execute(command)
            x.message(update)
            x.wire.reads.last().second(baseline); x.drain()
            assertTrue(x.session.ready(x.game.id)); assertEquals(x.tree, x.session.retainedTree(x.game.id))
            val oldNode = x.tree.currentNodeId
            val undo = command.copy(requestId = f.id(801), action = GameAction.Undo)
            x.message(x.execute(undo))
            val frames = x.wire.frames.size
            x.wire.listener.disconnected(); x.drain()
            var reviewed: DownloadedHistoryState? = null
            assertTrue(x.session.review(x.game.id, oldNode) { reviewed = it }); x.drain()
            assertEquals(x.tree.initial.root.id, reviewed!!.cursor); assertTrue(reviewed!!.reviewing)
            assertEquals(x.tree.initial.board, reviewed!!.state!!.board)
            assertEquals(frames, x.wire.frames.size)
            var waiting: GameCommand? = null
            assertTrue(x.session.gameplay(x.game, f.a, PlayAction.AdjustLife(f.a, "1"), captured = { waiting = it }))
            assertEquals(frames, x.wire.frames.size, "A grace-period original waits without sending on the old socket")
            // Fresh reconnect can replace all pruned envelopes from complete accepted records alone.
            x.connect("2", listOf(GameRequestStatus(waiting!!.requestId, "notAdmitted")))
            if (x.wire.reads.isNotEmpty() && !x.session.ready(x.game.id)) x.publish()
            assertEquals(x.tree, x.session.retainedTree(x.game.id))
        }
    }

    @Test fun staleAccountAndPlayCallbacksCannotStageOrPublishEvenBeforeNextTick() {
        Fixture().use { x ->
            x.connect(); val read = x.wire.reads.single().second; val transfer = x.transfer()
            x.actor = f.b
            read(transfer); x.drain()
            assertNull(x.cache.saved); assertTrue(x.cache.chunks.isEmpty()); assertNull(x.session.retainedTree(x.game.id))
        }
        Fixture().use { x ->
            x.connect(); val read = x.wire.reads.single().second; val transfer = x.transfer()
            val next = x.head().let { it.copy(summary = it.summary.copy(playId = f.id(999), revision = "2"),
                revision = "2", play = it.play!!.copy(id = f.id(999))) }
            x.message(next); read(transfer); x.drain()
            assertNull(x.cache.saved); assertTrue(x.cache.chunks.isEmpty())
        }
    }

    @Test fun freshOfflineSessionLoadsCompleteAcceptedRecordsWithoutRecreatingCommands() {
        Fixture().use { x ->
            x.token = null
            x.cache.seed(TreeSync.generation(x.tree), 10)
            x.session.watch(listOf(x.game)); x.drain()
            assertEquals(x.tree, x.session.retainedTree(x.game.id))
            assertFalse(x.session.ready(x.game.id)); assertTrue(x.wire.frames.isEmpty())
            var review: DownloadedHistoryState? = null
            assertTrue(x.session.review(x.game.id, x.tree.initial.root.id) { review = it }); x.drain()
            assertEquals(10, review!!.revision); assertEquals(x.tree.initial.board, review!!.state!!.board)
            assertTrue(x.wire.frames.isEmpty())
        }
    }

    @Test fun rootCurrentHeadIsNotEvidenceOfAnEmptyRetainedTreeEvenWithoutDiskMounting() {
        Fixture(persistent = false).use { x ->
            val future = f.tap(x.tree)
            x.tree = assertNotNull(future.activate(future.initial.root.id).tree)
            x.revision = 5
            x.connect(required = true)
            assertNull(x.session.acceptedPlay(x.game)); assertFalse(x.session.ready(x.game.id))
            x.wire.baselines.single()(GameplayBaseline(GameplaySnapshot(x.head(), emptyList(),
                TreeSync.records(TreeSync.generation(x.tree)).toList()), emptyList()))
            x.drain()
            assertTrue(x.session.ready(x.game.id)); assertEquals(x.tree, x.session.retainedTree(x.game.id))
            assertNotNull(x.session.retainedTree(x.game.id)!!.stateAt(future.currentNodeId))
        }
    }

    @Test fun metadataOnlyRevisionAdvancesSameCursorWithoutDroppingTreeBeforeNextGameplayDelta() {
        Fixture().use { x ->
            x.connect(); x.publish()
            val before = x.tree
            val base = x.revision++
            val metadata = GameRevision(x.game.id, base.toString(), x.revision.toString(), f.id(900), f.b,
                GameScope(x.game.id, playId = before.initial.id), x.game.copy(revision = x.revision.toString()))
            x.message(metadata)
            assertEquals(before, x.session.retainedTree(x.game.id))
            assertEquals(x.revision, x.cache.saved!!.version.revision)
            assertTrue(x.session.gameplay(x.game, f.a, PlayAction.AdjustLife(f.a, "1")))
            val command = x.commands().last()
            x.message(x.execute(command))
            assertEquals(x.tree, x.session.retainedTree(x.game.id))
            assertEquals(x.revision, x.cache.saved!!.version.revision)
        }
    }

    @Test fun acceptedInspectionObserverWaitsForAcceptedTreeAndNeverUsesReceiptOrWithdrawnPredictionAsDisclosure() {
        Fixture().use { x ->
            x.connect(); x.publish()
            val events = mutableListOf<InspectionEvent>()
            val action = PlayAction.InspectFace(f.id(9), f.id(11), f.id(10))
            val local = ManualActions()
            local.cards(x.tree.initial, action.cardId, listOf(action.cardId)); local.inspect(x.tree.initial, f.a)
            assertTrue(x.commands().isEmpty(), "own-area local inspection is not logged")
            val subscription = assertNotNull(x.session.inspectFace(x.game, f.a, action, "inspector-one", { true }, events::add))
            val command = x.commands().single()
            assertEquals(command.requestId, subscription.request.requestId)
            assertEquals(action, subscription.request.action); assertTrue(events.isEmpty())
            x.message(GameReceipt(command.requestId, command.scope, "1")); assertTrue(events.isEmpty())
            x.session.withdrawGameplayPrediction(x.game.id, command.requestId)
            val update = x.execute(command)
            val result = GameResult(command.requestId, command.scope, "accepted", ingressOrdinal = "1",
                commitRevision = update.resultRevision, mutation = x.tree.nodes.getValue(x.tree.currentNodeId))
            x.message(result); assertTrue(events.isEmpty(), "result-before-state is not a presented disclosure")
            x.message(update)
            val accepted = events.single() as InspectionEvent.Accepted
            assertEquals(subscription.request, accepted.request)
            assertEquals(GameplayDisclosure(action.cardId, action.faceId, action.containerId, listOf(f.a)), accepted.disclosure)
            assertEquals(x.session.acceptedPlay(x.game), accepted.state)
            assertEquals(update.resultRevision, accepted.throughRevision)
            x.message(update); x.message(result); assertEquals(1, events.size)
            local.inspected(accepted.state, f.a, accepted.disclosure)
            assertEquals(listOf(action.faceId), local.inspection!!.faceIds)
        }
    }

    @Test fun inspectionRevisionBeforeResultNotifiesOnceWhileFinalRejectionAndNoopAreNotAcceptedEvents() {
        Fixture().use { x ->
            x.connect(); x.publish()
            val events = mutableListOf<InspectionEvent>()
            val action = PlayAction.InspectFace(f.id(9), f.id(11), f.id(10))
            assertNotNull(x.session.inspectFace(x.game, f.a, action, "revision-first", { true }, events::add))
            val command = x.commands().last(); val update = x.execute(command)
            x.message(update)
            assertTrue(events.single() is InspectionEvent.Accepted)
            val result = GameResult(command.requestId, command.scope, "accepted", ingressOrdinal = "1", commitRevision = update.resultRevision)
            x.message(result); x.message(result); assertEquals(1, events.size)
            for (outcome in listOf("rejected", "noOp")) {
                val subscription = assertNotNull(x.session.inspectFace(x.game, f.a, action, "final-$outcome", { true }, events::add))
                val original = x.commands().last()
                val refused = GameResult(original.requestId, original.scope, outcome, if (outcome == "rejected") "missingReference" else null,
                    ingressOrdinal = "2")
                x.message(refused); x.message(refused)
                val event = events.last() as InspectionEvent.NotAccepted
                assertEquals(subscription.request, event.request); assertEquals(refused, event.result)
            }
            assertEquals(3, events.size)
        }
    }

    @Test fun closedInspectorExpiredLifetimeAccountAndIncarnationNeverReopenFromLateAcceptedInspection() {
        for (closedBy in listOf("subscription", "lifetime", "account", "play")) Fixture().use { x ->
            x.connect(); x.publish()
            val events = mutableListOf<InspectionEvent>()
            var current = true
            val subscription = assertNotNull(x.session.inspectFace(x.game, f.a,
                PlayAction.InspectFace(f.id(9), f.id(11), f.id(10)), "closed-$closedBy", { current }, events::add))
            val command = x.commands().last(); val update = x.execute(command)
            when (closedBy) {
                "subscription" -> subscription.close()
                "lifetime" -> current = false
                "account" -> x.actor = f.b
                "play" -> x.message(x.head().let { it.copy(summary = it.summary.copy(playId = f.id(999), revision = "3"),
                    revision = "3", play = it.play!!.copy(id = f.id(999))) })
            }
            x.message(update)
            x.message(GameResult(command.requestId, command.scope, "accepted", ingressOrdinal = "1", commitRevision = update.resultRevision))
            assertTrue(events.isEmpty(), closedBy)
        }
    }

    @Test fun acceptedInspectionCrossedByBaselineNeverRetargetsMovedRegionAndOfflineInvocationCreatesNothing() {
        Fixture().use { x ->
            x.connect(); x.publish()
            val events = mutableListOf<InspectionEvent>()
            val subscription = assertNotNull(x.session.inspectFace(x.game, f.a,
                PlayAction.InspectFace(f.id(9), f.id(11), f.id(10)), "bound-region", { true }, events::add))
            val command = x.commands().last(); val disclosure = x.execute(command)
            val move = GameCommand(f.id(901), command.scope, GameAction.Play(PlayAction.MoveCard(f.id(9), MoveSource(f.id(10)),
                MoveDestination(f.id(13), MoveInsertion.TOP))), command.streamId, command.generation)
            x.execute(move)
            x.message(x.head()) // lose both deltas; original acceptance is recovered from the same full tree.
            val result = GameResult(command.requestId, command.scope, "accepted", ingressOrdinal = "1", commitRevision = disclosure.resultRevision)
            x.publish(listOf(GameRequestStatus(command.requestId, "final", result)))
            assertTrue(events.isEmpty()); assertFalse(subscription.active)
            assertEquals(f.id(13), x.session.acceptedPlay(x.game)!!.board.cards.single { it.id == f.id(9) }.containerId)
            x.wire.listener.disconnected(); x.drain(); val sent = x.commands().size
            assertNull(x.session.inspectFace(x.game, f.a, subscription.request.action, "offline", { true }, events::add))
            assertEquals(sent, x.commands().size)
        }
    }

    @Test fun authorizedCapturedActivityBoundaryReviewsAcceptedTreeWithDeletedParentFallbackNotSharedReplacement() {
        Fixture().use { x ->
            x.connect(); x.publish()
            assertTrue(x.session.gameplay(x.game, f.a, PlayAction.AdjustLife(f.a, "1")))
            val first = x.commands().last(); x.message(x.execute(first))
            val boundary = x.tree.currentNodeId
            val group = x.tree.stateAt(boundary)!!.currentGroup!!.id
            val remote = GameCommand(f.id(950), first.scope, GameAction.Play(PlayAction.SetTapped(f.id(12), true)), first.streamId, "1")
            x.message(x.execute(remote, f.b))
            val undo = first.copy(requestId = f.id(951), action = GameAction.Undo)
            x.message(x.execute(undo))
            val target = CapturedHistoryTarget(f.a, x.game.id, x.tree.initial.id, group, boundary)
            val current = x.tree.currentNodeId; val frames = x.wire.frames.size
            var reviewed: CapturedHistoryReview? = null
            assertTrue(x.session.reviewTarget(target, { true }) { reviewed = it }); x.drain()
            assertEquals(target, reviewed!!.target)
            assertTrue(reviewed!!.view.reviewing); assertEquals(x.tree.initial.root.id, reviewed!!.view.cursor)
            assertEquals(x.tree.contextVersion(x.tree.initial.root.id), reviewed!!.view.contextVersion)
            assertEquals(x.tree.initial.board, reviewed!!.view.state!!.board)
            assertEquals(current, x.session.retainedTree(x.game.id)!!.currentNodeId)
            assertNotEquals(current, reviewed!!.view.cursor); assertEquals(frames, x.wire.frames.size)
        }
    }

    @Test fun capturedActivityTargetRefusesStaleAccountPlayAccessAndRouteLifetimeWithoutCurrentFallback() {
        Fixture().use { x ->
            x.connect(); x.publish()
            val target = CapturedHistoryTarget(f.a, x.game.id, x.tree.initial.id, f.id(960), x.tree.initial.root.id)
            val received = mutableListOf<CapturedHistoryReview>()
            assertFalse(x.session.reviewTarget(target.copy(accountId = f.b), { true }, received::add))
            assertFalse(x.session.reviewTarget(target.copy(playId = f.id(961)), { true }, received::add))
            assertFalse(x.session.reviewTarget(target.copy(gameId = f.id(962)), { true }, received::add))
            assertFalse(x.session.reviewTarget(target, { false }, received::add), "expired authorized route/access guard")
            val frames = x.wire.frames.size
            val entered = java.util.concurrent.CountDownLatch(1)
            val release = java.util.concurrent.CountDownLatch(1)
            x.worker.execute { entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)) }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            var current = true
            assertTrue(x.session.reviewTarget(target, { current }, received::add))
            current = false; release.countDown(); x.drain()
            assertTrue(received.isEmpty()); assertNull(x.cache.reviewCursor())
            assertTrue(x.session.reviewTarget(target.copy(boundaryNodeId = f.id(963)), { true }, received::add)); x.drain()
            assertNull(received.single().view.state); assertNull(received.single().view.cursor)
            assertNotNull(received.single().view.error); assertEquals(frames, x.wire.frames.size)
        }
    }

    @Test fun normalLobbyControllerExposesAcceptedInspectionAndExactPersonalHistoryCallbacksWithRouteFences() {
        val root = f.root()
        val game = GameSummary(root.initial.gameId, "Consumers", "2026-10-23T00:00:00Z", AccountIdentity(f.a, "A"),
            AccountIdentity(f.b, "B"), root.initial.preparationRound, f.id(970), InvitationState.ACCEPTED,
            lifecycle = GameLifecycle.ACTIVE, revision = "1", playId = root.initial.id)
        val posts = LinkedBlockingQueue<() -> Unit>()
        val worker = Executors.newSingleThreadExecutor()
        val wire = Wire()
        val api = object : Lobbies {
            override suspend fun search(token: String, query: String) = UserSearch(emptyList())
            override suspend fun contacts(token: String, after: String?) = ContactList(emptyList())
            override suspend fun add(token: String, input: AddContact) = game.recipient
            override suspend fun create(token: String, input: CreateInvitation) = game
            override suspend fun games(token: String, invitations: Boolean, after: String?) = GameList(if (invitations) emptyList() else listOf(game))
            override suspend fun head(token: String, id: String) = game
            override fun close() = Unit
        }
        val lobby = LobbyController(api, { "A".repeat(43) }, worker, { posts.add(it) }, gameConnection = wire, accountId = { f.a })
        fun drain() { repeat(3) { worker.submit {}.get(10, TimeUnit.SECONDS); while (true) (posts.poll() ?: break).invoke() } }
        fun message(message: GameServerMessage) { wire.listener.message(message); drain() }
        try {
            lobby.open(game.id); drain(); wire.listener.opened(); drain(); message(GameWelcome())
            val stream = wire.frames.filterIsInstance<OpenGameStream>().last()
            message(GameStreamReady(game.id, stream.streamId, "1")); message(GameHead(game.id, "1", game, play = root.initial))
            val events = mutableListOf<InspectionEvent>()
            val action = PlayAction.InspectFace(f.id(9), f.id(11), f.id(10))
            assertNotNull(lobby.inspectFace(action, "mounted-toolkit", { true }, events::add))
            assertTrue(events.isEmpty())
            val command = wire.frames.filterIsInstance<GameCommand>().last()
            val accepted = assertNotNull(HistoryContinuation.change(root, f.a, command).tree)
            message(GameRevision(game.id, "1", "2", command.requestId, f.a, command.scope, game.copy(revision = "2"),
                treeDelta = TreeSync.delta(root, accepted)))
            assertTrue(events.single() is InspectionEvent.Accepted)
            val target = CapturedHistoryTarget(f.a, game.id, root.initial.id, null, root.initial.root.id)
            var review: CapturedHistoryReview? = null
            assertTrue(lobby.reviewHistoryTarget(target, { true }) { review = it })
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (review == null) { posts.poll(100, TimeUnit.MILLISECONDS)?.invoke(); assertTrue(System.nanoTime() < end) }
            assertEquals(root.initial.board, review!!.view.state!!.board)
            assertEquals(accepted.currentNodeId, lobby.state.acceptedPlay!!.currentNodeId)
            assertNotNull(lobby.inspectFace(action, "closed-toolkit", { true }, events::add))
            val late = wire.frames.filterIsInstance<GameCommand>().last()
            lobby.back(); drain()
            val next = assertNotNull(HistoryContinuation.change(accepted, f.a, late).tree)
            message(GameRevision(game.id, "2", "3", late.requestId, f.a, late.scope, game.copy(revision = "3"),
                treeDelta = TreeSync.delta(accepted, next)))
            assertEquals(1, events.size, "closed route must not reopen an accepted inspector")
            assertFalse(lobby.reviewHistoryTarget(target, { true }) { fail("closed route") })
        } finally {
            lobby.close(); worker.shutdown(); assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS))
        }
    }
}
