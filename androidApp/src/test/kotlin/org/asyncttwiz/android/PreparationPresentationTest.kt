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

    @Test fun swipeMovesOnlyPastTheThresholdInEitherDirection() {
        val threshold = preparationSwipeThreshold(1000f, 300f)
        assertEquals(300f, threshold); assertEquals(70f, preparationSwipeThreshold(200f, 300f))
        assertFalse(preparationSwipeCommits(299f, threshold)); assertFalse(preparationSwipeCommits(-299f, threshold))
        assertTrue(preparationSwipeCommits(300f, threshold)); assertTrue(preparationSwipeCommits(-450f, threshold))
        assertFalse(preparationSwipeCommits(10f, preparationSwipeThreshold(0f, 300f))) // Unmeasured rows never move.
        assertEquals(.5f, preparationSwipeProgress(-150f, threshold)); assertEquals(1f, preparationSwipeProgress(900f, threshold))
    }

    @Test fun undoInvertsTheExactCopiesAndBasicsAndIsScopedToTheShownRoundAndDeck() {
        val one = capture(false)
        val moved = PreparationRules.previewTransfer(base, actor, one)
        val back = one.inverse()
        assertEquals(SavedDeckSection.SIDEBOARD, back.source); assertEquals(one.entries.map { it.firstIndex }, back.entries.map { it.firstIndex })
        assertEquals(base.players[0].deck!!.entries, PreparationRules.previewTransfer(moved, actor, back).players[0].deck!!.entries)
        var undo = PreparationUndo().push(base.gameId, base.roundId, deck.id, PreparationStep.Moved(one))
            .push(base.gameId, base.roundId, deck.id, PreparationStep.Basics(SavedDeckSection.SIDEBOARD, PodDeckBasics(island = 3)))
        assertEquals(PreparationStep.Basics(SavedDeckSection.SIDEBOARD, PodDeckBasics(island = 3)), undo.last(moved, actor))
        undo = undo.pop()
        assertEquals(PreparationStep.Moved(one), undo.last(moved, actor))
        assertNull(undo.last(moved.copy(roundId = id()), actor)); assertNull(undo.last(moved, id()))
        assertNull(undo.last(moved.copy(players = listOf(PlayerPreparation(actor, deck = deck.copy(id = id())))), actor))
        assertNull(undo.pop().last(moved, actor))
        val other = undo.push(id(), base.roundId, deck.id, PreparationStep.Moved(one))
        assertEquals(1, other.steps.size)
        val full = (1..60).fold(PreparationUndo()) { stack, count ->
            stack.push(base.gameId, base.roundId, deck.id, PreparationStep.Basics(SavedDeckSection.MAINBOARD, PodDeckBasics(plains = count))) }
        assertEquals(PreparationUndo.LIMIT, full.steps.size)
        assertEquals(PreparationStep.Basics(SavedDeckSection.MAINBOARD, PodDeckBasics(plains = 11)), full.steps.first())
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
