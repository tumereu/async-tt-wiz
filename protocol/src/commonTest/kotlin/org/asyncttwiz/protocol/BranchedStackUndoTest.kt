package org.asyncttwiz.protocol

import kotlin.test.*

class BranchedStackUndoTest {
    private val f = UndoFixture
    private var request = 20000
    private fun command(tree: GameplayTree, action: GameAction, at: String? = null) =
        f.command(action, request++).copy(scope = GameScope(tree.initial.gameId, playId = tree.initial.id),
            continuation = at?.let { Continuation(it, true) })
    private fun step(tree: GameplayTree, action: GameAction, actor: String = f.a, at: String? = null): GameplayTree {
        val change = HistoryContinuation.change(tree, actor, command(tree, action, at))
        assertNull(change.reason, "$action: ${change.reason}")
        return change.tree ?: tree
    }
    private fun play(tree: GameplayTree, action: PlayAction, actor: String = f.a, at: String? = null) =
        step(tree, GameAction.Play(action), actor, at)
    private fun cold(tree: GameplayTree) = GameWire.json.decodeFromString(GameplayTree.serializer(),
        GameWire.json.encodeToString(GameplayTree.serializer(), tree))

    private fun root(): PlayState {
        val definitions = List(6) { index ->
            val definition = CardDefinition("Synthetic $index", listOf(DefinitionFace(f.id(40 + index),
                "Face $index", corner = "2/3", rulesText = "First paragraph\n\nSecond paragraph")))
            SubmittedDefinition(DeckJson.digest(definition), definition)
        }
        val cards = listOf(f.a, f.b).flatMapIndexed { player, actor -> List(60) { index ->
            val definition = definitions[index % definitions.size]
            PlayCard(f.id(1000 + player * 60 + index), definition.digest, actor, f.id(20 + player * 5),
                definition.definition.faces.single().faceId, corner = "2/3")
        } }
        val containers = listOf(f.a, f.b).flatMapIndexed { player, actor ->
            listOf(PlayZone.LIBRARY, PlayZone.HAND, PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD, PlayZone.EXILE)
                .mapIndexed { index, zone -> PlayContainer(f.id(20 + player * 5 + index), zone, actor,
                    if (zone == PlayZone.LIBRARY) cards.filter { it.owner == actor }.map { it.id } else emptyList()) }
        } + PlayContainer(f.id(30), PlayZone.STACK)
        return f.root().copy(board = BoardState(listOf(PlayPlayer(f.a), PlayPlayer(f.b)), cards, containers,
            TurnState(f.b)), definitions = definitions)
    }

    /** Reconstructs the 42 retained mutations and two continuations read from dev play
     * 9e8007c4-179d-58d0-960f-c5c50ace896c. Synthetic cards replace catalog/private data.
     * Includes its historical ping: validating that prefix also reconstructs the referenced board. */
    private fun beforeStackActions(branched: Boolean = true): GameplayTree {
        var tree = GameplayTree(root())
        fun state() = assertNotNull(tree.stateAt(tree.currentNodeId))
        fun act(action: PlayAction, actor: String = f.a) { tree = play(tree, action, actor) }
        fun draw(actor: String, library: Int, hand: Int) {
            LibraryActions.draw(state().board, f.id(library), f.id(hand), 1).forEach { act(it, actor) }
        }
        act(PlayAction.ShuffleLibrary(f.id(25), RandomV1.encodeSeed(ByteArray(32))), f.b)
        repeat(7) { draw(f.b, 25, 26) }
        val bobCards = state().board.containers.single { it.id == f.id(26) }.cards
        act(PlayAction.MoveCard(bobCards[0], MoveSource(f.id(26)),
            MoveDestination(f.id(27), MoveInsertion.EMPTY, BoardPosition(2, 1))), f.b)
        act(PlayAction.SetTapped(bobCards[0], true), f.b)
        repeat(3) { act(PlayAction.AdjustLife(f.b, "-1"), f.b) }
        act(PlayAction.AdjustCounter(CounterTarget.PLAYER, f.b, CounterColor.RED, "1"), f.b)
        repeat(2) { act(PlayAction.NextStep, f.b) }
        act(PlayAction.EndTurn, f.b)
        val junction = tree.currentNodeId
        act(PlayAction.CreateToken(TokenCatalog.blank))
        val displaced = tree.currentNodeId
        if (branched) {
            tree = play(tree, PlayAction.AdjustLife(f.a, "1"), at = junction)
            tree = step(tree, GameAction.MakeCurrent(displaced))
            assertEquals(1, tree.branches.size)
            assertEquals(2, tree.branchHeads.size)
        }
        act(PlayAction.Combat(CombatAction.Open))
        val token = state().board.cards.single { it.token }.id
        act(PlayAction.Combat(CombatAction.Declare(state().board.combat!!.id, token)))
        act(PlayAction.SetTapped(token, true))
        act(PlayAction.MoveCard(bobCards[1], MoveSource(f.id(26)), MoveDestination(f.id(30), MoveInsertion.TOP),
            GameplayRules.stackDigest(state())), f.b)
        val reference = EventReference(tree.initial.id, tree.currentNodeId, tree.contextVersion(tree.currentNodeId))
        act(PlayAction.Ping(reference, PingTarget(PingTargetKind.CARD, bobCards[0])))
        repeat(13) { act(PlayAction.AdjustLife(f.a, if (it % 2 == 0) "1" else "-1")) }
        draw(f.a, 20, 21)
        val card = state().board.containers.single { it.id == f.id(21) }.cards.single()
        act(PlayAction.MoveCard(card, MoveSource(f.id(21)),
            MoveDestination(f.id(22), MoveInsertion.EMPTY, BoardPosition(3, 2))))
        return tree
    }

    private fun ontoStack(tree: GameplayTree, projection: Boolean = false): GameplayTree {
        val state = tree.stateAt(tree.currentNodeId)!!
        val card = state.board.cards.single { !it.token && it.containerId == f.id(22) }
        return play(tree, if (projection) PlayAction.Stack(StackAction.AddEffect(card.id, GameplayRules.stackDigest(state)))
            else PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement),
                MoveDestination(f.id(30), MoveInsertion.TOP), GameplayRules.stackDigest(state)))
    }

    private fun assertUndoRedo(before: GameplayTree, after: GameplayTree) {
        val target = after.currentNodeId
        // Both direct eligibility and the server's shared control path must accept cold SQL/wire data.
        val direct = SelectiveUndo.change(cold(after), f.a, command(after, GameAction.Undo))
        assertEquals(target, direct.targetId, direct.reason)
        assertNotNull(direct.tree, direct.reason)
        val removed = step(cold(after), GameAction.Undo)
        assertEquals(before.nodes, removed.nodes)
        assertEquals(before.stateAt(before.currentNodeId)!!.board, removed.stateAt(removed.currentNodeId)!!.board)
        assertEquals(before.currentNodeId, removed.resolveCursor(target))
        assertEquals(after.nodes, step(cold(removed), GameAction.Redo).nodes)
        for ((id, node) in before.nodes) assertEquals(node, removed.nodes[id])
    }

    @Test fun reconstructed42MutationGameUndoesLatestOwnStackMoveAfterMakeCurrent() {
        var tree = ontoStack(beforeStackActions())
        var state = tree.stateAt(tree.currentNodeId)!!
        val aliceEntry = CombatStackRules.stack(state.board).cards.first()
        for (action in StackQuickActions.actions(state, aliceEntry, PlayZone.BATTLEFIELD)) tree = play(tree, action)
        val before = tree
        state = tree.stateAt(tree.currentNodeId)!!
        val bobEntry = CombatStackRules.stack(state.board).cards.single()
        for (action in StackQuickActions.actions(state, bobEntry, PlayZone.GRAVEYARD)) tree = play(tree, action)
        assertEquals(42, tree.nodes.size)
        assertEquals(2, tree.branchHeads.size)
        assertUndoRedo(before, tree)
    }

    @Test fun stackQuickActionsAndTextMarksUndoInStraightHistoryAndAfterBranchSwitch() {
        for (branched in listOf(false, true)) for (projection in listOf(false, true)) {
            val onStack = ontoStack(beforeStackActions(branched), projection)
            val state = onStack.stateAt(onStack.currentNodeId)!!
            val entry = CombatStackRules.stack(state.board).cards.first()
            for (zone in listOf(PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD)) {
                var tree = onStack
                val actions = StackQuickActions.actions(state, entry, zone)
                var mutations = 0
                for (action in actions) {
                    val next = play(tree, action)
                    if (next === tree) continue // A default corner reset is a no-op, not another mutation.
                    mutations++
                    assertUndoRedo(tree, next) // Each individual mutation, not an atomic invocation Undo.
                    tree = next
                }
                assertEquals(if (projection && zone == PlayZone.GRAVEYARD) 2 else 1, mutations)
                repeat(mutations) { tree = step(tree, GameAction.Undo) }
                assertEquals(onStack.nodes, tree.nodes)
            }
            val marked = play(onStack, PlayAction.Stack(StackAction.MarkText(entry, 1)))
            assertUndoRedo(onStack, marked)
            val cleared = play(marked, PlayAction.Stack(StackAction.MarkText(entry, null)))
            assertUndoRedo(marked, cleared)
            val dependent = play(marked, PlayAction.Stack(StackAction.MarkText(entry, 0)), f.b)
            assertEquals("replayBlocked", SelectiveUndo.change(cold(dependent), f.a,
                command(dependent, GameAction.Undo)).reason)
        }
    }

    @Test fun realSizedBranchedGameStillChecksEverySuccessorAndRejects129() {
        val target = ontoStack(beforeStackActions())
        var tree = target
        repeat(128) { tree = play(tree, PlayAction.AdjustLife(f.b, "1"), f.b) }
        val result = SelectiveUndo.change(cold(tree), f.a, command(tree, GameAction.Undo))
        val removed = assertNotNull(result.tree, result.reason)
        assertEquals(target.currentNodeId, result.targetId)
        assertEquals(tree.nodes.keys - target.currentNodeId, removed.nodes.keys)
        assertNotNull(removed.stateAt(removed.currentNodeId))
        val tooLong = play(tree, PlayAction.AdjustLife(f.b, "1"), f.b)
        val refused = SelectiveUndo.change(cold(tooLong), f.a, command(tooLong, GameAction.Undo))
        assertEquals("suffixUnavailable", refused.reason)
        assertNull(refused.tree)
    }
}
