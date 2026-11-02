package org.asyncttwiz.storage

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Layout
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest

/** What the device knows about one catalog card's rules (docs/specs/arcanum-atw-integration.md I-D2). */
sealed interface ParsedCard {
    /** The card parsed: [definition] keeps every ability the parser couldn't read as [Ability.Unparsed] (played by
     * hand); [manualAbilities] counts them over all faces. */
    data class Read(val definition: CardDefinition, val manualAbilities: Int) : ParsedCard {
        val fullyParsed: Boolean get() = manualAbilities == 0
    }

    /** The card's printed values (type line, mana cost, P/T, faces) couldn't be read, so the whole card stays
     * manual; [problem] says why. */
    data class Unreadable(val problem: String) : ParsedCard
}

/**
 * Arcanum card definitions of installed catalog cards, parsed on the device with [OracleParser] when a game needs
 * them (I-D2) and cached in [ClientStorage] by catalog card id and parser version. A cached definition is used while
 * the parser version and the card's catalog records are unchanged; otherwise the card is parsed again. Definitions
 * of other parser versions are dropped on first use. Blocking: call on a worker, never the UI thread.
 */
class CardDefinitions internal constructor(
    private val catalog: CatalogRepository,
    private val storage: ClientStorage,
    private val parserVersion: Int,
    private val parse: (Layout, List<OracleCard>) -> CardDefinition,
) {
    constructor(catalog: CatalogRepository, storage: ClientStorage) :
        this(catalog, storage, OracleParser.VERSION, OracleParser::parseCard)

    @Volatile private var evicted = false

    /**
     * The cards with these catalog printing ids (any face's id; a game card's `CardDefinition.printingId`). Ids not in
     * the installed catalog, or every id while no catalog is installed, are absent from the result.
     */
    fun forCards(ids: Collection<String>): Map<String, ParsedCard> {
        if (!evicted) { storage.evictCardDefinitionsExcept(parserVersion.toLong()); evicted = true }
        val sources = catalog.snapshot { index -> index?.let { ids.distinct().mapNotNull { id -> source(it, id)?.let { id to it } }.toMap() } }
            .orEmpty()
        if (sources.isEmpty()) return emptyMap()
        val cached = storage.cachedCardDefinitions(parserVersion.toLong(), sources.keys).associateBy { it.cardId }
        val result = LinkedHashMap<String, ParsedCard>()
        val fresh = mutableListOf<CachedCardDefinition>()
        for ((id, source) in sources) {
            val hit = cached[id]?.takeIf { it.sourceDigest == source.digest }?.let(::decode)
            if (hit != null) { result[id] = hit; continue }
            val parsed = when (val card = source.card) {
                is CatalogOracleCard.Card -> try {
                    val definition = parse(card.layout, card.faces)
                    ParsedCard.Read(definition, definition.faces.sumOf { face -> face.abilities.count { it is Ability.Unparsed } })
                } catch (error: RuntimeException) {
                    // The parser throws on printed values it can't read (a type line, a mana cost).
                    ParsedCard.Unreadable(error.message ?: error::class.java.simpleName)
                }
                is CatalogOracleCard.Unreadable -> ParsedCard.Unreadable(card.problem)
            }
            result[id] = parsed
            fresh += when (parsed) {
                is ParsedCard.Read -> CachedCardDefinition(id, source.digest, json.encodeToString(CardDefinition.serializer(),
                    parsed.definition), parsed.manualAbilities.toLong(), null)
                is ParsedCard.Unreadable -> CachedCardDefinition(id, source.digest, null, 0, parsed.problem)
            }
        }
        if (fresh.isNotEmpty()) storage.cacheCardDefinitions(parserVersion.toLong(), fresh)
        return result
    }

    private class Source(val digest: String, val card: CatalogOracleCard)

    private fun source(index: CatalogIndex, id: String): Source? {
        val printing = index.printing(id) ?: return null
        val linked = mutableMapOf<String, CatalogPrinting?>()
        val lookup = { face: String -> linked.getOrPut(face) { index.printing(face) } }
        val card = CatalogOracleCard.of(printing, lookup)
        // The records the card was read from, so an updated catalog record (an oracle errata) is parsed again.
        val records = listOf(printing.id to printing.json) + linked.toSortedMap().map { (face, record) -> face to record?.json.orEmpty() }
        return Source(digest(listOf("mapping-$MAPPING_VERSION") + records.flatMap { listOf(it.first, it.second) }), card)
    }

    private fun decode(entry: CachedCardDefinition): ParsedCard? = when {
        entry.definitionJson != null -> try {
            ParsedCard.Read(json.decodeFromString(CardDefinition.serializer(), entry.definitionJson), entry.manualAbilities.toInt())
        } catch (_: IllegalArgumentException) {
            null // Written by an older definition model: parse again.
        }
        else -> ParsedCard.Unreadable(entry.problem.orEmpty())
    }

    private companion object {
        /** Bump with any change to how catalog records become [OracleCard]s, so cached definitions are parsed again. */
        const val MAPPING_VERSION = 1
        val json = Json

        fun digest(parts: List<String>): String = MessageDigest.getInstance("SHA-256").run {
            parts.forEach { update(it.toByteArray(Charsets.UTF_8)); update(0) }
            digest().joinToString("") { "%02x".format(it) }
        }
    }
}

/** One row of the card definition cache: [definitionJson] null exactly when [problem] says why the card is
 * unreadable. */
internal data class CachedCardDefinition(val cardId: String, val sourceDigest: String, val definitionJson: String?,
    val manualAbilities: Long, val problem: String?)

/** A catalog card as the Arcanum parser reads it: its layout and printed faces, from the retained MTGJSON records. */
internal sealed interface CatalogOracleCard {
    data class Card(val layout: Layout, val faces: List<OracleCard>) : CatalogOracleCard
    data class Unreadable(val problem: String) : CatalogOracleCard

    companion object {
        /** [printing] and its linked faces ([face] looks a linked printing up by id). */
        fun of(printing: CatalogPrinting, face: (String) -> CatalogPrinting?): CatalogOracleCard {
            val record = record(printing)
            val layoutName = record.text("layout") ?: "normal"
            val linked = printing.otherFaces.map { id -> face(id) ?: return Unreadable("missing face $id") }
            if (layoutName == "meld") return meld(printing, record, linked, face)
            val printings = (linked + printing).sortedWith(compareBy<CatalogPrinting> { it.side }.thenBy { it.id })
            val layout = if (printings.size == 1) Layout.Normal else when (layoutName) {
                // An aftermath card is a split card whose second half has aftermath (CR 702.127).
                "split", "aftermath" -> Layout.Split
                "flip" -> Layout.Flip
                "transform" -> Layout.Transform
                "modal_dfc" -> Layout.ModalDoubleFaced
                "adventure" -> Layout.Adventure
                "omen" -> Layout.Omen
                else -> return Unreadable("faces of layout $layoutName")
            }
            return Card(layout, printings.map(::face))
        }

        /** A meld card (CR 712, 701.42): its front face, and the melded back face it shares with its partner, whose name the
         * layout keeps. The melded face alone isn't a card. */
        private fun meld(printing: CatalogPrinting, record: JsonObject, linked: List<CatalogPrinting>,
            face: (String) -> CatalogPrinting?): CatalogOracleCard {
            val backName = printing.name.substringAfter(" // ", "")
            if (backName.isEmpty() || faceName(printing, record) == backName) return Unreadable("melded face of ${printing.name}")
            val back = linked.firstOrNull { faceName(it, record(it)) == backName } ?: return Unreadable("missing melded face $backName")
            val partner = back.otherFaces.filter { it != printing.id }.mapNotNull(face).map { faceName(it, record(it)) }
                .firstOrNull { it != backName } ?: return Unreadable("missing meld partner of ${printing.name}")
            return Card(Layout.Meld(partner), listOf(face(printing), face(back)))
        }

        private fun face(printing: CatalogPrinting): OracleCard {
            val record = record(printing)
            val manaCost = record.text("manaCost")
            return OracleCard(
                name = faceName(printing, record),
                manaCost = manaCost,
                // A token record's type line starts with "Token", which isn't a card type.
                typeLine = record.text("type").orEmpty().let { if (printing.token) it.removePrefix("Token ") else it },
                text = record.text("text").orEmpty(),
                power = record.text("power"),
                toughness = record.text("toughness"),
                loyalty = record.text("loyalty"),
                defense = record.text("defense"),
                // The catalog keeps a face's colours, not its colour indicator: a coloured face without a mana cost
                // has its colour from an indicator (CR 202.2, 204).
                colorIndicator = if (manaCost == null) colorIndicator(record) else null,
            )
        }

        private fun colorIndicator(record: JsonObject): String? =
            (record["colors"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?.sortedBy { "WUBRG".indexOf(it) }?.joinToString("")?.takeIf { it.isNotEmpty() }

        private fun faceName(printing: CatalogPrinting, record: JsonObject) = record.text("faceName") ?: printing.name
        private fun record(printing: CatalogPrinting) = Json.parseToJsonElement(printing.json).jsonObject
        private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }
}
