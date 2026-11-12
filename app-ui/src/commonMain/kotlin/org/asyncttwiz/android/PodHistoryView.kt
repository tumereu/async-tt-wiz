package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import org.asyncttwiz.appui.AppPlatform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun podHistoryHeader(pick: DraftHistoryPick): String =
    "Pack ${pick.pack} · Pick ${pick.pick}" + if (pick.automatic) " · given automatically" else ""

/** Who [self]'s packs of round [round] (one-based) passed to (DRF-043: [DraftEngine.direction], to the next seat of
 * [seating] in round 1, the direction flipping each round); null when [self] isn't seated. */
fun podHistoryPassedTo(seating: List<String>, self: String, round: Int): String? {
    val seat = seating.indexOf(self)
    if (seat < 0 || seating.size < 2) return null
    val step = if (DraftEngine.direction(round - 1) == DraftDirection.NEXT_SEAT) 1 else seating.size - 1
    return seating[(seat + step) % seating.size]
}

/** A round's direction around the seat ring, which draws the seats clockwise in seat order: to the next seat is clockwise. */
fun podHistoryClockwise(round: Int): Boolean = DraftEngine.direction(round - 1) == DraftDirection.NEXT_SEAT

/** DRF-165 (owner, 2026-11-11): a round's header naming where its packs went, "Round 1 · passed to Bob", with
 * "· clockwise" or "· counter-clockwise" only with three or more players; "Round 1" alone when the seating isn't known. */
fun podHistoryRoundHeader(round: Int, seating: List<String>, self: String, name: (String) -> String): String {
    val to = podHistoryPassedTo(seating, self, round) ?: return "Round $round"
    val way = if (seating.size < 3) "" else if (podHistoryClockwise(round)) " · clockwise" else " · counter-clockwise"
    return "Round $round · passed to ${name(to)}$way"
}

/** The round each item of the history list belongs to, in the list's order (a round's header, then each pick's header,
 * its cards, and the "Taken since" line and cards): which round the seat ring shows while the list scrolls (DRF-165). */
fun podHistoryItemRounds(rounds: List<Pair<Int, List<IndexedValue<DraftHistoryPick>>>>): List<Int> = buildList {
    for ((round, shown) in rounds) {
        add(round)
        for ((_, pick) in shown) {
            repeat(1 + pick.cards.size + if (pick.takenSince.isEmpty()) 0 else 1 + pick.takenSince.size) { add(round) }
        }
    }
}

/** DRF-165: the picks shown, round by round, in order within each round, each with its place in [picks] (a round's last
 * card given automatically may come after you opened the next round); [returningOnly]: only packs that came back to you a
 * second or later time in their round, a round without any left out. */
fun podHistoryRounds(picks: List<DraftHistoryPick>, returningOnly: Boolean): List<Pair<Int, List<IndexedValue<DraftHistoryPick>>>> =
    picks.withIndex().filter { !returningOnly || it.value.returning }.groupBy { it.value.round }.toList().sortedBy { it.first }

/** A history pack's cards sorted like Build columns: W U B R G, multicolour, colourless, then name (DRF-165). */
fun podHistoryOrder(cards: List<DraftCopy>, definitions: Map<String, CardDefinition>): List<DraftCopy> =
    podRotisserieOrder(cards.map { LimitedSlot(it) }, definitions).map { it.copy }

/** The history view's largest pack: the pack size setting, 15 for sets whose booster recipe fixes the size (DRF-165). */
fun podHistoryPackSize(detail: PodDetail): Int =
    if (detail.source.kind == PodSourceKind.SET && detail.source.boosterKind == SetBoosterKind.RECIPE_BASED) PodPickColumnRange.last
    else detail.settings.cardsPerPack.coerceAtLeast(1)

/** DRF-070: every card the history shows, pick by pick. */
fun podHistoryPrefetchCards(history: PodDraftHistory?): List<DraftCopy> =
    history?.picks.orEmpty().flatMap { it.cards + it.takenSince }

/** The History icon in the pod's header on every tab, the Draft tab included (DRF-165): the history during the draft, while building and after. */
@Composable
fun PodHistoryHeaderButton(controller: PodController, detail: PodDetail, self: String, definitions: Map<String, CardDefinition>,
    columns: PodBuildColumns) {
    var open by remember { mutableStateOf(false) }
    HoldTip("History") {
        IconButton({ controller.loadHistory(); open = true }, Modifier.size(48.dp).testTag("pod-history")) { EntryIcon("History", "history") }
    }
    if (open) PodHistoryDialog(detail.id, controller.history, controller.historyFailed, definitions, columns.count,
        detail.members.map { it.account }, self, controller::loadHistory) { open = false }
}

/** DRF-165 (owner, 2026-11-11): the "Returning packs only" chip of pod [pod]'s history, remembered on the device (personal UI). */
private fun podHistoryReturningKey(pod: String) = "history-returning-$pod"

/**
 * Draft history (DRF-165), full screen: every pick of yours in order under "Pack 1 · Pick 3", the pack as it came with
 * keeps green and burns red, and for a pack that came round again the cards taken since, greyed. Holding inspects. Each
 * round has its header naming where its packs went ("Round 2 · passed to Bob") and a divider before it; a ring of the
 * [players]' avatars at the top shows the seating, its arrows the direction of the round at the top of the list;
 * "Returning packs only" shows only packs that came back to you, remembered per pod (owner, 2026-11-10 and 2026-11-11).
 */
@Composable
fun PodHistoryDialog(pod: String, history: PodDraftHistory?, failed: Boolean, definitions: Map<String, CardDefinition>,
    columns: Int, players: List<AccountIdentity>, self: String, retry: () -> Unit, dismiss: () -> Unit) {
    val known = remember(definitions, history) { definitions + history?.definitions.orEmpty().associate { it.digest to it.definition } }
    val preview = remember { PodLimitedPreview() }
    val platform = AppPlatform.current
    val preferences = remember(platform) { platform.preferences("pod-pick") }
    var returningOnly by remember(pod) { mutableStateOf(preferences.getBoolean(podHistoryReturningKey(pod), false)) }
    val grid = rememberLazyGridState()
    val rounds = remember(history, returningOnly) { podHistoryRounds(history?.picks.orEmpty(), returningOnly) }
    val itemRounds = remember(rounds) { podHistoryItemRounds(rounds) }
    // The round at the top of the list; scrolled to its end, the last round (which may never reach the top)
    val shownRound by remember(itemRounds) { derivedStateOf {
        (if (grid.canScrollBackward && !grid.canScrollForward) itemRounds.lastOrNull() else itemRounds.getOrNull(grid.firstVisibleItemIndex))
            ?: rounds.firstOrNull()?.first ?: 1
    } }
    val names = remember(players) { players.associate { it.accountId to it.username } }
    val name = { id: String -> names[id] ?: "a player" }
    val seating = history?.seating.orEmpty()
    PodArtPrefetch(remember(history) { podHistoryPrefetchCards(history) }, known, preview.tileSize)
    Dialog(dismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        ScreenTheme {
            Surface(Modifier.fillMaxSize().testTag("pod-history-dialog"), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Draft history", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
                        FilterChip(returningOnly, {
                            returningOnly = !returningOnly
                            // Remembered per pod on this device; off is the default, so only an "on" is kept
                            preferences.edit().apply { if (returningOnly) putBoolean(podHistoryReturningKey(pod), true)
                                else remove(podHistoryReturningKey(pod)) }.apply()
                            AppLog.info("pods", "Draft history of pod $pod: returning packs only ${if (returningOnly) "on" else "off"}")
                        }, { Text("Returning packs only") }, Modifier.testTag("pod-history-returning"),
                            leadingIcon = if (returningOnly) {{ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }} else null)
                        ScreenIconButton("Close draft history", "close", dismiss)
                    }
                    // DRF-165, DRF-043: the seating, with the direction of the round at the top of the list
                    if (history != null && podHistoryPassedTo(seating, self, shownRound) != null) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            PodSeatRing(seating.map { id -> players.firstOrNull { it.accountId == id } ?: AccountIdentity(id, name(id)) },
                                self, podHistoryClockwise(shownRound), shownRound)
                            Text(podHistoryRoundHeader(shownRound, seating, self, name), Modifier.weight(1f).testTag("pod-history-seat-round"),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
                        else -> PodHistoryPicks(rounds, grid, known, columns.coerceAtLeast(1), preview) { round ->
                            podHistoryRoundHeader(round, seating, self, name)
                        }
                    }
                }
            }
            // Inside the dialog: from outside, its window would lie behind the dialog's.
            PodLimitedPreviewHost(preview, known)
        }
    }
}

@Composable
private fun PodHistoryPicks(rounds: List<Pair<Int, List<IndexedValue<DraftHistoryPick>>>>, grid: LazyGridState,
    definitions: Map<String, CardDefinition>, columns: Int, preview: PodLimitedPreview, roundHeader: (Int) -> String) {
    // The items, in order, are the ones podHistoryItemRounds counts: the seat ring follows the round at the top
    LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize().testTag("pod-history-picks"), state = grid,
        contentPadding = PaddingValues(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rounds.forEachIndexed { place, (round, shown) ->
            // DRF-165 (owner, 2026-11-10): a header per round, a strong divider between rounds
            item(span = { GridItemSpan(maxLineSpan) }, key = "round-$place-$round") {
                Column {
                    if (place > 0) HorizontalDivider(Modifier.padding(top = 10.dp, bottom = 6.dp), thickness = 3.dp,
                        color = MaterialTheme.colorScheme.outline)
                    Text(roundHeader(round), Modifier.testTag("pod-history-round-$round"),
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

/**
 * DRF-165, DRF-043 (owner, 2026-11-11): the draft's [seats] as a ring of avatars, [self] at the bottom and the others
 * clockwise in seat order, on a thin circle whose arrowheads, one between each two neighbours, show which way round
 * [round]'s packs pass ([clockwise]: to the next seat).
 */
@Composable
fun PodSeatRing(seats: List<AccountIdentity>, self: String, clockwise: Boolean, round: Int, modifier: Modifier = Modifier) {
    val avatar = 24.dp
    val radius: Dp = if (seats.size <= 4) 28.dp else 40.dp
    val start = seats.indexOfFirst { it.accountId == self }.coerceAtLeast(0)
    val color = MaterialTheme.colorScheme.primary
    val way = if (clockwise) "clockwise" else "counter-clockwise"
    Box(modifier.size(radius * 2 + avatar).testTag("pod-history-seats").semantics {
        contentDescription = "Seating: " + List(seats.size) { seats[(start + it) % seats.size].username }.joinToString(", ") +
            "; round $round passes $way"
    }) {
        // Seat k (counted from you) sits at angle 90° + k·360°/n: you at the bottom, the next seats clockwise on screen (y down)
        fun angle(k: Float) = PI / 2 + k * 2 * PI / seats.size
        Canvas(Modifier.matchParentSize().testTag(if (clockwise) "pod-history-seats-clockwise" else "pod-history-seats-counter-clockwise")) {
            val center = Offset(size.width / 2, size.height / 2)
            val r = radius.toPx()
            drawCircle(color.copy(alpha = .5f), r, center, style = Stroke(1.5.dp.toPx()))
            val head = 6.dp.toPx()
            // The tangent the packs move along: an increasing angle is clockwise on screen
            val sign = if (clockwise) 1f else -1f
            for (k in seats.indices) {
                val a = angle(k + .5f)
                val normal = Offset(cos(a).toFloat(), sin(a).toFloat())
                val at = center + normal * r
                val tangent = Offset(-normal.y * sign, normal.x * sign)
                val tip = at + tangent * head
                val back = at - tangent * (head * .6f)
                drawPath(Path().apply {
                    moveTo(tip.x, tip.y)
                    (back + normal * (head * .7f)).let { lineTo(it.x, it.y) }
                    (back - normal * (head * .7f)).let { lineTo(it.x, it.y) }
                    close()
                }, color)
            }
        }
        seats.forEachIndexed { index, seat ->
            val k = ((index - start) % seats.size + seats.size) % seats.size
            val a = angle(k.toFloat())
            Avatar(seat, Modifier.offset(radius * cos(a).toFloat() + radius, radius * sin(a).toFloat() + radius)
                .let { if (seat.accountId == self) it.border(2.dp, color, CircleShape) else it }, avatar)
        }
    }
}
