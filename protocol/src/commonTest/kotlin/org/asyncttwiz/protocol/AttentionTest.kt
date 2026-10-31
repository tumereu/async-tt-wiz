package org.asyncttwiz.protocol

import kotlinx.serialization.json.*
import kotlin.test.*

class AttentionTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = id(7)
    private val b = id(8)
    private fun state(position: TurnPosition = TurnPosition.UNTAP, holder: String? = null) = PlayState(id(1), id(2), id(3), id(4),
        PlayRoot(id(5), id(6)), id(5), BoardState(listOf(PlayPlayer(a), PlayPlayer(b)), emptyList(), emptyList(),
            TurnState(a, phase = position.phase, step = position.step), attentionHolder = holder),
        emptyList(), emptyList(), InitialStarter(StarterChoice(a), a))
    private fun command(action: GameAction, n: Int) = GameCommand(id(n), GameScope(id(1), playId = id(2)), action, id(9), "1")
    private fun play(action: PlayAction, n: Int) = command(GameAction.Play(action), n)

    // TRN-014
    @Test fun everyAdvancePickerAndTurnBoundarySetsAttentionAndUndoRestoresExactPreviousHolder() {
        for (position in TurnPosition.entries) for (holder in listOf(null, a, b)) {
            val initial = state(position, holder)
            val actions = listOf(PlayAction.NextStep, PlayAction.EndTurn) + TurnPosition.entries
                .filter { it != position }.map { PlayAction.ChooseStep(it) }
            for (action in actions) {
                val changed = GameplayTree(initial).play(b, play(action, 20))
                val tree = assertNotNull(changed.tree)
                val next = assertNotNull(tree.copy().stateAt(tree.currentNodeId)) // cold replay
                val expected = if (TurnPosition.of(next.board.turn) == TurnPosition.DECLARE_BLOCKERS)
                    next.board.players.single { it.accountId != next.board.turn.accountId }.accountId else next.board.turn.accountId
                assertEquals(expected, next.board.effectiveAttentionHolder, "$position / $action / $holder")
                assertEquals(AttentionChange(holder, expected), tree.nodes.getValue(tree.currentNodeId).transition.attention)
                assertEquals(1, tree.nodes.size) // no synthetic pass event
                val undone = assertNotNull(SelectiveUndo.change(tree, b, command(GameAction.Undo, 21)).tree)
                assertEquals(initial.board, undone.stateAt(undone.currentNodeId)!!.board)
            }
        }
    }

    // HIS-050, HIS-051, HIS-086, TRN-006
    @Test fun eitherPlayerFlipsRatherThanAssignsAndDisplayGroupingNeverChangesUndo() {
        for (actor in listOf(a, b)) {
            val initial = state()
            val edited = GameplayTree(initial).play(a, play(PlayAction.AdjustLife(a, "1"), 20)).tree!!
            val before = edited.stateAt(edited.currentNodeId)!!
            val passed = edited.play(actor, play(PlayAction.PassAttention, 21)).tree!!
            val next = passed.stateAt(passed.currentNodeId)!!
            assertEquals(b, next.board.effectiveAttentionHolder)
            assertEquals(before.board.copy(attentionHolder = b), next.board)
            assertEquals(before.currentGroup!!.id, next.currentGroup!!.id)
            val twice = passed.play(actor, play(PlayAction.PassAttention, 22)).tree!!
            assertEquals(a, twice.stateAt(twice.currentNodeId)!!.board.effectiveAttentionHolder)
            val undo = SelectiveUndo.change(passed, actor, command(GameAction.Undo, 23)).tree!!
            assertEquals(edited.nodes, undo.nodes)
            assertEquals(before.board, undo.stateAt(undo.currentNodeId)!!.board)
            assertEquals(next, passed.copy().stateAt(passed.currentNodeId))
            val mutation = passed.nodes.getValue(passed.currentNodeId)
            assertEquals(before.board, GameplayHistory.reverse(next, mutation))
            assertTrue(GameWire.valid(play(PlayAction.PassAttention, 24)))
            assertEquals("accessDenied", GameplayRules.change(initial, id(99), play(PlayAction.PassAttention, 25)).reason)
        }
    }

    // TRN-014
    @Test fun nonTrackingActionsDoNotPassAndSelectingCurrentStepIsANoOp() {
        val initial = state(holder = b)
        for (action in listOf(PlayAction.AdjustLife(a, "1"), PlayAction.RollDie(6, 4),
            PlayAction.ChooseStep(TurnPosition.UNTAP))) {
            val result = GameplayRules.change(initial, a, play(action, 20))
            assertEquals(b, result.state!!.board.effectiveAttentionHolder)
            assertNull(result.mutation?.transition?.attention)
        }
    }

    // HIS-051
    @Test fun passJoinsEvenAnOpponentsEndingSuggestionForDisplayOnly() {
        val suggested = GameplayTree(state()).play(a, play(PlayAction.SuggestResult(SuggestedResult.WIN), 20)).tree!!
        val passed = suggested.play(b, play(PlayAction.PassAttention, 21)).tree!!
        val before = suggested.stateAt(suggested.currentNodeId)!!
        val after = passed.stateAt(passed.currentNodeId)!!
        assertEquals(before.currentGroup!!.id, after.currentGroup!!.id)
        assertEquals(before.currentGroup.representative, after.currentGroup.representative)
        val undone = SelectiveUndo.change(passed, b, command(GameAction.Undo, 22)).tree!!
        assertEquals(before.board, undone.stateAt(undone.currentNodeId)!!.board)
    }

    // TRN-014
    @Test fun addingAStackEffectDoesNotPassAttention() {
        val definition = CardDefinition("Source", listOf(DefinitionFace(id(30), "Source")))
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        val initial = state(holder = b).let { it.copy(definitions = listOf(submitted), board = it.board.copy(
            cards = listOf(PlayCard(id(31), submitted.digest, a, id(32), id(30), covered = false)),
            containers = listOf(PlayContainer(id(32), PlayZone.BATTLEFIELD, a, listOf(id(31))),
                PlayContainer(id(33), PlayZone.STACK)))) }
        val action = PlayAction.Stack(StackAction.AddEffect(id(31), GameplayRules.stackDigest(initial)))
        val changed = GameplayRules.change(initial, a, play(action, 20))
        assertEquals("accepted", changed.outcome)
        assertEquals(b, changed.state!!.board.effectiveAttentionHolder)
        assertNull(changed.mutation!!.transition.attention)
    }

    @Test fun legacySnapshotsKeepTurnPlayerFallbackButSchemaOneEventsAreRejected() {
        val initial = state(TurnPosition.DECLARE_ATTACKERS)
        val json = GameWire.json
        val encoded = json.encodeToJsonElement(initial).jsonObject
        val legacyBoard = JsonObject(encoded.getValue("board").jsonObject - "attentionHolder")
        val legacy = json.decodeFromJsonElement<PlayState>(JsonObject(encoded + ("board" to legacyBoard)))
        assertEquals(a, legacy.board.effectiveAttentionHolder)
        val change = GameplayRules.change(legacy, a, play(PlayAction.NextStep, 20))
        val mutation = change.mutation!!
        val oldMutation = mutation.copy(schemaVersion = 1, bookkeeping = null,
            transition = mutation.transition.copy(attention = null, delta = null, reads = emptyList()))
        val replay = GameplayHistory.replay(legacy, oldMutation)
        assertEquals("replayBlocked", replay.reason)
        val oldTree = GameplayTree(legacy, mapOf(oldMutation.id to oldMutation), oldMutation.id)
        assertNull(oldTree.stateAt(oldMutation.id))
        val boundary = GameplayRules.change(state(TurnPosition.CLEANUP), a, play(PlayAction.NextStep, 21)).mutation!!
        val oldBoundary = boundary.copy(schemaVersion = 1, bookkeeping = null,
            transition = boundary.transition.copy(attention = null, delta = null, reads = emptyList()))
        assertEquals("replayBlocked", GameplayHistory.replay(state(TurnPosition.CLEANUP), oldBoundary).reason)
    }
}
