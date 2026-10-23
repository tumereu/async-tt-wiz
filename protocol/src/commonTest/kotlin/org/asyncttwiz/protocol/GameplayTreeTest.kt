package org.asyncttwiz.protocol

import kotlin.test.*

internal object UndoFixture {
    fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    val a = id(7)
    val b = id(8)
    fun root() = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
        BoardState(listOf(PlayPlayer(a), PlayPlayer(b)),
            listOf(PlayCard(id(9), "definition", a, id(10), id(11), note = "preserved"),
                PlayCard(id(12), "definition", b, id(10), id(11))),
            listOf(PlayContainer(id(10), PlayZone.LIBRARY, a, listOf(id(9), id(12)))), TurnState(a)),
        emptyList(), emptyList(), InitialStarter(StarterChoice(a), a))
    fun command(action: GameAction, n: Int) = GameCommand(id(n), GameScope(id(1), playId = id(2)), action, id(13), "1")
    fun play(tree: GameplayTree, actor: String, action: PlayAction, n: Int): GameplayTree =
        assertNotNull(tree.play(actor, command(GameAction.Play(action), n)).tree)
    fun tap(tree: GameplayTree, actor: String = a, card: Int = 9, value: Boolean = true, n: Int = 100) =
        play(tree, actor, PlayAction.SetTapped(id(card), value), n)
    fun undo(tree: GameplayTree, actor: String = a, n: Int = 900, limits: SelectiveUndoLimits = SelectiveUndoLimits()) =
        SelectiveUndo.change(tree, actor, command(GameAction.Undo, n), limits)
    fun redo(tree: GameplayTree, actor: String = a, n: Int = 901, limits: SelectiveUndoLimits = SelectiveUndoLimits()) =
        SelectiveUndo.change(tree, actor, command(GameAction.Redo, n), limits)
}

class GameplayTreeTest {
    private val f = UndoFixture
    @Test fun sameTreeRewiringStableNodesAndCursorFallback() {
        val root = GameplayTree(f.root())
        val first = f.tap(root)
        val a = first.currentNodeId
        val second = f.tap(first, f.b, 12, n = 101)
        val b = second.currentNodeId
        val removed = assertNotNull(f.undo(second).tree)
        assertEquals(setOf(b), removed.nodes.keys)
        assertEquals(root.currentNodeId, removed.nodes.getValue(b).parentId)
        assertEquals(second.nodes.getValue(b).copy(parentId = root.currentNodeId), removed.nodes.getValue(b))
        assertEquals(b, removed.currentNodeId)
        assertEquals(root.currentNodeId, removed.resolveCursor(a))
        assertEquals(b, removed.resolveCursor(b))
        assertNull(removed.resolveCursor(f.id(999)))
        val board = assertNotNull(removed.stateAt(b)).board
        assertFalse(board.cards[0].tapped); assertTrue(board.cards[1].tapped)
        assertEquals("preserved", board.cards[0].note)
        assertEquals(second.initial.board.containers, board.containers)
        assertEquals(second.initial.board.turn, board.turn)
        val restored = assertNotNull(f.redo(removed).tree)
        assertEquals(second.nodes, restored.nodes)
        assertEquals(second.currentNodeId, restored.currentNodeId)
        assertEquals(second.stateAt(b), restored.stateAt(b))
        assertTrue(restored.removedParents.isEmpty())
        assertEquals(root, GameplayTree(f.root())) // immutable input wasn't edited
    }

    @Test fun multipleDeletedCursorsFollowOriginalParentChain() {
        val first = f.tap(GameplayTree(f.root()))
        val second = f.tap(first, card = 12, n = 101)
        val once = assertNotNull(f.undo(second).tree)
        val twice = assertNotNull(f.undo(once).tree)
        assertEquals(f.root().root.id, twice.resolveCursor(second.currentNodeId))
        assertEquals(f.root().root.id, twice.currentNodeId)
        assertTrue(twice.nodes.isEmpty())
    }

    @Test fun activationClearsBothQueuesOnlyOnRealSwitchAndDoesNotRestoreOldStacks() {
        val own = f.tap(GameplayTree(f.root()))
        val other = f.tap(own, f.b, 12, n = 101)
        val removedA = assertNotNull(f.undo(other).tree)
        val removedBoth = assertNotNull(f.undo(removedA, f.b).tree)
        assertEquals(setOf(f.a, f.b), removedBoth.redoByAccount.keys)
        assertEquals("noOp", removedBoth.activate(removedBoth.currentNodeId).outcome)
        assertEquals("missingReference", removedBoth.activate(f.id(999)).reason)
        val restoredB = assertNotNull(f.redo(removedBoth, f.b).tree)
        val switched = assertNotNull(restoredB.activate(f.root().root.id).tree)
        assertTrue(switched.redoByAccount.isEmpty())
        assertEquals(restoredB.nodes, switched.nodes)
        assertEquals("noCandidate", f.redo(switched).reason)
    }

    @Test fun codecIncludesTopologyRemovedParentsAndAccountQueuesWithoutBoardSnapshotsPerNode() {
        val original = f.tap(GameplayTree(f.root()))
        val removed = assertNotNull(f.undo(original).tree)
        val encoded = GameWire.json.encodeToString(GameplayTree.serializer(), removed)
        assertEquals(removed, GameWire.json.decodeFromString(GameplayTree.serializer(), encoded))
        assertEquals(1, Regex("\"board\"").findAll(encoded).count())
    }

    @Test fun unknownSchemaAndUnavailableMovementMappingFailClosed() {
        val tree = f.tap(GameplayTree(f.root()))
        val node = tree.nodes.values.single()
        assertNull(tree.copy(nodes = mapOf(node.id to node.copy(schemaVersion = 2))).stateAt(node.id))
        val movement = node.copy(action = PlayAction.MoveCard(f.id(9), MoveSource(f.id(10)),
            MoveDestination(f.id(10), MoveInsertion.BOTTOM)))
        assertNull(tree.copy(nodes = mapOf(node.id to movement)).stateAt(node.id))
    }
}
