package org.asyncttwiz.protocol

import kotlin.test.*

class PlayBatchTest {
    private val f = UndoFixture
    private fun command(actions: List<PlayAction>) = f.command(GameAction.PlayBatch(actions), 700)

    @Test fun wireSizeAndRoundTrip() {
        val action = PlayAction.AdjustLife(f.a, "1")
        for (size in listOf(0, 1, 100, 101)) {
            val command = command(List(size) { action })
            assertEquals(size in 1..100, GameWire.valid(command))
            assertEquals(command, GameWire.decodeClient(GameWire.json.encodeToString(GameClientMessage.serializer(), command)))
        }
    }

    @Test fun successIsConsecutiveDeterministicAndUndoRedoIsOneInvocation() {
        val tree = GameplayTree(f.root())
        val command = command(listOf(PlayAction.SetTapped(f.id(9), true), PlayAction.SetTapped(f.id(12), true)))
        val next = assertNotNull(HistoryContinuation.change(tree, f.a, command).tree)
        val members = (command.action as GameAction.PlayBatch).actions.mapIndexed { i, action -> PlayBatches.member(command, i, action) }
        val nodes = members.map { next.nodes.getValue(CommandIds.uuidV5(it.requestId, "board-node")) }
        assertEquals(listOf(tree.currentNodeId, nodes[0].id), nodes.map { it.parentId })
        assertEquals(members.map { it.requestId }, nodes.map { it.requestId })
        assertTrue(nodes.all { it.invocationId == command.requestId })
        assertEquals(CommandIds.uuidV5(command.requestId, "member/0"), nodes[0].requestId)
        assertEquals(next.nodes, assertNotNull(HistoryContinuation.change(tree, f.a, command).tree).nodes)
        val delta = TreeSync.delta(tree, next)
        assertEquals(nodes.map { it.id }, delta.upserts.filter { it.kind == "node" }.map { it.id })
        assertEquals(next.nodes, TreeSync.apply(TreeSync.generation(tree), delta).tree.nodes)
        val undone = assertNotNull(f.undo(next).tree)
        assertEquals(tree.initial.board, undone.stateAt(undone.currentNodeId)!!.board)
        assertTrue(undone.nodes.isEmpty())
        assertEquals(next.nodes, assertNotNull(f.redo(undone).tree).nodes)
    }

    @Test fun firstRefusalPublishesNothingEvenAfterSuccessfulMembers() {
        val tree = GameplayTree(f.root())
        val before = tree.stateAt(tree.currentNodeId)
        val result = HistoryContinuation.change(tree, f.a, command(listOf(
            PlayAction.AdjustLife(f.a, "1"), PlayAction.SetTapped(f.id(999), true), PlayAction.AdjustLife(f.a, "5"))))
        assertEquals("missingReference", result.reason)
        assertNull(result.tree)
        assertTrue(tree.nodes.isEmpty())
        assertEquals(before, tree.stateAt(tree.currentNodeId))
    }

    @Test fun hundredMembersRemainUndoableWithoutChangingLegacyBudget() {
        val tree = GameplayTree(f.root())
        val next = assertNotNull(HistoryContinuation.change(tree, f.a,
            command(List(100) { PlayAction.AdjustLife(f.a, "1") })).tree)
        val undone = assertNotNull(f.undo(next).tree)
        assertTrue(undone.nodes.isEmpty())
        assertEquals(next.nodes, assertNotNull(f.redo(undone).tree).nodes)
    }

    @Test fun edgeIntentIsResolvedAgainForEveryMember() {
        val root = gameplayFixture()
        val action = PlayAction.MoveCard(gameplayId(10), MoveSource(gameplayId(20), edge = MoveSourceEdge.TOP, takeFromEdge = true),
            MoveDestination(gameplayId(21), MoveInsertion.UNORDERED))
        val command = GameCommand(gameplayId(700), GameScope(root.gameId, playId = root.id),
            GameAction.PlayBatch(List(3) { action }), gameplayId(80), "1")
        val next = assertNotNull(HistoryContinuation.change(GameplayTree(root), gameplayId(7), command).tree)
        assertEquals((10..12).map(::gameplayId), next.stateAt(next.currentNodeId)!!.board.containers.single { it.zone == PlayZone.HAND }.cards)
        assertEquals((10..12).map(::gameplayId), next.nodes.values.map { (it.action as PlayAction.MoveCard).cardId })
    }

    @Test fun batchUndoRetainsALaterOpponentsRelativeChange() {
        val initial = GameplayTree(f.root())
        val batch = assertNotNull(HistoryContinuation.change(initial, f.a,
            command(List(4) { PlayAction.AdjustLife(f.a, "1") })).tree)
        val next = f.play(batch, f.b, PlayAction.AdjustLife(f.b, "10"), 800)
        val undo = f.undo(next)
        val undone = assertNotNull(undo.tree, undo.reason)
        assertEquals("20", undone.stateAt(undone.currentNodeId)!!.board.players.single { it.accountId == f.a }.life)
        assertEquals("30", undone.stateAt(undone.currentNodeId)!!.board.players.single { it.accountId == f.b }.life)
    }

    @Test fun singleMemberAndNoOpsUseOrdinaryPlaySemantics() {
        val tree = GameplayTree(f.root())
        val action = PlayAction.AdjustLife(f.a, "1")
        val batch = command(listOf(action))
        assertEquals(tree.play(f.a, PlayBatches.member(batch, 0, action)).tree?.nodes,
            HistoryContinuation.change(tree, f.a, batch).tree?.nodes)
        assertEquals("noOp", HistoryContinuation.change(tree, f.a, command(listOf(PlayAction.AdjustLife(f.a, "0")))).outcome)
    }
}
