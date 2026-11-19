package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals

/** TBL-200: which rules reminders a card shows in Inspect, in a game and outside one. */
class RulesRemindersTest {
    private val f = TableFixture

    /** A board of [TableFixture] whose cards all have the rules text [text]. */
    private fun board(text: String): TableBoard {
        val definition = f.definition.copy(faces = listOf(f.definition.faces.single().copy(rulesText = text)))
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        val play = f.play(hand = 1, board = 1)
        return tableBoard(play.copy(board = play.board.copy(cards = play.board.cards.map { it.copy(definition = submitted.digest) }),
            definitions = listOf(submitted)))
    }

    private fun reminders(table: TableBoard, cardId: String, viewer: String): List<String> {
        val card = table.board.cards.single { it.id == cardId }
        val inspection = ManualInspection(table.gameId, table.playId, card.id,
            ManualDisclosure.permittedFaces(table.board, table.definitions, card, viewer, inspecting = true).map { it.faceId })
        return gameInspectGroups(table, viewer, inspection, card, emptyMap()).flatMap { group -> group.reminders.map { it.name } }
    }

    // TBL-200: a battlefield card's printed keywords and the token kind it makes, without reminder text, are reminded
    @Test fun aGameCardShowsItsUnexplainedRulesWords() {
        val table = board("Flying\nWard {2}\nWhen this creature enters, create a Treasure token.")
        assertEquals(listOf("Flying", "Ward", "Treasure"), reminders(table, "a/BATTLEFIELD/0", "a"))
        // The opponent sees it on the battlefield too
        assertEquals(listOf("Flying", "Ward", "Treasure"), reminders(table, "a/BATTLEFIELD/0", "b"))
    }

    // TBL-200: a card the viewer can't see (the opponent's hand) has no reminders, which would tell what it is
    @Test fun aCoveredCardHasNone() {
        assertEquals(emptyList(), reminders(board("Flying"), "b/HAND/0", "a"))
    }

    // TBL-200: reminder text explains its word; the card's own names never count
    @Test fun aCardExplainingItselfHasNone() {
        assertEquals(emptyList(), reminders(board("Bargain (You may sacrifice an artifact, enchantment, or token as you cast this spell.)"),
            "a/BATTLEFIELD/0", "a"))
        val named = CardDefinition("Ward Keeper", listOf(DefinitionFace("a", "Ward Keeper", rulesText = "When Ward Keeper enters, scry 1.")))
        assertEquals(listOf("Scry"), cardReminders(named).map { it.name })
    }

    // TBL-200: every face and printed part counts, each word once, and a printed name counts as the card's own
    @Test fun everyFaceAndPartCounts() {
        val card = CardDefinition("Ember Squire // Bold Charge", listOf(DefinitionFace("a", "Ember Squire", rulesText = "Haste",
            printedName = "Spark Herald", parts = listOf(PrintedPart("Ember Squire", rulesText = "Haste"),
                PrintedPart("Bold Charge", rulesText = "Target creature gains trample until end of turn. Spark Herald can't block."))),
            DefinitionFace("b", "Back Face", rulesText = "Flying, haste")), layout = "adventure")
        assertEquals(listOf("Haste", "Trample", "Flying"), cardReminders(card).map { it.name })
        assertEquals(emptyList(), cardReminders(CardDefinition("Blank", listOf(DefinitionFace("a", "Blank")))))
    }
}
