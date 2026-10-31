package org.asyncttwiz.protocol

import org.asyncttwiz.fixtures.ScaleGames as F
import kotlin.system.measureNanoTime
import kotlin.test.*

class ScaleAcceptanceTest {
    private fun bounded(tree: GameplayTree, label: String, limit: Int = 50, operation: () -> Unit) {
        tree.work.reset()
        operation()
        val work = tree.work
        assertEquals(0, work.rootMaterializations, "$label reconstructed from root")
        assertEquals(0, work.fullHistoryScans, "$label scanned history")
        assertTrue(work.replayedNodes + work.unappliedNodes <= limit, "$label replay=${work.replayedNodes} reverse=${work.unappliedNodes}")
        assertTrue(work.ancestrySteps <= limit * 4, "$label ancestry=${work.ancestrySteps}")
        assertTrue(work.indexedNodes <= limit * 4, "$label topology=${work.indexedNodes}")
    }

    // SYS-026
    @Test fun tenThousandActionsHaveBoundedIncrementalWorkAndExactRoundTrips() {
        val tree = F.build(10000)
        assertEquals(10000, tree.nodes.size)
        val state = assertNotNull(tree.preparedState(tree.currentNodeId))
        val anchor = EventReference(state.id, state.currentNodeId)
        lateinit var appended: GameplayTree
        bounded(tree, "append", 2) { appended = F.play(tree, PlayAction.AdjustLife(F.a, "1"), 500001) }
        bounded(tree, "delta", 2) {
            val delta = TreeSync.delta(tree, appended)
            assertEquals(1, delta.upserts.count { it.kind == "node" })
        }
        lateinit var undone: GameplayTree
        bounded(appended, "undo") { undone = F.change(appended, F.command(appended, GameAction.Undo, 500002)) }
        bounded(undone, "redo") {
            val redone = F.change(undone, F.command(undone, GameAction.Redo, 500003))
            assertEquals(appended.stateAt(appended.currentNodeId), redone.stateAt(redone.currentNodeId))
        }
        val parent = tree.nodes.getValue(tree.currentNodeId).parentId
        tree.prepareReview(parent)
        bounded(tree, "one review group backwards/forwards", 8) {
            // At this fixture's tail each author's change starts its own group.
            val previous = tree.nodes.getValue(parent).parentId
            assertNotNull(tree.prepareReview(previous))
            assertNotNull(tree.prepareReview(parent))
        }
        // The other endpoint remains at the 10k head: choosing a nearby historical endpoint must
        // not first walk the distant head's complete ancestry to calculate its distance.
        val middle = tree.nodes.values.elementAt(5000)
        assertNotNull(tree.prepareReview(middle.id))
        bounded(tree, "deep historical review", 8) {
            assertNotNull(tree.prepareReview(middle.parentId))
            assertNotNull(tree.prepareReview(middle.id))
        }
        lateinit var ping: GameplayTree
        bounded(tree, "ping", 8) { ping = F.play(tree, PlayAction.Ping(anchor, F.pingTarget), 500004) }
        lateinit var unping: GameplayTree
        bounded(ping, "unping", 8) { unping = F.play(ping, PlayAction.Unping(anchor, F.pingTarget), 500005) }
        bounded(unping, "undo unping", 8) {
            val restored = F.change(unping, F.command(unping, GameAction.Undo, 500006))
            assertEquals(ping.stateAt(ping.currentNodeId), restored.stateAt(restored.currentNodeId))
        }
        lateinit var comment: GameplayTree
        bounded(tree, "comment", 8) { comment = F.play(tree, PlayAction.EventComment(anchor, "Scale comment"), 500007) }
        bounded(comment, "remove comment", 8) {
            val removed = F.change(comment, F.command(comment, GameAction.Undo, 500008))
            assertEquals(state, removed.stateAt(removed.currentNodeId))
        }
        val baseline = TreeSync.generation(tree)
        bounded(tree, "accepted delta", 8) {
            val next = TreeSync.apply(baseline, TreeSync.delta(tree, appended))
            assertEquals(appended.stateAt(appended.currentNodeId), next.tree.stateAt(next.tree.currentNodeId))
        }
        // Only the touched annotation record (and one ordinal key), never a cumulative list.
        val entities = ping.nodes.getValue(ping.currentNodeId).transition.delta!!.entities
        assertTrue(entities.all { it.kind in setOf("ping", "ping-ordinal") })
        assertTrue(GameWire.json.encodeToString(GameplayDelta.serializer(), ping.nodes.getValue(ping.currentNodeId).transition.delta!!).length < 4000)
    }

    private fun operations(tree: GameplayTree): Map<String, () -> Unit> {
        val appended = F.play(tree, PlayAction.AdjustLife(F.a, "1"), 600001)
        val undone = F.change(appended, F.command(appended, GameAction.Undo, 600002))
        val anchor = EventReference(tree.initial.id, tree.currentNodeId)
        val ping = F.play(tree, PlayAction.Ping(anchor, F.pingTarget), 600003)
        val comment = F.play(tree, PlayAction.EventComment(anchor, "timed"), 600004)
        val parent = tree.nodes.getValue(tree.currentNodeId).parentId
        val grandparent = tree.nodes.getValue(parent).parentId
        tree.prepareReview(parent)
        val baseline = TreeSync.generation(tree)
        val delta = TreeSync.delta(tree, appended)
        return linkedMapOf(
            "append" to { F.play(tree, PlayAction.AdjustLife(F.a, "1"), 600005); Unit },
            "undo" to { F.change(appended, F.command(appended, GameAction.Undo, 600006)); Unit },
            "redo" to { F.change(undone, F.command(undone, GameAction.Redo, 600007)); Unit },
            "review" to { tree.prepareReview(grandparent); tree.prepareReview(parent); Unit },
            "ping" to { F.play(tree, PlayAction.Ping(anchor, F.pingTarget), 600008); Unit },
            "unping" to { F.play(ping, PlayAction.Unping(anchor, F.pingTarget), 600009); Unit },
            "comment" to { F.play(tree, PlayAction.EventComment(anchor, "timed"), 600010); Unit },
            "remove-comment" to { F.change(comment, F.command(comment, GameAction.Undo, 600011)); Unit },
            "delta" to { TreeSync.delta(tree, appended); Unit },
            "sync" to { TreeSync.apply(baseline, delta); Unit },
        )
    }

    // SYS-025
    @Test fun oneAndTenThousandActionMediansStayWithinDesktopBudgets() {
        val games = listOf(1000, 10000).associateWith { size ->
            lateinit var tree: GameplayTree
            val buildMs = measureNanoTime { tree = F.build(size) } / 1_000_000.0
            println("scale fixture $size: ${"%.2f".format(buildMs)} ms")
            assertTrue(buildMs < 10000, "Incremental fixture must build in seconds")
            operations(tree)
        }
        // Warm both sizes before collecting either: classloading/JIT is not scaling work.
        repeat(40) { games.values.forEach { operations -> operations.values.forEach { it() } } }
        for (name in games.getValue(1000).keys) {
            val samples = games.mapValues { (_, ops) -> List(41) { measureNanoTime { ops.getValue(name)() } }.sorted()[20] / 1_000_000.0 }
            val small = samples.getValue(1000); val large = samples.getValue(10000)
            println("scale $name: 1k=${"%.4f".format(small)} ms 10k=${"%.4f".format(large)} ms ratio=${"%.2f".format(large / small)}")
            assertTrue(large < 20, "$name median=$large ms")
            assertTrue(large < small * 3 + 0.03, "$name scaled from $small to $large ms (30us noise allowance)")
        }
    }
}
