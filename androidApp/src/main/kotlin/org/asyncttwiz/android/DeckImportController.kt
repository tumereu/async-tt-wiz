package org.asyncttwiz.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.asyncttwiz.storage.DeckImportResult
import org.asyncttwiz.storage.CatalogMetadata
import org.asyncttwiz.storage.ParsedDeck
import org.asyncttwiz.protocol.*
import java.util.concurrent.ExecutorService

data class DeckImportState(val active: Boolean = false, val text: String = "", val busy: Boolean = false,
    val result: DeckImportResult? = null, val preview: Boolean = false, val name: String = "",
    val saving: Boolean = false, val error: String? = null, val target: String? = null,
    val mode: DeckImportMode = DeckImportMode.OVERWRITE, val editing: Boolean = false,
    val cover: String? = null, val choosingCover: Boolean = false, val discard: Boolean = false,
    val lookupFailed: Boolean = false, val fromUrl: Boolean = false, val url: String = "",
    val clipboardRead: Boolean = false, val fetchingUrl: Boolean = false, val imported: Boolean = false)

/** Personal volatile draft, retained by the Activity ViewModel across rotation, never a saved deck. */
class DeckImportController(private val resolve: (String) -> DeckImportResult,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit) : AutoCloseable {
    var state by mutableStateOf(DeckImportState())
        private set
    @Volatile private var epoch = 0L
    private var closed = false
    private var retryPlan: DeckSavePlan? = null
    private var saveAction: (() -> ((DeckSavePlan) -> SavedDeck)?)? = null
    private var saved: (SavedDeck) -> Unit = {}
    /** Called on the worker after an accepted save (import or edit) with the saved definitions. */
    var acceptedDefinitions: (deckId: String, definitions: List<SubmittedDefinition>) -> Unit = { _, _ -> }
    private var catalogMetadata: CatalogMetadata? = null
    private var catalogInstalling = false
    private var relookupAfterCurrent = false
    private var source: DeckEditorSource? = null
    private var initial = DeckImportState()
    private var saveAfterLookup = false
    private var chosenCover: SubmittedDefinition? = null
    private var resolveParsed: (ParsedDeck) -> DeckImportResult = { resolve(it.text) }
    private var fetchUrl: (DeckImportLocation) -> ImportedDeck = ::fetchDeckUrl
    fun connectUrl(resolve: (ParsedDeck) -> DeckImportResult, fetch: (DeckImportLocation) -> ImportedDeck = ::fetchDeckUrl) {
        resolveParsed = resolve; fetchUrl = fetch
    }
    fun fromUrl(value: Boolean) {
        if (value != state.fromUrl) cancelUrlFetch()
        state = state.copy(fromUrl = value, error = null)
    }
    fun prefillUrl(read: () -> String?) {
        if (state.clipboardRead || !state.active || !state.fromUrl) return
        val match = ImportClipboard.deck(read())
        state = state.copy(clipboardRead = true, url = if (state.url.isBlank()) match.orEmpty() else state.url)
    }
    fun url(value: String) {
        if (closed || !state.active) return
        cancelUrlFetch()
        state = state.copy(url = value, error = null)
    }
    /** The Link field's paste icon: paste the clipboard and import it at once (typed text waits for Import). */
    fun pasteUrl(clip: String?) {
        if (closed || !state.active || state.saving) return
        val text = clip?.trim().orEmpty()
        if (text.isEmpty()) { state = state.copy(error = "Clipboard is empty."); return }
        url(ImportClipboard.deck(text) ?: text)
        importUrl()
    }
    /** Something valid to save: a resolved list with a name, and no lookup, fetch or save in progress. */
    val canSave: Boolean get() = state.active && state.result?.resolved == true && state.name.isNotBlank() &&
        !state.busy && !state.saving
    private fun cancelUrlFetch() {
        if (state.fetchingUrl) { epoch++; state = state.copy(busy = false, fetchingUrl = false) }
    }
    fun importUrl() {
        if (closed || !state.active || (state.busy && !state.fetchingUrl) || state.saving) return
        val location = DeckUrlImport.location(state.url) ?: run {
            state = state.copy(error = "Enter a supported deck URL or site:ID."); return
        }
        val generation = ++epoch
        state = state.copy(busy = true, fetchingUrl = true, error = null)
        worker.execute {
            if (generation != epoch) return@execute
            val fetched = runCatching { fetchUrl(location) }
            val imported = fetched.getOrNull()
            val resolved = imported?.let { runCatching { resolveParsed(it.parsed) } }
            post { if (!closed && generation == epoch && state.active) {
                if (imported == null) state = state.copy(busy = false, fetchingUrl = false,
                    error = "${location.site.label} could not be loaded. Try Paste list.")
                else {
                    source = null; retryPlan = null; chosenCover = null
                    state = state.copy(text = imported.parsed.text, name = imported.name, result = resolved?.getOrNull(),
                        busy = false, fetchingUrl = false, imported = true, cover = null, preview = false,
                        lookupFailed = resolved?.isFailure == true,
                        error = if (resolved?.isFailure == true) "Card records could not be read. Retry lookup." else null)
                }
            } }
        }
    }
    val coverCards: List<SubmittedDefinition> get() = state.result?.let { result ->
        result.entries.map { entry ->
            val definition = PhysicalCardFaces.normalize(source?.definitions?.get(entry.printing.id) ?: entry.definition(result.catalog))
            SubmittedDefinition(DeckJson.digest(definition), definition)
        }.distinctBy { it.digest }
    }.orEmpty()
    val currentCover: DeckCover? get() = (state.cover ?: coverCards.firstOrNull()?.digest)?.let { digest ->
        val card = coverCards.find { it.digest == digest } ?: chosenCover?.takeIf { it.digest == digest }
            ?: source?.detail?.definitions?.get(digest)?.let { SubmittedDefinition(digest, it) }
        card?.let {
            val face = it.definition.faces.first()
            DeckCover(digest, it.definition.name, face.scryfallId, face.imageUri)
        }
    }
    fun chooseCover() { if (!state.saving && state.result?.resolved == true) state = state.copy(choosingCover = true) }
    fun dismissError() { state = state.copy(error = null) }
    fun closeCover() { state = state.copy(choosingCover = false) }
    fun cover(digest: String) {
        if (!state.saving && coverCards.any { it.digest == digest }) {
            chosenCover = coverCards.first { it.digest == digest }
            retryPlan = null; state = state.copy(cover = digest, choosingCover = false)
        }
    }
    fun cancel() {
        saveAfterLookup = false
        if (state.text != initial.text || state.name != initial.name || state.cover != initial.cover || state.url != initial.url)
            state = state.copy(discard = true)
        else exit()
    }
    fun keepEditing() { state = state.copy(discard = false) }
    fun catalogChanged(installed: CatalogMetadata?, installing: Boolean) {
        val changed = installed != null && installed != catalogMetadata
        catalogMetadata = installed
        catalogInstalling = installing
        if (changed && state.active && !state.preview && !state.saving) {
            if (state.busy) relookupAfterCurrent = true
            else if (state.lookupFailed || state.result?.issues?.isNotEmpty() == true) lookup()
        }
    }
    val inspection = CardInspectionController()
    fun inspect(entry: org.asyncttwiz.storage.ResolvedDeckEntry) {
        val result = state.result ?: return
        if (!closed && state.preview && entry in result.entries) {
            val definition = source?.definitions?.get(entry.printing.id)
            inspection.open(if (definition == null) InspectedCard.preview(entry, result.catalog)
                else InspectedCard.retained(DeckJson.digest(definition), definition))
        }
    }
    fun connectSave(action: () -> ((DeckSavePlan) -> SavedDeck)?, saved: (SavedDeck) -> Unit) {
        saveAction = action; this.saved = saved
    }
    /** Shared production wiring: reacquire credentials on retry, never regenerate retained intent. */
    fun connectSave(api: Decks, credential: () -> String?, saved: (SavedDeck) -> Unit) {
        connectSave(action = {
            credential()?.let { token -> { plan: DeckSavePlan -> kotlinx.coroutines.runBlocking { serviceRetry(current = { credential() == token }) {
                val accepted = api.submit(token, plan.definitions)
                if (accepted.digests.toSet() != plan.definitions.definitions.map { it.digest }.toSet())
                    throw DeckFailure("invalid_response")
                if (plan.target == null) {
                    val created = api.create(token, plan.deck)
                    if (plan.cover == null) created else {
                        api.update(token, created.deck.id, UpdateDeck(
                            CommandIds.uuidV5(plan.deck.requestId, "deck-cover"), cover = plan.cover))
                        api.read(token, created.deck.id)
                    }
                }
                else {
                    api.update(token, plan.target, plan.update())
                    // Uncertain retries return original outcomes; reopen current accepted state.
                    api.read(token, plan.target)
                }
            } } } }
        }, saved = saved)
    }
    fun open() { if (!closed) { epoch++; inspection.close(); retryPlan = null; source = null; chosenCover = null; saveAfterLookup = false; relookupAfterCurrent = false; state = DeckImportState(active = true); initial = state } }
    fun openEditor(detail: DeckDetail) {
        open()
        if (closed) return
        val retained = DeckEditorSource(detail)
        source = retained
        val cover = detail.saved.deck.cover?.definition?.let { digest ->
            retained.definitions[digest]?.let { DeckJson.digest(PhysicalCardFaces.normalize(it)) } ?: digest
        }
        state = state.copy(target = detail.saved.deck.id, name = detail.saved.deck.name, text = retained.text,
            cover = cover, editing = true)
        initial = state
        lookup()
    }
    fun openExisting(deck: DeckSummary) {
        open()
        if (!closed) state = state.copy(target = deck.id, name = deck.name)
    }
    fun mode(value: DeckImportMode) {
        if (!closed && state.target != null && !state.saving && state.mode != value) {
            retryPlan = null; state = state.copy(mode = value, error = null)
        }
    }
    fun exit() { epoch++; inspection.close(); retryPlan = null; source = null; chosenCover = null; saveAfterLookup = false; relookupAfterCurrent = false; state = DeckImportState() }
    fun edit(text: String) {
        if (!closed && state.active) { if (!state.saving) epoch++; retryPlan = null
            relookupAfterCurrent = false; saveAfterLookup = false
            state = state.copy(text = text, busy = false, fetchingUrl = false, result = null, preview = false, error = null, lookupFailed = false) }
    }
    fun lookup() {
        if (closed || !state.active || state.busy || state.saving || state.preview || catalogInstalling) return
        val generation = ++epoch
        val text = state.text
        val retained = source
        state = state.copy(busy = true, error = null)
        worker.execute {
            if (generation != epoch) return@execute
            val outcome = runCatching { retained?.resolve(text, resolve) ?: resolve(text) }
            val result = outcome.getOrNull()
            post { if (!closed && generation == epoch && state.active) {
                state = state.copy(busy = false, result = result, lookupFailed = outcome.isFailure,
                    error = if (outcome.isFailure) "Card records could not be read. Check storage or update card records, then retry lookup." else null)
                if (result?.resolved == true && state.cover != null && coverCards.none { it.digest == state.cover }) {
                    retryPlan = null; state = state.copy(cover = null)
                }
                if (saveAfterLookup) {
                    saveAfterLookup = false
                    if (result?.resolved == true) save()
                }
                val retry = relookupAfterCurrent
                relookupAfterCurrent = false
                if (retry && (outcome.isFailure || result?.issues?.isNotEmpty() == true)) lookup()
            } }
        }
    }
    fun review() { if (state.result?.resolved == true && !state.busy) state = state.copy(preview = true) }
    fun editList() { if (!state.saving) { inspection.close(); state = state.copy(preview = false, error = null) } }
    fun name(value: String) { if (value != state.name) {
        cancelUrlFetch()
        retryPlan = null; state = state.copy(name = value, error = null)
    } }
    fun save() {
        if (closed || !state.active || state.saving) return
        if (state.result?.resolved != true) {
            saveAfterLookup = true
            if (!state.busy) lookup()
            return
        }
        val input = state
        if (input.name.isBlank()) { state = state.copy(error = "Enter a deck name."); return }
        val action = saveAction?.invoke() ?: run {
            state = state.copy(error = "Sign in to save this deck."); return
        }
        val generation = ++epoch
        val previous = retryPlan
        val retained = source?.definitions.orEmpty()
        val cover = input.cover ?: coverCards.firstOrNull()?.digest
        state = state.copy(saving = true, error = null)
        worker.execute {
            if (generation != epoch) return@execute
            var plan = previous
            try {
                 if (plan == null) plan = DeckSavePlan.from(checkNotNull(input.result), input.name, input.target, input.mode,
                       cover, retained, input.editing)
                val result = action(checkNotNull(plan))
                runCatching { acceptedDefinitions(result.deck.id, plan.definitions.definitions) }
                post { if (!closed && generation == epoch && state.active) {
                    // A merged list is a delta, not an editable snapshot: never save it again after acceptance.
                    if (input.mode == DeckImportMode.MERGE ||
                        (state.text == input.text && state.name == input.name && state.cover == input.cover)) { exit(); saved(result) }
                    else {
                        retryPlan = null
                        initial = input.copy(target = result.deck.id, editing = true)
                        state = state.copy(saving = false, target = result.deck.id, editing = true)
                        if (state.result == null) lookup()
                    }
                } }
            } catch (failure: Exception) {
                val retained = plan
                post { if (!closed && generation == epoch && state.active) {
                     retryPlan = retained.takeIf { state.text == input.text && state.name == input.name && state.cover == input.cover }
                     state = state.copy(saving = false, error = deckError(failure))
                } }
            }
        }
    }
    fun back() { if (inspection.state != null) inspection.close() else if (state.choosingCover) closeCover()
        else if (state.discard) keepEditing() else if (state.preview) editList() else cancel() }
    override fun close() { closed = true; exit(); worker.shutdownNow() }
}
