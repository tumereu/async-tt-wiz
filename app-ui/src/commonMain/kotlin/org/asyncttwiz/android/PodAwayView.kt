package org.asyncttwiz.android

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** One pick of "While you were away" (DRF-239): its number in the whole draft (1-based), who took which card, and whether
 * it was the viewer's own pick taken from their queue. */
data class PodAwayPick(val number: Int, val player: String, val card: DraftCopy, val fromQueue: Boolean = false)

/**
 * What happened in a Rotisserie draft since the viewer last looked (DRF-239): the picks since then, oldest first, the pick
 * that cleared the viewer's queue (DRF-218) and their queue's last card when it ran out (both null when neither happened).
 */
data class PodRotisserieAway(val picks: List<PodAwayPick>, val clearedBy: PodAwayPick? = null, val ranOutAt: PodAwayPick? = null)

/**
 * DRF-239: the picks of [view] after the first [seen], or null when there is nothing to tell: not Rotisserie, nothing new,
 * or only picks the viewer made by hand (on another device, where they saw them).
 */
fun podRotisserieAway(view: LimitedView, seen: Int): PodRotisserieAway? {
    if (view.mode != DraftMode.ROTISSERIE) return null
    val unseen = view.picks.withIndex().drop(seen.coerceAtLeast(0)).mapNotNull { (index, take) ->
        take.cards.singleOrNull()?.let { take to PodAwayPick(index + 1, take.player, it, take.fromQueue) }
    }
    if (unseen.none { (_, pick) -> pick.player != view.player || pick.fromQueue }) return null
    return PodRotisserieAway(unseen.map { it.second }, unseen.lastOrNull { it.first.clearedQueue }?.second,
        unseen.lastOrNull { it.first.queueRanOut }?.second)
}

private fun podAwayCardName(card: DraftCopy, definitions: Map<String, CardDefinition>): String =
    definitions[card.definition]?.name ?: cardDataUnavailable("PodAwayView", card.definition)

private fun podAwayName(player: String, me: String, members: List<AccountIdentity>): String =
    if (player == me) "You" else members.firstOrNull { it.accountId == player }?.username ?: "Another player"

/** The lines about the viewer's queue (DRF-239): "Your queue was cleared: Bob took Lightning Bolt", "Your queue ran out:
 * Shock was its last card". */
fun podAwayQueueTexts(away: PodRotisserieAway, me: String, members: List<AccountIdentity>,
    definitions: Map<String, CardDefinition>): List<String> = listOfNotNull(
    away.clearedBy?.let { "Your queue was cleared: ${podAwayName(it.player, me, members)} took ${podAwayCardName(it.card, definitions)}" },
    away.ranOutAt?.let { "Your queue ran out: ${podAwayCardName(it.card, definitions)} was its last card" })

/** "3 picks since you last looked" (DRF-239). */
fun podAwayCountText(count: Int) = "$count ${if (count == 1) "pick" else "picks"} since you last looked"

/** One pick's line (DRF-239): "Pick 12 · Bob · Lightning Bolt", your own from the queue "Pick 13 · You · Shock · from your queue". */
fun podAwayPickText(pick: PodAwayPick, me: String, members: List<AccountIdentity>, definitions: Map<String, CardDefinition>): String =
    listOfNotNull("Pick ${pick.number}", podAwayName(pick.player, me, members), podAwayCardName(pick.card, definitions),
        "from your queue".takeIf { pick.fromQueue }).joinToString(" · ")

/**
 * "While you were away" (DRF-239): what happened to your queue, then every pick since you last looked, oldest first, each
 * with its card (holding it inspects), the picker's avatar and name. OK or the close button dismisses it; the History
 * icon's picks (DRF-160) keep the whole draft.
 */
@Composable
fun PodAwayDialog(away: PodRotisserieAway, me: String, members: List<AccountIdentity>, definitions: Map<String, CardDefinition>,
    dismiss: () -> Unit) {
    val preview = remember { PodLimitedPreview() }
    PodArtPrefetch(remember(away) { away.picks.map { it.card } }, definitions, preview.tileSize)
    ScreenDialog(dismiss, Modifier.testTag("pod-away"), title = "While you were away", close = "Close", actions = {
        DialogMainButton("OK", dismiss, Modifier.testTag("pod-away-ok"))
    }) {
        podAwayQueueTexts(away, me, members, definitions).forEachIndexed { index, text ->
            Text(text, Modifier.testTag("pod-away-queue-$index"), color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium)
        }
        Text(podAwayCountText(away.picks.size), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        away.picks.forEach { pick -> PodAwayPickRow(pick, me, members, definitions, preview) }
        // Inside the dialog: from outside, its window would lie behind the dialog's.
        PodLimitedPreviewHost(preview, definitions)
    }
}

@Composable
private fun PodAwayPickRow(pick: PodAwayPick, me: String, members: List<AccountIdentity>, definitions: Map<String, CardDefinition>,
    preview: PodLimitedPreview) {
    val text = podAwayPickText(pick, me, members, definitions)
    Row(Modifier.fillMaxWidth().testTag("pod-away-pick-${pick.number}").semantics { contentDescription = text },
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val shape = MaterialTheme.shapes.extraSmall
        Box(Modifier.width(36.dp).aspectRatio(1f / 1.4f).onSizeChanged { preview.tileSize = it }.testTag("pod-away-card-${pick.card.id}").clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .pointerInput(pick.card.id) {
                podPickGestures(onTap = {}, onInspect = { preview.hold(pick.card, it) }, onInspectScroll = { preview.scroll.dispatchRawDelta(it) })
            }) {
            PodLimitedArt(pick.card, definitions[pick.card.definition], Modifier.fillMaxSize())
        }
        members.firstOrNull { it.accountId == pick.player }?.let { Avatar(it, size = 20.dp) }
        Column(Modifier.weight(1f)) {
            Text(podAwayCardName(pick.card, definitions), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(listOfNotNull("Pick ${pick.number}", podAwayName(pick.player, me, members), "from your queue".takeIf { pick.fromQueue })
                .joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
