package org.asyncttwiz.android

import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*

/** Preserve already accepted printings when editing, including without an installed catalog. */
internal class CubeEditorSource(private val detail: CubeDetail) {
    val definitions = detail.definitions
    private fun cardText(definition: CardDefinition): String {
        val raw = runCatching { definition.faces.first().sourceData?.let { DeckJson.format.parseToJsonElement(it).jsonObject } }.getOrNull()
        val set = (raw?.get("setCode") as? JsonPrimitive)?.contentOrNull
        val number = (raw?.get("number") as? JsonPrimitive)?.contentOrNull
        return definition.name + if (!set.isNullOrBlank() && !number.isNullOrBlank()) " ($set) $number" else ""
    }
    val text = "Deck\n" + detail.saved.entries.joinToString("\n") { "${it.quantity} ${cardText(definitions.getValue(it.definition))}" }
    private val retained = detail.saved.entries.groupBy { cardText(definitions.getValue(it.definition)) }
    fun resolve(text: String, includeSideboard: Boolean, lookup: (String, Boolean) -> DeckImportResult): DeckImportResult {
        val parsed = DeckTextParser.parse(text, CubeValidation.MAX_CARDS, includeSideboard)
        val issues = parsed.issues.toMutableList()
        val substitutions = mutableListOf<ImportIssue>()
        val occurrences = mutableMapOf<String, Int>()
        var metadata: CatalogMetadata? = null
        val entries = parsed.lines.flatMap { line ->
            val key = line.name + (line.set?.let { " ($it) ${line.number}" } ?: "")
            val candidates = retained.entries.firstOrNull { it.key.equals(key, true) }?.value.orEmpty()
            val occurrence = occurrences.getOrDefault(key, 0)
            occurrences[key] = occurrence + 1
            val saved = candidates.singleOrNull() ?: candidates.getOrNull(occurrence)
            if (saved != null) {
                val definition = definitions.getValue(saved.definition)
                val faces = definition.faces.mapIndexed { index, face ->
                    CatalogPrinting(if (index == 0) saved.definition else face.faceId, definition.name, line.set.orEmpty(),
                        line.number.orEmpty(), "English", if (index == 0) "a" else "b", face.scryfallId,
                        definition.token, face.otherFaceIds, face.sourceData ?: "{}")
                }
                listOf(ResolvedDeckEntry(DeckSection.MAINBOARD, line.quantity, faces, listOf(line.line)))
            } else {
                val result = lookup("Deck\n${line.quantity} $key", false)
                metadata = result.catalog ?: metadata
                issues += result.issues.map { it.copy(line = if (it.line > 0) line.line else 0) }
                substitutions += result.substitutions.map { it.copy(line = line.line) }
                result.entries.map { it.copy(sourceLines = listOf(line.line)) }
            }
        }
        return DeckImportResult(text, if (issues.isEmpty()) entries else emptyList(), issues, metadata, substitutions)
    }
}
