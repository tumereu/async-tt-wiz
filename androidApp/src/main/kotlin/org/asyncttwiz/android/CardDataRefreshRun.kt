package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import java.util.UUID

/**
 * I-D21: rebuilds a player's saved decks' and cubes' card data made before it kept the printed values Automatum reads
 * (split halves, colour indicators; [org.asyncttwiz.storage.CardDataRefresh]) from the installed catalog, so games
 * started from them carry those values. Each list keeps its cards, sections, quantities, name and cover; only the stale
 * cards' data is replaced, and a list with none is left alone. A card the catalog can't rebuild stays as it was (it
 * plays with its printed values, I-D21) and is reported in [Outcome.notRebuilt]; nothing is ever deleted. Draft decks
 * are read-only until saved as an ordinary deck and are left alone.
 *
 * [stale] says whether a card's data needs it; [rebuild] gives the rebuilt card data (null: the catalog can't).
 */
internal class CardDataRefreshRun(private val decks: Decks, private val cubes: Cubes,
    private val stale: (CardDefinition) -> Boolean, private val rebuild: (CardDefinition) -> CardDefinition?) {
    data class Outcome(val decksUpdated: Int = 0, val cubesUpdated: Int = 0, val notRebuilt: List<String> = emptyList())

    /** Runs over every ordinary deck and cube of the signed-in player. Throws on a service failure (retry later). */
    suspend fun run(token: String): Outcome {
        var decksUpdated = 0
        var cubesUpdated = 0
        val notRebuilt = mutableListOf<String>()
        var after: String? = null
        do {
            val page = decks.list(token, after)
            for (summary in page.decks.filter { it.draft == null }) {
                val saved = decks.read(token, summary.id)
                val digests = saved.entries.map { it.definition }.distinct()
                val definitions = digests.associateWith { decks.definition(token, summary.id, it).definition }
                val replaced = replacements(definitions, summary.name, notRebuilt)
                if (replaced.isEmpty()) continue
                submit(token, replaced.values)
                val entries = saved.entries.map { it.copy(definition = replaced[it.definition]?.digest ?: it.definition) }
                    .groupBy { it.section to it.definition }.map { (_, rows) -> rows.first().copy(quantity = rows.sumOf { it.quantity }) }
                val cover = summary.cover?.definition?.let { replaced[it]?.digest }
                decks.update(token, summary.id, UpdateDeck(UUID.randomUUID().toString(), cover = cover, contents = DeckContentsImport(entries)))
                decksUpdated++
            }
            after = page.next
        } while (after != null)
        after = null
        do {
            val page = cubes.listCubes(token, after)
            for (summary in page.cubes) {
                val saved = cubes.readCube(token, summary.id)
                val digests = saved.entries.map { it.definition }.distinct()
                val definitions = digests.chunked(CubeValidation.MAX_DEFINITIONS_PER_READ).flatMap { chunk ->
                    cubes.cubeDefinitions(token, summary.id, ReadCubeDefinitions(chunk)).definitions
                }.associate { it.digest to it.definition }
                val replaced = replacements(definitions, summary.name, notRebuilt)
                if (replaced.isEmpty()) continue
                submit(token, replaced.values)
                val entries = saved.entries.map { it.copy(definition = replaced[it.definition]?.digest ?: it.definition) }
                    .groupBy { it.section to it.definition }.map { (_, rows) -> rows.first().copy(quantity = rows.sumOf { it.quantity }) }
                cubes.update(token, summary.id, UpdateCube(UUID.randomUUID().toString(), entries = entries))
                cubesUpdated++
            }
            after = page.next
        } while (after != null)
        return Outcome(decksUpdated, cubesUpdated, notRebuilt)
    }

    /** The rebuilt card data of [definitions]' stale cards, by old digest; the ones the catalog can't rebuild are noted. */
    private fun replacements(definitions: Map<String, CardDefinition>, list: String, notRebuilt: MutableList<String>) =
        definitions.filterValues(stale).mapNotNull { (digest, definition) ->
            val rebuilt = rebuild(definition)?.takeIf(DeckValidation::definition)
            if (rebuilt == null) { notRebuilt += "${definition.name} in $list"; null }
            else digest to SubmittedDefinition(DeckJson.digest(rebuilt), rebuilt)
        }.toMap()

    private suspend fun submit(token: String, definitions: Collection<SubmittedDefinition>) {
        decks.submit(token, SubmitDefinitions(UUID.randomUUID().toString(), definitions.distinctBy { it.digest }))
    }
}
