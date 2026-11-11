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
import org.asyncttwiz.protocol.game.GameCards
import org.asyncttwiz.protocol.CardDefinition as GameCard

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
            val parsed = GameCards.parse(card.definition, parse).let { p ->
                p.definition?.let { ParsedCard.Read(it, p.manualAbilities) } ?: ParsedCard.Unreadable(p.problem.orEmpty())
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
