package org.asyncttwiz.protocol

import kotlin.test.*

/** Independent regressions for the movement/Manual conflict resolution, not a capability audit. */
class GameplayMergeVerificationTest {
    @Test fun battlefieldLeaveRetainsFrontFaceResetOutputAndInverseWithoutFollowupEdits() {
        val original = gameplayFixture().withBattlefieldCards(gameplayId(10))
        val card = original.board.cards.first()
        val face = gameplayId(31)
        val definition = original.definitions.single().definition.let {
            it.copy(faces = it.faces + DefinitionFace(face, "Other", corner = "7/9"))
        }
        var state = original.copy(definitions = listOf(SubmittedDefinition(card.definition, definition)))
        state = gameplayChange(state, PlayAction.SetFace(card.id, face), 200).state!!
        val beforeEntry = state
        val move = PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement),
            MoveDestination(state.board.containers[3].id, MoveInsertion.UNORDERED), faceDown = true)
        assertEquals(listOf(move), GameplayRules.moveActions(state, move))
        val sequence = listOf(move, PlayAction.InspectFace(card.id, gameplayId(30), move.destination.containerId))
        val changes = sequence.mapIndexed { index, action ->
            val before = state
            gameplayChange(before, action, 201 + index).also { change ->
                assertEquals("accepted", change.outcome)
                assertEquals(change, GameplayHistory.replay(before, change.mutation!!))
                assertEquals(before.board, GameplayHistory.reverse(change.state!!, change.mutation))
                state = change.state
            }
        }
        val moved = changes[0].state!!.board.cards.first()
        assertEquals(card.copy(containerId = move.destination.containerId, placement = null, corner = "3/5",
            counters = emptyMap(), covered = false, note = ""), moved)
        val reset = changes[0]
        assertTrue(GameplayFieldChange(card.id, GameplayField.FACE, face, gameplayId(30)) in reset.mutation!!.transition.fields)
        assertEquals("replayBlocked", GameplayHistory.replay(beforeEntry.copy(definitions = emptyList()), reset.mutation).reason)
        assertEquals(listOf(gameplayId(7)), changes[1].mutation!!.transition.disclosures.single().viewers)
        assertEquals(state.board, changes[0].state!!.board) // Viewing logs exposure, not a field change.
        assertNotEquals(changes[0].state!!.currentGroup!!.id, state.currentGroup!!.id)
    }

    @Test fun liveManualUsesCurrentValueWhileReplayAndStackAdditionKeepTheirDifferentGuards() {
        val root = gameplayFixture().withBattlefieldCards(gameplayId(10)); val card = root.board.cards.first()
        val stack = root.board.containers.last()
        val manual = PlayAction.AdjustCounter(CounterTarget.CARD, card.id, CounterColor.RED, "1")
        val captured = gameplayChange(root, manual, 220)
        val remoteChange = gameplayChange(root, PlayAction.SetCounter(CounterTarget.CARD, card.id, CounterColor.RED, "8"), 221)
        val remote = remoteChange.state!!
        val live = gameplayChange(remote, manual, 222)
        assertEquals(GameplayFieldChange(card.id, GameplayField.COUNTER, "8", "9", CounterColor.RED),
            live.mutation!!.transition.fields.single())
        assertEquals("replayBlocked", GameplayHistory.replay(remote, captured.mutation!!).reason)
        val addition = PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement),
            MoveDestination(stack.id, MoveInsertion.TOP), GameplayRules.stackDigest(root), faceDown = true)
        val added = gameplayChange(live.state!!, addition, 223)
        assertEquals("accepted", added.outcome) // Manual edits did not change semantic Stack contents.
        assertTrue(added.state!!.board.cards.first().counters.isEmpty())
        // Stack order is a merged transition: traverse the complete retained tree, not the scalar inverse helper.
        val addedTree = GameplayTree(root,
            listOf(remoteChange.mutation!!, live.mutation, added.mutation!!).associateBy { it.id }, added.state.currentNodeId)
        assertEquals(added.state, addedTree.stateAt(addedTree.currentNodeId))
        val undo = GameCommand(gameplayId(226), GameScope(root.gameId, playId = root.id),
            GameAction.Undo, gameplayId(80), "1")
        val withoutAdded = SelectiveUndo.change(addedTree, gameplayId(7), undo)
        assertEquals(added.mutation.id, withoutAdded.targetId)
        val restoredLive = assertNotNull(withoutAdded.tree)
        assertEquals(live.state.board, assertNotNull(restoredLive.stateAt(restoredLive.currentNodeId)).board)
        val later = addition.copy(cardId = root.board.cards[1].id, source = MoveSource(root.board.cards[1].containerId))
        assertEquals("stackConflict", gameplayChange(added.state, later, 224).reason)
        val fresh = gameplayChange(added.state, later.copy(expectedStackDigest = GameplayRules.stackDigest(added.state)), 225)
        assertEquals("accepted", fresh.outcome)
        assertEquals(fresh, GameplayHistory.replay(added.state, fresh.mutation!!))
        val freshTree = addedTree.copy(nodes = addedTree.nodes + (fresh.mutation!!.id to fresh.mutation),
            currentNodeId = fresh.state!!.currentNodeId)
        assertEquals(fresh.state, freshTree.stateAt(freshTree.currentNodeId))
        val withoutFresh = SelectiveUndo.change(freshTree, gameplayId(7), undo.copy(requestId = gameplayId(227)))
        assertEquals(fresh.mutation.id, withoutFresh.targetId)
        val restoredAdded = assertNotNull(withoutFresh.tree)
        assertEquals(added.state.board, assertNotNull(restoredAdded.stateAt(restoredAdded.currentNodeId)).board)
    }
}
