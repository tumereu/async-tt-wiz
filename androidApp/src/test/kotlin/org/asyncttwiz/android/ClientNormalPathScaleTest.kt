package org.asyncttwiz.android

import org.asyncttwiz.fixtures.ScaleGames as F
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.concurrent.Executor
import kotlin.test.*

/** Normal-task operation guards are primary; medians catch accidentally expensive constant work.
 * Both sizes use the real reducer fixture, including annotations, forks and selective controls. */
class ClientNormalPathScaleTest {
    companion object {
        private val small by lazy { F.build(1000) }
        private val large by lazy { F.build(10000) }
    }
    private fun median(operation: () -> Unit): Double {
        repeat(12) { operation() }
        return List(31) {
            val start = System.nanoTime()
            repeat(8) { operation() }
            (System.nanoTime() - start) / 8.0 / 1_000_000
        }.sorted()[15]
    }
    private fun scale(label: String, budgetMs: Double = 50.0, setup: (GameplayTree) -> (() -> Unit)) {
        // Warm both sizes before either measurement, avoiding cold class loading in the ratio.
        val a = setup(small); val b = setup(large)
        repeat(12) { a(); b() }
        val t1 = median(a); val t10 = median(b)
        val ratio = t10 / t1
        println("$label: 1k=${t1}ms 10k=${t10}ms ratio=$ratio")
        assertTrue(ratio < 3.0, "$label ratio $ratio >= 3")
        assertTrue(t10 < budgetMs, "$label 10k median $t10 >= $budgetMs ms")
    }
    private fun index(tree: GameplayTree, previous: HistoryTree? = null) = HistoryTree(tree.initial.root.id,
        emptyList(), tree.currentNodeId, tree.currentNodeId, containers = tree.initial.board.containers,
        source = tree, previous = previous)

    @Test fun ingestionPreparesGeometryAndCatchUpNeverReplaysThePrefix() {
        val tree = large.materializedCopy()
        val navigation = HistoryPlayback(index(tree))
        repeat(5) { navigation.previous() }
        val seen = navigation.position.cursorId
        val distance = tree.endpointIndex().positions.getValue(tree.currentNodeId).depth -
            tree.endpointIndex().positions.getValue(seen).depth
        val history = TableHistoryController(Executor { it.run() }, { it() }, { node, complete ->
            val worker = tree.materializedCopy()
            val cursor = node ?: tree.currentNodeId
            val state = worker.prepareReview(cursor)
            tree.receivePreparedReview(worker)
            complete(DownloadedHistoryState(1, cursor, node != null, state))
            true
        }, { _, _, _ -> false })
        tree.work.reset()
        history.openFrom(seen) { _, _ -> }
        history.update(tree, "head", viewer = F.a)
        history.displayPosition()
        val player = history.playback!!
        repeat(5) {
            val events = HistoryTimelineLayout.events(player.tree, tree.currentNodeId) as HistoryTimelineLayout.IndexedHistoryEvents
            val selected = events.positionOf(player.position.cursorId)
            HistoryTimelineLayout.visible(events.size, selected, 480f, 24f).forEach { assertNotNull(events[it]) }
            assertTrue(events.projectedEvents <= 23)
            player.next(true)
            history.displayPosition()
            player.finishAnimation(player.position.cursorId)
        }
        assertEquals(HistoryMode.LIVE, player.position.mode)
        assertEquals(tree.currentNodeId, history.view?.cursor)
        assertEquals(0, tree.work.rootMaterializations)
        assertEquals(0, tree.work.fullHistoryScans)
        assertTrue(tree.work.replayedNodes + tree.work.unappliedNodes <= distance * 2)
        assertTrue(tree.work.ancestrySteps <= distance * 4)
        assertTrue(player.tree.navigationReads < 500)
        assertTrue(player.tree.hasPresentationIndex, "Fixed tree geometry is prepared during ingestion, not dialog open")
        assertTrue(player.tree.index.layout.chips.values.all { it.presentation == null }, "Text/glyph projection stays lazy")
        history.close()

        // After ingestion, showing the already-materialized initial endpoint is O(1).
        tree.work.reset()
        val firstOpen = HistoryPlayback(HistoryTree(tree.initial.root.id, emptyList(), tree.currentNodeId,
            "first", containers = tree.initial.board.containers, source = tree, lazyPresentation = true))
        firstOpen.openFrom(null)
        assertEquals(tree.initial, tree.prepareReview(firstOpen.position.cursorId))
        assertEquals(0, tree.work.replayedNodes + tree.work.unappliedNodes + tree.work.ancestrySteps)
        assertEquals(0, tree.work.fullHistoryScans + tree.work.rootMaterializations)
        assertTrue(firstOpen.tree.hasPresentationIndex)
        assertTrue(firstOpen.tree.navigationReads < 20)
        firstOpen.next(true)
        assertNotNull(tree.prepareReview(firstOpen.position.cursorId))
        assertTrue(tree.work.replayedNodes < 24)
        assertEquals(0, tree.work.rootMaterializations)
    }

    @Test fun item1AppendTextsIndexAndPublicationVisitOnlyTheNewEdge() = scale("1 history append") { tree ->
        val old = index(tree)
        val texts = historyTexts(tree, F.a, emptyMap())
        val next = F.play(tree, PlayAction.AdjustLife(F.a, "1"), 800001)
        val playback = HistoryPlayback(old)
        val operation: () -> Unit = {
            next.work.reset()
            val projected = index(next.materializedCopy(), old)
            val work = HistoryTextWork()
            projected.texts = historyTexts(next, F.a, emptyMap(), tree, texts, projected.index.affected, work)
            playback.receive(projected)
            assertEquals(1, projected.indexedNodes)
            assertEquals(1, work.projectedEvents)
            assertEquals(0, work.scannedEvents)
            assertEquals(0, next.work.fullHistoryScans)
            assertEquals(0, next.work.rootMaterializations)
            assertTrue(next.work.replayedNodes + next.work.unappliedNodes <= 1)
            assertSame(texts[tree.currentNodeId], projected.texts[tree.currentNodeId])
        }
        operation
    }

    @Test fun coalescedIndexesKeepExplicitRemovalsAndPredictionConfirmationLineage() {
        val initial = index(large)
        val first = F.play(large, PlayAction.AdjustLife(F.a, "1"), 800005)
        val firstIndex = index(first, initial)
        val playback = HistoryPlayback(firstIndex)
        playback.scrub(first.currentNodeId)
        val second = F.play(first, PlayAction.AdjustLife(F.a, "1"), 800006)
        val unpublished = index(second.materializedCopy(), firstIndex)
        // Undo is per invocation, not per presentation group: these are two separate requests.
        val undoneSecond = F.change(second, F.command(second, GameAction.Undo, 800007))
        val undone = F.change(undoneSecond, F.command(undoneSecond, GameAction.Undo, 800008))
        val next = index(undone.materializedCopy(), unpublished)
        playback.receive(next)
        assertEquals(large.currentNodeId, playback.position.cursorId)
        assertEquals(first.nodes.getValue(first.currentNodeId).parentId, next.removedParents[first.currentNodeId])
        assertEquals(2, undone.changedNodeIdsSince(large)?.size)

        // Accepted and predicted siblings share the base, not necessarily the immediate edit token.
        val accepted = TreeSync.apply(TreeSync.generation(large), TreeSync.delta(large, first)).tree
        assertEquals(setOf(first.currentNodeId), accepted.materializedCopy().changedNodeIdsSince(first.materializedCopy()))
        val confirmed = index(accepted, firstIndex)
        assertEquals(1, confirmed.indexedNodes)
        assertEquals(firstIndex.path(first.currentNodeId), confirmed.path(accepted.currentNodeId))
    }

    @Test fun indexedChatDeduplicatesReordersAndTracksCoalescedUpserts() {
        val game = large.initial.gameId; val play = large.initial.id
        fun message(n: Int) = ChatMessage(F.id(950000 + n), game, play, F.b, "$n", n.toString(), "date")
        val old = IndexedChatMessages().upsert(listOf(message(3), message(1)))
        val personal = GameChat()
        assertEquals(2, personal.presentation(game, F.a, old, emptyList()).unread)
        val edited = old.upsert(listOf(message(2))).upsert(listOf(message(3), message(1).copy(text = "edited")))
        assertEquals(listOf("1", "2", "3"), edited.map { it.revision })
        assertEquals("edited", edited.byId(message(1).id)?.text)
        assertEquals(3, personal.presentation(game, F.a, edited, emptyList()).unread)
        personal.presented(edited, setOf(message(2).id))
        personal.presented(edited, setOf(message(2).id))
        assertEquals(2, personal.presentation(game, F.a, edited, emptyList()).unread)
        assertNull(IndexedChatMessages().changedSince(edited))
    }

    private class GuardMap(private val source: Map<String, GameplayMutation>) : Map<String, GameplayMutation> by source {
        var scans = 0
        override val values get() = source.values.also { scans++ }
        override val entries get() = source.entries.also { scans++ }
        override fun equals(other: Any?): Boolean { scans++; return source == other }
    }
    @Test fun playbackAutoPauseOnlyReadsTheAdvancingGroupAtTenThousandActions() {
        val guarded = GuardMap(large.nodes)
        val source = large.copy(nodes = guarded)
        val history = index(source)
        val player = HistoryPlayback(history)
        player.previous()
        val from = player.position.cursorId
        val board = source.prepareReview(from)!!.board
        guarded.scans = 0
        source.work.reset()
        player.play()
        player.next(true, F.a, board = board)
        player.pauseAfterAnimation()
        assertEquals(0, guarded.scans)
        assertEquals(0, source.work.replayedNodes + source.work.unappliedNodes + source.work.fullHistoryScans)
    }
    @Test fun item2PresentedWithoutSuggestionNeverEnumeratesNodes() = scale("2 presented") { tree ->
        val guarded = GuardMap(tree.nodes)
        val controller = TableHistoryController(Executor { it.run() }, { it() }, { _, _ -> false }, { _, _, _ -> false })
        controller.update(tree.copy(nodes = guarded), "1")
        guarded.scans = 0
        val state = tree.preparedState(tree.currentNodeId)!!
        val operation: () -> Unit = {
            repeat(64) { controller.presented(state, state, true) }
            assertEquals(0, guarded.scans)
        }
        operation
    }

    @Test fun item3TransportMembershipAndTimelineOnlyTouchNeighboursAndWindow() = scale("3 transport/window") { tree ->
        val index = index(tree)
        val playback = HistoryPlayback(index)
        val operation: () -> Unit = {
            val before = index.navigationReads
            playback.continuationApplied(); playback.previous(); playback.next()
            playback.play(); playback.next(automatic = true)
            assertTrue(index.isAncestor(playback.position.cursorId, tree.currentNodeId))
            val events = HistoryTimelineLayout.events(index, tree.currentNodeId) as HistoryTimelineLayout.IndexedHistoryEvents
            val selected = events.positionOf(playback.position.cursorId)
            val visible = HistoryTimelineLayout.visible(events.size, selected, 480f, 24f)
            visible.forEach { assertNotNull(events[it].description) }
            assertTrue(events.projectedEvents <= 23)
            assertTrue(index.navigationReads - before < 100)
            assertEquals(index.path(tree.currentNodeId).size, events.size)
        }
        operation
    }

    @Test fun item4PingRenderingAndCaptureReuseActiveIndex() = scale("4 active ping") { tree ->
        val pinged = F.play(tree, PlayAction.Ping(EventReference(tree.initial.id, tree.currentNodeId), F.pingTarget), 800002)
        val state = pinged.preparedState(pinged.currentNodeId)!!
        val projection = ActivePingProjection()
        val records = projection.records(state)
        assertEquals(1, records.size)
        val before = projection.projectedRecords
        val operation: () -> Unit = {
            repeat(64) {
                assertSame(records, projection.records(state))
                assertFalse(tablePingIntent(state, EventReference(state.id, state.currentNodeId), F.pingTarget, projection).add)
            }
            assertEquals(before, projection.projectedRecords)
        }
        operation
    }

    @Test fun item5ChatAppendUnreadRefreshAndVisibleLookupAreIndexed() = scale("5 chat") { tree ->
        fun message(n: Int) = ChatMessage(F.id(900000 + n), tree.initial.gameId, tree.initial.id,
            F.b, "Message $n", n.toString(), "date")
        var messages = IndexedChatMessages().upsert(List(tree.nodes.size) { message(it + 1) })
        val personal = GameChat()
        var sequence = messages.size
        val count = messages.size
        assertEquals(count, personal.presentation(tree.initial.gameId, F.a, messages, emptyList()).unread)
        val operation: () -> Unit = {
            val before = personal.inspectedMessages
            val added = message(++sequence)
            messages = messages.upsert(listOf(added))
            assertEquals(1, messages.writes)
            assertTrue(messages.nodeVisits < 128)
            assertEquals(count + 1, personal.presentation(tree.initial.gameId, F.a, messages, emptyList()).unread)
            repeat(4) { assertEquals(count + 1, personal.presentation(tree.initial.gameId, F.a, messages, emptyList()).unread) }
            val visible = presentedChatIds(messages, listOf(added.id, "pending", 1), true)
            assertEquals(setOf(added.id), visible)
            personal.presented(messages, visible)
            assertEquals(count, personal.presentation(tree.initial.gameId, F.a, messages, emptyList()).unread)
            assertTrue(personal.inspectedMessages - before <= 4)
        }
        operation
    }

    @Test fun item6EffectIdentityNeverCallsTreeEquality() = scale("6 effect identity") { tree ->
        val guarded = GuardMap(tree.nodes)
        val a = tree.copy(nodes = guarded); val b = tree.copy(nodes = guarded)
        val operation: () -> Unit = {
            repeat(64) {
                assertEquals(IdentityKey(a), IdentityKey(a))
                assertNotEquals(IdentityKey(a), IdentityKey(b))
            }
            assertEquals(0, guarded.scans)
        }
        operation
    }

    @Test fun item8SelectionReusesGraphPanIsCulledAndCommentsArePaged() = scale("8 selection/viewport/page") { tree ->
        val controller = HistoryExplorerController(Executor { it.run() }, { it() }, { _, error -> throw error })
        controller.update(tree)
        val graph = controller.state.graph!!
        val parent = tree.nodes.getValue(tree.currentNodeId).parentId
        val point = graph.positions.getValue(tree.currentNodeId)
        val state = tree.preparedState(tree.currentNodeId)!!
        val operation: () -> Unit = {
            controller.select(parent); controller.select(tree.currentNodeId)
            assertSame(graph, controller.state.graph)
            val visible = graph.visible(point.x - 120, point.y - 60, 240f, 120f)
            assertTrue(visible.size < 50)
            assertTrue(graph.spatialVisits < 300)
            val pages = CommentPages(state.annotations)
            assertTrue(pages.next().size <= 50)
            assertTrue(pages.inspected <= 50)
        }
        operation
    }

    private fun head(tree: GameplayTree): GameHead {
        val p = tree.preparedState(tree.currentNodeId)!!
        val summary = GameSummary(p.gameId, "Scale", "date", AccountIdentity(F.a, "A"), AccountIdentity(F.b, "B"),
            p.preparationRound, F.id(99), InvitationState.ACCEPTED, GameLifecycle.ACTIVE, revision = "10001", playId = p.id)
        return GameHead(p.gameId, summary.revision, summary, play = p)
    }
    @Test fun predictionAndReviewTelemetryStaysUnderTwentyMilliseconds() {
        for (action in listOf("Play", "Undo", "Redo", "HistoryReview")) scale("telemetry $action", 20.0) { fixture ->
            val tree = if (action == "Redo") F.change(fixture, F.command(fixture, GameAction.Undo, 800003)) else fixture
            val generation = TreeSync.generation(tree)
            val head = head(tree)
            val samples = mutableListOf<ActionLatency>()
            val recorder = LatencyRecorder(true, log = samples::add, record = { _, _ -> })
            val operation: () -> Unit = {
                val replica = GameplayReplica()
                assertTrue(replica.published(head, generation, head.revision, emptyList(), emptyList()))
                tree.work.reset()
                val trace = recorder.begin(tree.initial.gameId, action, tree.nodes.size + 1)!!
                if (action == "HistoryReview") {
                    val retained = replica.tree!!.tree
                    assertNotNull(retained.prepareReview(retained.nodes.getValue(retained.currentNodeId).parentId))
                } else {
                    val command = F.command(tree, when (action) {
                        "Play" -> GameAction.Play(PlayAction.AdjustLife(F.a, "1"))
                        "Undo" -> GameAction.Undo
                        else -> GameAction.Redo
                    }, 800004)
                    assertTrue(replica.predict(command, F.a))
                }
                trace.submitted(null, true); trace.finish()
                assertTrue(samples.last().predicted)
                assertEquals(tree.nodes.size + 1, samples.last().historyNodeCount)
                assertEquals(0, tree.work.fullHistoryScans)
                assertEquals(0, tree.work.rootMaterializations)
                assertTrue(tree.work.replayedNodes + tree.work.unappliedNodes <= 12)
                recorder.drawn()
            }
            operation
        }
    }
}
