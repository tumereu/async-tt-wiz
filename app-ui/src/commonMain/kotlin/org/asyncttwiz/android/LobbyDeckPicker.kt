package org.asyncttwiz.android

import org.asyncttwiz.appui.executeSuspending
import org.asyncttwiz.protocol.*
import org.asyncttwiz.appui.ExecutorService

data class DeckPickerState(val game: GameSummary, val decks: List<DeckSummary> = emptyList(),
    val draft: DeckSummary? = null, val loading: Boolean = true, val confirming: Boolean = false,
    val importing: Boolean = false, val error: String? = null, val loaded: Boolean = false,
    /** LOB-255: the decks the game's creator lends (for the other player, while lending), and the viewer's own categories. */
    val lent: LentDecks? = null, val categories: List<DeckCategory> = emptyList()) {
    /** LOB-255: [deck] is one of the creator's lent decks, borrowed by the viewer. */
    fun borrowed(deck: DeckSummary) = lent?.decks?.any { it.id == deck.id } == true && decks.none { it.id == deck.id }
}

/** LOB-255: one section of Choose deck: its [heading] (null: none; [minor]: a category's name under the lender's decks),
 * its decks, and whether they can be chosen in this game ([usable]: greyed when not). */
data class DeckPickerSection(val heading: String?, val decks: List<DeckSummary>, val usable: Boolean = true, val minor: Boolean = false)

/** LOB-255: the creator's decks that are lent under [settings], of [own] ([categories]: the creator's). */
fun lentOwnDecks(settings: DeckSettings, own: List<DeckSummary>, categories: List<DeckCategory>): Set<String> = when (val lend = settings.lend) {
    DeckLending.Off -> emptySet()
    DeckLending.All -> own.filter { it.draft == null }.map { it.id }.toSet()
    is DeckLending.Categories -> DeckCategories.within(categories, lend.ids).let { lent ->
        own.filter { it.draft == null && it.category in lent }.map { it.id }.toSet() }
}

/**
 * LOB-255, LOB-040: Choose deck's sections for [viewer] in [game]: for the other player, while the creator lends decks,
 * "<creator>'s decks" first (the lent decks under their categories' names, uncategorised ones last), then "Your decks" and
 * the draft decks; with Only lent decks, the decks that can't be used (the viewer's own; for the creator, those not lent)
 * are greyed.
 */
fun deckPickerSections(game: GameSummary, viewer: String, own: List<DeckSummary>, categories: List<DeckCategory>,
    lent: LentDecks?): List<DeckPickerSection> {
    val settings = game.deckSettings
    val creator = viewer == game.inviter.accountId
    val lentToMe = lent?.takeIf { !creator && settings.lending }
    val usable = { deck: DeckSummary -> !settings.onlyLentDecks || (creator && deck.id in lentOwnDecks(settings, own, categories)) }
    val sections = mutableListOf<DeckPickerSection>()
    if (lentToMe != null) {
        sections.add(DeckPickerSection(lendersDecksHeading(lentToMe.lender.username), emptyList()))
        val ids = lentToMe.categories.map { it.id }.toSet()
        val byCategory = lentToMe.decks.groupBy { it.category?.takeIf { category -> category in ids } }
        for ((category, _) in DeckCategories.ordered(lentToMe.categories)) byCategory[category.id]?.let { decks ->
            sections.add(DeckPickerSection(DeckCategories.pathName(lentToMe.categories, category.id), decks, minor = true)) }
        byCategory[null]?.let { decks -> sections.add(DeckPickerSection(if (sections.size > 1) "Uncategorised" else null, decks, minor = true)) }
    }
    val (drafts, ordinary) = own.partition { it.draft != null }
    if (ordinary.isNotEmpty()) {
        if (creator && settings.onlyLentDecks) {
            // The creator's own decks: the lent ones usable, the others greyed
            val (allowed, others) = ordinary.partition(usable)
            if (allowed.isNotEmpty()) sections.add(DeckPickerSection(null, allowed))
            if (others.isNotEmpty()) sections.add(DeckPickerSection("Not lent", others, usable = false))
        } else sections.add(DeckPickerSection(if (lentToMe != null) "Your decks" else null, ordinary, usable = !settings.onlyLentDecks))
    }
    if (drafts.isNotEmpty()) sections.add(DeckPickerSection("Draft decks", drafts, usable = !settings.onlyLentDecks))
    return sections
}

/** LOB-255: the heading of the creator's lent decks in Choose deck. */
fun lendersDecksHeading(lender: String) = "$lender's decks"
/** LOB-255: why greyed decks can't be chosen. */
fun onlyLentNote(lender: String) = "Only $lender's lent decks can be used in this game."

/** LobbyController's deck picker: lists saved and draft decks over HTTP, keeps the highlighted deck and
 * a deck import started from it, and selects the confirmed deck for the game once its connection is
 * ready. Main thread; reads run on [worker]. */
class LobbyDeckPicker(private val scope: LobbyScope, private val deckApi: Decks?, private val credential: () -> String?,
    private val accountId: () -> String?, private val worker: ExecutorService, private val post: (() -> Unit) -> Unit,
    private val session: GameSession?, private val queue: LobbyActionQueue,
    /** The phone's card records, which a chosen deck's cards are built from (owner 2026-11-10). */
    private val records: () -> SavedCardRecords = { SavedCardRecords.NONE }) {
    private val cardRecords get() = records()
    private var state: LobbyState
        get() = scope.state
        set(value) { scope.state = value }
    private val closed get() = scope.closed
    private val epoch get() = scope.epoch
    private var pickerVersion = 0L
    fun clear() { pickerVersion++ }
    /** The picker is personal and lists saved decks over HTTP, so it opens without the game connection. */
    fun chooseDeck() {
        val game = state.detail ?: return
        if (closed || deckApi == null || game.podId != null || state.preparation?.podRules != null || game.lifecycle != GameLifecycle.LOBBY ||
            (game.inviter.accountId != accountId() && game.invitationState != InvitationState.ACCEPTED)) return
        state = state.copy(picker = DeckPickerState(game))
        refreshPicker()
    }
    fun closePicker() { pickerVersion++; queue.dropQueued("deck"); state = state.copy(picker = null) }
    fun dismissPickerError() { state = state.copy(picker = state.picker?.copy(error = null)) }
    fun highlightDeck(deck: DeckSummary) {
        val picker = state.picker ?: return
        val game = state.detail ?: picker.game
        val usable = deckPickerSections(game, accountId() ?: return, picker.decks, picker.categories, picker.lent)
            .any { section -> section.usable && section.decks.any { it.id == deck.id } }
        if (!picker.confirming && usable) state = state.copy(picker = picker.copy(draft = deck, error = null))
    }
    /** LOB-248: reads the creator's own categories for Lend my decks (once per lobby visit; again on each open). */
    fun loadLendCategories() {
        val api = deckApi ?: return
        val token = credential() ?: return
        val generation = epoch
        worker.executeSuspending {
            val result = runCatching { serviceRetry(current = { generation == epoch }) { api.categories(token) } }
            post { if (!closed && generation == epoch) result.onSuccess { state = state.copy(lendCategories = it.categories) }
                .onFailure { AppLog.warn("lobby", "Lend my decks: categories not read", it) } }
        }
    }
    fun importForPicker() { state.picker?.takeIf { !it.confirming }?.let { state = state.copy(picker = it.copy(importing = true)) } }
    /** Back from an import started in the picker; a just-saved [saved] deck comes back highlighted, not confirmed. */
    fun importedForPicker(saved: DeckSummary? = null): Boolean {
        val picker = state.picker?.takeIf { it.importing } ?: return false
        state = state.copy(picker = picker.copy(importing = false, draft = saved ?: picker.draft,
            decks = if (saved == null) picker.decks else listOf(saved) + picker.decks.filter { it.id != saved.id }))
        refreshPicker(); return true
    }
    fun refreshPicker() {
        val picker = state.picker ?: return
        val api = deckApi ?: return
        val token = credential() ?: return
        if (picker.confirming) return
        val generation = epoch; val version = ++pickerVersion
        state = state.copy(picker = picker.copy(loading = true, error = null))
        val game = state.detail ?: picker.game
        // LOB-255: the other player also reads the decks the creator lends
        val borrowing = game.deckSettings.lending && accountId() != game.inviter.accountId
        var categories = picker.categories
        var lent: LentDecks? = null
        worker.executeSuspending {
            val result = runCatching { run {
                val decks = linkedMapOf<String, DeckSummary>(); val seen = mutableSetOf<String>(); var next: String? = null
                do {
                    if (generation != epoch || version != pickerVersion) return@run emptyList()
                    val after = next
                    val page = serviceRetry(current = { generation == epoch && version == pickerVersion }) { api.list(token, after) }; page.decks.forEach { decks[it.id] = it }
                    if (after == null) categories = page.categories
                    next = page.next
                    next.let { check(it == null || seen.add(it)) }
                } while (next != null)
                if (borrowing && generation == epoch && version == pickerVersion) {
                    lent = serviceRetry(current = { generation == epoch && version == pickerVersion }) { api.lentDecks(token, game.id) }
                    AppLog.info("lobby", "Choose deck of ${game.id.take(8)}: ${lent?.decks?.size} decks lent by ${lent?.lender?.username}")
                }
                // Draft decks are playable too; the picker lists them under their own heading.
                if (generation != epoch || version != pickerVersion) return@run emptyList()
                serviceRetry(current = { generation == epoch && version == pickerVersion }) { api.drafts(token) }.decks.forEach { decks[it.id] = it }
                decks.values.toList()
            } }
            post { if (!closed && generation == epoch && version == pickerVersion) state.picker?.let {
                // A background collection read never silently substitutes or updates the captured draft.
                state = state.copy(picker = it.copy(decks = result.getOrDefault(it.decks), loading = false, loaded = it.loaded || result.isSuccess,
                    lent = if (result.isSuccess) lent else it.lent, categories = categories,
                    error = result.exceptionOrNull()?.let { deckError(it as? Exception ?: DeckFailure("network")) }))
            } }
        }
    }
    fun confirmDeck() {
        val picker = state.picker ?: return
        val source = picker.draft ?: return
        val api = deckApi ?: return
        val token = credential() ?: return
        val actor = accountId() ?: return
        if (closed || picker.confirming || picker.loading) return
        val generation = epoch; val version = ++pickerVersion
        state = state.copy(picker = picker.copy(confirming = true, error = null))
        // LOB-256: a lent deck is read through the game, and its copy names its lender
        val lender = picker.lent?.lender?.accountId?.takeIf { picker.borrowed(source) }
        AppLog.info("lobby", "Deck chosen for ${picker.game.id.take(8)}: ${source.name} (${source.id.take(8)})${if (lender != null) ", borrowed" else ""}")
        worker.executeSuspending {
            val result = runCatching { run {
                if (generation != epoch || version != pickerVersion) return@run null
                val saved = serviceRetry(current = { generation == epoch && version == pickerVersion }) {
                    if (lender != null) api.lentDeck(token, picker.game.id, source.id) else api.read(token, source.id) }
                if (saved.deck.revision != source.revision) throw DeckFailure("source_changed")
                if (generation != epoch || version != pickerVersion) return@run null
                // Its cards built from the card records by printing, as the server builds them (owner 2026-11-10)
                val cards = saved.cards()
                saved to cardRecords.read(cards.mapNotNull { it.printingId }).submitted(cards)
            } }
            fun current() = !closed && generation == epoch && version == pickerVersion && state.picker != null
            fun failed(failure: Throwable?) = state.picker?.let { current ->
                state = state.copy(picker = current.copy(confirming = false, error = when ((failure as? DeckFailure)?.code) {
                    "source_changed" -> "This saved deck changed. Refresh and choose it again."
                    "not_found" -> "This saved deck was deleted. Refresh and choose another deck."
                    null -> "This game changed or is disconnected. Reconnect and choose again."
                    else -> deckError(failure as? Exception ?: DeckFailure("network"))
                }))
            }
            post { if (current()) {
                val data = result.getOrNull()
                // The deck read needs no game connection; only the selection waits for it (still "Loading deck…").
                if (data == null) failed(result.exceptionOrNull())
                else queue.whenReady(picker.game.id, "deck") {
                    if (current()) {
                        if (state.detail?.preparationRound == picker.game.preparationRound &&
                            session?.selectDeck(picker.game, actor, data.first, data.second, lender) == true) closePicker()
                        else failed(null)
                    }
                }
            } }
        }
    }
}
