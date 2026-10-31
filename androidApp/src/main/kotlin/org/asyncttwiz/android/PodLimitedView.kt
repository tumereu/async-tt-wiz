package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.asyncttwiz.protocol.*

/** A limited engine's move, sent by [PodController.turn]. */
internal typealias PodTurnSender = (action: LimitedAction, turn: Int, card: String?, packId: String?, pile: Int?) -> Unit

/** Who the pod waits for (DRF-071): "Waiting for <names>", Rochester with its round. */
internal fun podLimitedWaitingText(detail: PodDetail, view: LimitedView, accountId: String): String {
    val busy = detail.members.filter { it.packsWaiting > 0 }.map { it.account.accountId }.toSet()
    val names = detail.members.map { it.account }.filter { it.accountId != accountId && it.accountId in busy }.map { it.username }
    val who = if (names.isEmpty()) "Waiting for other players" else "Waiting for ${names.joinToString(", ")}"
    return if (view.mode == DraftMode.ROCHESTER && view.rounds > 0) "$who · Round ${view.round} of ${view.rounds}" else who
}

/** The Draft tab of Rochester, Rotisserie, Winston and Winchester pods. */
@Composable
internal fun PodLimitedDraftView(detail: PodDetail, accountId: String, definitions: Map<String, CardDefinition>,
    busy: Boolean, send: PodTurnSender, modifier: Modifier, columns: PodPickColumns, waitingText: String,
    onMainMenu: (() -> Unit)?, inspection: CardInspectionController) {
    Box(modifier, contentAlignment = Alignment.Center) { PodPickWaiting(waitingText, onMainMenu) }
}
