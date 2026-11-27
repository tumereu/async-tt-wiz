package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** LOB-280: the kept drafts [opponent] (an account id; null: All) was in, in their order. */
fun statsDrafts(drafts: List<DraftRecordSummary>, opponent: String?): List<DraftRecordSummary> =
    if (opponent == null) drafts else drafts.filter { draft -> draft.players.any { it.accountId == opponent } }

/** LOB-280: a kept draft's second line, its set's name and the date its drafter's own draft was done, "Test Set · 26 Nov 2026". */
fun statsDraftLine(draft: DraftRecordSummary): String =
    listOfNotNull(draft.source.setName ?: draft.source.setCode, draftDate(draft).takeIf { it.isNotEmpty() }).joinToString(" · ")

/**
 * LOB-280 (owner, 2026-11-26): Stats' Drafts tab: the drafts the server keeps for you (LOB-279), newest first, [opponent]'s
 * only with one chosen; a tap opens one's draft history (LOB-281) with card ratings read through [filters].
 */
@Composable
fun StatsDraftsTab(history: HistoryController, me: String, opponent: String?, filters: PodCardFilters?, modifier: Modifier) {
    LaunchedEffect(Unit) { history.loadDrafts() }
    val list = history.drafts
    var open by remember { mutableStateOf<DraftRecordSummary?>(null) }
    val all = list.value
    if (all == null) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (list.failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Your drafts couldn't be loaded.")
                TextButton(history::loadDrafts, Modifier.testTag("stats-drafts-retry")) { ButtonLabel("refresh", "Retry") }
            } else CircularProgressIndicator()
        }
        return
    }
    val drafts = remember(all, opponent) { statsDrafts(all, opponent) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp).testTag("stats-drafts"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (drafts.isEmpty()) Text("No drafts yet. Drafts with card ratings show here once your own draft is done.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (draft in drafts) StatsDraftRow(draft) {
            AppLog.info("history", "Kept draft ${draft.id.take(8)} opened: pod ${draft.podId.take(8)} '${draft.podName}'")
            open = draft
            if (history.draftRecords[draft.id]?.value == null) history.loadDraft(draft.id)
        }
    }
    open?.let { draft -> StatsDraftHistory(draft, history.draftRecords[draft.id], me, filters, { history.loadDraft(draft.id) }) { open = null } }
}

/** LOB-280: one kept draft: the pod's name, then its set and date. */
@Composable
private fun StatsDraftRow(draft: DraftRecordSummary, open: () -> Unit) {
    OutlinedCard(onClick = open, modifier = Modifier.fillMaxWidth().testTag("stats-draft-${draft.id}")) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(draft.podName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(statsDraftLine(draft), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * LOB-281: kept draft [draft]'s draft history, the pod's own dialog (DRF-165, Arena's DRF-289) on what the server kept
 * ([record], read on opening): its seating and players, and its card ratings (DRF-315: the draft was kept for having them).
 */
@Composable
fun StatsDraftHistory(draft: DraftRecordSummary, record: HistoryList<DraftRecord>?, me: String, filters: PodCardFilters?,
    retry: () -> Unit, dismiss: () -> Unit) {
    val set = podBotRatingsSet(draft.source)
    LaunchedEffect(set, filters) { if (set != null) filters?.loadRatings(set) }
    val table = set?.let { filters?.ratings?.get(it) }
    val ratings = remember(table) { table?.let(::PodHistoryRatings) }
    LaunchedEffect(draft.id, ratings) {
        AppLog.info("history", "Kept draft ${draft.id.take(8)} history: " + when {
            set == null -> "no card ratings: its pod isn't of one set with them"
            filters == null -> "no card ratings: no card records here"
            ratings == null -> "card ratings of $set not read yet"
            else -> "card ratings shown ($ratings)"
        })
    }
    val columns = rememberPodHistoryColumns(podHistoryPackSize(draft.settings, draft.source))
    PodHistoryDialog(draft.podId, record?.value?.history, record?.failed == true, emptyMap(), columns.count, draft.players, me, retry,
        arena = draft.settings.takeIf { it.mode == DraftMode.ARENA }?.let { it to draft.source.kind }, ratings = ratings, dismiss = dismiss)
}
