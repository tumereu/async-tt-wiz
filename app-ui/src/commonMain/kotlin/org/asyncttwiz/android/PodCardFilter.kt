package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.asyncttwiz.appui.Executor
import org.asyncttwiz.protocol.ArenaEngine
import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.CardSearchCard
import org.asyncttwiz.protocol.CardSearchNode
import org.asyncttwiz.protocol.CardSearchParse
import org.asyncttwiz.protocol.CardSearchPrinting
import org.asyncttwiz.protocol.CardSearchQuery
import org.asyncttwiz.protocol.DraftBotRatings
import org.asyncttwiz.protocol.SetCardRarity
import org.asyncttwiz.storage.CatalogPrinting

/** The draft filter's rarity chips (DRF-252): a card's own printing's rarity; a special or bonus printing's the one it
 * belongs to in Arena (DRF-291, [podFilterPrintings]). */
enum class SearchRarity(val label: String, val term: String) {
    COMMON("Common", "r:common"), UNCOMMON("Uncommon", "r:uncommon"), RARE("Rare", "r:rare"), MYTHIC("Mythic", "r:mythic")
}

/**
 * The draft's quick filter (DRF-250..DRF-255) over the Rotisserie pool or Build: typed text and chips; within a row any
 * chosen chip matches, and the rows and the text all apply.
 */
data class PodCardFilter(val text: String = "", val colors: Set<SearchColor> = emptySet(), val multicolor: Boolean = false,
    val types: Set<SearchType> = emptySet(), val values: Set<SearchManaValue> = emptySet(),
    val rarities: Set<SearchRarity> = emptySet()) {
    /** Whether anything is set, so that the pool shows only matches and the button shows it (DRF-253). */
    val active: Boolean get() = text.isNotBlank() || colors.isNotEmpty() || multicolor || types.isNotEmpty() || values.isNotEmpty() ||
        rarities.isNotEmpty()

    /** Why the typed text can't be read, null when it can (or is empty). */
    val error: String? get() = text.takeIf { it.isNotBlank() }?.let { (CardSearchQuery.parse(it) as? CardSearchParse.Invalid)?.error?.message }

    /** The chips' rows as search terms. */
    private val rows: List<List<String>> get() = listOf(
        SearchColor.entries.filter { it in colors }.map { it.term } + listOfNotNull("c:m".takeIf { multicolor }),
        SearchType.entries.filter { it in types }.map { it.term },
        SearchManaValue.entries.filter { it in values }.map { it.term },
        SearchRarity.entries.filter { it in rarities }.map { it.term })

    /** The text and chips as one search, as written (for the log). */
    val query: String get() = searchQuery(text, rows)

    /**
     * The search the cards are matched by, null when nothing is set: text that can't be read is left out (its reason shows
     * under the field) and the chips still apply. Bare words match the type line and rules text as well as the name (DRF-251).
     */
    fun matcher(): CardSearchQuery? {
        if (!active) return null
        val query = searchQuery(if (error == null) text else "", rows).takeIf { it.isNotBlank() } ?: return null
        val parsed = (CardSearchQuery.parse(query) as? CardSearchParse.Valid)?.query ?: return null
        return CardSearchQuery(parsed.root?.widened(), parsed.includeExtras)
    }
}

/** DRF-251: a bare word or quoted phrase (not `!exact`) also matches the type line and rules text, case-insensitively. */
internal fun CardSearchNode.widened(): CardSearchNode = when (this) {
    is CardSearchNode.And -> CardSearchNode.And(terms.map { it.widened() })
    is CardSearchNode.Or -> CardSearchNode.Or(terms.map { it.widened() })
    is CardSearchNode.Not -> CardSearchNode.Not(term.widened())
    is CardSearchNode.Name -> if (exact) this else CardSearchNode.Or(listOf(this, CardSearchNode.TypeLine(text), CardSearchNode.OracleText(text)))
    else -> this
}

/** [definition] as the filter reads it: its own faces, with its printing's set and rarity from card records ([printings], by
 * printing id) where they are known (DRF-252). */
fun podFilterCard(definition: CardDefinition, printings: Map<String, List<CardSearchPrinting>>): CardSearchCard {
    val card = podSearchCard(definition)
    return definition.printingId?.let(printings::get)?.let { card.copy(printings = it) } ?: card
}

/** Whether [definition] shows under [matcher] (every card without a filter; a card without card data never with one). */
fun podFilterShows(matcher: CardSearchQuery?, definition: CardDefinition?, printings: Map<String, List<CardSearchPrinting>>): Boolean =
    matcher == null || definition != null && matcher.matches(podFilterCard(definition, printings))

/** DRF-254: Build's columns with only the cards matching [filter] (each column's place kept, emptied where none match). */
fun podBuildFiltered(columns: List<List<PodBuildTile>>, filter: PodCardFilter,
    printings: Map<String, List<CardSearchPrinting>> = emptyMap()): List<List<PodBuildTile>> {
    val matcher = filter.matcher() ?: return columns
    return columns.map { column -> column.filter { podFilterShows(matcher, it.definition, printings) } }
}

/**
 * DRF-252: each of [ids]' printings as card records know it, for its rarity: the card each printing is a face of ([cards],
 * CardCatalog.cards), read by [searchData], narrowed to that printing's set. Printings the records don't know are left out.
 * A special or bonus printing also holds the rarity it belongs to in Arena (DRF-291, [ArenaEngine.rarity]): the lowest of
 * the card's other printings, or rare without any, so the rarity chips find it there while typed search terms still see
 * its own rarity.
 */
fun podFilterPrintings(ids: Collection<String>, cards: (Collection<String>) -> Map<String, List<CatalogPrinting>>,
    searchData: (Collection<CatalogPrinting>) -> Map<String, CardSearchCard>): Map<String, List<CardSearchPrinting>> {
    val fronts = cards(ids).mapNotNull { (id, faces) -> faces.firstOrNull()?.let { id to it } }
    val data = searchData(fronts.map { it.second }.distinctBy { it.id })
    return fronts.mapNotNull { (id, printing) ->
        val set = printing.set.lowercase()
        val card = data[printing.id] ?: return@mapNotNull null
        val tier by lazy { ArenaEngine.tier(card.printings.mapNotNull { podFilterRarity(it.rarity) }) }
        card.printings.filter { it.set == set }.flatMap { own ->
            val rarity = podFilterRarity(own.rarity)
            if (rarity == null || !ArenaEngine.special(rarity)) listOf(own)
            else listOf(own, own.copy(rarity = ArenaEngine.rarity(rarity, tier).name.lowercase()))
        }.distinct().takeIf { it.isNotEmpty() }?.let { id to it }
    }.toMap()
}

/** A card record's rarity word as a set rarity, null for one Arena doesn't know. */
private fun podFilterRarity(word: String): SetCardRarity? = SetCardRarity.entries.firstOrNull { it.name.equals(word, ignoreCase = true) }

/** Where a pod's filter is: the Rotisserie pool (DRF-250) or Build (DRF-254). Each has its own (DRF-255). */
sealed interface PodFilterPlace {
    val key: String
    data object Pool : PodFilterPlace { override val key = "pool" }
    data object Build : PodFilterPlace { override val key = "build" }
}

/** One place's filter and whether its popup is open: personal, local, never sent (DRF-255). */
@Stable
class PodCardFilterState(private val label: String = "") {
    var filter by mutableStateOf(PodCardFilter())
        private set
    var open by mutableStateOf(false)
        private set
    fun open() { open = true; AppLog.info("pod", "Filter $label opened: ${describe()}") }
    fun close() { open = false; AppLog.info("pod", "Filter $label closed: ${describe()}") }
    fun text(value: String) { filter = filter.copy(text = value) }
    fun toggle(color: SearchColor) = change { copy(colors = colors.toggled(color)) }
    fun toggleMulticolor() = change { copy(multicolor = !multicolor) }
    fun toggle(type: SearchType) = change { copy(types = types.toggled(type)) }
    fun toggle(value: SearchManaValue) = change { copy(values = values.toggled(value)) }
    fun toggle(rarity: SearchRarity) = change { copy(rarities = rarities.toggled(rarity)) }
    fun clear() = change { PodCardFilter() }
    private fun change(edit: PodCardFilter.() -> PodCardFilter) {
        filter = filter.edit()
        AppLog.info("pod", "Filter $label: ${describe()}")
    }
    private fun describe() = filter.takeIf { it.active }?.query?.let { "\"$it\"" } ?: "nothing set"
    private fun <T> Set<T>.toggled(value: T) = if (value in this) this - value else this + value
}

/**
 * Each pod's filters, kept while the app runs (DRF-255), and the rarities they read from card records (DRF-252): [read]
 * (printing ids to their printings) runs on [worker] and its answer is handed back by [post].
 */
class PodCardFilters(private val read: ((Collection<String>) -> Map<String, List<CardSearchPrinting>>)? = null,
    private val worker: Executor = Executor { it.run() }, private val post: (() -> Unit) -> Unit = { it() },
    private val shutdown: () -> Unit = {}, private val readRatings: ((String) -> Map<String, Int>)? = null) {
    private var closed = false

    /** DRF-312: the card ratings of the sets asked so far ([loadRatings]), by set code; a set without them has none. */
    var ratings by mutableStateOf<Map<String, DraftBotRatings?>>(emptyMap())
        private set
    private val ratingsAsked = HashSet<String>()

    /** DRF-312: reads set [code]'s card ratings from card records once (the draft bot's debug view, DRF-292). */
    fun loadRatings(code: String) {
        val reader = readRatings?.takeIf { !closed } ?: return
        if (!ratingsAsked.add(code)) return
        worker.execute {
            val table = runCatching { reader(code) }
                .onFailure { AppLog.warn("draft-bot", "Card ratings of $code could not be read", it) }.getOrDefault(emptyMap())
            post {
                AppLog.info("draft-bot", "Card ratings of $code: ${table.size} cards")
                // Without card records yet, ask again next time
                if (table.isEmpty()) ratingsAsked.remove(code)
                ratings = ratings + (code to table.takeIf { it.isNotEmpty() }?.let { DraftBotRatings(code, it) })
            }
        }
    }
    private val states = HashMap<String, PodCardFilterState>()
    fun of(pod: String, place: PodFilterPlace): PodCardFilterState = states.getOrPut("$pod/${place.key}") { PodCardFilterState(place.key) }

    /** What card records said of the printings asked so far, by printing id. */
    var printings by mutableStateOf<Map<String, List<CardSearchPrinting>>>(emptyMap())
        private set
    private val asked = HashSet<String>()

    /** Reads the printings of [ids] not asked yet; until they come, rarity chips match none of them. */
    fun load(ids: Collection<String>) {
        val reader = read?.takeIf { !closed } ?: return
        val missing = ids.filter { asked.add(it) }
        if (missing.isEmpty()) return
        worker.execute {
            val found = runCatching { reader(missing) }
                .onFailure { AppLog.warn("pod", "Filter: card records of ${missing.size} printings could not be read", it) }
                .getOrDefault(emptyMap())
            post {
                AppLog.info("pod", "Filter: card records know ${found.size} of ${missing.size} printings")
                printings = printings + found
            }
        }
    }

    /** A popup opening asks again for what card records didn't know before: they may have been installing then. */
    fun opened() { asked.retainAll(printings.keys) }

    fun close() { closed = true; shutdown() }
}

/** The filter button's distance from its scrolling area's bottom right corner (DRF-250). */
val PodFilterButtonInset = 8.dp
/** Room at the end of a filtered scrolling area, so its last cards scroll clear of the button. */
val PodFilterButtonRoom = 64.dp

/**
 * The filter button floating in a scrolling area's corner (DRF-250): a funnel, filled in the accent colour with a dot while
 * a filter is set (DRF-253); a tap opens its popup above it. [definitions]: the cards it filters, whose rarities it reads
 * from card records while the popup is open or a rarity is chosen.
 */
@Composable
fun PodCardFilterButton(state: PodCardFilterState, filters: PodCardFilters, definitions: Collection<CardDefinition>,
    tag: String, modifier: Modifier = Modifier) {
    val filter = state.filter
    val reading = state.open || filter.rarities.isNotEmpty()
    val ids = remember(definitions) { definitions.mapNotNullTo(HashSet()) { it.printingId } }
    LaunchedEffect(reading, ids) { if (reading) filters.load(ids) }
    // Leaving the screen (another tab, another pod) closes the popup; the filter stays (DRF-255)
    DisposableEffect(state) { onDispose { if (state.open) state.close() } }
    Box(modifier) {
        HoldTip("Filter cards") {
            SmallFloatingActionButton({ filters.opened(); state.open() }, Modifier.testTag(tag).semantics {
                contentDescription = "Filter cards"
                stateDescription = if (filter.active) "Filter on" else "No filter"
            }, containerColor = if (filter.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (filter.active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) {
                BadgedBox(badge = { if (filter.active) Badge(Modifier.testTag("$tag-badge")) }) {
                    Icon(Icons.Outlined.FilterAlt, null, Modifier.size(22.dp))
                }
            }
        }
        DropdownMenu(state.open, { state.close() }, Modifier.testTag("$tag-popup")) { PodCardFilterContent(state, tag) }
    }
}

/** The popup (DRF-250): "Filter cards" with Clear, the search field, and the chips: colours with a golden M for
 * Multicolor, mana values, card types and rarities (DRF-252). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PodCardFilterContent(state: PodCardFilterState, tag: String) {
    val filter = state.filter
    val error = filter.error
    Column(Modifier.width(320.dp).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Filter cards", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            TextButton({ state.clear() }, Modifier.testTag("$tag-clear"), enabled = filter.active) { Text("Clear") }
        }
        OutlinedTextField(filter.text, state::text, Modifier.fillMaxWidth().testTag("$tag-field"),
            label = { Text("Name, type or rules text") }, singleLine = true, isError = error != null,
            supportingText = error?.let { { Text(it, Modifier.testTag("$tag-error")) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = hideKeyboardActions())
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            val gap = Arrangement.spacedBy(4.dp)
            FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
                SearchColor.entries.forEach { color ->
                    SearchSymbolChip(color in filter.colors, color.label, "Colour ${color.label}", "$tag-colour-${color.label}") { state.toggle(color) }
                }
                // DRF-252 (owner, 2026-11-20): a golden M in the colours' row, named as Stats names it
                SearchSymbolChip(filter.multicolor, "M", "Multicolor", "$tag-multicolour", state::toggleMulticolor)
            }
            FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
                SearchManaValue.entries.forEach { value ->
                    SearchSymbolChip(value in filter.values, value.label, "Mana value ${value.label}", "$tag-value-${value.label}") { state.toggle(value) }
                }
            }
            FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
                SearchType.entries.forEach { type ->
                    FilterChip(type in filter.types, { state.toggle(type) }, { Text(type.label) }, Modifier.height(32.dp).testTag("$tag-type-${type.label}"))
                }
            }
            FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
                SearchRarity.entries.forEach { rarity ->
                    FilterChip(rarity in filter.rarities, { state.toggle(rarity) }, { Text(rarity.label) }, Modifier.height(32.dp).testTag("$tag-rarity-${rarity.label}"))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
