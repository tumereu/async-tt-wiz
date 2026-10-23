package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import java.util.Locale

data class TokenSearchPage(val generation: String, val definitions: List<SubmittedDefinition>,
    val next: CatalogTokenCursor?)

/** Injected at the panel boundary; both operations must perform disk/CPU work off the UI thread. */
interface TokenSearchSource {
    suspend fun search(query: String, cursor: CatalogTokenCursor?): TokenSearchPage
    suspend fun isCurrent(generation: String): Boolean
}

/** The factory itself runs on IO: even opening/compatibility-indexing an old generation never touches main. */
class RepositoryTokenSearchSource(private val repository: () -> CatalogRepository) : TokenSearchSource {
    override suspend fun search(query: String, cursor: CatalogTokenCursor?): TokenSearchPage = withContext(Dispatchers.IO) {
        val page = repository().searchTokens(query, cursor)
        TokenSearchPage(page.generation, TokenActions.definitions(page), page.next)
    }
    override suspend fun isCurrent(generation: String): Boolean = withContext(Dispatchers.IO) {
        repository().tokenGeneration() == generation
    }
}

/** Personal panel state, confined to its UI scope. No board prediction or local creation lives here.
 * isActive must fence the current account/game/play/catalog identity and panel lifetime, including clicks.
 */
class TokenSearchController(private val source: TokenSearchSource, private val scope: CoroutineScope,
    private val isActive: () -> Boolean, private val submit: (SubmittedDefinition) -> Boolean,
    private val retained: List<SubmittedDefinition> = emptyList()) {
    var query by mutableStateOf("")
        private set
    var downloaded by mutableStateOf<List<SubmittedDefinition>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var selecting by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var next by mutableStateOf<CatalogTokenCursor?>(null)
        private set
    // Installed search can match a back face whose name differs from the canonical front definition.
    // Do not discard that explicit face match by filtering downloaded snapshots by front name again.
    val choices get() = (TokenActions.catalog(retained, query) + downloaded)
        .filter(TokenLinkRules::validDefinition).distinctBy { it.digest }
        .sortedWith(compareBy({ it.definition.name }, { it.digest }))
    private var generation: String? = null
    private var revision = 0L
    private var closed = false
    private var sent = false
    private var job: Job? = null
    private var selectionJob: Job? = null

    fun search(value: String = query) {
        query = value
        revision++; job?.cancel(); selectionJob?.cancel(); selecting = false
        downloaded = emptyList(); next = null; generation = null; error = null
        load(null)
    }
    fun more() { if (!loading && !selecting) next?.let(::load) }
    private fun active(ticket: Long) = !closed && revision == ticket && isActive()
    private fun load(cursor: CatalogTokenCursor?) {
        if (closed || !isActive()) return
        val ticket = revision
        val capturedQuery = query
        loading = true; error = null
        job = scope.launch {
            try {
                val page = source.search(capturedQuery, cursor)
                if (!source.isCurrent(page.generation)) throw CatalogGenerationChangedException()
                if (!active(ticket)) return@launch
                if (cursor != null && page.generation != generation) throw CatalogGenerationChangedException()
                downloaded = (if (cursor == null) page.definitions else downloaded + page.definitions).distinctBy { it.digest }
                generation = page.generation; next = page.next
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                if (active(ticket)) {
                    if (failure is CatalogGenerationChangedException) { downloaded = emptyList(); next = null; generation = null }
                    error = failure.message ?: "Token search failed. Retry."
                }
            } finally { if (active(ticket)) loading = false }
        }
    }
    fun select(definition: SubmittedDefinition) {
        if (closed || sent || selecting || !isActive() || definition !in choices) return
        val ticket = revision
        val selectedGeneration = generation.takeIf { definition in downloaded && definition !in retained && definition != TokenCatalog.blank }
        selecting = true
        selectionJob = scope.launch {
            try {
                if (selectedGeneration != null && !source.isCurrent(selectedGeneration)) throw CatalogGenerationChangedException()
                if (active(ticket)) sent = submit(definition)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                if (active(ticket)) {
                    downloaded = emptyList(); next = null; generation = null
                    error = failure.message ?: "Token selection failed. Retry."
                }
            } finally { if (active(ticket)) selecting = false }
        }
    }
    fun close() {
        closed = true; revision++; job?.cancel(); selectionJob?.cancel()
        downloaded = emptyList(); next = null; generation = null; loading = false; selecting = false
    }
}

/** Exact retained definitions from the catalog adapter; Blank is an ordinary catalog choice. */
object TokenActions {
    /** Uses the existing exact snapshot encoding; never re-fetches faces from a newer generation. */
    internal fun definitions(page: CatalogTokenPage): List<SubmittedDefinition> = page.tokens.map { token ->
        val definition = ResolvedDeckEntry(DeckSection.MAINBOARD, 1, token.faces, emptyList()).definition(page.metadata)
        SubmittedDefinition(DeckJson.digest(definition), definition).also { check(TokenLinkRules.validDefinition(it)) }
    }.distinctBy { it.digest }

    fun catalog(definitions: List<SubmittedDefinition>, query: String): List<SubmittedDefinition> =
        (listOf(TokenCatalog.blank) + definitions).distinctBy { it.digest }
            .filter { TokenLinkRules.validDefinition(it) && it.definition.name.lowercase(Locale.ROOT).contains(query.trim().lowercase(Locale.ROOT)) }
            .sortedWith(compareBy({ it.definition.name }, { it.digest }))

    fun removable(play: PlayState, scope: TokenLinkScope): List<String> = scope.cardIds.filter { id ->
        play.board.cards.any { it.id == id && it.token }
    }
}

/** A reusable production menu; all shared effects go through the captured-controller request seam. */
@Composable
fun TokenActionMenu(controller: TokenLinkController, play: PlayState) {
    val scope = controller.scope ?: return
    if (controller.pickingLink || controller.showingLinks) return
    val live = scope.cardIds.count { id -> play.board.cards.any { it.id == id } }
    TableMenuPanel(controller::close, title = "Actions · ${scope.cardIds.size} captured") {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ManualActionSquare("Clone", "", live > 0, onClick = {
                controller.clone(); if (controller.error == null) controller.close()
            })
            ManualActionSquare("Link", "", live > 0, onClick = controller::beginLink)
            ManualActionSquare("Unlink", "", live > 0, onClick = controller::links)
            if (TokenActions.removable(play, scope).isNotEmpty()) {
                ManualActionSquare("Remove token", "", onClick = {
                    controller.removeTokens(); if (controller.error == null) controller.close()
                })
            }
        }
            if (live == 0) Text("Captured cards are no longer available.")
            controller.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/** Search input and one-tap creation. No quantity, custom-token form, placement step or extra Send. */
@Composable
fun TokenCatalogDialog(controller: TokenLinkController, definitions: List<SubmittedDefinition>,
    loading: Boolean = false, catalogError: String? = null, enabled: Boolean = true,
    catalogIdentity: Any = controller, searchSource: TokenSearchSource? = null) {
    if (!controller.showingCatalog) return
    val context = LocalContext.current.applicationContext
    val source = remember(context, searchSource) { searchSource ?: RepositoryTokenSearchSource { AndroidCatalogStorage.repository(context) } }
    val scope = rememberCoroutineScope()
    val currentIdentity by rememberUpdatedState(catalogIdentity)
    val currentEnabled by rememberUpdatedState(enabled)
    val search = remember(controller, source, catalogIdentity, definitions) {
        TokenSearchController(source, scope,
            { controller.showingCatalog && currentIdentity == catalogIdentity && currentEnabled },
            { definition -> controller.create(definition); !controller.showingCatalog }, definitions)
    }
    DisposableEffect(search) { onDispose { search.close() } }
    LaunchedEffect(search, enabled) { if (enabled) search.search() }
    val matches = search.choices
    TableMenuPanel(controller::close, title = "Create token") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(search.query, { search.search(it) }, Modifier.fillMaxWidth(), label = { Text("Search tokens") }, singleLine = true)
            if (loading || search.loading || search.selecting) LinearProgressIndicator(Modifier.fillMaxWidth())
            catalogError?.let { Text(it) }
            search.error?.let { Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { search.search() }, enabled = enabled && !search.loading) { Text("Retry search") } }
            if (matches.isEmpty() && !search.loading && search.error == null) Text("No matching token definitions.")
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(matches, key = { it.digest }) { definition ->
                    MenuButton(definition.definition.name, Modifier.fillMaxWidth(), enabled = enabled && !search.selecting) { search.select(definition) }
                }
            }
            if (search.next != null) TextButton(onClick = search::more,
                enabled = enabled && !search.loading && !search.selecting) { Text("Load more") }
            controller.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun CreateTokenButton(controller: TokenLinkController) {
    MenuButton("Create token", onClick = controller::catalog)
}
