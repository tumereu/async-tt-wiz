package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ArtworkReference
import org.asyncttwiz.storage.CatalogCard
import org.asyncttwiz.storage.CatalogMetadata
import org.asyncttwiz.storage.DeckSection
import org.asyncttwiz.storage.ResolvedDeckEntry
import org.asyncttwiz.storage.definition as catalogDefinition

internal enum class CardListTab(val label: String) { CARDS("Cards"), TEXT("Text") }

/**
 * The one editor of decks and cubes: name (and a deck's cover) on top, Cards | Text tabs, one full-width Save at the
 * bottom. Changes in either tab are kept only by Save. The Cards tab's Add cards opens the full-screen [search] page.
 */
@Composable
internal fun CardListEditorScaffold(title: String, navigation: (() -> Unit)?, landscape: Boolean, editor: CardListEditor,
    search: CardListSearch, tab: CardListTab, onTab: (CardListTab) -> Unit, openLink: () -> Unit, linkEnabled: Boolean,
    canSave: Boolean, saving: Boolean, save: () -> Unit, saveTag: String, lookupPending: Boolean, error: String?,
    dismissError: () -> Unit, nameRow: @Composable () -> Unit, listField: @Composable ColumnScope.() -> Unit,
    feedback: @Composable ColumnScope.() -> Unit) {
    if (search.state.open) { CardSearchPage(editor, search, landscape); DeckCardInspection(editor.inspection); return }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val columns = rememberDeckViewColumns(if (editor.kind == CardListKind.CUBE) DeckViewScreen.CUBE_EDITOR else DeckViewScreen.EDITOR)
    val tabs: @Composable (Modifier) -> Unit = { modifier ->
        TabRow(selectedTabIndex = tab.ordinal, modifier) {
            CardListTab.entries.forEach { entry ->
                Tab(selected = tab == entry, onClick = { keyboard?.hide(); focus.clearFocus(); onTab(entry) },
                    modifier = Modifier.testTag("card-list-tab-${entry.name.lowercase()}"), text = { Text(entry.label) })
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        // Landscape keeps the pictures tall enough: the name joins the title row, the Cards summary the tab row.
        val link: @Composable () -> Unit = {
            val open = { keyboard?.hide(); focus.clearFocus(); openLink() }
            if (landscape || androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 400)
                ScreenOutlinedButton(open, Modifier.testTag("open-import-link"), enabled = linkEnabled) {
                    EntryIcon(null, "link"); Spacer(Modifier.width(8.dp)); Text(IMPORT_FROM_LINK)
                }
            else ScreenIconButton(IMPORT_FROM_LINK, "link", open, Modifier.testTag("open-import-link"), enabled = linkEnabled)
        }
        if (landscape) Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (navigation != null) IconButton(onClick = navigation) { EntryIcon("Open navigation", "menu") }
            Text(title, Modifier.padding(start = if (navigation == null) 8.dp else 0.dp), style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Box(Modifier.weight(1f)) { nameRow() }
            link()
        } else ScreenHeader(title, navigation) { link() }
        if (!landscape) {
            Box(Modifier.padding(horizontal = 16.dp)) { nameRow() }
            tabs(Modifier.padding(top = 8.dp))
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            tabs(Modifier.width(320.dp))
            if (tab == CardListTab.CARDS) editor.entries?.let { CardListSummary(editor, it, columns, Modifier.weight(1f)) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                CardListTab.CARDS -> CardListCardsTab(editor, search, lookupPending, columns, summary = !landscape, feedback)
                CardListTab.TEXT -> if (landscape) Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), content = listField)
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScrollable(), verticalArrangement = Arrangement.spacedBy(8.dp), content = feedback)
                } else Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f).fillMaxWidth(), content = listField)
                    Column(Modifier.height(96.dp).fillMaxWidth().verticalScrollable(), verticalArrangement = Arrangement.spacedBy(8.dp), content = feedback)
                }
            }
            if (tab == CardListTab.CARDS && editor.entries != null) FloatingTransientError(error, dismissError, Modifier.align(Alignment.BottomCenter))
        }
        ScreenButton(onClick = { keyboard?.hide(); focus.clearFocus(); save() },
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = if (landscape) 8.dp else 16.dp, top = 8.dp).testTag(saveTag),
            enabled = canSave) {
            if (saving) CircularProgressIndicator(Modifier.size(20.dp), LocalContentColor.current, 2.dp)
            else EntryIcon(null, "save")
            Spacer(Modifier.width(8.dp)); Text("Save")
        }
    }
    DeckCardInspection(editor.inspection)
}

/** Leaving an editor with unsaved changes (LOB-064). */
@Composable
internal fun DiscardChangesDialog(keepEditing: () -> Unit, discard: () -> Unit) {
    ScreenDialog(keepEditing) {
        Text("Discard unsaved changes?", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            ScreenOutlinedButton(keepEditing) { Text("Keep editing") }
            ScreenButton(discard) { Text("Discard") }
        }
    }
}

/** The tab an editor opens on: an existing list on its cards, a new one on its text to paste into. */
@Composable
internal fun rememberCardListTab(existing: Boolean) = rememberSaveable { mutableStateOf(if (existing) CardListTab.CARDS else CardListTab.TEXT) }

@Composable
private fun Modifier.verticalScrollable() = this.then(Modifier.verticalScroll(rememberScrollState()))

/** Every displayed card's definition, read once per printing: a retained card's saved one, otherwise the catalog's. */
@Composable
private fun rememberCardDefinitions(editor: CardListEditor): (ResolvedDeckEntry) -> CardDefinition {
    val cache = remember(editor) { HashMap<String, CardDefinition>() }
    return { entry ->
        cache.getOrPut(entry.printing.id) { editor.retainedDefinition(entry.printing.id) ?: entry.catalogDefinition(editor.listCatalog) }
    }
}

/** The list as pictures by section: swipe moves one copy to the next section, tap opens the card menu, hold previews. */
@Composable
private fun CardListCardsTab(editor: CardListEditor, search: CardListSearch, lookupPending: Boolean, columns: PodBuildColumns,
    summary: Boolean, feedback: @Composable ColumnScope.() -> Unit) {
    val entries = editor.entries
    val definitionOf = rememberCardDefinitions(editor)
    val menus = remember { PodCardMenuOwner() }
    var held by remember { mutableStateOf<Pair<String, PreparationTile>?>(null) }
    val previewScroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        if (entries == null) Column(Modifier.fillMaxSize().padding(16.dp).verticalScrollable(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (lookupPending) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("Looking up cards…")
            } else Text("The list has problems. Fix them in the Text tab.", style = MaterialTheme.typography.titleSmall)
            feedback()
        } else {
            Column(Modifier.fillMaxSize()) {
                if (summary) CardListSummary(editor, entries, columns, Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("card-list-cards"), contentPadding = PaddingValues(bottom = 88.dp)) {
                    for (section in editor.kind.sections) {
                        val saved = section.saved()
                        val rows = entries.filter { it.section == section }
                        val definitions = rows.associate { it.printing.id to definitionOf(it) }
                        val groups = preparationGroups(rows.map { it.printing.id to it.quantity.toLong() }, definitions)
                        visualDeckSection(saved, rows.sumOf { it.quantity.toLong() }, groups, columns.count) { tile ->
                            val key = "$section/${tile.digest}"
                            EditorCard(editor, tile, key, section, menus, onHold = { open ->
                                if (open) {
                                    menus.open = null
                                    previewScroll.dispatchRawDelta(-previewScroll.value.toFloat())
                                    held = key to tile
                                } else if (held?.first == key) held = null
                            }, onHoldScroll = { previewScroll.dispatchRawDelta(it) })
                        }
                    }
                }
            }
        }
        Surface(Modifier.align(Alignment.BottomStart).padding(16.dp).size(56.dp).testTag("add-cards"),
            shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 4.dp) {
            IconButton({ search.open(CardListEdits.total(entries.orEmpty())) }, enabled = entries != null && editor.editable,
                modifier = Modifier.semantics { contentDescription = "Add cards" }) {
                Icon(screenIcon("add"), null)
            }
        }
    }
    held?.second?.let { tile ->
        val card = remember(tile.digest, tile.definition) {
            tile.definition?.takeIf { it.faces.isNotEmpty() }?.let { InspectedCard.retained(tile.digest, it) }
        }
        PodCardArtPreview(card, tile.name, previewScroll)
    }
}

/** The Cards tab's line: the section counts and the columns control. */
@Composable
private fun CardListSummary(editor: CardListEditor, entries: List<ResolvedDeckEntry>, columns: PodBuildColumns, modifier: Modifier) {
    Row(modifier.height(40.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(cardListSummary(editor.kind, entries), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        DeckViewColumnsControl(columns)
    }
}

/** "60 main · 15 side · 2 maybe" (cubes "540 main · 12 maybe"); sections without cards other than Main are left out. */
internal fun cardListSummary(kind: CardListKind, entries: List<ResolvedDeckEntry>): String = kind.sections.mapNotNull { section ->
    val count = entries.filter { it.section == section }.sumOf { it.quantity.toLong() }
    if (count == 0L && section != DeckSection.MAINBOARD) null else "$count ${section.saved().shortLabel().lowercase()}"
}.joinToString(" · ")

/** One editor tile: swipe moves one copy to the next section; tap opens +1, −1, Remove all, Move to…, Inspect. */
@Composable
private fun EditorCard(editor: CardListEditor, tile: PreparationTile, key: String, section: DeckSection, menus: PodCardMenuOwner,
    onHold: (Boolean) -> Unit, onHoldScroll: (Float) -> Unit) {
    val next = editor.kind.next(section)
    val enabled = editor.editable
    val name = tile.name
    fun apply(change: (List<ResolvedDeckEntry>) -> List<ResolvedDeckEntry>): Boolean {
        val entries = editor.entries ?: return false
        if (!editor.editable) return false
        editor.applyCards(change(entries), editor.listCatalog); return true
    }
    fun moveOne() = apply { CardListEdits.move(it, tile.digest, section, next, 1) }
    fun inspect() { tile.definition?.takeIf { it.faces.isNotEmpty() }?.let { editor.inspection.open(InspectedCard.retained(tile.digest, it)) } }
    var choosing by remember(key) { mutableStateOf(false) }
    LaunchedEffect(menus.open == key) { if (menus.open != key) choosing = false }
    SwipeDeckCard(tile, key, "editor-card-$key", section.saved().sectionLabel(), next.saved(), enabled, menus,
        capture = { { moveOne() } }, inspect = ::inspect, onHold = onHold, onHoldScroll = onHoldScroll,
        actions = listOf(CustomAccessibilityAction("Move one $name to ${next.saved().sectionLabel()}") { enabled && moveOne() })) { close ->
        if (!choosing) {
            DropdownMenuItem(text = { Text("+1") }, enabled = enabled, onClick = {
                close()
                val entries = editor.entries ?: return@DropdownMenuItem
                val result = CardListEdits.increase(editor.kind, entries, tile.digest, section)
                if (result.refused != null) editor.cardsMessage(result.refused) else editor.applyCards(result.entries, editor.listCatalog)
            })
            DropdownMenuItem(text = { Text("−1") }, enabled = enabled, onClick = { close(); apply { CardListEdits.change(it, tile.digest, section, -1) } })
            DropdownMenuItem(text = { Text("Remove all") }, enabled = enabled, onClick = { close(); apply { CardListEdits.removeAll(it, tile.digest, section) } })
            DropdownMenuItem(text = { Text("Move to…") }, enabled = enabled, onClick = { choosing = true })
            DropdownMenuItem(text = { Text("Inspect") }, onClick = { close(); inspect() })
        } else editor.kind.sections.filter { it != section }.forEach { target ->
            DropdownMenuItem(text = { Text(if (tile.quantity > 1) "All ${tile.quantity} to ${target.saved().sectionLabel()}"
                else "To ${target.saved().sectionLabel()}", color = target.saved().sectionColor()) }, enabled = enabled,
                onClick = { close(); apply { CardListEdits.move(it, tile.digest, section, target) } })
        }
    }
}

/**
 * Full-screen catalog search (Add cards): the search field and quick filter chips on top, results as pictures, then the
 * destination group (Main | Side | Maybe; cubes Main | Maybe) and "Done · +N cards" within thumb reach. Tap a result to
 * add one copy to the destination, tap a section square to remove one from it, hold to preview. Done or Back keeps every change.
 */
@Composable
private fun CardSearchPage(editor: CardListEditor, search: CardListSearch, landscape: Boolean) {
    val state = search.state
    val entries = editor.entries.orEmpty()
    BackHandler { search.close() }
    LaunchedEffect(search.query) { delay(350); search.search() }
    var held by remember { mutableStateOf<Pair<String, InspectedCard?>?>(null) }
    val previewScroll = rememberScrollState()
    val grid = rememberLazyGridState()
    val columns = rememberDeckViewColumns(DeckViewScreen.SEARCH)
    // Pages end early when the work budget runs out; keep reading while the end of the results is in view.
    val nearEnd by remember { derivedStateOf {
        val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        last >= grid.layoutInfo.totalItemsCount - 6
    } }
    LaunchedEffect(nearEnd, state.next, state.loading) { if (nearEnd && state.next != null && !state.loading) search.more() }
    val keyboard = LocalSoftwareKeyboardController.current
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().testTag("card-search")) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ search.close() }) { EntryIcon("Back to the list", "chevron-left") }
            ScreenTextField(state.text, search::text, Modifier.weight(1f).testTag("card-search-field")
                .semantics { contentDescription = "Search cards" }, placeholder = "Search cards, e.g. t:goblin mv<=2",
                keyboardOptions = imeOptions(ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                trailingIcon = if (state.text.isEmpty()) null else ({
                    IconButton({ search.text("") }, Modifier.size(48.dp)) { EntryIcon("Clear search", "close") }
                }))
        }
        SearchChips(search, oneRow = landscape)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.error != null -> Text(state.error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                state.query.isBlank() -> Text("Type a card name or search terms, or choose filters.", Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.cards.isEmpty() && !state.loading && state.next == null -> Text("No cards found.", Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyVerticalGrid(GridCells.Fixed(columns.count), Modifier.fillMaxSize().testTag("card-search-results"), grid,
                    contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.cards, key = { it.key }) { card ->
                        SearchResult(editor, card, state.metadata, entries, onHold = { open ->
                            if (open) {
                                previewScroll.dispatchRawDelta(-previewScroll.value.toFloat())
                                held = card.key to InspectedCard.preview(CardListEdits.entry(card, DeckSection.MAINBOARD), state.metadata)
                            } else if (held?.first == card.key) held = null
                        }, onHoldScroll = { previewScroll.dispatchRawDelta(it) })
                    }
                }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
        HorizontalDivider()
        val added = CardListEdits.total(entries) - state.baseline
        val done: @Composable (Modifier) -> Unit = { modifier ->
            ScreenButton({ search.close() }, modifier.testTag("card-search-done")) {
                EntryIcon(null, "ready"); Spacer(Modifier.width(8.dp))
                Text(cardSearchDoneLabel(added))
            }
        }
        // Landscape puts Done beside the destination so the results keep their height.
        if (landscape) Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            DestinationGroup(editor, Modifier.weight(1f))
            done(Modifier.width(240.dp))
        } else {
            DestinationGroup(editor, Modifier.fillMaxWidth())
            done(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp))
        }
    }
    FloatingTransientError(editor.cardsError, { editor.cardsMessage(null) }, Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }
    held?.let { (key, card) -> PodCardArtPreview(card, state.cards.find { it.key == key }?.name ?: "Card", previewScroll) }
}

/** "Done · +3 cards", "Done · −1 card", or "Done" when nothing changed in this visit. */
internal fun cardSearchDoneLabel(added: Long): String = when {
    added == 0L -> "Done"
    else -> "Done · ${if (added > 0) "+" else "−"}${kotlin.math.abs(added)} ${if (kotlin.math.abs(added) == 1L) "card" else "cards"}"
}

@Composable
private fun SearchChips(search: CardListSearch, oneRow: Boolean) {
    val state = search.state
    @Composable fun row(content: @Composable RowScope.() -> Unit) =
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    val types: @Composable RowScope.() -> Unit = {
        SearchType.entries.forEach { type -> FilterChip(type in state.types, { search.toggle(type) }, { Text(type.label) }) }
    }
    Column(Modifier.padding(vertical = 4.dp)) {
        row {
            SearchColor.entries.forEach { color ->
                FilterChip(color in state.colors, { search.toggle(color) }, { Text(color.label) },
                    Modifier.semantics { contentDescription = "Colour ${color.label}" })
            }
            Spacer(Modifier.width(8.dp))
            SearchManaValue.entries.forEach { value ->
                FilterChip(value in state.values, { search.toggle(value) }, { Text(value.label) },
                    Modifier.semantics { contentDescription = "Mana value ${value.label}" })
            }
            if (oneRow) { Spacer(Modifier.width(8.dp)); types() }
        }
        if (!oneRow) row(types)
    }
}

/** Where tapped results go: one button per section in its colour, the chosen one filled. */
@Composable
private fun DestinationGroup(editor: CardListEditor, modifier: Modifier) {
    Row(modifier.padding(horizontal = 16.dp, vertical = 8.dp).selectableGroup(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Add to", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        editor.kind.sections.forEach { section ->
            val saved = section.saved()
            val chosen = editor.destination == section
            Surface(onClick = { editor.destination(section) }, modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                .testTag("destination-${saved.shortLabel().lowercase()}").semantics { selected = chosen; role = Role.RadioButton },
                shape = RoundedCornerShape(6.dp), color = if (chosen) saved.sectionColor() else MaterialTheme.colorScheme.surface,
                contentColor = if (chosen) Color.White else saved.sectionColor(), border = BorderStroke(1.dp, saved.sectionColor())) {
                Box(contentAlignment = Alignment.Center) { Text(saved.shortLabel(), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

/** One result: its picture and, at its bottom left, a small square per section that holds copies (Main, Side, Maybe in that
 * order and colour) with that section's count; tapping a square removes one copy from it. Tap adds one copy to the chosen
 * section, whose square pulses; hold previews. */
@Composable
private fun SearchResult(editor: CardListEditor, card: CatalogCard, metadata: CatalogMetadata?, entries: List<ResolvedDeckEntry>,
    onHold: (Boolean) -> Unit, onHoldScroll: (Float) -> Unit) {
    val destination = editor.destination
    val count = CardListEdits.count(entries, card.name, destination)
    val squares = editor.kind.sections.mapNotNull { section ->
        CardListEdits.count(entries, card.name, section).takeIf { it > 0 }?.let { section to it }
    }
    val currentHold by rememberUpdatedState(onHold)
    val currentHoldScroll by rememberUpdatedState(onHoldScroll)
    // Read the destination when the tap lands, not when this result was composed.
    val add: () -> Unit = add@{
        if (!editor.editable) return@add
        val result = CardListEdits.add(editor.kind, editor.entries ?: return@add, card.faces, editor.destination)
        if (result.refused != null) editor.cardsMessage(result.refused) else editor.applyCards(result.entries, editor.listCatalog ?: metadata)
    }
    val currentAdd by rememberUpdatedState(add)
    val remove: (DeckSection) -> Unit = remove@{ section ->
        if (!editor.editable) return@remove
        editor.applyCards(CardListEdits.removeOne(editor.entries ?: return@remove, card.name, section), editor.listCatalog ?: metadata)
    }
    val pulse = remember { Animatable(1f) }
    var shown by remember { mutableStateOf(destination to count) }
    LaunchedEffect(destination, count) {
        if (shown.first == destination && count > shown.second) { pulse.snapTo(1.35f); pulse.animateTo(1f, tween(220)) }
        shown = destination to count
    }
    val reference = remember(card.key, metadata) { ArtworkReference.from(card.printing, metadata?.checksum.orEmpty(), "normal") }
    val saved = destination.saved()
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("search-result-${card.name}")) {
        val width = with(LocalDensity.current) { maxWidth.roundToPx().coerceAtLeast(1) }
        Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.4f)
            .pointerInput(card.key) {
                podPickGestures(onTap = { currentAdd() }, onInspect = { currentHold(it) }, onInspectScroll = { currentHoldScroll(it) })
            }
            .clearAndSetSemantics {
                contentDescription = "${card.name}, $count in ${saved.sectionLabel()}"
                onClick("Add ${card.name} to ${saved.sectionLabel()}") { currentAdd(); true }
            }) {
            CardArtwork(reference, card.name, width, (width * 1.4f).toInt().coerceAtLeast(1), Modifier.fillMaxSize(),
                placeholder = { PodCardPlaceholder(card.name) })
        }
        if (squares.isNotEmpty()) Row(Modifier.align(Alignment.BottomStart).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            squares.forEach { (section, copies) ->
                val label = section.saved().sectionLabel()
                Box(Modifier.size(24.dp).scale(if (section == destination) pulse.value else 1f)
                    .background(section.saved().sectionColor(), RoundedCornerShape(4.dp))
                    .clickable(onClickLabel = "Remove one ${card.name} from $label") { remove(section) }
                    .testTag("search-count-${card.name}-${section.saved().shortLabel().lowercase()}")
                    .semantics { contentDescription = "Remove one ${card.name}, $copies in $label" },
                    contentAlignment = Alignment.Center) {
                    Text("$copies", color = Color.White, fontSize = if (copies < 100) 13.sp else 10.sp, lineHeight = 14.sp, maxLines = 1)
                }
            }
        }
    }
}
