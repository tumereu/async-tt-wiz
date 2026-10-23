package org.asyncttwiz.protocol

import kotlinx.serialization.json.*
import kotlin.math.ceil
import kotlin.test.*
import kotlin.time.TimeSource

/** LOWER-LAYER synthetic accepted histories, NOT UI-created games or E2E evidence.
 * Run the JVM selector under the executor's RSS/cgroup sampler: common Kotlin has no heap API.
 * Fixture construction/warmup are outside the cold-open clock. No selective limit is raised.
 */
class RuntimeWorkloadTest {
    @Test fun sparse10k() = workload(10_000, false)
    @Test fun heavilyBranched10k() = workload(10_000, true)
    @Test fun sparse100k() = workload(100_000, false)
    @Test fun heavilyBranched100k() = workload(100_000, true)

    private fun workload(count: Int, branched: Boolean) {
        val fixture = history(count, branched)
        val tree = fixture.first
        val expected = fixture.second
        val shape = if (branched) "heavily-branched" else "sparse"
        // Warm code on a different tree; never populate the measured tree's disposable checkpoint.
        assertNotNull(history(20, branched).first.let { it.stateAt(it.currentNodeId) })
        val cold = List(3) {
            val input = tree.copy()
            val start = TimeSource.Monotonic.markNow()
            val state = assertNotNull(input.stateAt(input.currentNodeId))
            val nanos = start.elapsedNow().inWholeNanoseconds
            assertEquals(expected, state)
            assertEquals(count, input.nodes.size)
            nanos
        }
        observations("cold-current-open", count, shape, cold)
        // 3 seconds is the profile's cold accepted 10k open target, not a 100k download deadline.
        if (count == 10_000) assertTrue(cold.all { it <= 3_000_000_000L }, "10k cold open exceeds 3s")

        val generation = TreeSync.generation(tree)
        val validation = List(3) {
            val start = TimeSource.Monotonic.markNow()
            TreeSync.validate(generation) // production complete-tree child/reference indexes + replay
            start.elapsedNow().inWholeNanoseconds
        }
        observations("complete-index-validation-replay", count, shape, validation)
        val start = TimeSource.Monotonic.markNow()
        var bytes = 0L
        var records = 0
        var largestRecord = 0
        // One encoded record at a time; deliberately no toList/joinToString of the transfer.
        val decoded = TreeSync.decode(TreeSync.records(generation).map { record ->
            val encoded = GameWire.json.encodeToString(TreeSyncRecord.serializer(), record).encodeToByteArray()
            bytes += encoded.size + 1L
            largestRecord = maxOf(largestRecord, encoded.size)
            records++
            GameWire.json.decodeFromString(TreeSyncRecord.serializer(), encoded.decodeToString())
        }, tree.initial.gameId, tree.initial.id)
        observations("flat-complete-codec-stream", count, shape,
            listOf(start.elapsedNow().inWholeNanoseconds), bytes, records, largestRecord)
        assertEquals(tree, decoded.tree) // exact offshoots/heads/lineage, not only the current path
        assertEquals(expected, decoded.tree.stateAt(tree.currentNodeId))
        for (head in tree.branchHeads.values) {
            val state = assertNotNull(decoded.tree.stateAt(head))
            val depth = if (branched) count / 100 else count / 2
            assertEquals((20 + depth).toString(), state.board.players.first().life)
            assertEquals(tree.initial.board.cards, state.board.cards)
        }

        val undo = GameCommand(gameplayId(900_000), GameScope(tree.initial.gameId, playId = tree.initial.id),
            GameAction.Undo, gameplayId(80), "1")
        val meterStart = TimeSource.Monotonic.markNow()
        val refused = SelectiveUndo.change(tree, gameplayId(7), undo)
        val undoneTree = refused.tree
        observations("default-selective-work-meter", count, shape,
            listOf(meterStart.elapsedNow().inWholeNanoseconds), result = refused.reason ?: refused.outcome)
        if (count == 100_000 || !branched) {
            assertEquals("workLimit", refused.reason) // default metadata or ancestor budget
            assertNull(refused.tree)
        } else if (undoneTree != null) {
            // 10k short branches may fit the default meter; successful Undo must be exact.
            assertEquals(count - 1, undoneTree.nodes.size)
            val undone = assertNotNull(undoneTree.stateAt(undoneTree.currentNodeId))
            assertEquals("119", undone.board.players.first().life)
            assertEquals(expected.board.cards, undone.board.cards)
        } else assertEquals("workLimit", refused.reason)
        assertEquals(expected, tree.stateAt(tree.currentNodeId))
        assertEquals(count, tree.nodes.size) // refusal must not truncate retained ordinary history
    }

    private fun history(count: Int, branched: Boolean): Pair<GameplayTree, PlayState> {
        val root = gameplayFixture()
        val nodes = linkedMapOf<String, GameplayMutation>()
        val heads = linkedMapOf<String, String>()
        val lineage = linkedMapOf<String, GameplayBranch>()
        // Sparse = two long continuations. Heavy = 100 continuations, all retained and exported.
        val branches = if (branched) 100 else 2
        var request = 1_000
        var expected = root
        repeat(branches) { branch ->
            val branchId = if (branch == 0) root.root.branchId else gameplayId(500_000 + branch)
            var state = root.copy(currentBranchId = branchId)
            var first: GameplayMutation? = null
            repeat(count / branches) {
                val command = GameCommand(gameplayId(request++), GameScope(root.gameId, playId = root.id),
                    GameAction.Play(PlayAction.AdjustLife(gameplayId(7), "1")), gameplayId(80), "1")
                val change = GameplayRules.change(state, gameplayId(7), command)
                state = assertNotNull(change.state)
                val node = assertNotNull(change.mutation)
                if (first == null) first = node
                nodes[node.id] = node
            }
            heads[branchId] = state.currentNodeId
            if (branch != 0) lineage[branchId] = GameplayBranch(branchId, root.root.id, first!!.id,
                first!!.requestId, gameplayId(7), heads.getValue(root.root.branchId), root.root.branchId)
            expected = state
        }
        return GameplayTree(root, nodes, expected.currentNodeId, branches = lineage,
            currentBranchId = expected.currentBranchId, branchHeads = heads) to expected
    }

    private fun observations(metric: String, nodes: Int, shape: String, nanos: List<Long>, bytes: Long = 0,
        records: Int = 0, largestRecord: Int = 0, result: String = "measured") {
        val sorted = nanos.sorted()
        fun percentile(p: Double) = sorted[(ceil(sorted.size * p).toInt() - 1).coerceAtLeast(0)]
        println(buildJsonObject {
            put("workload", "I33-v1"); put("layer", "LOWER-LAYER"); put("metric", metric)
            put("result", result)
            put("basePin", "0436d6ec399ad821c19e24170f2e6ef01d637015")
            put("profile", "runtime-and-verification-profile"); put("nodes", nodes); put("shape", shape)
            put("samples", nanos.size); put("p95Nanos", percentile(.95)); put("p99Nanos", percentile(.99))
            putJsonArray("rawNanos") { nanos.forEach { add(JsonPrimitive(it)) } }
            put("encodedBytes", bytes); put("records", records); put("largestRecordBytes", largestRecord)
            put("memory", "executor-owned peak RSS/cgroup sampling required; no common heap API")
        })
    }
}
