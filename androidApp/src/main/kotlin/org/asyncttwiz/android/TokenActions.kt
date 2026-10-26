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
        val catalog = repository()
        var page = catalog.searchTokens(query, cursor)
        val generation = page.generation
        val definitions = TokenActions.definitions(page).toMutableList()
        while (page.next != null) {
            ensureActive()
            page = catalog.searchTokens(query, page.next)
            if (page.generation != generation) throw CatalogGenerationChangedException()
            definitions += TokenActions.definitions(page)
        }
        TokenSearchPage(generation, definitions, null)
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
    private val retained: List<SubmittedDefinition> = emptyList(), private val localSearch: Boolean = false) {
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
    var favorites by mutableStateOf(TokenFavorites())
    val allChoices get() = TokenActions.ordered(TokenActions.catalog(retained, "") + downloaded, favorites)
    val choices get() = if (localSearch) allChoices.filter { definition ->
        definition.definition.faces.any { it.name.contains(query.trim(), ignoreCase = true) }
    } else TokenActions.ordered(TokenActions.catalog(retained, query) + downloaded, favorites)
    private var generation: String? = null
    private var revision = 0L
    private var closed = false
    private var sent = false
    private var job: Job? = null
    private var selectionJob: Job? = null

    fun search(value: String = query) {
        query = value
        revision++; job?.cancel(); selectionJob?.cancel(); selecting = false
        if (localSearch && generation != null) return
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
    val source = remember(context, searchSource) { searchSource ?: RepositoryTokenSearchSource { AndroidCatalogStorage.repository(context) } }
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
                    val favorite = TokenIdentity.favorite(definition.definition, search.favorites)
                    Row(Modifier.fillMaxWidth().semantics { contentDescription = "Create ${definition.definition.name} token" }
                        .combinedClickable(enabled = enabled && !search.selecting, onLongClickLabel = "Inspect token",
                        onClick = { search.select(definition) }, onLongClick = { inspection.open(card) })
                        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CardArtwork(card.artwork.first(), "—", 80, 112, Modifier.size(40.dp * cardSize, 56.dp * cardSize).border(1.dp, MenuBorder))
                        Column(Modifier.weight(1f)) {
                            Text(definition.definition.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(TokenActions.detail(definition.definition), style = MaterialTheme.typography.bodySmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        IconToggleButton(checked = favorite,
                            enabled = enabled && favoritesReady && !search.loading && !saving &&
                                (favorite || (search.favorites.keys?.size ?: 0) < 100), onCheckedChange = { checked ->
                            val keys = search.favorites.keys?.toMutableSet() ?: search.allChoices.filter {
                                TokenIdentity.favorite(it.definition, search.favorites)
                            }.map { TokenIdentity.key(it.definition) }.toMutableSet()
                            val key = TokenIdentity.key(definition.definition)
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
