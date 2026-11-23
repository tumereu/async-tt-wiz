package org.asyncttwiz.android

import org.asyncttwiz.protocol.Fault
import org.asyncttwiz.protocol.Faults
import org.asyncttwiz.protocol.game.GameDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** TBL-212, TBL-213, HIS-235, HIS-236 (owner 2026-11-19): what a shake's bubble and an undo's reshuffle line say. */
class RefusalWordsTest {
    private fun reported(block: () -> Unit): List<Fault> {
        val faults = mutableListOf<Fault>()
        val sink = Faults.sink
        Faults.sink = { faults += it }
        try { block() } finally { Faults.sink = sink }
        return faults
    }

    // TBL-212: a known reason is said as it is, reporting nothing
    @Test fun aKnownReasonIsSaid() {
        assertEquals(emptyList(), reported { assertEquals(NOT_A_TARGET, refusalReason(NOT_A_TARGET, "table item")) })
    }

    // TBL-213: a shake without a reason says the stand-in and is reported with where it came from
    @Test fun aShakeWithoutAReasonIsReported() {
        val faults = reported { assertEquals(REFUSAL_STAND_IN, refusalReason(null, "table item")) }
        assertEquals(listOf("Refusal: shake without a reason"), faults.map { it.key })
        assertEquals("table item", faults.single().details["where"])
        assertEquals(1, reported { refusalReason(" ", "control") }.size, "a blank reason is none")
    }

    // TBL-212, HIS-089: an Undo that can't do anything says why in plain words; an unknown reason is reported (TBL-213)
    @Test fun anUndoThatCantSaysWhy() {
        assertEquals("Nothing to undo yet.", undoRefusal("nothing"))
        assertEquals("An undo request is open: wait for the answer.", undoRefusal(GameDevice.REWIND_REQUESTED))
        assertEquals("Your last move is still on its way: try again in a moment.", undoRefusal("sending"))
        val faults = reported { assertEquals(REFUSAL_STAND_IN, undoRefusal("failed")) }
        assertEquals("undo: failed", faults.single().details["where"])
    }

    // SYS-139, TBL-213: a refusal of your own action the game took as allowed says the catch-all and is reported with the reason
    @Test fun aRefusalOfYourOwnActionWithoutWordsIsReported() {
        assertEquals(emptyList(), reported {
            assertEquals("An undo request is open: wait for the answer.", refusedShakeText(GameDevice.REWIND_REQUESTED)) })
        val faults = reported { assertEquals(REFUSED_NOW, refusedShakeText("illegal")) }
        assertEquals("illegal", faults.single().details["reason"])
    }

    // TBL-212: a newer bubble replaces the one shown; an old one's time running out leaves the newer
    @Test fun theNewestBubbleShows() {
        val bubbles = RefusalBubbles()
        bubbles.show("First", null)
        val first = bubbles.bubble!!.serial
        bubbles.show("Second", null)
        bubbles.expire(first)
        assertEquals("Second", bubbles.bubble?.text)
        bubbles.expire(bubbles.bubble!!.serial)
        assertNull(bubbles.bubble)
    }

    // HIS-235: the dialogs' line names whose library the undo reshuffles; none touched, no line
    @Test fun theReshuffleWarningNamesTheLibraries() {
        val names = mapOf("a" to "Alice", "b" to "Bob")
        assertNull(EngineEventText.reshuffleWarning(emptySet(), "a", names))
        assertEquals("Undoing this reshuffles your library: the next cards drawn may change.",
            EngineEventText.reshuffleWarning(setOf("a"), "a", names))
        assertEquals("Undoing this reshuffles Bob's library: the next cards drawn may change.",
            EngineEventText.reshuffleWarning(setOf("b"), "a", names))
        assertEquals("Undoing this reshuffles both libraries: the next cards drawn may change.",
            EngineEventText.reshuffleWarning(setOf("a", "b"), "b", names))
    }

    // HIS-236, HIS-168: after a free undo that touched library cards the top text has a second line; after an approved
    // request (its dialogs said it) and after one that touched none, only the undo's words. The other player's forms
    // ("Alice undid …") are only logged since 2026-11-22 (owner): their table shows no text for the undo
    @Test fun aFreeUndoNoteSaysTheLibrariesReshuffled() {
        val names = mapOf("a" to "Alice", "b" to "Bob")
        assertEquals("Undid\nYour library reshuffled: the next draws may change.",
            EngineEventText.undoNote(org.asyncttwiz.android.EngineGame.Undone("a", null, null, libraries = setOf("a")), "a", names))
        assertEquals("Alice undid\nAlice's library reshuffled: the next draws may change.",
            EngineEventText.undoNote(org.asyncttwiz.android.EngineGame.Undone("a", null, null, libraries = setOf("a")), "b", names))
        assertEquals("Undid", EngineEventText.undoNote(
            org.asyncttwiz.android.EngineGame.Undone("a", null, null, libraries = setOf("a", "b"), requested = true), "a", names))
        assertEquals("Alice undid", EngineEventText.undoNote(org.asyncttwiz.android.EngineGame.Undone("a", null, null), "b", names))
    }
}
