package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/**
 * I-D51 (LOB-115, DRF-161): works out the Automatum support of the signed-in player's saved decks (draft decks too) and
 * cubes that have none for this app's [parserVersion] and [engineVersion] — lists saved before it existed, draft decks
 * and copies, lists whose cards changed elsewhere, and every list after a parser or engine change — and stores it with
 * each list on the server.
 * [support] works a list out from its card data (blocking). Lists are read one at a time; throws on a service failure
 * (tried again later).
 */
internal class AutomatumSupportRun(private val decks: Decks, private val cubes: Cubes,
    private val parserVersion: Int, private val engineVersion: Int,
    private val support: (Collection<SubmittedDefinition>) -> CardListSupport,
    /** Waits before each list is read (owner playtest 2026-11-06: the run keeps well inside the deck service's
     * per-account limit, so a deck chosen meanwhile is never refused as busy). */
    private val pace: suspend () -> Unit = {}) {
    data class Outcome(val decks: Int = 0, val cubes: Int = 0)

    private fun needed(value: CardListSupport?) = value == null || value.parserVersion != parserVersion || value.engineVersion != engineVersion

    suspend fun run(token: String): Outcome {
        var deckCount = 0
        val summaries = mutableListOf<DeckSummary>()
        var after: String? = null
        do {
            val page = decks.list(token, after)
            summaries += page.decks
            after = page.next
        } while (after != null)
        summaries += decks.drafts(token).decks
        for (summary in summaries.filter { needed(it.automatum) }.distinctBy { it.id }) {
            pace()
            val saved = decks.read(token, summary.id)
            val definitions = decks.definitions(token, summary.id, saved.entries.map { it.definition })
            if (decks.setSupport(token, summary.id, SetCardListSupport(saved.deck.revision, support(definitions))).stored) deckCount++
        }
        var cubeCount = 0
        after = null
        do {
            val page = cubes.listCubes(token, after)
            for (summary in page.cubes.filter { needed(it.automatum) }) {
                pace()
                val saved = cubes.readCube(token, summary.id)
                val definitions = saved.entries.map { it.definition }.distinct().chunked(CubeValidation.MAX_DEFINITIONS_PER_READ)
                    .flatMap { cubes.cubeDefinitions(token, summary.id, ReadCubeDefinitions(it)).definitions }
                if (cubes.setCubeSupport(token, summary.id, SetCardListSupport(saved.cube.revision, support(definitions))).stored) cubeCount++
            }
            after = page.next
        } while (after != null)
        return Outcome(deckCount, cubeCount)
    }
}
