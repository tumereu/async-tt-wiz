package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.asyncttwiz.storage.DeckSection

/**
 * WEB-039 (wireframe D8): the deck and cube editor on the desktop layout: the header (title, name, cover, Import from link,
 * Delete, Save with its key), the list on the left (Cards | Text, the Cards tab's filter and pictures as on the phone) and
 * the catalog search on the right instead of the phone's full-screen search page (LOB-106). WEB-040: Hide search folds the
 * search into a Search rail at the right edge, the list taking the width; the rail opens it again as it was left. WEB-041:
 * Ctrl+S saves.
 */
@Composable
internal fun DesktopCardListEditor(title: String, navigation: (() -> Unit)?, editor: CardListEditor, search: CardListSearch,
    tab: CardListTab, columns: PodBuildColumns, tabs: @Composable (Modifier) -> Unit, named: @Composable (Modifier) -> Unit,
    link: @Composable () -> Unit, canSave: Boolean, saving: Boolean, save: () -> Unit, saveTag: String, lookupPending: Boolean,
    error: String?, dismissError: () -> Unit, listField: @Composable ColumnScope.() -> Unit,
    feedback: @Composable ColumnScope.() -> Unit) {
    // WEB-040: the editor opens with the search shown; hidden, the search keeps its query, chips and Add to
    var searchShown by remember(editor) { mutableStateOf(true) }
    val showSearch = { shown: Boolean ->
        if (shown != searchShown) AppLog.info("editor", "Card list editor: search ${if (shown) "shown" else "hidden"}")
        searchShown = shown
    }
    // WEB-041: Ctrl+S (rebindable) saves, as the Save button does; not while a text field has the keyboard
    val bindings = currentKeyBindings()
    val currentSave by rememberUpdatedState(save)
    val currentCanSave by rememberUpdatedState(canSave)
    DesktopKeyboard.Listen(true) { key ->
        if (hotkeyOf(key, bindings) != HotkeyAction.Save) return@Listen false
        if (!key.repeat) {
            AppLog.info("keys", "Hotkey ${key.key}: save the card list${if (currentCanSave) "" else " (not saveable now)"}")
            if (currentCanSave) currentSave()
        }
        true
    }
    Column(Modifier.fillMaxSize().testTag("desktop-card-list-editor")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (navigation != null) HoldTip("Open navigation") { IconButton(onClick = navigation) { EntryIcon("Open navigation", "menu") } }
            Text(title, Modifier.padding(start = if (navigation == null) 8.dp else 0.dp), style = MaterialTheme.typography.titleLarge, maxLines = 1)
            named(Modifier.weight(1f))
            link()
            ScreenButton(onClick = save, Modifier.testTag(saveTag), enabled = canSave) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), LocalContentColor.current, 2.dp)
                else EntryIcon(null, "save")
                Spacer(Modifier.width(8.dp)); Text("Save")
                bindings.keyOf(HotkeyAction.Save)?.let { key ->
                    Spacer(Modifier.width(8.dp))
                    Text(key.label, style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = .75f))
                }
            }
        }
        HorizontalDivider(color = ScreenBorder)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    tabs(Modifier.width(320.dp))
                    if (tab == CardListTab.CARDS) editor.entries?.let { CardListSummary(editor, it, columns, Modifier.weight(1f)) }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        CardListTab.CARDS -> CardListCardsTab(editor, search, lookupPending, columns, summary = false, feedback,
                            openSearch = { section ->
                                AppLog.info("editor", "Card list editor: + on ${section.name.lowercase()}, search shown there")
                                showSearch(true)
                            })
                        CardListTab.TEXT -> Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f).fillMaxWidth(), content = listField)
                            Column(Modifier.height(96.dp).fillMaxWidth().verticalScrollable(), verticalArrangement = Arrangement.spacedBy(8.dp), content = feedback)
                        }
                    }
                    if (tab == CardListTab.CARDS && editor.entries != null) FloatingTransientError(error, dismissError, Modifier.align(Alignment.BottomCenter))
                }
            }
            if (searchShown) DesktopSearchPane(editor, search, hide = { showSearch(false) })
            else SearchRail { showSearch(true) }
        }
    }
}

/**
 * WEB-039: the editor's search pane: "Search cards" with Hide search, the field and quick filter chips (LOB-119), "Add to"
 * choosing the section (Main | Side | Maybe; cubes Main | Maybe), then the results as pictures: a click adds one copy to that
 * section (its square counts up, LOB-121), a click on a square takes one away, holding previews.
 */
@Composable
private fun DesktopSearchPane(editor: CardListEditor, search: CardListSearch, hide: () -> Unit) {
    val state = search.state
    val entries = editor.entries.orEmpty()
    LaunchedEffect(search.query) { delay(350); search.search() }
    var held by remember { mutableStateOf<Pair<String, InspectedCard?>?>(null) }
    val previewScroll = rememberScrollState()
    val previewScreen = holdPreviewScreenHeight()
    val grid = rememberLazyGridState()
    val nearEnd by remember { derivedStateOf {
        val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        last >= grid.layoutInfo.totalItemsCount - 6
    } }
    LaunchedEffect(nearEnd, state.next, state.loading) { if (nearEnd && state.next != null && !state.loading) search.more() }
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(Modifier.width(DesktopScreens.SearchPaneWidth).fillMaxHeight().testTag("card-search-pane"), color = ScreenBackground,
        border = BorderStroke(1.dp, ScreenBorder)) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Search cards", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    ScreenOutlinedButton(hide, Modifier.testTag("hide-search")) {
                        Text("Hide search"); Spacer(Modifier.width(4.dp)); EntryIcon(null, "chevron")
                    }
                }
                ScreenTextField(state.text, search::text, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("card-search-field").semantics { contentDescription = "Search cards" },
                    placeholder = "Search cards, e.g. t:goblin mv<=2", keyboardOptions = imeOptions(ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    trailingIcon = if (state.text.isEmpty()) null else ({
                        HoldTip("Clear search") { IconButton({ search.text("") }, Modifier.size(48.dp)) { EntryIcon("Clear search", "close") } }
                    }))
                SearchChips(SearchChipChoices(state.colors, state.types, state.values, search::toggle, search::toggle, search::toggle), oneRow = false)
                DestinationGroup(editor, Modifier.fillMaxWidth())
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.error != null -> Text(state.error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                        state.query.isBlank() -> Text("Type a card name or search terms, or choose filters.", Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.cards.isEmpty() && !state.loading && state.next == null -> Text("No cards found.", Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> LazyVerticalGrid(GridCells.Fixed(SEARCH_PANE_COLUMNS), Modifier.fillMaxSize().testTag("card-search-results"), grid,
                            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.cards, key = { it.key }) { card ->
                                SearchResult(editor, card, state.metadata, entries, onHold = { open ->
                                    if (open) {
                                        previewScroll.dispatchRawDelta(-previewScroll.value.toFloat())
                                        held = card.key to InspectedCard.preview(CardListEdits.entry(card, DeckSection.MAINBOARD), state.metadata)
                                    } else if (held?.first == card.key) held = null
                                }, onHoldScroll = { previewScroll.holdPreviewScroll(it, previewScreen) })
                            }
                        }
                    }
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }
            FloatingTransientError(editor.cardsError, { editor.cardsMessage(null) }, Modifier.align(Alignment.BottomCenter))
        }
    }
    held?.let { (key, card) -> PodCardArtPreview(card, state.cards.find { it.key == key }?.name ?: "Card", previewScroll) }
}

/** WEB-040: the hidden search as a slim rail at the editor's right edge: a search icon over "Search" written downwards. */
@Composable
private fun SearchRail(open: () -> Unit) {
    Surface(Modifier.width(DesktopScreens.SearchRailWidth).fillMaxHeight().testTag("search-rail")
        .clickable(onClickLabel = "Show search", role = Role.Button, onClick = open)
        .semantics { contentDescription = "Show search"; role = Role.Button },
        color = ScreenBackground, border = BorderStroke(1.dp, ScreenBorder)) {
        Column(Modifier.fillMaxSize().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(screenIcon("search"), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Search", Modifier.downwards(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** Text turned a quarter clockwise, taking the room it then needs (as CSS's vertical writing). */
private fun Modifier.downwards(): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(placeable.height, placeable.width) {
        placeable.place((placeable.height - placeable.width) / 2, (placeable.width - placeable.height) / 2)
    }
}.rotate(90f)

/** WEB-039: the search pane's results per row (wireframe D8). */
private const val SEARCH_PANE_COLUMNS = 4
