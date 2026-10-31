package org.asyncttwiz.protocol

import kotlinx.serialization.json.*
import kotlin.test.*

class TreeSyncTest {
    private val f = UndoFixture
    private fun root(): GameplayTree {
        val definition = CardDefinition("Retained Wizard 🧙", listOf(DefinitionFace(f.id(11), "Wizard")))
        val digest = DeckJson.digest(definition)
        val initial = f.root().let { it.copy(board = it.board.copy(cards = it.board.cards.map { card -> card.copy(definition = digest) }),
            definitions = listOf(SubmittedDefinition(digest, definition))) }
        return GameplayTree(initial)
    }
    private fun generation(tree: GameplayTree) = DownloadedTree(tree, TreeSync.branchIds(tree).associateWith {
        buildJsonObject { put("archived", it == f.id(30)); put("hidden", it == f.id(30)); put("label", "retained 🧙") }
    })
    private fun decode(records: Sequence<TreeSyncRecord>) = TreeSync.decode(records, f.id(1), f.id(2))
    private fun branched(): DownloadedTree {
        val root = root()
        val first = f.tap(root)
        val line = f.tap(first, f.b, 12, n = 101)
        val other = f.tap(root, f.b, 12, n = 102).nodes.values.single().copy(branchId = f.id(30),
            references = listOf(GameplayNodeReference(first.currentNodeId, first.nodes.values.single().transition)))
        val detached = assertNotNull(f.undo(f.tap(root, n = 103)).tree)
        return generation(line.copy(nodes = line.nodes + (other.id to other), currentNodeId = first.currentNodeId,
            redoByAccount = detached.redoByAccount, removedParents = detached.removedParents))
    }

    @Test fun authoritativeRestoreChecksTopologyAndEndpointPointersWithoutReplayingBoards() {
        val input = branched()
        val endpoint = assertNotNull(input.tree.stateAt(input.tree.currentNodeId))
        val records = TreeSync.records(input).toList()
        val restored = TreeSync.restore(records.asSequence(), f.id(1), f.id(2), endpoint)
        assertEquals(endpoint, restored.tree.stateAt(restored.tree.currentNodeId))
        assertEquals(0, restored.tree.work.replayedNodes)
        assertEquals(0, restored.tree.work.rootMaterializations)
        assertFails { TreeSync.restore(records.asSequence(), f.id(1), f.id(2), endpoint.copy(currentNodeId = f.id(999))) }
        assertFails { TreeSync.restore(records.asSequence(), f.id(1), f.id(2), endpoint.copy(currentBranchId = f.id(999))) }
        val ancestor = input.tree.nodes.values.single { it.author == f.a }
        assertFails { TreeSync.restore(records.filterNot { it.kind == "node" && it.id == ancestor.id }.asSequence(), f.id(1), f.id(2), endpoint) }
        TreeSync.validate(restored)
        assertEquals(input.tree.nodes.size, restored.tree.work.replayedNodes, "Explicit maintenance still validates every retained mutation")
        assertEquals(input.tree.nodes.size, restored.tree.work.deltaApplications)
    }

    @Test fun materializedAnnotationsBypassAncestryAndActualDeltaApplicationsIncludingLegacyProjection() {
        var tree = root()
        repeat(16) { n -> tree = f.play(tree, f.a, PlayAction.EventComment(EventReference(f.id(2), f.id(5)), "mark $n"), 4000 + n) }
        val endpoint = tree.stateAt(tree.currentNodeId)!!
        val records = TreeSync.records(generation(tree)).toList()
        val bounded = endpoint.copy(annotations = EventAnnotationState())
        val restored = TreeSync.restore(records.asSequence(), f.id(1), f.id(2), bounded,
            boundedEndpoint = true, annotations = endpoint.annotations)
        assertEquals(endpoint, restored.tree.stateAt(tree.currentNodeId))
        assertEquals(0, restored.tree.work.deltaApplications)
        assertEquals(0, restored.tree.work.ancestrySteps)
        val legacy = TreeSync.restore(records.asSequence(), f.id(1), f.id(2), bounded, boundedEndpoint = true)
        assertEquals(endpoint, legacy.tree.stateAt(tree.currentNodeId))
        assertEquals(16, legacy.tree.work.deltaApplications, "Annotation-only reducer calls must be measured")
        assertEquals(16, legacy.tree.work.ancestrySteps)
        val next = f.play(restored.tree, f.a, PlayAction.EventComment(EventReference(f.id(2), f.id(5)), "new"), 5000)
        restored.tree.work.reset()
        assertEquals(setOf(GameplayTreeKey("comment", next.currentNodeId)), TreeSync.annotationChanges(restored.tree, next))
        assertEquals(1, next.work.ancestrySteps)
    }

    @Test fun acceptedDeltaKeepsWarmHeadAcross360EventsWithoutRootReplay() {
        var tree = root()
        repeat(360) { n ->
            val next = f.play(tree, f.a, PlayAction.SetLife(f.a, (n + 21).toString()), 2000 + n)
            tree = next
        }
        var accepted = decode(TreeSync.records(generation(tree)))
        val baselineReplays = accepted.tree.replayedNodes
        assertEquals(360, baselineReplays)
        assertSame(accepted.tree.preparedState(accepted.tree.currentNodeId), accepted.tree.stateAt(accepted.tree.currentNodeId))
        repeat(8) { n ->
            val next = f.play(accepted.tree, f.a, PlayAction.SetLife(f.a, (500 + n).toString()), 3000 + n)
            val delta = TreeSync.delta(accepted.tree, next)
            val started = kotlin.time.TimeSource.Monotonic.markNow()
            val published = TreeSync.apply(accepted, delta)
            assertEquals(next.stateAt(next.currentNodeId), published.tree.stateAt(published.tree.currentNodeId))
            assertTrue(started.elapsedNow().inWholeMilliseconds < 50, "Incremental accepted revision must stay below 50 ms")
            assertEquals(1, published.tree.replayedNodes, "Only the new event may replay")
            assertEquals(if (n == 0) baselineReplays else 1, accepted.tree.replayedNodes, "Old generation must not replay its root")
            accepted = published
        }
        val undone = assertNotNull(f.undo(accepted.tree).tree)
        val published = TreeSync.apply(accepted, TreeSync.delta(accepted.tree, undone))
        assertEquals(undone.stateAt(undone.currentNodeId), published.tree.stateAt(published.tree.currentNodeId))
        assertEquals(0, published.tree.replayedNodes, "Undo must reverse the old head, not replay ancestry")
    }

    // SYS-027
    @Test fun schemaOneAndMissingInverseDataAreRejectedRatherThanReexecuted() {
        val tree = f.tap(root())
        val node = tree.nodes.getValue(tree.currentNodeId)
        for (invalid in listOf(node.copy(schemaVersion = 1), node.copy(bookkeeping = null),
            node.copy(transition = node.transition.copy(delta = null)))) {
            val bad = tree.copy(nodes = mapOf(node.id to invalid))
            assertFails { decode(TreeSync.records(generation(bad))) }
            assertNull(bad.stateAt(node.id))
        }
    }

    @Test fun incrementalValidationRejectsCorruptOffshootAndReparentedDescendants() {
        val before = decode(TreeSync.records(branched()))
        val node = before.tree.nodes.values.first()
        val corrupt = node.copy(transition = GameplayTransition())
        assertFails { TreeSync.apply(before, GameplayTreeDelta(listOf(TreeSyncRecord("node", node.id,
            GameWire.json.encodeToJsonElement(corrupt))))) }
        val changed = before.tree.copy(nodes = before.tree.nodes + (node.id to node.copy(parentId = f.id(999))))
        assertFails { TreeSync.apply(before, TreeSync.delta(before.tree, changed)) }
    }

    @Test fun flatRoundTripIncludesOffshootsHiddenArchiveMetadataFutureReferencesRedoAndRemovalParents() {
        val input = branched()
        val records = TreeSync.records(input).toList()
        val output = decode(records.asReversed().asSequence()) // order-independent records, not lazy branch order
        assertEquals(input, output)
        assertEquals(3, output.tree.nodes.size)
        assertEquals(input.tree.nodes.keys, records.filter { it.kind == "node" }.map { it.id }.toSet())
        assertTrue(output.branches.getValue(f.id(30)).getValue("hidden").jsonPrimitive.boolean)
        assertTrue(output.branches.getValue(f.id(30)).getValue("archived").jsonPrimitive.boolean)
        for (node in input.tree.nodes.keys + input.tree.initial.root.id) assertEquals(input.tree.stateAt(node), output.tree.stateAt(node))
        assertEquals(input.tree.initial.root.id, output.tree.resolveCursor(input.tree.removedParents.keys.single()))
        val text = records.joinToString("\n") { GameWire.json.encodeToString(TreeSyncRecord.serializer(), it) }
        assertEquals(1, Regex("\"board\"").findAll(text).count(), "not full-board snapshots per node")
        assertEquals(1, records.count { it.kind == "definition" })
    }

    @Test fun missingReferencesDefinitionsBranchesAncestryAndInvalidMetadataFailClosed() {
        val input = branched()
        val all = TreeSync.records(input).toList()
        for (kind in listOf("root", "current", "definition", "branch")) {
            assertFails { decode(all.filterNot { it.kind == kind }.asSequence()) }
        }
        val first = input.tree.nodes.values.single { it.author == f.a }
        assertFails { decode(all.filterNot { it.kind == "node" && it.id == first.id }.asSequence()) }
        assertFails { decode((all + all.first()).asSequence()) }
        assertFails { decode(all.asSequence().map { if (it.kind == "node") it.copy(version = 2) else it }) }
        assertFails { decode((all + TreeSyncRecord("unimplemented-control", "control", JsonObject(emptyMap()))).asSequence()) }
        assertFails { TreeSync.decode(all.asSequence(), f.id(999), f.id(2)) }
        assertFails { TreeSync.decode(all.asSequence(), f.id(1), f.id(999)) }
        assertFails { TreeSync.validate(input.copy(tree = input.tree.copy(removedParents = mapOf(f.id(900) to f.id(901), f.id(901) to f.id(900))))) }
        assertFails { TreeSync.validate(input.copy(tree = input.tree.copy(removedParents = mapOf(f.id(900) to f.id(999))))) }
        val queue = input.tree.redoByAccount.getValue(f.a)
        assertFails { TreeSync.validate(input.copy(tree = input.tree.copy(redoByAccount = mapOf(f.a to
            listOf(queue.single().copy(successorId = f.id(999))))))) }
        assertFails { TreeSync.validate(input.copy(tree = input.tree.copy(redoByAccount = mapOf(f.b to queue)))) }
    }

    @Test fun allPathsReplayBeyondUndoCeilingWithoutRecursionOrHistoryCap() {
        var tree = root()
        repeat(320) { n -> tree = f.play(tree, f.a, PlayAction.SetLife(f.a, (n + 21).toString()), 1000 + n) }
        val current = tree.currentNodeId
        tree = tree.copy(currentNodeId = tree.initial.root.id)
        val decoded = decode(TreeSync.records(generation(tree)))
        assertEquals(320, decoded.tree.nodes.size)
        assertEquals("340", decoded.tree.stateAt(current)!!.board.players.first().life)
        assertEquals(tree.initial.root.id, decoded.tree.currentNodeId)
    }

    @Test fun sameIdReparentingUsesNewGenerationAndDeletedCursorFollowsNearestSurvivor() {
        val first = f.tap(root())
        val second = f.tap(first, f.b, 12, n = 101)
        val before = decode(TreeSync.records(generation(second)))
        assertTrue(before.tree.stateAt(second.currentNodeId)!!.board.cards[0].tapped)
        val removed = assertNotNull(f.undo(second).tree)
        val after = decode(TreeSync.records(generation(removed)))
        assertEquals(second.currentNodeId, after.tree.resolveCursor(second.currentNodeId))
        assertFalse(after.tree.stateAt(second.currentNodeId)!!.board.cards[0].tapped)
        assertTrue(after.tree.stateAt(second.currentNodeId)!!.board.cards[1].tapped)
        assertEquals(first.initial.root.id, after.tree.resolveCursor(first.currentNodeId))
    }

    @Test fun corruptRecordedTransitionOnInactivePathIsNotHiddenByCorrectCurrentBoard() {
        val input = branched()
        val offshoot = input.tree.nodes.values.single { it.branchId == f.id(30) }
        val corrupt = offshoot.copy(transition = GameplayTransition())
        assertFails { TreeSync.validate(input.copy(tree = input.tree.copy(nodes = input.tree.nodes + (offshoot.id to corrupt)))) }
        assertFails { TreeSync.validate(input.copy(tree = input.tree.copy(currentNodeId = f.id(999)))) }
    }
}
