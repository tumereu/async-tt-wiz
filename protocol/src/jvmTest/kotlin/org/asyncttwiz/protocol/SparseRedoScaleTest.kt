package org.asyncttwiz.protocol

import kotlin.system.measureNanoTime
import kotlin.test.*
import org.asyncttwiz.fixtures.ScaleGames as F

class SparseRedoScaleTest {
    @Test fun aRedoEntryDoesNotSerializeTheSiblingsOfAPopularFork() {
        val games = listOf(1000, 10000).associateWith { size ->
            var tree = F.build(size)
            val anchor = tree.currentNodeId
            repeat(size) { n ->
                val command = F.command(tree, GameAction.Play(PlayAction.AdjustLife(F.a, "1")), 500000 + n)
                    .copy(continuation = Continuation(anchor, true))
                tree = F.change(tree, command)
            }
            tree
        }
        fun operation(tree: GameplayTree) {
            tree.work.reset()
            val next = F.change(tree, F.command(tree, GameAction.Undo, 800003))
            val delta = TreeSync.delta(tree, next)
            val entry = next.redoByAccount[F.a]!!.last()
            assertTrue(entry.otherChildIds.isEmpty())
            assertNotNull(entry.otherChildCount)
            assertTrue(GameWire.json.encodeToString(GameplayTreeDelta.serializer(), delta).length < 10000)
            val restored = F.change(next, F.command(next, GameAction.Redo, 800004))
            assertEquals(tree.stateAt(tree.currentNodeId), restored.stateAt(restored.currentNodeId))
            assertEquals(0, tree.work.fullHistoryScans)
            assertEquals(0, tree.work.rootMaterializations)
            assertTrue(tree.work.indexedNodes <= 8)
        }
        repeat(20) { games.values.forEach(::operation) }
        val times = games.mapValues { (_, tree) -> List(21) { measureNanoTime { operation(tree) } }.sorted()[10] / 1_000_000.0 }
        val small = times.getValue(1000); val large = times.getValue(10000)
        println("sparse redo siblings 1k=$small ms 10k=$large ms ratio=${large / small}; sibling ids written=0")
        assertTrue(large < small * 3, "$times")
        assertTrue(large < 250, "$times")
    }

    @Test fun tenThousandActionRedoStacksWriteOnlyChangedSlots() {
        val games = listOf(1000, 10000).associateWith { size ->
            var tree = F.build(size)
            // Real accepted push/pop history, not a forged queue with repeated entries.
            repeat(size) { tree = F.play(tree, PlayAction.AdjustLife(F.a, "1"), 300000 + it) }
            repeat(size) { tree = F.change(tree, F.command(tree, GameAction.Undo, 400000 + it)) }
            assertEquals(size, tree.redoByAccount[F.a]!!.size)
            tree
        }
        fun operation(tree: GameplayTree) {
            tree.work.reset()
            val next = F.change(tree, F.command(tree, GameAction.Redo, 800001))
            val delta = TreeSync.delta(tree, next)
            assertEquals(1, delta.upserts.count { it.kind == "redo-size" })
            assertEquals(0, delta.upserts.count { it.kind == "redo-entry" || it.kind == "redo" })
            assertTrue(GameWire.json.encodeToString(GameplayTreeDelta.serializer(), delta).length < 10000)
            val back = F.change(next, F.command(next, GameAction.Undo, 800002))
            val pushed = TreeSync.delta(next, back)
            assertEquals(1, pushed.upserts.count { it.kind == "redo-entry" })
            assertEquals(1, pushed.upserts.count { it.kind == "redo-size" })
            assertEquals(0, tree.work.fullHistoryScans)
            assertEquals(0, tree.work.rootMaterializations)
            // Each control has several sparse reducer stages; two controls touch at most eight
            // node-index entries total, regardless of the number of queued entries.
            assertTrue(tree.work.indexedNodes <= 8, "indexedNodes=${tree.work.indexedNodes}")
            val applied = TreeSync.apply(DownloadedTree(tree, mapOf(tree.initial.root.branchId to kotlinx.serialization.json.buildJsonObject {} ) +
                tree.branchHeads.keys.associateWith { kotlinx.serialization.json.buildJsonObject {} }), delta).tree
            assertEquals(next.stateAt(next.currentNodeId), applied.stateAt(applied.currentNodeId))
            assertEquals(next.redoByAccount[F.a]!!.size, applied.redoByAccount[F.a]!!.size)
        }
        repeat(20) { games.values.forEach(::operation) }
        val medians = games.mapValues { (_, tree) -> List(21) { measureNanoTime { operation(tree) } }.sorted()[10] / 1_000_000.0 }
        val small = medians.getValue(1000); val large = medians.getValue(10000)
        println("sparse redo 1k=$small ms 10k=$large ms ratio=${large / small}; pop=1 size, push=1 entry+1 size")
        assertTrue(large < small * 3, "Redo stack work must not scale with queue length: $medians")
        assertTrue(large < 250, "Generous absolute budget: $medians")
    }
}
