package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import java.util.Locale

data class TokenSearchPage(val generation: String, val definitions: List<SubmittedDefinition>,
    val next: CatalogTokenCursor?, val prepared: List<CatalogTokenChoice>? = null,
    val index: TokenChoiceIndex? = prepared?.let(::TokenChoiceIndex))

/** Snapshot access is synchronous; suspend operations must keep disk work off the UI thread. */
interface TokenSearchSource {
    fun cached(): TokenSearchPage? = null
    fun cachedError(): String? = null
    suspend fun search(query: String, cursor: CatalogTokenCursor?): TokenSearchPage
    suspend fun isCurrent(generation: String): Boolean
}

/** Warmed at startup and after import. Dialogs only read the process snapshot, never provider pages. */
class RepositoryTokenSearchSource(private val repository: () -> CatalogRepository) : TokenSearchSource {
    private val mutex = Mutex()
    private val snapshot = MutableStateFlow<TokenCatalogSnapshot?>(null)
    internal val snapshots = snapshot.asStateFlow()
    override fun cached() = snapshot.value?.page
    override fun cachedError() = snapshot.value?.failure?.message

    suspend fun warm(prepare: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val catalog = repository()
                if (prepare) catalog.prepareTokens()
                val list = catalog.loadTokens()
                if (cached()?.generation != list.generation) snapshot.value = TokenCatalogSnapshot(TokenSearchPage(list.generation,
                    list.choices.map { it.submitted }, null, list.choices))
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) { snapshot.value = TokenCatalogSnapshot(failure = error) }
        }
    }
    override suspend fun search(query: String, cursor: CatalogTokenCursor?): TokenSearchPage {
        // Also resolves when startup was skipped/cancelled. Never await an unowned deferred.
        return withTimeoutOrNull(30_000) {
            if (snapshot.value == null) warm()
            cached() ?: throw (snapshot.value?.failure ?: CatalogTokensNotReadyException())
        } ?: throw CatalogTokensNotReadyException()
    }
    override suspend fun isCurrent(generation: String): Boolean = withContext(Dispatchers.IO) {
        repository().tokenGeneration() == generation
    }
}

internal data class TokenCatalogSnapshot(val page: TokenSearchPage? = null, val failure: Exception? = null)

internal object AndroidTokenCatalog {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var application: android.content.Context? = null
    private var instance: RepositoryTokenSearchSource? = null
    @Synchronized fun source(context: android.content.Context): RepositoryTokenSearchSource {
        val app = context.applicationContext
        // One source per application/process, also when a host test recreates the application.
        return instance?.takeIf { application === app }
            ?: RepositoryTokenSearchSource { AndroidCatalogStorage.repository(app) }.also {
                application = app
                instance = it
                scope.launch { it.warm(prepare = true) }
            }
    }

    /** Publication notification; disk reads, decoding and indexing stay on the process IO scope. */
    fun refresh(context: android.content.Context): Job {
        val source = source(context)
        return scope.launch { source.warm() }
    }
}

/** Prepared keys and alphabetical order are reused across opens and every keystroke. */
class TokenChoiceIndex(choices: List<CatalogTokenChoice>) {
    private val alphabetical = (listOf(blank) + choices).distinctBy { it.identity }
        .sortedWith(compareBy({ it.name.lowercase(Locale.ROOT) }, { it.identity }))
    private val byDigest = alphabetical.associateBy { it.submitted.digest }
    private val identities = alphabetical.map { it.identity }.toHashSet()
    fun withRetained(retained: List<CatalogTokenChoice>): TokenChoiceIndex =
        if (retained.all { it.identity in identities }) this else TokenChoiceIndex(retained + alphabetical)
    fun choice(definition: SubmittedDefinition): CatalogTokenChoice = byDigest.getValue(definition.digest)
    fun ordered(favorites: TokenFavorites): List<CatalogTokenChoice> {
        val keys = favorites.keys?.toHashSet()
        val favorite = mutableListOf<CatalogTokenChoice>()
        val rest = mutableListOf<CatalogTokenChoice>()
        for (choice in alphabetical) if (choice.identity != blank.identity) {
            if (keys?.contains(choice.identity) ?: (choice.name in TokenIdentity.defaultNames)) favorite += choice else rest += choice
        }
        return listOf(blank) + favorite + rest
    }
    companion object {
        private val blank = CatalogTokenChoice.from(TokenCatalog.blank)
        fun filter(ordered: List<CatalogTokenChoice>, query: String): List<SubmittedDefinition> {
            val normalized = query.trim().lowercase(Locale.ROOT)
            return ordered.filter { choice -> choice.searchNames.any { it.contains(normalized) } }.map { it.submitted }
        }
    }
}

/** Personal panel state, confined to its UI scope. No board prediction or local creation lives here.
 * isActive must fence the current account/game/play/catalog identity and panel lifetime, including clicks.
 */
class TokenSearchController(private val source: TokenSearchSource, private val scope: CoroutineScope,
    private val isActive: () -> Boolean, private val submit: (SubmittedDefinition) -> Boolean,
    private val retained: List<SubmittedDefinition> = emptyList(), private val localSearch: Boolean = false) {
    var query by mutableStateOf("")
        private set
    private val initial = if (localSearch) source.cached() else null
    var downloaded by mutableStateOf<List<SubmittedDefinition>>(initial?.definitions.orEmpty())
        private set
    var loading by mutableStateOf(false)
        private set
    var selecting by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(if (localSearch) source.cachedError() else null)
        private set
    var next by mutableStateOf<CatalogTokenCursor?>(null)
        private set
    // Installed search can match a back face whose name differs from the canonical front definition.
    // Do not discard that explicit face match by filtering downloaded snapshots by front name again.
    var favorites by mutableStateOf(TokenFavorites())
    private val retainedChoices = retained.filter(TokenLinkRules::validDefinition).map(CatalogTokenChoice::from)
    private var index by mutableStateOf((initial?.index ?: TokenChoiceIndex(initial?.definitions.orEmpty().map(CatalogTokenChoice::from)))
        .withRetained(retainedChoices))
    private val ordered by derivedStateOf { index.ordered(favorites) }
    val allChoices get() = if (localSearch) ordered.map { it.submitted }
        else TokenActions.ordered(TokenActions.catalog(retained, "") + downloaded, favorites)
    val choices get() = if (localSearch) TokenChoiceIndex.filter(ordered, query)
        else TokenActions.ordered(TokenActions.catalog(retained, query) + downloaded, favorites)
    fun detail(definition: SubmittedDefinition) = if (localSearch) index.choice(definition).detail
        else TokenActions.detail(definition.definition)
    fun identity(definition: SubmittedDefinition) = if (localSearch) index.choice(definition).identity
        else TokenIdentity.key(definition.definition)
    fun favorite(definition: SubmittedDefinition) = favorites.keys?.contains(identity(definition))
        ?: (definition.definition.name in TokenIdentity.defaultNames)
    private var generation: String? = initial?.generation
    private var revision = 0L
    private var closed = false
    private var sent = false
    private var job: Job? = null
    private var selectionJob: Job? = null

    fun search(value: String = query) {
        query = value
        revision++; job?.cancel(); selectionJob?.cancel(); selecting = false
        if (localSearch && generation != null) return
        if (localSearch && source.cachedError() != null) { error = source.cachedError(); loading = false; return }
        downloaded = emptyList(); next = null; generation = null; error = null
        load(null)
    }
    fun more() { if (!loading && !selecting) next?.let(::load) }
    private fun active(ticket: Long) = !closed && revision == ticket && isActive()
    private fun load(cursor: CatalogTokenCursor?) {
        if (closed || !isActive()) return
        val ticket = revision
        val capturedQuery = if (localSearch) "" else query
        loading = true; error = null
        job = scope.launch {
            try {
                val page = source.search(capturedQuery, cursor)
                if (!source.isCurrent(page.generation)) throw CatalogGenerationChangedException()
                if (!active(ticket)) return@launch
                if (cursor != null && page.generation != generation) throw CatalogGenerationChangedException()
                downloaded = (if (cursor == null) page.definitions else downloaded + page.definitions).distinctBy { it.digest }
                if (localSearch) index = (page.index ?: TokenChoiceIndex(downloaded.map(CatalogTokenChoice::from))).withRetained(retainedChoices)
                generation = page.generation; next = page.next
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                if (active(ticket)) {
                    if (failure is CatalogGenerationChangedException) { downloaded = emptyList(); next = null; generation = null
                        index = TokenChoiceIndex(retainedChoices) }
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
                    index = TokenChoiceIndex(retainedChoices)
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
    private val blankKey = TokenIdentity.key(TokenCatalog.blank.definition)
    fun ordered(definitions: List<SubmittedDefinition>, favorites: TokenFavorites = TokenFavorites()): List<SubmittedDefinition> {
        val keys = favorites.keys?.toHashSet()
        return definitions.filter(TokenLinkRules::validDefinition).map { TokenIdentity.key(it.definition) to it }
            .distinctBy { it.first }.sortedWith(compareBy<Pair<String, SubmittedDefinition>>(
                { (key, token) -> if (key == blankKey) 0
                    else if (keys?.contains(key) ?: (token.definition.name in TokenIdentity.defaultNames)) 1 else 2 },
                { it.second.definition.name.lowercase(Locale.ROOT) }, { it.first })).map { it.second }
    }

    fun detail(definition: CardDefinition): String = definition.faces.first().let { face ->
        listOf(face.corner.ifEmpty { if (face.power.isNotEmpty() || face.toughness.isNotEmpty()) "${face.power}/${face.toughness}" else "" },
            TokenIdentity.attributes(face)).filter(String::isNotBlank).joinToString(" · ")
    }
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
    if (controller.showingLinks) return
    val live = scope.cardIds.count { id -> play.board.cards.any { it.id == id } }
    TableMenuPanel(controller::close, title = "Actions · ${scope.cardIds.size} captured") {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ManualActionSquare("Clone", "", live > 0, onClick = {
                controller.clone(); if (controller.error == null) controller.close()
            })
            ManualActionSquare("Link", "", controller.canLink(), onClick = { if (controller.linkSelected()) controller.close() })
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
@OptIn(ExperimentalFoundationApi::class)
internal fun TokenCatalogDialog(controller: TokenLinkController, definitions: List<SubmittedDefinition>,
    loading: Boolean = false, catalogError: String? = null, enabled: Boolean = true,
    catalogIdentity: Any = controller, searchSource: TokenSearchSource? = null, favoritesSource: TokenFavoritesSource? = null) {
    if (!controller.showingCatalog) return
    val context = LocalContext.current.applicationContext
    val source = remember(context, searchSource) { searchSource ?: AndroidTokenCatalog.source(context) }
    val scope = rememberCoroutineScope()
    val currentIdentity by rememberUpdatedState(catalogIdentity)
    val currentEnabled by rememberUpdatedState(enabled)
    val search = remember(controller, source, catalogIdentity, definitions) {
        TokenSearchController(source, scope,
            { controller.showingCatalog && currentIdentity == catalogIdentity && currentEnabled },
            { definition -> controller.create(definition); !controller.showingCatalog }, definitions, localSearch = true)
    }
    DisposableEffect(search) { onDispose { search.close() } }
    LaunchedEffect(search, enabled) { if (enabled) search.search() }
    var favoriteError by remember(search) { mutableStateOf<String?>(null) }
    var favoritesReady by remember(search) { mutableStateOf(favoritesSource == null) }
    var saving by remember(search) { mutableStateOf(false) }
    LaunchedEffect(search, favoritesSource) {
        favoritesSource?.let { source ->
            search.favorites = source.cached()
            try { search.favorites = source.refresh() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { favoriteError = "Favorites are offline; using saved favorites." }
        }
        favoritesReady = true
    }
    val inspection = remember(search) { CardInspectionController() }
    val matches = search.choices
    val cardSize = rememberCardAppearance().size(CardArea.PANELS)
    val listState = rememberLazyListState()
    LaunchedEffect(search, listState) {
        snapshotFlow { Triple(search.next, search.loading,
            (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= listState.layoutInfo.totalItemsCount - 4) }
            .collect { (next, loading, nearEnd) -> if (next != null && !loading && nearEnd) search.more() }
    }
    TableMenuPanel(controller::close, title = "Create token") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(search.query, { search.search(it) }, Modifier.fillMaxWidth(), label = { Text("Search tokens") }, singleLine = true)
            if (loading || search.loading || search.selecting) LinearProgressIndicator(Modifier.fillMaxWidth())
            catalogError?.let { Text(it) }
            favoriteError?.let { Text(it) }
            search.error?.let { Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { search.search() }, enabled = enabled && !search.loading) { Text("Retry search") } }
            if (matches.isEmpty() && !search.loading && search.error == null) Text("No matching token definitions.")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), state = listState) {
                items(matches, key = { it.digest }) { definition ->
                    val card = remember(definition) { InspectedCard.retained(definition.digest, definition.definition) }
                    val favorite = search.favorite(definition)
                    Row(Modifier.fillMaxWidth().semantics { contentDescription = "Create ${definition.definition.name} token" }
                        .combinedClickable(enabled = enabled && !search.selecting, onLongClickLabel = "Inspect token",
                        onClick = { search.select(definition) }, onLongClick = { inspection.open(card) })
                        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CardArtwork(card.artwork.first(), "—", 80, 112, Modifier.size(40.dp * cardSize, 56.dp * cardSize).border(1.dp, MenuBorder))
                        Column(Modifier.weight(1f)) {
                            Text(definition.definition.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(search.detail(definition), style = MaterialTheme.typography.bodySmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        IconToggleButton(checked = favorite,
                            enabled = enabled && favoritesReady && !search.loading && !saving &&
                                (favorite || (search.favorites.keys?.size ?: 0) < 100), onCheckedChange = { checked ->
                            val keys = search.favorites.keys?.toMutableSet() ?: search.allChoices.filter {
                                search.favorite(it)
                            }.map { search.identity(it) }.toMutableSet()
                            val key = search.identity(definition)
                            if (checked) keys += key else keys -= key
                            search.favorites = TokenFavorites(keys.sorted())
                            val updated = search.favorites
                            saving = true
                            scope.launch {
                                try { favoritesSource?.set(updated); favoriteError = null }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { favoriteError = "Saved offline; favorites will sync when reopened." }
                                finally { saving = false }
                            }
                        }) { Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            if (favorite) "Remove favorite ${definition.definition.name}" else "Favorite ${definition.definition.name}") }
                    }
                }
            }
            controller.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (inspection.state != null) CardInspection(inspection, token = true)
}

@Composable
fun CreateTokenButton(controller: TokenLinkController) {
    MenuButton("Create token", onClick = controller::catalog)
}
