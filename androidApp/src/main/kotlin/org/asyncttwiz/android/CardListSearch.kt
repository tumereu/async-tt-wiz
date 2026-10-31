package org.asyncttwiz.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.ExecutorService
import org.asyncttwiz.storage.CatalogCard
import org.asyncttwiz.storage.CatalogCardCursor
import org.asyncttwiz.storage.CatalogCardSearch
import org.asyncttwiz.storage.CatalogGenerationChangedException
import org.asyncttwiz.storage.CatalogMetadata
import org.asyncttwiz.storage.CatalogNotInstalledException
import org.asyncttwiz.storage.CatalogSearchNotReadyException

/** The search page's quick filters. Within one kind any chosen chip matches; kinds and the typed text all apply. */
enum class SearchColor(val label: String, val term: String) {
    W("W", "c:w"), U("U", "c:u"), B("B", "c:b"), R("R", "c:r"), G("G", "c:g"), C("C", "c:c")
}
enum class SearchType(val label: String) {
    CREATURE("Creature"), PLANESWALKER("Planeswalker"), INSTANT("Instant"), SORCERY("Sorcery"),
    ARTIFACT("Artifact"), ENCHANTMENT("Enchantment"), LAND("Land");
    val term get() = "t:${label.lowercase()}"
}
enum class SearchManaValue(val label: String, val term: String) {
    ZERO("0", "mv=0"), ONE("1", "mv=1"), TWO("2", "mv=2"), THREE("3", "mv=3"), FOUR("4", "mv=4"), FIVE("5", "mv=5"), SIX("6+", "mv>=6")
}

data class CardListSearchState(val open: Boolean = false, val text: String = "",
    val colors: Set<SearchColor> = emptySet(), val types: Set<SearchType> = emptySet(), val values: Set<SearchManaValue> = emptySet(),
    val query: String = "", val cards: List<CatalogCard> = emptyList(), val metadata: CatalogMetadata? = null,
    val next: CatalogCardCursor? = null, val loading: Boolean = false, val error: String? = null,
    /** Cards in the list when the page opened, for "Done · +N cards". */
    val baseline: Long = 0)

/** The typed text plus the chosen quick filters as one Scryfall-style query (CardSearchQuery syntax). */
internal fun cardSearchQuery(text: String, colors: Set<SearchColor>, types: Set<SearchType>, values: Set<SearchManaValue>): String {
    fun any(terms: List<String>) = when (terms.size) { 0 -> null; 1 -> terms.single(); else -> terms.joinToString(" or ", "(", ")") }
    return listOfNotNull(text.trim().takeIf { it.isNotEmpty() }?.let { if (" or " in it.lowercase()) "($it)" else it },
        any(SearchColor.entries.filter { it in colors }.map { it.term }),
        any(SearchType.entries.filter { it in types }.map { it.term }),
        any(SearchManaValue.entries.filter { it in values }.map { it.term })).joinToString(" ")
}

/**
 * The card list editor's full-screen catalog search (personal, local). Runs the shared search engine
 * ([org.asyncttwiz.storage.CatalogRepository.searchCards]) on the editor's worker and pages results by name.
 */
class CardListSearch(private val worker: ExecutorService, private val post: (() -> Unit) -> Unit) {
    var state by mutableStateOf(CardListSearchState())
        private set
    var source: ((String, CatalogCardCursor?) -> CatalogCardSearch)? = null
    @Volatile private var epoch = 0L

    /** Opens the page with the previous query kept, so a second visit continues where the last one ended. */
    fun open(total: Long) { state = state.copy(open = true, baseline = total) }
    fun close() { state = state.copy(open = false) }
    /** A new edit session starts with an empty page. */
    fun reset() { epoch++; state = CardListSearchState() }
    fun text(value: String) { state = state.copy(text = value) }
    fun toggle(color: SearchColor) { state = state.copy(colors = state.colors.toggled(color)) }
    fun toggle(type: SearchType) { state = state.copy(types = state.types.toggled(type)) }
    fun toggle(value: SearchManaValue) { state = state.copy(values = state.values.toggled(value)) }
    private fun <T> Set<T>.toggled(value: T) = if (value in this) this - value else this + value
    val query get() = cardSearchQuery(state.text, state.colors, state.types, state.values)

    /** Searches the current query from the first page; an empty query clears the results. */
    fun search() {
        val query = query
        // Coming back to the page keeps the shown results of the same query.
        if (query == state.query && query.isNotBlank() && (state.cards.isNotEmpty() || state.loading) && state.error == null) return
        val generation = ++epoch
        if (query.isBlank()) { state = state.copy(query = "", cards = emptyList(), next = null, loading = false, error = null); return }
        state = state.copy(query = query, cards = emptyList(), next = null, loading = true, error = null)
        load(generation, query, null)
    }

    /** The next page, when the results so far end with more to come. */
    fun more() {
        val next = state.next ?: return
        if (state.loading) return
        state = state.copy(loading = true)
        load(epoch, state.query, next)
    }

    private fun load(generation: Long, query: String, cursor: CatalogCardCursor?) {
        val search = source ?: run { state = state.copy(loading = false, error = "Card search is not available."); return }
        worker.execute {
            if (generation != epoch) return@execute
            val outcome = runCatching { search(query, cursor) }
            post {
                if (generation != epoch) return@post
                val failure = outcome.exceptionOrNull()
                if (failure is CatalogGenerationChangedException) { search(); return@post }
                state = when (val page = outcome.getOrNull()) {
                    is CatalogCardSearch.Page -> state.copy(loading = false, metadata = page.metadata,
                        cards = (state.cards + page.cards).distinctBy { it.key }, next = page.next)
                    is CatalogCardSearch.Invalid -> state.copy(loading = false, error = page.error.message)
                    null -> state.copy(loading = false, error = when (failure) {
                        is CatalogNotInstalledException -> "Card records are not installed. Download card records, then search."
                        is CatalogSearchNotReadyException -> failure.message
                        else -> "Card records could not be read. Try again."
                    })
                }
            }
        }
    }
}
