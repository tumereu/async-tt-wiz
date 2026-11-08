package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
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
internal data class PodOpponentPicks(val account: AccountIdentity, val cards: List<DraftCopy?>)

/** Engines with a Picks view (DRF-160): Winston, Winchester and Grid. */
internal fun podHasPicksView(mode: DraftMode) = mode == DraftMode.WINSTON || mode == DraftMode.WINCHESTER || mode == DraftMode.GRID

/** Every other member's picks, in seat order, each newest first (a take's own cards keep their order). */
internal fun podOpponentPicks(detail: PodDetail, accountId: String): List<PodOpponentPicks> {
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
internal fun podPicksPrefetchCards(picks: List<PodOpponentPicks>): List<DraftCopy> = picks.flatMap { it.cards }.filterNotNull()

/** The "Picks" button above a Winston, Winchester or Grid board; it opens [PodPicksDialog]. */
@Composable
internal fun PodPicksButton(detail: PodDetail, accountId: String, definitions: Map<String, CardDefinition>) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
        TextButton({ open = true }, Modifier.testTag("pod-picks")) { ButtonLabel("decks", "Picks") }
    }
    if (open) PodPicksDialog(podOpponentPicks(detail, accountId), definitions) { open = false }
}

/** Each opponent's picks (DRF-160): a chip per opponent, then their cards newest first, unseen ones as backs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PodPicksDialog(picks: List<PodOpponentPicks>, definitions: Map<String, CardDefinition>, dismiss: () -> Unit) {
    var chosen by remember { mutableStateOf(picks.firstOrNull()?.account?.accountId) }
    val preview = remember { PodLimitedPreview() }
    PodArtPrefetch(remember(picks) { podPicksPrefetchCards(picks) }, definitions, preview.tileSize)
    ScreenDialog(dismiss, title = "Picks", close = "Close picks") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            picks.forEach { entry -> FilterChip(chosen == entry.account.accountId, { chosen = entry.account.accountId },
                label = { Text("${entry.account.username} (${entry.cards.size})") },
                leadingIcon = { Avatar(entry.account, size = 20.dp) }) }
        }
        val cards = picks.firstOrNull { it.account.accountId == chosen }?.cards.orEmpty()
        Box(Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 480.dp), contentAlignment = Alignment.TopCenter) {
            if (cards.isEmpty()) Text("No picks yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxWidth().testTag("pod-picks-cards"),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(cards, key = { index, _ -> index }) { index, copy ->
                    if (copy == null) PodCardBack("pod-picks-back-$index", preview)
                    else PodLimitedCard(copy, definitions[copy.definition], null, emptyList(), emptyMap(), false,
                        "pod-picks-${copy.id}", preview) {}
                }
            }
        }
        // Inside the dialog: from outside, its window would lie behind the dialog's.
        PodLimitedPreviewHost(preview, definitions)
    }
}
