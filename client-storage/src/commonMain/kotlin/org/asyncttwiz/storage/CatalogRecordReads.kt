package org.asyncttwiz.storage

/** The picture reference of catalog printing [record]'s face (its side), or null when its side isn't a face index. */
fun ArtworkReference.Companion.from(record: CatalogPrinting, size: String = "normal"): ArtworkReference? {
    val face = if (record.side.isEmpty()) 0 else record.side.singleOrNull()?.let { it - 'a' } ?: return null
    if (face !in 0..15) return null
    val name = jsonObjectOrNull(record.json)?.primitive("faceName")?.takeIf { it.isNotBlank() } ?: record.name
    return ArtworkReference(record.scryfallId, face, size, faceName = name)
}

/** Each name a player can choose for this card with its own type line (CR 201.4b-f): a split card's halves, a
 * double-faced card's sides, an alternative name, each apart from the others. */
fun CatalogCard.namedParts(): List<CatalogNamedPart> = faces.map { face ->
    val raw = jsonObject(face.json)
    fun field(key: String) = raw.primitive(key).orEmpty()
    CatalogNamedPart(field("faceName").ifBlank { face.name }, field("type"))
}
