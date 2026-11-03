package org.asyncttwiz.storage

import arcanum.api.CardDefinition
import arcanum.parser.OracleParser
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * I-D51: which cards of each draft set in the installed card records Automatum plays by hand, worked out on the device
 * from the cards' rules text with [OracleParser] ([OracleParser.VERSION]) and kept in the catalog generation beside the
 * records: per card by its oracle id and parser version (reprints are read once), per set as the names of its
 * unsupported cards. Sets missing a value for this parser version (after an import or a parser change) are worked out
 * by [refresh]. [byHand] says whether a parsed card has parts done by hand (Automatum's rule); it is given by the app,
 * which knows the engine. Blocking: run on a background worker only.
 */
class CatalogSetSupport(private val repository: CatalogRepository, private val byHand: (CardDefinition) -> Boolean,
    private val parserVersion: Int = OracleParser.VERSION) {
    /** Unsupported card names by set code, for the sets worked out with this parser version. */
    fun sets(): Map<String, List<String>> = repository.snapshot { index ->
        if (index == null || !index.hasSetSupport()) emptyMap()
        else index.select("SELECT code, by_hand FROM automatum_set WHERE parser_version=?", listOf(parserVersion)) {
            it.getString(0)!! to names(it.getString(1)!!)
        }.toMap()
    }

    /** Works out every draftable set that has no value for this parser version yet, one set per catalog lock so the
     * records stay usable meanwhile; stops early when [cancelled]. Returns how many sets it worked out. */
    fun refresh(cancelled: () -> Boolean = { false }): Int {
        val missing = repository.snapshot { index ->
            if (index == null) return@snapshot emptyList()
            index.prepareSetSupport(parserVersion)
            val done = index.texts("SELECT code FROM automatum_set WHERE parser_version=$parserVersion").toSet()
            index.draftableSets().filter { it.draftable && it.code !in done }.map { it.code }
        }
        var count = 0
        for (code in missing) {
            if (cancelled()) break
            compute(code)
            count++
        }
        return count
    }

    /** A card of a set: its oracle id, name and card data (null when already known or seen, or unreadable). */
    private class SetCard(val oracle: String, val name: String, val data: org.asyncttwiz.protocol.CardDefinition?)

    /** One set: its cards are read under the catalog lock, parsed outside it (the records stay usable meanwhile), and
     * the results written under it again. */
    private fun compute(code: String) {
        val read = repository.snapshot { index ->
            if (index == null || !index.hasSetSupport()) return@snapshot null
            val metadata = index.metadata()
            val oracles = index.select("SELECT id, oracle FROM printing WHERE set_code=? AND token=0 AND side IN ('', 'a')",
                listOf(code)) { it.getString(0)!! to it.getString(1)!! }.toMap()
            val known = index.select("SELECT oracle, by_hand FROM automatum_card WHERE parser_version=? AND oracle IN " +
                "(SELECT oracle FROM printing WHERE set_code=? AND token=0)", listOf(parserVersion, code)) {
                it.getString(0)!! to (it.getLong(1) == 1L)
            }.toMap()
            val seen = HashSet<String>()
            val cards = index.rows("SELECT * FROM printing WHERE set_code=? AND token=0 AND side IN ('', 'a')", listOf(code))
                .mapNotNull { printing ->
                    val oracle = oracles[printing.id] ?: return@mapNotNull null
                    // As a deck import makes the card's data; a missing face leaves it unreadable (by hand).
                    val data = if (oracle in known || !seen.add(oracle)) null else runCatching {
                        val faces = (printing.otherFaces + printing.id).distinct().map { id -> checkNotNull(index.printing(id)) }
                            .sortedWith(compareBy<CatalogPrinting> { it.side }.thenBy { it.id })
                        ResolvedDeckEntry(DeckSection.MAINBOARD, 1, faces, emptyList()).definition(metadata)
                    }.getOrNull()
                    SetCard(oracle, printing.name, data)
                }
            cards to known
        } ?: return
        val (cards, known) = read
        val results = HashMap(known)
        for (card in cards) if (card.oracle !in results) results[card.oracle] = card.data?.let(::cardByHand) ?: true
        val names = cards.filter { results[it.oracle] == true }.map { it.name }.toSortedSet().toList()
        repository.snapshot { index ->
            if (index == null || !index.hasSetSupport()) return@snapshot
            index.transaction {
                for ((oracle, hand) in results) if (oracle !in known)
                    index.execute("INSERT OR REPLACE INTO automatum_card VALUES (?, ?, ?)", listOf(oracle, parserVersion, if (hand) 1L else 0L))
                index.execute("INSERT OR REPLACE INTO automatum_set VALUES (?, ?, ?)", listOf(code, parserVersion, json.encodeToString(NAMES, names)))
            }
        }
    }

    /** As a game parses the card's data (CardDefinitions); unreadable: by hand. */
    private fun cardByHand(data: org.asyncttwiz.protocol.CardDefinition): Boolean = try {
        when (val printed = PrintedCard.of(data)) {
            is PrintedCard.Card -> byHand(OracleParser.parseCard(printed.layout, printed.faces))
            is PrintedCard.Unreadable -> true
        }
    } catch (_: RuntimeException) {
        true // The parser throws on printed values it can't read: the whole card is by hand.
    }

    private fun names(value: String): List<String> = runCatching { json.decodeFromString(NAMES, value) }.getOrDefault(emptyList())

    private companion object {
        val json = Json
        val NAMES = ListSerializer(String.serializer())
    }
}

internal fun CatalogIndex.hasSetSupport(): Boolean =
    long("SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE name='automatum_set')") != 0L

/** Optional derived data like set names, not an index-version change: other parser versions' rows are dropped. */
internal fun CatalogIndex.prepareSetSupport(parserVersion: Int) = transaction {
    sql("CREATE TABLE IF NOT EXISTS automatum_card (oracle TEXT NOT NULL, parser_version INTEGER NOT NULL, " +
        "by_hand INTEGER NOT NULL, PRIMARY KEY(oracle, parser_version)) WITHOUT ROWID")
    sql("CREATE TABLE IF NOT EXISTS automatum_set (code TEXT NOT NULL, parser_version INTEGER NOT NULL, " +
        "by_hand TEXT NOT NULL, PRIMARY KEY(code, parser_version)) WITHOUT ROWID")
    execute("DELETE FROM automatum_card WHERE parser_version != ?", listOf(parserVersion))
    execute("DELETE FROM automatum_set WHERE parser_version != ?", listOf(parserVersion))
}

