package org.asyncttwiz.catalog

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.DefinitionFace
import org.asyncttwiz.protocol.PhysicalCardFaces
import org.asyncttwiz.protocol.defaultCorner
import org.asyncttwiz.protocol.game.GameCards

/**
 * Card data from catalog records, as a device makes it from its card records for a deck (client-storage's
 * `ResolvedDeckEntry.definition`): the sides of one physical card, each face with its printed values, its picture and its
 * printing's record ([sourceData]). The server builds set packs with it (section 3.7) and the catalog build checks support
 * with it (section 3.8).
 */
object CatalogCards {
    private const val FRONT = "a"
    private const val REVERSIBLE = "reversible_card"

    /** A printing and the faces it links to (itself included), sides in order. */
    fun linked(catalog: CardCatalog, printing: CatalogPrintingRecord): List<CatalogPrintingRecord> =
        (printing.others + printing.id).distinct().mapNotNull { catalog.printing[it] }
            .sortedWith(compareBy<CatalogPrintingRecord> { catalog.faces[it.face].side }.thenBy { it.id })

    /** Problem report 7ZZC89: whether [printing], with [linked] ([linked]), is a meld result, the shared back of two
     * fronts: never a card of its own. */
    fun meldResult(catalog: CardCatalog, printing: CatalogPrintingRecord, linked: List<CatalogPrintingRecord>): Boolean =
        catalog.faces[printing.face].side != FRONT && linked.count { catalog.faces[it.face].side == FRONT } > 1

    /** The sides of one physical card from [linked]: of two fronts (a meld pair) only the first stays (CR 712.4). */
    fun physicalSides(catalog: CardCatalog, linked: List<CatalogPrintingRecord>): List<CatalogPrintingRecord> {
        val front = linked.firstOrNull { catalog.faces[it.face].side == FRONT }
        return linked.filter { catalog.faces[it.face].side != FRONT || it === front }
    }

    /** The card data of the physical card printing [printing] is a face of. */
    fun definition(catalog: CardCatalog, printing: CatalogPrintingRecord): CardDefinition =
        definition(catalog, physicalSides(catalog, linked(catalog, printing)))

    /** A reversible printing (layout "reversible_card": the same card on both sides) is the card it shows. */
    fun reversible(face: CatalogFaceRecord): Boolean = face.layout == REVERSIBLE

    /** The name of the card [face] belongs to: a reversible printing's is the card it shows ([reversible]). */
    fun cardName(face: CatalogFaceRecord): String = if (reversible(face) && face.faceName.isNotEmpty()) face.faceName else face.name

    /** The card data of one physical card's [sides] (from [physicalSides]), the first naming it. A reversible printing
     * is the card its first side shows, a card of one face ([reversible]). */
    fun definition(catalog: CardCatalog, sides: List<CatalogPrintingRecord>): CardDefinition {
        val first = catalog.faces[sides.first().face]
        if (reversible(first)) return definition(sides.first().copy(others = emptyList()),
            first.copy(name = cardName(first), faceName = "", layout = "normal", side = ""), catalog.header)
        return definition(catalog.header, sides) { catalog.faces[it.face] }
    }

    private fun definition(printing: CatalogPrintingRecord, face: CatalogFaceRecord, header: CatalogHeader): CardDefinition =
        definition(header, listOf(printing)) { face }

    private fun definition(header: CatalogHeader, sides: List<CatalogPrintingRecord>,
        faceOf: (CatalogPrintingRecord) -> CatalogFaceRecord): CardDefinition {
        val ids = sides.map { it.id }.toSet()
        val faces = sides.map { side ->
            val face = faceOf(side)
            val record = sourceRecord(side, face)
            DefinitionFace(side.id, face.faceName.ifBlank { face.name }, side.others.filter { it in ids }, face.type,
                face.manaCost, face.text, face.power, face.toughness, face.loyalty, face.defense, scryfallId = side.scryfall,
                sourceData = record.toString(), colorIndicator = GameCards.colorIndicator(record))
                .let { it.copy(corner = it.defaultCorner()) }
        }
        val first = sides.first()
        val face = faceOf(first)
        val definition = CardDefinition(face.name, faces, provider = "mtgjson",
            sourceVersion = "${header.mtgjsonDate}/${header.mtgjsonVersion}", printingId = first.id,
            layout = face.layout, token = first.token)
        // Linked token/designation records are physical sides, even for provider layouts such as double_faced_token
        // or token. Do not flatten them like split/adventure spell parts.
        return if (first.token && definition.layout !in setOf("adventure", "split", "flip", "aftermath")) definition
            else PhysicalCardFaces.normalize(definition)
    }

    /** A printing's record as the app keeps it (MtgJsonCatalog's retained fields, in that order), made from the catalog. */
    fun sourceRecord(printing: CatalogPrintingRecord, face: CatalogFaceRecord): JsonObject = buildJsonObject {
        fun text(key: String, value: String) { if (value.isNotEmpty()) put(key, value) }
        put("uuid", printing.id)
        put("name", face.name)
        put("setCode", printing.set)
        put("number", printing.number)
        put("language", "English")
        text("side", face.side)
        if (printing.others.isNotEmpty()) putJsonArray("otherFaceIds") { printing.others.forEach { add(JsonPrimitive(it)) } }
        text("faceName", face.faceName)
        put("layout", face.layout)
        text("type", face.type)
        text("manaCost", face.manaCost)
        text("text", face.text)
        text("power", face.power)
        text("toughness", face.toughness)
        text("loyalty", face.loyalty)
        text("defense", face.defense)
        putJsonArray("colors") { face.colors.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("availability") { printing.availability.forEach { add(JsonPrimitive(it)) } }
        if (printing.onlineOnly) put("isOnlineOnly", true)
        if (printing.promo) put("isPromo", true)
        putJsonObject("identifiers") {
            printing.scryfall?.let { put("scryfallId", it) }
            if (face.oracle.isNotEmpty()) put("scryfallOracleId", face.oracle)
        }
    }
}
