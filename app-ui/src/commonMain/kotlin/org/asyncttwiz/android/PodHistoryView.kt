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

/**
 * DRF-209 (owner, 2026-11-26): which packs the draft history shows, its packs chip going round All → Returning → New → All:
 * every pack, only the packs that came back to you a second or later time in their round, or each pack only the first
 * time it reached you in its round. [key] is how the device keeps the choice.
 */
sealed class PodHistoryPacks(val label: String, val key: String) {
    data object All : PodHistoryPacks("All packs", "all")
    data object Returning : PodHistoryPacks("Returning", "returning")
    data object New : PodHistoryPacks("New", "new")

    /** Whether [pick]'s pack shows. */
    fun shows(pick: DraftHistoryPick): Boolean = when (this) { All -> true; Returning -> pick.returning; New -> !pick.returning }

    /** The choice the chip's next tap makes. */
    val next: PodHistoryPacks get() = when (this) { All -> Returning; Returning -> New; New -> All }

    companion object {
        /** The choice kept as [key]; All for none (or one this app doesn't know). */
        fun of(key: String?): PodHistoryPacks = listOf(All, Returning, New).firstOrNull { it.key == key } ?: All
    }
}

/** DRF-165: the picks shown, round by round, in order within each round, each with its place in [picks] (a round's last
 * card given automatically may come after you opened the next round); only the packs [packs] shows (DRF-209), a round
 * without any left out. */
fun podHistoryRounds(picks: List<DraftHistoryPick>, packs: PodHistoryPacks): List<Pair<Int, List<IndexedValue<DraftHistoryPick>>>> =
    picks.withIndex().filter { packs.shows(it.value) }.groupBy { it.value.round }.toList().sortedBy { it.first }

/** A history pack's cards sorted like Build columns: W U B R G, multicolour, colourless, then name (DRF-165). */
fun podHistoryOrder(cards: List<DraftCopy>, definitions: Map<String, CardDefinition>): List<DraftCopy> =
    podRotisserieOrder(cards.map { LimitedSlot(it) }, definitions).map { it.copy }

/** The history view's largest pack: the pack size setting, 15 for sets whose booster recipe fixes the size (DRF-165). */
fun podHistoryPackSize(detail: PodDetail): Int = podHistoryPackSize(detail.settings, detail.source)

/** The history view's largest pack for a pod of [settings] and [source] (also a draft kept in Stats, LOB-281). */
fun podHistoryPackSize(settings: DraftSettings, source: PodSource): Int =
    if (settings.mode == DraftMode.ARENA) settings.cardsPerPack.coerceAtLeast(1)
    else if (source.kind == PodSourceKind.SET && source.boosterKind == SetBoosterKind.RECIPE_BASED) PodPickColumnRange.last
    else settings.cardsPerPack.coerceAtLeast(1)

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
    // DRF-315: the pod's card ratings once your own draft is done, in a pod of one set that has them
    val ratings = rememberPodHistoryRatings(detail, controller.filters)
    if (open) PodHistoryDialog(detail.id, controller.history, controller.historyFailed, definitions, columns.count,
        detail.members.map { it.account }, self, controller::loadHistory,
        arena = detail.settings.takeIf { it.mode == DraftMode.ARENA }?.let { it to detail.source.kind }, ratings = ratings) { open = false }
    LaunchedEffect(open, ratings) {
        if (open) AppLog.info("pods", "Draft history of pod ${detail.id} (${detail.settings.mode.name.lowercase()}): " +
            (ratings?.let { "card ratings shown ($it)" } ?: "no card ratings: ${podHistoryNoRatingsReason(detail) ?: "not read yet"}"))
    }
}

/** DRF-316: from this width the draft history's title, "By rating" and the packs chip share one row. */
private val PodHistoryOneRowWidth = 480.dp

/** DRF-209 (owner, 2026-11-26): the packs chip of pod [pod]'s history, remembered on the device (personal UI). */
private fun podHistoryPacksKey(pod: String) = "history-packs-$pod"

/** A drafter's name in the draft history; without one "a player", reported as a stand-in (LOB-090). */
fun podHistoryPlayerName(names: Map<String, String>, id: String): String =
    org.asyncttwiz.automatum.PlayerNames.of(names, id, "a player", "PodHistoryView")

/**
 * Draft history (DRF-165), full screen: every pick of yours in order under "Pack 1 · Pick 3", the pack as it came with
 * keeps green and burns red, and for a pack that came round again the cards taken since, greyed. Holding inspects. Each
 * round has its header naming where its packs went ("Round 2 · passed to Bob") and a divider before it; a ring of the
 * [players]' avatars at the top shows the seating, its arrows the direction of the round at the top of the list;
 * the packs chip shows every pack, only the packs that came back to you or each only the first time, remembered per pod
 * (owner, 2026-11-10, 2026-11-11 and 2026-11-26). [pod] is the pod's id, also for a draft kept in Stats (LOB-281).
 */
@Composable
fun PodHistoryDialog(pod: String, history: PodDraftHistory?, failed: Boolean, definitions: Map<String, CardDefinition>,
    columns: Int, players: List<AccountIdentity>, self: String, retry: () -> Unit,
    /** DRF-289: an Arena pod's settings and source kind: its picks in one list, no seating ring or returning chip. */
    arena: Pair<DraftSettings, PodSourceKind>? = null,
    /** DRF-315: the card ratings to show, null for none (before your own draft is done, or a pod without them). */
    ratings: PodHistoryRatings? = null, dismiss: () -> Unit) {
    val known = remember(definitions, history) { definitions + history?.definitions.orEmpty().associate { it.digest to it.definition } }
    val preview = remember { PodLimitedPreview() }
    val platform = AppPlatform.current
    val preferences = remember(platform) { platform.preferences("pod-pick") }
    var packs by remember(pod) {
        mutableStateOf(if (arena != null) PodHistoryPacks.All else PodHistoryPacks.of(preferences.getString(podHistoryPacksKey(pod), null)))
    }
    val grid = rememberLazyGridState()
    val rounds = remember(history, packs) {
        // DRF-289: Arena's picks are one list, each pick its own offer
        if (arena != null) listOf(1 to history?.picks.orEmpty().withIndex().toList()).filter { it.second.isNotEmpty() }
        else podHistoryRounds(history?.picks.orEmpty(), packs)
    }
    // DRF-316: best first by rating, remembered on the device; only while ratings show
    val byRatingChoice = rememberPodByRating()
    val byRating = ratings?.takeIf { byRatingChoice.value }
    // DRF-317: the citation is the list's first item, in the first round
    val itemRounds = remember(rounds, ratings != null) {
        (if (ratings != null) listOfNotNull(rounds.firstOrNull()?.first) else emptyList()) + podHistoryItemRounds(rounds)
    }
    // The round at the top of the list; scrolled to its end, the last round (which may never reach the top)
    val shownRound by remember(itemRounds) { derivedStateOf {
        (if (grid.canScrollBackward && !grid.canScrollForward) itemRounds.lastOrNull() else itemRounds.getOrNull(grid.firstVisibleItemIndex))
            ?: rounds.firstOrNull()?.first ?: 1
    } }
    val names = remember(players) { players.associate { it.accountId to it.username } }
    val name = { id: String -> podHistoryPlayerName(names, id) }
    val seating = history?.seating.orEmpty()
    PodArtPrefetch(remember(history) { podHistoryPrefetchCards(history) }, known, preview.tileSize)
    Dialog(dismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        ScreenTheme {
            Surface(Modifier.fillMaxSize().testTag("pod-history-dialog"), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    val returningChip: @Composable () -> Unit = {
                        // DRF-209: each tap the next choice, All → Returning → New → All
                        FilterChip(packs != PodHistoryPacks.All, {
                            val was = packs
                            packs = packs.next
                            // Remembered per pod on this device; All is the default, so only another choice is kept
                            preferences.edit().apply { if (packs == PodHistoryPacks.All) remove(podHistoryPacksKey(pod))
                                else putString(podHistoryPacksKey(pod), packs.key) }.apply()
                            val picks = history?.picks.orEmpty()
                            AppLog.info("pods", "Draft history of pod $pod: packs ${was.key} -> ${packs.key}, " +
                                "${picks.count(packs::shows)} of ${picks.size} picks shown")
                        }, { Text(packs.label) }, Modifier.testTag("pod-history-returning"),
                            leadingIcon = if (packs != PodHistoryPacks.All) {{ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }} else null)
                    }
                    val byRatingChip: @Composable () -> Unit = { PodByRatingChip(byRatingChoice, "Draft history of pod $pod") }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        // DRF-316: "By rating" beside the packs chip; where both don't fit beside the title, the two
                        // chips take a row of their own under it, at its end
                        val chipsBelow = ratings != null && arena == null && maxWidth < PodHistoryOneRowWidth
                        Column {
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Draft history", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
                                if (!chipsBelow) {
                                    if (ratings != null) byRatingChip()
                                    if (ratings != null && arena == null) Spacer(Modifier.width(8.dp))
                                    if (arena == null) returningChip()
                                }
                                ScreenIconButton("Close draft history", "close", dismiss)
                            }
                            if (chipsBelow) Row(Modifier.fillMaxWidth().padding(end = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                                byRatingChip()
                                returningChip()
                            }
                        }
                    }
                    // DRF-165, DRF-043: the seating, with the direction of the round at the top of the list
                    if (arena == null && history != null && podHistoryPassedTo(seating, self, shownRound) != null) {
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
                        packs == PodHistoryPacks.Returning && history.picks.none { it.returning } -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No packs have come back to you yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> PodHistoryPicks(rounds, grid, known, columns.coerceAtLeast(1), preview, ratings, byRating,
                            pickHeader = { pick -> arena?.let { (settings, kind) -> podArenaHistoryHeader(settings, pick.round, kind) } ?: podHistoryHeader(pick) },
                            roundHeader = { round -> if (arena != null) null else podHistoryRoundHeader(round, seating, self, name) })
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
    definitions: Map<String, CardDefinition>, columns: Int, preview: PodLimitedPreview, ratings: PodHistoryRatings?,
    byRating: PodHistoryRatings?, pickHeader: (DraftHistoryPick) -> String, roundHeader: (Int) -> String?) {
    // The items, in order, are the ones podHistoryItemRounds counts: the seat ring follows the round at the top
    LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize().testTag("pod-history-picks"), state = grid,
        contentPadding = PaddingValues(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        // DRF-317 (owner, 2026-11-25): 17Lands asks for a citation in sight wherever its numbers show
        if (ratings != null) item(span = { GridItemSpan(maxLineSpan) }, key = "ratings-citation") {
            PodRatingsCitation(Modifier.padding(start = 2.dp, bottom = 2.dp))
        }
        rounds.forEachIndexed { place, (round, shown) ->
            // DRF-165 (owner, 2026-11-10): a header per round, a strong divider between rounds
            roundHeader(round)?.let { header ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "round-$place-$round") {
                    Column {
                        if (place > 0) HorizontalDivider(Modifier.padding(top = 10.dp, bottom = 6.dp), thickness = 3.dp,
                            color = MaterialTheme.colorScheme.outline)
                        Text(header, Modifier.testTag("pod-history-round-$round"),
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            shown.forEachIndexed { inRound, (index, pick) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "header-$index") {
                    Column {
                        if (inRound > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text(pickHeader(pick), Modifier.testTag("pod-history-header-$index"), style = MaterialTheme.typography.labelLarge)
                    }
                }
                // DRF-316: best first by rating, the keeps and burns marked wherever they land
                items(podHistoryRatingOrder(podHistoryOrder(pick.cards, definitions), definitions, byRating),
                    key = { "card-$index-${it.id}" }) { copy ->
                    // DRF-207 (owner, 2026-11-22): the cards thrown away after the pick (DRF-045, DRF-046) show as burns
                    val mark = when (copy.id) {
                        in pick.kept -> PodPickMark.KEEP
                        in pick.burned, in pick.discarded -> PodPickMark.BURN
                        else -> null
                    }
                    PodHistoryCard(copy, definitions[copy.definition], mark, copy.id in pick.librarians, false,
                        "pod-history-$index-${copy.id}", preview, ratings)
                }
                if (pick.takenSince.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "since-$index") {
                        Text("Taken since you last saw it", Modifier.padding(top = 4.dp).testTag("pod-history-since-$index"),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(podHistoryRatingOrder(podHistoryOrder(pick.takenSince, definitions), definitions, byRating),
                        key = { "since-$index-${it.id}" }) { copy ->
                        PodHistoryCard(copy, definitions[copy.definition], null, false, true, "pod-history-$index-${copy.id}", preview, ratings)
                    }
                }
            }
        }
    }
}

/** One history card: a keep's green border and ✓, a burn's red border and 🔥, a spent Librarian's book; [gone] greys it. */
@Composable
private fun PodHistoryCard(copy: DraftCopy, definition: CardDefinition?, mark: PodPickMark?, spent: Boolean, gone: Boolean,
    tag: String, preview: PodLimitedPreview, ratings: PodHistoryRatings? = null) {
    val shape = RoundedCornerShape(4.dp)
    val name = definition?.shownName ?: cardDataUnavailable("PodHistoryView", copy.definition)
    val state = when { mark == PodPickMark.KEEP -> "kept"; mark == PodPickMark.BURN -> "burned"; gone -> "taken since"; else -> null }
    val rating = ratings?.rating(definition)
    Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.4f).onSizeChanged { preview.tileSize = it }.testTag(tag)
        .semantics { contentDescription = listOfNotNull(name, state, rating?.let { "rated ${podRatingText(it)}" }).joinToString(", ") }
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
        // DRF-315: a rated card's GIH WR on its bottom edge, coloured by its step in its set
        if (rating != null && ratings != null) PodRatingChip(rating, ratings.step(rating), "$tag-rating", Modifier.align(Alignment.BottomCenter))
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
