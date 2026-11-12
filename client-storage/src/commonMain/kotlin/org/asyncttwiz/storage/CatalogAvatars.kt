package org.asyncttwiz.storage

import org.asyncttwiz.protocol.Avatar
import org.asyncttwiz.protocol.DeckValidation

/** MTGJSON printings with a Scryfall reference can resolve art independently of the catalog. */
fun CatalogPrinting.avatarChoice(legendaryOnly: Boolean): CatalogAvatar? {
    val id = scryfallId?.takeIf(DeckValidation::uuid) ?: return null
    val face = if (side.isEmpty()) 0 else side.singleOrNull()?.let { it - 'a' } ?: return null
    if (face !in 0..15) return null
    val card = jsonObjectOrNull(json) ?: return null
    if (legendaryOnly) {
        // Only the type line survives the production import. Subtypes are not card types.
        val types = card.present("type")?.asText()
            ?.substringBefore('—')?.split(' ').orEmpty()
        if (token || "Legendary" !in types || types.none { it == "Creature" || it == "Planeswalker" }) return null
    }
    return CatalogAvatar(name, Avatar(id, face))
}
