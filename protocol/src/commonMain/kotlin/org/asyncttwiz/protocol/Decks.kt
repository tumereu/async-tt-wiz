package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.asyncttwiz.core.EntityId

/** Plain immutable metadata, never executable rules. Face order is semantic; links are explicit.
 * [colorIndicator] and [parts] are printed values a rules engine reads; absent ones are left out of the JSON, so
 * definitions made before they existed keep their bytes and digests. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
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
    /** The face's colour indicator as colour letters in WUBRG order ("G", "WB"), on faces that have one (CR 204). */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val colorIndicator: String? = null,
    /** The printed parts of one physical face that holds several: a split card's halves, an adventurer card's creature
     * and Adventure, a flip card's two halves (PhysicalCardFaces), in printed order. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val parts: List<PrintedPart> = emptyList(),
)

/** One printed part of a [DefinitionFace] that holds several, with its own printed values. */
@Serializable
data class PrintedPart(
    val name: String,
    val typeLine: String = "",
    val manaCost: String = "",
    val rulesText: String = "",
    val power: String = "",
    val toughness: String = "",
    val loyalty: String = "",
    val defense: String = "",
    val colorIndicator: String? = null,
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
/** A card list's sections. Decks have all three; cubes have Main and Maybe. Maybe holds cards being considered: never
 * dealt or drafted; a deck's Maybe comes along to its games outside the game with Side (LOB-141), never into the library. */
@Serializable enum class SavedDeckSection { MAINBOARD, SIDEBOARD, MAYBEBOARD;
    companion object {
        /** The sections a game's deck preparation shows and moves cards between, in order (LOB-047); a prepared deck's
         * Maybe comes along unchanged. */
        val PREPARED = listOf(MAINBOARD, SIDEBOARD)
        /** A cube's sections, in order. */
        val CUBE = listOf(MAINBOARD, MAYBEBOARD)
    }
}
/**
 * The one swipe rule wherever cards move between sections (owner, 2026-11-09: the card list editor LOB-105, deck
 * preparation LOB-048, Build DRF-099 and its review, Prepare cube DRF-141): in the order of [sections] (Main, Side, Maybe,
 * those present), a swipe right moves a card one section up and a swipe left one down, wrapping (right from the first goes
 * to the last, left from the last to the first); with two sections either way goes to the other.
 */
object SectionSwipe {
    fun <T> target(sections: List<T>, from: T, right: Boolean): T {
        val at = sections.indexOf(from)
        require(at >= 0) { "$from is none of $sections" }
        return sections[(at + (if (right) -1 else 1) + sections.size) % sections.size]
    }
}
@Serializable data class SavedDeckEntry(val section: SavedDeckSection, val definition: String, val quantity: Int)
/**
 * One card of a deck or cube save (owner 2026-11-10: card data from the server's card catalog): [quantity] copies in
 * [section] of the card [printingId] is a printing of (any face of a card of several faces names the whole card), its card
 * data built by the server from its catalog; or, for a card the list already holds from before (an editor keeps it as
 * stored), that card's digest. The same card named twice in a section is one entry, its copies added up.
 */
@Serializable data class CardListEntry(val section: SavedDeckSection, val printingId: String, val quantity: Int)
/** A new deck, in one request: its [entries] and its [cover], the digest of one of its cards as the server builds them. */
@Serializable data class CreateDeck(val requestId: String, val name: String, val entries: List<CardListEntry>, val cover: String? = null)
@Serializable data class DeckCover(val definition: String, val name: String, val scryfallId: String? = null, val imageUri: String? = null)
/** Present only on a draft deck: saved automatically from a pod deck submission, read-only until copied. */
@Serializable data class DraftDeckOrigin(val podId: String, val podName: String)
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class DeckSummary(val id: String, val name: String, val revision: String, val createdAt: String,
    val mainboard: Long = 0, val sideboard: Long = 0, val cover: DeckCover? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val draft: DraftDeckOrigin? = null,
    /** Copies in Maybe; omitted when none. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val maybeboard: Long = 0,
    /** Which cards Automatum can't play yet (I-D51, LOB-124), worked out by the server from its card catalog's marks
     * whenever it reads the deck; null when it has no catalog. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val automatum: CardListSupport? = null)
@Serializable data class SavedDeck(val deck: DeckSummary, val entries: List<SavedDeckEntry>)
/** I-D51: the cards of a deck, cube or set Automatum can't play yet, by the card catalog's marks (SYS-082), made with the
 * parser of [parserVersion]. [byHand] names those cards (distinct, sorted); empty: Automatum plays every card. */
@Serializable data class CardListSupport(val parserVersion: Int, val byHand: List<String> = emptyList(),
    /** The rules engine's support version (`Engine.SUPPORT_VERSION`) it was worked out with; 0 before it was kept. */
    val engineVersion: Int = 0) {
    val supported: Boolean get() = byHand.isEmpty()
}
@Serializable data class DeckList(val decks: List<DeckSummary>, val next: String? = null)
/** Omitted fields are unchanged. Cover is an exact attached definition, never an arbitrary URL.
 * New contents always replace the whole list. */
@Serializable data class DeckContentsImport(val entries: List<CardListEntry>)
@Serializable data class UpdateDeck(val requestId: String, val name: String? = null, val cover: String? = null,
    val contents: DeckContentsImport? = null)
@Serializable data class DeleteDeck(val requestId: String)
/** POST /v1/decks/{id}/copy: "Save as deck". An independent ordinary saved deck with the same contents and cover. */
@Serializable data class CopyDeck(val requestId: String)
/** Draft decks: GET /v1/decks?drafts=true lists them newest first; GET /v1/decks lists only ordinary decks.
 * Saving a new one beyond [LIMIT] deletes the account's oldest draft deck. */
object DraftDecks {
    const val LIMIT = 20
}
@Serializable data class DeckDeleted(val id: String)
@Serializable data class DeckError(val code: String, val field: String? = null)

/** RFC8785 subset: typed schemas contain only strings, booleans, null and exact 32-bit integers.
 * Expand defaults/nulls, sort object keys by UTF-16 and sets explicitly; ordered arrays stay ordered.
 */
object DeckJson {
    val format = Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = false }
    private fun canonical(value: JsonElement): String = StringBuilder().also { canonical(value, it) }.toString()
    // One builder for the whole value (every history event's seal canonicalizes the event).
    private fun canonical(value: JsonElement, out: StringBuilder) {
        when (value) {
            is JsonObject -> {
                out.append('{')
                value.keys.sorted().forEachIndexed { i, key ->
                    if (i > 0) out.append(',')
                    quoted(key, out)
                    out.append(':')
                    canonical(value.getValue(key), out)
                }
                out.append('}')
            }
            is JsonArray -> {
                out.append('[')
                value.forEachIndexed { i, element -> if (i > 0) out.append(','); canonical(element, out) }
                out.append(']')
            }
            is JsonPrimitive -> if (value.isString) quoted(value.content, out) else out.append(value.toString())
        }
    }
    /** As the JSON library prints a string; one that needs no escape is copied as it is. */
    private fun quoted(text: String, out: StringBuilder) {
        if (text.any { it < ' ' || it == '"' || it == '\\' }) out.append(JsonPrimitive(text).toString())
        else out.append('"').append(text).append('"')
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
    const val MAX_CARDS = 1000
    const val FIELD_BYTES = 64 * 1024
    fun uuid(value: String): Boolean = runCatching { EntityId(value) }.isSuccess
    fun digest(value: String): Boolean = Regex("sha256:[0-9a-f]{64}").matches(value)
    fun text(value: String): Boolean = text(value, FIELD_BYTES)
    /** Text PostgreSQL can keep (no NUL, whole surrogate pairs) of at most [maxBytes] UTF-8 bytes. */
    fun text(value: String, maxBytes: Int): Boolean {
        var index = 0
        while (index < value.length) {
            val char = value[index++]
            if (char.isHighSurrogate()) {
                if (index == value.length || !value[index++].isLowSurrogate()) return false
            } else if (char.isLowSurrogate()) return false
            if (char == '\u0000') return false // PostgreSQL text cannot retain NUL.
        }
        return value.encodeToByteArray().size <= maxBytes
    }
    /** Card data the server built from its catalog (or a test made) that it stores and games read: one physical card of
     * consistently linked faces with ids, and text PostgreSQL keeps. Nothing a device sends is checked here any more: no
     * device sends card data (owner 2026-11-10). */
    fun definition(value: CardDefinition): Boolean {
        if (value.schemaVersion != 1 || value.name.isBlank() || value.faces.size !in 1..64) return false
        val strings = listOfNotNull(value.name, value.provider, value.sourceVersion, value.printingId, value.layout) +
            value.faces.flatMap { listOfNotNull(it.faceId, it.name, it.typeLine, it.manaCost, it.rulesText,
                it.power, it.toughness, it.loyalty, it.defense, it.corner, it.scryfallId, it.sourceData) +
                it.parts.flatMap { part -> listOf(part.name, part.typeLine, part.manaCost, part.rulesText, part.power,
                    part.toughness, part.loyalty, part.defense) } }
        if (!strings.all(::text)) return false
        val indicators = value.faces.flatMap { face -> listOf(face.colorIndicator) + face.parts.map { it.colorIndicator } }
        if (!indicators.all { it == null || (it.isNotEmpty() && COLOR_INDICATOR.matches(it)) } || value.faces.any { it.parts.size > 64 }) return false
        val ids = value.faces.map { it.faceId }.toSet()
        if (ids.size != value.faces.size || !ids.all(::uuid)) return false
        return value.faces.all { face -> face.name.isNotBlank() &&
            face.otherFaceIds.size == ids.size - 1 && face.otherFaceIds.toSet() == ids - face.faceId &&
            (face.scryfallId == null || uuid(face.scryfallId)) }
    }
    private val COLOR_INDICATOR = Regex("W?U?B?R?G?")
    fun quantity(value: Int): Boolean = value in 1..MAX_CARDS
    fun cardCount(value: Long): Boolean = value in 1..MAX_CARDS.toLong()
    fun quantities(value: List<Int>): Boolean = value.all(::quantity) && cardCount(value.sumOf { it.toLong() })
    fun entries(value: List<SavedDeckEntry>): Boolean = value.isNotEmpty() &&
        quantities(value.map { it.quantity }) && value.all { digest(it.definition) } &&
        value.map { it.section to it.definition }.distinct().size == value.size
    /** A save's cards ([CardListEntry]): some, each a printing id or a digest, at most [MAX_CARDS] copies in all. */
    fun input(value: List<CardListEntry>): Boolean = value.isNotEmpty() && quantities(value.map { it.quantity }) &&
        value.all { uuid(it.printingId) || digest(it.printingId) }
    fun create(value: CreateDeck): Boolean = text(value.name) && value.name.isNotBlank() && input(value.entries) &&
        (value.cover == null || digest(value.cover))
}
