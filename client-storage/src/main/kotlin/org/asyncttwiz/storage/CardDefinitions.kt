package org.asyncttwiz.storage

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Layout
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.asyncttwiz.protocol.DefinitionFace
import org.asyncttwiz.protocol.PrintedPart
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.CardDefinition as GameCard

/** What the device knows about one game card's rules (docs/specs/arcanum-atw-integration.md I-D2). */
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
 * Arcanum card definitions of a game's cards, parsed on the device with [OracleParser] from the game's own card data
 * (its [SubmittedDefinition]s, I-D2) and cached in [ClientStorage] by definition digest and parser version.
 * Definitions of other parser versions are dropped on first use. Blocking: call on a worker, never the UI thread.
 */
class CardDefinitions internal constructor(
    private val storage: ClientStorage,
    private val parserVersion: Int,
    private val parse: (Layout, List<OracleCard>) -> CardDefinition,
) {
    constructor(storage: ClientStorage) : this(storage, OracleParser.VERSION, OracleParser::parseCard)

    @Volatile private var evicted = false

    /** Every given card by its definition digest. The digest names the definition exactly (the server checks it). */
    fun forDefinitions(submitted: Collection<SubmittedDefinition>): Map<String, ParsedCard> {
        if (!evicted) { storage.evictCardDefinitionsExcept(parserVersion.toLong()); evicted = true }
        val cards = submitted.associateBy { it.digest }
        if (cards.isEmpty()) return emptyMap()
        val cached = storage.cachedCardDefinitions(parserVersion.toLong(), cards.keys).associateBy { it.digest }
        val result = LinkedHashMap<String, ParsedCard>()
        val fresh = mutableListOf<CachedCardDefinition>()
        for ((digest, card) in cards) {
            val hit = cached[digest]?.let(::decode)
            if (hit != null) { result[digest] = hit; continue }
            val parsed = when (val printed = PrintedCard.of(card.definition)) {
                is PrintedCard.Card -> try {
                    val definition = parse(printed.layout, printed.faces)
                    ParsedCard.Read(definition, definition.faces.sumOf { face -> face.abilities.count { it is Ability.Unparsed } })
                } catch (error: RuntimeException) {
                    // The parser throws on printed values it can't read (a type line, a mana cost).
                    ParsedCard.Unreadable(error.message ?: error::class.java.simpleName)
                }
                is PrintedCard.Unreadable -> ParsedCard.Unreadable(printed.problem)
            }
            result[digest] = parsed
            fresh += when (parsed) {
                is ParsedCard.Read -> CachedCardDefinition(digest, json.encodeToString(CardDefinition.serializer(),
                    parsed.definition), parsed.manualAbilities.toLong(), null)
                is ParsedCard.Unreadable -> CachedCardDefinition(digest, null, 0, parsed.problem)
            }
        }
        if (fresh.isNotEmpty()) storage.cacheCardDefinitions(parserVersion.toLong(), fresh)
        return result
    }

    private fun decode(entry: CachedCardDefinition): ParsedCard? = when {
        entry.definitionJson != null -> try {
            ParsedCard.Read(json.decodeFromString(CardDefinition.serializer(), entry.definitionJson), entry.manualAbilities.toInt())
        } catch (_: IllegalArgumentException) {
            null // Written by an older definition model: parse again.
        }
        else -> ParsedCard.Unreadable(entry.problem.orEmpty())
    }

    companion object {
        private val json = Json

        /** The parser version results are kept for ([OracleParser.VERSION]); a new one works them out again (I-D51). */
        val parserVersion: Int get() = OracleParser.VERSION

        /** Every subtype the pinned Comprehensive Rules list ([OracleParser.subtypes]): a choice of a creature type with
         * no given list offers those that are creature types, never only the game's (I-D27, CR 205.3m). */
        val ruleSubtypes: Set<String> get() = OracleParser.subtypes
    }
}

/** One row of the card definition cache: [definitionJson] null exactly when [problem] says why the card is
 * unreadable. */
internal data class CachedCardDefinition(val digest: String, val definitionJson: String?, val manualAbilities: Long,
    val problem: String?)

/** A game card as the Arcanum parser reads it: its layout and printed faces. */
internal sealed interface PrintedCard {
    data class Card(val layout: Layout, val faces: List<OracleCard>) : PrintedCard
    data class Unreadable(val problem: String) : PrintedCard

    companion object {
        /** Layouts whose parts share one physical face (PhysicalCardFaces keeps them as [DefinitionFace.parts]). */
        private val SHARED_FACE = setOf("split", "aftermath", "adventure", "flip")

        fun of(card: GameCard): PrintedCard {
            val single = card.faces.singleOrNull()
            val faces = when {
                single != null && single.parts.isNotEmpty() -> single.parts.map { part(it) }
                // Made before parts were kept: the halves are only in the combined text.
                single != null && card.layout in SHARED_FACE -> return Unreadable("the parts of this ${card.layout} card aren't in its card data")
                else -> card.faces.map { face(it, card.token) }
            }
            val layout = if (faces.size == 1) Layout.Normal else when (card.layout) {
                // An aftermath card is a split card whose second half has aftermath (CR 702.127).
                "split", "aftermath" -> Layout.Split
                "flip" -> Layout.Flip
                "transform" -> Layout.Transform
                "modal_dfc" -> Layout.ModalDoubleFaced
                "adventure" -> Layout.Adventure
                "omen" -> Layout.Omen
                else -> return Unreadable("faces of layout ${card.layout}")
            }
            return Card(layout, faces)
        }

        private fun face(face: DefinitionFace, token: Boolean) = OracleCard(
            name = face.name,
            manaCost = face.manaCost.ifBlank { null },
            // A token record's type line starts with "Token", which isn't a card type.
            typeLine = if (token) face.typeLine.removePrefix("Token ") else face.typeLine,
            text = face.rulesText,
            power = face.power.ifBlank { null },
            toughness = face.toughness.ifBlank { null },
            loyalty = face.loyalty.ifBlank { null },
            defense = face.defense.ifBlank { null },
            // Card data made before the field existed: read it from the catalog record the face was made from.
            colorIndicator = face.colorIndicator ?: face.sourceData?.let { data ->
                runCatching { catalogColorIndicator(Json.parseToJsonElement(data).jsonObject) }.getOrNull()
            },
        )

        private fun part(part: PrintedPart) = OracleCard(
            name = part.name,
            manaCost = part.manaCost.ifBlank { null },
            typeLine = part.typeLine,
            text = part.rulesText,
            power = part.power.ifBlank { null },
            toughness = part.toughness.ifBlank { null },
            loyalty = part.loyalty.ifBlank { null },
            defense = part.defense.ifBlank { null },
            colorIndicator = part.colorIndicator,
        )
    }
}
