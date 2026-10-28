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
        val threshold = 60f
        assertFalse(preparationSwipeCommits(59f, threshold)); assertFalse(preparationSwipeCommits(-59f, threshold))
        assertTrue(preparationSwipeCommits(60f, threshold)); assertTrue(preparationSwipeCommits(-450f, threshold))
        assertFalse(preparationSwipeCommits(10f, 0f)) // An unmeasured threshold never moves.
        assertEquals(.5f, preparationSwipeProgress(-30f, threshold)); assertEquals(1f, preparationSwipeProgress(900f, threshold))
    }

    @Test fun cardTypesUseTheFirstMatchingGroupFromTheFrontTypeLineOnly() {
        assertEquals(PreparationTypeGroup.CREATURES, preparationTypeGroup("Artifact Creature — Golem"))
        assertEquals(PreparationTypeGroup.CREATURES, preparationTypeGroup("Enchantment Creature - God"))
        assertEquals(PreparationTypeGroup.PLANESWALKERS, preparationTypeGroup("Legendary Planeswalker — Jace"))
        assertEquals(PreparationTypeGroup.BATTLES, preparationTypeGroup("Battle — Siege"))
        assertEquals(PreparationTypeGroup.INSTANTS, preparationTypeGroup("Kindred Instant — Elf"))
        assertEquals(PreparationTypeGroup.SORCERIES, preparationTypeGroup("Sorcery"))
        assertEquals(PreparationTypeGroup.ARTIFACTS, preparationTypeGroup("Artifact Land"))
        assertEquals(PreparationTypeGroup.ENCHANTMENTS, preparationTypeGroup("Enchantment — Aura"))
        assertEquals(PreparationTypeGroup.LANDS, preparationTypeGroup("Basic Land — Forest"))
        assertEquals(PreparationTypeGroup.INSTANTS, preparationTypeGroup("Instant // Sorcery"))
        assertEquals(PreparationTypeGroup.OTHER, preparationTypeGroup("Conspiracy"))
        assertEquals(PreparationTypeGroup.OTHER, preparationTypeGroup(""))
        assertEquals(PreparationTypeGroup.OTHER, preparationTypeGroup("Tribal — Landfall Creature")) // Subtypes never decide.
    }

    @Test fun groupsFollowTypeOrderSkipEmptyGroupsSortByNameAndLeaveSuppliedBasicsToSteppers() {
        fun card(name: String, type: String) = CardDefinition(name, listOf(DefinitionFace(id(), name, typeLine = type)))
            .let { SubmittedDefinition(DeckJson.digest(it), it) }
        val zebra = card("Zebra Scout", "Creature — Horse"); val ant = card("ant Soldier", "Creature — Insect")
        val bolt = card("Bolt", "Instant"); val forest = card("Forest", "Basic Land — Forest")
        val swamp = card("Swamp", "Basic Land — Swamp")
        val definitions = listOf(zebra, ant, bolt, forest, swamp).associate { it.digest to it.definition }
        val deck = PreparedDeck(id(), id(), "1", "Deck", null, id(), listOf(
            PreparedEntry(forest.digest, SavedDeckSection.MAINBOARD, "0", 7),
            PreparedEntry(zebra.digest, SavedDeckSection.MAINBOARD, "7", 2),
            PreparedEntry(bolt.digest, SavedDeckSection.MAINBOARD, "9", 1),
            PreparedEntry(ant.digest, SavedDeckSection.MAINBOARD, "10", 1),
            PreparedEntry(zebra.digest, SavedDeckSection.MAINBOARD, "11", 1),
            PreparedEntry(swamp.digest, SavedDeckSection.MAINBOARD, "12", 5, BasicLand.SWAMP),
            PreparedEntry(bolt.digest, SavedDeckSection.SIDEBOARD, "17", 3)))
        val main = preparationGroups(deck, SavedDeckSection.MAINBOARD, definitions)
        assertEquals(listOf(PreparationTypeGroup.CREATURES, PreparationTypeGroup.INSTANTS, PreparationTypeGroup.LANDS), main.map { it.type })
        assertEquals(listOf("ant Soldier" to 1L, "Zebra Scout" to 3L), main[0].tiles.map { it.name to it.quantity })
        assertEquals(4L, main[0].count)
        assertEquals(listOf("Forest" to 7L), main[2].tiles.map { it.name to it.quantity }) // Ordinary basics are tiles; supplied ones are not.
        val side = preparationGroups(deck, SavedDeckSection.SIDEBOARD, definitions)
        assertEquals(listOf(PreparationGroup(PreparationTypeGroup.INSTANTS, listOf(PreparationTile(bolt.digest, bolt.definition, 3)))), side)
        val unknown = deck.copy(entries = listOf(PreparedEntry("missing", SavedDeckSection.SIDEBOARD, "0", 2)))
        assertEquals(listOf(PreparationTypeGroup.OTHER to "Card"), preparationGroups(unknown, SavedDeckSection.SIDEBOARD, definitions)
            .map { it.type to it.tiles.single().name })
        assertTrue(preparationGroups(unknown, SavedDeckSection.MAINBOARD, definitions).isEmpty())
    }

    @Test fun tilesFitAtLeastTheMinimumWidthAndAlwaysOnePerRow() {
        assertEquals(4, preparationColumns(380f, 80f, 4f)) // Portrait phone.
        assertEquals(10, preparationColumns(860f, 80f, 4f)) // Landscape phone.
        assertEquals(1, preparationColumns(40f, 80f, 4f))
        assertEquals(2, preparationColumns(164f, 80f, 4f))
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
