package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.asyncttwiz.protocol.*

/** "Pack 1 · Pick 3", "· given automatically" for an automatic award (DRF-165). */
internal fun podHistoryHeader(pick: DraftHistoryPick): String =
    "Pack ${pick.pack} · Pick ${pick.pick}" + if (pick.automatic) " · given automatically" else ""

/** DRF-165 (owner, 2026-11-10): a round's header, "Round 2 · passed right": round 1 passes left, the direction flipping
 * each round (DRF-043; [DraftEngine.direction]: the next seat is the left one). */
internal fun podHistoryRoundHeader(round: Int): String =
    "Round $round · passed " + if (DraftEngine.direction(round - 1) == DraftDirection.NEXT_SEAT) "left" else "right"

/** DRF-165: the picks shown, round by round, in order within each round, each with its place in [picks] (a round's last
 * card given automatically may come after you opened the next round); [returningOnly]: only packs that came back to you a
 * second or later time in their round, a round without any left out. */
internal fun podHistoryRounds(picks: List<DraftHistoryPick>, returningOnly: Boolean): List<Pair<Int, List<IndexedValue<DraftHistoryPick>>>> =
    picks.withIndex().filter { !returningOnly || it.value.returning }.groupBy { it.value.round }.toSortedMap().toList()

/** A history pack's cards sorted like Build columns: W U B R G, multicolour, colourless, then name (DRF-165). */
internal fun podHistoryOrder(cards: List<DraftCopy>, definitions: Map<String, CardDefinition>): List<DraftCopy> =
    podRotisserieOrder(cards.map { LimitedSlot(it) }, definitions).map { it.copy }

/** The history view's largest pack: the pack size setting, 15 for sets whose booster recipe fixes the size (DRF-165). */
internal fun podHistoryPackSize(detail: PodDetail): Int =
    if (detail.source.kind == PodSourceKind.SET && detail.source.boosterKind == SetBoosterKind.RECIPE_BASED) PodPickColumnRange.last
    else detail.settings.cardsPerPack.coerceAtLeast(1)

/** DRF-070: every card the history shows, pick by pick. */
internal fun podHistoryPrefetchCards(history: PodDraftHistory?): List<DraftCopy> =
    history?.picks.orEmpty().flatMap { it.cards + it.takenSince }

/** The icon in the pod's header on every tab but Draft (DRF-165), which has [PodHistoryButton]: the history while building and after. */
@Composable
internal fun PodHistoryHeaderButton(controller: PodController, definitions: Map<String, CardDefinition>, columns: PodBuildColumns) {
    var open by remember { mutableStateOf(false) }
    HoldTip("History") {
        IconButton({ controller.loadHistory(); open = true }, Modifier.size(48.dp).testTag("pod-history")) { EntryIcon("History", "history") }
    }
    if (open) PodHistoryDialog(controller.history, controller.historyFailed, definitions, columns.count,
        controller::loadHistory) { open = false }
}

/** The "History" button above a Booster or Glimpse pick screen (DRF-165); it loads and opens [PodHistoryDialog]. */
@Composable
internal fun PodHistoryButton(controller: PodController, definitions: Map<String, CardDefinition>, columns: PodBuildColumns) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
        TextButton({ controller.loadHistory(); open = true }, Modifier.testTag("pod-history")) { ButtonLabel("history", "History") }
    }
    if (open) PodHistoryDialog(controller.history, controller.historyFailed, definitions, columns.count,
        controller::loadHistory) { open = false }
}

/**
 * Draft history (DRF-165), full screen: every pick of yours in order under "Pack 1 · Pick 3", the pack as it came with
 * keeps green and burns red, and for a pack that came round again the cards taken since, greyed. Holding inspects. Each
 * round has its header ("Round 2 · passed right") and a divider before it; "Returning packs only" shows only packs that
 * came back to you (owner, 2026-11-10).
 */
@Composable
internal fun PodHistoryDialog(history: PodDraftHistory?, failed: Boolean, definitions: Map<String, CardDefinition>,
    columns: Int, retry: () -> Unit, dismiss: () -> Unit) {
    val known = remember(definitions, history) { definitions + history?.definitions.orEmpty().associate { it.digest to it.definition } }
    val preview = remember { PodLimitedPreview() }
    var returningOnly by rememberSaveable { mutableStateOf(false) }
    PodArtPrefetch(remember(history) { podHistoryPrefetchCards(history) }, known, preview.tileSize)
    Dialog(dismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        ScreenTheme {
            Surface(Modifier.fillMaxSize().testTag("pod-history-dialog"), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Draft history", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
                        FilterChip(returningOnly, {
                            returningOnly = !returningOnly
                            AppLog.info("pods", "Draft history: returning packs only ${if (returningOnly) "on" else "off"}")
                        }, { Text("Returning packs only") }, Modifier.testTag("pod-history-returning"),
                            leadingIcon = if (returningOnly) {{ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }} else null)
                        ScreenIconButton("Close draft history", "close", dismiss)
                    }
                    when {
                        history == null && failed -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Couldn't load the draft history", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(retry, Modifier.testTag("pod-history-retry")) { ButtonLabel("refresh", "Retry") }
                        }
                        history == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        history.picks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No picks yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        returningOnly && history.picks.none { it.returning } -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No packs have come back to you yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> PodHistoryPicks(history.picks, returningOnly, known, columns.coerceAtLeast(1), preview)
                    }
                }
            }
            // Inside the dialog: from outside, its window would lie behind the dialog's.
            PodLimitedPreviewHost(preview, known)
        }
    }
}

@Composable
private fun PodHistoryPicks(picks: List<DraftHistoryPick>, returningOnly: Boolean, definitions: Map<String, CardDefinition>,
    columns: Int, preview: PodLimitedPreview) {
    val rounds = remember(picks, returningOnly) { podHistoryRounds(picks, returningOnly) }
    LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize().testTag("pod-history-picks"),
        contentPadding = PaddingValues(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rounds.forEachIndexed { place, (round, shown) ->
            // DRF-165 (owner, 2026-11-10): a header per round, a strong divider between rounds
            item(span = { GridItemSpan(maxLineSpan) }, key = "round-$place-$round") {
                Column {
                    if (place > 0) HorizontalDivider(Modifier.padding(top = 10.dp, bottom = 6.dp), thickness = 3.dp,
                        color = MaterialTheme.colorScheme.outline)
                    Text(podHistoryRoundHeader(round), Modifier.testTag("pod-history-round-$round"),
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            shown.forEachIndexed { inRound, (index, pick) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "header-$index") {
                    Column {
                        if (inRound > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text(podHistoryHeader(pick), Modifier.testTag("pod-history-header-$index"), style = MaterialTheme.typography.labelLarge)
                    }
                }
                items(podHistoryOrder(pick.cards, definitions), key = { "card-$index-${it.id}" }) { copy ->
                    val mark = when (copy.id) { in pick.kept -> PodPickMark.KEEP; in pick.burned -> PodPickMark.BURN; else -> null }
                    PodHistoryCard(copy, definitions[copy.definition], mark, copy.id in pick.librarians, false,
                        "pod-history-$index-${copy.id}", preview)
                }
                if (pick.takenSince.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "since-$index") {
                        Text("Taken since you last saw it", Modifier.padding(top = 4.dp).testTag("pod-history-since-$index"),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(podHistoryOrder(pick.takenSince, definitions), key = { "since-$index-${it.id}" }) { copy ->
                        PodHistoryCard(copy, definitions[copy.definition], null, false, true, "pod-history-$index-${copy.id}", preview)
                    }
                }
            }
        }
    }
}

/** One history card: a keep's green border and ✓, a burn's red border and 🔥, a spent Librarian's book; [gone] greys it. */
@Composable
private fun PodHistoryCard(copy: DraftCopy, definition: CardDefinition?, mark: PodPickMark?, spent: Boolean, gone: Boolean,
    tag: String, preview: PodLimitedPreview) {
    val shape = RoundedCornerShape(4.dp)
    val name = definition?.name ?: "Card data unavailable"
    val state = when { mark == PodPickMark.KEEP -> "kept"; mark == PodPickMark.BURN -> "burned"; gone -> "taken since"; else -> null }
    Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.4f).onSizeChanged { preview.tileSize = it }.testTag(tag)
        .semantics { contentDescription = listOfNotNull(name, state).joinToString(", ") }
        .clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        PodLimitedArt(copy, definition, Modifier.fillMaxSize().alpha(if (gone) .4f else 1f).pointerInput(copy) {
            podPickGestures(onTap = {}, onInspect = { preview.hold(copy, it) }, onInspectScroll = { preview.scroll.dispatchRawDelta(it) })
        })
        if (mark != null) {
            val color = if (mark == PodPickMark.KEEP) PodPickGreen else PodPickRed
            Box(Modifier.matchParentSize().border(3.dp, color, shape)) {
                Text(if (mark == PodPickMark.KEEP) "✓" else "🔥", Modifier.align(Alignment.TopEnd).background(color).padding(3.dp),
                    color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (spent) Icon(Icons.Default.MenuBook, "Spent Librarian", Modifier.align(Alignment.TopStart)
            .background(PodPickGreen, RoundedCornerShape(4.dp)).padding(2.dp).size(14.dp), tint = Color.White)
    }
}
