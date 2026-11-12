package org.asyncttwiz.storage

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Where a picture's bytes come from on Scryfall (both clients' artwork repositories): the image addresses of a card's
 * API answer, and which bytes are pictures at all. */
object ScryfallImages {
    /** What a card's API answer gives for a picture: its image addresses, best first, or the meld result to look up
     * instead. */
    sealed interface Lookup {
        data class Images(val urls: List<String>) : Lookup
        data class MeldResult(val printing: String) : Lookup
    }

    /** The image addresses for [reference] in [metadata], the API answer for card [id]; throws when it isn't that card. */
    fun lookup(reference: ArtworkReference, metadata: String, id: String): Lookup {
        val card = jsonObject(metadata)
        check(card["object"]?.asText() == "card" && card["id"]?.asText() == id)
        val top = card["image_uris"]?.let { it as JsonObject }
        val faces = card["card_faces"]?.let { it as kotlinx.serialization.json.JsonArray }?.map { it.jsonObject }.orEmpty()
        fun JsonObject.name() = this["name"]?.asText()
        // Reversible cards and some double-faced tokens print the SAME name on both sides: the
        // provider index is exact when its name agrees; the name only corrects a differing index.
        val indexed = faces.getOrNull(reference.face)
        val named = reference.faceName?.let { name -> indexed?.takeIf { it.name() == name }
            ?: faces.firstOrNull { it.name() == name } }
        val layout = card["layout"]?.asText()
        // Spell parts share a physical image, even when MTGJSON labels the row b/c.
        // A named top-level printing (meld parts and separately printed token sides) is also exact.
        val shared = layout in setOf("split", "aftermath", "adventure", "flip", "room")
        val topMatches = reference.faceName != null && card.name() == reference.faceName
        val images = if (top != null && (shared || topMatches || reference.face == 0)) top else
            (named ?: indexed)?.get("image_uris")?.let { it as JsonObject }
        if (layout == "meld" && reference.faceName != null && !topMatches) {
            val result = card["all_parts"]?.let { it as kotlinx.serialization.json.JsonArray }?.firstOrNull {
                it.jsonObject["component"]?.asText() == "meld_result" && it.jsonObject.name() == reference.faceName
            }?.jsonObject?.get("id")?.asText()
            if (result != null) return Lookup.MeldResult(result)
        }
        if (images == null) return Lookup.Images(emptyList())
        // Never replace an art crop with a full card, or a physical back with a front.
        val sizes = if (reference.size == "art_crop") listOf("art_crop") else
            listOf(reference.size, "normal", "large", "png", "small").distinct()
        return Lookup.Images(sizes.mapNotNull { images[it]?.asText() }.distinct())
    }

    /** Only raster bytes are ever decoded. Content-Type alone cannot admit HTML/SVG. */
    fun raster(bytes: ByteArray, type: String): Boolean = when (type.lowercase()) {
        "image/png" -> bytes.size >= 8 && bytes.take(8) == listOf(137, 80, 78, 71, 13, 10, 26, 10).map(Int::toByte)
        "image/jpeg" -> bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
        "image/webp" -> bytes.size >= 12 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
            bytes.copyOfRange(8, 12).decodeToString() == "WEBP"
        else -> false
    }

    /** A Scryfall card id as the API names it: a lower-case UUID. */
    fun validId(id: String): Boolean = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(id)
}
