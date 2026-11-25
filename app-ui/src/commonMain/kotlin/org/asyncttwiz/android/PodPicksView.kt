package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** One opponent's picks, newest first; a card the viewer never saw is null (a card back). */
data class PodOpponentPicks(val account: AccountIdentity, val cards: List<DraftCopy?>)

/** Engines whose History icon opens the Picks view (DRF-160): Winston, Winchester, Grid, Rochester and Rotisserie. */
fun podHasPicksView(mode: DraftMode) = mode == DraftMode.WINSTON || mode == DraftMode.WINCHESTER || mode == DraftMode.GRID ||
    mode == DraftMode.ROCHESTER || mode == DraftMode.ROTISSERIE

/** DRF-165, DRF-160 (owner, 2026-11-19): whether the pod's header has the History icon: every draft mode (Sealed has no picks). */
fun podHasHistoryIcon(mode: DraftMode) = mode.draftState || podHasPicksView(mode)

/** Every other member's picks, in seat order, each newest first (a take's own cards keep their order). */
fun podOpponentPicks(detail: PodDetail, accountId: String): List<PodOpponentPicks> {
    val takes: List<Pair<String, List<DraftCopy?>>> = detail.limited?.picks?.map { it.player to it.cards }
        ?: detail.grid?.publicView?.log?.filter { it.line != null }?.map { it.player to it.kept }
        ?: emptyList()
    val seating = detail.limited?.seating ?: detail.grid?.publicView?.seating ?: detail.members.map { it.account.accountId }
    return seating.filter { it != accountId }.mapNotNull { id ->
        val account = detail.members.firstOrNull { it.account.accountId == id }?.account ?: return@mapNotNull null
        PodOpponentPicks(account, takes.filter { it.first == id }.asReversed().flatMap { it.second })
    }
}

/** DRF-070: the Picks view's face-up cards, opponent by opponent as the chips list them; never a card back. */
fun podPicksPrefetchCards(picks: List<PodOpponentPicks>): List<DraftCopy> = picks.flatMap { it.cards }.filterNotNull()

/** The History icon in the pod's header of a Winston, Winchester, Grid, Rochester or Rotisserie pod, where Booster's and
 * Glimpse's is (DRF-165): it opens [PodPicksDialog] (DRF-160), with the card ratings once your own draft is done (DRF-315). */
@Composable
fun PodPicksHeaderButton(detail: PodDetail, accountId: String, definitions: Map<String, CardDefinition>, filters: PodCardFilters) {
    var open by remember { mutableStateOf(false) }
    HoldTip("History") {
        IconButton({ open = true }, Modifier.size(48.dp).testTag("pod-picks")) { EntryIcon("History", "history") }
    }
    val ratings = rememberPodHistoryRatings(detail, filters)
    if (open) PodPicksDialog(podOpponentPicks(detail, accountId), definitions, ratings) { open = false }
    LaunchedEffect(open, ratings) {
        if (open) AppLog.info("pods", "Picks of pod ${detail.id} (${detail.settings.mode.name.lowercase()}): " +
            (ratings?.let { "card ratings shown ($it)" } ?: "no card ratings: ${podHistoryNoRatingsReason(detail) ?: "not read yet"}"))
    }
}

/** Each opponent's picks (DRF-160): a chip per opponent, then their cards newest first, unseen ones as backs. With card
 * [ratings] (DRF-315) each face-up rated card shows its rating, the citation heads the cards (DRF-317) and "By rating" in
 * the title row sorts them best first (DRF-316). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PodPicksDialog(picks: List<PodOpponentPicks>, definitions: Map<String, CardDefinition>, ratings: PodHistoryRatings? = null,
    dismiss: () -> Unit) {
    var chosen by remember { mutableStateOf(picks.firstOrNull()?.account?.accountId) }
    val preview = remember { PodLimitedPreview() }
    val byRatingChoice = rememberPodByRating()
    PodArtPrefetch(remember(picks) { podPicksPrefetchCards(picks) }, definitions, preview.tileSize)
    ScreenDialog(dismiss, title = "Picks", close = "Close picks",
        titleActions = if (ratings == null) null else {{ PodByRatingChip(byRatingChoice, "Picks") }}) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            picks.forEach { entry -> FilterChip(chosen == entry.account.accountId, { chosen = entry.account.accountId },
                label = { Text("${entry.account.username} (${entry.cards.size})") },
                leadingIcon = { Avatar(entry.account, size = 20.dp) }) }
        }
        val shown = picks.firstOrNull { it.account.accountId == chosen }?.cards.orEmpty()
        // DRF-316: best first by rating; card backs and unrated cards after, in their order
        val cards = podPicksRatingOrder(shown, definitions, ratings?.takeIf { byRatingChoice.value })
        Box(Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 480.dp), contentAlignment = Alignment.TopCenter) {
            if (cards.isEmpty()) Text("No picks yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxWidth().testTag("pod-picks-cards"),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // DRF-317 (owner, 2026-11-25): 17Lands asks for a citation in sight wherever its numbers show
                if (ratings != null) item(span = { GridItemSpan(maxLineSpan) }, key = "ratings-citation") { PodRatingsCitation() }
                itemsIndexed(cards, key = { index, copy -> copy?.id ?: "back-$index" }) { index, copy ->
                    if (copy == null) PodCardBack("pod-picks-back-$index", preview)
                    else Box {
                        PodLimitedCard(copy, definitions[copy.definition], null, emptyList(), emptyMap(), false,
                            "pod-picks-${copy.id}", preview) {}
                        // DRF-315: only cards shown face up get a rating; card backs none
                        ratings?.rating(definitions[copy.definition])?.let { rating ->
                            PodRatingChip(rating, ratings.step(rating), "pod-picks-${copy.id}-rating", Modifier.align(Alignment.BottomCenter))
                        }
                    }
                }
            }
        }
        // Inside the dialog: from outside, its window would lie behind the dialog's.
        PodLimitedPreviewHost(preview, definitions)
    }
}
