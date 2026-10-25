package org.asyncttwiz.protocol

import kotlin.test.*

class ManualEditsTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun state(): PlayState {
        val definition = CardDefinition("Front", listOf(DefinitionFace(id(11), "Front", listOf(id(12)), corner = "2/3"),
            DefinitionFace(id(12), "Back", listOf(id(11)), corner = "4/5")))
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))),
                listOf(PlayCard(id(9), "definition", id(8), id(10), id(11), tapped = true, corner = "2/3", note = "keep",
                    placement = PlayPlacement(BoardPosition(3, 4), id(20)), explicitlyCovered = true)),
                listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, id(7), listOf(id(9)))), TurnState(id(7))),
            listOf(SubmittedDefinition("definition", definition)), emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))
    }
    private fun command(action: PlayAction, n: Int = 30) = GameCommand(id(n), GameScope(id(1), playId = id(2)),
        GameAction.Play(action), id(31), "1")
    private fun change(state: PlayState, action: PlayAction, actor: String = id(8), n: Int = 30): GameplayChange =
        GameplayRules.change(state, actor, command(action, n))
    private fun accepted(state: PlayState, action: PlayAction): GameplayChange = change(state, action).also {
        assertEquals("accepted", it.outcome)
        val command = command(action)
        assertTrue(GameWire.valid(command))
        assertEquals(command, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(command)))
        assertEquals(it, GameplayHistory.replay(state, it.mutation!!))
        assertEquals(state.board, GameplayHistory.reverse(it.state!!, it.mutation))
        assertEquals(state.board.containers, it.state!!.board.containers)
        assertEquals(state.board.turn, it.state.board.turn)
    }

    @Test fun cornerArithmeticPlainStringsAndCheckedBounds() {
        for ((part, delta, value) in listOf(Triple(CornerPart.LEFT, 1, "3/3"), Triple(CornerPart.RIGHT, -1, "2/2"),
            Triple(CornerPart.BOTH, 1, "3/4"))) {
            val changed = accepted(state(), PlayAction.AdjustCorner(id(9), part, delta))
            assertEquals(state().board.cards.single().copy(corner = value), changed.state!!.board.cards.single())
            assertEquals(listOf(GameplayFieldChange(id(9), GameplayField.CORNER, "2/3", value)), changed.mutation!!.transition.fields)
        }
        assertEquals("-2/+03", ManualText.corner(" -1/+03 ", CornerPart.LEFT, -1))
        assertEquals("-1", ManualText.corner("0", CornerPart.WHOLE, -1))
        for (text in listOf("", "2/*", "*/*", "1.2", "1+2", "2 /3", "1/2/3"))
            for (part in CornerPart.entries) assertNull(ManualText.corner(text, part, 1), "$text / $part")
        assertNull(ManualText.add(Long.MAX_VALUE.toString(), 1))
        assertNull(ManualText.add(Long.MIN_VALUE.toString(), -1))
        assertNull(ManualText.corner("2/3", CornerPart.BOTH, 2))
    }

    @Test fun setBlankTrimsAndResetReadsCurrentFaceNotOpeningState() {
        val set = accepted(state(), PlayAction.SetCorner(id(9), " \n flying  high\n line \t"))
        assertEquals("flying  high\n line", set.state!!.board.cards.single().corner)
        val blank = accepted(set.state, PlayAction.SetCorner(id(9), "  \n"))
        assertEquals("", blank.state!!.board.cards.single().corner)
        val flipped = accepted(blank.state, PlayAction.SetFace(id(9), id(12)))
        assertEquals(blank.state.board.cards.single().copy(faceId = id(12)), flipped.state!!.board.cards.single())
        val reset = accepted(flipped.state, PlayAction.ResetCorner(id(9)))
        assertEquals("4/5", reset.state!!.board.cards.single().corner)
        assertEquals(listOf(GameplayRead(id(9), GameplayField.FACE, id(12))), reset.mutation!!.transition.reads)
        assertEquals("replayBlocked", GameplayHistory.replay(blank.state, reset.mutation).reason)
        // New events witness definition availability, even though output remains literal.
        val differentDefaults = flipped.state.copy(definitions = emptyList())
        assertEquals("replayBlocked", GameplayHistory.replay(differentDefaults, reset.mutation).reason)
        val missing = state().copy(definitions = emptyList())
        assertEquals("", accepted(missing, PlayAction.ResetCorner(id(9))).state!!.board.cards.single().corner)
    }

    @Test fun sixColorsCardAndPlayerAbsenceZeroTextAndRemoval() {
        for (target in CounterTarget.entries) for (color in CounterColor.entries) {
            val targetId = if (target == CounterTarget.CARD) id(9) else id(7)
            val original = state()
            fun values(s: PlayState) = if (target == CounterTarget.CARD) s.board.cards.single().counters else s.board.players.first().counters
            val increment = accepted(original, PlayAction.AdjustCounter(target, targetId, color, "1"))
            assertEquals(mapOf(color to "1"), values(increment.state!!))
            assertNull(increment.mutation!!.transition.fields.single().from)
            val decrement = accepted(original, PlayAction.AdjustCounter(target, targetId, color, "-1"))
            assertEquals("-1", values(decrement.state!!)[color])
            val zero = accepted(original, PlayAction.SetCounter(target, targetId, color, " 0 "))
            assertEquals("0", values(zero.state!!)[color])
            assertEquals("replayBlocked", GameplayHistory.replay(zero.state, increment.mutation).reason)
            val text = accepted(zero.state, PlayAction.SetCounter(target, targetId, color, " flying "))
            assertEquals("inapplicable", change(text.state!!, PlayAction.AdjustCounter(target, targetId, color, "1")).reason)
            val removed = accepted(text.state, PlayAction.SetCounter(target, targetId, color, " \n "))
            assertTrue(values(removed.state!!).isEmpty())
            assertNull(removed.mutation!!.transition.fields.single().to)
            assertEquals("noOp", change(original, PlayAction.SetCounter(target, targetId, color, "")).outcome)
            assertEquals("replayBlocked", GameplayHistory.replay(original.copy(board = original.board.copy(cards = emptyList(), players = emptyList())),
                increment.mutation).reason)
        }
    }

    @Test fun ordinaryOrderingPreservesIndependentFieldsAndActualTuples() {
        val first = accepted(state(), PlayAction.AdjustCounter(CounterTarget.CARD, id(9), CounterColor.BLUE, "1"))
        val note = accepted(first.state!!, PlayAction.SetNote(id(9), "  new\n  note  "))
        val second = accepted(note.state!!, PlayAction.AdjustCounter(CounterTarget.CARD, id(9), CounterColor.BLUE, "1"))
        assertEquals("1", second.mutation!!.transition.fields.single().from)
        assertEquals("2", second.mutation.transition.fields.single().to)
        assertEquals("new\n  note", second.state!!.board.cards.single().note)
        assertEquals("replayBlocked", GameplayHistory.replay(state(), second.mutation).reason)
        val replayOnIndependent = state().copy(board = state().board.copy(cards = listOf(state().board.cards.single().copy(note = "remote"))))
        assertEquals("remote", GameplayHistory.replay(replayOnIndependent, first.mutation!!).state!!.board.cards.single().note)
        val set = accepted(second.state, PlayAction.SetCounter(CounterTarget.CARD, id(9), CounterColor.BLUE, "9"))
        assertEquals("2", set.mutation!!.transition.fields.single().from)
        val life = accepted(set.state!!, PlayAction.AdjustLife(id(7), "-3"))
        assertEquals("17", life.state!!.board.players.first().life)
        assertEquals(set.state.board.cards, life.state.board.cards)
    }

    @Test fun ownerFaceCoverAndDisclosureAreSeparateLiteralChanges() {
        val original = state()
        val owner = accepted(original, PlayAction.SetOwner(id(9), id(7)))
        assertEquals(original.board.cards.single().copy(owner = id(7)), owner.state!!.board.cards.single())
        assertEquals("noOp", change(owner.state, PlayAction.SetOwner(id(9), id(7))).outcome)
        assertEquals("inapplicable", change(original, PlayAction.SetOwner(id(9), id(99))).reason)
        assertEquals("inapplicable", change(original, PlayAction.SetFace(id(9), id(99))).reason)
        val switchCovered = accepted(original, PlayAction.SetFace(id(9), id(12)))
        assertTrue(switchCovered.state!!.board.cards.single().covered)
        assertTrue(switchCovered.mutation!!.transition.disclosures.isEmpty())
        val uncover = accepted(switchCovered.state, PlayAction.SetCovered(id(9), false))
        assertEquals(listOf(GameplayDisclosure(id(9), id(12), id(10), listOf(id(7), id(8)))), uncover.mutation!!.transition.disclosures)
        val switchVisible = accepted(uncover.state!!, PlayAction.SetFace(id(9), id(11)))
        assertEquals(id(11), switchVisible.mutation!!.transition.disclosures.single().faceId)
        assertEquals("4/5", state().definitions.single().definition.faces.last().corner)
        assertEquals("2/3", switchVisible.state!!.board.cards.single().corner)
        assertEquals("noOp", change(switchVisible.state, PlayAction.SetCovered(id(9), false)).outcome)
    }

    @Test fun inspectionUsesRegionNotOwnerAndExplicitViewingDoesNotEditBoard() {
        val original = state() // card owned by B in A's region
        val card = original.board.cards.single()
        assertTrue(ManualDisclosure.permittedFaces(original, card, id(8), true).isEmpty())
        assertEquals(2, ManualDisclosure.permittedFaces(original, card, id(7), true).size)
        assertTrue(ManualDisclosure.permittedFaces(original, card, id(7), false).isEmpty())
        val inspected = accepted(original, PlayAction.InspectFace(id(9), id(12), id(10)))
        assertEquals(original.board, inspected.state!!.board)
        assertEquals(listOf(id(8)), inspected.mutation!!.transition.disclosures.single().viewers)
        val repeated = change(inspected.state, PlayAction.InspectFace(id(9), id(12), id(10)), n = 32)
        assertEquals("accepted", repeated.outcome)
        assertNotEquals(inspected.state.currentGroup!!.id, repeated.state!!.currentGroup!!.id)
        assertEquals("inapplicable", change(original, PlayAction.InspectFace(id(9), id(12), id(99))).reason)
        assertEquals("inapplicable", change(original, PlayAction.InspectFace(id(99), id(12), id(10))).reason)
        val moved = original.copy(board = original.board.copy(cards = listOf(card.copy(containerId = id(99)))))
        assertEquals("replayBlocked", GameplayHistory.replay(moved, inspected.mutation).reason)
        val visible = card.copy(covered = false)
        assertEquals(listOf(id(11)), ManualDisclosure.permittedFaces(original, visible, id(8), true).map { it.faceId })
    }

    @Test fun presentationDoesNotChangeAbsentZeroAndNonNumericState() {
        assertEquals("", ManualText.playerCounterText(null))
        assertEquals("–", ManualText.cardCounterText(null))
        assertEquals("0", ManualText.playerCounterText("0"))
        for (value in listOf("", "0", "+0", "-000", " 0000 "))
            assertFalse(ManualText.playerRowVisible(mapOf(CounterColor.RED to value)))
        for (value in listOf("1", "-1", "flying", "0.0", "999999999999999999999999999"))
            assertTrue(ManualText.playerRowVisible(mapOf(CounterColor.RED to value)))
    }

    @Test fun malformedAndScopeInputsAreRejectedBySoleProcessor() {
        for (action in listOf(PlayAction.SetCorner("bad", "x"), PlayAction.SetNote(id(9), "\u0000"),
            PlayAction.AdjustCorner(id(9), CornerPart.LEFT, 2),
            PlayAction.AdjustCounter(CounterTarget.CARD, id(9), CounterColor.RED, "1.5")))
            assertEquals("invalidInput", change(state(), action).reason)
        assertEquals("accessDenied", change(state(), PlayAction.SetCorner(id(9), "1"), id(99)).reason)
        val stale = state().copy(id = id(98))
        assertEquals("inapplicable", change(stale, PlayAction.SetCorner(id(9), "1")).reason)
        assertEquals("inapplicable", change(state(), PlayAction.SetNote(id(99), "x")).reason)
    }
}
