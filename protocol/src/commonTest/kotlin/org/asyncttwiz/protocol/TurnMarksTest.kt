package org.asyncttwiz.protocol

import kotlinx.serialization.json.*
import kotlin.test.*

class TurnMarksTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun state(zone: PlayZone = PlayZone.BATTLEFIELD): PlayState = PlayState(
        id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
        BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))),
            listOf(PlayCard(id(9), "definition", id(7), id(10), id(11), covered = false, corner = "2/3", note = "permanent",
                placement = if (zone == PlayZone.BATTLEFIELD) PlayPlacement(BoardPosition(0, 0), id(40)) else null)),
            listOf(PlayContainer(id(10), zone, id(7), listOf(id(9))), PlayContainer(id(12), PlayZone.GRAVEYARD, id(7))), TurnState(id(7))),
        emptyList(), emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))

    private fun change(state: PlayState, action: PlayAction, request: Int = 100): GameplayChange {
        val command = GameCommand(id(request), GameScope(state.gameId, playId = state.id), GameAction.Play(action), id(99), "1")
        assertTrue(GameWire.valid(command))
        assertEquals(command, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(command)))
        return GameplayRules.change(state, id(7), command).also { result ->
            assertEquals("accepted", result.outcome)
            assertEquals(CURRENT_GAMEPLAY_RULES_VERSION, result.mutation!!.transition.rulesVersion)
            assertEquals(result, GameplayHistory.replay(state, result.mutation))
            assertEquals(state.board, GameplayHistory.reverse(result.state!!, result.mutation))
        }
    }
    private fun marked(zone: PlayZone = PlayZone.BATTLEFIELD) = state(zone).let { s -> s.copy(board = s.board.copy(cards = s.board.cards.map {
        it.copy(damage = "2", eotCorner = "+3/+3", eotNote = "can't block")
    })) }

    @Test fun literalAndRelativeEditsReplayWithIndependentPermanentValues() {
        for (zone in listOf(PlayZone.BATTLEFIELD, PlayZone.STACK)) {
            var current = state(zone)
            val actions = listOf(PlayAction.SetDamage(id(9), " 2 "), PlayAction.AdjustDamage(id(9), "-1"),
                PlayAction.SetEotCorner(id(9), " +2/+2 "), PlayAction.AdjustEotCorner(id(9), CornerPart.LEFT, -1),
                PlayAction.AdjustEotCorner(id(9), CornerPart.RIGHT, 1), PlayAction.AdjustEotCorner(id(9), CornerPart.BOTH, 1),
                PlayAction.SetEotNote(id(9), " can't block "))
            actions.forEachIndexed { index, action -> current = change(current, action, 100 + index).state!! }
            val card = current.board.cards.single()
            assertEquals("1", card.damage)
            assertEquals("2/4", card.eotCorner)
            assertEquals("can't block", card.eotNote)
            assertEquals("2/3", card.corner)
            assertEquals("permanent", card.note)
            val emptyDelta = change(state(zone), PlayAction.AdjustEotCorner(id(9), CornerPart.LEFT, 1))
            assertEquals("1/0", emptyDelta.state!!.board.cards.single().eotCorner)
            assertEquals(HistorySignificance.CARD_CORNER_COUNTER_EDIT, emptyDelta.mutation!!.grouping!!.significance)
        }
        assertEquals("1", change(state(), PlayAction.AdjustDamage(id(9), "1")).state!!.board.cards.single().damage)
        assertEquals("", change(marked(), PlayAction.SetEotCorner(id(9), "")).state!!.board.cards.single().eotCorner)
        for (action in listOf(PlayAction.SetDamage(id(9), "x"), PlayAction.SetEotCorner(id(9), "3"),
            PlayAction.SetEotCorner(id(9), "*/2"), PlayAction.AdjustEotCorner(id(9), CornerPart.WHOLE, 1))) assertFalse(GameplayRules.valid(action))
    }

    // TBL-095, TRN-003
    @Test fun turnPassClearsAllCardsInsideOneEventAndOneUndoRestoresThem() {
        for (action in listOf(PlayAction.EndTurn, PlayAction.NextStep)) {
            val initial = marked().let { s -> s.copy(board = s.board.copy(turn = s.board.turn.copy(phase = "ENDING", step = "CLEANUP"),
                cards = s.board.cards + s.board.cards.single().copy(id = id(13), containerId = id(12), placement = null),
                containers = s.board.containers.map { if (it.id == id(12)) it.copy(cards = listOf(id(13))) else it })) }
            val result = change(initial, action)
            assertEquals("2", result.state!!.board.turn.number)
            assertTrue(result.state.board.cards.all { it.damage.isEmpty() && it.eotCorner.isEmpty() && it.eotNote.isEmpty() })
            assertTrue(result.state.board.cards.all { it.note == "permanent" && it.corner == "2/3" })
            assertEquals(6, result.mutation!!.transition.fields.count { it.field in setOf(GameplayField.DAMAGE, GameplayField.EOT_CORNER, GameplayField.EOT_NOTE) })
            assertTrue(result.mutation.transition.concerns!!.fields.any { it.field == ConcernField.TemporaryMarks })
        }
        for (action in listOf(PlayAction.NextStep, PlayAction.ChooseStep(TurnPosition.PRECOMBAT_MAIN))) {
            assertEquals(marked().board.cards, change(marked(), action).state!!.board.cards)
        }
    }

    // TBL-095
    @Test fun zoneLeaveResetsMarksAndWitnessesEmptyAssignmentsButPlacementDoesNot() {
        for (zone in listOf(PlayZone.BATTLEFIELD, PlayZone.STACK)) {
            val initial = marked(zone)
            val card = initial.board.cards.single()
            val move = PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement), MoveDestination(id(12), MoveInsertion.UNORDERED))
            val result = change(initial, move)
            val moved = result.state!!.board.cards.single()
            assertEquals("", moved.damage); assertEquals("", moved.eotCorner); assertEquals("", moved.eotNote)
            val empty = state(zone)
            val event = change(empty, move).mutation!!
            assertEquals("replayBlocked", GameplayHistory.replay(initial, event).reason)
        }
        val initial = marked()
        val card = initial.board.cards.single()
        val move = PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement),
            MoveDestination(card.containerId, MoveInsertion.EMPTY, BoardPosition(1, 1)))
        assertEquals("2", change(initial, move).state!!.board.cards.single().damage)
    }

    @Test fun undoConcernsIncludeTurnCleanupNegativeWitnessAndOnlyEditedMark() {
        val empty = state()
        val end = change(empty, PlayAction.EndTurn).mutation!!
        assertEquals("replayBlocked", GameplayHistory.replay(marked(), end).reason)
        val initial = marked()
        val damage = change(initial, PlayAction.AdjustDamage(id(9), "1")).mutation!!
        val unrelated = initial.copy(board = initial.board.copy(cards = initial.board.cards.map { it.copy(note = "other", corner = "9/9", eotNote = "other") }))
        assertEquals("other", GameplayHistory.replay(unrelated, damage).state!!.board.cards.single().eotNote)
        assertEquals("replayBlocked", GameplayHistory.replay(empty, damage).reason)
    }

    @Test fun defaultsOmitNewKeysAndLegacySealedEventsRemainReplayable() {
        val initial = state()
        val json = GameWire.json
        val encoded = json.encodeToJsonElement(initial.board.cards.single()).jsonObject
        for (key in listOf("damage", "eotCorner", "eotNote")) assertFalse(key in encoded)
        assertEquals(initial.board.cards.single(), json.decodeFromJsonElement<PlayCard>(encoded))
        assertEquals(marked().board.cards.single(), json.decodeFromJsonElement<PlayCard>(json.encodeToJsonElement(marked().board.cards.single())))
        val accepted = change(initial, PlayAction.EndTurn)
        // Build a retained v16 event with the pre-r17 acceptance path, then round-trip its seal.
        val command = GameCommand(id(200), GameScope(initial.gameId, playId = initial.id), GameAction.Play(PlayAction.EndTurn), id(99), "1")
        val legacy = GameplayRules.record(marked(), id(7), command, marked().board.copy(turn = accepted.state!!.board.turn),
            GameplayTransition(), rulesVersion = 16)
        val next = legacy.state!!
        val mutation = legacy.mutation!!
        val delta = GameplayDeltas.capture(marked(), next, mutation.id)
        val recorded = GameplayHistory.recontextualize(marked(), mutation.copy(schemaVersion = 2,
            transition = mutation.transition.copy(delta = delta, concerns = GameplayConcerns.capture(marked(), next, mutation, delta))))
        val sealed = recorded.copy(transition = recorded.transition.copy(delta = delta.copy(seal = GameplayDeltas.seal(recorded))))
        val decoded = json.decodeFromString<GameplayMutation>(json.encodeToString(sealed))
        assertEquals("2", GameplayHistory.apply(marked(), decoded)!!.board.cards.single().damage)
        assertEquals(sealed.transition.delta!!.seal, GameplayDeltas.seal(decoded))
    }
}
