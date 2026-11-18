package org.asyncttwiz.android

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.SavedDeckEntry
import org.asyncttwiz.protocol.SavedDeckSection
import org.asyncttwiz.storage.CatalogPrinting

/**
 * Card records holding invented [cards] (each with a printing id), as [SavedCardRecords] reads them (owner 2026-11-10: saved
 * decks and cubes hold printings, the phone builds the cards from its records): each card's faces as printings, the first
 * face the card's printing, its printed values in the record so the card built from it has them.
 */
internal fun testCardRecords(vararg cards: CardDefinition): SavedCardRecords = testCardRecords(cards.toList())

internal fun testCardRecords(cards: Collection<CardDefinition>, set: String = "TST"): SavedCardRecords {
    val faces = cards.associate { card -> checkNotNull(card.printingId) { "${card.name} has no printing" } to testPrintings(card, set) }
    return SavedCardRecords { ids -> SavedCardFaces(faces.filterKeys { it in ids }, null) }
}

/** [card]'s faces as card records' printings, front first; of set [set] number 1, or of none (no number) for "". */
internal fun testPrintings(card: CardDefinition, set: String = "TST"): List<CatalogPrinting> {
    val number = if (set.isEmpty()) "" else "1"
    val ids = card.faces.mapIndexed { index, face -> if (index == 0) card.printingId!! else face.faceId }
    return card.faces.mapIndexed { index, face ->
        val json = buildJsonObject {
            put("uuid", ids[index]); put("name", card.name); put("setCode", "TST"); put("number", "1"); put("language", "English")
            if (card.faces.size > 1) { put("side", ('a' + index).toString()); put("faceName", face.name) }
            put("layout", card.layout); put("type", face.typeLine); put("manaCost", face.manaCost); put("text", face.rulesText)
            put("power", face.power); put("toughness", face.toughness); put("loyalty", face.loyalty); put("defense", face.defense)
        }
        CatalogPrinting(ids[index], card.name, set, number, "English", if (card.faces.size > 1) ('a' + index).toString() else "",
            face.scryfallId, card.token, ids.filterIndexed { other, _ -> other != index }, json.toString())
    }
}

/** [quantity] copies of [card] in [section] of a saved deck. */
internal fun savedEntry(section: SavedDeckSection, card: CardDefinition, quantity: Int) =
    SavedDeckEntry(section, card.printingId, card.name, quantity)

/** A deck (or with [cube], a cube) text resolver over invented [cards] by name, as the card records' import resolves lines (unknown names are issues). */
internal fun testResolver(vararg cards: CardDefinition, cube: Boolean = false): (String) -> org.asyncttwiz.storage.DeckImportResult = { text ->
    val parsed = if (cube) org.asyncttwiz.storage.CubeImportResolver.parse(text) else org.asyncttwiz.storage.DeckTextParser.parse(text)
    val issues = parsed.issues + parsed.lines.filter { line -> cards.none { it.name == line.name } }
        .map { org.asyncttwiz.storage.ImportIssue(it.line, "${it.name} could not be resolved.") }
    val entries = parsed.lines.mapNotNull { line -> cards.firstOrNull { it.name == line.name }?.let {
        org.asyncttwiz.storage.ResolvedDeckEntry(line.section, line.quantity, testPrintings(it), listOf(line.line)) } }
    org.asyncttwiz.storage.DeckImportResult(text, if (issues.isEmpty()) entries else emptyList(), issues, null)
}

/** [card] as the phone builds it from [testCardRecords]: what a chosen deck's selection carries. */
internal fun builtCard(card: CardDefinition): org.asyncttwiz.protocol.SubmittedDefinition =
    SavedCardFaces(mapOf(card.printingId!! to testPrintings(card)), null)
        .submitted(listOf(SavedCard(SavedDeckSection.MAINBOARD, card.printingId, card.name, 1))).single()
