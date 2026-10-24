package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class HistoryExplorerTest {
    @Test fun diagnosticsDistinguishMissingCursorReplayFailureAndLayoutException() {
        val failures = mutableListOf<Pair<String, Throwable>>()
        val controller = HistoryExplorerController(Executor { it.run() }, { it() }, { true }, { _, _ -> false },
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
        val controller = HistoryExplorerController(queue, { it() }, { true }, { _, _ -> error("unexpected activation") })
        try {
            controller.update(GameplayTree(root())); queue.drain()
            controller.select(id(999)); queue.drain()
            assertFalse(controller.state.busy)
            assertEquals("History unavailable", controller.state.error)
            assertNull(controller.captureMakeCurrent())
            controller.select(root().root.id); queue.drain()
            assertNull(controller.state.error)
            assertNotNull(controller.state.reviewed)
        } finally { controller.close() }
    }
    @Test fun archivedSelectedBoundaryRemainsExactlyReviewableAndRequiresExplicitRestoreBeforeActivation() {
        val original = play(GameplayTree(root()), 100)
        val alternative = assertNotNull(original.play(b, command(101).copy(continuation = Continuation(root().root.id, true))).tree)
        val archived = assertNotNull(HistoryContinuation.change(alternative, a, command(102).copy(
            action = GameAction.BranchArchive(BranchArchiveIntent.Archive(original.currentBranchId)))).tree)
        val queue = Queue()
        val controller = HistoryExplorerController(queue, { it() }, { true }, { _, _ -> error("No implicit activation") })
        try {
            controller.update(archived, openingNodeId = original.currentNodeId, showHidden = false); queue.drain()
            assertEquals(original.currentNodeId, controller.state.selectedNodeId)
            assertEquals(original.currentNodeId, controller.state.reviewed!!.currentNodeId)
            assertTrue(controller.state.archivedSelection)
            assertNull(controller.captureMakeCurrent())
            assertTrue(original.currentNodeId in controller.state.graph!!.positions)
            assertEquals(2, archived.nodes.size, "Filtering never removes retained data")
        } finally { controller.close() }
    }
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = id(7)
    private val b = id(8)
    private fun root() = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
        BoardState(listOf(PlayPlayer(a), PlayPlayer(b)), listOf(PlayCard(id(9), "hidden", a, id(10), id(11))),
            listOf(PlayContainer(id(10), PlayZone.LIBRARY, a, listOf(id(9)))), TurnState(a)),
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
        assertEquals(4, graph.ranges.size) // root, prefix, old suffix, interruption
        val prefix = graph.ranges.single { it.targetId == first.currentNodeId }
        val suffixes = graph.ranges.filter { it.parentRangeId == prefix.id }
        assertEquals(2, suffixes.size)
        assertEquals(setOf(third.currentNodeId, branch.currentNodeId), suffixes.map { it.targetId }.toSet())
        assertNotEquals(suffixes[0].position.y, suffixes[1].position.y)
        assertNotEquals(graph.positions[second.currentNodeId], graph.positions[third.currentNodeId])
        val suffix = graph.ranges.single { second.currentNodeId in it.nodeIds }
        val parent = graph.ranges.single { it.id == suffix.parentRangeId }
        val interior = graph.positions.getValue(second.currentNodeId)
        assertEquals((parent.position.x + suffix.position.x) / 2, interior.x)
        assertEquals((parent.position.y + suffix.position.y) / 2, interior.y)
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
        val reveal = graph.ranges.single { it.glyph == HistoryGlyph.INSPECT }
        assertEquals(PlayAction.EventComment::class, tree.nodes.getValue(reveal.targetId).action::class)
        assertTrue(tree.stateAt(reveal.targetId)!!.board.contentsDisplays.isNotEmpty())
    }

    @Test fun groupingUsesSharedProcessorAndLiteralRepresentativeNotActionTarget() {
        val tap = play(GameplayTree(root()), 100, action = PlayAction.SetTapped(id(9), true))
        val life = play(tap, 101)
        val graph = HistoryGraphLayout.build(life)
        assertEquals(2, graph.ranges.size)
        assertEquals(HistoryGlyph.LIFE, graph.ranges.last().glyph)
        assertEquals(life.currentNodeId, graph.ranges.last().representativeId)
        val more = play(life, 102)
        val updated = HistoryGraphLayout.build(more)
        assertEquals(life.currentNodeId, updated.ranges.last().representativeId) // first equal-rank event
        assertEquals(more.currentNodeId, updated.ranges.last().targetId)
        assertEquals(a, updated.ranges.last().author)
        assertEquals(HistoryGlyph.ROOT, updated.ranges.first().glyph)
        assertNull(updated.ranges.first().representativeId)
    }

    @Test fun localExactSelectionAndCapturedActivationSurviveGrowthAndReranking() {
        val queue = Queue()
        val submissions = mutableListOf<Pair<GameScope, GameAction>>()
        var online = true
        val controller = HistoryExplorerController(queue, { it() }, { online }, { scope, action ->
            submissions.add(scope to action); true
        })
        val tap = play(GameplayTree(root()), 100, action = PlayAction.SetTapped(id(9), true))
        controller.update(tap, openingNodeId = root().root.id)
        assertTrue(submissions.isEmpty()); queue.drain()
        assertEquals(root().root.id, controller.state.selectedNodeId)
        controller.select(tap.currentNodeId); queue.drain()
        val captured = assertNotNull(controller.captureMakeCurrent())
        val grown = play(tap, 101)
        controller.update(grown); queue.drain()
        assertEquals(tap.currentNodeId, controller.state.selectedNodeId)
        assertEquals(tap.currentNodeId, controller.state.reviewed!!.currentNodeId)
        assertEquals(HistoryGlyph.LIFE, controller.state.graph!!.ranges.last().glyph)
        assertTrue(submissions.isEmpty()) // review/growth/pan/arming never activate
        assertNotNull(controller.armContinuation(id(800)))
        assertTrue(submissions.isEmpty())
        online = false; assertFalse(controller.makeCurrent(captured))
        online = true; assertTrue(controller.makeCurrent(captured))
        assertEquals(GameAction.MakeCurrent(tap.currentNodeId), submissions.single().second)
        assertEquals(grown.currentNodeId, controller.state.graph!!.currentNodeId) // submit never changes shared current locally
        controller.close(); assertFalse(controller.makeCurrent(captured))
    }

    @Test fun removedReviewFallsBackButCapturedActionNeverRetargetsAndScopesAreFenced() {
        val queue = Queue()
        val submissions = mutableListOf<GameAction>()
        val controller = HistoryExplorerController(queue, { it() }, { true }, { _, action -> submissions.add(action); true })
        val first = play(GameplayTree(root()), 100)
        controller.update(first); queue.drain()
        val captured = controller.captureMakeCurrent()!!
        val removed = assertNotNull(SelectiveUndo.change(first, a,
            command(101).copy(action = GameAction.Undo)).tree)
        controller.update(removed); queue.drain()
        assertEquals(root().root.id, controller.state.selectedNodeId)
        assertTrue(controller.makeCurrent(captured))
        assertEquals(GameAction.MakeCurrent(first.currentNodeId), submissions.single())
        controller.update(GameplayTree(root().copy(id = id(30))))
        assertNull(controller.state.reviewed); assertNull(controller.state.graph)
        assertFalse(controller.makeCurrent(captured))
        controller.close(); queue.drain(); assertNull(controller.state.reviewed)
    }

    @Test fun selectionWhileWorkIsPendingIsNotOverwrittenBySyncOrOldCallbacks() {
        val queue = Queue()
        val posts = ArrayDeque<() -> Unit>()
        val controller = HistoryExplorerController(queue, { posts.addLast(it) }, { true }, { _, _ -> error("unexpected submit") })
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
            } }, { posted.add(it) }, { false }, { _, _ -> false })
            controller.update(cold)
            assertTrue(controller.state.busy)
            assertNull(controller.state.reviewed)
            assertNotNull(posted.poll(30, TimeUnit.SECONDS)).invoke()
            assertEquals("history-reconstruction", workerThread)
            assertEquals("2075", controller.state.reviewed!!.board.players[0].life)
            assertEquals(2, controller.state.graph!!.ranges.size)
            assertFalse(controller.makeCurrent())
            controller.close()
        } finally { worker.shutdown(); assertTrue(worker.awaitTermination(30, TimeUnit.SECONDS)) }
    }
}
