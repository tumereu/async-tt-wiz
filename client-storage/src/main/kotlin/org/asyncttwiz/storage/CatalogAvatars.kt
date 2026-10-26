package org.asyncttwiz.storage

import com.google.gson.JsonParser
import org.asyncttwiz.protocol.Avatar
import org.asyncttwiz.protocol.DeckValidation

data class CatalogAvatar(val name: String, val avatar: Avatar)

/** MTGJSON printings with a Scryfall reference can resolve art independently of the catalog. */
fun CatalogPrinting.avatarChoice(legendaryOnly: Boolean): CatalogAvatar? {
    val id = scryfallId?.takeIf(DeckValidation::uuid) ?: return null
    val face = if (side.isEmpty()) 0 else side.singleOrNull()?.let { it - 'a' } ?: return null
    if (face !in 0..15) return null
    val card = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
    if (legendaryOnly) {
        // Only the type line survives the production import. Subtypes are not card types.
        val types = card.get("type")?.takeUnless { it.isJsonNull }?.asString
            ?.substringBefore('—')?.split(' ').orEmpty()
        if (token || "Legendary" !in types || types.none { it == "Creature" || it == "Planeswalker" }) return null
    }
    return CatalogAvatar(name, Avatar(id, face))
}
