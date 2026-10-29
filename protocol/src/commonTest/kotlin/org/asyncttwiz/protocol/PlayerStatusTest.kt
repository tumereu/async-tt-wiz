package org.asyncttwiz.protocol

import kotlin.test.*

class PlayerStatusTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = id(1)
    private val b = id(2)
    private fun play(status: String = "") = PlayState(id(3), id(6), id(4), id(7), PlayRoot(id(8), id(9)), id(8),
        BoardState(listOf(PlayPlayer(a, status = status), PlayPlayer(b, status = status)), emptyList(), emptyList(), TurnState(a),
            attentionHolder = b),
        emptyList(), emptyList(), InitialStarter(StarterChoice(a), a))
    private fun command(text: String, n: Int = 10) = command(GameAction.Play(PlayAction.SetStatus(text)), n)
    private fun command(action: GameAction, n: Int) = GameCommand(id(n), GameScope(id(3), playId = id(6)), action, id(11), "1")

    @Test fun rulesVersionIsAtLeast26() = assertTrue(CURRENT_GAMEPLAY_RULES_VERSION >= 26)

    @Test fun statusIsEmptyOrTrimmedSingleLineAndAtMost512Characters() {
        fun valid(text: String) = GameWire.valid(command(text))
        for (good in listOf("", "Wait", "Thinking…", "x".repeat(65), "x".repeat(512), "Back in ten. ".repeat(39) + "Sorry",
                "GG ✨", PlayerStatus.OPENING)) assertTrue(valid(good), good)
        for (bad in listOf("x".repeat(513), " Wait", "Wait ", "two\nlines", "tab\there", "nul\u0000", "\uD800"))
            assertFalse(valid(bad), bad)
        assertEquals("invalidInput", GameplayRules.change(play(), a, command("Wait\n")).reason)
        assertEquals(512, PlayerStatus.MAX_LENGTH)
        assertEquals("x".repeat(512), GameplayRules.change(play(), a, command("x".repeat(512))).state!!.board.players.first().status)
        assertEquals("invalidInput", GameplayRules.change(play(), a, command("x".repeat(513))).reason)
    }

    @Test fun emptyStatusKeepsTheOldWireShapeAndActionsRoundTrip() {
        assertFalse("status" in GameWire.json.encodeToString(PlayPlayer.serializer(), PlayPlayer(a)))
        assertTrue("\"status\":\"Wait\"" in GameWire.json.encodeToString(PlayPlayer.serializer(), PlayPlayer(a, status = "Wait")))
        for (text in listOf("", "Got a response")) {
            val sent = command(text)
            assertEquals(sent, GameWire.decodeClient(GameWire.json.encodeToString(GameClientMessage.serializer(), sent)))
        }
    }

    @Test fun settingChangesOnlyTheActorsStatusNeverAttentionAndHistoryRestoresItExactly() {
        val before = play()
        val change = GameplayRules.change(before, b, command("Wait"))
        val after = change.state!!
        assertEquals(listOf("", "Wait"), after.board.players.map { it.status })
        assertEquals(before.board.attentionHolder, after.board.attentionHolder)
        assertEquals(before.board.effectiveAttentionHolder, after.board.effectiveAttentionHolder)
        assertEquals(before.board.turn, after.board.turn)
        val event = change.mutation!!
        assertNull(event.transition.attention)
        assertEquals(listOf(GameplayFieldChange(b, GameplayField.STATUS, "", "Wait")), event.transition.fields)
        assertEquals(CURRENT_GAMEPLAY_RULES_VERSION, event.transition.rulesVersion)
        assertNotNull(event.transition.concerns)
        assertEquals(after, GameplayHistory.apply(before, event))
        assertEquals(before, GameplayHistory.unapply(after, event))
        assertEquals(change, GameplayHistory.replay(before, event))
        // Same text is a no-op; a non-player cannot set a status.
        assertNull(GameplayRules.change(after, b, command("Wait", 12)).mutation)
        assertEquals("accessDenied", GameplayRules.change(before, id(99), command("Wait")).reason)
        // Clearing empties exactly the actor's own status.
        val cleared = GameplayRules.change(play("BRB"), a, command("", 13))
        assertEquals(listOf("", "BRB"), cleared.state!!.board.players.map { it.status })
        assertEquals(GameplayFieldChange(a, GameplayField.STATUS, "BRB", ""), cleared.mutation!!.transition.fields.single())
    }

    @Test fun setAndClearAreUndoableAndRedoable() {
        val initial = play(PlayerStatus.OPENING)
        val ready = GameplayTree(initial).play(a, command("")).tree!!
        assertEquals(listOf("", PlayerStatus.OPENING), ready.stateAt(ready.currentNodeId)!!.board.players.map { it.status })
        val waiting = ready.play(a, command("Wait", 12)).tree!!
        // The other player's later status does not concern A's status: A can still undo.
        val other = waiting.play(b, command("Thinking…", 13)).tree!!
        val undone = SelectiveUndo.change(other, a, command(GameAction.Undo, 14)).tree!!
        assertEquals(listOf("", "Thinking…"), undone.stateAt(undone.currentNodeId)!!.board.players.map { it.status })
        val undoneAgain = SelectiveUndo.change(undone, a, command(GameAction.Undo, 15)).tree!!
        assertEquals(listOf(PlayerStatus.OPENING, "Thinking…"), undoneAgain.stateAt(undoneAgain.currentNodeId)!!.board.players.map { it.status })
        val redone = SelectiveUndo.change(undoneAgain, a, command(GameAction.Redo, 16)).tree!!
        assertEquals(listOf("", "Thinking…"), redone.stateAt(redone.currentNodeId)!!.board.players.map { it.status })
    }
}
