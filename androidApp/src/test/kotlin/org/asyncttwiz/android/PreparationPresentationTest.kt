package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.*

class PreparationPresentationTest {
    private fun id() = UUID.randomUUID().toString()
    private val actor = id()
    private val definition = CardDefinition("Coppercloud Surveyor", listOf(DefinitionFace(id(), "Coppercloud Surveyor")))
    private val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
    private val deck = PreparedDeck(id(), id(), "1", "Deck", null, id(), listOf(
        PreparedEntry(submitted.digest, SavedDeckSection.MAINBOARD, "0", 4),
        PreparedEntry(submitted.digest, SavedDeckSection.SIDEBOARD, "4", 2)))
    private val base = LobbyPreparation(id(), id(), listOf(PlayerPreparation(actor, deck = deck)), listOf(submitted))
    private fun capture(all: Boolean = true) = PreparationRules.capture(base, actor, submitted.digest, SavedDeckSection.MAINBOARD, all)!!

    @Test fun deckNamesAreOnlyPresentedToTheirOwnerIncludingPodGames() {
        val secret = "Unexpected sideboard strategy"
        assertEquals(secret, gameDeckName(actor, actor, secret))
        assertEquals("Deck", gameDeckName(actor, id(), secret))
        assertEquals("Deck", gameDeckName(actor, null, secret))
    }

    @Test fun tapAndRecognizedDragHaveOneCapturedReleaseAndCancelOutsideOrConsumedSendNothing() {
        val captured = capture()
        assertSame(captured, PreparationGesture(captured, 8f).release(false))
        val drag = PreparationGesture(captured, 8f); drag.move(9f); drag.move(1f)
        assertTrue(drag.dragging); assertNull(drag.release(false))
        assertSame(captured, drag.release(true)) // Release chooses the captured object, never captures a later row.
        val cancel = PreparationGesture(captured, 8f); cancel.move(20f); cancel.cancel(); assertNull(cancel.release(true))
        val scroll = PreparationGesture(captured, 8f); scroll.move(2f, consumed = true); assertNull(scroll.release(false))
        assertNull(PreparationGesture(null, 8f).release(true))
    }

    @Test fun actualDisplayedPartialTransferAndRollbackReplaceFeedbackButReceiptsAndIdenticalReadsDoNot() {
        val one = capture(false)
        val moved = PreparationRules.previewTransfer(base, actor, one)
        val feedback = preparationFeedback(base, moved, actor, null)!!
        assertEquals("1× Coppercloud Surveyor → Sideboard", feedback.text)
        assertEquals(feedback, preparationFeedback(moved, moved, actor, feedback))
        val rollback = preparationFeedback(moved, base, actor, feedback)!!
        assertEquals("1× Coppercloud Surveyor → Mainboard", rollback.text); assertEquals(2, rollback.serial)
        val remaining = PreparationRules.previewTransfer(moved, actor, capture())
        assertEquals("3× Coppercloud Surveyor → Sideboard", preparationFeedback(moved, remaining, actor, rollback)!!.text)
        assertNull(preparationFeedback(base, base.copy(players = listOf(PlayerPreparation(actor, deck = deck.copy(id = id())))), actor, feedback))
        assertNull(preparationFeedback(null, base, actor, null))
    }
}
