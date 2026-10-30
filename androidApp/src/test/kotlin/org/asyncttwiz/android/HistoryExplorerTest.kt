package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class HistoryExplorerTest {
    @Test fun turnLabelsUseTurnPlayerNotAuthorAndOnlyStartOfEachTurnIncludingForks() {
        val initial = GameplayTree(root())
        val first = play(initial, 600)
        val next = play(first, 601, a, PlayAction.EndTurn)
        val later = play(next, 602, a)
        val fork = assertNotNull(later.play(b, command(603, PlayAction.EndTurn).copy(
            continuation = Continuation(first.currentNodeId, true))).tree)
        val graph = HistoryGraphLayout.build(fork)
        val tags = graph.ranges.mapNotNull { range -> range.turnStart?.let { range.nodeIds.first() to it } }.toMap()
        assertEquals(mapOf(root().root.id to HistoryTurnStart("1", a), next.currentNodeId to HistoryTurnStart("2", b),
            fork.currentNodeId to HistoryTurnStart("2", b)), tags)
        assertEquals("T2", tags.getValue(next.currentNodeId).label)
        assertEquals(a, graph.ranges.single { next.currentNodeId in it.nodeIds }.author)
        assertFalse(first.currentNodeId in tags)
        assertFalse(later.currentNodeId in tags)
    }

    @Test fun pinchZoomAnchorsCentroidAndPanningAndVisibleQueriesStayWindowBounded() {
        val nodes = (1..10_000).map { n -> GameplayMutation(id(1000 + n), if (n == 1) root().root.id else id(999 + n),
            id(6), id(20_000 + n), a, null, PlayAction.EndTurn, GameplayTransition()) }
        val tree = GameplayTree(root(), nodes = nodes.associateBy { it.id }, currentNodeId = nodes.last().id)
        val graph = HistoryGraphLayout.build(tree, membership = nodes.associate { it.id to it.id })
        val center = HistoryPoint(200f, 100f)
        val viewport = HistoryTreeViewport().transform(center, HistoryPoint(0f, 0f), 2f, graph, 400f, 200f)
        assertEquals(center, viewport.screen(center))
        assertEquals(2f, viewport.zoom)
        val moved = viewport.transform(center, HistoryPoint(-100f, -20f), 1f, graph, 400f, 200f)
        assertEquals(HistoryPoint(100f, 80f), moved.screen(center))
        val zoomedOut = moved.transform(center, HistoryPoint(0f, 0f), .01f, graph, 400f, 200f)
        assertEquals(.5f, zoomedOut.zoom)
        assertTrue(zoomedOut.visible(graph, 400f, 200f).size < 40)
        assertTrue(graph.spatialVisits < 100, "Pan/zoom must query the spatial index, not scan history")
    }

    @Test fun nonFiniteGestureInputsLeaveViewportUnchanged() {
        val graph = HistoryGraphLayout.build(GameplayTree(root()))
        val viewport = HistoryTreeViewport(HistoryPoint(120f, 80f), 1.5f)
        val center = HistoryPoint(200f, 100f)
        val delta = HistoryPoint(20f, -10f)
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertSame(viewport, viewport.transform(center.copy(x = invalid), delta, 1f, graph, 400f, 200f))
            assertSame(viewport, viewport.transform(center.copy(y = invalid), delta, 1f, graph, 400f, 200f))
            assertSame(viewport, viewport.transform(center, delta.copy(x = invalid), 1f, graph, 400f, 200f))
            assertSame(viewport, viewport.transform(center, delta.copy(y = invalid), 1f, graph, 400f, 200f))
            assertSame(viewport, viewport.transform(center, delta, invalid, graph, 400f, 200f))
            assertSame(viewport, viewport.transform(center, delta, 1f, graph, invalid, 200f))
            assertSame(viewport, viewport.transform(center, delta, 1f, graph, 400f, invalid))
            assertSame(viewport, viewport.transform(center, delta, 1f, graph.copy(width = invalid), 400f, 200f))
            assertSame(viewport, viewport.transform(center, delta, 1f, graph.copy(height = invalid), 400f, 200f))
        }
        assertSame(viewport, viewport.transform(center, delta, 0f, graph, 400f, 200f))
        assertSame(viewport, viewport.transform(center, delta, -1f, graph, 400f, 200f))
        assertNotEquals(viewport, viewport.transform(center, delta, 1f, graph, 400f, 200f))
    }

    @Test fun diagnosticsDistinguishMissingCursorReplayFailureAndLayoutException() {
        val failures = mutableListOf<Pair<String, Throwable>>()
        val controller = HistoryExplorerController(Executor { it.run() }, { it() },
            { context, failure -> failures.add(context to failure) })
        try {
            controller.update(GameplayTree(root()))
            controller.select(id(999))
            assertTrue(failures.last().second.message!!.contains("Cannot resolve retained cursor"))
            val first = play(GameplayTree(root()), 100)
            val node = first.nodes.values.single()
            val corrupt = first.copy(nodes = mapOf(node.id to node.copy(transition = GameplayTransition())))
            controller.update(corrupt, openingNodeId = node.id)
            controller.select(node.id)
            assertTrue(failures.last().second.message!!.contains("Cannot reconstruct retained boundary"))
            // A malformed offshoot must report a topology failure, not masquerade as a missing cursor.
            val orphan = node.copy(id = id(998), parentId = id(997))
            controller.update(first.copy(nodes = first.nodes + (orphan.id to orphan)))
            controller.select(first.initial.root.id)
            assertEquals("Unavailable retained ancestry", failures.last().second.message)
            assertTrue(failures.last().first.contains("game=${root().gameId}"))
            assertTrue(failures.last().first.contains("nodes=2"))
            assertEquals("History unavailable", controller.state.error)
        } finally { controller.close() }
    }

    @Test fun missingBoundaryInNewTreeShowsUnavailableWithoutThrowingOrActivating() {
        val queue = Queue()
        val controller = HistoryExplorerController(queue, { it() })
        try {
            controller.update(GameplayTree(root())); queue.drain()
            controller.select(id(999)); queue.drain()
            assertFalse(controller.state.busy)
            assertEquals("History unavailable", controller.state.error)
            controller.select(root().root.id); queue.drain()
            assertNull(controller.state.error)
            assertNotNull(controller.state.reviewed)
        } finally { controller.close() }
    }
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = id(7)
    private val b = id(8)
    private fun root() = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
        BoardState(listOf(PlayPlayer(a), PlayPlayer(b)), listOf(PlayCard(id(9), "hidden", a, id(10), id(11),
            placement = PlayPlacement(BoardPosition(0, 0), id(40)))),
            listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, a, listOf(id(9)))), TurnState(a)),
        emptyList(), emptyList(), InitialStarter(StarterChoice(a), a))
    private fun command(n: Int, action: PlayAction = PlayAction.AdjustLife(a, "1")) =
        GameCommand(id(n), GameScope(id(1), playId = id(2)), GameAction.Play(action), id(13), "1")
    private fun play(tree: GameplayTree, n: Int, actor: String = a, action: PlayAction = PlayAction.AdjustLife(a, "1")) =
        assertNotNull(tree.play(actor, command(n, action)).tree)
    private class Queue : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }

    @Test fun simpleAlternativeFilteringStillPreservesRevealsCommentsMixedAuthorsAndExactReview() {
        val prefix = play(GameplayTree(root()), 300)
        val future = play(prefix, 304)
        val alternative = assertNotNull(future.play(b, command(301).copy(
            continuation = Continuation(prefix.currentNodeId, true))).tree)
        fun inactive(tree: GameplayTree) = assertNotNull(tree.play(a, command(305).copy(
            continuation = Continuation(prefix.currentNodeId, true))).tree)
        val controller = HistoryExplorerController(Executor { it.run() }, { it() })
        try {
            val tree = inactive(alternative)
            controller.update(tree, showHidden = false)
            assertFalse(alternative.currentNodeId in controller.state.graph!!.positions)
            assertTrue(tree.currentNodeId in controller.state.graph!!.positions)
            assertTrue(prefix.currentNodeId in controller.state.graph!!.positions)
            controller.select(alternative.currentNodeId)
            assertEquals(alternative.stateAt(alternative.currentNodeId), controller.state.reviewed)
            assertTrue(alternative.currentNodeId in controller.state.graph!!.positions)
            controller.update(tree, openingNodeId = tree.currentNodeId, showHidden = true)
            assertTrue(alternative.currentNodeId in controller.state.graph!!.positions)
            val prefixNode = tree.nodes.getValue(prefix.currentNodeId)
            controller.update(tree.copy(nodes = tree.nodes + (prefixNode.id to prefixNode.copy(revealsInformation = true))), showHidden = false)
            assertFalse(alternative.currentNodeId in controller.state.graph!!.positions, "Prefix exposure does not expose the suffix")
            val node = alternative.nodes.getValue(alternative.currentNodeId)
            val exposed = alternative.copy(nodes = alternative.nodes + (node.id to node.copy(revealsInformation = true)))
            val commented = play(alternative, 302, b, PlayAction.EventComment(EventReference(root().id, prefix.currentNodeId), "keep visible"))
            val mixed = play(alternative, 303, a)
            for (retained in listOf(exposed, commented, mixed)) {
                val current = inactive(retained)
                controller.update(current, openingNodeId = current.currentNodeId, showHidden = false)
                assertNull(controller.state.error)
                assertTrue(retained.currentNodeId in controller.state.graph!!.positions)
                assertEquals(retained.nodes.size + 1, current.nodes.size, "Filtering never removes retained data")
            }
        } finally { controller.close() }
    }

    @Test fun compactGroupsWithRealForkJunctionAndInternalCurrent() {
        val first = play(GameplayTree(root()), 100)
        val second = play(first, 101)
        val third = play(second, 102)
        // A genuine interruption, sharing the first node, not a copied linear prefix.
        val branch = assertNotNull(third.play(b, command(103).copy(
            continuation = Continuation(first.currentNodeId, true))).tree)
        val internal = branch.copy(currentNodeId = second.currentNodeId, currentBranchId = second.currentBranchId)
        val groups = internal.nodes.keys.associateWith { "same-effective-group" }
        val graph = HistoryGraphLayout.build(internal, groups)
        assertEquals(3, graph.ranges.size) // root, original chip (never split), interruption
        val prefix = graph.ranges.single { first.currentNodeId in it.nodeIds }
        val suffixes = graph.ranges.filter { it.parentRangeId == prefix.id }
        assertEquals(1, suffixes.size)
        assertEquals(branch.currentNodeId, suffixes.single().targetId)
        assertNotEquals(prefix.position.y, suffixes.single().position.y)
        assertEquals(graph.positions[second.currentNodeId], graph.positions[third.currentNodeId])
        val suffix = graph.ranges.single { second.currentNodeId in it.nodeIds }
        val parent = graph.ranges.single { it.id == suffix.parentRangeId }
        val interior = graph.positions.getValue(second.currentNodeId)
        assertEquals(parent.position.x + 24f, interior.x)
        assertEquals(suffix.position.y, interior.y) // Events stay on parallel lanes after the fork.
        assertEquals(suffix.position, interior)
        assertEquals(setOf(root().root.id, first.currentNodeId, second.currentNodeId), graph.activeNodeIds)
        assertEquals(internal.nodes.size + 1, graph.ranges.sumOf { it.nodeIds.size })
    }

    @Test fun renderedFixtureHasTenEventsThreeActiveGroupsAndARealRetainedRevealFork() {
        val fixture = TableStatesFixture
        val tree = fixture.history()
        val playback = fixture.playback(tree)
        val events = HistoryTimelineLayout.events(playback.tree, tree.currentNodeId)
        assertEquals(10, tree.nodes.size)
        assertEquals(1, tree.branches.size)
        assertEquals(3, events.drop(1).map { it.groupId }.distinct().size)
        assertEquals(1, events.sumOf { it.alternatives })
        val graph = HistoryGraphLayout.build(tree)
        for (event in events.drop(1)) {
            assertEquals(event.groupId, graph.ranges.single { event.id in it.nodeIds }.groupId)
        }
        for (range in graph.ranges.drop(1)) {
            val last = tree.nodes.getValue(range.targetId)
            assertEquals(last.author, range.author)
            assertEquals(last.grouping!!.icon, range.icon)
        }
        val reveal = graph.ranges.single { tree.nodes[it.targetId]?.action is PlayAction.EventComment }
        assertEquals(HistoryIcon.CHAT_BUBBLE, reveal.icon)
        assertEquals(PlayAction.EventComment::class, tree.nodes.getValue(reveal.targetId).action::class)
        assertTrue(tree.stateAt(reveal.targetId)!!.board.contentsDisplays.isNotEmpty())
    }

    @Test fun groupingUsesSharedProcessorAndLastEventForGlyphAndTarget() {
        val tap = play(GameplayTree(root()), 100, action = PlayAction.SetTapped(id(9), true))
        val life = play(tap, 101)
        val graph = HistoryGraphLayout.build(life)
        assertEquals(2, graph.ranges.size)
        assertEquals(HistoryIcon.FAVORITE, graph.ranges.last().icon)
        assertEquals(life.currentNodeId, graph.ranges.last().representativeId)
        val more = play(life, 102)
        val updated = HistoryGraphLayout.build(more)
        assertEquals(more.currentNodeId, updated.ranges.last().representativeId)
        assertEquals(more.currentNodeId, updated.ranges.last().targetId)
        assertEquals(a, updated.ranges.last().author)
        assertEquals(HistoryIcon.FLAG, updated.ranges.first().icon)
        assertNull(updated.ranges.first().representativeId)
    }

    @Test fun localExactSelectionSurvivesGroupGrowthAndReopeningUsesReviewCursor() {
        val queue = Queue()
        val controller = HistoryExplorerController(queue, { it() })
        val tap = play(GameplayTree(root()), 100, action = PlayAction.SetTapped(id(9), true))
        controller.update(tap, openingNodeId = root().root.id)
        queue.drain()
        assertEquals(root().root.id, controller.state.selectedNodeId)
        controller.select(tap.currentNodeId); queue.drain()
        val grown = play(tap, 101)
        controller.update(grown); queue.drain()
        assertEquals(tap.currentNodeId, controller.state.selectedNodeId)
        assertEquals(tap.currentNodeId, controller.state.reviewed!!.currentNodeId)
        assertEquals(HistoryIcon.FAVORITE, controller.state.graph!!.ranges.last().icon)
        controller.update(grown, openingNodeId = root().root.id); queue.drain()
        assertEquals(root().root.id, controller.state.selectedNodeId)
        val graph = controller.state.graph!!
        for ((width, height) in listOf(412f to 780f, 892f to 300f)) {
            val pan = graph.centeredOn(controller.state.selectedNodeId!!, width, height)
            val cursor = graph.positions.getValue(root().root.id)
            assertEquals(width / 2, cursor.x + pan.x)
            assertEquals(height / 2, cursor.y + pan.y)
            assertNotEquals(width / 2, graph.positions.getValue(grown.currentNodeId).x + pan.x)
        }
        controller.close()
    }

    @Test fun removedReviewFallsBackAndScopesAreFenced() {
        val queue = Queue()
        val controller = HistoryExplorerController(queue, { it() })
        val first = play(GameplayTree(root()), 100)
        controller.update(first); queue.drain()
        val removed = assertNotNull(SelectiveUndo.change(first, a,
            command(101).copy(action = GameAction.Undo)).tree)
        controller.update(removed); queue.drain()
        assertEquals(root().root.id, controller.state.selectedNodeId)
        controller.update(GameplayTree(root().copy(id = id(30))))
        assertNull(controller.state.reviewed); assertNull(controller.state.graph)
        controller.close(); queue.drain(); assertNull(controller.state.reviewed)
    }

    @Test fun selectionWhileWorkIsPendingIsNotOverwrittenBySyncOrOldCallbacks() {
        val queue = Queue()
        val posts = ArrayDeque<() -> Unit>()
        val controller = HistoryExplorerController(queue, { posts.addLast(it) })
        val first = play(GameplayTree(root()), 100)
        controller.update(first); queue.drain() // result posted but not applied
        controller.select(root().root.id)
        controller.update(play(first, 101))
        queue.drain()
        while (posts.isNotEmpty()) posts.removeFirst()()
        assertEquals(root().root.id, controller.state.selectedNodeId)
        assertFalse(controller.state.busy)
    }

    @Test fun historicalInvocationLowersIndividualRequestsAndOnlySuccessfulMutationAdvancesBoundary() {
        val live = play(GameplayTree(root()), 100, b)
        val intent = HistoricalPlayIntent(command(101).scope, root().root.id, id(800))
        val noOp = intent.lower(command(101, PlayAction.SetTapped(id(9), false)))
        val unchanged = live.play(a, noOp)
        assertEquals("noOp", unchanged.outcome); intent.predicted(noOp, unchanged)
        val first = intent.lower(command(102))
        assertEquals(root().root.id, first.continuation!!.nodeId)
        val change = live.play(a, first); intent.predicted(first, change)
        val second = intent.lower(command(103))
        assertEquals(CommandIds.uuidV5(first.requestId, "board-node"), second.continuation!!.nodeId)
        assertEquals(first.invocationId, second.invocationId)
        val next = assertNotNull(change.tree!!.play(a, second).tree)
        assertEquals(1, next.branches.size)
        assertEquals(3, next.nodes.size)
        assertEquals("22", next.stateAt(next.currentNodeId)!!.board.players[0].life)
        assertFailsWith<IllegalArgumentException> { intent.lower(command(104).copy(scope = GameScope(id(999), playId = id(2)))) }
    }

    @Test fun twoAxisPanAndFilteredPathsKeepTrueJunctionAndActiveAncestry() {
        val first = play(GameplayTree(root()), 100)
        val second = play(first, 101)
        val branch = assertNotNull(second.play(b, command(102).copy(continuation = Continuation(first.currentNodeId, true))).tree)
        val graph = HistoryGraphLayout.build(branch, visibleNodeIds = setOf(second.currentNodeId))
        assertTrue(graph.positions.keys.containsAll(branch.nodes.keys + root().root.id))
        val moved = graph.pan(HistoryPoint(0f, 0f), -40f, 30f, 200f, 100f)
        assertEquals(HistoryPoint(-40f, 30f), moved)
        assertEquals(HistoryPoint(-graph.width, 100f), graph.pan(moved, -10000f, 10000f, 200f, 100f))
    }

    @Test fun coldLongReadIsExecutedOnWorkerWithNoUndoWorkCap() {
        var tree = GameplayTree(root())
        repeat(2055) { tree = play(tree, 100 + it) }
        val cold = tree.copy()
        val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "history-reconstruction") }
        val posted = java.util.concurrent.LinkedBlockingQueue<() -> Unit>()
        try {
            var workerThread: String? = null
            val controller = HistoryExplorerController(Executor { task -> worker.execute {
                workerThread = Thread.currentThread().name; task.run()
            } }, { posted.add(it) })
            controller.update(cold)
            assertTrue(controller.state.busy)
            assertNull(controller.state.reviewed)
            assertNotNull(posted.poll(30, TimeUnit.SECONDS)).invoke()
            assertEquals("history-reconstruction", workerThread)
            assertEquals("2075", controller.state.reviewed!!.board.players[0].life)
            assertEquals(2, controller.state.graph!!.ranges.size)
            controller.close()
        } finally { worker.shutdown(); assertTrue(worker.awaitTermination(30, TimeUnit.SECONDS)) }
    }
}
