package org.asyncttwiz.android

import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*

/** Retained records are already looked up. Reuse them rather than silently changing printings or
 * provenance when the installed catalog is newer (or temporarily unavailable). New lines use import. */
internal class DeckEditorSource(val detail: DeckDetail) {
    // Use the snapshot digest, not printingId: a merged deck can contain two retained versions
    // of the same printing, and saving must not collapse one version into the other.
    val definitions = detail.definitions
    private fun field(definition: CardDefinition, key: String): String? = runCatching { definition.faces.first().sourceData?.let {
        (DeckJson.format.parseToJsonElement(it).jsonObject[key] as? JsonPrimitive)?.contentOrNull
    } }.getOrNull()
    private fun cardText(definition: CardDefinition): String {
        val set = field(definition, "setCode")
        val number = field(definition, "number")
        return definition.name + if (!set.isNullOrBlank() && !number.isNullOrBlank()) " ($set) $number" else ""
    }
    val text = SavedDeckSection.entries.joinToString("\n") { section ->
        (listOf(if (section == SavedDeckSection.MAINBOARD) "Deck" else "Sideboard") +
            detail.saved.entries.filter { it.section == section }.map {
                "${it.quantity} ${cardText(detail.definitions.getValue(it.definition))}"
            }).joinToString("\n")
    }
    private val retained = detail.definitions.values.groupBy { definition ->
        DeckTextParser.parse("1 ${cardText(definition)}").lines.single().let { Triple(it.name, it.set, it.number) }
    }
    fun resolve(text: String, lookup: (String) -> DeckImportResult): DeckImportResult {
        val parsed = DeckTextParser.parse(text)
        val issues = parsed.issues.toMutableList()
        var catalog: CatalogMetadata? = null
        val occurrences = mutableMapOf<Pair<DeckSection, Triple<String, String?, String?>>, Int>()
        val entries = parsed.lines.flatMap { line ->
            val key = Triple(line.name, line.set, line.number)
            val candidates = retained[key].orEmpty()
            val occurrenceKey = line.section to key
            val occurrence = occurrences.getOrDefault(occurrenceKey, 0)
            occurrences[occurrenceKey] = occurrence + 1
            val rows = detail.saved.entries.filter { entry ->
                entry.section.name == line.section.name && detail.definitions.getValue(entry.definition) in candidates
            }
            val definition = candidates.singleOrNull() ?: rows.getOrNull(occurrence)?.let { detail.definitions.getValue(it.definition) }
            if (definition != null) {
                val faces = definition.faces.mapIndexed { index, face ->
                    CatalogPrinting(if (index == 0) DeckJson.digest(definition) else face.faceId,
                        definition.name, line.set.orEmpty(), line.number.orEmpty(), "English", if (index == 0) "a" else "b",
                        face.scryfallId, definition.token, face.otherFaceIds, face.sourceData ?: "{}")
                }
                listOf(ResolvedDeckEntry(DeckSection.valueOf(line.section.name), line.quantity, faces, listOf(line.line)))
            } else {
                val result = lookup("${line.section.title}\n${line.quantity} ${line.name}${line.printingSuffix}")
                catalog = result.catalog ?: catalog
                issues += result.issues.map { it.copy(line = if (it.line > 0) line.line else 0) }
                result.entries.map { it.copy(sourceLines = listOf(line.line)) }
            }
        }
        return DeckImportResult(text, if (issues.isEmpty()) entries else emptyList(), issues, catalog)
    }
}
