package org.asyncttwiz.protocol

import kotlinx.serialization.json.*
import kotlin.test.*

class HistoryRuntimeIntegrationTest {
    @Test fun preparedSingleReviewEndpointIsDisposableAcrossSelectiveEdits() {
        var tree = GameplayTree(gameplayFixture().withBattlefieldCards(gameplayId(10), gameplayId(11)))
        tree = play(tree, PlayAction.SetTapped(gameplayId(10), true))
        tree = play(tree, PlayAction.SetNote(gameplayId(11), "reviewed"), b)
        val selected = tree.currentNodeId
        tree = play(tree, PlayAction.SetNote(gameplayId(11), "later"), b)
        val review = assertNotNull(tree.prepareReview(selected))
        assertTrue(review.board.cards.single { it.id == gameplayId(10) }.tapped)
        assertSame(review, tree.stateAt(selected))
        val undo = command(tree, GameAction.Undo)
        val edited = assertNotNull(SelectiveUndo.change(tree, a, undo).tree)
        assertFalse(edited.stateAt(selected)!!.board.cards.single { it.id == gameplayId(10) }.tapped)
        assertTrue(tree.stateAt(selected)!!.board.cards.single { it.id == gameplayId(10) }.tapped)
        assertEquals(edited, roundtrip(edited))
    }
    private var request = 10000
    private val a = gameplayId(7)
    private val b = gameplayId(8)
    private fun command(tree: GameplayTree, action: GameAction, at: String? = null) = GameCommand(gameplayId(request++),
        GameScope(tree.initial.gameId, playId = tree.initial.id), action, gameplayId(80), "1",
        continuation = at?.let { Continuation(it, true) })
    private fun step(tree: GameplayTree, action: GameAction, actor: String = a, at: String? = null): GameplayTree =
        HistoryContinuation.change(tree, actor, command(tree, action, at)).let {
            assertEquals("accepted", it.outcome, "$action: ${it.reason}"); assertNotNull(it.tree) }
    private fun play(tree: GameplayTree, action: PlayAction, actor: String = a, at: String? = null) =
        step(tree, GameAction.Play(action), actor, at)
    private fun roundtrip(tree: GameplayTree): GameplayTree = TreeSync.decode(
        TreeSync.records(TreeSync.generation(tree)), tree.initial.gameId, tree.initial.id).tree.also {
             assertEquals(tree, it); assertEquals(tree.stateAt(tree.currentNodeId), it.stateAt(it.currentNodeId)) }

    @Test fun legacyTreeJsonDiscardsArchiveMetadataAndPreviouslyArchivedBranchesStillPlay() {
        val original = play(GameplayTree(gameplayFixture()), PlayAction.SetLife(a, "21"))
        val alternative = play(original, PlayAction.SetLife(a, "30"), at = original.initial.root.id)
        val legacy = JsonObject(GameWire.json.encodeToJsonElement(alternative).jsonObject + ("branchArchives" to buildJsonObject {
            put("archivedBranchIds", JsonArray(listOf(JsonPrimitive(original.currentBranchId))))
            put("lastControlByBranch", buildJsonObject { put(original.currentBranchId, gameplayId(900)) })
            put("records", buildJsonObject { put(gameplayId(900), buildJsonObject {
                put("actor", a)
                put("request", buildJsonObject { put("intent", buildJsonObject {
                    put("type", "archive"); put("branchId", original.currentBranchId)
                }) })
            }) })
        }))
        val decoded = GameWire.json.decodeFromString<GameplayTree>(legacy.toString())
        assertEquals(alternative, decoded)
        assertFalse("branchArchives" in GameWire.json.encodeToJsonElement(decoded).jsonObject)
        assertEquals(original.stateAt(original.currentNodeId), decoded.stateAt(original.currentNodeId))
        val continued = play(decoded, PlayAction.AdjustLife(a, "1"), at = original.currentNodeId)
        assertEquals("22", continued.stateAt(continued.currentNodeId)!!.board.players[0].life)
        val undone = step(continued, GameAction.Undo)
        assertEquals(original.currentNodeId, undone.currentNodeId)
        assertEquals(continued.nodes, step(undone, GameAction.Redo).nodes)
        roundtrip(continued)
    }

    @Test fun legacyFlatArchiveControlsAreIgnoredInCachedTreesAndDeltas() {
        val tree = play(GameplayTree(gameplayFixture()), PlayAction.SetLife(a, "21"))
        val generation = TreeSync.generation(tree)
        val legacy = listOf(
            TreeSyncRecord("archived", tree.currentBranchId, JsonPrimitive(true)),
            TreeSyncRecord("archive-control", tree.currentBranchId, JsonPrimitive(gameplayId(900))),
            TreeSyncRecord("archive-record", gameplayId(900), buildJsonObject { put("removedIntent", "archive") }))
        val decoded = TreeSync.decode(TreeSync.records(generation) + legacy.asSequence(), tree.initial.gameId, tree.initial.id)
        assertEquals(generation, decoded)
        assertTrue(TreeSync.records(decoded).none { it.kind.startsWith("archive") })
        val next = play(tree, PlayAction.AdjustLife(a, "1"))
        val delta = TreeSync.delta(tree, next)
        assertEquals(next, TreeSync.apply(decoded, delta.copy(upserts = delta.upserts + legacy)).tree)
        assertEquals(next, TreeSync.apply(decoded, delta.copy(removals = legacy.map { GameplayTreeKey(it.kind, it.id) })).tree)
    }

    @Test fun retainedReferencesProtectExactInactiveMaterializationsWithoutWholeBoardLocks() {
        var tree = GameplayTree(gameplayFixture().withBattlefieldCards(gameplayId(10), gameplayId(11)))
        tree = play(tree, PlayAction.SetTapped(gameplayId(10), true))
        val tap = tree.currentNodeId
        tree = play(tree, PlayAction.SetNote(gameplayId(11), "independent"), b)
        val independent = tree.currentNodeId
        tree = play(tree, PlayAction.EventComment(EventReference(tree.initial.id, tap, tree.contextVersion(tap)), "about tap"), b)
        assertNotNull(roundtrip(tree).stateAt(tree.currentNodeId))
        val blocked = HistoryContinuation.change(tree, a, command(tree, GameAction.Undo))
        assertEquals("replayBlocked", blocked.reason); assertNull(blocked.tree)
        // An unrelated root reference preserves its original materialization under selective edits.
        var unrelated = GameplayTree(gameplayFixture().withBattlefieldCards(gameplayId(10), gameplayId(11)))
        unrelated = play(unrelated, PlayAction.SetTapped(gameplayId(10), true))
        unrelated = play(unrelated, PlayAction.EventComment(EventReference(unrelated.initial.id, unrelated.initial.root.id), "root"), b)
        val removed = step(unrelated, GameAction.Undo)
        assertEquals(1, removed.nodes.size); roundtrip(removed)
        // Inactive references are real retained contexts, not the current board relabelled by ID.
        val fork = play(tree, PlayAction.SetLife(a, "99"), at = tree.initial.root.id)
        val annotated = play(fork, PlayAction.Ping(EventReference(fork.initial.id, independent,
            fork.contextVersion(independent)), PingTarget(PingTargetKind.CARD, gameplayId(11))))
        roundtrip(annotated)
        assertEquals("missingReference", HistoryContinuation.change(annotated, a, command(annotated,
            GameAction.Play(PlayAction.EventComment(EventReference(annotated.initial.id, independent, "0"), "stale")))).reason)
        // A reference recorded on another offshoot is not on the target's linear suffix.
        var crossBranch = play(GameplayTree(gameplayFixture().withBattlefieldCards(gameplayId(10), gameplayId(11))), PlayAction.SetTapped(gameplayId(10), true))
        val referenced = crossBranch.currentNodeId
        crossBranch = play(crossBranch, PlayAction.EventComment(EventReference(crossBranch.initial.id, referenced),
            "inactive offshoot dependency"), b, crossBranch.initial.root.id)
        crossBranch = play(crossBranch, PlayAction.SetNote(gameplayId(11), "continue referenced branch"), b, referenced)
        val refusal = HistoryContinuation.change(crossBranch, a, command(crossBranch, GameAction.Undo))
        assertEquals("referenceBlocked", refusal.reason); assertEquals(referenced, refusal.targetId)
        for (branch in crossBranch.branches.values) assertNotNull(roundtrip(crossBranch).stateAt(branch.firstNodeId))
    }

    @Test fun retainedHeadsAndDisplacedVersionsSurviveUndoRedoAndContinuation() {
        var tree = GameplayTree(gameplayFixture().withBattlefieldCards(gameplayId(11)))
        tree = play(tree, PlayAction.SetLife(a, "21"))
        val oldHead = tree.currentNodeId
        val rootBranch = tree.currentBranchId
        val selectionBeforeUndo = tree.selectionControlId
        tree = step(tree, GameAction.Undo)
        assertEquals(tree.initial.root.id, tree.branchHeads[rootBranch])
        tree = step(tree, GameAction.Redo)
        assertEquals(oldHead, tree.branchHeads[rootBranch])
        assertEquals(selectionBeforeUndo, tree.selectionControlId)
        tree = play(tree, PlayAction.SetNote(gameplayId(11), "alternative"), at = tree.initial.root.id)
        val forkControl = tree.selectionControlId
        assertNotNull(forkControl)
        assertEquals(oldHead, tree.branchHeads[rootBranch])
        val branch = tree.branches.values.single()
        assertEquals(oldHead, branch.displacedNodeId)
        assertEquals(tree.materializationId(oldHead), branch.displacedVersion)
        val continued = play(tree, PlayAction.AdjustLife(a, "1"), at = oldHead)
        assertNotEquals(forkControl, continued.selectionControlId)
        assertEquals("22", continued.stateAt(continued.currentNodeId)!!.board.players[0].life)
        roundtrip(continued)
    }

    @Test fun failedAndNoopHistoricalInputsPreserveStacksFirstSuccessForksAndFullDeltaKeepsOffshoots() {
        var tree = GameplayTree(gameplayFixture().withBattlefieldCards(gameplayId(10), gameplayId(11)))
        tree = play(tree, PlayAction.SetLife(a, "21"))
        tree = play(tree, PlayAction.SetNote(gameplayId(11), "B"), b)
        tree = step(tree, GameAction.Undo)
        val before = roundtrip(tree)
        val noOp = command(tree, GameAction.Play(PlayAction.SetTapped(gameplayId(10), false)), tree.initial.root.id)
        assertEquals("noOp", HistoryContinuation.change(tree, b, noOp).outcome)
        val failure = command(tree, GameAction.Play(PlayAction.SetNote(gameplayId(999), "missing")), tree.initial.root.id)
        assertEquals("rejected", HistoryContinuation.change(tree, b, failure).outcome)
        assertEquals(before, tree)
        val next = play(tree, PlayAction.SetTapped(gameplayId(10), true), b, tree.initial.root.id)
        assertTrue(next.redoByAccount.isEmpty()); assertEquals(1, next.branches.size)
        val delta = TreeSync.delta(tree, next)
        assertTrue(delta.upserts.none { it.kind == "root" })
        assertEquals(next, TreeSync.apply(TreeSync.generation(tree), delta).tree)
        assertNotNull(roundtrip(next).stateAt(tree.currentNodeId))
    }

    @Test fun ordinaryRetainedReviewAndUndoHaveNoAncestryOrPayloadBudget() {
        val initial = gameplayFixture()
        var state = initial
        val nodes = linkedMapOf<String, GameplayMutation>()
        repeat(2055) {
            val change = GameplayRules.change(state, a, command(GameplayTree(initial), GameAction.Play(PlayAction.AdjustLife(a, "1"))))
            state = assertNotNull(change.state); val node = assertNotNull(change.mutation); nodes[node.id] = node
        }
        val tree = GameplayTree(initial, nodes, state.currentNodeId,
            branchHeads = mapOf(initial.root.branchId to state.currentNodeId))
        assertEquals(state, roundtrip(tree).stateAt(state.currentNodeId))
        assertNotNull(HistoryContinuation.change(tree, a, command(tree, GameAction.Undo)).tree)
        val small = play(GameplayTree(initial), PlayAction.CloneToken(gameplayId(10)))
        val before = TreeSync.records(TreeSync.generation(small)).toList()
        assertNotNull(SelectiveUndo.change(small, a, command(small, GameAction.Undo)).tree)
        assertEquals(before, TreeSync.records(TreeSync.generation(small)).toList())
    }
}
