package org.asyncttwiz.protocol

import kotlin.test.*

/** Independent regressions for the movement/Manual conflict resolution, not a capability audit. */
class GameplayMergeVerificationTest {
    @Test fun entryConstituentsReadInterleavedFaceAndRetainConcreteResetOutputAndInverse() {
        val original = gameplayFixture()
        val card = original.board.cards.first()
        val face = gameplayId(31)
        val definition = original.definitions.single().definition.let {
            it.copy(faces = it.faces + DefinitionFace(face, "Other", corner = "7/9"))
        }
        var state = original.copy(definitions = listOf(SubmittedDefinition(card.definition, definition)))
        state = gameplayChange(state, PlayAction.MoveCard(card.id, MoveSource(card.containerId),
            MoveDestination(state.board.containers[1].id, MoveInsertion.UNORDERED)), 200).state!!
        val beforeEntry = state
        val move = PlayAction.MoveCard(card.id, MoveSource(state.board.containers[1].id),
            MoveDestination(state.board.containers[3].id, MoveInsertion.UNORDERED))
        assertEquals(listOf(move, PlayAction.ClearCardCounter(card.id, CounterColor.RED),
            PlayAction.ResetCorner(card.id)), GameplayRules.moveActions(state, move))
        val sequence = listOf(move, PlayAction.ClearCardCounter(card.id, CounterColor.RED),
            PlayAction.SetFace(card.id, face), PlayAction.ResetCorner(card.id),
            PlayAction.SetCovered(card.id, false), PlayAction.InspectFace(card.id, gameplayId(30), move.destination.containerId))
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
        assertEquals(beforeEntry.board.cards.first().copy(containerId = move.destination.containerId), moved)
        val reset = changes[3]
        assertEquals(listOf(GameplayRead(card.id, GameplayField.FACE, face)), reset.mutation!!.transition.reads)
        assertEquals(GameplayFieldChange(card.id, GameplayField.CORNER, "custom", "7/9"), reset.mutation.transition.fields.single())
        val resetInput = changes[2].state!!
        assertEquals("7/9", GameplayHistory.replay(resetInput.copy(definitions = emptyList()), reset.mutation)
            .state!!.board.cards.first().corner)
        assertEquals("replayBlocked", GameplayHistory.replay(changes[1].state!!, reset.mutation).reason)
        assertEquals(listOf(gameplayId(7), gameplayId(8)), changes[4].mutation!!.transition.disclosures.single().viewers)
        assertEquals(listOf(gameplayId(7)), changes[5].mutation!!.transition.disclosures.single().viewers)
        assertEquals(state.board, changes[4].state!!.board) // Viewing logs exposure, not a field change.
        assertNotEquals(changes[4].state!!.currentGroup!!.id, state.currentGroup!!.id)
        assertEquals(card.copy(containerId = move.destination.containerId, faceId = face, corner = "7/9",
            counters = emptyMap(), covered = false), state.board.cards.first())
    }

    @Test fun liveManualUsesCurrentValueWhileReplayAndStackAdditionKeepTheirDifferentGuards() {
        val root = gameplayFixture(); val card = root.board.cards.first()
        val stack = root.board.containers.last()
        val manual = PlayAction.AdjustCounter(CounterTarget.CARD, card.id, CounterColor.RED, "1")
        val captured = gameplayChange(root, manual, 220)
        val remote = gameplayChange(root, PlayAction.SetCounter(CounterTarget.CARD, card.id, CounterColor.RED, "8"), 221).state!!
        val live = gameplayChange(remote, manual, 222)
        assertEquals(GameplayFieldChange(card.id, GameplayField.COUNTER, "8", "9", CounterColor.RED),
            live.mutation!!.transition.fields.single())
        assertEquals("replayBlocked", GameplayHistory.replay(remote, captured.mutation!!).reason)
        val addition = PlayAction.MoveCard(card.id, MoveSource(card.containerId),
            MoveDestination(stack.id, MoveInsertion.TOP), GameplayRules.stackDigest(root))
        val added = gameplayChange(live.state!!, addition, 223)
        assertEquals("accepted", added.outcome) // Manual edits did not change semantic Stack contents.
        assertEquals("9", added.state!!.board.cards.first().counters[CounterColor.RED])
        assertEquals(live.state.board, GameplayHistory.reverse(added.state, added.mutation!!))
        val later = addition.copy(cardId = root.board.cards[1].id)
        assertEquals("stackConflict", gameplayChange(added.state, later, 224).reason)
        val fresh = gameplayChange(added.state, later.copy(expectedStackDigest = GameplayRules.stackDigest(added.state)), 225)
        assertEquals("accepted", fresh.outcome)
        assertEquals(fresh, GameplayHistory.replay(added.state, fresh.mutation!!))
        assertEquals(added.state.board, GameplayHistory.reverse(fresh.state!!, fresh.mutation))
    }
}
