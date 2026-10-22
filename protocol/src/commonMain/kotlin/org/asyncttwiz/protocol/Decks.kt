package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.asyncttwiz.core.EntityId

/** Plain immutable metadata, never executable rules. Face order is semantic; links are explicit. */
@Serializable
data class DefinitionFace(
    val faceId: String,
    val name: String,
    val otherFaceIds: List<String> = emptyList(),
    val typeLine: String = "",
    val manaCost: String = "",
    val rulesText: String = "",
    val power: String = "",
    val toughness: String = "",
    val loyalty: String = "",
    val defense: String = "",
    val corner: String = "",
    val scryfallId: String? = null,
    val imageUri: String? = null,
    val sourceData: String? = null,
)

@Serializable
data class CardDefinition(
    val name: String,
    val faces: List<DefinitionFace>,
    val schemaVersion: Int = 1,
    val provider: String? = null,
    val sourceVersion: String? = null,
    val printingId: String? = null,
    val layout: String = "normal",
    val token: Boolean = false,
)

@Serializable data class SubmittedDefinition(val digest: String, val definition: CardDefinition)
@Serializable data class SubmitDefinitions(val requestId: String, val definitions: List<SubmittedDefinition>)
@Serializable data class DefinitionsAccepted(val digests: List<String>)
@Serializable enum class SavedDeckSection { MAINBOARD, SIDEBOARD }
@Serializable data class SavedDeckEntry(val section: SavedDeckSection, val definition: String, val quantity: Int)
@Serializable data class CreateDeck(val requestId: String, val name: String, val entries: List<SavedDeckEntry>)
@Serializable data class DeckSummary(val id: String, val name: String, val revision: String, val createdAt: String)
@Serializable data class SavedDeck(val deck: DeckSummary, val entries: List<SavedDeckEntry>)
@Serializable data class DeckList(val decks: List<DeckSummary>, val next: String? = null)
@Serializable data class DeckError(val code: String, val field: String? = null)

/** RFC8785 subset: typed schemas contain only strings, booleans, null and exact 32-bit integers.
 * Expand defaults/nulls, sort object keys by UTF-16 and sets explicitly; ordered arrays stay ordered.
 */
object DeckJson {
    val format = Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = false }
    private fun canonical(value: JsonElement): String = when (value) {
        is JsonObject -> value.keys.sorted().joinToString(",", "{", "}") {
            JsonPrimitive(it).toString() + ":" + canonical(value.getValue(it))
        }
        is JsonArray -> value.joinToString(",", "[", "]", transform = ::canonical)
        else -> value.toString()
    }
    inline fun <reified T> canonical(value: T): String = canonicalElement(format.encodeToJsonElement(value))
    fun canonicalElement(value: JsonElement): String = canonical(value)
    fun normalized(definition: CardDefinition) = definition.copy(faces = definition.faces.map {
        it.copy(otherFaceIds = it.otherFaceIds.sorted())
    })
    fun definitionBytes(definition: CardDefinition): ByteArray =
        ("async-tt-wiz:definition:1\n" + canonical(normalized(definition))).encodeToByteArray()
    fun digest(definition: CardDefinition): String = "sha256:" + sha256(definitionBytes(definition))
    fun intent(kind: String, canonical: String): String = sha256(("async-tt-wiz:$kind:1\n" + canonical).encodeToByteArray())
}

internal expect fun sha256(bytes: ByteArray): String

object DeckValidation {
    const val RECORD_BYTES = 256 * 1024
    const val FIELD_BYTES = 64 * 1024
    fun uuid(value: String): Boolean = runCatching { EntityId(value) }.isSuccess
    fun digest(value: String): Boolean = Regex("sha256:[0-9a-f]{64}").matches(value)
    fun text(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val char = value[index++]
            if (char.isHighSurrogate()) {
                if (index == value.length || !value[index++].isLowSurrogate()) return false
            } else if (char.isLowSurrogate()) return false
            if (char == '\u0000') return false // PostgreSQL text cannot retain NUL.
        }
        return value.encodeToByteArray().size <= FIELD_BYTES
    }
    fun definition(value: CardDefinition): Boolean {
        if (value.schemaVersion != 1 || value.name.isBlank() || value.faces.size !in 1..64) return false
        val strings = listOfNotNull(value.name, value.provider, value.sourceVersion, value.printingId, value.layout) +
            value.faces.flatMap { listOfNotNull(it.faceId, it.name, it.typeLine, it.manaCost, it.rulesText,
                it.power, it.toughness, it.loyalty, it.defense, it.corner, it.scryfallId, it.imageUri, it.sourceData) }
        if (!strings.all(::text) || DeckJson.definitionBytes(value).size > RECORD_BYTES) return false
        val ids = value.faces.map { it.faceId }.toSet()
        if (ids.size != value.faces.size || !ids.all(::uuid)) return false
        return value.faces.all { face -> face.name.isNotBlank() &&
            face.otherFaceIds.size == ids.size - 1 && face.otherFaceIds.toSet() == ids - face.faceId &&
            (face.scryfallId == null || uuid(face.scryfallId)) &&
            (face.imageUri == null || safeImageReference(face.imageUri)) }
    }
    // No DNS/network access. Actual downloading must additionally validate DNS and every redirect.
    private fun safeImageReference(value: String): Boolean {
        if (!value.startsWith("https://") || value.any { it <= ' ' || it == '\\' } || '#' in value) return false
        val authority = value.removePrefix("https://").takeWhile { it != '/' && it != '?' }
        if (authority.isEmpty() || '@' in authority || ':' in authority || '[' in authority) return false
        if (!authority.matches(Regex("[A-Za-z0-9.-]+")) || '.' !in authority || authority.endsWith('.')) return false
        val host = authority.lowercase()
        return !host.endsWith(".localhost") && !host.endsWith(".local") && !host.endsWith(".internal") &&
            host.any { it in 'a'..'z' }
    }
    fun create(value: CreateDeck): Boolean = text(value.name) && value.name.isNotBlank() &&
        value.entries.isNotEmpty() && value.entries.all { digest(it.definition) && it.quantity > 0 } &&
        value.entries.map { it.section to it.definition }.distinct().size == value.entries.size
}
