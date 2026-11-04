package org.asyncttwiz.android

import org.asyncttwiz.fixtures.ScaleGames as F
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.TreeCacheVersion
import org.junit.Test
import java.util.concurrent.Executor
import kotlin.test.*

class HistoryLayoutIndexTest {
    private fun index(tree: GameplayTree, previous: HistoryTree? = null) = HistoryTree(tree.initial.root.id,
        emptyList(), tree.currentNodeId, tree.currentNodeId, containers = tree.initial.board.containers,
        source = tree, previous = previous, lazyPresentation = true)

    @Test fun reenteringDownloadedTwentyThousandEventGameReusesRetainedLayoutAndExtendsOnlyNewEvents() {
        var tree = GameplayTree(F.root())
        repeat(20_000) { tree = F.play(tree, PlayAction.EndTurn, 1000 + it) }
        // In-memory downloaded generation: the disk/decode adapter is exercised in TreeDownloadTest.
        var downloaded = PublishedDownloadedTree(TreeCacheVersion(10, "fixture"), TreeSync.generation(tree))
        assertEquals(20_000, downloaded.history.indexedNodes, "Cold load indexes the downloaded generation once")
        val replica = GameplayReplica()
        fun publish() {
            // Same isolated presentation-copy handoff as GameSession, with the retained index attached.
            val generation = downloaded.generation.copy(tree = downloaded.generation.tree.materializedCopy())
            val play = assertNotNull(generation.tree.preparedState(generation.tree.currentNodeId))
            val summary = GameSummary(play.gameId, "Layout", "date", AccountIdentity(F.a, "A"), AccountIdentity(F.b, "B"),
                play.preparationRound, F.id(99), InvitationState.ACCEPTED, GameLifecycle.ACTIVE,
                revision = downloaded.version.revision.toString(), playId = play.id)
            assertTrue(replica.published(GameHead(play.gameId, summary.revision, summary, play = play), generation,
                summary.revision, emptyList(), emptyList(), downloaded.history))
        }
        fun enter(): TableHistoryController {
            val history = TableHistoryController(Executor { it.run() }, { it() }, { _, _ -> false }, { _, _, _ -> false })
            history.update(replica.displayTree!!, replica.revision!!, accepted = replica.tree!!.tree, indexed = replica.history)
            val presented = assertNotNull(history.playback).tree
            assertEquals(0, presented.indexedNodes, "Table entry must not re-index already downloaded events")
            assertSame(downloaded.history.index.layout, presented.index.layout)
            return history
        }
        publish()
        enter().close() // Leave discards the table controller, not the accepted generation's index.
        assertSame(downloaded.history, replica.history)
        enter().close()

        val before = downloaded.generation.tree
        val after = F.play(before, PlayAction.EndTurn, 50_000)
        val delta = TreeSync.delta(before, after)
        val generation = TreeSync.apply(downloaded.generation, delta)
        downloaded = PublishedDownloadedTree(TreeCacheVersion(11, "next"), generation,
            HistoryTree.ingest(generation.tree, "11", downloaded.history))
        assertEquals(1, downloaded.history.indexedNodes, "An arrival while away extends the retained layout incrementally")
        publish()
        enter().close()
    }

    @Test fun selectingInitialStateInTwentyThousandEventLinearGameNeverAppliesOrUnappliesHistory() {
        var tree = GameplayTree(F.root())
        repeat(20_000) { tree = F.play(tree, PlayAction.EndTurn, 1000 + it) }
        val prepared = index(tree)
        assertNull(tree.preparedState(tree.initial.root.id))
        val explorer = HistoryExplorerController(Executor { it.run() }, { it() }, { _, failure -> throw failure })
        try {
            explorer.update(tree, indexed = prepared)
            tree.work.reset()
            explorer.select(tree.initial.root.id)
            assertNull(explorer.state.error)
            assertSame(tree.initial, explorer.state.reviewed)
            assertEquals(tree.initial.root.id, explorer.state.selectedNodeId)
            assertEquals(0, tree.work.replayedNodes)
            assertEquals(0, tree.work.unappliedNodes)
            assertEquals(0, tree.work.ancestrySteps + tree.work.fullHistoryScans + tree.work.rootMaterializations)
        } finally { explorer.close() }
    }

    // SYS-026
    @Test fun twentyThousandEventsFiftyBranchesOpenAppendAndSelectWithoutHistorySizedWork() {
        var tree = GameplayTree(F.root())
        repeat(20_000) { n ->
            val action = if (n % 5 == 0) PlayAction.EndTurn else PlayAction.AdjustLife(F.a, "1")
            var command = F.command(tree, GameAction.Play(action), 1000 + n)
            if (n > 0 && n % 400 == 0) command = command.copy(
                continuation = Continuation(tree.nodes.getValue(tree.currentNodeId).parentId, true))
            tree = F.change(tree, command)
        }
        assertEquals(49, tree.branches.size)
        var indexed = index(tree)
        assertEquals(20_000, indexed.indexedNodes)
        assertTrue(indexed.index.layout.chips.values.all { it.presentation == null })
        val initialPositions = indexed.index.layout.chips.mapValues { it.value.position }
        val history = TableHistoryController(Executor { it.run() }, { it() }, { _, _ -> false }, { _, _, _ -> false })
        val explorer = HistoryExplorerController(Executor { it.run() }, { it() }, { _, failure -> throw failure })
        try {
            // Exercise the same prepared-index handoff as the production table, not a cold dialog build.
            history.update(tree, "loaded")
            tree.work.reset()
            explorer.update(tree, indexed = history.explorerHistory)
            fun window(): HistoryGraph {
                assertNull(explorer.state.error)
                val graph = assertNotNull(explorer.state.graph)
                val point = graph.positions.getValue(tree.currentNodeId)
                val visible = graph.visible(point.x - 120, point.y - 72, 240f, 144f)
                assertTrue(visible.size < 80)
                visible.filter { it.position.x in (point.x - 120)..(point.x + 120) &&
                    it.position.y in (point.y - 72)..(point.y + 72) }.forEach { graph.eventsById.getValue(it.targetId) }
                assertTrue(graph.spatialVisits < 400)
                assertTrue(graph.nodeVisits < 400, "Opening/selection must visit branches and visible chips only")
                return graph
            }
            val opened = window()
            assertEquals(0, tree.work.fullHistoryScans + tree.work.rootMaterializations)
            assertEquals(0, tree.work.replayedNodes + tree.work.unappliedNodes)
            val parent = tree.nodes.getValue(tree.currentNodeId).parentId
            val beforeSelect = opened.nodeVisits
            explorer.select(parent); explorer.select(tree.currentNodeId)
            assertSame(opened, explorer.state.graph)
            assertTrue(opened.nodeVisits - beforeSelect < 110)
            assertTrue(tree.work.replayedNodes + tree.work.unappliedNodes <= 2)
            repeat(100) { n ->
                tree = F.play(tree, if (n % 5 == 0) PlayAction.EndTurn else PlayAction.AdjustLife(F.a, "1"), 50_000 + n)
                val next = index(tree, indexed)
                assertEquals(1, next.indexedNodes)
                assertEquals(1, next.index.affected.size)
                indexed = next
                tree.work.reset()
                explorer.update(tree, indexed = indexed)
                window()
                assertEquals(0, tree.work.fullHistoryScans + tree.work.rootMaterializations)
                assertEquals(0, tree.work.replayedNodes + tree.work.unappliedNodes,
                    "An unchanged selection must not be reconstructed again on each arrival")
            }
            for ((id, point) in initialPositions) assertEquals(point, indexed.index.layout.chips.getValue(id).position)
        } finally { explorer.close(); history.close() }
    }

    // old-model game tree
    @Test fun midChipForkNeverSplitsOrMovesOriginalChipsAndSwitchOnlyChangesHighlight() {
        var main = GameplayTree(F.root())
        val cursors = mutableListOf<String>()
        repeat(3) { main = F.play(main, PlayAction.AdjustLife(F.a, "1"), 100 + it); cursors.add(main.currentNodeId) }
        main = F.play(main, PlayAction.EndTurn, 104)
        val original = index(main)
        val fork = F.change(main, F.command(main, GameAction.Play(PlayAction.AdjustLife(F.b, "1")), 105)
            .copy(continuation = Continuation(cursors[1], true)), F.b)
        val branched = index(fork, original)
        val layout = branched.index.layout
        for ((id, chip) in original.index.layout.chips) assertSame(chip, layout.chips[id])
        val shared = layout.boundaries.getValue(cursors[0]).chip
        assertEquals(cursors.toList(), layout.chips.getValue(shared).nodes.toList())
        val alternative = layout.boundaries.getValue(fork.currentNodeId).chip
        val graph = HistoryGraph(fork, layout)
        assertTrue(graph.isActive(shared)); assertTrue(graph.isActive(alternative))
        assertFalse(graph.isActive(main.currentNodeId))
        assertTrue(cursors[1] in graph.activeNodeIds)
        assertFalse(cursors[2] in graph.activeNodeIds, "Exact ancestry ends inside the unchanged chip")
        assertTrue(graph.isHead(main.currentNodeId)); assertTrue(graph.isHead(alternative))
        val switched = fork.copy(currentNodeId = main.currentNodeId, currentBranchId = main.currentBranchId)
        val switchedGraph = HistoryGraph(switched, layout)
        assertTrue(switchedGraph.isActive(main.currentNodeId)); assertFalse(switchedGraph.isActive(alternative))
        assertEquals(graph.positions, switchedGraph.positions)
        assertEquals(graph.positions[cursors[0]], graph.positions[cursors[2]])
    }

    // old model (I-D54 §14)
    @Test fun undoPopAndBulkRollbackRemoveTailChipsAndRetainSurvivingCoordinates() {
        var tree = GameplayTree(F.root())
        repeat(12) { tree = F.play(tree, PlayAction.EndTurn, 200 + it) }
        val old = index(tree)
        val undone = F.change(tree, F.command(tree, GameAction.Undo, 220))
        val popped = index(undone, old)
        assertEquals(0, popped.indexedNodes)
        assertFalse(tree.currentNodeId in popped.index.layout.boundaries)
        assertFalse(tree.currentNodeId in popped.index.layout.chips)
        val restored = F.change(undone, F.command(undone, GameAction.Redo, 221))
        val redone = index(restored, popped)
        assertEquals(old.index.layout.chips.getValue(tree.currentNodeId).position,
            redone.index.layout.chips.getValue(tree.currentNodeId).position)

        val removed = mutableSetOf<String>()
        var cursor = restored.currentNodeId
        repeat(8) { removed.add(cursor); cursor = restored.nodes.getValue(cursor).parentId }
        val remaining = restored.nodes - removed
        val rollback = HistoryIndex.build(restored.initial.root.id, remaining, restored.initial.board.containers,
            redone.index, restored.nodes, removed, restored.initial)
        assertEquals(0, rollback.visited)
        assertEquals(removed, rollback.affected)
        for (id in removed) {
            assertFalse(id in rollback.layout.chips)
            assertFalse(id in rollback.layout.boundaries)
        }
        for ((id, chip) in rollback.layout.chips) assertEquals(old.index.layout.chips.getValue(id).position, chip.position)
        val rolledTree = restored.copy(nodes = remaining, currentNodeId = cursor)
        assertTrue(HistoryGraph(rolledTree, rollback.layout).visible(0f, 0f, 1000f, 1000f).none { it.id in removed })
    }

    @Test fun crossingForkLineIsQueriedEvenWhenBothEndpointChipsAreOffscreen() {
        val initial = F.root()
        var layout = HistoryLayoutIndex.root(initial)
        val nodes = linkedMapOf<String, GameplayMutation>()
        repeat(50) { n ->
            val node = GameplayMutation(F.id(1000 + n), initial.root.id, F.id(2000 + n), F.id(3000 + n), F.a,
                null, PlayAction.EndTurn, GameplayTransition())
            nodes[node.id] = node; layout = layout.append(node, node.id)
        }
        val graph = HistoryGraph(GameplayTree(initial, nodes = nodes), layout)
        val visible = graph.visible(0f, 700f, 70f, 80f)
        assertTrue(nodes.values.last().id in visible.map { it.id })
        assertNull(layout.chips.getValue(nodes.values.last().id).presentation,
            "Querying a crossing edge must not project its offscreen chip's text/glyph")
        assertTrue(graph.spatialVisits < 300, "Crossing edges use interval queries, not a scan of all chips")
        val last = nodes.values.last()
        val removed = layout.remove(setOf(last.id), nodes)
        val next = HistoryGraph(GameplayTree(initial, nodes = nodes - last.id), removed)
        assertFalse(last.id in next.visible(0f, 700f, 70f, 80f).map { it.id })
    }

    @Test fun reopeningWhileIngestionIsPendingDoesNotFrameOrReviewThePreviousSelection() {
        val tree = F.play(GameplayTree(F.root()), PlayAction.AdjustLife(F.a, "1"), 500)
        val explorer = HistoryExplorerController(Executor { it.run() }, { it() })
        explorer.update(tree, indexed = index(tree))
        assertNotNull(explorer.state.reviewed)
        explorer.opening(tree.initial.root.id)
        assertNull(explorer.state.graph); assertNull(explorer.state.reviewed); assertTrue(explorer.state.busy)
        explorer.update(tree, openingNodeId = tree.initial.root.id, indexed = index(tree))
        assertEquals(tree.initial.root.id, explorer.state.selectedNodeId)
        assertFalse(explorer.state.busy)
        explorer.close()
    }

    // old-model game tree
    @Test fun reparentedForkKeepsItsLaneAndUpdatesItsAncestryAfterSelectiveRemoval() {
        val initial = F.root()
        val prefix = GameplayMutation(F.id(600), initial.root.id, initial.root.branchId, F.id(700), F.a,
            null, PlayAction.EndTurn, GameplayTransition())
        val branch = prefix.copy(id = F.id(601), parentId = prefix.id, branchId = F.id(602))
        val oldNodes = listOf(prefix, branch).associateBy { it.id }
        val old = HistoryIndex.build(initial.root.id, oldNodes, initial.board.containers, initial = initial)
        val next = branch.copy(parentId = initial.root.id)
        val rewritten = HistoryIndex.build(initial.root.id, mapOf(next.id to next), initial.board.containers,
            old, oldNodes, setOf(prefix.id, branch.id), initial).layout
        assertEquals(old.layout.branches.getValue(branch.branchId).lane, rewritten.branches.getValue(branch.branchId).lane)
        assertEquals(initial.root.id, rewritten.branches.getValue(branch.branchId).fork)
        assertEquals(setOf(initial.root.branchId, branch.branchId), rewritten.path(next.id).keys)
        val backwards = HistoryForkIndex().put(2, 1, 20, next.id)
        val crossing = mutableListOf<String>()
        backwards.query(2, 10, 12, {}, crossing::add)
        assertEquals(listOf(next.id), crossing)
    }
}
