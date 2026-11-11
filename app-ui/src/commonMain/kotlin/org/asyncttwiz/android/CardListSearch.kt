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
import org.asyncttwiz.storage.CatalogPrinting
import org.asyncttwiz.storage.CatalogSearchNotReadyException
import org.asyncttwiz.storage.ResolvedDeckEntry
import org.asyncttwiz.protocol.CardColors
import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.CardSearchCard
import org.asyncttwiz.protocol.CardSearchFace
import org.asyncttwiz.protocol.CardSearchPrinting
import org.asyncttwiz.protocol.DeckJson
import org.asyncttwiz.protocol.DefinitionFace
import org.asyncttwiz.protocol.PodDeckManaCost
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

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
fun cardSearchQuery(text: String, colors: Set<SearchColor>, types: Set<SearchType>, values: Set<SearchManaValue>): String {
    fun any(terms: List<String>) = when (terms.size) { 0 -> null; 1 -> terms.single(); else -> terms.joinToString(" or ", "(", ")") }
    return listOfNotNull(text.trim().takeIf { it.isNotEmpty() }?.let { if (" or " in it.lowercase()) "($it)" else it },
        any(SearchColor.entries.filter { it in colors }.map { it.term }),
        any(SearchType.entries.filter { it in types }.map { it.term }),
        any(SearchManaValue.entries.filter { it in values }.map { it.term })).joinToString(" ")
}

/** The Cards tab's filter (LOB-142): typed text and chips as on the search page, over the list itself. */
data class CardListFilter(val text: String = "", val colors: Set<SearchColor> = emptySet(),
    val types: Set<SearchType> = emptySet(), val values: Set<SearchManaValue> = emptySet()) {
    val query get() = cardSearchQuery(text, colors, types, values)
}

/** A face's own colours as search reads them: its record's, otherwise those of its mana cost. */
private fun DefinitionFace.searchColors(): Int {
    val recorded = sourceData?.let { data -> runCatching { DeckJson.format.parseToJsonElement(data).jsonObject["colors"] as? JsonArray }.getOrNull() }
    return CardColors.mask(recorded?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: PodDeckManaCost.parse(manaCost).colors.map { it.name })
}

/**
 * [entry]'s card for the search matcher (LOB-108): the catalog's ([known]), down to the entry's own printing where the
 * catalog has it; a card the catalog doesn't know is read from its definition, which has no rarity or legalities.
 */
fun listSearchCard(entry: ResolvedDeckEntry, definition: CardDefinition, known: CardSearchCard?): CardSearchCard {
    val set = entry.printing.set.lowercase()
    if (known != null) return known.copy(printings = known.printings.filter { it.set == set }.ifEmpty { known.printings })
    val faces = definition.faces.map { face ->
        CardSearchFace(face.name, face.typeLine, face.rulesText, face.manaCost, face.searchColors(),
            face.power.ifEmpty { null }, face.toughness.ifEmpty { null }, face.loyalty.ifEmpty { null })
    }
    val identity = definition.faces.zip(faces).fold(0) { mask, (face, read) ->
        mask or read.colors or CardColors.mask(PodDeckManaCost.parse(face.manaCost).colors.map { it.name })
    }
    return CardSearchCard(definition.name, definition.layout ?: "normal", faces, identity,
        printings = if (set.isEmpty()) emptyList() else listOf(CardSearchPrinting(set, "")))
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
    fun reset() { epoch++; state = CardListSearchState(); filter = CardListFilter(); filterCards = emptyMap(); filterAsked = HashSet() }

    /** The Cards tab's filter (LOB-142): only what the tab shows follows it, never the list. */
    var filter by mutableStateOf(CardListFilter())
        private set
    fun filterText(value: String) { filter = filter.copy(text = value) }
    fun filterToggle(color: SearchColor) { filter = filter.copy(colors = filter.colors.toggled(color)) }
    fun filterToggle(type: SearchType) { filter = filter.copy(types = filter.types.toggled(type)) }
    fun filterToggle(value: SearchManaValue) { filter = filter.copy(values = filter.values.toggled(value)) }
    /** Reads the list's cards as the catalog's search knows them ([org.asyncttwiz.storage.CatalogRepository.searchData]). */
    var cardData: ((Collection<CatalogPrinting>) -> Map<String, CardSearchCard>)? = null
    /** What [cardData] gave so far, by printing id. */
    var filterCards by mutableStateOf<Map<String, CardSearchCard>>(emptyMap())
        private set
    /** Printing ids asked of [cardData] in this edit session; a new session's set tells a late answer it is too late. */
    private var filterAsked = HashSet<String>()
    /** Reads what the filter needs of [printings] and doesn't have yet; until then, and without card records, they match by their definitions. */
    fun loadFilterCards(printings: List<CatalogPrinting>) {
        val read = cardData ?: return
        val missing = printings.filter { filterAsked.add(it.id) }
        if (missing.isEmpty()) return
        val asked = filterAsked
        worker.execute {
            val found = runCatching { read(missing) }.getOrDefault(emptyMap())
            post { if (asked === filterAsked) filterCards = filterCards + found }
        }
    }
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
